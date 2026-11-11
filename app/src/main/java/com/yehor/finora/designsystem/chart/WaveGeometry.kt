package com.yehor.finora.designsystem.chart

import kotlin.math.sqrt

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

    /** Monotone cubic (Fritsch–Carlson) segments for one run; never overshoots between monotone neighbours. */
    fun smooth(run: List<WavePoint>): List<CubicSegment> {
        val n = run.size
        if (n < 2) return emptyList()
        val dx = FloatArray(n - 1) { run[it + 1].x - run[it].x }
        val slope = FloatArray(n - 1) { if (dx[it] == 0f) 0f else (run[it + 1].y - run[it].y) / dx[it] }
        val m = FloatArray(n)
        m[0] = slope[0]
        m[n - 1] = slope[n - 2]
        for (i in 1 until n - 1) {
            m[i] = if (slope[i - 1] * slope[i] <= 0f) 0f else (slope[i - 1] + slope[i]) / 2f
        }
        for (i in 0 until n - 1) {
            if (slope[i] == 0f) {
                m[i] = 0f
                m[i + 1] = 0f
            } else {
                val a = m[i] / slope[i]
                val b = m[i + 1] / slope[i]
                val s = a * a + b * b
                if (s > 9f) {
                    val t = 3f / sqrt(s)
                    m[i] = t * a * slope[i]
                    m[i + 1] = t * b * slope[i]
                }
            }
        }
        return List(n - 1) { i ->
            val p0 = run[i]
            val p1 = run[i + 1]
            val h = dx[i] / 3f
            CubicSegment(p0, WavePoint(p0.x + h, p0.y + m[i] * h), WavePoint(p1.x - h, p1.y - m[i + 1] * h), p1)
        }
    }
}
