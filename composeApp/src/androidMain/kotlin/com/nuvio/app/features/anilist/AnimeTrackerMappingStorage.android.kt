package com.nuvio.app.features.anilist

import android.content.Context
import android.content.SharedPreferences

internal actual object AnimeTrackerMappingStorage {
    private const val preferencesName = "nuvio_anime_tracker_mappings"
    private const val prefixAniList = "anilist_"
    private const val prefixMal = "mal_"
    private const val prefixSimkl = "simkl_"

    private var preferences: SharedPreferences? = null

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    }

    actual fun getAniListOverride(contentId: String): Int? {
        val id = preferences?.getInt(prefixAniList + contentId, -1) ?: -1
        return if (id != -1) id else null
    }

    actual fun saveAniListOverride(contentId: String, aniListId: Int) {
        preferences?.edit()?.putInt(prefixAniList + contentId, aniListId)?.commit()
    }

    actual fun removeAniListOverride(contentId: String) {
        preferences?.edit()?.remove(prefixAniList + contentId)?.commit()
    }

    actual fun getMalOverride(contentId: String): Int? {
        val id = preferences?.getInt(prefixMal + contentId, -1) ?: -1
        return if (id != -1) id else null
    }

    actual fun saveMalOverride(contentId: String, malId: Int) {
        preferences?.edit()?.putInt(prefixMal + contentId, malId)?.commit()
    }

    actual fun removeMalOverride(contentId: String) {
        preferences?.edit()?.remove(prefixMal + contentId)?.commit()
    }

    actual fun getSimklOverride(contentId: String): String? {
        val id = preferences?.getString(prefixSimkl + contentId, null)
        return id?.takeIf { it.isNotBlank() }
    }

    actual fun saveSimklOverride(contentId: String, simklId: String) {
        preferences?.edit()?.putString(prefixSimkl + contentId, simklId)?.commit()
    }

    actual fun removeSimklOverride(contentId: String) {
        preferences?.edit()?.remove(prefixSimkl + contentId)?.commit()
    }

    actual fun exportToSyncPayload(): Map<String, String> {
        val prefs = preferences ?: return emptyMap()
        val payload = mutableMapOf<String, String>()
        for ((key, value) in prefs.all) {
            if (value != null) {
                payload[key] = value.toString()
            }
        }
        return payload
    }

    actual fun applySyncPayload(payload: Map<String, String>) {
        val editor = preferences?.edit() ?: return
        for ((key, value) in payload) {
            if (key.startsWith(prefixSimkl)) {
                editor.putString(key, value)
            } else {
                value.toIntOrNull()?.let {
                    editor.putInt(key, it)
                }
            }
        }
        editor.commit()
    }
}
