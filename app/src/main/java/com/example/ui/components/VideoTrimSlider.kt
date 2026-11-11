package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class DragTarget {
    NONE,
    LEFT_HANDLE,
    RIGHT_HANDLE,
    PLAYHEAD
}

private fun formatTimelineClock(ms: Long, includeTenths: Boolean = true): String {
    val safeMs = ms.coerceAtLeast(0L)
    val totalSec = safeMs / 1000L
    val tenths = (safeMs % 1000L) / 100L
    val hours = totalSec / 3600L
    val minutes = (totalSec % 3600L) / 60L
    val seconds = totalSec % 60L
    return when {
        hours > 0L -> {
            if (includeTenths) {
                "%d:%02d:%02d.%d".format(hours, minutes, seconds, tenths)
            } else {
                "%d:%02d:%02d".format(hours, minutes, seconds)
            }
        }
        minutes > 0L -> {
            if (includeTenths) {
                "%02d:%02d.%d".format(minutes, seconds, tenths)
            } else {
                "%02d:%02d".format(minutes, seconds)
            }
        }
        else -> {
            if (includeTenths) {
                "%.1fs".format(safeMs / 1000f)
            } else {
                "%ds".format(seconds)
            }
        }
    }
}

@Composable
fun VideoTrimSlider(
    frames: List<Bitmap>,
    videoDuration: Long,
    trimStartMs: Long,
    trimEndMs: Long,
    onTrimChanged: (Long, Long) -> Unit,
    modifier: Modifier = Modifier,
    currentPlaybackMs: Long = trimStartMs,
    onScrubbing: ((Long?) -> Unit)? = null,
    onBoundaryPreview: ((Long?) -> Unit)? = null,
    onWindowChanged: ((Long, Long) -> Unit)? = null,
    showDirectInput: Boolean = true
) {
    val validDurationMs = maxOf(videoDuration, trimEndMs, 100L)
    val showMacroBar = validDurationMs > 15_000L

    // Default to full-length (0 = 全长) so the main track always spans 0..validDurationMs without unexpected clamping.
    // Users can optionally tap "15s放大" or "30s放大" if they want zoomed fine-tuning on long videos.
    var windowSpanPresetMs by remember(validDurationMs) {
        mutableLongStateOf(0L)
    }
    val activeWindowSpanMs = if (windowSpanPresetMs <= 0L || windowSpanPresetMs >= validDurationMs) {
        validDurationMs
    } else {
        windowSpanPresetMs.coerceIn(5_000L, validDurationMs)
    }

    var windowStartMs by remember(validDurationMs) { mutableLongStateOf(0L) }

    LaunchedEffect(activeWindowSpanMs, validDurationMs) {
        if (activeWindowSpanMs >= validDurationMs) {
            windowStartMs = 0L
        } else {
            val maxWinStart = (validDurationMs - activeWindowSpanMs).coerceAtLeast(0L)
            windowStartMs = windowStartMs.coerceIn(0L, maxWinStart)
        }
    }

    val windowEndMs = (windowStartMs + activeWindowSpanMs).coerceAtMost(validDurationMs)
    val effectiveWindowSpanMs = (windowEndMs - windowStartMs).coerceAtLeast(100L)

    LaunchedEffect(windowStartMs, windowEndMs) {
        if (validDurationMs > 0L) {
            onWindowChanged?.invoke(windowStartMs, windowEndMs)
        }
    }

    val startSec = (trimStartMs / 100L) / 10f
    val endSec = (trimEndMs / 100L) / 10f

    var startInputText by remember { mutableStateOf("%.1f".format(startSec)) }
    var endInputText by remember { mutableStateOf("%.1f".format(endSec)) }

    LaunchedEffect(startSec) {
        val parsed = startInputText.toFloatOrNull()
        if (parsed == null || abs(parsed - startSec) > 0.05f) {
            startInputText = "%.1f".format(startSec)
        }
    }

    LaunchedEffect(endSec) {
        val parsed = endInputText.toFloatOrNull()
        if (parsed == null || abs(parsed - endSec) > 0.05f) {
            endInputText = "%.1f".format(endSec)
        }
    }

    val handleYellow = Color(0xFFFACC15)
    val density = LocalDensity.current

    val currentTrimStartMs by rememberUpdatedState(trimStartMs)
    val currentTrimEndMs by rememberUpdatedState(trimEndMs)
    val currentValidDurationMs by rememberUpdatedState(validDurationMs)
    val currentWindowStartMs by rememberUpdatedState(windowStartMs)
    val currentWindowEndMs by rememberUpdatedState(windowEndMs)
    val currentWindowSpanMs by rememberUpdatedState(effectiveWindowSpanMs)
    val currentOnTrimChanged by rememberUpdatedState(onTrimChanged)
    val currentOnScrubbing by rememberUpdatedState(onScrubbing)
    val currentOnBoundaryPreview by rememberUpdatedState(onBoundaryPreview)

    var localScrubMs by remember { mutableStateOf<Long?>(null) }

    val shiftClipWindowToStart: (Long) -> Unit = { rawNewClipStartMs ->
        val clipLen = (currentTrimEndMs - currentTrimStartMs).coerceIn(500L, currentValidDurationMs)
        val maxClipStart = (currentValidDurationMs - clipLen).coerceAtLeast(0L)
        val newClipStart = rawNewClipStartMs.coerceIn(0L, maxClipStart)
        val newClipEnd = (newClipStart + clipLen).coerceAtMost(currentValidDurationMs)
        if (activeWindowSpanMs < currentValidDurationMs) {
            val maxWinStart = (currentValidDurationMs - activeWindowSpanMs).coerceAtLeast(0L)
            val center = (newClipStart + newClipEnd) / 2L
            windowStartMs = (center - activeWindowSpanMs / 2L).coerceIn(0L, maxWinStart)
        }
        currentOnTrimChanged(newClipStart, newClipEnd)
        currentOnScrubbing?.invoke(newClipStart)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        if (showMacroBar) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                )
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${formatTimelineClock(trimStartMs, false)} ~ ${formatTimelineClock(trimEndMs, false)} / ${formatTimelineClock(validDurationMs, true)}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (activeWindowSpanMs < validDurationMs) {
                                    "${activeWindowSpanMs / 1000L}s (${formatTimelineClock(windowStartMs, false)} ~ ${formatTimelineClock(windowEndMs, false)})"
                                } else {
                                    "0.0s ~ ${formatTimelineClock(validDurationMs, true)}"
                                },
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            val fullPresetLabel = stringResource(R.string.timeline_full_length)
                            val win15sLabel = stringResource(R.string.timeline_15s_window)
                            val win30sLabel = stringResource(R.string.timeline_30s_window)
                            val presets = buildList {
                                add(0L to fullPresetLabel)
                                add(15_000L to win15sLabel)
                                if (validDurationMs > 30_000L) {
                                    add(30_000L to win30sLabel)
                                }
                            }
                            presets.forEach { (spanVal, label) ->
                                val isSel = (spanVal == 0L && activeWindowSpanMs >= validDurationMs) ||
                                    (spanVal > 0L && activeWindowSpanMs == spanVal && activeWindowSpanMs < validDurationMs)
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.clickable {
                                        windowSpanPresetMs = spanVal
                                        if (spanVal > 0L && spanVal < currentValidDurationMs) {
                                            val maxWinStart = (currentValidDurationMs - spanVal).coerceAtLeast(0L)
                                            windowStartMs = currentTrimStartMs.coerceIn(0L, maxWinStart)
                                            if (currentTrimEndMs > windowStartMs + spanVal) {
                                                currentOnTrimChanged(
                                                    currentTrimStartMs,
                                                    (windowStartMs + spanVal).coerceAtMost(currentValidDurationMs)
                                                )
                                            }
                                        } else {
                                            windowStartMs = 0L
                                        }
                                    }
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF151821))
                            .pointerInput(validDurationMs) {
                                detectTapGestures { tapOffset ->
                                    val w = size.width.toFloat().coerceAtLeast(1f)
                                    val ratio = (tapOffset.x / w).coerceIn(0f, 1f)
                                    val targetStart = (ratio * currentValidDurationMs).toLong()
                                    shiftClipWindowToStart(targetStart)
                                }
                            }
                            .pointerInput(validDurationMs) {
                                detectDragGestures(
                                    onDragEnd = { currentOnScrubbing?.invoke(null) },
                                    onDragCancel = { currentOnScrubbing?.invoke(null) }
                                ) { change, _ ->
                                    change.consume()
                                    val w = size.width.toFloat().coerceAtLeast(1f)
                                    val ratio = (change.position.x / w).coerceIn(0f, 1f)
                                    val targetStart = (ratio * currentValidDurationMs).toLong()
                                    shiftClipWindowToStart(targetStart)
                                }
                            }
                    ) {
                        val macroWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
                        val clipLeftRatio = (trimStartMs.toFloat() / validDurationMs.toFloat()).coerceIn(0f, 1f)
                        val clipRightRatio = (trimEndMs.toFloat() / validDurationMs.toFloat()).coerceIn(clipLeftRatio, 1f)

                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val tickCount = 20
                            for (i in 1 until tickCount) {
                                val x = size.width * (i.toFloat() / tickCount)
                                drawLine(
                                    color = Color.White.copy(alpha = 0.16f),
                                    start = Offset(x, size.height * 0.25f),
                                    end = Offset(x, size.height * 0.75f),
                                    strokeWidth = 1.dp.toPx()
                                )
                            }

                            val selLeftPx = clipLeftRatio * macroWidthPx
                            val selWidthPx = ((clipRightRatio - clipLeftRatio) * macroWidthPx).coerceAtLeast(10.dp.toPx())
                            drawRoundRect(
                                color = handleYellow.copy(alpha = 0.30f),
                                topLeft = Offset(selLeftPx.coerceIn(0f, macroWidthPx - selWidthPx), 2.dp.toPx()),
                                size = Size(selWidthPx, size.height - 4.dp.toPx()),
                                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                            )
                            drawRoundRect(
                                color = handleYellow,
                                topLeft = Offset(selLeftPx.coerceIn(0f, macroWidthPx - selWidthPx), 2.dp.toPx()),
                                size = Size(selWidthPx, size.height - 4.dp.toPx()),
                                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                                style = Stroke(width = 1.5.dp.toPx())
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val jumpSteps = buildList {
                            if (validDurationMs >= 20 * 60_000L) {
                                add(-600_000L to "-10分")
                            }
                            if (validDurationMs >= 60_000L) {
                                add(-60_000L to "-1分")
                            }
                            add(-5_000L to "-5秒")
                            add(5_000L to "+5秒")
                            if (validDurationMs >= 60_000L) {
                                add(60_000L to "+1分")
                            }
                            if (validDurationMs >= 20 * 60_000L) {
                                add(600_000L to "+10分")
                            }
                        }

                        jumpSteps.forEach { (deltaMs, label) ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 2.dp)
                                    .clickable {
                                        shiftClipWindowToStart(currentTrimStartMs + deltaMs)
                                    }
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 5.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = handleYellow.copy(alpha = 0.2f)
            ) {
                Text(
                    text = stringResource(R.string.trim_start_label, "%.1f".format(startSec)),
                    color = handleYellow,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }

            val rangeSec = (endSec - startSec).coerceAtLeast(0f)
            val totalVideoSec = (validDurationMs / 100L) / 10f
            Text(
                text = "${stringResource(R.string.trim_selected_duration, "%.1f".format(rangeSec))} (总长 ${"%.1f".format(totalVideoSec)}s)",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = handleYellow.copy(alpha = 0.2f)
            ) {
                Text(
                    text = stringResource(R.string.trim_end_label, "%.1f".format(endSec)),
                    color = handleYellow,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }

        // Main Thumbnail Track: Touch gestures are handled in the stationary parent coordinate space (0..totalWidthPx)
        // so handle offsets never shift the pointerInput coordinate system during a drag!
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(66.dp)
        ) {
            val totalWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
            val handleWidthDp = 22.dp
            val handleWidthPx = with(density) { handleWidthDp.toPx() }
            val usableWidth = (totalWidthPx - handleWidthPx * 2f).coerceAtLeast(1f)

            val relStartFraction = ((trimStartMs - windowStartMs).toFloat() / effectiveWindowSpanMs.toFloat()).coerceIn(0f, 1f)
            val relEndFraction = ((trimEndMs - windowStartMs).toFloat() / effectiveWindowSpanMs.toFloat()).coerceIn(relStartFraction, 1f)

            val leftHandleX = (relStartFraction * usableWidth).coerceIn(0f, usableWidth)
            val rightHandleX = (handleWidthPx + relEndFraction * usableWidth).coerceIn(leftHandleX + handleWidthPx, totalWidthPx - handleWidthPx)

            val middleWidthPx = (rightHandleX - (leftHandleX + handleWidthPx)).coerceAtLeast(1f)

            val displayedPlayheadMs = (localScrubMs ?: currentPlaybackMs).coerceIn(trimStartMs, trimEndMs)
            val playheadProgress = if (trimEndMs > trimStartMs) {
                (displayedPlayheadMs - trimStartMs).toFloat() / (trimEndMs - trimStartMs).toFloat()
            } else 0f
            val playheadPx = (leftHandleX + handleWidthPx + playheadProgress * middleWidthPx).coerceIn(leftHandleX + handleWidthPx, rightHandleX)

            val currentLeftHandleX by rememberUpdatedState(leftHandleX)
            val currentRightHandleX by rememberUpdatedState(rightHandleX)
            val currentUsableWidth by rememberUpdatedState(usableWidth)

            var activeDragTarget by remember { mutableStateOf(DragTarget.NONE) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1E212B))
                    .pointerInput(validDurationMs) {
                        detectTapGestures { tapOffset ->
                            val x = tapOffset.x
                            val lStart = currentLeftHandleX + handleWidthPx
                            val rStart = currentRightHandleX
                            if (x in lStart..rStart && rStart > lStart) {
                                val ratio = ((x - lStart) / (rStart - lStart)).coerceIn(0f, 1f)
                                val tappedMs = (currentTrimStartMs + ratio * (currentTrimEndMs - currentTrimStartMs)).toLong()
                                localScrubMs = null
                                currentOnScrubbing?.invoke(tappedMs)
                            } else {
                                // Tapping outside the handles moves the nearest handle to that point
                                val fraction = ((x - handleWidthPx) / currentUsableWidth).coerceIn(0f, 1f)
                                val targetMs = (currentWindowStartMs + fraction * currentWindowSpanMs).toLong()
                                    .coerceIn(currentWindowStartMs, currentWindowEndMs)
                                val distToLeft = abs(x - (currentLeftHandleX + handleWidthPx / 2f))
                                val distToRight = abs(x - (currentRightHandleX + handleWidthPx / 2f))
                                if (distToLeft <= distToRight) {
                                    val newStart = targetMs.coerceIn(currentWindowStartMs, (currentTrimEndMs - 200L).coerceAtLeast(currentWindowStartMs))
                                    currentOnTrimChanged(newStart, currentTrimEndMs)
                                    currentOnScrubbing?.invoke(newStart)
                                } else {
                                    val newEnd = targetMs.coerceIn((currentTrimStartMs + 200L).coerceAtMost(currentWindowEndMs), currentWindowEndMs)
                                    currentOnTrimChanged(currentTrimStartMs, newEnd)
                                    currentOnScrubbing?.invoke(newEnd)
                                }
                            }
                        }
                    }
                    .pointerInput(validDurationMs) {
                        detectDragGestures(
                            onDragStart = { startOffset ->
                                val x = startOffset.x
                                val leftCenter = currentLeftHandleX + handleWidthPx / 2f
                                val rightCenter = currentRightHandleX + handleWidthPx / 2f
                                val hitSlop = handleWidthPx * 1.45f
                                val distLeft = abs(x - leftCenter)
                                val distRight = abs(x - rightCenter)

                                activeDragTarget = when {
                                    distLeft <= hitSlop && distLeft <= distRight -> DragTarget.LEFT_HANDLE
                                    distRight <= hitSlop -> DragTarget.RIGHT_HANDLE
                                    else -> DragTarget.PLAYHEAD
                                }
                            },
                            onDragEnd = {
                                when (activeDragTarget) {
                                    DragTarget.LEFT_HANDLE, DragTarget.RIGHT_HANDLE -> {
                                        (currentOnBoundaryPreview ?: currentOnScrubbing)?.invoke(null)
                                    }
                                    DragTarget.PLAYHEAD -> {
                                        val finalMs = localScrubMs?.coerceIn(currentTrimStartMs, currentTrimEndMs)
                                        localScrubMs = null
                                        if (finalMs != null) {
                                            currentOnScrubbing?.invoke(finalMs)
                                        }
                                    }
                                    DragTarget.NONE -> {}
                                }
                                activeDragTarget = DragTarget.NONE
                            },
                            onDragCancel = {
                                localScrubMs = null
                                (currentOnBoundaryPreview ?: currentOnScrubbing)?.invoke(null)
                                activeDragTarget = DragTarget.NONE
                            }
                        ) { change, _ ->
                            change.consume()
                            val fingerX = change.position.x
                            when (activeDragTarget) {
                                DragTarget.LEFT_HANDLE -> {
                                    val fraction = ((fingerX - handleWidthPx / 2f) / currentUsableWidth).coerceIn(0f, 1f)
                                    val rawMs = (currentWindowStartMs + fraction * currentWindowSpanMs).toLong()
                                    val maxStart = (currentTrimEndMs - 200L).coerceAtLeast(currentWindowStartMs)
                                    val newStart = rawMs.coerceIn(currentWindowStartMs, maxStart)
                                    currentOnTrimChanged(newStart, currentTrimEndMs)
                                    (currentOnBoundaryPreview ?: currentOnScrubbing)?.invoke(newStart)
                                }
                                DragTarget.RIGHT_HANDLE -> {
                                    val fraction = ((fingerX - handleWidthPx * 1.5f) / currentUsableWidth).coerceIn(0f, 1f)
                                    val rawMs = (currentWindowStartMs + fraction * currentWindowSpanMs).toLong()
                                    val minEnd = (currentTrimStartMs + 200L).coerceAtMost(currentWindowEndMs)
                                    val newEnd = rawMs.coerceIn(minEnd, currentWindowEndMs)
                                    currentOnTrimChanged(currentTrimStartMs, newEnd)
                                    (currentOnBoundaryPreview ?: currentOnScrubbing)?.invoke(newEnd)
                                }
                                DragTarget.PLAYHEAD -> {
                                    val lStart = currentLeftHandleX + handleWidthPx
                                    val rStart = currentRightHandleX
                                    val spanPx = (rStart - lStart).coerceAtLeast(1f)
                                    val ratio = ((fingerX - lStart) / spanPx).coerceIn(0f, 1f)
                                    val targetMs = (currentTrimStartMs + ratio * (currentTrimEndMs - currentTrimStartMs)).toLong()
                                        .coerceIn(currentTrimStartMs, currentTrimEndMs)
                                    localScrubMs = targetMs
                                    currentOnScrubbing?.invoke(targetMs)
                                }
                                DragTarget.NONE -> {}
                            }
                        }
                    }
            ) {
                if (frames.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        frames.forEach { frame ->
                            if (!frame.isRecycled) {
                                Image(
                                    bitmap = frame.asImageBitmap(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                )
                            }
                        }
                    }
                }

                Canvas(modifier = Modifier.fillMaxSize()) {
                    if (leftHandleX > 0f) {
                        drawRect(
                            color = Color.Black.copy(alpha = 0.72f),
                            topLeft = Offset(0f, 0f),
                            size = Size(leftHandleX, size.height)
                        )
                    }
                    val rightBound = rightHandleX + handleWidthPx
                    if (rightBound < totalWidthPx) {
                        drawRect(
                            color = Color.Black.copy(alpha = 0.72f),
                            topLeft = Offset(rightBound, 0f),
                            size = Size(totalWidthPx - rightBound, size.height)
                        )
                    }
                }

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeW = 3.dp.toPx()
                    val frameWidth = (rightHandleX + handleWidthPx - leftHandleX).coerceAtLeast(handleWidthPx * 2)
                    drawRoundRect(
                        color = handleYellow,
                        topLeft = Offset(leftHandleX, 0f),
                        size = Size(frameWidth, size.height),
                        cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
                        style = Stroke(width = strokeW)
                    )
                }

                // Left Yellow Trim Handle (pure visual indicator, touch handled in stationary parent Box)
                Box(
                    modifier = Modifier
                        .offset { IntOffset(leftHandleX.roundToInt(), 0) }
                        .width(handleWidthDp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
                        .background(handleYellow),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Right Yellow Trim Handle (pure visual indicator, touch handled in stationary parent Box)
                Box(
                    modifier = Modifier
                        .offset { IntOffset(rightHandleX.roundToInt(), 0) }
                        .width(handleWidthDp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp))
                        .background(handleYellow),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // White Playhead Indicator
                val playheadTouchWidthDp = 28.dp
                val playheadHalfTouchPx = with(density) { (playheadTouchWidthDp / 2).toPx() }
                Box(
                    modifier = Modifier
                        .offset { IntOffset((playheadPx - playheadHalfTouchPx).roundToInt(), 0) }
                        .width(playheadTouchWidthDp)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(3.5.dp)
                            .fillMaxHeight()
                            .background(Color.White)
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .size(width = 10.dp, height = 6.dp)
                            .background(Color.White, CircleShape)
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .size(width = 10.dp, height = 6.dp)
                            .background(Color.White, CircleShape)
                    )
                }
            }
        }

        if (showDirectInput) {
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.trim_input_start), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = {
                                val newStart = (currentTrimStartMs - 100L).coerceAtLeast(0L)
                                currentOnTrimChanged(newStart, currentTrimEndMs)
                                currentOnScrubbing?.invoke(newStart)
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(14.dp))
                        }
                        OutlinedTextField(
                            value = startInputText,
                            onValueChange = { input ->
                                startInputText = input
                                val parsed = input.toFloatOrNull()
                                if (parsed != null) {
                                    val targetMs = (parsed * 1000L).toLong()
                                    val clamped = targetMs.coerceIn(0L, (currentTrimEndMs - 200L).coerceAtLeast(0L))
                                    currentOnTrimChanged(clamped, currentTrimEndMs)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent
                            )
                        )
                        IconButton(
                            onClick = {
                                val newStart = (currentTrimStartMs + 100L).coerceAtMost(currentTrimEndMs - 200L)
                                currentOnTrimChanged(newStart, currentTrimEndMs)
                                currentOnScrubbing?.invoke(newStart)
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                        }
                        Text("s", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.trim_input_end), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = {
                                val newEnd = (currentTrimEndMs - 100L).coerceAtLeast(currentTrimStartMs + 200L)
                                currentOnTrimChanged(currentTrimStartMs, newEnd)
                                currentOnScrubbing?.invoke(newEnd)
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(14.dp))
                        }
                        OutlinedTextField(
                            value = endInputText,
                            onValueChange = { input ->
                                endInputText = input
                                val parsed = input.toFloatOrNull()
                                if (parsed != null) {
                                    val targetMs = (parsed * 1000L).toLong()
                                    val clamped = targetMs.coerceIn((currentTrimStartMs + 200L).coerceAtMost(currentValidDurationMs), currentValidDurationMs)
                                    currentOnTrimChanged(currentTrimStartMs, clamped)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent
                            )
                        )
                        IconButton(
                            onClick = {
                                val newEnd = (currentTrimEndMs + 100L).coerceAtMost(currentValidDurationMs)
                                currentOnTrimChanged(currentTrimStartMs, newEnd)
                                currentOnScrubbing?.invoke(newEnd)
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                        }
                        Text("s", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
