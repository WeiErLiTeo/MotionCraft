package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

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
    showDirectInput: Boolean = true
) {
    val durationSec = (videoDuration / 100L) / 10f
    val validDurationSec = if (durationSec <= 0.1f) 0.1f else durationSec
    val validDurationMs = videoDuration.coerceAtLeast(100L)

    val startSec = (trimStartMs / 100L) / 10f
    val endSec = (trimEndMs / 100L) / 10f

    var startInputText by remember { mutableStateOf("%.1f".format(startSec)) }
    var endInputText by remember { mutableStateOf("%.1f".format(endSec)) }

    LaunchedEffect(startSec) {
        val parsed = startInputText.toFloatOrNull()
        if (parsed == null || kotlin.math.abs(parsed - startSec) > 0.05f) {
            startInputText = "%.1f".format(startSec)
        }
    }

    LaunchedEffect(endSec) {
        val parsed = endInputText.toFloatOrNull()
        if (parsed == null || kotlin.math.abs(parsed - endSec) > 0.05f) {
            endInputText = "%.1f".format(endSec)
        }
    }

    val handleYellow = Color(0xFFFACC15)
    val density = LocalDensity.current

    val currentTrimStartMs by rememberUpdatedState(trimStartMs)
    val currentTrimEndMs by rememberUpdatedState(trimEndMs)
    val currentValidDurationMs by rememberUpdatedState(validDurationMs)
    val currentPlaybackPosMs by rememberUpdatedState(currentPlaybackMs)
    val currentOnTrimChanged by rememberUpdatedState(onTrimChanged)
    val currentOnScrubbing by rememberUpdatedState(onScrubbing)

    Column(modifier = modifier.fillMaxWidth()) {
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
                    text = "起点: ${"%.1f".format(startSec)}s",
                    color = handleYellow,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }

            val rangeSec = (endSec - startSec).coerceAtLeast(0f)
            Text(
                text = "已选时长: ${"%.1f".format(rangeSec)}s",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = handleYellow.copy(alpha = 0.2f)
            ) {
                Text(
                    text = "终点: ${"%.1f".format(endSec)}s",
                    color = handleYellow,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
        ) {
            val totalWidthPx = constraints.maxWidth.toFloat()
            val handleWidthDp = 20.dp
            val handleWidthPx = with(density) { handleWidthDp.toPx() }

            val usableWidth = (totalWidthPx - handleWidthPx * 2).coerceAtLeast(1f)
            val currentUsableWidth by rememberUpdatedState(usableWidth)

            val leftHandleX = (trimStartMs.toFloat() / validDurationMs.toFloat() * usableWidth).coerceIn(0f, usableWidth)
            val rightHandleX = (handleWidthPx + trimEndMs.toFloat() / validDurationMs.toFloat() * usableWidth).coerceIn(leftHandleX + handleWidthPx, totalWidthPx - handleWidthPx)

            val middleWidthPx = (rightHandleX - (leftHandleX + handleWidthPx)).coerceAtLeast(0f)
            val middleWidthDp = with(density) { middleWidthPx.toDp() }
            val currentMiddleWidthPx by rememberUpdatedState(middleWidthPx)

            val playheadMsClamped = currentPlaybackMs.coerceIn(trimStartMs, trimEndMs)
            val playheadProgress = if (trimEndMs > trimStartMs) {
                (playheadMsClamped - trimStartMs).toFloat() / (trimEndMs - trimStartMs).toFloat()
            } else 0f
            val playheadPx = (leftHandleX + handleWidthPx + playheadProgress * middleWidthPx).coerceIn(leftHandleX + handleWidthPx, rightHandleX)

            var middleDragAccumulator by remember { mutableFloatStateOf(0f) }
            var leftDragAccumulator by remember { mutableFloatStateOf(0f) }
            var rightDragAccumulator by remember { mutableFloatStateOf(0f) }
            var playheadDragAccumulator by remember { mutableFloatStateOf(0f) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1E212B))
            ) {
                if (frames.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        frames.forEach { frame ->
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

                Box(
                    modifier = Modifier
                        .offset { IntOffset((leftHandleX + handleWidthPx).roundToInt(), 0) }
                        .width(middleWidthDp)
                        .fillMaxHeight()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { offset ->
                                    if (currentMiddleWidthPx > 0f) {
                                        val ratio = (offset.x / currentMiddleWidthPx).coerceIn(0f, 1f)
                                        val tappedMs = (currentTrimStartMs + ratio * (currentTrimEndMs - currentTrimStartMs)).toLong()
                                        currentOnScrubbing?.invoke(tappedMs)
                                    }
                                }
                            )
                        }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { middleDragAccumulator = 0f },
                                onDragEnd = { currentOnScrubbing?.invoke(null) },
                                onDragCancel = { currentOnScrubbing?.invoke(null) }
                            ) { change, dragAmount ->
                                change.consume()
                                if (currentUsableWidth > 0f) {
                                    middleDragAccumulator += (dragAmount.x / currentUsableWidth) * currentValidDurationMs
                                    val deltaMs = middleDragAccumulator.toLong()
                                    if (deltaMs != 0L) {
                                        val windowLen = currentTrimEndMs - currentTrimStartMs
                                        var newStart = currentTrimStartMs + deltaMs
                                        var newEnd = currentTrimEndMs + deltaMs
                                        if (newStart < 0L) {
                                            newStart = 0L
                                            newEnd = windowLen
                                        } else if (newEnd > currentValidDurationMs) {
                                            newEnd = currentValidDurationMs
                                            newStart = (currentValidDurationMs - windowLen).coerceAtLeast(0L)
                                        }
                                        middleDragAccumulator -= deltaMs
                                        currentOnTrimChanged(newStart, newEnd)
                                        currentOnScrubbing?.invoke(newStart)
                                    }
                                }
                            }
                        }
                )

                Box(
                    modifier = Modifier
                        .offset { IntOffset(leftHandleX.roundToInt(), 0) }
                        .width(handleWidthDp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
                        .background(handleYellow)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { leftDragAccumulator = 0f },
                                onDragEnd = { currentOnScrubbing?.invoke(null) },
                                onDragCancel = { currentOnScrubbing?.invoke(null) }
                            ) { change, dragAmount ->
                                change.consume()
                                if (currentUsableWidth > 0f) {
                                    leftDragAccumulator += (dragAmount.x / currentUsableWidth) * currentValidDurationMs
                                    val deltaMs = leftDragAccumulator.toLong()
                                    if (deltaMs != 0L) {
                                        val newStart = (currentTrimStartMs + deltaMs).coerceIn(0L, (currentTrimEndMs - 200L).coerceAtLeast(0L))
                                        leftDragAccumulator -= deltaMs
                                        currentOnTrimChanged(newStart, currentTrimEndMs)
                                        currentOnScrubbing?.invoke(newStart)
                                    }
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .offset { IntOffset(rightHandleX.roundToInt(), 0) }
                        .width(handleWidthDp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp))
                        .background(handleYellow)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { rightDragAccumulator = 0f },
                                onDragEnd = { currentOnScrubbing?.invoke(null) },
                                onDragCancel = { currentOnScrubbing?.invoke(null) }
                            ) { change, dragAmount ->
                                change.consume()
                                if (currentUsableWidth > 0f) {
                                    rightDragAccumulator += (dragAmount.x / currentUsableWidth) * currentValidDurationMs
                                    val deltaMs = rightDragAccumulator.toLong()
                                    if (deltaMs != 0L) {
                                        val newEnd = (currentTrimEndMs + deltaMs).coerceIn((currentTrimStartMs + 200L).coerceAtMost(currentValidDurationMs), currentValidDurationMs)
                                        rightDragAccumulator -= deltaMs
                                        currentOnTrimChanged(currentTrimStartMs, newEnd)
                                        currentOnScrubbing?.invoke(newEnd)
                                    }
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .offset { IntOffset((playheadPx - with(density) { 2.dp.toPx() }).roundToInt(), 0) }
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(Color.White)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { playheadDragAccumulator = 0f },
                                onDragEnd = { currentOnScrubbing?.invoke(null) },
                                onDragCancel = { currentOnScrubbing?.invoke(null) }
                            ) { change, dragAmount ->
                                change.consume()
                                if (currentUsableWidth > 0f) {
                                    playheadDragAccumulator += (dragAmount.x / currentUsableWidth) * currentValidDurationMs
                                    val deltaMs = playheadDragAccumulator.toLong()
                                    if (deltaMs != 0L) {
                                        val newPos = (currentPlaybackPosMs + deltaMs).coerceIn(currentTrimStartMs, currentTrimEndMs)
                                        playheadDragAccumulator -= deltaMs
                                        currentOnScrubbing?.invoke(newPos)
                                    }
                                }
                            }
                        }
                )
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
                        Text("起始", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        Text("结束", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
