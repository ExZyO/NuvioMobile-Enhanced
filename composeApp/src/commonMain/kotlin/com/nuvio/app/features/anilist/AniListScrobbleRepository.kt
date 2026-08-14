package com.nuvio.app.features.anilist

import co.touchlab.kermit.Logger
import com.nuvio.app.features.trakt.TraktPlatformClock
import kotlinx.coroutines.CancellationException

internal object AniListScrobbleRepository {
    private val log = Logger.withTag("AniListScrobble")

    private val lastScrobbleByMedia = mutableMapOf<Int, Long>()
    private val minSendIntervalMs = 3_000L

    suspend fun scrobbleStop(
        contentId: String,
        videoId: String? = null,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null,
        force: Boolean = false,
    ) {
        val uiState = AniListAuthRepository.snapshot()
        if (uiState.mode != AniListConnectionMode.CONNECTED) return
        val accessToken = AniListAuthRepository.getAccessToken() ?: return

        val progress = episodeNumber ?: 1
        val mediaId = resolveToAniListId(contentId, videoId) ?: return

        val now = TraktPlatformClock.nowEpochMs()
        val lastTime = lastScrobbleByMedia[mediaId] ?: 0L
        if (!force && now - lastTime < minSendIntervalMs) return

        lastScrobbleByMedia[mediaId] = now

        log.d { "Scrobbling to AniList: mediaId=$mediaId progress=$progress" }

        val success = runCatching {
            AniListApiClient.saveProgress(
                accessToken = accessToken,
                mediaId = mediaId,
                progress = progress,
                status = if (progress > 0) "CURRENT" else null,
            )
        }.onFailure { error ->
            if (error is CancellationException) throw error
            log.w(error) { "Failed to scrobble to AniList" }
        }.getOrDefault(false)

        if (success) {
            log.d { "Successfully scrobbled to AniList" }
        } else {
            log.w { "AniList scrobble returned false" }
        }
    }
}
