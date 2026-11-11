package com.yehor.finora.designsystem.chart

import android.graphics.BlurMaskFilter
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.State
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.yehor.finora.designsystem.motion.FinoraMotion
import com.yehor.finora.designsystem.motion.rememberReduceMotion
import com.yehor.finora.designsystem.theme.GrainBrush
import com.yehor.finora.designsystem.theme.SpaceGrotesk
import com.yehor.finora.designsystem.theme.drawGrain
import com.yehor.finora.designsystem.theme.finance
import com.yehor.finora.designsystem.theme.hairline
import com.yehor.finora.designsystem.theme.rememberGrainAlpha
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** Pan/zoom callbacks for the wave chart; wired up by the Statistics screen (not handled yet). */
data class WaveGestures(
    val onPan: (deltaFraction: Float) -> Unit,
    val onZoom: (scale: Float, focusFraction: Float) -> Unit,
    val onGestureEnd: () -> Unit,
)

/** Extra drawing room around the chart so the glow, halos and round caps are never clipped at its edges. */
private val Bleed = 16.dp

/**
 * Smooth area chart. Tap or hold-and-drag scrubs to the nearest index ([onSelect]); tapping the
 * selected index again clears it. [sparkline] draws only a 2dp line (no glow, fill, live point, scrub
 * or bubble). [gestures] is accepted for later wiring and is not handled yet.
 *
 * All paths are built once per (values, compare, size, zeroLine); animation frames only move clips,
 * the selection marker and the live-point pulse. The canvas extends [Bleed] beyond the chart's bounds
 * so glow and halos can spill over the edges instead of being cut off.
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
    val pulse: State<Float>? = if (!sparkline && !instant) {
        rememberInfiniteTransition(label = "livePoint").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "livePulse",
        )
    } else {
        null
    }

    var size by remember { mutableStateOf(IntSize.Zero) }
    val currentValues by rememberUpdatedState(values)
    val currentSelected by rememberUpdatedState(selectedIndex)
    val currentOnSelect by rememberUpdatedState(onSelect)

    val density = LocalDensity.current
    val padTop = with(density) { (if (sparkline) 4.dp else 30.dp).toPx() }
    val padBottom = with(density) { 4.dp.toPx() }
    val bleedPx = with(density) { Bleed.toPx() }
    val strokePx = with(density) { (if (sparkline) 2.dp else 2.75.dp).toPx() }
    val w = size.width.toFloat()
    val h = size.height.toFloat()

    val geometry = remember(values, compare, w, h, zeroLine, padTop, padBottom, sparkline) {
        WaveGeometryCache.build(values, compare, w, h, padTop, padBottom, zeroLine, sparkline)
    }
    val below = finance.negative
    val glowAbove = if (color == null) finance.accentGlow else lineColor.copy(alpha = finance.accentGlow.alpha)
    val paints = remember(geometry, lineColor, below, glowAbove, scheme.onSurfaceVariant, strokePx, density) {
        WavePaints(geometry, lineColor, below, glowAbove, scheme.onSurfaceVariant, strokePx, density.density)
    }

    // Selection marker glides between indices on a spring; instant (no animation) under reduce motion.
    val target = selectedIndex?.takeIf { !sparkline }?.let { geometry.pointAt(it) }
    val selX = remember { Animatable(0f) }
    val selY = remember { Animatable(0f) }
    var animatedIndex by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(target, selectedIndex) {
        if (target == null) {
            animatedIndex = null
            return@LaunchedEffect
        }
        val glide = !instant && animatedIndex != null && animatedIndex != selectedIndex
        animatedIndex = selectedIndex
        if (glide) {
            launch { selX.animateTo(target.x, FinoraMotion.bouncy) }
            selY.animateTo(target.y, FinoraMotion.bouncy)
        } else {
            selX.snapTo(target.x)
            selY.snapTo(target.y)
        }
    }
    val showSelection = target != null && (instant || animatedIndex != null)
    fun markerX(): Float = if (instant) target?.x ?: 0f else selX.value
    fun markerY(): Float = if (instant) target?.y ?: 0f else selY.value

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

    val surface = scheme.surface
    val onSurface = scheme.onSurface
    Box(modifier.then(scrub).onSizeChanged { size = it }) {
        Canvas(
            Modifier
                .fillMaxSize()
                .bleed(bleedPx.roundToInt())
                .graphicsLayer(compositingStrategy = if (sparkline) CompositingStrategy.Auto else CompositingStrategy.Offscreen),
        ) {
            if (w <= 0f || h <= 0f) return@Canvas
            translate(bleedPx, bleedPx) {
                val b = bleedPx
                val zeroY = geometry.zeroY
                val splitY = if (zeroLine) zeroY else null
                clipRect(-b, -b, -b + progress.value * (w + 2 * b), h + b) {
                    geometry.compare.forEach { drawPath(it, paints.compareColor, style = paints.compareStroke) }
                    if (!sparkline) {
                        geometry.runs.forEach { run ->
                            val fill = run.fill ?: return@forEach
                            sides(splitY, w, h, b) { aboveZero ->
                                drawPath(fill, if (aboveZero) paints.fillAbove else paints.fillBelow)
                                drawGrain(GrainBrush, grainAlpha, fill)
                            }
                        }
                        if (!reduceMotion) {
                            drawIntoCanvas { canvas ->
                                geometry.runs.forEach { run ->
                                    val native = run.line.asAndroidPath()
                                    sides(splitY, w, h, b) { aboveZero ->
                                        canvas.nativeCanvas.drawPath(native, if (aboveZero) paints.glowAbove else paints.glowBelow)
                                    }
                                }
                            }
                        }
                    }
                    geometry.runs.forEach { run ->
                        sides(splitY, w, h, b) { aboveZero ->
                            drawPath(run.line, if (aboveZero) paints.strokeAbove else paints.strokeBelow, style = paints.stroke)
                        }
                    }
                    geometry.dots.forEach { p ->
                        drawCircle(colorAt(p, splitY, lineColor, below), strokePx, Offset(p.x, p.y))
                    }
                }
                if (zeroLine && zeroY != null && !sparkline) {
                    drawLine(
                        onSurface.copy(alpha = 0.22f),
                        Offset(0f, zeroY),
                        Offset(w, zeroY),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 5.dp.toPx())),
                    )
                }
                val latest = geometry.latest
                if (!sparkline && latest != null && geometry.latestIndex != selectedIndex) {
                    val appear = ((progress.value - 0.85f) / 0.15f).coerceIn(0f, 1f)
                    if (appear > 0f) {
                        val t = pulse?.value ?: 0f
                        val c = colorAt(latest, splitY, lineColor, below)
                        val center = Offset(latest.x, latest.y)
                        drawCircle(c.copy(alpha = (0.35f + (0.10f - 0.35f) * t) * appear), 8.dp.toPx() * (1f + 0.6f * t), center)
                        drawCircle(surface.copy(alpha = appear), 6.dp.toPx(), center)
                        drawCircle(c.copy(alpha = appear), 4.dp.toPx(), center)
                    }
                }
                if (showSelection) {
                    val x = markerX()
                    val y = markerY()
                    val c = colorAt(WavePoint(x, y), splitY, lineColor, below)
                    drawLine(
                        scheme.onSurfaceVariant.copy(alpha = 0.6f),
                        Offset(x, padTop),
                        Offset(x, h),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())),
                    )
                    drawCircle(c.copy(alpha = 0.22f), 9.dp.toPx(), Offset(x, y))
                    drawCircle(onSurface, 5.dp.toPx(), Offset(x, y))
                    drawCircle(c, 5.dp.toPx(), Offset(x, y), style = paints.ring)
                }
            }
        }
        val sel = selectedIndex
        if (showSelection && sel != null && w > 0f) {
            val shape = RoundedCornerShape(12.dp)
            Text(
                label(sel),
                style = MaterialTheme.typography.labelLarge.copy(fontFamily = SpaceGrotesk, fontFeatureSettings = "tnum"),
                color = onSurface,
                modifier = Modifier
                    .layout { measurable, constraints ->
                        val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                        layout(p.width, p.height) {
                            val px = (markerX() - p.width / 2f).roundToInt()
                                .coerceIn(0, (w.roundToInt() - p.width).coerceAtLeast(0))
                            p.place(px, 0)
                        }
                    }
                    .background(scheme.surfaceContainerHighest, shape)
                    .border(hairline(finance.isDark), shape)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

/** Grows the node by [px] on every side without changing the size it reports to its parent. */
private fun Modifier.bleed(px: Int): Modifier = layout { measurable, constraints ->
    val width = if (constraints.hasBoundedWidth) constraints.maxWidth else 0
    val height = if (constraints.hasBoundedHeight) constraints.maxHeight else 0
    val p = measurable.measure(Constraints.fixed(width + 2 * px, height + 2 * px))
    layout(width, height) { p.place(-px, -px) }
}

