package com.yehor.finora.ui.common.charts

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Canvas charts. Each takes plain data, draws shapes only, and carries a content description. The
 * numbers are always also shown as text next to the chart (design section 7), so a chart is never
 * the only carrier of a figure.
 */

data class DonutSlice(val value: Long, val color: Color)

@Composable
fun DonutChart(
    slices: List<DonutSlice>,
    description: String,
    modifier: Modifier = Modifier,
    centerText: String? = null,
) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(
        modifier.size(160.dp).clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(160.dp)) {
            val stroke = 28.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)
            drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            val total = slices.sumOf { it.value.coerceAtLeast(0L) }
            if (total > 0L) {
                var start = -90f
                for (slice in slices) {
                    val part = slice.value.coerceAtLeast(0L)
                    if (part == 0L) continue
                    val sweep = 360f * part.toFloat() / total.toFloat()
                    drawArc(slice.color, start, sweep, false, topLeft, arcSize, style = Stroke(stroke))
                    start += sweep
                }
            }
        }
        if (centerText != null) {
            Text(
                centerText,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.size(96.dp),
            )
        }
    }
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
