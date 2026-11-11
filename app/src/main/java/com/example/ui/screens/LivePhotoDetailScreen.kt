@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.media3.common.util.UnstableApi::class)
package com.example.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import kotlin.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import coil.compose.AsyncImage
import com.example.R
import com.example.core.converter.LivePhotoToolConverter
import com.example.core.media.VideoProcessor
import com.example.core.protocol.AppleLivePhotoArchiveHelper
import com.example.data.LivePhotoRecord
import com.example.ui.components.ExoTextureVideoView
import com.example.ui.components.SquigglyWavySlider
import com.example.ui.components.VideoTrimSlider
import com.example.util.DebugLogManager
import com.example.viewmodel.LivePhotoViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun LivePhotoDetailScreen(
    record: LivePhotoRecord,
    viewModel: LivePhotoViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        stringResource(R.string.extract_img_tab),
        stringResource(R.string.extract_video_tab),
        stringResource(R.string.extract_info_tab)
    )

    var inspectInfo by remember { mutableStateOf<LivePhotoToolConverter.ExtractionInfo?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var exportDialogState by remember { mutableStateOf<com.example.ui.components.ExportProgressUiState?>(null) }

    com.example.ui.components.ExportProgressFloatingDialog(state = exportDialogState)

    var selectedImageFormat by remember { mutableStateOf("JPEG") }
    var currentSeekTimeMs by remember { mutableLongStateOf(0L) }
    var keepAudioInVideo by remember { mutableStateOf(true) }

    var trimStartMs by remember { mutableLongStateOf(0L) }
    var trimEndMs by remember { mutableLongStateOf(0L) }
    var previewFrames by remember { mutableStateOf<List<Bitmap>>(emptyList()) }

    var isPreviewPlaying by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }
    var hasRenderedFirstFrame by remember { mutableStateOf(false) }
    var lastDispatchedSeekWallMs by remember { mutableLongStateOf(0L) }
    var lastDispatchedTargetMs by remember { mutableLongStateOf(-1L) }

    val exoPlayer = remember {
        com.example.ui.components.PlayerDecoderConfig.createExoPlayer(context).apply {
            repeatMode = Player.REPEAT_MODE_ALL
            playWhenReady = false
            volume = 1f
            setSeekParameters(androidx.media3.exoplayer.SeekParameters.EXACT)
        }
    }

    LaunchedEffect(isMuted) {
        exoPlayer.volume = if (isMuted) 0f else 1f
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                hasRenderedFirstFrame = true
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    // Automatically pause both video and audio when user exits to home screen or locks screen
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                exoPlayer.pause()
                isPreviewPlaying = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.release()
        }
    }

    LaunchedEffect(record) {
        hasRenderedFirstFrame = false
        isPreviewPlaying = false
        exoPlayer.pause()

        val info = withContext(Dispatchers.IO) {
            LivePhotoToolConverter.inspectLivePhoto(context, record)
        }
        inspectInfo = info
        trimStartMs = 0L
        trimEndMs = info.videoDurationMs.coerceAtLeast(100L)
        currentSeekTimeMs = 0L

        val vFile = info.videoFile
        if (vFile != null && vFile.exists()) {
            exoPlayer.setMediaItem(MediaItem.fromUri(Uri.fromFile(vFile)))
            exoPlayer.prepare()
            exoPlayer.seekTo(0L)
            lastDispatchedTargetMs = 0L

            withContext(Dispatchers.IO) {
                val frames = com.example.core.media.VideoThumbnailCache.prefetchThumbnails(
                    context = context,
                    uri = Uri.fromFile(vFile),
                    durationMs = info.videoDurationMs,
                    count = 8,
                    thumbWidth = 140,
                    thumbHeight = 140
                )
                previewFrames = frames
            }
        }
    }

    // Direct hardware-decoded seeking on ExoPlayer without any static Bitmap overlay or disk I/O race
    val seekVideoSmoothly: (Long, Boolean) -> Unit = { rawTargetMs, forceCommit ->
        val maxDur = (inspectInfo?.videoDurationMs ?: 1500L).coerceAtLeast(100L)
        val targetMs = rawTargetMs.coerceIn(0L, maxDur)
        currentSeekTimeMs = targetMs
        if (isPreviewPlaying) {
            isPreviewPlaying = false
            exoPlayer.pause()
        }
        val nowWall = android.os.SystemClock.uptimeMillis()
        if (forceCommit || (nowWall - lastDispatchedSeekWallMs >= 25L && targetMs != lastDispatchedTargetMs)) {
            lastDispatchedSeekWallMs = nowWall
            lastDispatchedTargetMs = targetMs
            exoPlayer.seekTo(targetMs)
        }
    }

    LaunchedEffect(isPreviewPlaying, selectedTab, trimStartMs, trimEndMs) {
        if (isPreviewPlaying) {
            while (isActive && isPreviewPlaying) {
                val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
                if (selectedTab == 0) {
                    currentSeekTimeMs = pos
                    lastDispatchedTargetMs = pos
                } else if (selectedTab == 1) {
                    if (pos >= trimEndMs) {
                        exoPlayer.seekTo(trimStartMs)
                        currentSeekTimeMs = trimStartMs
                        lastDispatchedTargetMs = trimStartMs
                    } else {
                        currentSeekTimeMs = pos.coerceIn(trimStartMs, trimEndMs.coerceAtLeast(trimStartMs))
                        lastDispatchedTargetMs = currentSeekTimeMs
                    }
                }
                kotlinx.coroutines.delay(33)
            }
        }
    }

    val togglePlayPause = {
        val totalMs = (inspectInfo?.videoDurationMs ?: 1500L).coerceAtLeast(100L)
        val nextPlay = !isPreviewPlaying
        isPreviewPlaying = nextPlay
        DebugLogManager.interaction("LivePhotoDetailScreen", "Toggle preview play: $nextPlay")
        if (nextPlay) {
            if (selectedTab == 1) {
                if (currentSeekTimeMs < trimStartMs || currentSeekTimeMs >= trimEndMs - 40L) {
                    currentSeekTimeMs = trimStartMs
                    lastDispatchedTargetMs = trimStartMs
                    exoPlayer.seekTo(trimStartMs)
                } else if (kotlin.math.abs(exoPlayer.currentPosition - currentSeekTimeMs) > 60L) {
                    exoPlayer.seekTo(currentSeekTimeMs)
                }
            } else {
                if (currentSeekTimeMs >= totalMs - 40L) {
                    currentSeekTimeMs = 0L
                    lastDispatchedTargetMs = 0L
                    exoPlayer.seekTo(0L)
                } else if (kotlin.math.abs(exoPlayer.currentPosition - currentSeekTimeMs) > 60L) {
                    exoPlayer.seekTo(currentSeekTimeMs)
                }
            }
            exoPlayer.playWhenReady = true
            exoPlayer.play()
        } else {
            exoPlayer.pause()
            val pausedPos = exoPlayer.currentPosition.coerceIn(0L, totalMs)
            currentSeekTimeMs = pausedPos
            lastDispatchedTargetMs = pausedPos
        }
    }

    BackHandler {
        DebugLogManager.interaction("LivePhotoDetailScreen", "Back pressed")
        exoPlayer.pause()
        isPreviewPlaying = false
        onBack()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = record.title.ifEmpty { stringResource(R.string.detail_title) },
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        DebugLogManager.interaction("LivePhotoDetailScreen", "Click back button")
                        exoPlayer.pause()
                        isPreviewPlaying = false
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        DebugLogManager.interaction("LivePhotoDetailScreen", "Click share button")
                        try {
                            val fileToShare = File(record.coverPath)
                            if (fileToShare.exists()) {
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    fileToShare
                                )
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "image/jpeg"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_live_photo)))
                            }
                        } catch (_: Exception) {
                            Toast.makeText(context, context.getString(R.string.share_logs), Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Default.Share, contentDescription = stringResource(R.string.share_live_photo))
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
                .verticalScroll(rememberScrollState())
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 10f)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.Black)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (inspectInfo?.videoFile != null) {
                            togglePlayPause()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (!hasRenderedFirstFrame || inspectInfo?.videoFile == null) {
                    AsyncImage(
                        model = record.coverPath,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                if (inspectInfo?.videoFile != null) {
                    ExoTextureVideoView(
                        exoPlayer = exoPlayer,
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = if (hasRenderedFirstFrame) 1f else 0f }
                    )

                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.6f))
                                .clickable { isMuted = !isMuted },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.6f))
                                .clickable { togglePlayPause() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPreviewPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.background,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = {
                            DebugLogManager.interaction("LivePhotoDetailScreen", "Switch tab to $index: $title")
                            selectedTab = index
                            isPreviewPlaying = false
                            exoPlayer.pause()
                        },
                        text = {
                            Text(text = title, fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    when (selectedTab) {
                        0 -> {
                            Text(stringResource(R.string.extract_image_title), fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(12.dp))

                            Text(stringResource(R.string.extract_format), style = MaterialTheme.typography.titleSmall)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("JPEG", "PNG", "WEBP").forEach { fmt ->
                                    val isSel = selectedImageFormat == fmt
                                    FilterChip(
                                        selected = isSel,
                                        onClick = {
                                            DebugLogManager.interaction("LivePhotoDetailScreen", "Select format: $fmt")
                                            selectedImageFormat = fmt
                                        },
                                        label = { Text(fmt) },
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            val totalMs = (inspectInfo?.videoDurationMs ?: 1500L).coerceAtLeast(100L)
                            val curSec = currentSeekTimeMs / 1000f
                            val totalSec = totalMs / 1000f

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(stringResource(R.string.frame_time_select), fontWeight = FontWeight.Bold)
                                Text(
                                    text = "${"%.2f".format(curSec)}s / ${"%.2f".format(totalSec)}s",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            SquigglyWavySlider(
                                value = currentSeekTimeMs.toFloat(),
                                onValueChange = { msVal ->
                                    seekVideoSmoothly(msVal.toLong(), false)
                                },
                                onValueChangeFinished = {
                                    seekVideoSmoothly(currentSeekTimeMs, true)
                                },
                                valueRange = 0f..totalMs.toFloat(),
                                isPlaying = isPreviewPlaying,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                                thumbColor = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                Button(
                                    onClick = {
                                        val ms = (currentSeekTimeMs - 100L).coerceAtLeast(0L)
                                        seekVideoSmoothly(ms, true)
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Text("-0.1s", fontSize = 12.sp)
                                }
                                Button(
                                    onClick = {
                                        val ms = (currentSeekTimeMs - 50L).coerceAtLeast(0L)
                                        seekVideoSmoothly(ms, true)
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Text("-0.05s", fontSize = 12.sp)
                                }
                                Button(
                                    onClick = {
                                        val ms = (currentSeekTimeMs + 50L).coerceAtMost(totalMs)
                                        seekVideoSmoothly(ms, true)
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Text("+0.05s", fontSize = 12.sp)
                                }
                                Button(
                                    onClick = {
                                        val ms = (currentSeekTimeMs + 100L).coerceAtMost(totalMs)
                                        seekVideoSmoothly(ms, true)
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Text("+0.1s", fontSize = 12.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = {
                                    isProcessing = true
                                    exportDialogState = com.example.ui.components.ExportProgressUiState(
                                        title = "正在导出单帧高清图像",
                                        stepDescription = "正在解码 ${"%.2f".format(currentSeekTimeMs / 1000f)}s 处画面并编码为 $selectedImageFormat...",
                                        progressFraction = 0.45f
                                    )
                                    DebugLogManager.interaction("LivePhotoDetailScreen", "Extract image at $currentSeekTimeMs ms, format=$selectedImageFormat")
                                    coroutineScope.launch {
                                        val ok = LivePhotoToolConverter.extractAndSaveImage(
                                            context = context,
                                            record = record,
                                            timeMs = currentSeekTimeMs,
                                            format = selectedImageFormat
                                        )
                                        exportDialogState = null
                                        isProcessing = false
                                        Toast.makeText(context, if (ok) "已导出图像至系统相册" else "导出失败", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                shape = RoundedCornerShape(14.dp),
                                enabled = !isProcessing
                            ) {
                                if (isProcessing) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                                } else {
                                    Icon(Icons.Default.Download, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.btn_export_image), fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        1 -> {
                            Text(stringResource(R.string.extract_video_title), fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(12.dp))

                            val clipSec = ((trimEndMs - trimStartMs) / 100L) / 10f
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(stringResource(R.string.trim_segment_range), fontWeight = FontWeight.Bold)
                                Text("${"%.1f".format(clipSec)}s", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(8.dp))

                            VideoTrimSlider(
                                frames = previewFrames,
                                videoDuration = inspectInfo?.videoDurationMs ?: 1500L,
                                trimStartMs = trimStartMs,
                                trimEndMs = trimEndMs,
                                currentPlaybackMs = currentSeekTimeMs.coerceIn(trimStartMs, trimEndMs.coerceAtLeast(trimStartMs)),
                                onTrimChanged = { start, end ->
                                    trimStartMs = start
                                    trimEndMs = end
                                    if (currentSeekTimeMs < start) {
                                        seekVideoSmoothly(start, true)
                                    } else if (currentSeekTimeMs > end) {
                                        seekVideoSmoothly(end, true)
                                    }
                                },
                                onScrubbing = { scrubMs ->
                                    if (scrubMs != null) {
                                        seekVideoSmoothly(scrubMs, false)
                                    } else {
                                        seekVideoSmoothly(currentSeekTimeMs, true)
                                    }
                                },
                                showDirectInput = true
                            )

                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(stringResource(R.string.keep_original_audio), fontWeight = FontWeight.Medium)
                                Switch(
                                    checked = keepAudioInVideo,
                                    onCheckedChange = { keepAudioInVideo = it }
                                )
                            }

                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = {
                                    isProcessing = true
                                    exportDialogState = com.example.ui.components.ExportProgressUiState(
                                        title = "正在导出独立视频",
                                        stepDescription = "第 1/2 步：裁剪视频片段并执行音轨时间戳对齐...",
                                        progressFraction = 0.40f
                                    )
                                    DebugLogManager.interaction("LivePhotoDetailScreen", "Export video: start=$trimStartMs, end=$trimEndMs, keepAudio=$keepAudioInVideo")
                                    coroutineScope.launch {
                                        val ok = withContext(Dispatchers.IO) {
                                            val vFile = inspectInfo?.videoFile
                                            if (vFile != null && vFile.exists()) {
                                                val trimmedFile = File(context.cacheDir, "trimmed_${System.currentTimeMillis()}.mp4")
                                                val trimOk = VideoProcessor.trimVideo(context, Uri.fromFile(vFile), trimmedFile, trimStartMs, trimEndMs, keepAudioInVideo)
                                                exportDialogState = com.example.ui.components.ExportProgressUiState(
                                                    title = "正在导出独立视频",
                                                    stepDescription = "第 2/2 步：正在写入系统相册 Movies/MotionCraft...",
                                                    progressFraction = 0.85f
                                                )
                                                if (trimOk) {
                                                    val dummyRecord = record.copy(videoPath = trimmedFile.absolutePath)
                                                    LivePhotoToolConverter.extractAndSaveVideo(context, dummyRecord, keepAudioInVideo)
                                                } else false
                                            } else {
                                                LivePhotoToolConverter.extractAndSaveVideo(context, record, keepAudioInVideo)
                                            }
                                        }
                                        exportDialogState = null
                                        isProcessing = false
                                        Toast.makeText(context, if (ok) "已导出独立视频至系统相册" else "导出视频失败", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                shape = RoundedCornerShape(14.dp),
                                enabled = !isProcessing
                            ) {
                                if (isProcessing) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                                } else {
                                    Icon(Icons.Default.Movie, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.btn_export_video), fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        2 -> {
                            Text(stringResource(R.string.extract_info_title), fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(12.dp))

                            val info = inspectInfo
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    DetailRow(stringResource(R.string.detail_brand_protocol), info?.brandDetected ?: "Universal")
                                    DetailRow(stringResource(R.string.detail_video_resolution), if (info != null && info.videoWidth > 0) "${info.videoWidth} x ${info.videoHeight}" else "-")
                                    DetailRow(stringResource(R.string.detail_video_duration), if (info != null && info.videoDurationMs > 0) "${"%.2f".format(info.videoDurationMs / 1000f)}s" else "-")
                                    DetailRow(stringResource(R.string.detail_audio_track), if (info?.hasAudio == true) stringResource(R.string.detail_audio_included) else stringResource(R.string.detail_audio_muted))
                                    DetailRow(stringResource(R.string.detail_cover_size), "${(info?.coverSize ?: 0L) / 1024} KB")
                                    DetailRow(stringResource(R.string.detail_video_size), "${(info?.videoLength ?: 0L) / 1024} KB")
                                    DetailRow(stringResource(R.string.detail_total_file_size), "${(info?.fileSize ?: 0L) / 1024} KB")
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            OutlinedButton(
                                onClick = {
                                    isProcessing = true
                                    exportDialogState = com.example.ui.components.ExportProgressUiState(
                                        title = "正在打包苹果 .livp 实况",
                                        stepDescription = "正在注入 Apple ContentIdentifier UUID 并打包成 .livp 归档...",
                                        progressFraction = 0.55f
                                    )
                                    DebugLogManager.interaction("LivePhotoDetailScreen", "Export Apple .livp package")
                                    coroutineScope.launch {
                                        val livpFile = AppleLivePhotoArchiveHelper.exportToAppleLivp(context, record)
                                        exportDialogState = null
                                        isProcessing = false
                                        if (livpFile != null && livpFile.exists()) {
                                            Toast.makeText(context, context.getString(R.string.export_apple_livp_success), Toast.LENGTH_SHORT).show()
                                            try {
                                                val uri = FileProvider.getUriForFile(
                                                    context,
                                                    "${context.packageName}.fileprovider",
                                                    livpFile
                                                )
                                                val intent = Intent(Intent.ACTION_SEND).apply {
                                                    type = "application/octet-stream"
                                                    putExtra(Intent.EXTRA_STREAM, uri)
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                context.startActivity(Intent.createChooser(intent, context.getString(R.string.export_apple_livp)))
                                            } catch (_: Exception) {}
                                        } else {
                                            Toast.makeText(context, "Error", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                shape = RoundedCornerShape(14.dp),
                                enabled = !isProcessing
                            ) {
                                if (isProcessing) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                                } else {
                                    Icon(Icons.Default.Archive, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.export_apple_livp), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}
