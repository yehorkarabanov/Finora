package com.yehor.finora.designsystem.chart

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.yehor.finora.designsystem.motion.FinoraMotion
import com.yehor.finora.designsystem.motion.rememberReduceMotion
import com.yehor.finora.designsystem.theme.GrainBrush
import com.yehor.finora.designsystem.theme.drawGrain
import com.yehor.finora.designsystem.theme.finance
import com.yehor.finora.designsystem.theme.rememberGrainAlpha
import kotlin.math.abs
import kotlin.math.roundToInt

/** Pan/zoom callbacks for the wave chart; wired up by the Statistics screen (not handled yet). */
data class WaveGestures(
    val onPan: (deltaFraction: Float) -> Unit,
    val onZoom: (scale: Float, focusFraction: Float) -> Unit,
    val onGestureEnd: () -> Unit,
)

/**
 * Smooth area chart. Tap or hold-and-drag scrubs to the nearest index ([onSelect]); tapping the
 * selected index again clears it. [sparkline] draws only a 2dp line (no glow, fill, scrub or
 * bubble). [gestures] is accepted for later wiring and is not handled yet.
 */
@Composable
fun WaveChart(
    values: List<Long?>,
    modifier: Modifier = Modifier,
    compare: List<Long?>? = null,
    zeroLine: Boolean = false,
    selectedIndex: Int? = null,
    onSelect: (Int?) -> Unit = {},
    label: (Int) -> String,
    gestures: WaveGestures? = null,
    sparkline: Boolean = false,
    color: Color? = null,
) {
    val finance = MaterialTheme.finance
    val scheme = MaterialTheme.colorScheme
    val lineColor = color ?: finance.income
    val grainBrush = if (sparkline) null else GrainBrush
    val grainAlpha = rememberGrainAlpha()
    val reduceMotion = rememberReduceMotion()
    val instant = reduceMotion || LocalInspectionMode.current

    val progress = remember { Animatable(if (instant) 1f else 0f) }
    LaunchedEffect(values) {
        if (instant) {
            progress.snapTo(1f)
        } else {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(FinoraMotion.CHART_DRAW_MS))
        }
    }

    var size by remember { mutableStateOf(IntSize.Zero) }
    val currentValues by rememberUpdatedState(values)
    val currentSelected by rememberUpdatedState(selectedIndex)
    val currentOnSelect by rememberUpdatedState(onSelect)

    val density = LocalDensity.current
    val padTop = with(density) { (if (sparkline) 4.dp else 30.dp).toPx() }
    val padBottom = with(density) { 4.dp.toPx() }
    val w = size.width.toFloat()
    val h = size.height.toFloat()
    val layout = remember(values, compare, w, h, zeroLine, padTop, padBottom) {
        WaveGeometry.layout(values, w, h, padTop, padBottom, zeroLine, compare.orEmpty())
    }
    val compareLayout = remember(values, compare, w, h, zeroLine, padTop, padBottom) {
        compare?.let { WaveGeometry.layout(it, w, h, padTop, padBottom, zeroLine, values) }
    }

    val scrub = if (sparkline) {
        Modifier
    } else {
        Modifier
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val i = nearestIndex(currentValues, offset.x, this.size.width.toFloat())
                    currentOnSelect(if (i != null && i == currentSelected) null else i)
                }
            }
            .pointerInput(Unit) {
                // Hold, then drag: vertical drags before the long press stay with the parent scroll.
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        nearestIndex(currentValues, offset.x, this.size.width.toFloat())?.let { currentOnSelect(it) }
                    },
                    onDrag = { change, _ ->
                        nearestIndex(currentValues, change.position.x, this.size.width.toFloat())
                            ?.let { currentOnSelect(it) }
                        change.consume()
                    },
                    onDragEnd = {},
                    onDragCancel = {},
                )
            }
    }

    Box(modifier.then(scrub)) {
        Canvas(
            Modifier.fillMaxSize()
                .graphicsLayer(compositingStrategy = if (sparkline) CompositingStrategy.Auto else CompositingStrategy.Offscreen)
                .onSizeChanged { size = it },
        ) {
            val strokePx = (if (sparkline) 2.dp else 3.dp).toPx()
            val zeroY = layout.zeroY
            clipRect(right = this.size.width * progress.value) {
                compareLayout?.runs?.forEach { run ->
                    drawPath(
                        smoothPath(run),
                        scheme.onSurfaceVariant,
                        style = Stroke(
                            1.5.dp.toPx(),
                            cap = StrokeCap.Round,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())),
                        ),
                    )
                }
                val baseline = if (zeroLine && zeroY != null) zeroY else this.size.height
                layout.runs.forEach { run ->
                    if (run.size < 2) {
                        run.firstOrNull()?.let { drawCircle(lineColor, strokePx, Offset(it.x, it.y)) }
                        return@forEach
                    }
                    val line = smoothPath(run)
                    if (!sparkline) {
                        val fill = Path().apply {
                            addPath(line)
                            lineTo(run.last().x, baseline)
                            lineTo(run.first().x, baseline)
                            close()
                        }
                        if (zeroLine && zeroY != null) {
                            clipRect(bottom = zeroY) {
                                drawPath(fill, fade(lineColor, 0f, zeroY))
                                grainBrush?.let { drawGrain(it, grainAlpha, fill) }
                            }
                            clipRect(top = zeroY) {
                                drawPath(fill, fade(finance.negative, this.size.height, zeroY))
                                grainBrush?.let { drawGrain(it, grainAlpha, fill) }
                            }
                        } else {
                            drawPath(fill, fade(lineColor, 0f, baseline))
                            grainBrush?.let { drawGrain(it, grainAlpha, fill) }
                        }
                        if (!reduceMotion) {
                            strokeSplit(line, finance.accentGlow, finance.accentGlow, strokePx * 3f, false, zeroY)
                        }
                    }
                    strokeSplit(line, lineColor, finance.negative, strokePx, zeroLine, zeroY)
                }
            }
            if (zeroLine && zeroY != null && !sparkline) {
                drawLine(
                    scheme.onSurface.copy(alpha = 0.22f),
                    Offset(0f, zeroY),
                    Offset(this.size.width, zeroY),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
                )
            }
            val sel = selectedIndex
            if (!sparkline && sel != null && sel in values.indices && values[sel] != null) {
                val x = xOf(sel, values.size, this.size.width)
                val point = layout.runs.flatten().firstOrNull { abs(it.x - x) < 0.5f }
                if (point != null) {
                    drawLine(
                        scheme.onSurfaceVariant.copy(alpha = 0.6f),
                        Offset(x, padTop),
                        Offset(x, this.size.height),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())),
                    )
                    drawCircle(lineColor.copy(alpha = 0.35f), 9.dp.toPx(), Offset(point.x, point.y))
                    drawCircle(scheme.onSurface, 5.dp.toPx(), Offset(point.x, point.y))
                }
            }
        }
        val sel = selectedIndex
        if (!sparkline && sel != null && sel in values.indices && values[sel] != null && w > 0f) {
            val x = xOf(sel, values.size, w)
            Text(
                label(sel),
                style = MaterialTheme.typography.labelLarge,
                color = scheme.inverseOnSurface,
                modifier = Modifier
                    .layout { measurable, constraints ->
                        val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                        layout(p.width, p.height) {
                            val px = (x - p.width / 2f).roundToInt()
                                .coerceIn(0, (w.roundToInt() - p.width).coerceAtLeast(0))
                            p.place(px, 0)
                        }
                    }
                    .background(scheme.inverseSurface, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

private fun xOf(index: Int, count: Int, width: Float): Float =
    if (count <= 1) width / 2f else index * width / (count - 1)

private fun fade(color: Color, startY: Float, endY: Float): Brush =
    Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), Color.Transparent), startY, endY)

private fun nearestIndex(values: List<Long?>, x: Float, width: Float): Int? {
    if (values.isEmpty() || width <= 0f) return null
    val raw = if (values.size == 1) 0 else (x / width * (values.size - 1)).roundToInt().coerceIn(0, values.size - 1)
    return values.indices.filter { values[it] != null }.minByOrNull { abs(it - raw) }
}

private fun smoothPath(run: List<WavePoint>): Path = Path().apply {
    if (run.isEmpty()) return@apply
    moveTo(run[0].x, run[0].y)
    WaveGeometry.smooth(run).forEach { cubicTo(it.c1.x, it.c1.y, it.c2.x, it.c2.y, it.to.x, it.to.y) }
}

/** Draws [path] in [above]; with [split] and a [zeroY], the part below the zero line uses [below]. */
private fun DrawScope.strokeSplit(
    path: Path,
    above: Color,
    below: Color,
    width: Float,
    split: Boolean,
    zeroY: Float?,
) {
    val style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round)
    if (split && zeroY != null) {
        clipRect(bottom = zeroY) { drawPath(path, above, style = style) }
        clipRect(top = zeroY) { drawPath(path, below, style = style) }
    } else {
        drawPath(path, above, style = style)
    }
}
