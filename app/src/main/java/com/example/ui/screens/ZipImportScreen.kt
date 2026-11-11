@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.media3.common.util.UnstableApi::class)
package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import coil.compose.AsyncImage
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import com.example.R
import com.example.core.media.VideoProcessor
import com.example.core.protocol.AppleLivePhotoArchiveHelper
import com.example.data.LivePhotoRecord
import com.example.ui.components.ExoTextureVideoView
import com.example.util.DebugLogManager
import com.example.viewmodel.LivePhotoViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun ZipImportScreen(
    viewModel: LivePhotoViewModel,
    onClose: () -> Unit,
    onPlayRecord: (LivePhotoRecord) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    val isScanningArchive by viewModel.isScanningArchive.collectAsStateWithLifecycle()
    val archiveScanProgress by viewModel.archiveScanProgress.collectAsStateWithLifecycle()
    val archiveScanResult by viewModel.archiveScanResult.collectAsStateWithLifecycle()
    val isBatchImporting by viewModel.isBatchImporting.collectAsStateWithLifecycle()
    val batchProgress by viewModel.batchProgress.collectAsStateWithLifecycle()
    val activeBatchPairs by viewModel.activeBatchPairs.collectAsStateWithLifecycle()
    val archiveItemStatuses by viewModel.archiveItemStatuses.collectAsStateWithLifecycle()
    val completedArchiveRecords by viewModel.completedArchiveRecords.collectAsStateWithLifecycle()
    val isArchiveImportFinished by viewModel.isArchiveImportFinished.collectAsStateWithLifecycle()

    // Default selection: select all Live Photo / video items by default (or all items if archive only has static photos)
    var selectedIds by remember(archiveScanResult) {
        val pairs = archiveScanResult?.pairs.orEmpty()
        val liveCapable = pairs.filter { it.itemKind != AppleLivePhotoArchiveHelper.ArchiveItemKind.STANDALONE_PHOTO }
        val initialSet = if (liveCapable.isNotEmpty()) {
            liveCapable.map { it.id }.toSet()
        } else {
            pairs.map { it.id }.toSet()
        }
        mutableStateOf(initialSet)
    }
    var selectedFilterCategory by remember(archiveScanResult) {
        mutableStateOf("ALL")
    }

    // Dynamic on-demand thumbnail cache so all 555+ items get thumbnails as the user scrolls
    val lazyThumbPaths = remember(archiveScanResult?.archiveUri) {
        mutableStateMapOf<String, String>()
    }
    val thumbLoadSemaphore = remember { Semaphore(2) }

    var previewLoadingItemId by remember { mutableStateOf<String?>(null) }
    var previewJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var activePreviewData by remember { mutableStateOf<AppleLivePhotoArchiveHelper.ArchivePreviewData?>(null) }
    var isPreviewPlaying by remember { mutableStateOf(true) }
    var isPreviewMuted by remember { mutableStateOf(false) }
    var isDirectSaving by remember { mutableStateOf(false) }
    var previewPosMs by remember { mutableLongStateOf(0L) }
    var previewDurMs by remember { mutableLongStateOf(0L) }

    val playerDecoderMode by viewModel.playerDecoderMode.collectAsStateWithLifecycle()

    val previewPlayer = remember(playerDecoderMode) {
        com.example.ui.components.PlayerDecoderConfig.createExoPlayer(context, playerDecoderMode).apply {
            repeatMode = Player.REPEAT_MODE_ONE
            playWhenReady = true
            volume = 1f
        }
    }

    // If a raw .mov inside the ZIP fails direct playback, automatically transcode it to standard MP4 on the fly
    DisposableEffect(previewPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    val d = previewPlayer.duration
                    if (d > 0L) previewDurMs = d
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                val curData = activePreviewData ?: return
                if (curData.videoPath.isNotEmpty() && !curData.videoPath.endsWith("_compat.mp4")) {
                    DebugLogManager.w("ZipImportScreen", "Direct preview playback failed (${error.errorCodeName}); running compatibility transcode")
                    coroutineScope.launch {
                        val compatPath = AppleLivePhotoArchiveHelper.transcodePreviewVideoFallback(context, curData.videoPath)
                        if (compatPath != null && File(compatPath).exists()) {
                            activePreviewData = curData.copy(videoPath = compatPath)
                        }
                    }
                }
            }
        }
        previewPlayer.addListener(listener)
        onDispose {
            previewPlayer.removeListener(listener)
        }
    }

    // Pause inline preview player when starting batch import or leaving the app to home screen
    LaunchedEffect(isBatchImporting, isArchiveImportFinished) {
        if (isBatchImporting || isArchiveImportFinished) {
            previewPlayer.pause()
            isPreviewPlaying = false
        }
    }

    LaunchedEffect(isPreviewMuted) {
        previewPlayer.volume = if (isPreviewMuted) 0f else 1f
    }

    LaunchedEffect(activePreviewData) {
        val data = activePreviewData
        previewPosMs = 0L
        if (data != null && data.videoPath.isNotEmpty() && File(data.videoPath).exists()) {
            val videoFile = File(data.videoPath)
            val preDur = withContext(Dispatchers.IO) {
                VideoProcessor.getVideoDuration(context, Uri.fromFile(videoFile))
            }
            if (preDur > 0L) {
                previewDurMs = preDur
            }
            previewPlayer.setMediaItem(MediaItem.fromUri(Uri.fromFile(videoFile)))
            previewPlayer.prepare()
            previewPlayer.seekTo(0L)
            previewPlayer.playWhenReady = true
            previewPlayer.play()
            isPreviewPlaying = true
        } else {
            previewDurMs = 0L
            previewPlayer.pause()
            isPreviewPlaying = false
        }
    }

    // Poll preview playback progress for the sleek bottom progress bar
    LaunchedEffect(isPreviewPlaying, activePreviewData) {
        val hasVideo = activePreviewData?.videoPath?.isNotEmpty() == true
        while (isPreviewPlaying && hasVideo) {
            previewPosMs = previewPlayer.currentPosition.coerceAtLeast(0L)
            val d = previewPlayer.duration
            if (d > 0L) previewDurMs = d
            delay(50)
        }
    }

    DisposableEffect(lifecycleOwner, previewPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                previewPlayer.pause()
                isPreviewPlaying = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            previewPlayer.release()
        }
    }

    fun triggerPreview(
        item: AppleLivePhotoArchiveHelper.ArchivePairItem,
        openFullscreen: Boolean = false
    ) {
        val scanRes = archiveScanResult ?: return
        if (!openFullscreen && activePreviewData?.item?.id == item.id && previewLoadingItemId == null) {
            if (activePreviewData?.videoPath?.isNotEmpty() == true) {
                isPreviewPlaying = true
                previewPlayer.seekTo(0L)
                previewPlayer.playWhenReady = true
                previewPlayer.play()
            }
            return
        }
        previewJob?.cancel()
        previewPlayer.pause()
        isPreviewPlaying = false
        previewLoadingItemId = item.id
        previewJob = coroutineScope.launch {
            try {
                val data = AppleLivePhotoArchiveHelper.extractArchiveItemForPreview(
                    context = context,
                    archiveUri = scanRes.archiveUri,
                    item = item
                )
                if (data != null) {
                    activePreviewData = data
                    if (data.coverPath.isNotEmpty() && File(data.coverPath).exists()) {
                        lazyThumbPaths[item.id] = data.coverPath
                    }
                    if (openFullscreen && data.videoPath.isNotEmpty()) {
                        previewPlayer.pause()
                        isPreviewPlaying = false
                        onPlayRecord(
                            LivePhotoRecord(
                                id = -1,
                                title = item.baseName,
                                coverPath = data.coverPath,
                                videoPath = data.videoPath,
                                timestamp = System.currentTimeMillis(),
                                isEmbedded = false
                            )
                        )
                    }
                } else {
                    Toast.makeText(context, "无法预览该项", Toast.LENGTH_SHORT).show()
                }
            } catch (_: kotlinx.coroutines.CancellationException) {
            } finally {
                if (previewLoadingItemId == item.id) {
                    previewLoadingItemId = null
                }
            }
        }
    }

    // Automatically load the first item for inline preview once scanning completes
    LaunchedEffect(archiveScanResult) {
        val res = archiveScanResult
        if (res != null && res.pairs.isNotEmpty() && activePreviewData == null) {
            triggerPreview(res.pairs.first(), openFullscreen = false)
        }
    }

    BackHandler {
        previewPlayer.pause()
        onClose()
    }

    val topTitle = when {
        isScanningArchive -> stringResource(R.string.archive_scanning_title)
        isBatchImporting -> stringResource(
            R.string.archive_progress_title,
            batchProgress?.currentIndex ?: 1,
            batchProgress?.totalCount ?: activeBatchPairs.size.coerceAtLeast(1)
        )
        isArchiveImportFinished -> stringResource(R.string.zip_import_finished_title)
        else -> archiveScanResult?.archiveName?.ifBlank { stringResource(R.string.zip_import_title) }
            ?: stringResource(R.string.zip_import_title)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = topTitle,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        previewPlayer.pause()
                        onClose()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding()
        ) {
            when {
                // STAGE 1: Full-Screen Archive Structure Parsing & Reading Flow
                isScanningArchive -> {
                    val scanProg = archiveScanProgress
                    val pct = ((scanProg?.fraction ?: 0.05f) * 100f).toInt().coerceIn(0, 100)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Archive,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = stringResource(R.string.archive_scanning_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(
                                        R.string.archive_scanning_entry,
                                        scanProg?.scannedCount ?: 0,
                                        (scanProg?.totalCount ?: 1).coerceAtLeast(1),
                                        scanProg?.currentEntryName ?: "..."
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                LinearProgressIndicator(
                                    progress = { (scanProg?.fraction ?: 0.05f).coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(10.dp)
                                        .clip(RoundedCornerShape(5.dp))
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "$pct%",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(20.dp))
                                OutlinedButton(
                                    onClick = { viewModel.cancelArchiveScan() },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(stringResource(R.string.btn_cancel), color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }

                // STAGE 3: Full-Screen Unpacking & Synthesis Progress + Completion View
                isBatchImporting || isArchiveImportFinished -> {
                    val prog = batchProgress
                    val pct = if (isArchiveImportFinished) 100 else ((prog?.overallFraction ?: 0f) * 100f).toInt().coerceIn(0, 100)
                    val stepText = when (prog?.stepIndex) {
                        1 -> stringResource(R.string.apple_step_extracting)
                        2 -> stringResource(R.string.apple_step_converting_cover)
                        3 -> stringResource(R.string.apple_step_remuxing_mov)
                        else -> stringResource(R.string.apple_step_packaging_xmp)
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isArchiveImportFinished)
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                            else
                                MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (isArchiveImportFinished) {
                                            stringResource(R.string.zip_batch_done_count, activeBatchPairs.size)
                                        } else {
                                            stringResource(R.string.zip_batch_processing_item, prog?.currentName ?: "...")
                                        },
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (isArchiveImportFinished) {
                                            stringResource(R.string.zip_batch_saved_desc)
                                        } else {
                                            stepText
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "$pct%",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            LinearProgressIndicator(
                                progress = {
                                    if (isArchiveImportFinished) 1f else (prog?.overallFraction ?: 0f).coerceIn(0f, 1f)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(5.dp))
                            )
                        }
                    }

                    val recordsByTitle = remember(completedArchiveRecords) {
                        completedArchiveRecords.associateBy { it.title }
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(activeBatchPairs, key = { it.id }) { pair ->
                            val status = archiveItemStatuses[pair.id] ?: 0
                            val matchedRecord = recordsByTitle[pair.baseName]
                            val statusLabel = when (status) {
                                1 -> stringResource(R.string.apple_step_extracting)
                                2 -> stringResource(R.string.apple_step_converting_cover)
                                3 -> stringResource(R.string.apple_step_remuxing_mov)
                                4 -> stringResource(R.string.apple_step_packaging_xmp)
                                5 -> if (pair.itemKind == AppleLivePhotoArchiveHelper.ArchiveItemKind.STANDALONE_PHOTO)
                                    stringResource(R.string.zip_status_photo_saved)
                                else
                                    stringResource(R.string.zip_status_done_tap_play)
                                -1 -> stringResource(R.string.zip_status_failed)
                                else -> stringResource(R.string.zip_status_waiting)
                            }

                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = matchedRecord != null) {
                                        if (matchedRecord != null) {
                                            onPlayRecord(matchedRecord)
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(54.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color.Black.copy(alpha = 0.25f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        val thumbModel = matchedRecord?.coverPath ?: lazyThumbPaths[pair.id] ?: pair.previewThumbPath
                                        if (thumbModel != null && File(thumbModel).exists()) {
                                            AsyncImage(
                                                model = thumbModel,
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Icon(
                                                imageVector = if (pair.itemKind == AppleLivePhotoArchiveHelper.ArchiveItemKind.STANDALONE_VIDEO)
                                                    Icons.Default.Movie else Icons.Default.Image,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = pair.baseName,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "${pair.formatBadge} · $statusLabel",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = when (status) {
                                                5 -> MaterialTheme.colorScheme.primary
                                                -1 -> MaterialTheme.colorScheme.error
                                                1, 2, 3, 4 -> MaterialTheme.colorScheme.tertiary
                                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    when (status) {
                                        1, 2, 3, 4 -> {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(24.dp),
                                                strokeWidth = 2.5.dp
                                            )
                                        }
                                        5 -> {
                                            if (matchedRecord != null) {
                                                IconButton(onClick = { onPlayRecord(matchedRecord) }) {
                                                    Icon(
                                                        imageVector = Icons.Default.PlayCircleOutline,
                                                        contentDescription = "播放实况",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(28.dp)
                                                    )
                                                }
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                        }
                                        -1 -> {
                                            Icon(
                                                imageVector = Icons.Default.ErrorOutline,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 8.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (isBatchImporting) {
                                OutlinedButton(
                                    onClick = { viewModel.cancelArchiveBatchImport() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.btn_cancel),
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else {
                                Button(
                                    onClick = onClose,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.zip_btn_finish_back),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // STAGE 2: Full-Screen Archive File List Preview + Format Category Filtering + Redesigned Top Stage
                archiveScanResult != null -> {
                    val scanResult = archiveScanResult!!
                    val previewData = activePreviewData

                    val distinctBadges = remember(scanResult.pairs) {
                        scanResult.pairs.groupingBy { it.formatBadge }.eachCount()
                    }

                    val filteredPairs = remember(scanResult.pairs, selectedFilterCategory) {
                        when (selectedFilterCategory) {
                            "ALL" -> scanResult.pairs
                            "KIND_PAIRED" -> scanResult.pairs.filter { it.itemKind == AppleLivePhotoArchiveHelper.ArchiveItemKind.PAIRED_MEDIA }
                            "KIND_VIDEO" -> scanResult.pairs.filter { it.itemKind == AppleLivePhotoArchiveHelper.ArchiveItemKind.STANDALONE_VIDEO }
                            "KIND_PHOTO" -> scanResult.pairs.filter { it.itemKind == AppleLivePhotoArchiveHelper.ArchiveItemKind.STANDALONE_PHOTO }
                            "KIND_EMBEDDED" -> scanResult.pairs.filter { it.itemKind == AppleLivePhotoArchiveHelper.ArchiveItemKind.EMBEDDED_MOTION_PHOTO }
                            else -> scanResult.pairs.filter { it.formatBadge == selectedFilterCategory }
                        }
                    }

                    val allFilteredSelected = remember(filteredPairs, selectedIds) {
                        filteredPairs.isNotEmpty() && filteredPairs.all { selectedIds.contains(it.id) }
                    }

                    // Redesigned Top Media Preview Stage: Ambient blurred backdrop + glassmorphic header/footer + Direct Play & Save
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(22.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF101218))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9.4f)
                                .background(Color(0xFF0E1015))
                                .clickable {
                                    if (previewData != null && previewData.videoPath.isNotEmpty()) {
                                        isPreviewPlaying = !isPreviewPlaying
                                        if (isPreviewPlaying) previewPlayer.play() else previewPlayer.pause()
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (previewData != null) {
                                val backdropModel = previewData.coverPath.ifEmpty {
                                    lazyThumbPaths[previewData.item.id] ?: previewData.item.previewThumbPath ?: ""
                                }

                                // Soft ambient blurred background so portrait 9:16 videos / 3:4 photos don't have harsh black sidebars
                                if (backdropModel.isNotEmpty() && File(backdropModel).exists()) {
                                    AsyncImage(
                                        model = backdropModel,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .scale(1.25f)
                                            .blur(radius = 38.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.48f))
                                    )
                                }

                                // Center sharp media view
                                if (previewData.videoPath.isNotEmpty() && File(previewData.videoPath).exists()) {
                                    ExoTextureVideoView(
                                        exoPlayer = previewPlayer,
                                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else if (backdropModel.isNotEmpty() && File(backdropModel).exists()) {
                                    AsyncImage(
                                        model = backdropModel,
                                        contentDescription = null,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }

                                // Top gradient scrim + clean Header Bar (Title + Direct Save + Full-Screen Play)
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .fillMaxWidth()
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(Color.Black.copy(alpha = 0.72f), Color.Transparent)
                                            )
                                        )
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(7.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        if (previewData.videoPath.isNotEmpty())
                                                            Color(0xFF4ADE80)
                                                        else
                                                            Color(0xFF60A5FA)
                                                    )
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = previewData.item.baseName,
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = Color.White.copy(alpha = 0.18f)
                                            ) {
                                                Text(
                                                    text = previewData.item.formatBadge,
                                                    color = Color.White.copy(alpha = 0.92f),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        // Direct Save Video / Save Photo Button (no Live Photo conversion needed!)
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = Color.White.copy(alpha = 0.20f),
                                                modifier = Modifier.clickable(enabled = !isDirectSaving) {
                                                    isDirectSaving = true
                                                    coroutineScope.launch {
                                                        val msg = AppleLivePhotoArchiveHelper.saveArchiveItemDirectlyToGallery(
                                                            context = context,
                                                            archiveUri = scanResult.archiveUri,
                                                            item = previewData.item
                                                        )
                                                        isDirectSaving = false
                                                        Toast.makeText(
                                                            context,
                                                            msg ?: "保存失败",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    }
                                                }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Download,
                                                        contentDescription = null,
                                                        tint = Color.White,
                                                        modifier = Modifier.size(13.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(3.dp))
                                                    Text(
                                                        text = if (previewData.videoPath.isNotEmpty())
                                                            stringResource(R.string.zip_save_video_direct)
                                                        else
                                                            stringResource(R.string.zip_save_photo_direct),
                                                        color = Color.White,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }

                                            // Full-Screen Play Button (opens full-screen player overlay directly!)
                                            if (previewData.videoPath.isNotEmpty() && File(previewData.videoPath).exists()) {
                                                Surface(
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.88f),
                                                    modifier = Modifier.clickable {
                                                        previewPlayer.pause()
                                                        isPreviewPlaying = false
                                                        onPlayRecord(
                                                            LivePhotoRecord(
                                                                id = -1,
                                                                title = previewData.item.baseName,
                                                                coverPath = previewData.coverPath,
                                                                videoPath = previewData.videoPath,
                                                                timestamp = System.currentTimeMillis(),
                                                                isEmbedded = false
                                                            )
                                                        )
                                                    }
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Fullscreen,
                                                            contentDescription = null,
                                                            tint = Color.White,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(3.dp))
                                                        Text(
                                                            text = stringResource(R.string.zip_fullscreen_play),
                                                            color = Color.White,
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // Bottom gradient scrim + clean playback bar for video items
                                if (previewData.videoPath.isNotEmpty()) {
                                    val progFraction = if (previewDurMs > 0L) {
                                        (previewPosMs.toFloat() / previewDurMs.toFloat()).coerceIn(0f, 1f)
                                    } else 0f

                                    Column(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .fillMaxWidth()
                                            .background(
                                                Brush.verticalGradient(
                                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.76f))
                                                )
                                            )
                                            .padding(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                                    .background(Color.White.copy(alpha = 0.22f))
                                                    .clickable {
                                                        isPreviewPlaying = !isPreviewPlaying
                                                        if (isPreviewPlaying) previewPlayer.play() else previewPlayer.pause()
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = if (isPreviewPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }

                                            // Interactive Progress track (supports tap & horizontal drag seeking)
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(24.dp)
                                                    .pointerInput(previewDurMs) {
                                                        detectTapGestures { offset ->
                                                            if (previewDurMs > 0L && size.width > 0) {
                                                                val frac = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                                                val targetMs = (frac * previewDurMs).toLong()
                                                                previewPosMs = targetMs
                                                                previewPlayer.seekTo(targetMs)
                                                            }
                                                        }
                                                    }
                                                    .pointerInput(previewDurMs) {
                                                        detectHorizontalDragGestures { change, _ ->
                                                            change.consume()
                                                            if (previewDurMs > 0L && size.width > 0) {
                                                                val frac = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                                                                val targetMs = (frac * previewDurMs).toLong()
                                                                previewPosMs = targetMs
                                                                previewPlayer.seekTo(targetMs)
                                                            }
                                                        }
                                                    },
                                                contentAlignment = Alignment.CenterStart
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(4.dp)
                                                        .clip(RoundedCornerShape(2.dp))
                                                        .background(Color.White.copy(alpha = 0.28f))
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxHeight()
                                                            .fillMaxWidth(progFraction)
                                                            .background(Color.White)
                                                    )
                                                }
                                            }

                                            val curSec = "%.1f".format(previewPosMs / 1000f)
                                            val totSec = "%.1f".format(previewDurMs / 1000f)
                                            Text(
                                                text = "${curSec}s / ${totSec}s",
                                                color = Color.White.copy(alpha = 0.9f),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium
                                            )

                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                                    .background(Color.White.copy(alpha = 0.22f))
                                                    .clickable { isPreviewMuted = !isPreviewMuted },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = if (isPreviewMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    // Static photo badge at bottom
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color.Black.copy(alpha = 0.62f),
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .padding(bottom = 8.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.zip_no_paired_video_hint),
                                            color = Color.White.copy(alpha = 0.9f),
                                            fontSize = 10.sp,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            } else if (previewLoadingItemId != null) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(28.dp))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = stringResource(R.string.zip_loading_preview),
                                        color = Color.White.copy(alpha = 0.8f),
                                        fontSize = 12.sp
                                    )
                                }
                            } else {
                                Text(
                                    text = stringResource(R.string.zip_tap_to_preview_hint),
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }

                    // Format Breakdown + Format Category Filter Bar + Select All Header
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        if (scanResult.formatBreakdownSummary.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = stringResource(R.string.archive_contents_summary, scanResult.formatBreakdownSummary),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }

                        // Horizontal Format Category Filter Chips (including unpaired standalone photos!)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val filterAllLabel = stringResource(R.string.zip_filter_all, scanResult.pairs.size)
                            val filterPairedLabel = stringResource(R.string.zip_filter_paired, scanResult.pairedCount)
                            val filterVideoLabel = stringResource(R.string.zip_filter_video, scanResult.standaloneVideoCount)
                            val filterPhotoLabel = stringResource(R.string.zip_filter_photo, scanResult.standalonePhotoCount)
                            val filterEmbeddedLabel = stringResource(R.string.zip_filter_embedded, scanResult.embeddedPhotoCount)

                            val filterOptions = buildList {
                                add("ALL" to filterAllLabel)
                                if (scanResult.pairedCount > 0) {
                                    add("KIND_PAIRED" to filterPairedLabel)
                                }
                                if (scanResult.standaloneVideoCount > 0) {
                                    add("KIND_VIDEO" to filterVideoLabel)
                                }
                                if (scanResult.standalonePhotoCount > 0) {
                                    add("KIND_PHOTO" to filterPhotoLabel)
                                }
                                if (scanResult.embeddedPhotoCount > 0) {
                                    add("KIND_EMBEDDED" to filterEmbeddedLabel)
                                }
                                distinctBadges.forEach { (badge, count) ->
                                    add(badge to "$badge ($count)")
                                }
                            }

                            filterOptions.forEach { (key, label) ->
                                val isSel = selectedFilterCategory == key
                                FilterChip(
                                    selected = isSel,
                                    onClick = { selectedFilterCategory = key },
                                    label = {
                                        Text(
                                            text = label,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                                        )
                                    },
                                    leadingIcon = if (isSel) {
                                        {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(FilterChipDefaults.IconSize)
                                            )
                                        }
                                    } else null,
                                    shape = RoundedCornerShape(10.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.zip_category_selection_summary,
                                    filteredPairs.size,
                                    selectedIds.size
                                ),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            TextButton(
                                onClick = {
                                    val filterIds = filteredPairs.map { it.id }.toSet()
                                    selectedIds = if (allFilteredSelected) {
                                        selectedIds - filterIds
                                    } else {
                                        selectedIds + filterIds
                                    }
                                }
                            ) {
                                Text(
                                    text = if (allFilteredSelected)
                                        stringResource(R.string.zip_deselect_category_all)
                                    else
                                        stringResource(R.string.zip_select_category_all),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Full-Height Scrollable File List with Lazy On-Demand Thumbnail Loading
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredPairs, key = { it.id }) { pair ->
                            val checked = selectedIds.contains(pair.id)
                            val isCurrentPreview = activePreviewData?.item?.id == pair.id
                            val isLoadingPreview = previewLoadingItemId == pair.id
                            val resolvedThumbPath = lazyThumbPaths[pair.id] ?: pair.previewThumbPath

                            // Lazily generate thumbnail on-demand when row enters composition
                            LaunchedEffect(pair.id) {
                                if (resolvedThumbPath == null || !File(resolvedThumbPath).exists()) {
                                    while (previewLoadingItemId != null) {
                                        delay(50)
                                    }
                                    thumbLoadSemaphore.withPermit {
                                        while (previewLoadingItemId != null) {
                                            delay(50)
                                        }
                                        val loaded = AppleLivePhotoArchiveHelper.loadArchiveItemThumbnail(
                                            context = context,
                                            archiveUri = scanResult.archiveUri,
                                            item = pair
                                        )
                                        if (loaded != null) {
                                            lazyThumbPaths[pair.id] = loaded
                                        }
                                    }
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (isCurrentPreview)
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                else
                                    MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (isCurrentPreview) {
                                            Modifier.border(
                                                width = 1.5.dp,
                                                color = MaterialTheme.colorScheme.primary,
                                                shape = RoundedCornerShape(16.dp)
                                            )
                                        } else Modifier
                                    )
                                    .clickable {
                                        triggerPreview(pair, openFullscreen = false)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = checked,
                                        onCheckedChange = { isChecked ->
                                            selectedIds = if (isChecked) {
                                                selectedIds + pair.id
                                            } else {
                                                selectedIds - pair.id
                                            }
                                        }
                                    )

                                    Box(
                                        modifier = Modifier
                                            .size(54.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color.Black.copy(alpha = 0.25f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (resolvedThumbPath != null && File(resolvedThumbPath).exists()) {
                                            AsyncImage(
                                                model = resolvedThumbPath,
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Icon(
                                                imageVector = if (pair.itemKind == AppleLivePhotoArchiveHelper.ArchiveItemKind.STANDALONE_VIDEO)
                                                    Icons.Default.Movie else Icons.Default.Image,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = pair.baseName,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(3.dp))
                                        val sizeKb = pair.totalSizeBytes / 1024L
                                        val sizeText = if (sizeKb >= 1024L) {
                                            "%.1f MB".format(sizeKb / 1024f)
                                        } else if (sizeKb > 0L) {
                                            "$sizeKb KB"
                                        } else ""
                                        val noPairedVideoText = stringResource(R.string.zip_unpaired_subtitle)
                                        val subLabel = when (pair.itemKind) {
                                            AppleLivePhotoArchiveHelper.ArchiveItemKind.STANDALONE_PHOTO ->
                                                if (sizeText.isNotEmpty()) "${pair.formatBadge} · $noPairedVideoText · $sizeText" else "${pair.formatBadge} · $noPairedVideoText"
                                            else ->
                                                if (sizeText.isNotEmpty()) "${pair.formatBadge} · $sizeText" else pair.formatBadge
                                        }
                                        Text(
                                            text = subLabel,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (pair.itemKind == AppleLivePhotoArchiveHelper.ArchiveItemKind.STANDALONE_PHOTO)
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            else
                                                MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    // Direct Full-Screen Play button for videos so user doesn't have to convert to Live Photo
                                    if (pair.itemKind != AppleLivePhotoArchiveHelper.ArchiveItemKind.STANDALONE_PHOTO) {
                                        IconButton(
                                            onClick = {
                                                triggerPreview(pair, openFullscreen = true)
                                            },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Fullscreen,
                                                contentDescription = stringResource(R.string.zip_fullscreen_play),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }

                                    TextButton(
                                        onClick = {
                                            triggerPreview(pair, openFullscreen = false)
                                        }
                                    ) {
                                        if (isLoadingPreview) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
                                                strokeWidth = 2.dp
                                            )
                                        } else {
                                            Icon(
                                                imageVector = if (pair.itemKind == AppleLivePhotoArchiveHelper.ArchiveItemKind.STANDALONE_PHOTO)
                                                    Icons.Default.Image
                                                else
                                                    Icons.Default.PlayCircleOutline,
                                                contentDescription = stringResource(R.string.archive_preview_btn),
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = if (isCurrentPreview)
                                                    stringResource(R.string.zip_previewing_badge)
                                                else
                                                    stringResource(R.string.archive_preview_btn),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Bottom Sticky Action Bar with Target Brand Protocol Selector
                    val currentBrandMode by viewModel.brandMode.collectAsStateWithLifecycle()
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 8.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${stringResource(R.string.brand_protocol_setting)}:",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                com.example.core.protocol.LivePhotoBrandMode.entries.forEach { mode ->
                                    val isSel = currentBrandMode == mode
                                    val labelText = when (mode) {
                                        com.example.core.protocol.LivePhotoBrandMode.FUSION -> stringResource(R.string.protocol_fusion)
                                        com.example.core.protocol.LivePhotoBrandMode.OPPO -> stringResource(R.string.protocol_oppo)
                                        com.example.core.protocol.LivePhotoBrandMode.XIAOMI -> stringResource(R.string.protocol_xiaomi)
                                        com.example.core.protocol.LivePhotoBrandMode.SAMSUNG -> stringResource(R.string.protocol_samsung)
                                        com.example.core.protocol.LivePhotoBrandMode.VIVO -> stringResource(R.string.protocol_vivo)
                                        com.example.core.protocol.LivePhotoBrandMode.GOOGLE -> stringResource(R.string.protocol_google)
                                    }
                                    FilterChip(
                                        selected = isSel,
                                        onClick = { viewModel.setBrandMode(mode) },
                                        label = {
                                            Text(
                                                text = labelText,
                                                fontSize = 11.sp,
                                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        previewPlayer.pause()
                                        onClose()
                                    },
                                    modifier = Modifier
                                        .weight(0.35f)
                                        .height(48.dp),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Text(stringResource(R.string.btn_cancel), fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    enabled = selectedIds.isNotEmpty(),
                                    onClick = {
                                        previewPlayer.pause()
                                        val chosen = scanResult.pairs.filter { selectedIds.contains(it.id) }
                                        viewModel.startArchiveBatchImport(context, scanResult.archiveUri, chosen)
                                    },
                                    modifier = Modifier
                                        .weight(0.65f)
                                        .height(48.dp),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.archive_start_import, selectedIds.size),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
