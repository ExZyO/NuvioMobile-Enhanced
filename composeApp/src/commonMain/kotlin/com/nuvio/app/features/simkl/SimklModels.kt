package com.nuvio.app.features.simkl

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive

@Serializable
enum class SimklConnectionMode {
    DISCONNECTED,
    CONNECTED,
}

@Serializable
data class SimklAuthState(
    val customClientId: String? = null,
    val accessToken: String? = null,
    val tokenType: String? = null,
    val scope: String? = null,
    val username: String? = null,
    val userAvatar: String? = null,
    val connectedAtEpochMs: Long = 0L,
)

data class SimklAuthUiState(
    val mode: SimklConnectionMode = SimklConnectionMode.DISCONNECTED,
    val clientId: String = "",
    val username: String? = null,
    val userAvatar: String? = null,
    val isConnecting: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val credentialsConfigured: Boolean = true,
)

@Serializable
data class SimklTokenResponse(
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("token_type") val tokenType: String? = null,
    @SerialName("scope") val scope: String? = null,
)

@Serializable
data class SimklUserRatings(
    @SerialName("simkl") val simklId: Int? = null,
    @SerialName("rating") val rating: Int? = null,
)

@Serializable
data class SimklIds(
    @SerialName("simkl") val simkl: Int? = null,
    @SerialName("imdb") val imdb: String? = null,
    @SerialName("tmdb") val tmdb: Int? = null,
    @SerialName("mal") val mal: Int? = null,
    @SerialName("tvdb") val tvdb: Int? = null,
)

@Serializable
data class SimklMediaItem(
    @SerialName("title") val title: String? = null,
    @SerialName("poster") val poster: String? = null,
    @SerialName("ids") val ids: SimklIds? = null,
    @SerialName("total_episodes") val totalEpisodes: Int? = null,
    @SerialName("episodes_count") val episodesCount: Int? = null,
    @SerialName("ep_count") val epCount: Int? = null,
)

@Serializable
data class SimklMemoObject(
    @SerialName("text") val text: String? = null,
    @SerialName("is_private") val isPrivate: Boolean? = null,
)

@Serializable
data class SimklListEntry(
    @SerialName("status") val status: String? = null,
    @SerialName("user_rating") val userRating: Int? = null,
    @SerialName("last_watched_at") val lastWatchedAt: String? = null,
    @SerialName("last_watched") val lastWatched: String? = null,
    @SerialName("total_episodes_count") val totalEpisodesCount: Int? = null,
    @SerialName("watched_episodes_count") val watchedEpisodesCount: Double? = null,
    @SerialName("memo") val rawMemo: JsonElement? = null,
    @SerialName("private") val private: Boolean? = null,
    @SerialName("favorite") val isFavoriteRaw: JsonElement? = null,
    @SerialName("show") val show: SimklMediaItem? = null,
    @SerialName("movie") val movie: SimklMediaItem? = null,
    @SerialName("anime") val anime: SimklMediaItem? = null,
    val detectedCategory: String = "shows"
) {
    val memoText: String?
        get() {
            return when (val elem = rawMemo) {
                is JsonPrimitive -> elem.content
                is JsonObject -> elem["text"]?.jsonPrimitive?.content
                else -> null
            }
        }

    val isMemoPrivate: Boolean
        get() {
            val obj = rawMemo as? JsonObject ?: return private ?: false
            return obj["is_private"]?.jsonPrimitive?.booleanOrNull ?: private ?: false
        }
}

@Serializable
data class SimklUserProfile(
    @SerialName("user") val user: SimklUserInfo? = null,
)

@Serializable
data class SimklUserInfo(
    @SerialName("name") val name: String? = null,
    @SerialName("avatar") val avatar: String? = null,
)

@Serializable
data class SimklSearchResult(
    @SerialName("title") val title: String? = null,
    @SerialName("year") val year: Int? = null,
    @SerialName("poster") val poster: String? = null,
    @SerialName("ids") val ids: SimklIds? = null,
    @SerialName("ep_count") val epCount: Int? = null,
)
