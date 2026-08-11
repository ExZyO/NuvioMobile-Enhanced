package com.nuvio.app.features.simkl

// EaZy Nuvio+ Start — Simkl ID lookup, search, and progress save client
import com.nuvio.app.features.addons.httpRequestRaw
import com.nuvio.app.features.anilist.SearchResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
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

            SimklSearchResultItem(
                simklId = idVal,
                title = titleVal,
                imageUrl = simklPosterUrl(posterVal),
                year = yearVal,
                totalEpisodes = totalEps,
            )
        } catch (_: Exception) {
            null
        }
    }

    suspend fun searchItems(query: String): List<SearchResult> {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return emptyList()

        val results = mutableListOf<SearchResult>()

        if (cleanQuery.startsWith("tt") || cleanQuery.startsWith("imdb:") || cleanQuery.startsWith("tmdb:") || cleanQuery.all { c -> c.isDigit() }) {
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

        val endpoints = listOf("tv", "anime", "movie")
        val headers = simklRequestHeaders(contentTypeJson = false)

        for (endpoint in endpoints) {
            val url = buildSimklApiUrl("/search/$endpoint", mapOf("q" to cleanQuery, "limit" to "10"))
            try {
                val response = httpRequestRaw("GET", url, headers, "")
                if (response.status in 200..299 && response.body.isNotBlank()) {
                    val array = json.parseToJsonElement(response.body) as? JsonArray
                    if (array != null) {
                        for (element in array) {
                            val obj = element.jsonObject
                            val idsObj = obj["ids"]?.jsonObject
                            val simklId = idsObj?.get("simkl")?.jsonPrimitive?.intOrNull
                                ?: obj["simkl"]?.jsonPrimitive?.intOrNull ?: continue
                            val title = obj["title"]?.jsonPrimitive?.contentOrNull ?: continue
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
    ): Boolean {
        val idInt = simklId.toIntOrNull() ?: return false
        val headers = simklRequestHeaders(accessToken, contentTypeJson = true)
        var success = true

        if (!status.isNullOrBlank()) {
            val url = buildSimklApiUrl("/sync/add-to-list")
            val listBody = """
                {
                  "shows": [{"ids": {"simkl": $idInt}, "to": "$status"}],
                  "movies": [{"ids": {"simkl": $idInt}, "to": "$status"}],
                  "anime": [{"ids": {"simkl": $idInt}, "to": "$status"}]
                }
            """.trimIndent()
            runCatching {
                val res = httpRequestRaw("POST", url, headers, listBody)
                success = success && (res.status in 200..299)
            }
        }

        if (score != null && score > 0) {
            val url = buildSimklApiUrl("/sync/ratings")
            val ratingBody = """
                {
                  "shows": [{"ids": {"simkl": $idInt}, "rating": $score}],
                  "movies": [{"ids": {"simkl": $idInt}, "rating": $score}],
                  "anime": [{"ids": {"simkl": $idInt}, "rating": $score}]
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
                  "anime": [{"ids": {"simkl": $idInt}, "episodes": [$epList]}]
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
