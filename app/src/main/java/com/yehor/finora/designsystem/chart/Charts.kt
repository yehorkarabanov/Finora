package com.yehor.finora.designsystem.chart

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yehor.finora.designsystem.motion.FinoraMotion
import com.yehor.finora.designsystem.motion.rememberReduceMotion

/*
 * Canvas charts. Each takes plain data, draws shapes only, and carries a content description. The
 * numbers are always also shown as text next to the chart (design section 7), so a chart is never
 * the only carrier of a figure.
 */

data class DonutSlice(val value: Long, val color: Color)

private const val GAP_DEGREES = 2.5f

/**
 * A thin ring with rounded, separated segments. Tapping a segment reports its index (null when the
 * tap is outside the ring); [selectedIndex] is drawn thicker and the others are dimmed.
 */
@Composable
fun DonutChart(
    slices: List<DonutSlice>,
    description: String,
    modifier: Modifier = Modifier,
    diameter: Dp = 176.dp,
    strokeWidth: Dp = 18.dp,
    selectedIndex: Int? = null,
    onSliceClick: ((Int?) -> Unit)? = null,
    center: (@Composable () -> Unit)? = null,
) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    // Previews and screenshots render a single frame, so they start fully drawn.
    val inspection = LocalInspectionMode.current
    val progress = remember { Animatable(if (inspection) 1f else 0f) }
    val reduceMotion = rememberReduceMotion()
    LaunchedEffect(Unit) {
        if (reduceMotion) progress.snapTo(1f) else progress.animateTo(1f, tween(durationMillis = FinoraMotion.CHART_DRAW_MS))
    }
    val total = slices.sumOf { it.value.coerceAtLeast(0L) }
    // Start and sweep in degrees, clockwise from 12 o'clock, for drawing and hit testing.
    val arcs = remember(slices) {
        var start = -90f
        slices.map { slice ->
            val sweep = if (total > 0L) 360f * slice.value.coerceAtLeast(0L).toFloat() / total.toFloat() else 0f
            (start to sweep).also { start += sweep }
        }
    }

    Box(
        modifier
            .size(diameter)
            .clearAndSetSemantics { contentDescription = description }
            .then(
                if (onSliceClick == null) {
                    Modifier
                } else {
                    Modifier.pointerInput(arcs) {
                        detectTapGestures { offset ->
                            onSliceClick(hitTest(offset, size.width.toFloat(), strokeWidth.toPx(), arcs))
                        }
                    }
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(diameter)) {
            val baseStroke = strokeWidth.toPx()
            val maxStroke = baseStroke * 1.4f
            val inset = maxStroke / 2
            val arcSize = Size(size.width - maxStroke, size.height - maxStroke)
            val topLeft = Offset(inset, inset)
            val radius = arcSize.width / 2
            drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(baseStroke))
            if (total <= 0L) return@Canvas

            val visible = slices.count { it.value > 0L }
            val reveal = 360f * progress.value
            slices.forEachIndexed { index, slice ->
                val (start, sweep) = arcs[index]
                if (sweep <= 0f) return@forEachIndexed
                val selected = index == selectedIndex
                val stroke = if (selected) maxStroke else baseStroke
                // Round caps reach half a stroke past the arc ends; leave room for them and the gap.
                val capDegrees = Math.toDegrees((stroke / 2 / radius).toDouble()).toFloat()
                val trim = if (visible > 1) capDegrees + GAP_DEGREES / 2 else 0f
                val drawStart = start + trim
                val clippedEnd = minOf(start + sweep - trim, -90f + reveal)
                val drawSweep = (clippedEnd - drawStart).coerceAtLeast(0f)
                if (drawSweep <= 0f && reveal < 360f) return@forEachIndexed
                val alpha = if (selectedIndex == null || selected) 1f else 0.35f
                drawArc(
                    color = slice.color.copy(alpha = alpha),
                    startAngle = drawStart,
                    // A tiny slice still shows as a dot.
                    sweepAngle = drawSweep.coerceAtLeast(0.1f),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(stroke, cap = if (visible > 1) StrokeCap.Round else StrokeCap.Butt),
                )
            }
        }
        if (center != null) {
            Box(Modifier.size(diameter - strokeWidth * 3), contentAlignment = Alignment.Center) { center() }
        }
    }
}

