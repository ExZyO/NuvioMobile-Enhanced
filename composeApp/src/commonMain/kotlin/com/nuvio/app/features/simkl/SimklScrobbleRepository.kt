package com.nuvio.app.features.simkl

import co.touchlab.kermit.Logger
import com.nuvio.app.features.trakt.TraktPlatformClock
import kotlinx.coroutines.CancellationException

internal object SimklScrobbleRepository {
    private val log = Logger.withTag("SimklScrobble")

    private var lastScrobbleTimeMs: Long = 0
    private val minSendIntervalMs = 8_000L

    suspend fun scrobbleStop(
        imdbId: String?,
        tmdbId: String?,
        malId: String?,
        simklId: String? = null,
        mediaType: String,
        seasonNumber: Int? = null,
        episodeNumber: Int?,
    ) {
        val uiState = SimklAuthRepository.snapshot()
        if (uiState.mode != SimklConnectionMode.CONNECTED) return
        val accessToken = SimklAuthRepository.getAccessToken() ?: return
        val clientId = SimklAuthRepository.getClientId()

        val now = TraktPlatformClock.nowEpochMs()
        if (now - lastScrobbleTimeMs < minSendIntervalMs) return

        lastScrobbleTimeMs = now

        log.d { "Scrobbling to Simkl: imdb=$imdbId tmdb=$tmdbId mal=$malId season=$seasonNumber ep=$episodeNumber" }

        val success = runCatching {
            if (episodeNumber != null && episodeNumber > 0) {
                SimklApiClient.addEpisodeToHistory(
                    clientId = clientId,
                    accessToken = accessToken,
                    imdbId = imdbId,
                    tmdbId = tmdbId,
                    malId = malId,
                    simklId = simklId,
                    mediaType = mediaType,
                    seasonNumber = seasonNumber,
                    episodeNumber = episodeNumber,
                )
            } else {
                SimklApiClient.updateItemStatus(
                    clientId = clientId,
                    accessToken = accessToken,
                    imdbId = imdbId,
                    tmdbId = tmdbId,
                    malId = malId,
                    simklId = simklId,
                    mediaType = mediaType,
                    status = "watching",
                )
            }
        }.onFailure { error ->
            if (error is CancellationException) throw error
            log.w(error) { "Failed to scrobble to Simkl" }
        }.getOrDefault(false)

        if (success) {
            log.d { "Successfully scrobbled to Simkl" }
        } else {
            log.w { "Simkl scrobble returned false" }
        }
    }
}
