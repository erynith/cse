package com.erynith

import android.util.Log
import com.google.gson.Gson
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Encode
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.newExtractorLink
import java.util.Locale

const val DEBRID_IDs = mapOf(
    "Real-Debrid" to listOf("realdebrid", "rd"),
    "TorBox" to listOf("torbox", "tb"),
    "All-Debrid" to listOf("alldebrid", "ad"),
    "Debrid-Link" to listOf("debridlink", "dl"),
    "Premiumize" to listOf("premiumize", "pm"),
    "Debrider" to listOf("debrider", "dr"),
    "EasyDebrid" to listOf("easydebrid", "ed"),
    "Offcloud" to listOf("offcloud", "oc"),
    "PikPak" to listOf("pikpak", "pkp", "pp")
)

suspend fun invokeTorrentio(
    debridService: String? = null,
    debridKey: String? = null,
    imdbId: String? = null,
    season: Int? = null,
    episode: Int? = null,
    callback: (ExtractorLink) -> Unit
) {
    if (debridService !in arrayOf(
        "Real-Debrid", "TorBox", "All-Debrid", "Debrid-Link",
        "Premiumize", "EasyDebrid", "Offcloud"
    )) { return }
    val debridId = DEBRID_IDs[debridService]?.get(0)

    val mainUrl = "https://torrentio.strem.fun/debridoptions=nodownloadlinks|$debridId=$debridKey"

    val url = if (season == null) {
        "$mainUrl/stream/movie/$imdbId.json"
    } else {
        "$mainUrl/stream/series/$imdbId:$season:$episode.json"
    }

    runCatching {
        app.get(url, timeout = 10L).parsedSafe<StreamsResponse>()
    }.onSuccess { res ->
        res?.streams?.forEach { stream ->
            stream.runCallback(subtitleCallback, callback)
        }
    }.onFailure { e ->
        Log.e(name, "Error loading from Torrentio")
    }
}
