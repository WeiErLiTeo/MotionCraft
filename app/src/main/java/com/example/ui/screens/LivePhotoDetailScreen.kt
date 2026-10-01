@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.media3.common.util.UnstableApi::class)
package com.example.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import kotlin.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import kotlinx.coroutines.isActive
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.R
import com.example.core.converter.LivePhotoToolConverter
import com.example.core.media.VideoProcessor
import com.example.data.LivePhotoRecord
import com.example.ui.components.SquigglyWavySlider
import com.example.ui.components.VideoTrimSlider
import com.example.util.DebugLogManager
import com.example.viewmodel.LivePhotoViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.media3.common.util.UnstableApi::class)
@Composable
fun LivePhotoDetailScreen(
    record: LivePhotoRecord,
    viewModel: LivePhotoViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        stringResource(R.string.extract_img_tab),
        stringResource(R.string.extract_video_tab),
        stringResource(R.string.extract_info_tab)
    )

    var inspectInfo by remember { mutableStateOf<LivePhotoToolConverter.ExtractionInfo?>(null) }
    var isProcessing by remember { mutableStateOf(false) }

    var selectedImageFormat by remember { mutableStateOf("JPEG") }
    var currentSeekTimeMs by remember { mutableLongStateOf(0L) }
    var keepAudioInVideo by remember { mutableStateOf(true) }

    var trimStartMs by remember { mutableLongStateOf(0L) }
    var trimEndMs by remember { mutableLongStateOf(0L) }
    var previewFrames by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var liveSeekFrameBitmap by remember { mutableStateOf<Bitmap?>(null) }

    var isPreviewPlaying by remember { mutableStateOf(false) }
    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }

    LaunchedEffect(record) {
        val info = LivePhotoToolConverter.inspectLivePhoto(context, record)
        inspectInfo = info
        trimStartMs = 0L
        trimEndMs = info.videoDurationMs.coerceAtLeast(100L)
        currentSeekTimeMs = 0L

        val vFile = info.videoFile
        if (vFile != null && vFile.exists()) {
            val player = ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(vFile.absolutePath))
                repeatMode = Player.REPEAT_MODE_ALL
                prepare()
            }
            exoPlayer = player

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
                liveSeekFrameBitmap = com.example.core.media.VideoThumbnailCache.getOrExtractFrame(context, Uri.fromFile(vFile), 0L)
            }
        }
    }

    LaunchedEffect(exoPlayer, isPreviewPlaying, selectedTab, trimStartMs, trimEndMs) {
        val player = exoPlayer ?: return@LaunchedEffect
        if (isPreviewPlaying) {
            while (isActive && isPreviewPlaying) {
                val pos = player.currentPosition
                if (selectedTab == 0) {
                    currentSeekTimeMs = pos
                } else if (selectedTab == 1 && pos >= trimEndMs) {
                    player.seekTo(trimStartMs)
                }
                kotlinx.coroutines.delay(40)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer?.release()
            exoPlayer = null
        }
    }

    BackHandler {
        DebugLogManager.interaction("LivePhotoDetailScreen", "Back pressed")
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
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (selectedTab == 0 && !isPreviewPlaying && liveSeekFrameBitmap != null) {
                    Image(
                        bitmap = liveSeekFrameBitmap!!.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (exoPlayer != null && inspectInfo?.videoFile != null) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = exoPlayer
                                useController = false
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                layoutParams = android.view.ViewGroup.LayoutParams(
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    AsyncImage(
                        model = record.coverPath,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                if (inspectInfo?.videoFile != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp)
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f))
                            .clickable {
                                isPreviewPlaying = !isPreviewPlaying
                                DebugLogManager.interaction("LivePhotoDetailScreen", "Toggle preview play: $isPreviewPlaying")
                                if (isPreviewPlaying) {
                                    if (selectedTab == 1) {
                                        exoPlayer?.seekTo(trimStartMs)
                                    } else {
                                        exoPlayer?.seekTo(currentSeekTimeMs)
                                    }
                                    exoPlayer?.play()
                                } else {
                                    exoPlayer?.pause()
                                }
                            },
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
                            exoPlayer?.pause()
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
                                    val ms = msVal.toLong()
                                    currentSeekTimeMs = ms
                                    isPreviewPlaying = false
                                    exoPlayer?.pause()
                                    exoPlayer?.seekTo(ms)
                                    val vFile = inspectInfo?.videoFile
                                    if (vFile != null && vFile.exists()) {
                                        val cached = com.example.core.media.VideoThumbnailCache.getFromMemory(Uri.fromFile(vFile), ms)
                                        if (cached != null && !cached.isRecycled) {
                                            liveSeekFrameBitmap = cached
                                        }
                                    }
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
                                        currentSeekTimeMs = ms
                                        isPreviewPlaying = false
                                        exoPlayer?.pause()
                                        exoPlayer?.seekTo(ms)
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
                                        currentSeekTimeMs = ms
                                        isPreviewPlaying = false
                                        exoPlayer?.pause()
                                        exoPlayer?.seekTo(ms)
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
                                        currentSeekTimeMs = ms
                                        isPreviewPlaying = false
                                        exoPlayer?.pause()
                                        exoPlayer?.seekTo(ms)
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
                                        currentSeekTimeMs = ms
                                        isPreviewPlaying = false
                                        exoPlayer?.pause()
                                        exoPlayer?.seekTo(ms)
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
                                    DebugLogManager.interaction("LivePhotoDetailScreen", "Extract image at $currentSeekTimeMs ms, format=$selectedImageFormat")
                                    coroutineScope.launch {
                                        val ok = LivePhotoToolConverter.extractAndSaveImage(
                                            context = context,
                                            record = record,
                                            timeMs = currentSeekTimeMs,
                                            format = selectedImageFormat
                                        )
                                        isProcessing = false
                                        Toast.makeText(context, if (ok) "OK" else "Error", Toast.LENGTH_SHORT).show()
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
                                onTrimChanged = { start, end ->
                                    trimStartMs = start
                                    trimEndMs = end
                                },
                                onScrubbing = { scrubMs ->
                                    if (scrubMs != null) {
                                        isPreviewPlaying = false
                                        exoPlayer?.pause()
                                        exoPlayer?.seekTo(scrubMs)
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
                                    DebugLogManager.interaction("LivePhotoDetailScreen", "Export video: start=$trimStartMs, end=$trimEndMs, keepAudio=$keepAudioInVideo")
                                    coroutineScope.launch {
                                        val ok = withContext(Dispatchers.IO) {
                                            val vFile = inspectInfo?.videoFile
                                            if (vFile != null && vFile.exists()) {
                                                val trimmedFile = File(context.cacheDir, "trimmed_${System.currentTimeMillis()}.mp4")
                                                val trimOk = VideoProcessor.trimVideo(context, Uri.fromFile(vFile), trimmedFile, trimStartMs, trimEndMs)
                                                if (trimOk) {
                                                    val dummyRecord = record.copy(videoPath = trimmedFile.absolutePath)
                                                    LivePhotoToolConverter.extractAndSaveVideo(context, dummyRecord, keepAudioInVideo)
                                                } else false
                                            } else {
                                                LivePhotoToolConverter.extractAndSaveVideo(context, record, keepAudioInVideo)
                                            }
                                        }
                                        isProcessing = false
                                        Toast.makeText(context, if (ok) "OK" else "Error", Toast.LENGTH_SHORT).show()
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
