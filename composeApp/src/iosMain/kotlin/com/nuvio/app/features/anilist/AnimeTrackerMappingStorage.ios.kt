package com.nuvio.app.features.anilist

import platform.Foundation.NSUserDefaults

internal actual object AnimeTrackerMappingStorage {
    private const val prefixAniList = "nuvio_tracker_anilist_"
    private const val prefixMal = "nuvio_tracker_mal_"
    private const val prefixSimkl = "nuvio_tracker_simkl_"

    actual fun getAniListOverride(contentId: String): Int? {
        val id = NSUserDefaults.standardUserDefaults.integerForKey(prefixAniList + contentId).toInt()
        return if (id != 0) id else null
    }

    actual fun saveAniListOverride(contentId: String, aniListId: Int) {
        NSUserDefaults.standardUserDefaults.setInteger(aniListId.toLong(), forKey = prefixAniList + contentId)
    }

    actual fun removeAniListOverride(contentId: String) {
        NSUserDefaults.standardUserDefaults.removeObjectForKey(prefixAniList + contentId)
    }

    actual fun getMalOverride(contentId: String): Int? {
        val id = NSUserDefaults.standardUserDefaults.integerForKey(prefixMal + contentId).toInt()
        return if (id != 0) id else null
    }

    actual fun saveMalOverride(contentId: String, malId: Int) {
        NSUserDefaults.standardUserDefaults.setInteger(malId.toLong(), forKey = prefixMal + contentId)
    }

    actual fun removeMalOverride(contentId: String) {
        NSUserDefaults.standardUserDefaults.removeObjectForKey(prefixMal + contentId)
    }

    actual fun getSimklOverride(contentId: String): String? {
        val str = NSUserDefaults.standardUserDefaults.stringForKey(prefixSimkl + contentId)
        return str?.takeIf { it.isNotBlank() }
    }

    actual fun saveSimklOverride(contentId: String, simklId: String) {
        NSUserDefaults.standardUserDefaults.setObject(simklId, forKey = prefixSimkl + contentId)
    }

    actual fun removeSimklOverride(contentId: String) {
        NSUserDefaults.standardUserDefaults.removeObjectForKey(prefixSimkl + contentId)
    }

    actual fun exportToSyncPayload(): Map<String, String> {
        val defaults = NSUserDefaults.standardUserDefaults.dictionaryRepresentation()
        val payload = mutableMapOf<String, String>()
        for ((key, value) in defaults) {
            val keyStr = key.toString()
            if (keyStr.startsWith(prefixAniList) || keyStr.startsWith(prefixMal) || keyStr.startsWith(prefixSimkl)) {
                payload[keyStr] = value.toString()
            }
        }
        return payload
    }

    actual fun applySyncPayload(payload: Map<String, String>) {
        for ((key, value) in payload) {
            if (key.startsWith(prefixSimkl)) {
                NSUserDefaults.standardUserDefaults.setObject(value, forKey = key)
            } else {
                value.toIntOrNull()?.let {
                    NSUserDefaults.standardUserDefaults.setInteger(it.toLong(), forKey = key)
                }
            }
        }
    }
}
