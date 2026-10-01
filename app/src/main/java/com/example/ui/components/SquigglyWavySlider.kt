package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.sin

@Composable
fun SquigglyWavySlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    isPlaying: Boolean = false,
    waveAmplitude: Dp = 4.dp,
    waveFrequency: Float = 0.045f,
    activeTrackColor: Color = MaterialTheme.colorScheme.primary,
    inactiveTrackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    thumbColor: Color = MaterialTheme.colorScheme.primary,
    onValueChangeFinished: (() -> Unit)? = null
) {
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnValueChangeFinished by rememberUpdatedState(onValueChangeFinished)
    val rangeSpan = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.0001f)
    val normalizedFraction = ((value - valueRange.start) / rangeSpan).coerceIn(0f, 1f)

    val wavePhase = remember { Animatable(0f) }
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            wavePhase.animateTo(
                targetValue = (2 * Math.PI).toFloat(),
                animationSpec = infiniteRepeatable(
                    animation = tween(1200, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                )
            )
        } else {
            wavePhase.snapTo(0f)
        }
    }

    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(normalizedFraction) }

    val currentFraction = if (isDragging) dragFraction else normalizedFraction

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .pointerInput(valueRange) {
                detectTapGestures { offset ->
                    val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                    val newValue = valueRange.start + fraction * rangeSpan
                    currentOnValueChange(newValue)
                    currentOnValueChangeFinished?.invoke()
                }
            }
            .pointerInput(valueRange) {
                detectDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                        dragFraction = fraction
                        val newValue = valueRange.start + fraction * rangeSpan
                        currentOnValueChange(newValue)
                    },
                    onDragEnd = {
                        isDragging = false
                        currentOnValueChangeFinished?.invoke()
                    },
                    onDragCancel = {
                        isDragging = false
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                        dragFraction = fraction
                        val newValue = valueRange.start + fraction * rangeSpan
                        currentOnValueChange(newValue)
                    }
                )
            }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 4.dp)
        ) {
            val width = size.width
            val height = size.height
            val centerY = height / 2f
            val thumbX = width * currentFraction
            val amplitudePx = waveAmplitude.toPx()
            val strokeThickness = 3.5.dp.toPx()

            if (thumbX < width) {
                val startX = (thumbX + 4.dp.toPx()).coerceAtMost(width)
                drawLine(
                    color = inactiveTrackColor,
                    start = Offset(startX, centerY),
                    end = Offset(width, centerY),
                    strokeWidth = strokeThickness,
                    cap = StrokeCap.Round
                )
            }

            if (thumbX > 0f) {
                val wavePath = Path()
                wavePath.moveTo(0f, centerY)

                val step = 4f
                var x = 0f
                val phase = wavePhase.value

                while (x <= thumbX) {
                    val progressToThumb = if (thumbX > 0f) (x / thumbX) else 0f
                    val edgeDamping = when {
                        x < 16f -> x / 16f
                        (thumbX - x) < 16f -> (thumbX - x) / 16f
                        else -> 1f
                    }.coerceIn(0f, 1f)

                    val y = centerY + sin(x * waveFrequency + phase) * amplitudePx * edgeDamping
                    wavePath.lineTo(x, y)
                    x += step
                }
                wavePath.lineTo(thumbX, centerY)

                drawPath(
                    path = wavePath,
                    color = activeTrackColor,
                    style = Stroke(
                        width = strokeThickness,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }

            val thumbWidth = 4.5.dp.toPx()
            val thumbHeight = 22.dp.toPx()
            val thumbRadius = 2.25.dp.toPx()
            val thumbLeft = (thumbX - thumbWidth / 2f).coerceIn(0f, width - thumbWidth)
            val thumbTop = centerY - thumbHeight / 2f

            drawRoundRect(
                color = thumbColor,
                topLeft = Offset(thumbLeft, thumbTop),
                size = androidx.compose.ui.geometry.Size(thumbWidth, thumbHeight),
                cornerRadius = CornerRadius(thumbRadius, thumbRadius)
            )
        }
    }
}