/** Cached geometry for one (values, compare, size, zeroLine) combination. */
private class WaveGeometryCache(
    val runs: List<WaveRun>,
    val dots: List<WavePoint>,
    val compare: List<Path>,
    val zeroY: Float?,
    val baseline: Float,
    val peakY: Float,
    val troughY: Float,
    private val points: Array<WavePoint?>,
    val width: Float,
) {
    val latestIndex: Int? = points.indices.lastOrNull().takeIf { points.lastOrNull() != null }
    val latest: WavePoint? = points.lastOrNull()

    fun pointAt(index: Int): WavePoint? = points.getOrNull(index)

    class WaveRun(val line: Path, val fill: Path?)

    companion object {
        fun build(
            values: List<Long?>,
            compare: List<Long?>?,
            w: Float,
            h: Float,
            padTop: Float,
            padBottom: Float,
            zeroLine: Boolean,
            sparkline: Boolean,
        ): WaveGeometryCache {
            val layout = WaveGeometry.layout(values, w, h, padTop, padBottom, zeroLine, compare.orEmpty())
            val zeroY = layout.zeroY
            val baseline = if (zeroLine && zeroY != null) zeroY else h
            val runs = mutableListOf<WaveRun>()
            val dots = mutableListOf<WavePoint>()
            layout.runs.forEach { run ->
                if (run.size < 2) {
                    dots += run
                    return@forEach
                }
                val line = smoothPath(run, zeroY, h)
                val fill = if (sparkline) {
                    null
                } else {
                    Path().apply {
                        addPath(line)
                        lineTo(run.last().x, baseline)
                        lineTo(run.first().x, baseline)
                        close()
                    }
                }
                runs += WaveRun(line, fill)
            }
            val comparePaths = compare?.let { WaveGeometry.layout(it, w, h, padTop, padBottom, zeroLine, values) }
                ?.runs?.filter { it.size >= 2 }?.map { smoothPath(it, null, h) }.orEmpty()
            val flat = layout.runs.flatten()
            // Points come out in index order, so the n-th present value owns the n-th point.
            val points = arrayOfNulls<WavePoint>(values.size)
            var k = 0
            values.forEachIndexed { i, v -> if (v != null) points[i] = flat[k++] }
            return WaveGeometryCache(
                runs = runs,
                dots = dots,
                compare = comparePaths,
                zeroY = zeroY,
                baseline = baseline,
                peakY = flat.minOfOrNull { it.y } ?: 0f,
                troughY = flat.maxOfOrNull { it.y } ?: h,
                points = points,
                width = w,
            )
        }

        private fun smoothPath(run: List<WavePoint>, zeroY: Float?, h: Float): Path = Path().apply {
            moveTo(run[0].x, run[0].y)
            WaveGeometry.smooth(run, zeroY, 0f, h).forEach { cubicTo(it.c1.x, it.c1.y, it.c2.x, it.c2.y, it.to.x, it.to.y) }
        }
    }
}

