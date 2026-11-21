package com.example.presentation.screens.intruders

import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.R
import com.example.ui.components.Icon3DBadge
import com.example.ui.components.Tactile3DCard
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface IntruderContentState {
    data object Loading : IntruderContentState
    data object Empty : IntruderContentState
    data class Loaded(
        val records: List<IntruderRecord>,
        val selectedAppFilter: String? = null
    ) : IntruderContentState
}

sealed interface IntruderModalState {
    data object None : IntruderModalState
    data class PreviewRecord(val record: IntruderRecord) : IntruderModalState
    data object ConfirmClearAll : IntruderModalState
}

@Composable
private fun TargetAppIconBadge(
    packageName: String,
    isUninstallProtection: Boolean,
    packageManager: PackageManager,
    size: Dp = 28.dp
) {
    var iconBitmap by remember(packageName) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(packageName) {
        iconBitmap = if (packageName.isNotBlank()) {
            withContext(Dispatchers.IO) {
                try {
                    packageManager.getApplicationIcon(packageName)
                        .toBitmap(width = 96, height = 96)
                        .asImageBitmap()
                } catch (_: Exception) {
                    null
                }
            }
        } else {
            null
        }
    }

    if (iconBitmap != null && !isUninstallProtection) {
        Image(
            bitmap = iconBitmap!!,
            contentDescription = "Target App Icon",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, PureWhite.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
        )
    } else {
        Icon3DBadge(
            icon = if (isUninstallProtection) Icons.Default.AdminPanelSettings else Icons.Default.Lock,
            contentDescription = null,
            size = size,
            accentStart = if (isUninstallProtection) CrimsonAlert else ElectricPurple,
            accentEnd = if (isUninstallProtection) Color(0xFF881337) else DeepViolet
        )
    }
}

