package com.yehor.finora.designsystem.chart

import kotlin.math.abs

data class WavePoint(val x: Float, val y: Float)

data class CubicSegment(val from: WavePoint, val c1: WavePoint, val c2: WavePoint, val to: WavePoint)

data class WaveLayout(
    val runs: List<List<WavePoint>>,
    val zeroY: Float?,
    val minValue: Long,
    val maxValue: Long,
)

/** Pure geometry for the wave chart (no Compose types). y grows downward. */
object WaveGeometry {

    /**
     * Maps values to points in [width]x[height] with [padTop]/[padBottom]; null values split the
     * result into separate runs. Range always includes 0 when [includeZero].
     */
    fun layout(
        values: List<Long?>,
        width: Float,
        height: Float,
        padTop: Float,
        padBottom: Float,
        includeZero: Boolean,
        sharedScale: List<Long?> = emptyList(),
    ): WaveLayout {
        val present = values.filterNotNull()
        if (present.isEmpty()) return WaveLayout(emptyList(), null, 0L, 0L)
        val ranged = present + sharedScale.filterNotNull()
        var lo = ranged.min()
        var hi = ranged.max()
        if (includeZero) {
            lo = minOf(lo, 0L)
            hi = maxOf(hi, 0L)
        }
        val top = padTop
        val bottom = height - padBottom
        val span = (hi - lo).toFloat()
        fun yOf(v: Long): Float =
            if (span <= 0f) (top + bottom) / 2f else bottom - (v - lo).toFloat() / span * (bottom - top)

        val n = values.size
        fun xOf(i: Int): Float = if (n <= 1) width / 2f else i * width / (n - 1)

        val runs = mutableListOf<List<WavePoint>>()
        var current = mutableListOf<WavePoint>()
        values.forEachIndexed { i, v ->
            if (v == null) {
                if (current.isNotEmpty()) runs += current
                current = mutableListOf()
            } else {
                current += WavePoint(xOf(i), yOf(v))
            }
        }
        if (current.isNotEmpty()) runs += current
        val zeroY = if (lo <= 0L && hi >= 0L) yOf(0L) else null
        return WaveLayout(runs, zeroY, lo, hi)
    }

    /** Cardinal-spline tension: each handle is [TENSION] / 2 of the chord between a point's neighbours. */
    private const val TENSION = 0.4f

    /** How far (fraction of a segment's |Δy|) a control point may sit beyond the segment's own endpoints. */
    private const val OVERSHOOT = 0.12f

    /**
     * Cardinal (Catmull–Rom family) spline through one run, as cubic Béziers. Each point's tangent is
     * shared by both adjacent segments (so the curve stays C1-smooth) and its vertical part is scaled
     * down until every control point lies within [OVERSHOOT] of its segment's y-range, inside
     * [top]..[bottom], and — when both ends of a segment are on the same side of [zeroY] — on that side.
     */
    fun smooth(
        run: List<WavePoint>,
        zeroY: Float? = null,
        top: Float = Float.NEGATIVE_INFINITY,
        bottom: Float = Float.POSITIVE_INFINITY,
    ): List<CubicSegment> {
        val n = run.size
        if (n < 2) return emptyList()
        val k = TENSION / 2f
        val tx = FloatArray(n) { (run[minOf(it + 1, n - 1)].x - run[maxOf(it - 1, 0)].x) * k }
        val ty = FloatArray(n) { (run[minOf(it + 1, n - 1)].y - run[maxOf(it - 1, 0)].y) * k }
        val lo = FloatArray(n - 1)
        val hi = FloatArray(n - 1)
        for (j in 0 until n - 1) {
            val a = run[j].y
            val b = run[j + 1].y
            val slack = OVERSHOOT * abs(b - a)
            var l = minOf(a, b) - slack
            var h = maxOf(a, b) + slack
            if (zeroY != null) {
                if (a <= zeroY && b <= zeroY) h = minOf(h, zeroY)
                if (a >= zeroY && b >= zeroY) l = maxOf(l, zeroY)
            }
            lo[j] = maxOf(l, top)
            hi[j] = minOf(h, bottom)
        }
        // Largest scale in 0..1 so that y + offset * scale stays within lo..hi.
        fun limit(y: Float, offset: Float, l: Float, h: Float): Float = when {
            offset > 0f -> ((h - y) / offset).coerceIn(0f, 1f)
            offset < 0f -> ((l - y) / offset).coerceIn(0f, 1f)
            else -> 1f
        }
        for (i in 0 until n) {
            var scale = 1f
            if (i < n - 1) scale = minOf(scale, limit(run[i].y, ty[i], lo[i], hi[i]))
            if (i > 0) scale = minOf(scale, limit(run[i].y, -ty[i], lo[i - 1], hi[i - 1]))
            ty[i] *= scale
        }
        return List(n - 1) { j ->
            val p0 = run[j]
            val p1 = run[j + 1]
            CubicSegment(
                p0,
                WavePoint((p0.x + tx[j]).coerceIn(p0.x, p1.x), p0.y + ty[j]),
                WavePoint((p1.x - tx[j + 1]).coerceIn(p0.x, p1.x), p1.y - ty[j + 1]),
                p1,
            )
        }
    }
}
