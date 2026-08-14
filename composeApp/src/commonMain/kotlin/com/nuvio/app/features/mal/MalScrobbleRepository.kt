package com.nuvio.app.features.mal

import co.touchlab.kermit.Logger
import com.nuvio.app.features.trakt.TraktPlatformClock
import kotlinx.coroutines.CancellationException

internal object MalScrobbleRepository {
    private val log = Logger.withTag("MalScrobble")

    private val lastScrobbleByMedia = mutableMapOf<Int, Long>()
    private val minSendIntervalMs = 3_000L

    suspend fun scrobbleStop(
        contentId: String,
        videoId: String? = null,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null,
        force: Boolean = false,
    ) {
        val uiState = MalAuthRepository.snapshot()
        if (uiState.mode != MalConnectionMode.CONNECTED) return
        val accessToken = MalAuthRepository.getAccessToken() ?: return

        val progress = episodeNumber ?: 1
        val animeId = resolveToMalId(contentId, videoId) ?: return

        val now = TraktPlatformClock.nowEpochMs()
        val lastTime = lastScrobbleByMedia[animeId] ?: 0L
        if (!force && now - lastTime < minSendIntervalMs) return

        lastScrobbleByMedia[animeId] = now

        log.d { "Scrobbling to MAL: animeId=$animeId progress=$progress" }

        val success = runCatching {
            MalApiClient.saveProgress(
                accessToken = accessToken,
                animeId = animeId,
                numWatchedEpisodes = progress,
                status = if (progress > 0) "watching" else null,
            )
        }.onFailure { error ->
            if (error is CancellationException) throw error
            log.w(error) { "Failed to scrobble to MAL" }
        }.getOrDefault(false)

        if (success) {
            log.d { "Successfully scrobbled to MAL" }
        } else {
            log.w { "MAL scrobble returned false" }
        }
    }
}
