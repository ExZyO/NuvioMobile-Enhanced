package com.nuvio.app.features.simkl

// EaZy Nuvio+ Start — Simkl ID lookup, search, memo, and progress save client
import com.nuvio.app.features.addons.httpRequestRaw
import com.nuvio.app.features.anilist.SearchResult
import io.ktor.http.encodeURLParameter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal object SimklSearchClient {
    private val json = Json { ignoreUnknownKeys = true }

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
    )

    suspend fun lookupByContentId(contentId: String): SimklSearchResultItem? {
        val cleanId = contentId.trim()
        if (cleanId.isBlank() || cleanId == "UNTRACKED") return null

        val queryParam = when {
            cleanId.startsWith("simkl:") -> "simkl=${cleanId.removePrefix("simkl:")}"
            cleanId.startsWith("imdb:") || cleanId.startsWith("tt") -> "imdb=${cleanId.removePrefix("imdb:")}"
            cleanId.startsWith("tmdb:") -> "tmdb=${cleanId.removePrefix("tmdb:")}"
            cleanId.startsWith("mal:") -> "mal=${cleanId.removePrefix("mal:")}"
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
            val idVal = idsObj?.get("simkl")?.jsonPrimitive?.intOrNull?.toString()
                ?: idsObj?.get("simkl")?.jsonPrimitive?.contentOrNull
                ?: firstObj["simkl"]?.jsonPrimitive?.contentOrNull
                ?: return null

            val titleVal = firstObj["title"]?.jsonPrimitive?.contentOrNull ?: "Unknown Title"
            val posterVal = firstObj["poster"]?.jsonPrimitive?.contentOrNull
            val yearVal = firstObj["year"]?.jsonPrimitive?.contentOrNull
                ?: firstObj["year"]?.jsonPrimitive?.intOrNull?.toString()
            val totalEps = firstObj["total_episodes"]?.jsonPrimitive?.intOrNull
            val userRating = firstObj["user_rating"]?.jsonPrimitive?.intOrNull
            val rawStatus = firstObj["status"]?.jsonPrimitive?.contentOrNull
            val watchedEps = firstObj["watched_episodes_count"]?.jsonPrimitive?.intOrNull
                ?: firstObj["watched_episodes_count"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                ?: firstObj["episodes_watched"]?.jsonPrimitive?.intOrNull
                ?: firstObj["episodes_watched"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                ?: firstObj["watched"]?.jsonPrimitive?.intOrNull
                ?: firstObj["watched"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                ?: firstObj["last_watched"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                ?: firstObj["last_watched"]?.jsonPrimitive?.intOrNull
            val memoText = firstObj["memo"]?.jsonPrimitive?.contentOrNull
                ?: firstObj["note"]?.jsonPrimitive?.contentOrNull
                ?: firstObj["comment"]?.jsonPrimitive?.contentOrNull
            val memoPrivateStr = firstObj["memo_private"]?.jsonPrimitive?.contentOrNull?.lowercase()
                ?: firstObj["is_private"]?.jsonPrimitive?.contentOrNull?.lowercase()
                ?: firstObj["private"]?.jsonPrimitive?.contentOrNull?.lowercase()
            val isMemoPrivate = memoPrivateStr == "yes" || memoPrivateStr == "true" || memoPrivateStr == "1"
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
            )
        } catch (_: Exception) {
            null
        }
    }

    suspend fun searchItems(query: String): List<SearchResult> {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return emptyList()

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

        val endpoints = listOf("anime", "tv", "movies")
        for (endpoint in endpoints) {
            val url = "https://api.simkl.com/search/$endpoint?q=${cleanQuery.encodeURLParameter()}&limit=10&client_id=${SimklConfig.CLIENT_ID}"
            try {
                val response = httpRequestRaw("GET", url, headers, "")
                if (response.status in 200..299 && response.body.isNotBlank()) {
                    val array = json.parseToJsonElement(response.body) as? JsonArray
                    if (array != null) {
                        for (element in array) {
                            val obj = element.jsonObject
                            val idsObj = obj["ids"]?.jsonObject
                            val simklId = idsObj?.get("simkl")?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                                ?: idsObj?.get("simkl")?.jsonPrimitive?.intOrNull
                                ?: obj["simkl"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                                ?: obj["simkl"]?.jsonPrimitive?.intOrNull ?: continue
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

    suspend fun saveSimklProgress(
        accessToken: String,
        simklId: String,
        status: String?,
        score: Int?,
        progress: Int?,
        memo: String? = null,
        isPrivate: Boolean = false,
    ): Boolean {
        val idInt = simklId.toIntOrNull() ?: return false
        val headers = simklRequestHeaders(accessToken, contentTypeJson = true)
        var success = true

        val memoStr = memo ?: ""
        val memoEscaped = memoStr.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "")
        val statusStr = status ?: "watching"
        val isPrivateStr = if (isPrivate) "yes" else "no"

        if (!status.isNullOrBlank() || memo != null) {
            val url = buildSimklApiUrl("/sync/add-to-list")
            val listBody = """
                {
                  "shows": [{"ids": {"simkl": $idInt}, "to": "$statusStr", "memo": "$memoEscaped", "memo_private": "$isPrivateStr"}],
                  "movies": [{"ids": {"simkl": $idInt}, "to": "$statusStr", "memo": "$memoEscaped", "memo_private": "$isPrivateStr"}],
                  "anime": [{"ids": {"simkl": $idInt}, "to": "$statusStr", "memo": "$memoEscaped", "memo_private": "$isPrivateStr"}]
                }
            """.trimIndent()
            runCatching {
                val res = httpRequestRaw("POST", url, headers, listBody)
                success = success && (res.status in 200..299)
            }

            if (memo != null) {
                val notesUrl = buildSimklApiUrl("/users/notes/add")
                val notesBody = """
                    {
                      "simkl": $idInt,
                      "note": "$memoEscaped",
                      "private": $isPrivate
                    }
                """.trimIndent()
                runCatching {
                    httpRequestRaw("POST", notesUrl, headers, notesBody)
                }
            }
        }

        if (score != null) {
            val url = buildSimklApiUrl("/sync/ratings")
            val ratingVal = if (score > 0) score else 0
            val ratingBody = """
                {
                  "shows": [{"ids": {"simkl": $idInt}, "rating": $ratingVal}],
                  "movies": [{"ids": {"simkl": $idInt}, "rating": $ratingVal}],
                  "anime": [{"ids": {"simkl": $idInt}, "rating": $ratingVal}]
                }
            """.trimIndent()
            runCatching {
                val res = httpRequestRaw("POST", url, headers, ratingBody)
                success = success && (res.status in 200..299)
            }
        }

        if (progress != null && progress > 0) {
            val url = buildSimklApiUrl("/sync/history")
            val epList = (1..progress).joinToString(",") { """{"number": $it}""" }
            val historyBody = """
                {
                  "shows": [{"ids": {"simkl": $idInt}, "episodes": [$epList]}],
                  "anime": [{"ids": {"simkl": $idInt}, "episodes": [$epList]}],
                  "movies": [{"ids": {"simkl": $idInt}}]
                }
            """.trimIndent()
            runCatching {
                val res = httpRequestRaw("POST", url, headers, historyBody)
                success = success && (res.status in 200..299)
            }
        }

        return success
    }
}
// EaZy Nuvio+ End