/** Index of the arc under [offset], or null when the tap is off the ring. */
private fun hitTest(offset: Offset, side: Float, stroke: Float, arcs: List<Pair<Float, Float>>): Int? {
    val c = side / 2
    val dx = offset.x - c
    val dy = offset.y - c
    val distance = kotlin.math.sqrt(dx * dx + dy * dy)
    val ringRadius = (side - stroke * 1.4f) / 2
    if (distance < ringRadius - stroke * 1.2f || distance > ringRadius + stroke * 1.2f) return null
    // atan2 gives -180..180 with 0 at 3 o'clock; arcs start at -90 and run to 270.
    var angle = Math.toDegrees(kotlin.math.atan2(dy, dx).toDouble()).toFloat()
    if (angle < -90f) angle += 360f
    return arcs.indexOfFirst { (start, sweep) -> sweep > 0f && angle >= start && angle < start + sweep }
        .takeIf { it >= 0 }
}

data class Bar(val label: String, val value: Long)

/** Vertical bars scaled to the largest value. Negative values are drawn as zero. */
@Composable
fun TrendBars(
    bars: List<Bar>,
    color: Color,
    description: String,
    modifier: Modifier = Modifier,
    onBarClick: ((Int) -> Unit)? = null,
    showLabels: Boolean = true,
) {
    Column(modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description }) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(120.dp)
                .pointerInput(bars.size, onBarClick) {
                    if (onBarClick != null && bars.isNotEmpty()) {
                        detectTapGestures { offset ->
                            val index = (offset.x / (size.width.toFloat() / bars.size)).toInt()
                            onBarClick(index.coerceIn(0, bars.lastIndex))
                        }
                    }
                },
        ) {
            if (bars.isEmpty()) return@Canvas
            val max = bars.maxOf { it.value.coerceAtLeast(0L) }
            val slot = size.width / bars.size
            val barWidth = slot * 0.6f
            bars.forEachIndexed { i, bar ->
                val value = bar.value.coerceAtLeast(0L)
                val height = if (max == 0L) 0f else size.height * value.toFloat() / max.toFloat()
                val minHeight = if (value > 0L) 2.dp.toPx() else 0f
                val h = height.coerceAtLeast(minHeight)
                drawRect(
                    color = color,
                    topLeft = Offset(slot * i + (slot - barWidth) / 2, size.height - h),
                    size = Size(barWidth, h),
                )
            }
        }
        if (showLabels) {
            Row(Modifier.fillMaxWidth()) {
                bars.forEach { bar ->
                    Text(
                        bar.label,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** A line through [values], with a zero line when the values cross zero. */
@Composable
fun BalanceLine(
    values: List<Long>,
    color: Color,
    description: String,
    modifier: Modifier = Modifier,
) {
    val axis = MaterialTheme.colorScheme.outlineVariant
    Canvas(
        modifier
            .fillMaxWidth()
            .height(140.dp)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        if (values.isEmpty()) return@Canvas
        val min = minOf(values.min(), 0L)
        val max = maxOf(values.max(), 0L)
        val range = (max - min).coerceAtLeast(1L).toFloat()
        fun y(value: Long) = size.height - size.height * (value - min).toFloat() / range
        fun x(index: Int) = if (values.size == 1) size.width / 2 else size.width * index / (values.size - 1)

        drawLine(axis, Offset(0f, y(0)), Offset(size.width, y(0)), strokeWidth = 1.dp.toPx())
        if (values.size == 1) {
            drawCircle(color, radius = 4.dp.toPx(), center = Offset(x(0), y(values[0])))
            return@Canvas
        }
        val path = Path()
        values.forEachIndexed { i, v ->
            if (i == 0) path.moveTo(x(i), y(v)) else path.lineTo(x(i), y(v))
        }
        drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
    }
}
