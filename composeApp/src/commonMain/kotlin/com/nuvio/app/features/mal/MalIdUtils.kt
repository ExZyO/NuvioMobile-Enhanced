package com.nuvio.app.features.mal

import com.nuvio.app.features.player.skip.SkipIntroApi

internal suspend fun resolveToMalId(
    contentId: String,
    videoId: String? = null,
): Int? {
    // 1. Check local overrides
    val override = com.nuvio.app.features.anilist.AnimeTrackerMappingStorage.getMalOverride(contentId)
    if (override != null) return override

    // 2. Try to extract MAL ID directly
    val rawId = contentId.trim()
    if (rawId.startsWith("mal:", ignoreCase = true)) {
        val id = rawId.substringAfter(':').substringBefore(':').toIntOrNull()
        if (id != null) return id
    }
    val rawVid = videoId?.trim()
    if (rawVid != null && rawVid.startsWith("mal:", ignoreCase = true)) {
        val id = rawVid.substringAfter(':').substringBefore(':').toIntOrNull()
        if (id != null) return id
    }

    // 3. Try Kitsu/AniList from contentId or videoId
    if (rawId.startsWith("kitsu:", ignoreCase = true)) {
        val kitsuId = rawId.substringAfter(':').substringBefore(':')
        val entry = SkipIntroApi.resolveKitsuToMal(kitsuId)
        if (entry?.myanimelist != null) return entry.myanimelist
    }
    if (rawVid != null && rawVid.startsWith("kitsu:", ignoreCase = true)) {
        val kitsuId = rawVid.substringAfter(':').substringBefore(':')
        val entry = SkipIntroApi.resolveKitsuToMal(kitsuId)
        if (entry?.myanimelist != null) return entry.myanimelist
    }

    if (rawId.startsWith("anilist:", ignoreCase = true)) {
        val aniId = rawId.substringAfter(':').substringBefore(':').toIntOrNull()
        if (aniId != null) {
            val fetchedMalId = com.nuvio.app.features.anilist.AniListApiClient.fetchMalId(aniId)
            if (fetchedMalId != null) return fetchedMalId
        }
    }
    if (rawVid != null && rawVid.startsWith("anilist:", ignoreCase = true)) {
        val aniId = rawVid.substringAfter(':').substringBefore(':').toIntOrNull()
        if (aniId != null) {
            val fetchedMalId = com.nuvio.app.features.anilist.AniListApiClient.fetchMalId(aniId)
            if (fetchedMalId != null) return fetchedMalId
        }
    }

    // 4. Try IMDB
    if (rawId.startsWith("tt", ignoreCase = true)) {
        val imdbId = rawId.substringBefore(':')
        val entries = SkipIntroApi.resolveImdbToAll(imdbId)
        val found = entries.firstOrNull { it.myanimelist != null }?.myanimelist
        if (found != null) return found
    }

    if (rawVid != null && rawVid.startsWith("tt", ignoreCase = true)) {
        val imdbId = rawVid.substringBefore(':')
        val entries = SkipIntroApi.resolveImdbToAll(imdbId)
        val found = entries.firstOrNull { it.myanimelist != null }?.myanimelist
        if (found != null) return found
    }

    return null
}
