package com.example.ui.components

import android.content.Context
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import androidx.media3.ui.AspectRatioFrameLayout
import com.example.util.DebugLogManager
import kotlin.math.abs

object PlayerDecoderConfig {
    const val MODE_EXO_HW_FALLBACK = 0
    const val MODE_EXO_SW_PREFER = 1
    const val MODE_ANDROID_MEDIA_PLAYER = 2

    fun getSavedDecoderMode(context: Context): Int {
        val prefs = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
        return prefs.getInt("player_decoder_mode", MODE_EXO_HW_FALLBACK)
    }

    @OptIn(UnstableApi::class)
    fun createExoPlayer(context: Context, decoderMode: Int = getSavedDecoderMode(context)): ExoPlayer {
        val renderersFactory = DefaultRenderersFactory(context.applicationContext)
            .setEnableDecoderFallback(true)

        if (decoderMode == MODE_EXO_SW_PREFER) {
            renderersFactory.setMediaCodecSelector(
                MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
                    val defaultList = MediaCodecUtil.getDecoderInfos(
                        mimeType,
                        requiresSecureDecoder,
                        requiresTunnelingDecoder
                    )
                    if (defaultList.size <= 1) {
                        defaultList
                    } else {
                        val swFirst = ArrayList<MediaCodecInfo>(defaultList.size)
                        val hwLater = ArrayList<MediaCodecInfo>(defaultList.size)
                        for (info in defaultList) {
                            val name = info.name.lowercase()
                            val isSw = info.softwareOnly ||
                                !info.hardwareAccelerated ||
                                name.startsWith("c2.android.") ||
                                name.startsWith("omx.google.")
                            if (isSw) swFirst.add(info) else hwLater.add(info)
                        }
                        swFirst + hwLater
                    }
                }
            )
        }

        return ExoPlayer.Builder(context.applicationContext, renderersFactory)
            .build()
            .apply {
                setSeekParameters(androidx.media3.exoplayer.SeekParameters.EXACT)
            }
    }
}

/**
 * Applies a pure GPU center-fit / center-crop Matrix transform to [textureView].
 * This completely eliminates AspectRatioFrameLayout measurement lag, off-center shifts,
 * and portrait/landscape stretching when switching between Apple .MOV and MP4 videos.
 */
fun applyTextureViewScaleMatrix(
    textureView: TextureView,
    videoWidth: Int,
    videoHeight: Int,
    pixelRatio: Float = 1f,
    resizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_FIT
) {
    val viewW = textureView.width.toFloat()
    val viewH = textureView.height.toFloat()
    if (viewW <= 1f || viewH <= 1f || videoWidth <= 0 || videoHeight <= 0) {
        return
    }
    val safePixelRatio = if (pixelRatio > 0f) pixelRatio else 1f
    val videoAspect = (videoWidth * safePixelRatio) / videoHeight.toFloat()
    val viewAspect = viewW / viewH

    var scaleX = 1f
    var scaleY = 1f
    if (resizeMode == AspectRatioFrameLayout.RESIZE_MODE_ZOOM) {
        if (videoAspect > viewAspect) {
            scaleX = videoAspect / viewAspect
        } else {
            scaleY = viewAspect / videoAspect
        }
    } else {
        if (videoAspect > viewAspect) {
            scaleY = viewAspect / videoAspect
        } else {
            scaleX = videoAspect / viewAspect
        }
    }

    val matrix = Matrix()
    matrix.setScale(scaleX, scaleY, viewW / 2f, viewH / 2f)
    textureView.setTransform(matrix)
    textureView.invalidate()
}

private class ExoTextureHolder(
    val textureView: TextureView,
    val playerListener: Player.Listener,
    val layoutListener: View.OnLayoutChangeListener,
    var boundPlayer: ExoPlayer,
    var resizeMode: Int,
    var lastVideoW: Int = 0,
    var lastVideoH: Int = 0,
    var lastPixelRatio: Float = 1f
)

