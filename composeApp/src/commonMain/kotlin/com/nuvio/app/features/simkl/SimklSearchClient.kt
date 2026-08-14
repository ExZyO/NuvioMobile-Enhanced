package com.nuvio.app.features.simkl

// EaZy Nuvio+ Start — Simkl ID lookup, search, memo, and progress save client
import co.touchlab.kermit.Logger
import com.nuvio.app.core.ui.NuvioToastController
import com.nuvio.app.features.addons.httpRequestRaw
import com.nuvio.app.features.anilist.SearchResult
import com.nuvio.app.features.profiles.ProfileRepository
import com.nuvio.app.features.tracking.TrackingEpisode
import com.nuvio.app.features.tracking.TrackingExternalIds
import com.nuvio.app.features.tracking.TrackingHistoryItem
import com.nuvio.app.features.tracking.TrackingMediaKind
import com.nuvio.app.features.tracking.TrackingMediaReference
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal object SimklSearchClient {
    private val json = Json { ignoreUnknownKeys = true }
    private val log = Logger.withTag("SimklSync")

    data class SimklSearchResultItem(
        val simklId: String,
        val title: String,
        val imageUrl: String?,
        val year: String? = null,
        val totalEpisodes: Int? = null,
        val userRating: Int? = null,
        val status: String? = null,
        val watchedEpisodes: Int? = null,
        val memo: String? = null,
        val isMemoPrivate: Boolean = false,
        val isMovie: Boolean = false,
    )

    suspend fun lookupByContentId(contentId: String): SimklSearchResultItem? {
        val cleanId = contentId.trim()
        if (cleanId.isBlank() || cleanId == "UNTRACKED") return null

        val queryParam = when {
            cleanId.startsWith("simkl:") -> "simkl=${cleanId.removePrefix("simkl:")}"
            cleanId.startsWith("imdb:") || cleanId.startsWith("tt") -> "imdb=${cleanId.removePrefix("imdb:")}"
            cleanId.startsWith("tmdb:") -> "tmdb=${cleanId.removePrefix("tmdb:")}"
            cleanId.startsWith("mal:") -> "mal=${cleanId.removePrefix("mal:")}"
            cleanId.startsWith("kitsu:") -> "kitsu=${cleanId.removePrefix("kitsu:")}"
            cleanId.startsWith("anidb:") -> "anidb=${cleanId.removePrefix("anidb:")}"
            cleanId.startsWith("anilist:") -> "anilist=${cleanId.removePrefix("anilist:")}"
            cleanId.all { c -> c.isDigit() } -> "simkl=$cleanId"
            else -> return null
        }

        val paramName = queryParam.substringBefore('=')
        val paramVal = queryParam.substringAfter('=')
        val url = buildSimklApiUrl("/search/id", mapOf(paramName to paramVal))
        val headers = simklRequestHeaders(contentTypeJson = false)

        return try {
            val response = httpRequestRaw("GET", url, headers, "")
            if (response.status !in 200..299 || response.body.isBlank()) return null
            val array = json.parseToJsonElement(response.body) as? JsonArray ?: return null
            val firstObj = array.firstOrNull()?.jsonObject ?: return null

            val idsObj = firstObj["ids"]?.jsonObject
            val idVal = idsObj?.simklIdValue()
                ?: firstObj["simkl"]?.jsonPrimitive?.contentOrNull
                ?: firstObj["id"]?.jsonPrimitive?.intOrNull?.toString()
                ?: return null

            val titleVal = firstObj["title"]?.jsonPrimitive?.contentOrNull
                ?: firstObj["name"]?.jsonPrimitive?.contentOrNull
                ?: "Unknown Title"
            val posterVal = firstObj["poster"]?.jsonPrimitive?.contentOrNull
            val yearVal = firstObj["year"]?.jsonPrimitive?.contentOrNull
                ?: firstObj["year"]?.jsonPrimitive?.intOrNull?.toString()
            val totalEps = firstObj["total_episodes"]?.jsonPrimitive?.intOrNull
            val userRating = firstObj["user_rating"]?.jsonPrimitive?.intOrNull
                ?: firstObj["rating"]?.jsonPrimitive?.intOrNull
            val rawStatus = firstObj["status"]?.jsonPrimitive?.contentOrNull
            val rawType = firstObj["type"]?.jsonPrimitive?.contentOrNull
            val rawAnimeType = firstObj["anime_type"]?.jsonPrimitive?.contentOrNull
            val isMovieResult = rawType == "movie" || rawAnimeType == "movie"
            val watchedEps = firstObj["watched_episodes_count"]?.jsonPrimitive?.intOrNull
                ?: firstObj["watched_episodes_count"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                ?: firstObj["episodes_watched"]?.jsonPrimitive?.intOrNull
                ?: firstObj["episodes_watched"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                ?: firstObj["watched"]?.jsonPrimitive?.intOrNull
                ?: firstObj["watched"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                ?: firstObj["last_watched"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                ?: firstObj["last_watched"]?.jsonPrimitive?.intOrNull

            val memoObj = firstObj["memo"]?.let {
                runCatching { it.jsonObject }.getOrNull()
            }
            val memoText = memoObj?.get("text")?.jsonPrimitive?.contentOrNull
                ?: firstObj["memo"]?.jsonPrimitive?.contentOrNull
                ?: firstObj["note"]?.jsonPrimitive?.contentOrNull
                ?: firstObj["comment"]?.jsonPrimitive?.contentOrNull

            val memoPrivateStr = memoObj?.get("is_private")?.jsonPrimitive?.contentOrNull?.lowercase()
                ?: firstObj["memo_private"]?.jsonPrimitive?.contentOrNull?.lowercase()
                ?: firstObj["is_private"]?.jsonPrimitive?.contentOrNull?.lowercase()
                ?: firstObj["private"]?.jsonPrimitive?.contentOrNull?.lowercase()
            val isMemoPrivate = memoObj?.get("is_private")?.jsonPrimitive?.booleanOrNull == true
                || memoPrivateStr == "yes" || memoPrivateStr == "true" || memoPrivateStr == "1"
                || firstObj["memo_private"]?.jsonPrimitive?.booleanOrNull == true
                || firstObj["is_private"]?.jsonPrimitive?.booleanOrNull == true
                || firstObj["private"]?.jsonPrimitive?.booleanOrNull == true

            val statusVal = when (rawStatus?.lowercase()?.replace("_", "")?.replace(" ", "")) {
                "watching" -> "Watching"
                "completed" -> "Completed"
                "hold", "onhold" -> "On Hold"
                "dropped" -> "Dropped"
                "plantowatch" -> "Plan to Watch"
                else -> null
            }

            SimklSearchResultItem(
                simklId = idVal,
                title = titleVal,
                imageUrl = simklPosterUrl(posterVal),
                year = yearVal,
                totalEpisodes = totalEps,
                userRating = userRating,
                status = statusVal,
                watchedEpisodes = watchedEps,
                memo = memoText,
                isMemoPrivate = isMemoPrivate,
                isMovie = isMovieResult,
            )
        } catch (_: Exception) {
            null
        }
    }

    suspend fun searchItems(query: String): List<SearchResult> {
        val rawQuery = query.trim()
        if (rawQuery.isBlank()) return emptyList()

        val cleanQuery = rawQuery
            .replace(Regex("\\[.*?\\]"), "")
            .replace(Regex("\\(.*?\\)"), "")
            .replace(Regex("(?i)\\b(season|s)\\s*\\d+\\b"), "")
            .replace(Regex("(?i)\\b(part|cour)\\s*\\d+\\b"), "")
            .trim()
            .ifBlank { rawQuery }

        val results = mutableListOf<SearchResult>()

        if (cleanQuery.startsWith("tt") || cleanQuery.startsWith("imdb:") || cleanQuery.startsWith("tmdb:") || cleanQuery.startsWith("simkl:") || cleanQuery.startsWith("mal:")) {
            val item = lookupByContentId(cleanQuery)
            if (item != null) {
                results.add(
                    SearchResult(
                        id = item.simklId.toIntOrNull() ?: 0,
                        title = item.title,
                        imageUrl = item.imageUrl,
                        type = item.year,
                    )
                )
            }
        }

        val headers = simklRequestHeaders(contentTypeJson = false)
        val endpoints = listOf("anime", "tv", "movie")

        for (endpoint in endpoints) {
            val url = buildSimklApiUrl("/search/$endpoint", mapOf("q" to cleanQuery, "limit" to "15"))
            try {
                val response = httpRequestRaw("GET", url, headers, "")
                if (response.status in 200..299 && response.body.isNotBlank()) {
                    val array = json.parseToJsonElement(response.body) as? JsonArray
                    if (array != null) {
                        for (element in array) {
                            val obj = element.jsonObject
                            val idsObj = obj["ids"]?.jsonObject
                            val simklId = idsObj?.simklIdValue()?.toIntOrNull()
                                ?: obj["simkl"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                                ?: obj["simkl"]?.jsonPrimitive?.intOrNull
                                ?: obj["id"]?.jsonPrimitive?.intOrNull ?: continue
                            val title = obj["title"]?.jsonPrimitive?.contentOrNull
                                ?: obj["name"]?.jsonPrimitive?.contentOrNull ?: continue
                            val poster = obj["poster"]?.jsonPrimitive?.contentOrNull
                            val year = obj["year"]?.jsonPrimitive?.contentOrNull
                                ?: obj["year"]?.jsonPrimitive?.intOrNull?.toString()

                            results.add(
                                SearchResult(
                                    id = simklId,
                                    title = title,
                                    imageUrl = simklPosterUrl(poster),
                                    type = year ?: endpoint.uppercase(),
                                )
                            )
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        return results.distinctBy { it.id }
    }

    /** Fetches the full watch-order (season, episode) coordinate list for a show/anime,
     *  used to map the tracker sheet's flat progress count to Simkl's season coordinates. */
    suspend fun fetchEpisodeCoordinates(
        simklId: String,
        isAnime: Boolean,
    ): List<Pair<Int, Int>> {
        val idInt = simklId.toIntOrNull() ?: return emptyList()
        val path = if (isAnime) "/anime/episodes/$idInt" else "/tv/episodes/$idInt"
        return try {
            val response = httpRequestRaw("GET", buildSimklApiUrl(path), simklRequestHeaders(contentTypeJson = false), "")
            if (response.status !in 200..299 || response.body.isBlank()) return emptyList()
            val array = json.parseToJsonElement(response.body) as? JsonArray ?: return emptyList()
            array.mapNotNull { element ->
                val obj = element.jsonObject
                val season = obj["season"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
                val episode = obj["episode"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
                season to episode
            }
                .filter { it.first > 0 && it.second > 0 }
                .sortedWith(compareBy({ it.first }, { it.second }))
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Highest watched flat episode index (1-based), derived from the full episode
     *  list + the watched episodes. Treats a non-consecutive watched episode as
     *  "watched up to that point" (e.g. watching episode 8 means progress 8). */
    fun highestFlatEpisode(fullEpisodes: List<Pair<Int, Int>>, seasons: List<SimklSeason>): Int {
        val watched = seasons
            .flatMap { s -> s.episodes.filter { it.watchedAt != null }.map { (s.number ?: 1) to (it.number ?: 0) } }
            .toSet()
        val index = fullEpisodes.indexOfLast { it in watched }
        return if (index >= 0) index + 1 else 0
    }

    suspend fun saveSimklProgress(
        simklId: String,
        status: String?,
        score: Int?,
        progress: Int?,
        memo: String? = null,
        isPrivate: Boolean = false,
        isMovie: Boolean = false,
        ids: Map<String, JsonElement>? = null,
        isAnime: Boolean = false,
        oldProgress: Int? = null,
        seasons: List<SimklSeason>? = null,
        fullEpisodes: List<Pair<Int, Int>>? = null,
    ): Boolean {
        val idInt = simklId.toIntOrNull() ?: return false

        val idsJson = if (ids != null && ids.isNotEmpty()) {
            JsonObject(ids).toString()
        } else {
            """{"simkl": $idInt}"""
        }

        val simklStatusString = when (status?.lowercase()?.replace("_", "")?.replace(" ", "")) {
            "watching" -> "watching"
            "plantowatch" -> "plantowatch"
            "completed" -> "completed"
            "onhold", "hold" -> "hold"
            "dropped" -> "dropped"
            else -> "watching"
        }

        val memoJson = if (!memo.isNullOrBlank()) {
            val memoEscaped = memo.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "")
            """, "memo": {"text": "$memoEscaped", "is_private": $isPrivate}"""
        } else ""

        val ratingJson = if (score != null && score > 0) {
            """, "rating": $score"""
        } else ""

        val kind = when {
            isMovie -> TrackingMediaKind.MOVIE
            isAnime -> TrackingMediaKind.ANIME
            else -> TrackingMediaKind.SHOW
        }

        // Flatten into watch-order (season, episode) pairs so a flat progress
        // count maps to the correct season-aware coordinates (e.g. season 2
        // episode 3 for flat episode 13 of a 10-ep/season show). Prefer the full
        // episode list; fall back to the entry's (watched-only) seasons.
        val flattened = fullEpisodes
            ?: seasons
                ?.sortedBy { it.number ?: 0 }
                ?.flatMap { season ->
                    season.episodes.sortedBy { it.number ?: 0 }.map { ep ->
                        (season.number ?: 1) to (ep.number ?: 0)
                    }
                }
                ?.filter { it.second > 0 }

        fun mediaRef(season: Int, episode: Int) = TrackingMediaReference(
            kind = kind,
            ids = TrackingExternalIds(simkl = idInt.toLong()),
            episode = TrackingEpisode(season = if (isAnime) null else season, number = episode),
        )

        val profileId = ProfileRepository.activeProfileId

        // 1. Mark the newly-watched delta via the tested writer (the same path
        //    the auto-scrobble uses), instead of hand-rolled JSON.
        if (!isMovie && progress != null && progress > 0) {
            val start = oldProgress ?: 0
            val markPairs = if (flattened != null) {
                if (progress > start && progress <= flattened.size) flattened.subList(start, progress) else emptyList()
            } else {
                ((start + 1)..progress).map { 1 to it }
            }
            if (markPairs.isNotEmpty()) {
                val items = markPairs.map { (s, e) ->
                    TrackingHistoryItem(
                        media = mediaRef(s, e),
                        watchedAtEpochMs = SimklPlatformClock.nowEpochMs(),
                    )
                }
                val result = try {
                    SimklMutationRepository.addToHistory(profileId, items)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    log.w { "Simkl progress save failed: ${error.message}" }
                    NuvioToastController.show("Simkl save failed: ${error.message}")
                    return false
                }
                if (!result.isComplete) {
                    log.w { "Simkl progress save: ${result.notFoundCount} episodes not found" }
                    NuvioToastController.show("Simkl save failed: title not found")
                    return false
                }
            }
        }

        // 2. Decreasing progress unmarks the removed tail via the tested writer.
        if (!isMovie && progress != null && oldProgress != null && progress < oldProgress) {
            val removePairs = if (flattened != null) {
                if (oldProgress <= flattened.size) flattened.subList(progress, oldProgress) else emptyList()
            } else {
                ((progress + 1)..oldProgress).map { 1 to it }
            }
            if (removePairs.isNotEmpty()) {
                val snapshot = SimklSyncRepository.state.value.snapshot
                val enriched = removePairs.map { (s, e) ->
                    snapshot.enrichMediaReference(mediaRef(s, e))
                }
                runCatching {
                    SimklMutationRepository.removeFromHistory(profileId, enriched)
                }
            }
        }

        // 3. Set status + rating + memo. For shows/anime: add-to-list for status,
        //    plus a history call (with status to suppress auto-fill) for rating/
        //    memo. For movies: a single history call carries status.
        if (isMovie) {
            val item = """{"ids": $idsJson, "status": "$simklStatusString"$ratingJson$memoJson}"""
            runCatching {
                SimklApi.client.execute(
                    SimklApiRequest(
                        method = SimklHttpMethod.POST,
                        path = "/sync/history",
                        body = """{"movies": [$item]}""",
                        retryPolicy = SimklRetryPolicy.SYNC_WRITE,
                    ),
                )
            }
        } else {
            runCatching {
                SimklApi.client.execute(
                    SimklApiRequest(
                        method = SimklHttpMethod.POST,
                        path = "/sync/add-to-list",
                        body = """{"shows": [{"ids": {"simkl": $idInt}, "to": "$simklStatusString"}]}""",
                        retryPolicy = SimklRetryPolicy.SYNC_WRITE,
                    ),
                )
            }
            if (ratingJson.isNotEmpty() || memoJson.isNotEmpty()) {
                val item = """{"ids": $idsJson, "status": "$simklStatusString"$ratingJson$memoJson}"""
                runCatching {
                    SimklApi.client.execute(
                        SimklApiRequest(
                            method = SimklHttpMethod.POST,
                            path = "/sync/history",
                            body = """{"shows": [$item]}""",
                            retryPolicy = SimklRetryPolicy.SYNC_WRITE,
                        ),
                    )
                }
            }
        }

        // 4. progress == 0 clears the whole show history.
        if (!isMovie && progress == 0) {
            runCatching {
                SimklApi.client.execute(
                    SimklApiRequest(
                        method = SimklHttpMethod.POST,
                        path = "/sync/history/remove",
                        body = """{"shows": [{"ids": {"simkl": $idInt}}]}""",
                        retryPolicy = SimklRetryPolicy.SYNC_WRITE,
                    ),
                )
            }
        }

        NuvioToastController.show("Simkl saved")
        return true
    }

    private fun String.simklHistoryAdded(): Boolean {
        if (isBlank()) return true
        val root = runCatching { json.parseToJsonElement(this).jsonObject }.getOrNull() ?: return true
        val notFound = root["not_found"]?.jsonObject ?: return true
        val shows = notFound["shows"] as? JsonArray
        val movies = notFound["movies"] as? JsonArray
        val hasUnmatchedShow = shows != null && shows.isNotEmpty()
        val hasUnmatchedMovie = movies != null && movies.isNotEmpty()
        return !hasUnmatchedShow && !hasUnmatchedMovie
    }
}
// EaZy Nuvio+ End
