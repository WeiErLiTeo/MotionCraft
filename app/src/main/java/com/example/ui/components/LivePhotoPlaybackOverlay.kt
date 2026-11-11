package com.example.ui.components

import android.graphics.BitmapFactory
import android.media.ExifInterface
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward5
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay5
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import coil.compose.AsyncImage
import com.example.core.media.ImageProcessor
import com.example.core.media.VideoProcessor
import com.example.data.LivePhotoRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

private fun readCoverAspectRatio(coverPath: String): Float {
    return try {
        val file = File(coverPath)
        if (!file.exists()) return 9f / 16f
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        var w = opts.outWidth
        var h = opts.outHeight
        if (w <= 0 || h <= 0) return 9f / 16f
        try {
            val exif = ExifInterface(file.absolutePath)
            val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            if (orientation == ExifInterface.ORIENTATION_ROTATE_90 ||
                orientation == ExifInterface.ORIENTATION_ROTATE_270 ||
                orientation == ExifInterface.ORIENTATION_TRANSPOSE ||
                orientation == ExifInterface.ORIENTATION_TRANSVERSE
            ) {
                val tmp = w
                w = h
                h = tmp
            }
        } catch (_: Exception) {}
        (w.toFloat() / h.toFloat()).coerceIn(0.40f, 2.40f)
    } catch (_: Exception) {
        9f / 16f
    }
}

