package com.nuvio.app.features.simkl

// EaZy Nuvio+ Start — Simkl ID lookup, search, and save client for tracker sheet
import com.nuvio.app.features.addons.httpRequestRaw
import com.nuvio.app.features.anilist.SearchResult
import io.ktor.http.encodeURLParameter
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
    )

    suspend fun lookupByContentId(contentId: String): SimklSearchResultItem? {
        if (contentId.isBlank() || contentId == "UNTRACKED") return null

        val imdbId = contentId.takeIf { it.startsWith("tt") }
            ?: contentId.removePrefix("imdb:").takeIf { contentId.startsWith("imdb:") }

        val tmdbId = contentId.removePrefix("tmdb:").takeIf { contentId.startsWith("tmdb:") }
            ?: contentId.takeIf { !it.startsWith("tt") && it.all { c -> c.isDigit() } }

        val malId = contentId.removePrefix("mal:").takeIf { contentId.startsWith("mal:") }

        val queryParam = when {
            !imdbId.isNullOrBlank() -> "imdb=$imdbId"
            !tmdbId.isNullOrBlank() -> "tmdb=$tmdbId"
            !malId.isNullOrBlank() -> "mal=$malId"
            contentId.all { c -> c.isDigit() } -> "simkl=$contentId"
            else -> return null
        }

        val clientId = SimklConfig.CLIENT_ID.ifBlank { "6eaf02a9b63b01eb1750cdecf0f05e7d0d8dd949d1eb6894716857947bb68c1a" }
        val url = "https://api.simkl.com/search/id?$queryParam&client_id=$clientId"
        val headers = mapOf("Accept" to "application/json")

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

            SimklSearchResultItem(
                simklId = idVal,
                title = titleVal,
                imageUrl = simklPosterUrl(posterVal),
                year = yearVal,
            )
        } catch (_: Exception) {
            null
        }
    }

    suspend fun searchItems(query: String): List<SearchResult> {
        if (query.isBlank()) return emptyList()
        val clientId = SimklConfig.CLIENT_ID.ifBlank { "6eaf02a9b63b01eb1750cdecf0f05e7d0d8dd949d1eb6894716857947bb68c1a" }

        // If query looks like an ID (e.g. tt12345 or digits), try ID lookup first
        if (query.startsWith("tt") || query.all { c -> c.isDigit() }) {
            val item = lookupByContentId(query)
            if (item != null) {
                return listOf(
                    SearchResult(
                        id = item.simklId.toIntOrNull() ?: 0,
                        title = item.title,
                        imageUrl = item.imageUrl,
                        type = item.year,
                    )
                )
            }
        }

        val encodedQuery = query.encodeURLParameter()
        val headers = mapOf("Accept" to "application/json")
        val results = mutableListOf<SearchResult>()

        val endpoints = listOf("anime", "tv", "movies")
        for (endpoint in endpoints) {
            val url = "https://api.simkl.com/search/$endpoint?q=$encodedQuery&client_id=$clientId&limit=10"
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
        val clientId = SimklConfig.CLIENT_ID.ifBlank { "6eaf02a9b63b01eb1750cdecf0f05e7d0d8dd949d1eb6894716857947bb68c1a" }
        val headers = mapOf(
            "Authorization" to "Bearer $accessToken",
            "simkl-api-key" to clientId,
            "Content-Type" to "application/json",
            "Accept" to "application/json",
        )
        val idInt = simklId.toIntOrNull() ?: return false
        var success = true

        if (!status.isNullOrBlank()) {
            val listBody = """
                {
                  "shows": [{"ids": {"simkl": $idInt}, "to": "$status"}],
                  "movies": [{"ids": {"simkl": $idInt}, "to": "$status"}],
                  "anime": [{"ids": {"simkl": $idInt}, "to": "$status"}]
                }
            """.trimIndent()
            runCatching {
                val res = httpRequestRaw("POST", "https://api.simkl.com/sync/add-to-list", headers, listBody)
                success = success && (res.status in 200..299)
            }
        }

        if (score != null && score > 0) {
            val ratingBody = """
                {
                  "shows": [{"ids": {"simkl": $idInt}, "rating": $score}],
                  "movies": [{"ids": {"simkl": $idInt}, "rating": $score}],
                  "anime": [{"ids": {"simkl": $idInt}, "rating": $score}]
                }
            """.trimIndent()
            runCatching {
                val res = httpRequestRaw("POST", "https://api.simkl.com/sync/ratings", headers, ratingBody)
                success = success && (res.status in 200..299)
            }
        }

        if (progress != null && progress > 0) {
            val epList = (1..progress).joinToString(",") { """{"number": $it}""" }
            val historyBody = """
                {
                  "shows": [{"ids": {"simkl": $idInt}, "episodes": [$epList]}],
                  "anime": [{"ids": {"simkl": $idInt}, "episodes": [$epList]}]
                }
            """.trimIndent()
            runCatching {
                val res = httpRequestRaw("POST", "https://api.simkl.com/sync/history", headers, historyBody)
                success = success && (res.status in 200..299)
            }
        }

        return success
    }
}
// EaZy Nuvio+ End
