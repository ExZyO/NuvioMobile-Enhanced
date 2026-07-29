package com.nuvio.app.features.simkl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val OledSheetBg = Color(0xFF05060A)
private val OledCardBg = Color(0xFF0F111A)
private val OledCardBorder = Color(0xFF1E2235)
private val TextPrimary = Color(0xFFFFFFFF)
private val TextSecondary = Color(0xFFA1A5B7)

val SimklBrandColor = Color(0xFF00C755)

@Composable
fun SimklTrackerCard(
    imdbId: String? = null,
    tmdbId: String? = null,
    malId: String? = null,
    simklId: String? = null,
    mediaType: String = "show",
    maxEpisodesCount: Int = 2000,
    onUntrack: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val simklState by SimklAuthRepository.uiState.collectAsState()

    var isLoaded by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Plan to Watch") }
    var score by remember { mutableStateOf(0) }
    var progress by remember { mutableStateOf(0) }
    var fetchedMaxEp by remember { mutableStateOf<Int?>(null) }
    var resolvedCategory by remember { mutableStateOf(mediaType) }
    var statusExpanded by remember { mutableStateOf(false) }
    var memoText by remember { mutableStateOf("") }
    var isPrivateMemo by remember { mutableStateOf(false) }

    var initialMemoText by remember { mutableStateOf<String?>(null) }
    var initialIsPrivate by remember { mutableStateOf<Boolean?>(null) }

    val isMovie = resolvedCategory.lowercase() == "movies" || (mediaType.lowercase() == "movie" && maxEpisodesCount <= 1)
    val finalMaxEpisodes = when {
        isMovie -> 1
        fetchedMaxEp != null && fetchedMaxEp!! > 0 -> fetchedMaxEp!!
        maxEpisodesCount > 0 && maxEpisodesCount != 2000 -> maxEpisodesCount
        else -> 500
    }
    val sliderMax = if (finalMaxEpisodes != 500) finalMaxEpisodes else maxOf(100, progress + 20)
    val maxDisplayStr = if (finalMaxEpisodes != 500) "$finalMaxEpisodes" else "?"

    val statuses = listOf("Watching", "Plan to Watch", "Completed", "On Hold", "Dropped")

    LaunchedEffect(imdbId, tmdbId, malId, simklId) {
        val token = SimklAuthRepository.getAccessToken()
        val clientId = SimklAuthRepository.getClientId()
        if (!token.isNullOrBlank() && simklState.mode == SimklConnectionMode.CONNECTED) {
            val entry = SimklApiClient.fetchItemStatus(clientId, token, imdbId, tmdbId, malId, simklId)
            if (entry != null) {
                resolvedCategory = entry.detectedCategory
                val item = entry.show ?: entry.movie ?: entry.anime
                val totalEp = entry.totalEpisodesCount ?: item?.totalEpisodes ?: item?.episodesCount ?: item?.epCount
                if (totalEp != null && totalEp > 0) {
                    fetchedMaxEp = totalEp
                }
                status = when (entry.status?.lowercase()) {
                    "watching" -> "Watching"
                    "completed" -> "Completed"
                    "hold", "on_hold" -> "On Hold"
                    "dropped" -> "Dropped"
                    "plantowatch", "plan_to_watch" -> "Plan to Watch"
                    else -> "Plan to Watch"
                }

                val entryScore = entry.userRating ?: SimklApiClient.fetchRating(clientId, token, imdbId, tmdbId, malId, simklId)
                if (entryScore != null && entryScore > 0) {
                    score = entryScore
                }
                
                val watchedCount = entry.watchedEpisodesCount?.toInt() ?: entry.lastWatched?.toIntOrNull()
                if (watchedCount != null) {
                    progress = watchedCount
                }

                if (!entry.memoText.isNullOrBlank()) {
                    memoText = entry.memoText!!
                    isPrivateMemo = entry.isMemoPrivate
                }

                val targetSimklId = simklId ?: item?.ids?.simkl?.toString()
                if (!targetSimklId.isNullOrBlank()) {
                    val pair = SimklApiClient.fetchMediaSummary(clientId, targetSimklId, resolvedCategory)
                    if (pair != null) {
                        if (pair.first != null && pair.first!! > 0) fetchedMaxEp = pair.first
                        resolvedCategory = pair.second
                    }
                }
            } else {
                status = "Plan to Watch"
                progress = 0

                val targetSimklId = simklId
                if (!targetSimklId.isNullOrBlank()) {
                    val pair = SimklApiClient.fetchMediaSummary(clientId, targetSimklId, mediaType)
                    if (pair != null) {
                        if (pair.first != null && pair.first!! > 0) fetchedMaxEp = pair.first
                        resolvedCategory = pair.second
                    }
                }

                val entryScore = SimklApiClient.fetchRating(clientId, token, imdbId, tmdbId, malId, simklId)
                if (entryScore != null && entryScore > 0) {
                    score = entryScore
                }
            }
        }
        initialMemoText = memoText
        initialIsPrivate = isPrivateMemo
        isLoaded = true
    }

    // Debounced Auto-Sync for Memo changes (only when modified by user)
    LaunchedEffect(memoText, isPrivateMemo) {
        if (isLoaded && (memoText != initialMemoText || isPrivateMemo != initialIsPrivate)) {
            delay(500)
            val token = SimklAuthRepository.getAccessToken()
            val clientId = SimklAuthRepository.getClientId()
            if (!token.isNullOrBlank()) {
                val simklStatusString = when (status) {
                    "Watching" -> "watching"
                    "Completed" -> "completed"
                    "On Hold" -> "hold"
                    "Dropped" -> "dropped"
                    "Plan to Watch" -> "plantowatch"
                    else -> "watching"
                }
                val success = SimklApiClient.saveMemo(
                    clientId = clientId,
                    accessToken = token,
                    imdbId = imdbId,
                    tmdbId = tmdbId,
                    malId = malId,
                    simklId = simklId,
                    mediaType = resolvedCategory,
                    memoText = memoText,
                    isPrivate = isPrivateMemo,
                    status = simklStatusString
                )
                if (success) {
                    initialMemoText = memoText
                    initialIsPrivate = isPrivateMemo
                }
            }
        }
    }

    fun syncStatusToSimkl(
        newStatus: String = status,
        newProg: Int = progress,
        newMemo: String = memoText,
        newPriv: Boolean = isPrivateMemo
    ) {
        scope.launch {
            val token = SimklAuthRepository.getAccessToken()
            val clientId = SimklAuthRepository.getClientId()
            if (!token.isNullOrBlank()) {
                val simklStatusString = when (newStatus) {
                    "Watching" -> "watching"
                    "Completed" -> "completed"
                    "On Hold" -> "hold"
                    "Dropped" -> "dropped"
                    "Plan to Watch" -> "plantowatch"
                    else -> "watching"
                }
                SimklApiClient.updateItemStatus(
                    clientId = clientId,
                    accessToken = token,
                    imdbId = imdbId,
                    tmdbId = tmdbId,
                    malId = malId,
                    simklId = simklId,
                    mediaType = resolvedCategory,
                    status = simklStatusString,
                    progressCount = newProg,
                    memo = newMemo,
                    isPrivate = newPriv
                )
            }
        }
    }

    if (simklState.mode == SimklConnectionMode.CONNECTED) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF0F111A),
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, OledCardBorder)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Simkl Tracking",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = SimklBrandColor
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        scope.launch {
                            onUntrack()
                        }
                    }) {
                        Text("Untrack", color = TextSecondary, fontWeight = FontWeight.Medium)
                    }
                }

                Text("Status", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                Box {
                    OutlinedButton(
                        onClick = { statusExpanded = true },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = OledSheetBg,
                            contentColor = TextPrimary
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, OledCardBorder),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(status, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = TextSecondary)
                    }
                    DropdownMenu(
                        expanded = statusExpanded,
                        onDismissRequest = { statusExpanded = false }
                    ) {
                        statuses.forEach { s ->
                            DropdownMenuItem(
                                text = { Text(s) },
                                onClick = {
                                    status = s
                                    statusExpanded = false
                                    val targetProg = if (s == "Completed" && finalMaxEpisodes > 0 && finalMaxEpisodes != 500) finalMaxEpisodes else progress
                                    if (s == "Completed" && finalMaxEpisodes > 0 && finalMaxEpisodes != 500) {
                                        progress = finalMaxEpisodes
                                    }
                                    syncStatusToSimkl(newStatus = s, newProg = targetProg)
                                }
                            )
                        }
                    }
                }

                // Progress Controls (only if not a movie)
                if (!isMovie) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Watched Episodes", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = "$progress / $maxDisplayStr",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = SimklBrandColor
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        IconButton(
                            onClick = {
                                if (progress > 0) {
                                    progress -= 1
                                    syncStatusToSimkl(newProg = progress)
                                }
                            },
                            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(OledSheetBg)
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "-1", tint = TextPrimary)
                        }
                        Slider(
                            value = progress.toFloat().coerceIn(0f, sliderMax.toFloat()),
                            onValueChange = { progress = it.roundToInt() },
                            onValueChangeFinished = {
                                syncStatusToSimkl(newProg = progress)
                            },
                            valueRange = 0f..sliderMax.toFloat(),
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = SimklBrandColor,
                                activeTrackColor = SimklBrandColor
                            )
                        )
                        IconButton(
                            onClick = {
                                if (progress < sliderMax) {
                                    progress += 1
                                    syncStatusToSimkl(newProg = progress)
                                }
                            },
                            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(OledSheetBg)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "+1", tint = TextPrimary)
                        }
                    }
                }

                // Rating Controls
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Rating (1-10)", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = if (score == 0) "Unrated" else "$score / 10",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = SimklBrandColor
                    )
                }
                Slider(
                    value = score.toFloat().coerceIn(0f, 10f),
                    onValueChange = { score = it.roundToInt() },
                    onValueChangeFinished = {
                        scope.launch {
                            val token = SimklAuthRepository.getAccessToken()
                            val clientId = SimklAuthRepository.getClientId()
                            if (!token.isNullOrBlank()) {
                                SimklApiClient.updateRating(
                                    clientId = clientId,
                                    accessToken = token,
                                    imdbId = imdbId,
                                    tmdbId = tmdbId,
                                    malId = malId,
                                    simklId = simklId,
                                    mediaType = resolvedCategory,
                                    rating = score
                                )
                            }
                        }
                    },
                    valueRange = 0f..10f,
                    steps = 9,
                    colors = SliderDefaults.colors(
                        thumbColor = SimklBrandColor,
                        activeTrackColor = SimklBrandColor
                    )
                )

                // Simkl Memo / Notes Section
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Simkl Memo / Note", style = MaterialTheme.typography.labelMedium, color = TextSecondary)

                    OutlinedTextField(
                        value = memoText,
                        onValueChange = { newMemo ->
                            if (newMemo.length <= 140) {
                                memoText = newMemo
                            }
                        },
                        placeholder = { Text("Add personal note (max 140 chars)", color = TextSecondary) },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = OledSheetBg,
                            unfocusedContainerColor = OledSheetBg,
                            focusedBorderColor = SimklBrandColor,
                            unfocusedBorderColor = OledCardBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilterChip(
                            selected = isPrivateMemo,
                            onClick = {
                                isPrivateMemo = !isPrivateMemo
                            },
                            label = { Text(if (isPrivateMemo) "Private Memo" else "Public Memo") }
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = "${memoText.length} / 140",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }
        }
    }
}
