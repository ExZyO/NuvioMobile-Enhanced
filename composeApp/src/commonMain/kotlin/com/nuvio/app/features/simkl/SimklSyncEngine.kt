package com.nuvio.app.features.simkl

internal class SimklSyncEngine(
    private val remote: SimklSyncRemote,
    private val nowEpochMs: () -> Long,
) {
    suspend fun synchronize(
        current: SimklSyncSnapshot,
        forceFullRefresh: Boolean = false,
    ): SimklSyncSnapshot {
        if (!current.isInitialized) return initialSync()

        val activities = remote.fetchActivities()

        // The tracker sheet opens request a full re-fetch because memo/note
        // edits made on the website don't advance a tracked activity bucket,
        // so the watermark gate below would otherwise skip re-fetching them.
        if (forceFullRefresh && current.watermark != null) {
            val full = remote.fetchAllItems(SimklAllItemsRequest.FullRefresh)
            val now = nowEpochMs()
            return current.copy(
                watermark = activities.all,
                activities = activities,
                entries = mergeDelta(current.entries, full),
                playback = current.playback,
                lastSyncedAtEpochMs = now,
                lastCheckedAtEpochMs = now,
            ).reconcileWatchedPlayback()
        }

        if (activities.all == current.watermark) {
            return current.copy(
                activities = activities,
                lastCheckedAtEpochMs = nowEpochMs(),
            )
        }
        if (current.watermark == null) return initialSync()

        val allItemsChanged = hasAllItemsActivityChanged(current.activities, activities)
        val removalChanged = hasRemovalActivityChanged(current.activities, activities)
        val playbackChanged = hasPlaybackActivityChanged(current.activities, activities)
        val settingsChanged = hasSettingsActivityChanged(current.activities, activities)

        var entries = current.entries
        if (allItemsChanged) {
            val delta = remote.fetchAllItems(SimklAllItemsRequest.Changes(current.watermark))
            entries = mergeDelta(entries, delta)
        }

        if (removalChanged) {
            val authoritativeIds = remote.fetchAllItems(SimklAllItemsRequest.CurrentIds)
            entries = reconcileRemovedEntries(entries, authoritativeIds)
        }

        // Simkl exposes no memo/note activity bucket, so editing a memo on the
        // website only moves the global watermark without touching any tracked
        // bucket. The `date_from` delta does not reliably surface memo-only
        // edits, so do a full all-items fetch here to pick up the edited entry
        // (memo, watched progress, etc.) instead of skipping past the watermark.
        val unclassifiedChange =
            !allItemsChanged && !removalChanged && !playbackChanged && !settingsChanged
        if (unclassifiedChange) {
            val full = remote.fetchAllItems(SimklAllItemsRequest.FullRefresh)
            entries = mergeDelta(entries, full)
        }

        val playback = if (playbackChanged) {
            remote.fetchPlayback()
        } else {
            current.playback
        }

        val now = nowEpochMs()
        return current.copy(
            watermark = activities.all,
            activities = activities,
            entries = entries,
            playback = playback,
            lastSyncedAtEpochMs = now,
            lastCheckedAtEpochMs = now,
        ).reconcileWatchedPlayback()
    }

    private suspend fun initialSync(): SimklSyncSnapshot {
        val entries = buildList {
            SimklMediaType.entries.forEach { type ->
                addAll(
                    remote.fetchAllItems(SimklAllItemsRequest.Bootstrap(type))
                        .entriesFor(type),
                )
            }
        }
        val playback = remote.fetchPlayback()
        val activities = remote.fetchActivities()
        val now = nowEpochMs()
        return SimklSyncSnapshot(
            isInitialized = true,
            watermark = activities.all,
            activities = activities,
            entries = entries.distinctBy(SimklLibraryEntry::stableKey),
            playback = playback,
            lastSyncedAtEpochMs = now,
            lastCheckedAtEpochMs = now,
        ).reconcileWatchedPlayback()
    }
}

internal fun mergeDelta(
    current: List<SimklLibraryEntry>,
    delta: SimklAllItemsResponse,
): List<SimklLibraryEntry> {
    val merged = current.mapNotNull { entry -> entry.stableKey()?.let { key -> key to entry } }.toMap().toMutableMap()
    delta.presentTypes().forEach { type ->
        delta.entriesFor(type).forEach { entry ->
            entry.stableKey()?.let { key -> merged[key] = entry }
        }
    }
    return merged.values.sortedWith(simklEntryComparator)
}

internal fun reconcileRemovedEntries(
    current: List<SimklLibraryEntry>,
    authoritative: SimklAllItemsResponse,
): List<SimklLibraryEntry> {
    val allowedKeys = SimklMediaType.entries.flatMapTo(mutableSetOf()) { type ->
        authoritative.entriesFor(type).mapNotNull(SimklLibraryEntry::stableKey)
    }
    return current.filter { entry -> entry.stableKey() in allowedKeys }
        .sortedWith(simklEntryComparator)
}

private fun hasAllItemsActivityChanged(
    previous: SimklActivities?,
    current: SimklActivities,
): Boolean {
    if (previous == null) return true
    return SimklMediaType.entries.any { type ->
        current.domain(type).hasAllItemsActivityChangedFrom(previous.domain(type))
    }
}

private fun SimklActivityDomain.hasAllItemsActivityChangedFrom(
    previous: SimklActivityDomain,
): Boolean =
    ratedAt != previous.ratedAt ||
        plantowatch != previous.plantowatch ||
        watching != previous.watching ||
        completed != previous.completed ||
        hold != previous.hold ||
        dropped != previous.dropped

private fun hasRemovalActivityChanged(
    previous: SimklActivities?,
    current: SimklActivities,
): Boolean =
    previous == null ||
        SimklMediaType.entries.any { type ->
            previous.domain(type).removedFromList != current.domain(type).removedFromList
        }

private fun hasPlaybackActivityChanged(
    previous: SimklActivities?,
    current: SimklActivities,
): Boolean =
    previous == null ||
        SimklMediaType.entries.any { type ->
            previous.domain(type).playback != current.domain(type).playback
        }

private fun hasSettingsActivityChanged(
    previous: SimklActivities?,
    current: SimklActivities,
): Boolean =
    previous == null || current.settings.all != previous.settings.all

private val simklEntryComparator = compareBy<SimklLibraryEntry>(
    { entry -> entry.mediaType.ordinal },
    { entry -> entry.media?.title.orEmpty().lowercase() },
    { entry -> entry.stableKey().orEmpty() },
)