/** Brushes, strokes and blur paints for one geometry + palette; built once, reused every frame. */
private class WavePaints(
    g: WaveGeometryCache,
    line: Color,
    below: Color,
    glow: Color,
    variant: Color,
    strokePx: Float,
    density: Float,
) {
    val strokeAbove: Brush = Brush.horizontalGradient(listOf(line.copy(alpha = 0.7f), line), 0f, g.width.coerceAtLeast(1f))
    val strokeBelow: Brush = Brush.horizontalGradient(listOf(below.copy(alpha = 0.7f), below), 0f, g.width.coerceAtLeast(1f))
    val fillAbove: Brush = easedFade(line, g.peakY, g.baseline)
    val fillBelow: Brush = easedFade(below, g.troughY, g.zeroY ?: g.baseline)
    val stroke = Stroke(strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val ring = Stroke(2.5f * density)
    val glowAbove = blurPaint(glow, strokePx * 2f, 10f * density)
    val glowBelow = blurPaint(below.copy(alpha = 0.3f), strokePx * 2f, 10f * density)
    val compareColor = variant.copy(alpha = 0.55f)
    val compareStroke = Stroke(
        1.5f * density,
        cap = StrokeCap.Round,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f * density, 5f * density)),
    )

    private fun easedFade(color: Color, from: Float, to: Float): Brush {
        // Keep the gradient non-degenerate when the curve touches its own baseline.
        val end = if (abs(to - from) < 1f) from + (if (to >= from) 1f else -1f) else to
        return Brush.verticalGradient(
            0f to color.copy(alpha = 0.32f),
            0.45f to color.copy(alpha = 0.14f),
            0.8f to color.copy(alpha = 0.04f),
            1f to color.copy(alpha = 0f),
            startY = from,
            endY = end,
        )
    }

    private fun blurPaint(color: Color, width: Float, radius: Float) = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = width
        strokeCap = android.graphics.Paint.Cap.ROUND
        strokeJoin = android.graphics.Paint.Join.ROUND
        this.color = color.toArgb()
        maskFilter = BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL)
    }
}

/** Runs [block] once (no split) or twice, clipped above and below [zeroY]; the flag says which side. */
private inline fun DrawScope.sides(zeroY: Float?, w: Float, h: Float, b: Float, block: DrawScope.(aboveZero: Boolean) -> Unit) {
    if (zeroY == null) {
        block(true)
    } else {
        clipRect(-b, -b, w + b, zeroY) { block(true) }
        clipRect(-b, zeroY, w + b, h + b) { block(false) }
    }
}

private fun colorAt(p: WavePoint, zeroY: Float?, above: Color, below: Color): Color =
    if (zeroY != null && p.y > zeroY) below else above

private fun nearestIndex(values: List<Long?>, x: Float, width: Float): Int? {
    if (values.isEmpty() || width <= 0f) return null
    val raw = if (values.size == 1) 0 else (x / width * (values.size - 1)).roundToInt().coerceIn(0, values.size - 1)
    return values.indices.filter { values[it] != null }.minByOrNull { abs(it - raw) }
}
