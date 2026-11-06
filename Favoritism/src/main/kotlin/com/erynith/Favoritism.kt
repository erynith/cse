package com.erynith

import android.content.SharedPreferences
import android.os.Build
import androidx.annotation.RequiresApi
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.api.Log
import org.json.JSONObject
import com.lagradost.cloudstream3.base64Encode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addTMDbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addSimklId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SearchResponseList
import com.lagradost.cloudstream3.ShowStatus
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addDate
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.metaproviders.TmdbProvider
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.runAllAsync
import com.lagradost.cloudstream3.toNewSearchResponseList
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.SubtitleHelper
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.erynith.SubsExtractors.invokeOpenSubsPro
import com.erynith.SubsExtractors.invokeOpenSubs

class Favoritism(private val sharedPref: SharedPreferences) : TmdbProvider() {
    override var mainUrl = "https://example.com"
    override var name = "Favoritism"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Torrent)

    companion object {
        const val PREF_SERVICE = "debrid_service"
        const val PREF_KEY = "debrid_key"
        const val PREF_FAV_URL = "favorites_url"
        const val PREF_FAV_LOCAL = "favorites_local"
        const val PREF_FAV_REV = "favorites_rev"
        const val DEFAULT_FAV_URL = "https://erynith.pages.dev/favorites.txt"

        const val REQUEST_TIMEOUT_MS = 3_000L
        const val FAVORITES_TTL_MS = 10 * 60_000L

        private const val tmdbAPI = "https://api.themoviedb.org/3"
        private const val apiKey = BuildConfig.TMDB_API
        private const val simkl = "https://api.simkl.com"

        fun getType(t: String?): TvType {
            return when (t) {
                "movie" -> TvType.Movie
                else -> TvType.TvSeries
            }
        }

        fun getStatus(t: String?): ShowStatus {
            return when (t) {
                "Returning Series" -> ShowStatus.Ongoing
                else -> ShowStatus.Completed
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override val mainPage = run {
        val categories = mutableListOf<Pair<String, String>>()
        val currentMonth = LocalDate.now().monthValue

        categories += "$tmdbAPI/trending/all/day?api_key=$apiKey&region=US" to "Trending"
        categories += "$tmdbAPI/movie/popular?api_key=$apiKey&region=US" to "Popular Movies"
        categories += "$tmdbAPI/tv/popular?api_key=$apiKey&region=US&with_original_language=en" to "Popular TV Shows"
        categories += "$tmdbAPI/tv/airing_today?api_key=$apiKey&region=US&with_original_language=en" to "Airing Today TV Shows"
        if (currentMonth == 10) {
            categories += "$tmdbAPI/discover/movie?api_key=$apiKey&with_genres=27&region=US" to "Halloween Horror Movies"
        }
        if (currentMonth == 11 || currentMonth == 12) {
            categories += "$tmdbAPI/discover/movie?api_key=$apiKey&with_keywords=207317&region=US" to "Christmas Movies"
        }
        categories += "$tmdbAPI/discover/tv?api_key=$apiKey&with_networks=213" to "Netflix"
        categories += "$tmdbAPI/discover/tv?api_key=$apiKey&with_networks=1024" to "Amazon Prime"
        categories += "$tmdbAPI/discover/tv?api_key=$apiKey&with_networks=2739" to "Disney+"
        categories += "$tmdbAPI/discover/tv?api_key=$apiKey&with_networks=453" to "Hulu"
        categories += "$tmdbAPI/discover/tv?api_key=$apiKey&with_networks=2552" to "Apple TV+"
        categories += "$tmdbAPI/discover/tv?api_key=$apiKey&with_networks=49" to "HBO Max"
        categories += "$tmdbAPI/discover/tv?api_key=$apiKey&with_networks=4330" to "Paramount+"
        categories += "$tmdbAPI/discover/tv?api_key=$apiKey&with_networks=4551" to "Dropout"
        categories += "$tmdbAPI/movie/top_rated?api_key=$apiKey&region=US" to "Top Rated Movies"
        categories += "$tmdbAPI/tv/top_rated?api_key=$apiKey&region=US" to "Top Rated TV Shows"
        categories += "$tmdbAPI/movie/upcoming?api_key=$apiKey&region=US" to "Upcoming Movies"

        mainPageOf(*(categories).toTypedArray())
    }

    private fun getImageUrl(link: String?, fallback: String?): String? {
        if (link == null) return fallback
        return if (link.startsWith("/")) "https://image.tmdb.org/t/p/w500/$link" else link
    }

    private fun getOriImageUrl(link: String?, fallback: String?): String? {
        if (link == null) return fallback
        return if (link.startsWith("/")) "https://image.tmdb.org/t/p/original/$link" else link
    }

    override suspend fun getMainPage(
        page: Int, request: MainPageRequest
    ): HomePageResponse {
        val adultQuery =
            if (settingsForProvider.enableAdult) "" else "&without_keywords=190370|13059|226161|195669|190370"
        val type = if (request.data.contains("/movie")) "movie" else "tv"
        val home = app.get("${request.data}$adultQuery&page=$page")
            .parsedSafe<Results>()?.results?.mapNotNull { media ->
                media.toSearchResponse(type)
            } ?: throw ErrorLoadingException("Invalid Json reponse")
        return newHomePageResponse(request.name, home)
    }

    private fun Media.toSearchResponse(type: String? = null): SearchResponse? {
        return newMovieSearchResponse(
            title ?: name ?: originalTitle ?: return null,
            Data(id = id, type = mediaType ?: type).toJson(),
            TvType.Movie,
        ) {
            this.posterUrl = getImageUrl(posterPath, "https://files.catbox.moe/90n81c.jpg")
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query,1)?.items

    override suspend fun search(query: String, page: Int): SearchResponseList? {
        return app.get(
            "$tmdbAPI/search/multi?api_key=$apiKey&language=en-US&query=$query&page=$page&include_adult=${settingsForProvider.enableAdult}"
        ).parsedSafe<Results>()?.results?.mapNotNull { media ->
            media.toSearchResponse()
        }?.toNewSearchResponseList()
    }


    override suspend fun load(url: String): LoadResponse? {
        val data = parseJson<Data>(url)
        val type = getType(data.type)
        val resUrl = if (type == TvType.Movie) {
            "$tmdbAPI/movie/${data.id}?api_key=$apiKey&append_to_response=keywords,credits,external_ids,videos,recommendations"
        } else {
            "$tmdbAPI/tv/${data.id}?api_key=$apiKey&append_to_response=keywords,credits,external_ids,videos,recommendations"
        }
        val res = app.get(resUrl).parsedSafe<MediaDetail>()
            ?: throw ErrorLoadingException("Invalid Json Response")

        val title = res.title ?: res.name ?: return null
        val poster = getOriImageUrl(res.posterPath, "https://files.catbox.moe/32gthr.jpg")
        val bgPoster = getOriImageUrl(res.backdropPath, "https://files.catbox.moe/9qao8w.jpg")
        val releaseDate = res.releaseDate ?: res.firstAirDate
        val year = releaseDate?.split("-")?.first()?.toIntOrNull()
        val genres = res.genres?.mapNotNull { it.name }
        val isAnime =
            genres?.contains("Animation") == true && (res.original_language == "zh" || res.original_language == "ja")
        val keywords = res.keywords?.results?.mapNotNull { it.name }.orEmpty()
            .ifEmpty { res.keywords?.keywords?.mapNotNull { it.name } }

        val actors = res.credits?.cast?.mapNotNull { cast ->
            ActorData(
                Actor(
                    cast.name ?: cast.originalName ?: return@mapNotNull null,
                    getImageUrl(cast.profilePath, "https://files.catbox.moe/90n81c.jpg")
                ), roleString = cast.character
            )
        } ?: return null
        val recommendations =
            res.recommendations?.results?.mapNotNull { media -> media.toSearchResponse() }

        val trailer =
            res.videos?.results?.map { "https://www.youtube.com/watch?v=${it.key}" }?.randomOrNull()

        val logoUrl = fetchTmdbLogoUrl(
            tmdbAPI = tmdbAPI,
            apiKey = apiKey,
            type = type,
            tmdbId = res.id,
            appLangCode = "en"
        )

        val simklid = runCatching {
            res.external_ids?.imdb_id?.takeIf { it.isNotBlank() }?.let { imdb ->
                val path = if (type == TvType.Movie) "movies" else "tv"
                val resJson = JSONObject(app.get("$simkl/$path/$imdb?client_id=${com.lagradost.cloudstream3.BuildConfig.SIMKL_CLIENT_ID}").text)
                resJson.optJSONObject("ids")?.optInt("simkl")?.takeIf { it != 0 }
            }
        }.getOrNull()

        return if (type == TvType.TvSeries) {
            val episodes = res.seasons?.mapNotNull { season ->
                app.get("$tmdbAPI/${data.type}/${data.id}/season/${season.seasonNumber}?api_key=$apiKey")
                    .parsedSafe<MediaDetailEpisodes>()?.episodes?.map { eps ->
                        newEpisode(LoadData(
                            res.external_ids?.imdb_id,
                            eps.seasonNumber,
                            eps.episodeNumber
                        ).toJson())
                        {
                            this.name = eps.name + if (isUpcoming(eps.airDate)) " • [UPCOMING]" else ""
                            this.season = eps.seasonNumber
                            this.episode = eps.episodeNumber
                            this.posterUrl = getImageUrl(eps.stillPath, "https://files.catbox.moe/qbz6xd.jpg")
                            this.score = Score.from10(eps.voteAverage)
                            this.description = eps.overview
                            this.runTime = eps.runtime
                            this.addDate(eps.airDate)
                        }
                    }
            }?.flatten() ?: listOf()
            newTvSeriesLoadResponse(
                title, url, if (isAnime) TvType.Anime else TvType.TvSeries, episodes
            ) {
                this.posterUrl = poster
                this.backgroundPosterUrl = bgPoster
                try { this.logoUrl = logoUrl } catch(_:Throwable){}
                this.year = year
                this.plot = res.overview
                this.tags =  keywords.takeIf { !it.isNullOrEmpty() } ?: genres
                this.score = Score.from10(res.vote_average.toString())
                this.showStatus = getStatus(res.status)
                this.recommendations = recommendations
                this.actors = actors
                //this.contentRating = fetchContentRating(data.id, "US")
                addTrailer(trailer)
                addTMDbId(data.id.toString())
                addImdbId(res.external_ids?.imdb_id)
                addSimklId(simklid)
            }
        } else {
            newMovieLoadResponse(
                title,
                url,
                TvType.Movie,
                LoadData(res.external_ids?.imdb_id).toJson()
            ) {
                this.posterUrl = poster
                try { this.logoUrl = logoUrl } catch(_:Throwable){}
                this.comingSoon = isUpcoming(releaseDate)
                this.backgroundPosterUrl = bgPoster
                this.year = year
                this.plot = res.overview
                this.duration = res.runtime
                this.tags = keywords.takeIf { !it.isNullOrEmpty() } ?: genres
                this.score = Score.from10(res.vote_average.toString())
                this.recommendations = recommendations
                this.actors = actors
                //this.contentRating = fetchContentRating(data.id, "US")
                addTrailer(trailer)
                addTMDbId(data.id.toString())
                addImdbId(res.external_ids?.imdb_id)
                addSimklId(simklid)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val res = parseJson<LoadData>(data)
        val service = sharedPref.getString(PREF_SERVICE, null)
        val key = sharedPref.getString(PREF_KEY, null)

        if (res.imdbId.isNullOrBlank() || service.isNullOrBlank() || key.isNullOrBlank()) {
            return false
        }

        runAllAsync(
            suspend { loadStreams(service, key, res, subtitleCallback, callback) },
            suspend { invokeOpenSubsPro(res.imdbId, res.season, res.episode, subtitleCallback) },
            suspend { invokeOpenSubs(res.imdbId, res.season, res.episode, subtitleCallback) }
        )

        return true
    }

    private suspend fun loadStreams(
        service: String,
        key: String,
        res: LoadData,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val addons = ADDONS.mapNotNull { addon -> addon.baseUrl(service, key)?.let { addon to it } }
        if (addons.isEmpty()) return

        val favorites = FavoriteList.candidates(favoriteRules(), res.imdbId, res.season, res.episode)

        if (favorites.isEmpty()) {
            val results = coroutineScope {
                addons.map { (addon, base) -> async { addon to fetchStreams(addon, base, res) } }.awaitAll()
            }
            emitAll(results, subtitleCallback, callback)
            return
        }

        val collected = mutableListOf<Pair<Addon, List<Stream>>>()
        for ((addon, base) in addons) {
            val streams = fetchStreams(addon, base, res)
            val match = findFavorite(streams, favorites)
            if (match != null) {
                val (stream, rule) = match
                Log.d(name, "Favorite ${rule.hash} found on ${addon.name}" + (rule.comment?.let { " ($it)" } ?: ""))
                stream.emit(favorite = true, subtitleCallback, callback)
                return
            }
            collected += addon to streams
        }
        Log.d(name, "No favorite found for ${res.imdbId} S${res.season}E${res.episode}, returning all streams")
        emitAll(collected, subtitleCallback, callback)
    }

    private fun findFavorite(
        streams: List<Stream>,
        favorites: List<FavoriteList.Rule>
    ): Pair<Stream, FavoriteList.Rule>? {
        for (rule in favorites) {
            val hash = rule.hash.lowercase()
            val hit = streams.firstOrNull { it.url?.lowercase()?.contains(hash) == true }
            if (hit != null) return hit to rule
        }
        return null
    }

    private suspend fun emitAll(
        results: List<Pair<Addon, List<Stream>>>,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val seen = mutableSetOf<String>()
        for ((addon, streams) in results) {
            for (stream in streams) {
                val hash = addon.hashOf(stream)?.lowercase()
                if (!hash.isNullOrBlank() && !seen.add(hash)) continue
                stream.emit(favorite = false, subtitleCallback, callback)
            }
        }
    }

    private suspend fun fetchStreams(addon: Addon, base: String, res: LoadData): List<Stream> {
        val url = if (res.season == null) {
            "$base/stream/movie/${res.imdbId}.json"
        } else {
            "$base/stream/series/${res.imdbId}:${res.season}:${res.episode}.json"
        }
        return try {
            val streams = withTimeoutOrNull(REQUEST_TIMEOUT_MS) {
                app.get(url, timeout = REQUEST_TIMEOUT_MS / 1000).parsedSafe<StreamsResponse>()?.streams
            }
            if (streams == null) Log.e(name, "${addon.name}: no response within ${REQUEST_TIMEOUT_MS}ms")
            streams?.filterNotNull() ?: emptyList()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(name, "${addon.name}: ${e.message}")
            emptyList()
        }
    }

    private val favoritesLock = Mutex()
    private var remoteRules: List<FavoriteList.Rule> = emptyList()
    private var remoteUrl: String? = null
    private var remoteRev: Long = -1
    private var remoteLoadedAt = 0L

    private suspend fun favoriteRules(): List<FavoriteList.Rule> = favoritesLock.withLock {
        val url = sharedPref.getString(PREF_FAV_URL, DEFAULT_FAV_URL)?.trim().orEmpty()
        val rev = sharedPref.getLong(PREF_FAV_REV, 0L)
        val now = System.currentTimeMillis()

        if (url != remoteUrl || rev != remoteRev || now - remoteLoadedAt > FAVORITES_TTL_MS) {
            if (url.isEmpty()) {
                remoteRules = emptyList()
                remoteLoadedAt = now
            } else {
                val text = fetchFavoritesText(url)
                if (text != null) {
                    val parsed = FavoriteList.parse(text, sourceOrder = 1)
                    parsed.problems.forEach { Log.e(name, "favorites list $it") }
                    remoteRules = parsed.rules
                    remoteLoadedAt = now
                } else {
                    Log.e(name, "Couldn't load favorites list from $url")
                    if (url != remoteUrl) remoteRules = emptyList()
                    remoteLoadedAt = now - FAVORITES_TTL_MS + 60_000
                }
            }
            remoteUrl = url
            remoteRev = rev
        }

        val local = FavoriteList.parse(sharedPref.getString(PREF_FAV_LOCAL, "").orEmpty(), sourceOrder = 0).rules
        local + remoteRules
    }

    private class Addon(
        val name: String,
        val baseUrl: (service: String, key: String) -> String?,
        val hashOf: (Stream) -> String?,
    )

    private val ADDONS: List<Addon> by lazy {
        listOf(
            Addon(
                name = "Torrentio",
                baseUrl = { service, key ->
                    if (service !in setOf("Real-Debrid", "TorBox", "All-Debrid", "Debrid-Link", "Premiumize", "EasyDebrid", "Offcloud")) null
                    else "https://torrentio.strem.fun/debridoptions=nodownloadlinks|${DEBRID_IDs[service]?.get(0)}=$key"
                },
                hashOf = { s -> s.url?.takeIf { it.contains("/resolve/") }?.split("/")?.getOrNull(6) },
            ),
            Addon(
                name = "Comet",
                baseUrl = { service, key ->
                    if (service !in DEBRID_IDs) null
                    else {
                        val manifest = """
                            {
                                "maxResultsPerResolution": 0,
                                "maxSize": 0,
                                "cachedOnly": true,
                                "sortCachedUncachedTogether": false,
                                "removeTrash": true,
                                "resultFormat": ["all"],
                                "debridServices": [{"service": "${DEBRID_IDs[service]?.get(0)}", "apiKey": "$key"}],
                                "enableTorrent": false,
                                "deduplicateStreams": false,
                                "scrapeDebridAccountTorrents": false,
                                "debridStreamProxyPassword": "",
                                "languages": {"required": [], "allowed": [], "exclude": [], "preferred": []},
                                "resolutions": {},
                                "options": {"remove_ranks_under": -10000000000, "allow_english_in_languages": false, "remove_unknown_languages": false}
                            }
                        """.trimIndent()
                        "https://comet.feels.legal/${base64Encode(manifest.toByteArray())}"
                    }
                },
                hashOf = { s -> s.behaviorHints?.bingeGroup?.takeIf { it.contains("|") }?.substringAfterLast("|") },
            ),
            Addon(
                name = "StremThru",
                baseUrl = { service, key ->
                    if (service !in DEBRID_IDs) null
                    else {
                        val manifest = """
                            {
                                "indexers": null,
                                "stores": [{"c": "${DEBRID_IDs[service]?.get(1)}", "t": "$key"}],
                                "cached": true
                            }
                        """.trimIndent()
                        "https://stremthru.13377001.xyz/stremio/torz/${base64Encode(manifest.toByteArray())}"
                    }
                },
                hashOf = { s -> s.behaviorHints?.bingeGroup?.takeIf { it.contains(":") }?.substringAfterLast(":") },
            ),
        )
    }

    private val DEBRID_IDs = mapOf(
        "Real-Debrid" to listOf("realdebrid", "rd"),
        "TorBox" to listOf("torbox", "tb"),
        "All-Debrid" to listOf("alldebrid", "ad"),
        "Debrid-Link" to listOf("debridlink", "dl"),
        "Premiumize" to listOf("premiumize", "pm"),
        "Debrider" to listOf("debrider", "dr"),
        "EasyDebrid" to listOf("easydebrid", "ed"),
        "Offcloud" to listOf("offcloud", "oc"),
        "PikPak" to listOf("pikpak", "pp", "pkp")
    )

    private data class StreamsResponse(val streams: List<Stream?>? = null)

    private data class Subtitle(
        val url: String?,
        val lang: String?,
        val id: String?,
    )

    private data class ProxyHeaders(
        val request: Map<String, String>?,
    )

    private data class BehaviorHints(
        val bingeGroup: String? = null,
        val filename: String? = null,
        val proxyHeaders: ProxyHeaders? = null,
        val headers: Map<String, String>? = null,
    )

    private data class Stream(
        val name: String? = null,
        val title: String? = null,
        val url: String? = null,
        val description: String? = null,
        val behaviorHints: BehaviorHints? = null,
        val sources: List<String>? = null,
        val subtitles: List<Subtitle>? = null,
    ) {
        suspend fun emit(
            favorite: Boolean,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
        ) {
            val link = url ?: return
            var sourceName = fixSourceName(name, title, description)
            if (favorite) sourceName = "★ Favorite\n$sourceName"

            callback.invoke(
                newExtractorLink(
                    name ?: "",
                    "--------------------------------------------------------\n$sourceName |",
                    link,
                    INFER_TYPE,
                ) {
                    this.quality = getQuality(listOf(name, title, description))
                    this.headers = behaviorHints?.proxyHeaders?.request ?: behaviorHints?.headers ?: mapOf()
                }
            )
            subtitles?.forEach { sub ->
                subtitleCallback.invoke(
                    newSubtitleFile(
                        SubtitleHelper.fromTagToEnglishLanguageName(sub.lang ?: "") ?: sub.lang ?: "",
                        sub.url ?: return@forEach
                    )
                )
            }
        }
    }

    data class LoadData(
        val imdbId: String? = null,
        val season: Int? = null,
        val episode: Int? = null,
    )

    data class Data(
        val id: Int? = null,
        val type: String? = null,
        val aniId: String? = null,
        val malId: Int? = null,
    )

    data class Results(
        @JsonProperty("results") val results: ArrayList<Media>? = arrayListOf(),
    )

    data class Media(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("original_title") val originalTitle: String? = null,
        @JsonProperty("media_type") val mediaType: String? = null,
        @JsonProperty("poster_path") val posterPath: String? = null,
    )

    data class Genres(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("name") val name: String? = null,
    )

    data class Keywords(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("name") val name: String? = null,
    )

    data class KeywordResults(
        @JsonProperty("results") val results: ArrayList<Keywords>? = arrayListOf(),
        @JsonProperty("keywords") val keywords: ArrayList<Keywords>? = arrayListOf(),
    )

    data class Seasons(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("season_number") val seasonNumber: Int? = null,
        @JsonProperty("air_date") val airDate: String? = null,
    )

    data class Cast(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("original_name") val originalName: String? = null,
        @JsonProperty("character") val character: String? = null,
        @JsonProperty("known_for_department") val knownForDepartment: String? = null,
        @JsonProperty("profile_path") val profilePath: String? = null,
    )

    data class Episodes(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("overview") val overview: String? = null,
        @JsonProperty("air_date") val airDate: String? = null,
        @JsonProperty("still_path") val stillPath: String? = null,
        @JsonProperty("runtime") val runtime: Int? = null,
        @JsonProperty("vote_average") val voteAverage: Double? = null,
        @JsonProperty("episode_number") val episodeNumber: Int? = null,
        @JsonProperty("season_number") val seasonNumber: Int? = null,
    )

    data class MediaDetailEpisodes(
        @JsonProperty("episodes") val episodes: ArrayList<Episodes>? = arrayListOf(),
    )

    data class Trailers(
        @JsonProperty("key") val key: String? = null,
    )

    data class ResultsTrailer(
        @JsonProperty("results") val results: ArrayList<Trailers>? = arrayListOf(),
    )

    data class ExternalIds(
        @JsonProperty("imdb_id") val imdb_id: String? = null,
        @JsonProperty("tvdb_id") val tvdb_id: String? = null,
    )

    data class Credits(
        @JsonProperty("cast") val cast: ArrayList<Cast>? = arrayListOf(),
    )

    data class ResultsRecommendations(
        @JsonProperty("results") val results: ArrayList<Media>? = arrayListOf(),
    )

    data class LastEpisodeToAir(
        @JsonProperty("episode_number") val episode_number: Int? = null,
        @JsonProperty("season_number") val season_number: Int? = null,
    )

    data class MediaDetail(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("imdb_id") val imdbId: String? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("original_title") val originalTitle: String? = null,
        @JsonProperty("original_name") val originalName: String? = null,
        @JsonProperty("poster_path") val posterPath: String? = null,
        @JsonProperty("backdrop_path") val backdropPath: String? = null,
        @JsonProperty("release_date") val releaseDate: String? = null,
        @JsonProperty("first_air_date") val firstAirDate: String? = null,
        @JsonProperty("overview") val overview: String? = null,
        @JsonProperty("runtime") val runtime: Int? = null,
        @JsonProperty("vote_average") val vote_average: Any? = null,
        @JsonProperty("original_language") val original_language: String? = null,
        @JsonProperty("status") val status: String? = null,
        @JsonProperty("genres") val genres: ArrayList<Genres>? = arrayListOf(),
        @JsonProperty("keywords") val keywords: KeywordResults? = null,
        @JsonProperty("last_episode_to_air") val last_episode_to_air: LastEpisodeToAir? = null,
        @JsonProperty("seasons") val seasons: ArrayList<Seasons>? = arrayListOf(),
        @JsonProperty("videos") val videos: ResultsTrailer? = null,
        @JsonProperty("external_ids") val external_ids: ExternalIds? = null,
        @JsonProperty("credits") val credits: Credits? = null,
        @JsonProperty("recommendations") val recommendations: ResultsRecommendations? = null,
    )

}
