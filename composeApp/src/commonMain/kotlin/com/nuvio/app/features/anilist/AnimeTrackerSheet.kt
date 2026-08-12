package com.nuvio.app.features.anilist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.NuvioLoadingIndicator
import com.nuvio.app.features.mal.MalApiClient
import com.nuvio.app.features.mal.MalAuthRepository
import com.nuvio.app.features.mal.MalConnectionMode
import com.nuvio.app.features.mal.resolveToMalId
import com.nuvio.app.features.simkl.matchesContentId
import com.nuvio.app.features.simkl.simklIdValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.*
import kotlinx.datetime.TimeZone
import kotlin.math.roundToInt

// UI-UX Pro Max Theme Color System (OLED Dark Mode)
private val OledSheetBg = Color(0xFF05060A)
private val OledCardBg = Color(0xFF0F111A)
private val OledCardBorder = Color(0xFF1E2235)
private val AniListBrandColor = Color(0xFF02A9FF)
private val MalBrandColor = Color(0xFF2E51A2)
private val PrimaryAccent = Color(0xFF6366F1)
private val TextPrimary = Color(0xFFFFFFFF)
private val TextSecondary = Color(0xFFA1A5B7)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun AnimeTrackerSheet(
    contentId: String,
    videoId: String?,
    title: String,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current

    var isResolvingIds by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(true) }

    var aniListId by remember { mutableStateOf<Int?>(null) }
    var malId by remember { mutableStateOf<Int?>(null) }
    var aniListEntryId by remember { mutableStateOf<Int?>(null) }

    var aniListTitle by remember { mutableStateOf<String?>(null) }
    var aniListImageUrl by remember { mutableStateOf<String?>(null) }
    var malTitle by remember { mutableStateOf<String?>(null) }
    var malImageUrl by remember { mutableStateOf<String?>(null) }

    // User editable state
    var aniListStatus by remember { mutableStateOf("Plan to Watch") }
    var aniListScore by remember { mutableStateOf(0f) }
    var aniListProgress by remember { mutableStateOf(0f) }
    var isAniListFavourite by remember { mutableStateOf(false) }

    var malStatus by remember { mutableStateOf("Plan to Watch") }
    var malScore by remember { mutableStateOf(0f) }
    var malProgress by remember { mutableStateOf(0f) }
    var totalRewatches by remember { mutableStateOf(0) }
    var maxEpisodes by remember { mutableStateOf(2000) }
    var notes by remember { mutableStateOf("") }

    var startDateMillis by remember { mutableStateOf<Long?>(null) }
    var finishDateMillis by remember { mutableStateOf<Long?>(null) }

    // AniList Specific
    var isPrivate by remember { mutableStateOf(false) }
    var hideFromStatusLists by remember { mutableStateOf(false) }
    var storyScore by remember { mutableStateOf(0f) }
    var charScore by remember { mutableStateOf(0f) }
    var visualScore by remember { mutableStateOf(0f) }
    var audioScore by remember { mutableStateOf(0f) }
    var enjoymentScore by remember { mutableStateOf(0f) }

    // MAL Specific
    var priority by remember { mutableStateOf(0) }
    var rewatchValue by remember { mutableStateOf(0) }

    val aniListState by AniListAuthRepository.uiState.collectAsState()
    val malState by MalAuthRepository.uiState.collectAsState()
    val simklState by com.nuvio.app.features.simkl.SimklAuthRepository.uiState.collectAsState()

    var simklId by remember { mutableStateOf<String?>(null) }
    var simklTitle by remember { mutableStateOf<String?>(null) }
    var simklImageUrl by remember { mutableStateOf<String?>(null) }

    // EaZy Nuvio+ Start — Simkl Tracking Options State
    var simklStatus by remember { mutableStateOf("Watching") }
    var simklStatusExpanded by remember { mutableStateOf(false) }
    val simklStatuses = listOf("Watching", "Plan to Watch", "Completed", "On Hold", "Dropped")
    var simklScore by remember { mutableStateOf(0f) }
    var simklProgress by remember { mutableStateOf(0f) }
    var simklMemo by remember { mutableStateOf("") }
    var isPrivateMemo by remember { mutableStateOf(false) }
    // EaZy Nuvio+ End

    var aniListStatusExpanded by remember { mutableStateOf(false) }
    var malStatusExpanded by remember { mutableStateOf(false) }
    val aniListStatuses = listOf("Watching", "Plan to Watch", "Completed", "Rewatching", "Paused", "Dropped")
    val malStatuses = listOf("Watching", "Completed", "On Hold", "Dropped", "Plan to Watch")

    // Date Picker state
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showFinishDatePicker by remember { mutableStateOf(false) }
    val startDatePickerState = rememberDatePickerState(initialSelectedDateMillis = startDateMillis)
    val finishDatePickerState = rememberDatePickerState(initialSelectedDateMillis = finishDateMillis)

    LaunchedEffect(contentId, videoId) {
        isResolvingIds = true
        isLoading = true
        aniListId = resolveToAniListId(contentId, videoId)
        malId = resolveToMalId(contentId, videoId)
        val simklOverride = AnimeTrackerMappingStorage.getSimklOverride(contentId)
        simklId = if (simklOverride == "UNTRACKED") null else simklOverride
        isResolvingIds = false
    }

    LaunchedEffect(aniListId) {
        storyScore = 0f
        charScore = 0f
        visualScore = 0f
        audioScore = 0f
        enjoymentScore = 0f
        aniListProgress = 0f
        aniListScore = 0f
        maxEpisodes = 2000
    }

    LaunchedEffect(malId) {
        priority = 0
        rewatchValue = 0
        malProgress = 0f
        malScore = 0f
        maxEpisodes = 2000
    }

    LaunchedEffect(aniListId, malId, simklId, isResolvingIds) {
        if (isResolvingIds) return@LaunchedEffect
        isLoading = true

        var isUiInitialized = false

        val aniJob = launch {
            if (aniListState.credentialsConfigured && aniListId != null) {
                val token = AniListAuthRepository.getAccessToken()
                if (!token.isNullOrBlank()) {
                    val entry = AniListApiClient.fetchListEntry(token, aniListId!!)
                    if (entry != null) {
                        aniListEntryId = entry.id
                        aniListTitle = entry.title
                        aniListImageUrl = entry.imageUrl

                        val fetchedStatus = when (entry.status) {
                            "CURRENT" -> "Watching"
                            "COMPLETED" -> "Completed"
                            "PAUSED" -> "Paused"
                            "DROPPED" -> "Dropped"
                            "PLANNING" -> "Plan to Watch"
                            "REPEATING" -> "Rewatching"
                            else -> "Plan to Watch"
                        }
                        aniListStatus = fetchedStatus

                        val rawScore = entry.score ?: 0.0
                        aniListScore = rawScore.toFloat()

                        isAniListFavourite = entry.isFavourite

                        val newProg = (entry.progress ?: 0).toFloat()
                        aniListProgress = newProg

                        if (!isUiInitialized) {
                            totalRewatches = entry.repeat ?: 0
                            notes = entry.notes ?: ""
                            isPrivate = entry.private ?: false
                            hideFromStatusLists = entry.hiddenFromStatusLists ?: false

                            if (entry.startedAt != null && entry.startedAt.year != null && entry.startedAt.month != null && entry.startedAt.day != null) {
                                try {
                                    val ld = LocalDate(entry.startedAt.year, entry.startedAt.month, entry.startedAt.day)
                                    startDateMillis = ld.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
                                } catch (e: Exception) {}
                            }
                            if (entry.completedAt != null && entry.completedAt.year != null && entry.completedAt.month != null && entry.completedAt.day != null) {
                                try {
                                    val ld = LocalDate(entry.completedAt.year, entry.completedAt.month, entry.completedAt.day)
                                    finishDateMillis = ld.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
                                } catch (e: Exception) {}
                            }

                            isUiInitialized = true
                        }

                        if (entry.maxEpisodes != null && entry.maxEpisodes > 0) {
                            maxEpisodes = entry.maxEpisodes
                        }

                        if (entry.advancedScores != null && entry.advancedScores.size >= 5) {
                            storyScore = entry.advancedScores[0].toFloat()
                            charScore = entry.advancedScores[1].toFloat()
                            visualScore = entry.advancedScores[2].toFloat()
                            audioScore = entry.advancedScores[3].toFloat()
                            enjoymentScore = entry.advancedScores[4].toFloat()
                        }
                    }
                }
            }
        }

        val malJob = launch {
            if (malState.credentialsConfigured && malId != null) {
                val token = MalAuthRepository.getAccessToken()
                if (!token.isNullOrBlank()) {
                    val entry = MalApiClient.fetchListEntry(token, malId!!)
                    if (entry != null) {
                        malTitle = entry.title
                        malImageUrl = entry.imageUrl

                        val fetchedStatusMal = when (entry.status) {
                            "watching" -> "Watching"
                            "completed" -> "Completed"
                            "on_hold" -> "On Hold"
                            "dropped" -> "Dropped"
                            "plan_to_watch" -> "Plan to Watch"
                            else -> "Plan to Watch"
                        }
                        malStatus = fetchedStatusMal
                        malScore = (entry.score ?: 0.0).toFloat()

                        val newProgMal = (entry.progress ?: 0).toFloat()
                        malProgress = newProgMal
                        priority = entry.priority ?: 0 // EaZy Nuvio+
                        rewatchValue = entry.rewatchValue ?: 0 // EaZy Nuvio+

                        if (!isUiInitialized) {
                            totalRewatches = entry.numTimesRewatched ?: 0
                            notes = entry.comments ?: ""

                            if (!entry.startDate.isNullOrBlank()) {
                                try {
                                    val ld = LocalDate.parse(entry.startDate)
                                    startDateMillis = ld.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
                                } catch (e: Exception) {}
                            }
                            if (!entry.finishDate.isNullOrBlank()) {
                                try {
                                    val ld = LocalDate.parse(entry.finishDate)
                                    finishDateMillis = ld.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
                                } catch (e: Exception) {}
                            }
                            isUiInitialized = true
                        }

                        if (entry.maxEpisodes != null && entry.maxEpisodes > 0) {
                            maxEpisodes = entry.maxEpisodes
                        }
                    }
                }
            }
        }

        // EaZy Nuvio+ Start — Automatic Simkl Lookup & Data Population
        val simklJob = launch {
            val savedSimkl = AnimeTrackerMappingStorage.getSimklOverride(contentId)
            if (savedSimkl == "UNTRACKED") {
                simklId = null
                return@launch
            }
            if (simklState.mode == com.nuvio.app.features.simkl.SimklConnectionMode.CONNECTED) {
                com.nuvio.app.features.simkl.SimklSyncRepository.ensureLoaded()
                val snapshot = com.nuvio.app.features.simkl.SimklSyncRepository.state.value.snapshot
                val targetId = savedSimkl ?: contentId
                val localMatch = snapshot.entries.firstOrNull { entry ->
                    val simklIdStr = entry.media?.ids?.simklIdValue()
                    (savedSimkl != null && simklIdStr == savedSimkl) ||
                    entry.matchesContentId(targetId) ||
                    (contentId.isNotBlank() && entry.matchesContentId(contentId))
                }
                if (localMatch != null && localMatch.media != null) {
                    val media = localMatch.media!!
                    simklId = media.ids.simklIdValue()
                    simklTitle = media.title ?: title
                    simklImageUrl = com.nuvio.app.features.simkl.simklPosterUrl(media.poster)
                    simklStatus = when (localMatch.status) {
                        com.nuvio.app.features.simkl.SimklListStatus.WATCHING -> "Watching"
                        com.nuvio.app.features.simkl.SimklListStatus.PLAN_TO_WATCH -> "Plan to Watch"
                        com.nuvio.app.features.simkl.SimklListStatus.COMPLETED -> "Completed"
                        com.nuvio.app.features.simkl.SimklListStatus.ON_HOLD -> "On Hold"
                        com.nuvio.app.features.simkl.SimklListStatus.DROPPED -> "Dropped"
                        else -> "Watching"
                    }
                    simklScore = (localMatch.userRating ?: 0).toFloat()
                    simklProgress = localMatch.watchedEpisodesCount.toFloat()
                    simklMemo = localMatch.memo ?: ""
                    isPrivateMemo = localMatch.memoPrivate
                    if (localMatch.totalEpisodesCount > 0) {
                        maxEpisodes = localMatch.totalEpisodesCount
                    } else if (localMatch.isMovieEntry()) {
                        maxEpisodes = 1
                    }
                } else {
                    val lookupId = if (savedSimkl != null) "simkl:$savedSimkl" else targetId
                    val lookupRes = com.nuvio.app.features.simkl.SimklSearchClient.lookupByContentId(lookupId)
                    if (lookupRes != null) {
                        simklId = lookupRes.simklId
                        simklTitle = lookupRes.title
                        simklImageUrl = lookupRes.imageUrl
                        if (lookupRes.userRating != null && lookupRes.userRating > 0) {
                            simklScore = lookupRes.userRating.toFloat()
                        }
                        if (!lookupRes.status.isNullOrBlank()) {
                            simklStatus = lookupRes.status
                        }
                        if (lookupRes.watchedEpisodes != null) {
                            simklProgress = lookupRes.watchedEpisodes.toFloat()
                        }
                        if (!lookupRes.memo.isNullOrBlank()) {
                            simklMemo = lookupRes.memo
                        }
                        isPrivateMemo = lookupRes.isMemoPrivate
                        if (lookupRes.totalEpisodes != null && lookupRes.totalEpisodes > 0) {
                            maxEpisodes = lookupRes.totalEpisodes
                        }
                    }
                }
            }
        }
        // EaZy Nuvio+ End

        withTimeoutOrNull(5000) {
            joinAll(aniJob, malJob, simklJob)
        }
        isLoading = false
    }

    LaunchedEffect(startDateMillis, finishDateMillis) {
        startDatePickerState.selectedDateMillis = startDateMillis
        finishDatePickerState.selectedDateMillis = finishDateMillis
    }

    var showSearchModal by remember { mutableStateOf(false) }
    var searchMode by remember { mutableStateOf("AniList") }

    if (showSearchModal) {
        var searchQuery by remember { mutableStateOf(title) }
        var searchResults by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
        var isSearching by remember { mutableStateOf(false) }

        val performSearch: () -> Unit = {
            val q = searchQuery.ifBlank { title }
            if (q.isNotBlank() && !isSearching) {
                scope.launch {
                    isSearching = true
                    when (searchMode) {
                        "AniList" -> {
                            val token = AniListAuthRepository.getAccessToken()
                            if (token != null) searchResults = AniListApiClient.searchAnime(token, q)
                        }
                        "MAL" -> {
                            val token = MalAuthRepository.getAccessToken()
                            if (token != null) searchResults = MalApiClient.searchAnime(token, q)
                        }
                        "Simkl" -> {
                            searchResults = com.nuvio.app.features.simkl.SimklSearchClient.searchItems(q) // EaZy Nuvio+
                        }
                    }
                    isSearching = false
                }
            }
        }

        LaunchedEffect(showSearchModal) {
            if (showSearchModal) {
                performSearch()
            }
        }

        Dialog(
            onDismissRequest = { showSearchModal = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = OledSheetBg
            ) {
                Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { showSearchModal = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = TextPrimary)
                        }
                        Text("Link $searchMode", style = MaterialTheme.typography.titleLarge, color = TextPrimary, fontWeight = FontWeight.Bold)
                    }
                    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Search Anime / Media", color = TextSecondary) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = OledCardBg,
                            unfocusedContainerColor = OledCardBg,
                            focusedBorderColor = PrimaryAccent,
                            unfocusedBorderColor = OledCardBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = {
                            performSearch()
                            focusManager.clearFocus()
                        }),
                        trailingIcon = {
                            IconButton(onClick = {
                                performSearch()
                                focusManager.clearFocus()
                            }) {
                                Text("Go", color = PrimaryAccent, fontWeight = FontWeight.Bold)
                            }
                        }
                    )
                    Spacer(Modifier.height(16.dp))
                    if (isSearching) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { NuvioLoadingIndicator() }
                    } else {
                        androidx.compose.foundation.lazy.LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 32.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(searchResults.size) { i ->
                                val result = searchResults[i]
                                SearchItemCard(
                                    result = result,
                                    onClick = {
                                        when (searchMode) {
                                            "AniList" -> {
                                                aniListId = result.id
                                                aniListTitle = result.title
                                                aniListImageUrl = result.imageUrl
                                                AnimeTrackerMappingStorage.saveAniListOverride(contentId, result.id)
                                            }
                                            "MAL" -> {
                                                malId = result.id
                                                malTitle = result.title
                                                malImageUrl = result.imageUrl
                                                AnimeTrackerMappingStorage.saveMalOverride(contentId, result.id)
                                            }
                                            "Simkl" -> {
                                                simklId = result.id.toString()
                                                simklTitle = result.title
                                                simklImageUrl = result.imageUrl
                                                AnimeTrackerMappingStorage.saveSimklOverride(contentId, result.id.toString())
                                            }
                                        }
                                        showSearchModal = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showStartDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    startDateMillis = startDatePickerState.selectedDateMillis
                    showStartDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        startDateMillis = null
                        startDatePickerState.selectedDateMillis = null
                        showStartDatePicker = false
                    }) { Text("Clear Date", color = Color(0xFFFF4757)) }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { showStartDatePicker = false }) { Text("Cancel") }
                }
            }
        ) {
            DatePicker(state = startDatePickerState)
        }
    }

    if (showFinishDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showFinishDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    finishDateMillis = finishDatePickerState.selectedDateMillis
                    showFinishDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        finishDateMillis = null
                        finishDatePickerState.selectedDateMillis = null
                        showFinishDatePicker = false
                    }) { Text("Clear Date", color = Color(0xFFFF4757)) }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { showFinishDatePicker = false }) { Text("Cancel") }
                }
            }
        ) {
            DatePicker(state = finishDatePickerState)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = OledSheetBg,
        contentColor = TextPrimary,
        scrimColor = Color.Black.copy(alpha = 0.8f),
        dragHandle = {
            Surface(
                modifier = Modifier.padding(vertical = 12.dp),
                color = OledCardBorder,
                shape = RoundedCornerShape(4.dp)
            ) {
                Box(modifier = Modifier.size(width = 40.dp, height = 4.dp))
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
            // Hero Header Card
            ProSectionCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(14.dp).fillMaxWidth()
                ) {
                    val displayImage = aniListImageUrl ?: malImageUrl
                    if (displayImage != null) {
                        AsyncImage(
                            model = displayImage,
                            contentDescription = title,
                            modifier = Modifier
                                .size(width = 54.dp, height = 80.dp)
                                .clip(RoundedCornerShape(10.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = PrimaryAccent.copy(alpha = 0.18f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "Anime Tracker",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = PrimaryAccent,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                    }
                }
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                    NuvioLoadingIndicator()
                }
            } else {
                // Link Services Section
                if (aniListState.mode == AniListConnectionMode.CONNECTED) {
                    if (aniListId != null) {
                        TrackedItemPreview(
                            label = "AniList",
                            title = aniListTitle ?: "Linked",
                            imageUrl = aniListImageUrl,
                            onOpen = { uriHandler.openUri("https://anilist.co/anime/$aniListId") },
                            onChange = { searchMode = "AniList"; showSearchModal = true },
                            onUntrack = {
                                aniListId = null
                                scope.launch {
                                    AnimeTrackerMappingStorage.removeAniListOverride(contentId)
                                }
                            },
                            onDelete = {
                                val currentId = aniListId
                                val currentEntryId = aniListEntryId
                                aniListId = null
                                scope.launch {
                                    AnimeTrackerMappingStorage.removeAniListOverride(contentId)
                                    if (currentId != null && currentEntryId != null) {
                                        val token = AniListAuthRepository.getAccessToken()
                                        if (!token.isNullOrBlank()) {
                                            AniListApiClient.deleteEntry(token, currentEntryId)
                                        }
                                    }
                                }
                            }
                        )
                    } else {
                        OutlinedButton(
                            onClick = { searchMode = "AniList"; showSearchModal = true },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AniListBrandColor),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AniListBrandColor.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Link AniList Account", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (malState.mode == MalConnectionMode.CONNECTED) {
                    if (malId != null) {
                        TrackedItemPreview(
                            label = "MyAnimeList",
                            title = malTitle ?: "Linked",
                            imageUrl = malImageUrl,
                            onOpen = { uriHandler.openUri("https://myanimelist.net/anime/$malId") },
                            onChange = { searchMode = "MAL"; showSearchModal = true },
                            onUntrack = {
                                malId = null
                                scope.launch {
                                    AnimeTrackerMappingStorage.removeMalOverride(contentId)
                                }
                            },
                            onDelete = {
                                val currentId = malId
                                malId = null
                                scope.launch {
                                    AnimeTrackerMappingStorage.removeMalOverride(contentId)
                                    if (currentId != null) {
                                        val token = MalAuthRepository.getAccessToken()
                                        if (!token.isNullOrBlank()) {
                                            MalApiClient.deleteEntry(token, currentId)
                                        }
                                    }
                                }
                            }
                        )
                    } else {
                        OutlinedButton(
                            onClick = { searchMode = "MAL"; showSearchModal = true },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MalBrandColor),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MalBrandColor.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Link MyAnimeList Account", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (simklState.mode == com.nuvio.app.features.simkl.SimklConnectionMode.CONNECTED) {
                    if (simklId != null) {
                        TrackedItemPreview(
                            label = "Simkl",
                            title = simklTitle ?: "Linked",
                            imageUrl = simklImageUrl,
                            onOpen = { uriHandler.openUri("https://simkl.com/shows/$simklId") },
                            onChange = { searchMode = "Simkl"; showSearchModal = true },
                            onUntrack = {
                                simklId = null
                                simklTitle = null
                                simklImageUrl = null
                                scope.launch {
                                    AnimeTrackerMappingStorage.saveSimklOverride(contentId, "UNTRACKED")
                                }
                            },
                            onDelete = {
                                val currentId = simklId
                                simklId = null
                                simklTitle = null
                                simklImageUrl = null
                                scope.launch {
                                    AnimeTrackerMappingStorage.saveSimklOverride(contentId, "UNTRACKED")
                                }
                            }
                        )
                    } else {
                        OutlinedButton(
                            onClick = { searchMode = "Simkl"; showSearchModal = true },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00C755)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00C755).copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Link Simkl Account", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (aniListState.mode != AniListConnectionMode.CONNECTED &&
                    malState.mode != MalConnectionMode.CONNECTED &&
                    simklState.mode != com.nuvio.app.features.simkl.SimklConnectionMode.CONNECTED
                ) {
                    ProSectionCard {
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 16.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = "Please connect AniList, MyAnimeList, or Simkl in Settings to track your progress.",
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }
                } else {
                    // AniList Section Card
                    if (aniListState.mode == AniListConnectionMode.CONNECTED && aniListId != null) {
                        ProSectionCard {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    Text("AniList Tracking", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = AniListBrandColor)
                                    Spacer(modifier = Modifier.weight(1f))
                                    IconToggleButton(
                                        checked = isAniListFavourite,
                                        onCheckedChange = { checked ->
                                            isAniListFavourite = checked
                                            scope.launch {
                                                val token = AniListAuthRepository.getAccessToken()
                                                if (!token.isNullOrBlank()) {
                                                    val success = AniListApiClient.toggleFavourite(token, aniListId!!)
                                                    if (!success) isAniListFavourite = !checked
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (isAniListFavourite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                            contentDescription = "Favourite",
                                            tint = if (isAniListFavourite) Color(0xFFFF4757) else TextSecondary
                                        )
                                    }
                                }

                                Text("Status", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                                Box {
                                    OutlinedButton(
                                        onClick = { aniListStatusExpanded = true },
                                        modifier = Modifier.fillMaxWidth().height(48.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = OledSheetBg,
                                            contentColor = TextPrimary
                                        ),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, OledCardBorder),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text(aniListStatus, fontWeight = FontWeight.Medium)
                                        Spacer(modifier = Modifier.weight(1f))
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = TextSecondary)
                                    }
                                    DropdownMenu(
                                        expanded = aniListStatusExpanded,
                                        onDismissRequest = { aniListStatusExpanded = false }
                                    ) {
                                        aniListStatuses.forEach { s ->
                                            DropdownMenuItem(
                                                text = { Text(s) },
                                                onClick = {
                                                    aniListStatus = s
                                                    if (s == "Completed" && maxEpisodes < 2000) {
                                                        aniListProgress = maxEpisodes.toFloat()
                                                    }
                                                    aniListStatusExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }

                                // Progress Controls
                                val maxProgVal = if (maxEpisodes > 0 && maxEpisodes != 2000) maxEpisodes.toFloat() else 500f
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    Text("Progress", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                                    Spacer(modifier = Modifier.weight(1f))
                                    Text(
                                        text = "${aniListProgress.roundToInt()} / ${if (maxEpisodes > 0 && maxEpisodes != 2000) maxEpisodes else "?"}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = AniListBrandColor
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    IconButton(
                                        onClick = { if (aniListProgress > 0) aniListProgress -= 1f },
                                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(OledSheetBg)
                                    ) {
                                        Icon(Icons.Default.Remove, contentDescription = "-1", tint = TextPrimary)
                                    }
                                    Slider(
                                        value = aniListProgress.coerceIn(0f, maxProgVal),
                                        onValueChange = { aniListProgress = it.roundToInt().toFloat() },
                                        valueRange = 0f..maxProgVal,
                                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                                        colors = SliderDefaults.colors(
                                            thumbColor = AniListBrandColor,
                                            activeTrackColor = AniListBrandColor
                                        )
                                    )
                                    IconButton(
                                        onClick = { aniListProgress += 1f },
                                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(OledSheetBg)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "+1", tint = TextPrimary)
                                    }
                                }

                                // Score Controls
                                val format = aniListState.scoreFormat
                                val scoreRange: ClosedFloatingPointRange<Float>
                                val scoreSteps: Int
                                val scoreText: String
                                when (format) {
                                    "POINT_100" -> {
                                        scoreRange = 0f..100f
                                        scoreSteps = 99
                                        scoreText = if (aniListScore == 0f) "Unrated" else "${aniListScore.roundToInt()}"
                                    }
                                    "POINT_10_DECIMAL" -> {
                                        scoreRange = 0f..10f
                                        scoreSteps = 99
                                        val ds = (aniListScore * 10).roundToInt()
                                        scoreText = if (aniListScore == 0f) "Unrated" else "${ds / 10}.${ds % 10}"
                                    }
                                    "POINT_5" -> {
                                        scoreRange = 0f..5f
                                        scoreSteps = 4
                                        scoreText = if (aniListScore == 0f) "Unrated" else "${aniListScore.roundToInt()} Stars"
                                    }
                                    "POINT_3" -> {
                                        scoreRange = 0f..3f
                                        scoreSteps = 2
                                        scoreText = if (aniListScore == 0f) "Unrated" else when(aniListScore.roundToInt()) {
                                            1 -> ":("
                                            2 -> ":|"
                                            3 -> ":)"
                                            else -> "Unrated"
                                        }
                                    }
                                    else -> {
                                        scoreRange = 0f..10f
                                        scoreSteps = 99
                                        val ds = (aniListScore * 10).roundToInt()
                                        scoreText = if (aniListScore == 0f) "Unrated" else "${ds / 10}.${ds % 10}"
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    Text("Score", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                                    Spacer(modifier = Modifier.weight(1f))
                                    Text(scoreText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = AniListBrandColor)
                                }
                                Slider(
                                    value = aniListScore.coerceIn(scoreRange.start, scoreRange.endInclusive),
                                    onValueChange = { aniListScore = it },
                                    valueRange = scoreRange,
                                    steps = scoreSteps,
                                    colors = SliderDefaults.colors(
                                        thumbColor = AniListBrandColor,
                                        activeTrackColor = AniListBrandColor
                                    )
                                )
                            }
                        }
                    }

                    // MyAnimeList Section Card
                    if (malState.mode == MalConnectionMode.CONNECTED && malId != null) {
                        ProSectionCard {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                Text("MyAnimeList Tracking", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MalBrandColor)

                                Text("Status", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                                Box {
                                    OutlinedButton(
                                        onClick = { malStatusExpanded = true },
                                        modifier = Modifier.fillMaxWidth().height(48.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = OledSheetBg,
                                            contentColor = TextPrimary
                                        ),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, OledCardBorder),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text(malStatus, fontWeight = FontWeight.Medium)
                                        Spacer(modifier = Modifier.weight(1f))
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = TextSecondary)
                                    }
                                    DropdownMenu(
                                        expanded = malStatusExpanded,
                                        onDismissRequest = { malStatusExpanded = false }
                                    ) {
                                        malStatuses.forEach { s ->
                                            DropdownMenuItem(
                                                text = { Text(s) },
                                                onClick = {
                                                    malStatus = s
                                                    if (s == "Completed" && maxEpisodes < 2000) {
                                                        malProgress = maxEpisodes.toFloat()
                                                    }
                                                    malStatusExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }

                                // MAL Progress
                                val maxMalVal = if (maxEpisodes > 0 && maxEpisodes != 2000) maxEpisodes.toFloat() else 500f
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    Text("Progress", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                                    Spacer(modifier = Modifier.weight(1f))
                                    Text(
                                        text = "${malProgress.roundToInt()} / ${if (maxEpisodes > 0 && maxEpisodes != 2000) maxEpisodes else "?"}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MalBrandColor
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    IconButton(
                                        onClick = { if (malProgress > 0) malProgress -= 1f },
                                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(OledSheetBg)
                                    ) {
                                        Icon(Icons.Default.Remove, contentDescription = "-1", tint = TextPrimary)
                                    }
                                    Slider(
                                        value = malProgress.coerceIn(0f, maxMalVal),
                                        onValueChange = { malProgress = it.roundToInt().toFloat() },
                                        valueRange = 0f..maxMalVal,
                                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                                        colors = SliderDefaults.colors(
                                            thumbColor = MalBrandColor,
                                            activeTrackColor = MalBrandColor
                                        )
                                    )
                                    IconButton(
                                        onClick = { malProgress += 1f },
                                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(OledSheetBg)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "+1", tint = TextPrimary)
                                    }
                                }

                                // MAL Score
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    Text("Score", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                                    Spacer(modifier = Modifier.weight(1f))
                                    Text(
                                        text = if (malScore == 0f) "Unrated" else "${malScore.roundToInt()} / 10",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MalBrandColor
                                    )
                                }
                                Slider(
                                    value = malScore.coerceIn(0f, 10f),
                                    onValueChange = { malScore = it.roundToInt().toFloat() },
                                    valueRange = 0f..10f,
                                    steps = 9,
                                    colors = SliderDefaults.colors(
                                        thumbColor = MalBrandColor,
                                        activeTrackColor = MalBrandColor
                                    )
                                )
                            }
                        }
                    }



                        // EaZy Nuvio+ Start — Simkl Section Card
                        if (simklId != null && simklState.mode == com.nuvio.app.features.simkl.SimklConnectionMode.CONNECTED) {
                            val simklBrandColor = Color(0xFF00C755)
                            ProSectionCard {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                        Text("Simkl Tracking", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = simklBrandColor)
                                        Spacer(modifier = Modifier.weight(1f))
                                        TextButton(
                                            onClick = {
                                                simklId = null
                                                simklTitle = null
                                                simklImageUrl = null
                                                scope.launch {
                                                    AnimeTrackerMappingStorage.saveSimklOverride(contentId, "UNTRACKED")
                                                }
                                            },
                                            colors = ButtonDefaults.textButtonColors(contentColor = TextSecondary)
                                        ) {
                                            Text("Untrack")
                                        }
                                    }

                                    Text("Status", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                                    Box {
                                        OutlinedButton(
                                            onClick = { simklStatusExpanded = true },
                                            modifier = Modifier.fillMaxWidth().height(48.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                containerColor = OledSheetBg,
                                                contentColor = TextPrimary
                                            ),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, OledCardBorder),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Text(simklStatus, fontWeight = FontWeight.Medium)
                                            Spacer(modifier = Modifier.weight(1f))
                                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = TextSecondary)
                                        }
                                        DropdownMenu(
                                            expanded = simklStatusExpanded,
                                            onDismissRequest = { simklStatusExpanded = false }
                                        ) {
                                            simklStatuses.forEach { s ->
                                                DropdownMenuItem(
                                                    text = { Text(s) },
                                                    onClick = {
                                                        simklStatus = s
                                                        if (s == "Completed" && maxEpisodes < 2000 && maxEpisodes > 0) {
                                                            simklProgress = maxEpisodes.toFloat()
                                                        }
                                                        simklStatusExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    // Watched Episodes Controls
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                        Text("Watched Episodes", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                                        Spacer(modifier = Modifier.weight(1f))
                                        Text(
                                            text = "${simklProgress.roundToInt()} / ${if (maxEpisodes > 0 && maxEpisodes != 2000) maxEpisodes else "?"}",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = simklBrandColor
                                        )
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                        IconButton(
                                            onClick = { if (simklProgress > 0) simklProgress -= 1f },
                                            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(OledSheetBg)
                                        ) {
                                            Icon(Icons.Default.Remove, contentDescription = "-1", tint = TextPrimary)
                                        }
                                        val maxSimklVal = if (maxEpisodes > 0 && maxEpisodes != 2000) maxEpisodes.toFloat() else 500f
                                        Slider(
                                            value = simklProgress.coerceIn(0f, maxSimklVal),
                                            onValueChange = { simklProgress = it.roundToInt().toFloat() },
                                            valueRange = 0f..maxSimklVal,
                                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                                            colors = SliderDefaults.colors(thumbColor = simklBrandColor, activeTrackColor = simklBrandColor)
                                        )
                                        IconButton(
                                            onClick = { if (simklProgress < maxSimklVal) simklProgress += 1f },
                                            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(OledSheetBg)
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = "+1", tint = TextPrimary)
                                        }
                                    }

                                    // Rating Controls
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                        Text("Rating (1-10)", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                                        Spacer(modifier = Modifier.weight(1f))
                                        Text(
                                            text = if (simklScore == 0f) "Unrated" else "${simklScore.roundToInt()} / 10",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = simklBrandColor
                                        )
                                    }
                                    Slider(
                                        value = simklScore.coerceIn(0f, 10f),
                                        onValueChange = { simklScore = it.roundToInt().toFloat() },
                                        valueRange = 0f..10f,
                                        steps = 9,
                                        colors = SliderDefaults.colors(thumbColor = simklBrandColor, activeTrackColor = simklBrandColor)
                                    )

                                    // Simkl Memo / Note Section
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("Simkl Memo / Note", style = MaterialTheme.typography.labelMedium, color = TextSecondary)

                                        OutlinedTextField(
                                            value = simklMemo,
                                            onValueChange = { newMemo ->
                                                if (newMemo.length <= 140) {
                                                    simklMemo = newMemo
                                                }
                                            },
                                            placeholder = { Text("Add personal note (max 140 chars)", color = TextSecondary) },
                                            modifier = Modifier.fillMaxWidth(),
                                            maxLines = 3,
                                            shape = RoundedCornerShape(10.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedContainerColor = OledSheetBg,
                                                unfocusedContainerColor = OledSheetBg,
                                                focusedBorderColor = simklBrandColor,
                                                unfocusedBorderColor = OledCardBorder,
                                                focusedTextColor = TextPrimary,
                                                unfocusedTextColor = TextPrimary
                                            )
                                        )

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            FilterChip(
                                                selected = isPrivateMemo,
                                                onClick = { isPrivateMemo = !isPrivateMemo },
                                                label = { Text(if (isPrivateMemo) "Private Memo" else "Public Memo") },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = simklBrandColor.copy(alpha = 0.2f),
                                                    selectedLabelColor = simklBrandColor,
                                                    containerColor = OledSheetBg,
                                                    labelColor = TextSecondary
                                                )
                                            )
                                            Spacer(modifier = Modifier.weight(1f))
                                            Text(
                                                text = "${simklMemo.length} / 140",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = TextSecondary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        // EaZy Nuvio+ End

                        // Dates & Rewatches Card (if AniList or MAL is tracking something)
                        if ((aniListState.mode == AniListConnectionMode.CONNECTED && aniListId != null) ||
                            (malState.mode == MalConnectionMode.CONNECTED && malId != null)) {
                            ProSectionCard {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    Text("Dates & Notes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)

                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            onClick = { showStartDatePicker = true },
                                            modifier = Modifier.weight(1f).height(46.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(containerColor = OledSheetBg, contentColor = TextPrimary),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, OledCardBorder),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp), tint = PrimaryAccent)
                                            Spacer(Modifier.width(6.dp))
                                            Text(if (startDateMillis != null) Instant.fromEpochMilliseconds(startDateMillis!!).toLocalDateTime(TimeZone.UTC).date.toString() else "Start Date", fontSize = MaterialTheme.typography.bodySmall.fontSize)
                                        }
                                        OutlinedButton(
                                            onClick = { showFinishDatePicker = true },
                                            modifier = Modifier.weight(1f).height(46.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(containerColor = OledSheetBg, contentColor = TextPrimary),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, OledCardBorder),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp), tint = PrimaryAccent)
                                            Spacer(Modifier.width(6.dp))
                                            Text(if (finishDateMillis != null) Instant.fromEpochMilliseconds(finishDateMillis!!).toLocalDateTime(TimeZone.UTC).date.toString() else "Finish Date", fontSize = MaterialTheme.typography.bodySmall.fontSize)
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                        Text("Total Rewatches", style = MaterialTheme.typography.bodyMedium, color = TextSecondary, modifier = Modifier.weight(1f))
                                        IconButton(onClick = { if (totalRewatches > 0) totalRewatches-- }, modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(OledSheetBg)) {
                                            Icon(Icons.Default.Remove, contentDescription = "-", tint = TextPrimary)
                                        }
                                        Text("$totalRewatches", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.padding(horizontal = 12.dp))
                                        IconButton(onClick = { totalRewatches++ }, modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(OledSheetBg)) {
                                            Icon(Icons.Default.Add, contentDescription = "+", tint = TextPrimary)
                                        }
                                    }

                                    OutlinedTextField(
                                        value = notes,
                                        onValueChange = { notes = it },
                                        label = { Text("Notes", color = TextSecondary) },
                                        modifier = Modifier.fillMaxWidth(),
                                        maxLines = 3,
                                        shape = RoundedCornerShape(10.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor = OledSheetBg,
                                            unfocusedContainerColor = OledSheetBg,
                                            focusedBorderColor = PrimaryAccent,
                                            unfocusedBorderColor = OledCardBorder,
                                            focusedTextColor = TextPrimary,
                                            unfocusedTextColor = TextPrimary
                                        )
                                    )
                                }
                            }

                            // AniList Advanced Accordion
                            if (aniListId != null && aniListState.mode == AniListConnectionMode.CONNECTED) {
                                var anilistExpanded by remember { mutableStateOf(false) }
                                ProSectionCard(modifier = Modifier.clickable { anilistExpanded = !anilistExpanded }) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                            Text("AniList Advanced Options", style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.Bold)
                                            Spacer(modifier = Modifier.weight(1f))
                                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = TextSecondary)
                                        }
                                        if (anilistExpanded) {
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                FilterChip(
                                                    selected = isPrivate,
                                                    onClick = { isPrivate = !isPrivate },
                                                    label = { Text("Private") }
                                                )
                                                FilterChip(
                                                    selected = hideFromStatusLists,
                                                    onClick = { hideFromStatusLists = !hideFromStatusLists },
                                                    label = { Text("Hide from status lists") }
                                                )
                                            }

                                            if (aniListState.advancedScoringEnabled) {
                                                val scoreItems = listOf(
                                                    "Story" to storyScore,
                                                    "Characters" to charScore,
                                                    "Visuals" to visualScore,
                                                    "Audio" to audioScore,
                                                    "Enjoyment" to enjoymentScore
                                                )
                                                scoreItems.forEachIndexed { i, (label, value) ->
                                                    val v = (value * 10).roundToInt()
                                                    val displayVal = "${v / 10}.${v % 10}"
                                                    Text("$label: ${if(value == 0f) "Unrated" else displayVal}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                                    Slider(
                                                        value = value,
                                                        onValueChange = {
                                                            when(i) {
                                                                0 -> storyScore = it
                                                                1 -> charScore = it
                                                                2 -> visualScore = it
                                                                3 -> audioScore = it
                                                                4 -> enjoymentScore = it
                                                            }
                                                        },
                                                        valueRange = 0f..10f,
                                                        steps = 99,
                                                        colors = SliderDefaults.colors(thumbColor = AniListBrandColor, activeTrackColor = AniListBrandColor)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // MAL Advanced Accordion
                            if (malId != null && malState.mode == MalConnectionMode.CONNECTED) {
                                var malExpanded by remember { mutableStateOf(false) }
                                ProSectionCard(modifier = Modifier.clickable { malExpanded = !malExpanded }) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                            Text("MyAnimeList Advanced Options", style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.Bold)
                                            Spacer(modifier = Modifier.weight(1f))
                                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = TextSecondary)
                                        }
                                        if (malExpanded) {
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Text("Priority", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                listOf("Low", "Medium", "High").forEachIndexed { i, p ->
                                                    FilterChip(selected = priority == i, onClick = { priority = i }, label = { Text(p) })
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text("Rewatch Value", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                listOf("Very Low", "Low", "Medium", "High", "Very High").forEachIndexed { i, p ->
                                                    FilterChip(selected = rewatchValue == i + 1, onClick = { rewatchValue = i + 1 }, label = { Text(p) })
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                    }

                }

                // Sticky Action Bar - Save Progress
                Button(
                    onClick = {
                        val currentAniId = aniListId
                        val currentMalId = malId
                        val currentSimklId = simklId
                        onDismiss()
                        CoroutineScope(Dispatchers.Default).launch {
                            val startLd = startDateMillis?.let { Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC).date }
                            val finishLd = finishDateMillis?.let { Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC).date }

                            if (currentAniId != null && aniListState.mode == AniListConnectionMode.CONNECTED) {
                                        AnimeTrackerMappingStorage.saveAniListOverride(contentId, currentAniId)
                                        val token = AniListAuthRepository.getAccessToken()
                                        if (!token.isNullOrBlank()) {
                                            val aniStatus = when (aniListStatus) {
                                                "Watching" -> "CURRENT"
                                                "Completed" -> "COMPLETED"
                                                "On Hold", "Paused" -> "PAUSED"
                                                "Dropped" -> "DROPPED"
                                                "Plan to Watch" -> "PLANNING"
                                                "Rewatching" -> "REPEATING"
                                                else -> "CURRENT"
                                            }
                                            val advancedScoresList = listOf(storyScore, charScore, visualScore, audioScore, enjoymentScore)
                                                .map { (kotlin.math.round(it.toDouble() * 10.0) / 10.0) }

                                            AniListApiClient.saveProgress(
                                                accessToken = token,
                                                mediaId = currentAniId,
                                                progress = aniListProgress.roundToInt(),
                                                status = aniStatus,
                                                score = if (aniListScore > 0f) aniListScore.toDouble() else 0.0,
                                                repeat = totalRewatches,
                                                private = isPrivate,
                                                notes = notes,
                                                hiddenFromStatusLists = hideFromStatusLists,
                                                startedAt = startLd?.let { FuzzyDate(it.year, it.monthNumber, it.dayOfMonth) },
                                                completedAt = finishLd?.let { FuzzyDate(it.year, it.monthNumber, it.dayOfMonth) },
                                                clearStartDate = startLd == null,
                                                clearFinishDate = finishLd == null,
                                                advancedScores = advancedScoresList
                                            )
                                        }
                                    }
                                    if (currentMalId != null && malState.mode == MalConnectionMode.CONNECTED) {
                                        AnimeTrackerMappingStorage.saveMalOverride(contentId, currentMalId)
                                        val token = MalAuthRepository.getAccessToken()
                                        if (!token.isNullOrBlank()) {
                                            val malStatusString = when (malStatus) {
                                                "Watching" -> "watching"
                                                "Completed" -> "completed"
                                                "On Hold" -> "on_hold"
                                                "Dropped" -> "dropped"
                                                "Plan to Watch" -> "plan_to_watch"
                                                "Rewatching" -> "watching"
                                                else -> "watching"
                                            }
                                            MalApiClient.saveProgress(
                                                accessToken = token,
                                                animeId = currentMalId,
                                                numWatchedEpisodes = malProgress.roundToInt(),
                                                status = malStatusString,
                                                score = if (malScore > 0f) malScore.toDouble() else 0.0,
                                                startDate = startLd?.toString() ?: "",
                                                finishDate = finishLd?.toString() ?: "",
                                                numTimesRewatched = totalRewatches,
                                                comments = notes,
                                                priority = priority,
                                                rewatchValue = rewatchValue
                                            )
                                        }
                                    }
                                    // EaZy Nuvio+ Start — Simkl Save Progress
                                    if (currentSimklId != null && simklState.mode == com.nuvio.app.features.simkl.SimklConnectionMode.CONNECTED) {
                                        AnimeTrackerMappingStorage.saveSimklOverride(contentId, currentSimklId)
                                         val token = com.nuvio.app.features.simkl.SimklAuthRepository.authorizedAccessToken()
                                        if (!token.isNullOrBlank()) {
                                            val simklStatusString = when (simklStatus) {
                                                "Watching" -> "watching"
                                                "Plan to Watch" -> "plantowatch"
                                                "Completed" -> "completed"
                                                "On Hold" -> "hold"
                                                "Dropped" -> "dropped"
                                                else -> "watching"
                                            }
                                            com.nuvio.app.features.simkl.SimklSearchClient.saveSimklProgress(
                                                accessToken = token,
                                                simklId = currentSimklId,
                                                status = simklStatusString,
                                                score = if (simklScore > 0f) simklScore.roundToInt() else null,
                                                progress = simklProgress.roundToInt(),
                                                memo = simklMemo,
                                                isPrivate = isPrivateMemo
                                            )
                                            com.nuvio.app.features.simkl.SimklSyncRepository.refreshAsync(
                                                com.nuvio.app.features.tracking.TrackingRefreshIntent.USER_INITIATED
                                            )
                                        }
                                    }
                                    // EaZy Nuvio+ End
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Save Progress", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                        }
                    }
                }
            }
        }

@Composable
private fun ProSectionCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(OledCardBg)
            .border(1.dp, OledCardBorder, RoundedCornerShape(16.dp))
    ) {
        content()
    }
}

@Composable
private fun TrackedItemPreview(
    label: String,
    title: String,
    imageUrl: String?,
    onOpen: () -> Unit,
    onChange: () -> Unit,
    onUntrack: () -> Unit,
    onDelete: () -> Unit
) {
    val brandColor = when {
        label.contains("AniList", ignoreCase = true) -> AniListBrandColor
        label.contains("Simkl", ignoreCase = true) -> Color(0xFF00C755)
        else -> MalBrandColor
    }
    ProSectionCard {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = brandColor.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = brandColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (imageUrl != null) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = title,
                        modifier = Modifier.size(width = 48.dp, height = 72.dp).clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.width(12.dp))
                }
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onOpen,
                    modifier = Modifier.weight(1f).height(42.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                    border = androidx.compose.foundation.BorderStroke(1.dp, OledCardBorder),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Open")
                }
                TextButton(
                    onClick = onChange,
                    modifier = Modifier.weight(1f).height(42.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = TextSecondary)
                ) {
                    Text("Change")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = onUntrack,
                    modifier = Modifier.weight(1f).height(42.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = TextSecondary)
                ) {
                    Text("Untrack")
                }
                TextButton(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f).height(42.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFFF4757))
                ) {
                    Text("Delete")
                }
            }
        }
    }
}

@Composable
private fun SearchItemCard(
    result: SearchResult,
    onClick: () -> Unit
) {
    ProSectionCard(
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(modifier = Modifier.padding(12.dp)) {
            if (result.imageUrl != null) {
                AsyncImage(
                    model = result.imageUrl,
                    contentDescription = result.title,
                    modifier = Modifier.size(width = 72.dp, height = 108.dp).clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(12.dp))
            }
            Column {
                Text(result.title, style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (result.type != null) Text("Type: ${result.type}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                if (result.startDate != null) Text("Started: ${result.startDate}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                if (result.status != null) Text("Status: ${result.status}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                if (result.score != null) Text("Score: ${result.score}", style = MaterialTheme.typography.bodySmall, color = AniListBrandColor)
                if (!result.description.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        result.description.replace(Regex("<.*?>"), "").replace("\n", " "),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