@OptIn(UnstableApi::class)
@Composable
fun ExoTextureVideoView(
    exoPlayer: ExoPlayer,
    modifier: Modifier = Modifier,
    resizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_FIT
) {
    AndroidView(
        factory = { ctx ->
            val rootFrame = FrameLayout(ctx).apply {
                clipChildren = true
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            val textureView = TextureView(ctx).apply {
                isOpaque = false
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.Gravity.CENTER
                )
            }
            rootFrame.addView(textureView)
            exoPlayer.setVideoTextureView(textureView)

            val initialSize = exoPlayer.videoSize
            var holderRef: ExoTextureHolder? = null

            val playerListener = object : Player.Listener {
                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    val h = holderRef ?: return
                    if (videoSize.width > 0 && videoSize.height > 0) {
                        h.lastVideoW = videoSize.width
                        h.lastVideoH = videoSize.height
                        h.lastPixelRatio = if (videoSize.pixelWidthHeightRatio > 0f) videoSize.pixelWidthHeightRatio else 1f
                        applyTextureViewScaleMatrix(
                            textureView = h.textureView,
                            videoWidth = h.lastVideoW,
                            videoHeight = h.lastVideoH,
                            pixelRatio = h.lastPixelRatio,
                            resizeMode = h.resizeMode
                        )
                    }
                }

                override fun onRenderedFirstFrame() {
                    val h = holderRef ?: return
                    val vs = h.boundPlayer.videoSize
                    if (vs.width > 0 && vs.height > 0) {
                        h.lastVideoW = vs.width
                        h.lastVideoH = vs.height
                        h.lastPixelRatio = if (vs.pixelWidthHeightRatio > 0f) vs.pixelWidthHeightRatio else 1f
                    }
                    if (h.lastVideoW > 0 && h.lastVideoH > 0) {
                        applyTextureViewScaleMatrix(
                            textureView = h.textureView,
                            videoWidth = h.lastVideoW,
                            videoHeight = h.lastVideoH,
                            pixelRatio = h.lastPixelRatio,
                            resizeMode = h.resizeMode
                        )
                    }
                }
            }

            val layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                val h = holderRef ?: return@OnLayoutChangeListener
                val vs = h.boundPlayer.videoSize
                if (vs.width > 0 && vs.height > 0) {
                    h.lastVideoW = vs.width
                    h.lastVideoH = vs.height
                    h.lastPixelRatio = if (vs.pixelWidthHeightRatio > 0f) vs.pixelWidthHeightRatio else 1f
                }
                if (h.lastVideoW > 0 && h.lastVideoH > 0) {
                    applyTextureViewScaleMatrix(
                        textureView = h.textureView,
                        videoWidth = h.lastVideoW,
                        videoHeight = h.lastVideoH,
                        pixelRatio = h.lastPixelRatio,
                        resizeMode = h.resizeMode
                    )
                }
            }

            val holder = ExoTextureHolder(
                textureView = textureView,
                playerListener = playerListener,
                layoutListener = layoutListener,
                boundPlayer = exoPlayer,
                resizeMode = resizeMode,
                lastVideoW = initialSize.width,
                lastVideoH = initialSize.height,
                lastPixelRatio = if (initialSize.pixelWidthHeightRatio > 0f) initialSize.pixelWidthHeightRatio else 1f
            )
            holderRef = holder

            textureView.addOnLayoutChangeListener(layoutListener)
            exoPlayer.addListener(playerListener)
            rootFrame.tag = holder
            rootFrame
        },
        update = { rootFrame ->
            val holder = rootFrame.tag as? ExoTextureHolder ?: return@AndroidView
            holder.resizeMode = resizeMode
            if (holder.boundPlayer !== exoPlayer) {
                holder.boundPlayer.removeListener(holder.playerListener)
                holder.boundPlayer.clearVideoTextureView(holder.textureView)
                holder.boundPlayer = exoPlayer
                exoPlayer.addListener(holder.playerListener)
            }
            exoPlayer.setVideoTextureView(holder.textureView)
            val size = exoPlayer.videoSize
            if (size.width > 0 && size.height > 0) {
                holder.lastVideoW = size.width
                holder.lastVideoH = size.height
                holder.lastPixelRatio = if (size.pixelWidthHeightRatio > 0f) size.pixelWidthHeightRatio else 1f
            }
            if (holder.lastVideoW > 0 && holder.lastVideoH > 0) {
                applyTextureViewScaleMatrix(
                    textureView = holder.textureView,
                    videoWidth = holder.lastVideoW,
                    videoHeight = holder.lastVideoH,
                    pixelRatio = holder.lastPixelRatio,
                    resizeMode = holder.resizeMode
                )
            }
        },
        onRelease = { rootFrame ->
            val holder = rootFrame.tag as? ExoTextureHolder
            if (holder != null) {
                holder.textureView.removeOnLayoutChangeListener(holder.layoutListener)
                holder.boundPlayer.removeListener(holder.playerListener)
                holder.boundPlayer.clearVideoTextureView(holder.textureView)
            }
        },
        modifier = modifier
    )
}

