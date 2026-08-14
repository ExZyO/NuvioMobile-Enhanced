package com.nuvio.app.features.anilist

import com.nuvio.app.features.player.skip.SkipIntroApi

internal suspend fun resolveToAniListId(
    contentId: String,
    videoId: String? = null,
): Int? {
    // 1. Check local overrides
    val override = AnimeTrackerMappingStorage.getAniListOverride(contentId)
    if (override != null) return override

    // 2. Try to extract AniList ID directly
    val rawId = contentId.trim()
    if (rawId.startsWith("anilist:", ignoreCase = true)) {
        val id = rawId.substringAfter(':').substringBefore(':').toIntOrNull()
        if (id != null) return id
    }
    val rawVid = videoId?.trim()
    if (rawVid != null && rawVid.startsWith("anilist:", ignoreCase = true)) {
        val id = rawVid.substringAfter(':').substringBefore(':').toIntOrNull()
        if (id != null) return id
    }

    // 3. Try Kitsu/MAL from contentId or videoId
    if (rawId.startsWith("kitsu:", ignoreCase = true)) {
        val kitsuId = rawId.substringAfter(':').substringBefore(':')
        val entry = SkipIntroApi.resolveKitsuToAnilist(kitsuId)
        if (entry?.anilist != null) return entry.anilist
    }
    if (rawVid != null && rawVid.startsWith("kitsu:", ignoreCase = true)) {
        val kitsuId = rawVid.substringAfter(':').substringBefore(':')
        val entry = SkipIntroApi.resolveKitsuToAnilist(kitsuId)
        if (entry?.anilist != null) return entry.anilist
    }

    if (rawId.startsWith("mal:", ignoreCase = true)) {
        val malId = rawId.substringAfter(':').substringBefore(':')
        val entry = SkipIntroApi.resolveMalToAnilist(malId)
        if (entry?.anilist != null) return entry.anilist
    }
    if (rawVid != null && rawVid.startsWith("mal:", ignoreCase = true)) {
        val malId = rawVid.substringAfter(':').substringBefore(':')
        val entry = SkipIntroApi.resolveMalToAnilist(malId)
        if (entry?.anilist != null) return entry.anilist
    }

    // 4. Try IMDB
    if (rawId.startsWith("tt", ignoreCase = true)) {
        val imdbId = rawId.substringBefore(':')
        val entries = SkipIntroApi.resolveImdbToAll(imdbId)
        val found = entries.firstOrNull { it.anilist != null }?.anilist
        if (found != null) return found
    }

    if (rawVid != null && rawVid.startsWith("tt", ignoreCase = true)) {
        val imdbId = rawVid.substringBefore(':')
        val entries = SkipIntroApi.resolveImdbToAll(imdbId)
        val found = entries.firstOrNull { it.anilist != null }?.anilist
        if (found != null) return found
    }

    return null
}
