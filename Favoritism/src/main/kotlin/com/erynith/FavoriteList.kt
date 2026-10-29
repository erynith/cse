package com.erynith

object FavoriteList {

    enum class Kind(val rank: Int) { EPISODE(0), EPISODE_RANGE(1), SEASON(2), SEASON_RANGE(3), ALL(4) }

    data class Span(val kind: Kind, val from: Long, val to: Long) {
        fun contains(season: Int, episode: Int): Boolean {
            val k = key(season, episode)
            return k in from..to
        }
    }

    data class Rule(
        val imdbId: String,
        val hash: String,
        val spans: List<Span>,
        val comment: String?,
        val line: Int,
        val sourceOrder: Int = 0,
    ) {
        fun matchRank(imdb: String, season: Int?, episode: Int?): Int? {
            if (!imdbId.equals(imdb, ignoreCase = true)) return null
            if (spans.isEmpty()) return Kind.ALL.rank
            if (season == null) return null
            val ep = episode ?: 0
            return spans.filter { it.contains(season, ep) }.minOfOrNull { it.kind.rank }
        }
    }

    data class Problem(val line: Int, val text: String, val reason: String) {
        override fun toString() = "line $line: $reason  ->  ${text.trim()}"
    }

    data class Parsed(val rules: List<Rule>, val problems: List<Problem>)

    private const val MAX = 99_999L
    private fun key(season: Int, episode: Int): Long = season.toLong() * (MAX + 1) + episode
    private fun key(season: Long, episode: Long): Long = season * (MAX + 1) + episode

    private val imdbRegex = Regex("^tt\\d+$", RegexOption.IGNORE_CASE)
    private val hashRegex = Regex("^([0-9a-f]{40}|[a-z2-7]{32})$", RegexOption.IGNORE_CASE)
    private val startRegex = Regex("^S(\\d+)(?:E(\\d+))?$")
    private val endRegex = Regex("^(?:S(\\d+))?(?:E(\\d+))?$")
    private val bareNumber = Regex("^\\d+$")

    fun parse(text: String, sourceOrder: Int = 0): Parsed {
        val trimmed = text.trimStart('﻿').trim()
        if (trimmed.startsWith("{")) return parseLegacyJson(trimmed, sourceOrder)

        val rules = mutableListOf<Rule>()
        val problems = mutableListOf<Problem>()

        text.lineSequence().forEachIndexed { index, raw ->
            val lineNo = index + 1
            val hashPos = raw.indexOf('#')
            val body = (if (hashPos >= 0) raw.substring(0, hashPos) else raw).trim()
            val comment = if (hashPos >= 0) raw.substring(hashPos + 1).trim().ifEmpty { null } else null
            if (body.isEmpty()) return@forEachIndexed

            val tokens = body.split(Regex("\\s+"))
            if (tokens.size < 2) {
                problems += Problem(lineNo, raw, "needs at least an IMDb id and an infohash")
                return@forEachIndexed
            }
            val imdb = tokens.first()
            val hash = tokens.last()
            if (!imdbRegex.matches(imdb)) {
                problems += Problem(lineNo, raw, "'$imdb' doesn't look like an IMDb id (tt1234567)")
                return@forEachIndexed
            }
            if (!hashRegex.matches(hash)) {
                problems += Problem(lineNo, raw, "'$hash' doesn't look like an infohash (40 hex characters)")
                return@forEachIndexed
            }

            val scopeText = tokens.subList(1, tokens.size - 1).joinToString("")
            val spans = try {
                parseScope(scopeText)
            } catch (e: IllegalArgumentException) {
                problems += Problem(lineNo, raw, e.message ?: "bad scope")
                return@forEachIndexed
            }

            rules += Rule(imdb.lowercase(), hash.lowercase(), spans, comment, lineNo, sourceOrder)
        }
        return Parsed(rules, problems)
    }

    fun parseScope(scope: String): List<Span> {
        val s = scope.trim().uppercase()
        if (s.isEmpty() || s == "*" || s == "ALL") return emptyList()
        return s.split(',').filter { it.isNotBlank() }.map { parsePart(it.trim()) }
    }

    private fun parsePart(part: String): Span {
        if (part == "*" || part == "ALL") return Span(Kind.ALL, 0, Long.MAX_VALUE)
        val pieces = part.split('-')
        if (pieces.size > 2) throw IllegalArgumentException("'$part' has more than one '-'")

        val start = startRegex.matchEntire(pieces[0])
            ?: throw IllegalArgumentException("'${pieces[0]}' should look like S4 or S4E1")
        val s1 = start.groupValues[1].toLong()
        val e1 = start.groupValues[2].takeIf { it.isNotEmpty() }?.toLong()

        if (pieces.size == 1) {
            return if (e1 != null) Span(Kind.EPISODE, key(s1, e1), key(s1, e1))
            else Span(Kind.SEASON, key(s1, 0), key(s1, MAX))
        }

        val endText = pieces[1]
        if (endText.isEmpty()) throw IllegalArgumentException("'$part' is missing the end of the range")

        val s2: Long
        val e2: Long?
        if (bareNumber.matches(endText)) {
            if (e1 != null) { s2 = s1; e2 = endText.toLong() } else { s2 = endText.toLong(); e2 = null }
        } else {
            val end = endRegex.matchEntire(endText)
                ?: throw IllegalArgumentException("'$endText' should look like E8, S3 or S3E2")
            s2 = end.groupValues[1].takeIf { it.isNotEmpty() }?.toLong() ?: s1
            e2 = end.groupValues[2].takeIf { it.isNotEmpty() }?.toLong()
        }

        val from = key(s1, e1 ?: 0)
        val to = key(s2, e2 ?: MAX)
        if (to < from) throw IllegalArgumentException("'$part' ends before it starts")

        val kind = when {
            e1 == null && e2 == null -> if (s1 == s2) Kind.SEASON else Kind.SEASON_RANGE
            from == to -> Kind.EPISODE
            else -> Kind.EPISODE_RANGE
        }
        return Span(kind, from, to)
    }

    private fun parseLegacyJson(json: String, sourceOrder: Int): Parsed {
        val rules = mutableListOf<Rule>()
        val problems = mutableListOf<Problem>()
        Regex("\"([^\"]+)\"\\s*:\\s*\"([^\"]+)\"").findAll(json).forEachIndexed { i, m ->
            val (k, v) = m.destructured
            val parts = k.split(':')
            val scope = when (parts.size) {
                1 -> ""
                2 -> "S${parts[1]}"
                else -> "S${parts[1]}E${parts[2]}"
            }
            try {
                if (!hashRegex.matches(v)) throw IllegalArgumentException("'$v' doesn't look like an infohash")
                rules += Rule(parts[0].lowercase(), v.lowercase(), parseScope(scope), null, i + 1, sourceOrder)
            } catch (e: IllegalArgumentException) {
                problems += Problem(i + 1, m.value, e.message ?: "bad entry")
            }
        }
        return Parsed(rules, problems)
    }

    fun candidates(rules: List<Rule>, imdbId: String?, season: Int?, episode: Int?): List<Rule> {
        if (imdbId.isNullOrBlank()) return emptyList()
        return rules
            .mapNotNull { r -> r.matchRank(imdbId, season, episode)?.let { rank -> rank to r } }
            .sortedWith(compareBy({ it.first }, { it.second.sourceOrder }, { it.second.line }))
            .map { it.second }
            .distinctBy { it.hash }
    }
}