@OptIn(UnstableApi::class)
@Composable
fun InlineVideoPlayer(
    videoUri: Uri,
    trimStartMs: Long,
    trimEndMs: Long,
    modifier: Modifier = Modifier,
    videoDurationMs: Long = 0L,
    previewSeekTimeMs: Long? = null,
    isBoundaryPreview: Boolean = false,
    isPausedExternal: Boolean = false,
    onClearPreviewSeek: (() -> Unit)? = null,
    onPositionUpdate: ((Long) -> Unit)? = null,
    onDurationDetected: ((Long) -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnDurationDetected by rememberUpdatedState(onDurationDetected)

    val savedDecoderMode = remember { PlayerDecoderConfig.getSavedDecoderMode(context) }
    var activeDecoderMode by remember(videoUri) { mutableIntStateOf(savedDecoderMode) }

    var isPlaying by remember { mutableStateOf(true) }
    var isMuted by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var currentPosMs by remember { mutableLongStateOf(trimStartMs) }
    var resolvedPlayerDurationMs by remember(videoUri) { mutableLongStateOf(videoDurationMs) }

    val useSystemMediaPlayer = activeDecoderMode == PlayerDecoderConfig.MODE_ANDROID_MEDIA_PLAYER

    val exoPlayer = remember(activeDecoderMode) {
        PlayerDecoderConfig.createExoPlayer(context, activeDecoderMode).apply {
            repeatMode = Player.REPEAT_MODE_OFF
            playWhenReady = !useSystemMediaPlayer
        }
    }

    DisposableEffect(exoPlayer, videoUri, useSystemMediaPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    val dur = exoPlayer.duration
                    if (dur in 100L..36_000_000L) {
                        resolvedPlayerDurationMs = dur
                        currentOnDurationDetected?.invoke(dur)
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                DebugLogManager.w("InlineVideoPlayer", "ExoPlayer error (${error.errorCodeName}) in mode=$activeDecoderMode")
                if (activeDecoderMode == PlayerDecoderConfig.MODE_EXO_HW_FALLBACK) {
                    activeDecoderMode = PlayerDecoderConfig.MODE_EXO_SW_PREFER
                } else if (activeDecoderMode == PlayerDecoderConfig.MODE_EXO_SW_PREFER) {
                    activeDecoderMode = PlayerDecoderConfig.MODE_ANDROID_MEDIA_PLAYER
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    LaunchedEffect(videoUri, activeDecoderMode) {
        if (!useSystemMediaPlayer) {
            val mediaItem = MediaItem.fromUri(videoUri)
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.seekTo(trimStartMs)
        } else {
            exoPlayer.stop()
        }
    }

    LaunchedEffect(isPausedExternal) {
        if (isPausedExternal && isPlaying) {
            isPlaying = false
            exoPlayer.pause()
        }
    }

    LaunchedEffect(isMuted) {
        exoPlayer.volume = if (isMuted) 0f else 1f
    }

    LaunchedEffect(playbackSpeed) {
        if (!useSystemMediaPlayer) {
            exoPlayer.setPlaybackParameters(PlaybackParameters(playbackSpeed))
        }
    }

    var lastSeekWallMs by remember { mutableLongStateOf(0L) }
    var lastSeekTargetMs by remember { mutableLongStateOf(-1L) }

    DisposableEffect(lifecycleOwner, exoPlayer) {
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

    // Native Android MediaPlayer fallback / alternative engine
    var nativeMediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    LaunchedEffect(previewSeekTimeMs, isBoundaryPreview, useSystemMediaPlayer) {
        if (previewSeekTimeMs != null) {
            if (isPlaying) {
                isPlaying = false
                if (useSystemMediaPlayer) {
                    runCatching { if (nativeMediaPlayer?.isPlaying == true) nativeMediaPlayer?.pause() }
                } else {
                    exoPlayer.pause()
                }
            }
            if (!isBoundaryPreview) {
                currentPosMs = previewSeekTimeMs
                onPositionUpdate?.invoke(previewSeekTimeMs)
            } else {
                currentPosMs = currentPosMs.coerceIn(trimStartMs, trimEndMs.coerceAtLeast(trimStartMs))
            }
            val nowWall = android.os.SystemClock.uptimeMillis()
            if (nowWall - lastSeekWallMs >= 25L && previewSeekTimeMs != lastSeekTargetMs) {
                lastSeekWallMs = nowWall
                lastSeekTargetMs = previewSeekTimeMs
                if (useSystemMediaPlayer) {
                    runCatching {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            nativeMediaPlayer?.seekTo(previewSeekTimeMs, MediaPlayer.SEEK_CLOSEST)
                        } else {
                            nativeMediaPlayer?.seekTo(previewSeekTimeMs.toInt())
                        }
                    }
                } else {
                    exoPlayer.seekTo(previewSeekTimeMs)
                }
            }
        } else if (lastSeekTargetMs >= 0L) {
            val clampedPos = currentPosMs.coerceIn(trimStartMs, trimEndMs.coerceAtLeast(trimStartMs))
            currentPosMs = clampedPos
            if (useSystemMediaPlayer) {
                runCatching {
                    val cur = nativeMediaPlayer?.currentPosition?.toLong() ?: 0L
                    if (abs(cur - clampedPos) > 35L) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            nativeMediaPlayer?.seekTo(clampedPos, MediaPlayer.SEEK_CLOSEST)
                        } else {
                            nativeMediaPlayer?.seekTo(clampedPos.toInt())
                        }
                    }
                }
            } else {
                if (abs(exoPlayer.currentPosition - clampedPos) > 35L) {
                    exoPlayer.seekTo(clampedPos)
                }
            }
        }
    }

    LaunchedEffect(trimStartMs, trimEndMs, isPlaying, previewSeekTimeMs, isPausedExternal, useSystemMediaPlayer, nativeMediaPlayer) {
        if (previewSeekTimeMs == null && isPlaying && !isPausedExternal) {
            if (useSystemMediaPlayer) {
                val mp = nativeMediaPlayer
                if (mp != null) {
                    runCatching {
                        val cur = mp.currentPosition.toLong()
                        if (cur < trimStartMs - 150L || cur >= trimEndMs - 30L) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                mp.seekTo(trimStartMs, MediaPlayer.SEEK_CLOSEST)
                            } else {
                                mp.seekTo(trimStartMs.toInt())
                            }
                        }
                        if (!mp.isPlaying) mp.start()
                    }
                    var lastReportedMs = -1L
                    while (isPlaying && previewSeekTimeMs == null && !isPausedExternal) {
                        val pos = runCatching { mp.currentPosition.toLong() }.getOrDefault(trimStartMs)
                        val clamped = pos.coerceIn(trimStartMs, trimEndMs.coerceAtLeast(trimStartMs))
                        currentPosMs = clamped
                        lastSeekTargetMs = clamped
                        if (abs(clamped - lastReportedMs) >= 40L) {
                            lastReportedMs = clamped
                            onPositionUpdate?.invoke(clamped)
                        }
                        if (pos >= trimEndMs + 35L) {
                            runCatching {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    mp.seekTo(trimStartMs, MediaPlayer.SEEK_CLOSEST)
                                } else {
                                    mp.seekTo(trimStartMs.toInt())
                                }
                                if (!mp.isPlaying) mp.start()
                            }
                        }
                        kotlinx.coroutines.delay(40)
                    }
                }
            } else {
                if (exoPlayer.currentPosition < trimStartMs - 150L || exoPlayer.currentPosition >= trimEndMs - 30L) {
                    exoPlayer.seekTo(trimStartMs)
                }
                exoPlayer.playWhenReady = true
                exoPlayer.play()
                var lastReportedMs = -1L
                while (isPlaying && previewSeekTimeMs == null && !isPausedExternal) {
                    val pos = exoPlayer.currentPosition
                    val clamped = pos.coerceIn(trimStartMs, trimEndMs.coerceAtLeast(trimStartMs))
                    currentPosMs = clamped
                    lastSeekTargetMs = clamped
                    if (abs(clamped - lastReportedMs) >= 40L) {
                        lastReportedMs = clamped
                        onPositionUpdate?.invoke(clamped)
                    }
                    if (pos >= trimEndMs + 35L || exoPlayer.playbackState == Player.STATE_ENDED) {
                        exoPlayer.seekTo(trimStartMs)
                        exoPlayer.play()
                    }
                    kotlinx.coroutines.delay(40)
                }
            }
        } else if (!isPlaying || isPausedExternal) {
            if (useSystemMediaPlayer) {
                runCatching { if (nativeMediaPlayer?.isPlaying == true) nativeMediaPlayer?.pause() }
            } else {
                exoPlayer.pause()
            }
        }
    }

    LaunchedEffect(isMuted, nativeMediaPlayer) {
        val v = if (isMuted) 0f else 1f
        runCatching { nativeMediaPlayer?.setVolume(v, v) }
    }

    val togglePlayPause = {
        if (previewSeekTimeMs != null) {
            val targetResumeMs = if (isBoundaryPreview) {
                currentPosMs.coerceIn(trimStartMs, trimEndMs.coerceAtLeast(trimStartMs))
            } else {
                previewSeekTimeMs
            }
            onClearPreviewSeek?.invoke()
            isPlaying = true
            if (useSystemMediaPlayer) {
                runCatching {
                    val seekTarget = if (targetResumeMs >= trimEndMs - 40L || targetResumeMs < trimStartMs) trimStartMs else targetResumeMs
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        nativeMediaPlayer?.seekTo(seekTarget, MediaPlayer.SEEK_CLOSEST)
                    } else {
                        nativeMediaPlayer?.seekTo(seekTarget.toInt())
                    }
                    nativeMediaPlayer?.start()
                }
            } else {
                if (targetResumeMs >= trimEndMs - 40L || targetResumeMs < trimStartMs) {
                    exoPlayer.seekTo(trimStartMs)
                } else if (abs(exoPlayer.currentPosition - targetResumeMs) > 50L) {
                    exoPlayer.seekTo(targetResumeMs)
                }
                exoPlayer.playWhenReady = true
                exoPlayer.play()
            }
        } else {
            isPlaying = !isPlaying
            if (isPlaying) {
                if (useSystemMediaPlayer) {
                    runCatching {
                        val cur = nativeMediaPlayer?.currentPosition?.toLong() ?: 0L
                        if (cur >= trimEndMs - 40L || cur < trimStartMs) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                nativeMediaPlayer?.seekTo(trimStartMs, MediaPlayer.SEEK_CLOSEST)
                            } else {
                                nativeMediaPlayer?.seekTo(trimStartMs.toInt())
                            }
                        }
                        nativeMediaPlayer?.start()
                    }
                } else {
                    if (exoPlayer.currentPosition >= trimEndMs - 40L || exoPlayer.currentPosition < trimStartMs) {
                        exoPlayer.seekTo(trimStartMs)
                    }
                    exoPlayer.playWhenReady = true
                    exoPlayer.play()
                }
            } else {
                if (useSystemMediaPlayer) {
                    runCatching { nativeMediaPlayer?.pause() }
                } else {
                    exoPlayer.pause()
                }
            }
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF0E1015))
            .clickable { togglePlayPause() }
    ) {
        if (useSystemMediaPlayer) {
            SystemMediaPlayerTextureView(
                videoUri = videoUri,
                initialSeekMs = trimStartMs,
                isPlaying = isPlaying && !isPausedExternal && previewSeekTimeMs == null,
                isMuted = isMuted,
                onPlayerReady = { mp, dur ->
                    nativeMediaPlayer = mp
                    if (dur in 100L..36_000_000L) {
                        resolvedPlayerDurationMs = dur
                        currentOnDurationDetected?.invoke(dur)
                    }
                },
                onPlayerReleased = {
                    nativeMediaPlayer = null
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            ExoTextureVideoView(
                exoPlayer = exoPlayer,
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT,
                modifier = Modifier.fillMaxSize()
            )
        }

        AnimatedVisibility(
            visible = !isPlaying && previewSeekTimeMs == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.55f),
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f))
                    )
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.20f))
                        .clickable { togglePlayPause() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                val displayedMs = if (isBoundaryPreview && previewSeekTimeMs != null) previewSeekTimeMs else currentPosMs
                val absPosSec = (displayedMs.coerceAtLeast(0L) / 100L) / 10f
                val clipSec = ((trimEndMs - trimStartMs).coerceAtLeast(0L) / 100L) / 10f
                val effectiveTotalMs = maxOf(videoDurationMs, resolvedPlayerDurationMs, trimEndMs)
                val fullVideoSec = (effectiveTotalMs.coerceAtLeast(0L) / 100L) / 10f
                Text(
                    text = "${"%.1f".format(absPosSec)}s / ${"%.1f".format(fullVideoSec)}s (片段 ${"%.1f".format(clipSec)}s)",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.White.copy(alpha = 0.20f),
                    modifier = Modifier.clickable {
                        playbackSpeed = when (playbackSpeed) {
                            0.5f -> 1.0f
                            1.0f -> 1.5f
                            1.5f -> 2.0f
                            else -> 0.5f
                        }
                    }
                ) {
                    Text(
                        text = "${playbackSpeed}x",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.20f))
                        .clickable { isMuted = !isMuted },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SystemMediaPlayerTextureView(
    videoUri: Uri,
    initialSeekMs: Long,
    isPlaying: Boolean,
    isMuted: Boolean,
    onPlayerReady: (MediaPlayer, Long) -> Unit,
    onPlayerReleased: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentOnReady by rememberUpdatedState(onPlayerReady)
    val currentOnReleased by rememberUpdatedState(onPlayerReleased)

    AndroidView(
        factory = { ctx ->
            val textureView = TextureView(ctx).apply {
                isOpaque = false
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            var mp: MediaPlayer? = null
            var videoW = 0
            var videoH = 0

            val layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                if (videoW > 0 && videoH > 0) {
                    applyTextureViewScaleMatrix(textureView, videoW, videoH)
                }
            }
            textureView.addOnLayoutChangeListener(layoutListener)

            fun openMediaPlayer(surfaceTexture: SurfaceTexture) {
                try {
                    mp?.release()
                } catch (_: Exception) {}
                val player = MediaPlayer()
                mp = player
                try {
                    player.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
                    )
                    player.setSurface(Surface(surfaceTexture))
                    player.setDataSource(context, videoUri)
                    player.isLooping = true
                    val vol = if (isMuted) 0f else 1f
                    player.setVolume(vol, vol)
                    player.setOnVideoSizeChangedListener { _, w, h ->
                        if (w > 0 && h > 0) {
                            videoW = w
                            videoH = h
                            applyTextureViewScaleMatrix(textureView, w, h)
                        }
                    }
                    player.setOnPreparedListener { preparedMp ->
                        videoW = preparedMp.videoWidth
                        videoH = preparedMp.videoHeight
                        if (videoW > 0 && videoH > 0) {
                            applyTextureViewScaleMatrix(textureView, videoW, videoH)
                        }
                        if (initialSeekMs > 0L) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                preparedMp.seekTo(initialSeekMs, MediaPlayer.SEEK_CLOSEST)
                            } else {
                                preparedMp.seekTo(initialSeekMs.toInt())
                            }
                        }
                        if (isPlaying) {
                            preparedMp.start()
                        }
                        currentOnReady(preparedMp, preparedMp.duration.toLong())
                    }
                    player.prepareAsync()
                } catch (e: Exception) {
                    DebugLogManager.w("SystemMediaPlayer", "Failed preparing MediaPlayer: ${e.message}")
                }
            }

            textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                    openMediaPlayer(surface)
                }

                override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                    if (videoW > 0 && videoH > 0) {
                        applyTextureViewScaleMatrix(textureView, videoW, videoH)
                    }
                }

                override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                    try {
                        mp?.stop()
                    } catch (_: Exception) {}
                    try {
                        mp?.release()
                    } catch (_: Exception) {}
                    mp = null
                    currentOnReleased()
                    return true
                }

                override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
            }

            textureView.tag = Pair(videoUri.toString(), layoutListener)
            textureView
        },
        update = { textureView ->
            @Suppress("UNCHECKED_CAST")
            val tagPair = textureView.tag as? Pair<String, View.OnLayoutChangeListener>
            if (tagPair != null && tagPair.first != videoUri.toString()) {
                val st = textureView.surfaceTexture
                if (st != null) {
                    textureView.surfaceTextureListener?.onSurfaceTextureDestroyed(st)
                    textureView.tag = Pair(videoUri.toString(), tagPair.second)
                    textureView.surfaceTextureListener?.onSurfaceTextureAvailable(st, textureView.width, textureView.height)
                }
            }
        },
        modifier = modifier
    )
}