@Composable
private fun IntruderVideoThumbnail(
    videoFile: java.io.File,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    var thumbnail by remember(videoFile.absolutePath) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(videoFile.absolutePath) {
        thumbnail = withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(videoFile.absolutePath)
                retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.asImageBitmap()
            } catch (_: Exception) {
                null
            } finally {
                try {
                    retriever.release()
                } catch (_: Exception) {
                }
            }
        }
    }

    if (thumbnail != null) {
        Image(
            bitmap = thumbnail!!,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    } else {
        Box(
            modifier = modifier.background(
                Brush.linearGradient(
                    listOf(Color(0xFF281038), Color(0xFF140A24), Color(0xFF230E2C))
                )
            ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Videocam,
                contentDescription = contentDescription,
                tint = NeonViolet.copy(alpha = 0.45f),
                modifier = Modifier.size(44.dp)
            )
        }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun IntruderEmbeddedVideoPlayer(
    videoFile: java.io.File,
    context: Context,
    modifier: Modifier = Modifier
) {
    val exoPlayer = remember(videoFile.absolutePath) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(videoFile)))
            repeatMode = Player.REPEAT_MODE_ONE
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(videoFile.absolutePath) {
        onDispose {
            exoPlayer.release()
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = true
                setShowNextButton(false)
                setShowPreviousButton(false)
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
        },
        modifier = modifier.background(Color.Black)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntruderVaultScreen(context: Context, onBack: () -> Unit) {
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val packageManager = remember(context) { context.packageManager }
    var contentState by remember { mutableStateOf<IntruderContentState>(IntruderContentState.Loading) }
    var modalState by remember { mutableStateOf<IntruderModalState>(IntruderModalState.None) }

    val cardDateFormatter = remember { SimpleDateFormat("MMM dd • hh:mm a", Locale.getDefault()) }
    val fullDateFormatter = remember { SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault()) }

    fun reloadRecords() {
        coroutineScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                IntruderLogStore.loadAllRecords(context)
            }
            contentState = if (loaded.isEmpty()) {
                IntruderContentState.Empty
            } else {
                val previousFilter = (contentState as? IntruderContentState.Loaded)?.selectedAppFilter
                val validFilter = if (previousFilter != null && loaded.any { it.targetAppLabel == previousFilter }) {
                    previousFilter
                } else {
                    null
                }
                IntruderContentState.Loaded(records = loaded, selectedAppFilter = validFilter)
            }
        }
    }

    LaunchedEffect(Unit) {
        reloadRecords()
    }

    BackHandler {
        if (modalState != IntruderModalState.None) {
            modalState = IntruderModalState.None
        } else {
            onBack()
        }
    }

    val totalCount = (contentState as? IntruderContentState.Loaded)?.records?.size ?: 0

    Scaffold(
        containerColor = ObsidianBlack,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ObsidianBlack,
                    titleContentColor = PureWhite
                ),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon3DBadge(
                            icon = Icons.Default.GppMaybe,
                            contentDescription = null,
                            size = 34.dp,
                            accentStart = CrimsonAlert,
                            accentEnd = Color(0xFF881337)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Intruder Vault",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = PureWhite
                            )
                            Text(
                                text = if (totalCount == 1) {
                                    "1 break-in attempt logged"
                                } else {
                                    "$totalCount break-in attempts logged"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (totalCount > 0) CrimsonAlert else NeonViolet
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("intruder_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = PureWhite
                        )
                    }
                },
                actions = {
                    if (totalCount > 0) {
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                modalState = IntruderModalState.ConfirmClearAll
                            },
                            modifier = Modifier.testTag("intruder_clear_all_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Clear All Intruders",
                                tint = CrimsonAlert
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(ObsidianBlack, Color(0xFF130B24), ObsidianBlack)
                    )
                )
                .padding(paddingValues)
        ) {
            when (val state = contentState) {
                is IntruderContentState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = NeonViolet)
                    }
                }

                is IntruderContentState.Empty -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Tactile3DCard(
                            highlighted = true,
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.widthIn(max = 380.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(26.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(86.dp)
                                        .shadow(
                                            elevation = 16.dp,
                                            shape = RoundedCornerShape(24.dp),
                                            ambientColor = ElectricPurple.copy(alpha = 0.55f),
                                            spotColor = ElectricPurple.copy(alpha = 0.65f)
                                        )
                                        .clip(RoundedCornerShape(24.dp))
                                        .border(1.5.dp, NeonViolet.copy(alpha = 0.7f), RoundedCornerShape(24.dp))
                                ) {
                                    Image(
                                        painter = painterResource(id = R.drawable.img_shield_3d),
                                        contentDescription = "All Clear Shield",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                Spacer(modifier = Modifier.height(18.dp))
                                Text(
                                    text = "No Intruders Detected",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = PureWhite,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Whenever someone enters a wrong PIN, Pattern, Password, or Biometric, DASMO LOCK silently captures their front-camera photo (or an 8s video after 3 wrong tries) and logs the exact app they tried to open.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Surface(
                                    shape = CircleShape,
                                    color = NeonEmerald.copy(alpha = 0.14f),
                                    border = BorderStroke(1.dp, NeonEmerald.copy(alpha = 0.6f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.VerifiedUser,
                                            contentDescription = null,
                                            tint = NeonEmerald,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Silent Front-Camera Trap Armed",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = NeonEmerald,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                is IntruderContentState.Loaded -> {
                    val appCounts = remember(state.records) {
                        state.records
                            .groupingBy { it.targetAppLabel }
                            .eachCount()
                            .entries
                            .sortedByDescending { it.value }
                    }
                    val mostTargeted = remember(state.records, appCounts) {
                        val topLabel = appCounts.firstOrNull()?.key
                        state.records.firstOrNull { it.targetAppLabel == topLabel }
                    }
                    val visibleRecords = remember(state.records, state.selectedAppFilter) {
                        if (state.selectedAppFilter == null) {
                            state.records
                        } else {
                            state.records.filter { it.targetAppLabel == state.selectedAppFilter }
                        }
                    }

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item(span = { GridItemSpan(2) }) {
                            IntruderSummaryBanner(
                                totalCount = state.records.size,
                                mostTargeted = mostTargeted,
                                mostTargetedCount = appCounts.firstOrNull()?.value ?: 0,
                                latestTimestampText = cardDateFormatter.format(Date(state.records.first().timestampMillis)),
                                packageManager = packageManager
                            )
                        }

                        if (appCounts.isNotEmpty()) {
                            item(span = { GridItemSpan(2) }) {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    item(key = "filter_all") {
                                        FilterChip(
                                            selected = state.selectedAppFilter == null,
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                contentState = state.copy(selectedAppFilter = null)
                                            },
                                            label = { Text("All (${state.records.size})") },
                                            leadingIcon = if (state.selectedAppFilter == null) {
                                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                            } else null,
                                            modifier = Modifier.testTag("intruder_filter_all")
                                        )
                                    }
                                    items(appCounts, key = { it.key }) { (label, count) ->
                                        val isSelected = state.selectedAppFilter == label
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                contentState = state.copy(
                                                    selectedAppFilter = if (isSelected) null else label
                                                )
                                            },
                                            label = { Text("$label ($count)") },
                                            leadingIcon = if (isSelected) {
                                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                            } else null,
                                            modifier = Modifier.testTag("intruder_filter_${label.lowercase().replace(" ", "_")}")
                                        )
                                    }
                                }
                            }
                        }

                        itemsIndexed(
                            items = visibleRecords,
                            key = { _, rec -> rec.photoFile.absolutePath }
                        ) { index, record ->
                            IntruderEvidenceCard(
                                record = record,
                                index = index,
                                formattedTime = cardDateFormatter.format(Date(record.timestampMillis)),
                                packageManager = packageManager,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    modalState = IntruderModalState.PreviewRecord(record)
                                }
                            )
                        }
                    }
                }
            }

            when (val modal = modalState) {
                is IntruderModalState.None -> Unit

                is IntruderModalState.PreviewRecord -> {
                    IntruderEvidenceDetailDialog(
                        context = context,
                        record = modal.record,
                        formattedFullDate = fullDateFormatter.format(Date(modal.record.timestampMillis)),
                        packageManager = packageManager,
                        onDismiss = { modalState = IntruderModalState.None },
                        onDelete = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            coroutineScope.launch {
                                withContext(Dispatchers.IO) {
                                    IntruderLogStore.deleteRecord(modal.record)
                                }
                                modalState = IntruderModalState.None
                                reloadRecords()
                            }
                        }
                    )
                }

                is IntruderModalState.ConfirmClearAll -> {
                    AlertDialog(
                        onDismissRequest = { modalState = IntruderModalState.None },
                        containerColor = ObsidianSurface,
                        title = {
                            Text(
                                text = "Clear Intruder Evidence?",
                                fontWeight = FontWeight.Bold,
                                color = PureWhite
                            )
                        },
                        text = {
                            Text(
                                text = "This will permanently delete all $totalCount captured intruder photos, videos, and target app logs.",
                                color = TextSecondary
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        withContext(Dispatchers.IO) {
                                            IntruderLogStore.clearAll(context)
                                        }
                                        modalState = IntruderModalState.None
                                        reloadRecords()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CrimsonAlert),
                                modifier = Modifier.testTag("intruder_confirm_clear_btn")
                            ) {
                                Text("Delete All")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { modalState = IntruderModalState.None }) {
                                Text("Cancel", color = LavenderGlow)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun IntruderSummaryBanner(
    totalCount: Int,
    mostTargeted: IntruderRecord?,
    mostTargetedCount: Int,
    latestTimestampText: String,
    packageManager: PackageManager
) {
    Tactile3DCard(
        highlighted = true,
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("intruder_summary_banner")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0xFF2D1230), Color(0xFF1A102E))
                    )
                )
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (mostTargeted != null) {
                TargetAppIconBadge(
                    packageName = mostTargeted.targetPackageName,
                    isUninstallProtection = mostTargeted.isUninstallProtection,
                    packageManager = packageManager,
                    size = 48.dp
                )
            } else {
                Icon3DBadge(
                    icon = Icons.Default.Security,
                    contentDescription = null,
                    size = 48.dp,
                    accentStart = CrimsonAlert,
                    accentEnd = Color(0xFF881337)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "MOST TARGETED APP",
                    style = MaterialTheme.typography.labelSmall,
                    color = CrimsonAlert,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = mostTargeted?.targetAppLabel ?: "Locked App",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = PureWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "$mostTargetedCount of $totalCount attempts • Latest $latestTimestampText",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun IntruderEvidenceCard(
    record: IntruderRecord,
    index: Int,
    formattedTime: String,
    packageManager: PackageManager,
    onClick: () -> Unit
) {
    val isHighRisk = record.isUninstallProtection || record.failedAttemptNumber >= 3
    Tactile3DCard(
        highlighted = isHighRisk,
        shape = RoundedCornerShape(20.dp),
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.72f)
            .testTag("intruder_card_$index")
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (record.isVideo) {
                IntruderVideoThumbnail(
                    videoFile = record.photoFile,
                    contentDescription = "Intruder video targeting ${record.targetAppLabel}",
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                AsyncImage(
                    model = record.photoFile,
                    contentDescription = "Intruder trying to open ${record.targetAppLabel}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Top dark gradient scrim for badge readability
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(52.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.72f), Color.Transparent)
                        )
                    )
            )

            // Top-left attempt & auth method pill
            Surface(
                shape = CircleShape,
                color = if (isHighRisk) {
                    CrimsonAlert.copy(alpha = 0.85f)
                } else {
                    ObsidianBlack.copy(alpha = 0.75f)
                },
                border = BorderStroke(
                    1.dp,
                    if (isHighRisk) PureWhite.copy(alpha = 0.5f) else NeonViolet.copy(alpha = 0.65f)
                ),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
            ) {
                Text(
                    text = "#${record.failedAttemptNumber} • ${record.authMethodLabel}",
                    style = MaterialTheme.typography.labelSmall,
                    color = PureWhite,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }

            if (record.isVideo) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFEC4899).copy(alpha = 0.92f),
                    border = BorderStroke(1.dp, PureWhite.copy(alpha = 0.55f)),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .testTag("intruder_video_badge_$index")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = "Video Evidence",
                            tint = PureWhite,
                            modifier = Modifier.size(12.dp)
                        )
                        if (record.durationSeconds > 0) {
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "${record.durationSeconds}s",
                                style = MaterialTheme.typography.labelSmall,
                                color = PureWhite,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }

                // Centered Play emblem so video evidence is immediately recognizable
                Surface(
                    shape = CircleShape,
                    color = ObsidianBlack.copy(alpha = 0.68f),
                    border = BorderStroke(1.5.dp, PureWhite.copy(alpha = 0.75f)),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play Intruder Video",
                            tint = PureWhite,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            // Bottom gradient overlay with target app icon, app name, and timestamp
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                Color(0xCC0D0917),
                                Color(0xF50B0714)
                            )
                        )
                    )
                    .padding(horizontal = 10.dp, vertical = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TargetAppIconBadge(
                        packageName = record.targetPackageName,
                        isUninstallProtection = record.isUninstallProtection,
                        packageManager = packageManager,
                        size = 30.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (record.isUninstallProtection) "Tried to Uninstall" else "Tried to open",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (record.isUninstallProtection) CrimsonAlert else NeonViolet,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                        Text(
                            text = record.targetAppLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = PureWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("intruder_app_label_$index")
                        )
                        Text(
                            text = formattedTime,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IntruderEvidenceDetailDialog(
    context: Context,
    record: IntruderRecord,
    formattedFullDate: String,
    packageManager: PackageManager,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = ObsidianSurface,
            border = BorderStroke(1.dp, ObsidianBorder),
            tonalElevation = 10.dp,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 420.dp)
                .testTag("intruder_detail_dialog")
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(330.dp)
                        .background(ObsidianBlack)
                ) {
                    if (record.isVideo) {
                        IntruderEmbeddedVideoPlayer(
                            videoFile = record.photoFile,
                            context = context,
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("intruder_detail_video_player")
                        )
                    } else {
                        AsyncImage(
                            model = record.photoFile,
                            contentDescription = "Intruder photo targeting ${record.targetAppLabel}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Surface(
                        shape = CircleShape,
                        color = if (record.isVideo) Color(0xFFEC4899).copy(alpha = 0.9f) else CrimsonAlert.copy(alpha = 0.88f),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (record.isVideo) Icons.Default.Videocam else Icons.Default.Warning,
                                contentDescription = null,
                                tint = PureWhite,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = if (record.isVideo) {
                                    if (record.durationSeconds > 0) "INTRUDER VIDEO • ${record.durationSeconds}s" else "INTRUDER VIDEO"
                                } else {
                                    "INTRUDER CAUGHT"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = PureWhite,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(48.dp)
                            .background(ObsidianBlack.copy(alpha = 0.65f), CircleShape)
                            .testTag("intruder_detail_close_icon")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Evidence Viewer",
                            tint = PureWhite
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Highlighted Target App card
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = ObsidianElevated,
                        border = BorderStroke(
                            1.dp,
                            if (record.isUninstallProtection) CrimsonAlert.copy(alpha = 0.7f) else ElectricPurple.copy(alpha = 0.55f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TargetAppIconBadge(
                                packageName = record.targetPackageName,
                                isUninstallProtection = record.isUninstallProtection,
                                packageManager = packageManager,
                                size = 44.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (record.isUninstallProtection) {
                                        "UNINSTALL SHIELD TRIGGERED"
                                    } else {
                                        "ATTEMPTED TO OPEN"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (record.isUninstallProtection) CrimsonAlert else NeonViolet,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = record.targetAppLabel,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = PureWhite,
                                    modifier = Modifier.testTag("intruder_detail_app_name")
                                )
                                if (record.targetPackageName.isNotBlank()) {
                                    Text(
                                        text = record.targetPackageName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextTertiary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    // Forensic metadata chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DetailInfoChip(
                            icon = Icons.Default.Key,
                            label = "Method",
                            value = record.authMethodLabel,
                            accent = CyberCyan,
                            modifier = Modifier.weight(1f)
                        )
                        DetailInfoChip(
                            icon = Icons.Default.ErrorOutline,
                            label = "Failed Try",
                            value = "Attempt #${record.failedAttemptNumber}",
                            accent = CrimsonAlert,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    DetailInfoChip(
                        icon = Icons.Default.Schedule,
                        label = "Captured At",
                        value = formattedFullDate,
                        accent = NeonViolet,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDelete,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, CrimsonAlert.copy(alpha = 0.7f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CrimsonAlert),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("intruder_detail_delete_btn")
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Delete", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricPurple,
                                contentColor = PureWhite
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("intruder_detail_done_btn")
                        ) {
                            Text("Close", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailInfoChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = ObsidianSurfaceVariant,
        border = BorderStroke(1.dp, ObsidianBorder),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = PureWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