@OptIn(UnstableApi::class)
@Composable
fun LivePhotoPlaybackOverlay(
    record: LivePhotoRecord,
    autoPlay: Boolean,
    onDismiss: () -> Unit,
    onOpenTool: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    var isPlaying by remember(record) { mutableStateOf(autoPlay) }
    var isMuted by remember(record) { mutableStateOf(false) }
    var userRotationDegrees by remember(record) { mutableIntStateOf(0) }
    var hasRenderedFirstFrame by remember(record) { mutableStateOf(false) }
    var hasError by remember(record) { mutableStateOf(false) }
    var isRepairingVideo by remember(record) { mutableStateOf(false) }
    var fallbackStage by remember(record) { mutableIntStateOf(0) }
    var currentPlaybackPositionMs by remember(record) { mutableLongStateOf(0L) }
    var totalVideoDurationMs by remember(record) { mutableLongStateOf(0L) }
    var scrubbingPositionMs by remember(record) { mutableStateOf<Long?>(null) }
    var playableVideoPath by remember(record) { mutableStateOf("") }
    var mediaAspectRatio by remember(record) { mutableFloatStateOf(readCoverAspectRatio(record.coverPath)) }

    val exoPlayer = remember {
        PlayerDecoderConfig.createExoPlayer(context).apply {
            repeatMode = Player.REPEAT_MODE_ONE
            playWhenReady = autoPlay
            volume = 1f
        }
    }

    LaunchedEffect(isMuted) {
        exoPlayer.volume = if (isMuted) 0f else 1f
    }

    DisposableEffect(exoPlayer, record) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                hasRenderedFirstFrame = true
                hasError = false
                isRepairingVideo = false
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    val ratio = if (videoSize.pixelWidthHeightRatio > 0f) {
                        (videoSize.width * videoSize.pixelWidthHeightRatio) / videoSize.height.toFloat()
                    } else {
                        videoSize.width.toFloat() / videoSize.height.toFloat()
                    }
                    mediaAspectRatio = ratio.coerceIn(0.40f, 2.40f)
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    val d = exoPlayer.duration
                    if (d in 80L..10_800_000L) {
                        totalVideoDurationMs = d
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (fallbackStage == 0) {
                    fallbackStage = 1
                    isRepairingVideo = true
                    coroutineScope.launch {
                        val coverFile = File(record.coverPath)
                        val cacheDir = File(context.cacheDir, "temp_play").apply { mkdirs() }
                        val extractedVideo = File(cacheDir, "${record.id}_${record.timestamp}_extracted.mp4")
                        val repairedVideo = File(cacheDir, "${record.id}_${record.timestamp}_repaired.mp4")

                        val readyFile = withContext(Dispatchers.IO) {
                            extractedVideo.delete()
                            repairedVideo.delete()
                            if (coverFile.exists() && coverFile.length() > 256L) {
                                ImageProcessor.extractVideoFromMotionPhoto(coverFile, extractedVideo)
                            }
                            val candidate = if (extractedVideo.exists() && extractedVideo.length() > 256L) {
                                extractedVideo
                            } else {
                                File(record.videoPath)
                            }
                            if (candidate.exists() && candidate.length() > 256L) {
                                if (VideoProcessor.isPlayableMp4(candidate)) {
                                    candidate
                                } else {
                                    val healed = VideoProcessor.healToStandardMp4(
                                        context = context,
                                        inputFile = candidate,
                                        outputFile = repairedVideo
                                    )
                                    if (healed && repairedVideo.exists() && repairedVideo.length() > 512L) {
                                        if (record.videoPath.isNotEmpty() && record.id >= 0) {
                                            try {
                                                repairedVideo.copyTo(File(record.videoPath), overwrite = true)
                                            } catch (_: Exception) {}
                                        }
                                        repairedVideo
                                    } else {
                                        candidate
                                    }
                                }
                            } else {
                                null
                            }
                        }

                        if (readyFile != null && readyFile.exists() && readyFile.length() > 256L) {
                            hasError = false
                            isRepairingVideo = false
                            playableVideoPath = readyFile.absolutePath
                            val dur = withContext(Dispatchers.IO) {
                                VideoProcessor.getVideoDuration(context, android.net.Uri.fromFile(readyFile))
                            }
                            if (dur in 80L..10_800_000L) totalVideoDurationMs = dur
                            exoPlayer.setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(readyFile)))
                            exoPlayer.prepare()
                            if (isPlaying) {
                                exoPlayer.seekTo(0)
                                exoPlayer.play()
                            }
                        } else {
                            isRepairingVideo = false
                            hasError = true
                            isPlaying = false
                        }
                    }
                } else if (fallbackStage == 1) {
                    fallbackStage = 2
                    isRepairingVideo = true
                    coroutineScope.launch {
                        val cacheDir = File(context.cacheDir, "temp_play").apply { mkdirs() }
                        val currentSource = File(playableVideoPath)
                        val repairedVideo = File(cacheDir, "${record.id}_${record.timestamp}_transcoded.mp4")
                        val ok = withContext(Dispatchers.IO) {
                            if (currentSource.exists() && currentSource.length() > 256L) {
                                val res = VideoProcessor.healToStandardMp4(
                                    context = context,
                                    inputFile = currentSource,
                                    outputFile = repairedVideo
                                )
                                if (res && record.videoPath.isNotEmpty() && record.id >= 0) {
                                    try {
                                        repairedVideo.copyTo(File(record.videoPath), overwrite = true)
                                    } catch (_: Exception) {}
                                }
                                res
                            } else false
                        }
                        isRepairingVideo = false
                        if (ok && repairedVideo.exists() && repairedVideo.length() > 512L) {
                            hasError = false
                            playableVideoPath = repairedVideo.absolutePath
                            val dur = withContext(Dispatchers.IO) {
                                VideoProcessor.getVideoDuration(context, android.net.Uri.fromFile(repairedVideo))
                            }
                            if (dur in 80L..10_800_000L) totalVideoDurationMs = dur
                            exoPlayer.setMediaItem(MediaItem.fromUri(android.net.Uri.fromFile(repairedVideo)))
                            exoPlayer.prepare()
                            if (isPlaying) {
                                exoPlayer.seekTo(0)
                                exoPlayer.play()
                            }
                        } else {
                            hasError = true
                            isPlaying = false
                        }
                    }
                } else {
                    isRepairingVideo = false
                    hasError = true
                    isPlaying = false
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    LaunchedEffect(record) {
        hasRenderedFirstFrame = false
        hasError = false
        isRepairingVideo = false
        fallbackStage = 0
        userRotationDegrees = 0
        mediaAspectRatio = withContext(Dispatchers.IO) { readCoverAspectRatio(record.coverPath) }

        val resolvedFile = withContext(Dispatchers.IO) {
            val directVideoFile = File(record.videoPath)
            val coverFile = File(record.coverPath)
            val cacheDir = File(context.cacheDir, "temp_play").apply { mkdirs() }
            val cacheVideo = File(cacheDir, "${record.id}_${record.timestamp}_video.mp4")

            var candidate: File? = null
            if (directVideoFile.exists() && directVideoFile.length() > 256L) {
                candidate = directVideoFile
            } else if (coverFile.exists() && coverFile.length() > 256L) {
                if (!cacheVideo.exists() || cacheVideo.length() <= 256L) {
                    ImageProcessor.extractVideoFromMotionPhoto(coverFile, cacheVideo)
                }
                if (cacheVideo.exists() && cacheVideo.length() > 256L) {
                    candidate = cacheVideo
                }
            }

            if (candidate != null && candidate.exists()) {
                if (VideoProcessor.isPlayableMp4(candidate)) {
                    candidate
                } else {
                    isRepairingVideo = true
                    val repairedFile = File(cacheDir, "${record.id}_${record.timestamp}_healed.mp4")
                    val healed = VideoProcessor.healToStandardMp4(
                        context = context,
                        inputFile = candidate,
                        outputFile = repairedFile
                    )
                    if (healed && repairedFile.exists() && repairedFile.length() > 512L) {
                        if (record.videoPath.isNotEmpty() && record.id >= 0) {
                            try {
                                repairedFile.copyTo(directVideoFile, overwrite = true)
                            } catch (_: Exception) {}
                        }
                        repairedFile
                    } else {
                        candidate
                    }
                }
            } else {
                null
            }
        }

        isRepairingVideo = false
        val resolvedPath = resolvedFile?.absolutePath ?: ""
        playableVideoPath = resolvedPath
        if (resolvedFile != null && resolvedPath.isNotEmpty()) {
            val dur = withContext(Dispatchers.IO) {
                VideoProcessor.getVideoDuration(context, android.net.Uri.fromFile(resolvedFile))
            }
            if (dur in 80L..10_800_000L) {
                totalVideoDurationMs = dur
            }
            val mediaItem = MediaItem.fromUri(android.net.Uri.fromFile(resolvedFile))
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.seekTo(0)
            if (isPlaying) {
                exoPlayer.play()
            }
        } else {
            hasError = true
            isPlaying = false
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                exoPlayer.pause()
                isPlaying = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.release()
        }
    }

    LaunchedEffect(isPlaying, playableVideoPath, scrubbingPositionMs) {
        if (scrubbingPositionMs != null) {
            return@LaunchedEffect
        }
        if (isPlaying && playableVideoPath.isNotEmpty() && !hasError) {
            exoPlayer.playWhenReady = true
            exoPlayer.play()
            while (isPlaying && scrubbingPositionMs == null) {
                val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
                currentPlaybackPositionMs = pos
                if (pos > 0L && !hasRenderedFirstFrame) {
                    hasRenderedFirstFrame = true
                }
                val dur = exoPlayer.duration
                if (dur in 80L..10_800_000L) {
                    totalVideoDurationMs = dur
                }
                delay(40)
            }
        } else if (!isPlaying) {
            exoPlayer.pause()
        }
    }

    BackHandler { onDismiss() }

    // Effective aspect ratio swaps width/height when rotated 90° or 270°
    val effectiveAspectRatio = if (userRotationDegrees % 180 != 0) {
        (1f / mediaAspectRatio.coerceAtLeast(0.1f)).coerceIn(0.40f, 2.40f)
    } else {
        mediaAspectRatio.coerceIn(0.40f, 2.40f)
    }

    val currentTotalDurMs by rememberUpdatedState(totalVideoDurationMs)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 1. WeChat Moments-style Full-Screen Ambient Soft Glow Layer (strictly outside the video box)
        if (record.coverPath.isNotEmpty() && File(record.coverPath).exists()) {
            AsyncImage(
                model = record.coverPath,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .scale(1.35f)
                    .blur(radius = 78.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.38f))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top compact info bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = record.title,
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (userRotationDegrees != 0) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.White.copy(alpha = 0.18f)
                    ) {
                        Text(
                            text = "已旋转 ${userRotationDegrees}°",
                            color = Color.White,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            // 2. Bounded Middle Stage: Guarantees the sharp rectangular video NEVER pushes the bottom
            //    progress bar or action buttons off-screen even on tall 9:16 / 9:21 vertical videos!
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                val availW = maxWidth
                val availH = maxHeight
                val fitHeightForFullWidth = availW / effectiveAspectRatio
                val (boxWidthDp, boxHeightDp) = if (fitHeightForFullWidth <= availH) {
                    availW to fitHeightForFullWidth
                } else {
                    (availH * effectiveAspectRatio) to availH
                }

                Box(
                    modifier = Modifier
                        .size(width = boxWidthDp, height = boxHeightDp)
                        .clipToBounds()
                        .background(Color.Black)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            if (playableVideoPath.isNotEmpty() && !hasError) {
                                isPlaying = !isPlaying
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val innerWidthDp = if (userRotationDegrees % 180 != 0) boxHeightDp else boxWidthDp
                    val innerHeightDp = if (userRotationDegrees % 180 != 0) boxWidthDp else boxHeightDp

                    Box(
                        modifier = Modifier
                            .size(width = innerWidthDp, height = innerHeightDp)
                            .graphicsLayer {
                                rotationZ = userRotationDegrees.toFloat()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (record.coverPath.isNotEmpty() && File(record.coverPath).exists()) {
                            AsyncImage(
                                model = record.coverPath,
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        val showVideoLayer = (isPlaying || scrubbingPositionMs != null || currentPlaybackPositionMs > 0L) &&
                            (hasRenderedFirstFrame || currentPlaybackPositionMs > 0L) && !hasError
                        val videoAlpha by animateFloatAsState(
                            targetValue = if (showVideoLayer) 1f else 0f,
                            animationSpec = tween(durationMillis = 120),
                            label = "live_photo_video_alpha"
                        )

                        ExoTextureVideoView(
                            exoPlayer = exoPlayer,
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { alpha = videoAlpha }
                        )
                    }

                    if (isRepairingVideo) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(Color.Black.copy(alpha = 0.55f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.5.dp,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    } else if (!isPlaying && scrubbingPositionMs == null) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                }
            }

            // 3. Interactive Draggable Seek Bar & Time Labels (supports long videos smoothly!)
            val displayPosMs = scrubbingPositionMs ?: currentPlaybackPositionMs
            val progressFraction = if (totalVideoDurationMs > 0L) {
                (displayPosMs.toFloat() / totalVideoDurationMs.toFloat()).coerceIn(0f, 1f)
            } else 0f

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
            ) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(26.dp)
                        .pointerInput(totalVideoDurationMs) {
                            detectTapGestures { tapOffset ->
                                val dur = currentTotalDurMs
                                if (dur > 0L && size.width > 0) {
                                    val ratio = (tapOffset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                    val targetMs = (ratio * dur).toLong()
                                    currentPlaybackPositionMs = targetMs
                                    exoPlayer.seekTo(targetMs)
                                }
                            }
                        }
                        .pointerInput(totalVideoDurationMs) {
                            detectDragGestures(
                                onDragStart = { startOffset ->
                                    val dur = currentTotalDurMs
                                    if (dur > 0L && size.width > 0) {
                                        val ratio = (startOffset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                        val targetMs = (ratio * dur).toLong()
                                        scrubbingPositionMs = targetMs
                                        currentPlaybackPositionMs = targetMs
                                        exoPlayer.seekTo(targetMs)
                                    }
                                },
                                onDragEnd = {
                                    val finalMs = scrubbingPositionMs
                                    scrubbingPositionMs = null
                                    if (finalMs != null) {
                                        currentPlaybackPositionMs = finalMs
                                        exoPlayer.seekTo(finalMs)
                                    }
                                },
                                onDragCancel = {
                                    scrubbingPositionMs = null
                                }
                            ) { change, _ ->
                                change.consume()
                                val dur = currentTotalDurMs
                                if (dur > 0L && size.width > 0) {
                                    val ratio = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                                    val targetMs = (ratio * dur).toLong()
                                    scrubbingPositionMs = targetMs
                                    currentPlaybackPositionMs = targetMs
                                    exoPlayer.seekTo(targetMs)
                                }
                            }
                        },
                    contentAlignment = Alignment.CenterStart
                ) {
                    val trackWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
                    val thumbRadiusDp = 6.dp
                    val thumbRadiusPx = with(density) { thumbRadiusDp.toPx() }
                    val thumbCenterX = (progressFraction * trackWidthPx).coerceIn(thumbRadiusPx, (trackWidthPx - thumbRadiusPx).coerceAtLeast(thumbRadiusPx))

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
                                .fillMaxWidth(progressFraction)
                                .background(Color.White)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .offset { IntOffset((thumbCenterX - thumbRadiusPx).roundToInt(), 0) }
                            .size(thumbRadiusDp * 2)
                            .background(Color.White, CircleShape)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val totalSecs = if (totalVideoDurationMs > 0L) {
                        ((totalVideoDurationMs + 450L) / 1000L).coerceAtLeast(1L)
                    } else 0L
                    val currentSecs = if (totalVideoDurationMs > 0L) {
                        ((displayPosMs + 450L) / 1000L).coerceIn(0L, totalSecs)
                    } else 0L

                    val formatSeconds = { secTotal: Long ->
                        val h = secTotal / 3600L
                        val m = (secTotal % 3600L) / 60L
                        val s = secTotal % 60L
                        if (h > 0L) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
                    }
                    Text(
                        text = formatSeconds(currentSecs),
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )

                    if (totalVideoDurationMs > 8_000L) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable {
                                        val next = (exoPlayer.currentPosition - 5_000L).coerceAtLeast(0L)
                                        currentPlaybackPositionMs = next
                                        exoPlayer.seekTo(next)
                                    }
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Replay5, contentDescription = "快退5秒", tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text("-5s", color = Color.White.copy(alpha = 0.85f), fontSize = 10.sp)
                            }
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable {
                                        val next = (exoPlayer.currentPosition + 5_000L).coerceAtMost(totalVideoDurationMs)
                                        currentPlaybackPositionMs = next
                                        exoPlayer.seekTo(next)
                                    }
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Forward5, contentDescription = "快进5秒", tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text("+5s", color = Color.White.copy(alpha = 0.85f), fontSize = 10.sp)
                            }
                        }
                    }

                    Text(
                        text = formatSeconds(totalSecs),
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // 4. Guaranteed Visible Bottom Control Dock (Close, Play/Pause, Rotate 90°, Mute, Toolbox)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(52.dp)
                        .background(Color.White.copy(alpha = 0.18f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "关闭",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(
                    onClick = {
                        if (playableVideoPath.isNotEmpty() && !hasError) {
                            isPlaying = !isPlaying
                        }
                    },
                    modifier = Modifier
                        .size(52.dp)
                        .background(Color.White.copy(alpha = 0.18f), CircleShape)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "暂停" else "播放",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }

                IconButton(
                    onClick = {
                        userRotationDegrees = (userRotationDegrees + 90) % 360
                    },
                    modifier = Modifier
                        .size(52.dp)
                        .background(Color.White.copy(alpha = 0.18f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.RotateRight,
                        contentDescription = "旋转画面",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(
                    onClick = { isMuted = !isMuted },
                    modifier = Modifier
                        .size(52.dp)
                        .background(Color.White.copy(alpha = 0.18f), CircleShape)
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = if (isMuted) "取消静音" else "静音",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                if (record.id >= 0) {
                    IconButton(
                        onClick = onOpenTool,
                        modifier = Modifier
                            .size(52.dp)
                            .background(Color(0xFF8C9EFF), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = "实况工具箱",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}
