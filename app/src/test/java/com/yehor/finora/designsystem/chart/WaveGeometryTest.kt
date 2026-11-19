package com.yehor.finora.designsystem.chart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class WaveGeometryTest {
    @Test fun flatSeriesIsHorizontal() {
        val l = WaveGeometry.layout(listOf(5L, 5L, 5L), 300f, 100f, 0f, 0f, includeZero = false)
        assertTrue(l.runs.single().all { it.y == l.runs.single()[0].y })
    }

    @Test fun nullSplitsRuns() =
        assertEquals(2, WaveGeometry.layout(listOf(1L, null, 3L, 4L), 300f, 100f, 0f, 0f, false).runs.size)

    @Test fun zeroLineInsideRange() {
        val l = WaveGeometry.layout(listOf(-50L, 50L), 100f, 100f, 0f, 0f, true)
        assertEquals(50f, l.zeroY!!, 0.01f)
    }

    @Test fun singlePointIsCentered() =
        assertEquals(150f, WaveGeometry.layout(listOf(7L), 300f, 100f, 0f, 0f, false).runs.single().single().x, 0.01f)

    @Test fun allZeroDoesNotDivideByZero() {
        val l = WaveGeometry.layout(listOf(0L, 0L), 100f, 100f, 0f, 0f, true)
        assertTrue(l.runs.single().all { it.y.isFinite() })
    }

    @Test fun smoothDoesNotOvershootMonotone() {
        val segs = WaveGeometry.smooth(listOf(WavePoint(0f, 100f), WavePoint(10f, 50f), WavePoint(20f, 0f)))
        segs.forEach { s -> assertTrue(s.c1.y in 0f..100f && s.c2.y in 0f..100f) }
    }

    @Test fun peaksAreRounded() {
        val l = WaveGeometry.layout(listOf(0L, 100L, 0L), 200f, 100f, 0f, 0f, includeZero = false)
        val run = l.runs.single()
        val peakY = run[1].y
        val span = abs(run[0].y - peakY)
        val controls = WaveGeometry.smooth(run).flatMap { listOf(it.c1, it.c2) }
        assertTrue(controls.any { abs(it.y - peakY) > 0.5f })
        assertTrue(controls.none { it.y < peakY - 0.12f * span })
    }

    @Test fun asymmetricPeakIsNotFlat() {
        // An extremum between unequal neighbours keeps a tilted tangent (no flat shelf at the peak).
        val l = WaveGeometry.layout(listOf(0L, 100L, 60L), 200f, 100f, 0f, 0f, includeZero = false)
        val run = l.runs.single()
        val (before, after) = WaveGeometry.smooth(run)
        assertTrue(abs(before.c2.y - run[1].y) > 0.5f || abs(after.c1.y - run[1].y) > 0.5f)
    }

    @Test fun noCrossingZeroBetweenSameSign() {
        val l = WaveGeometry.layout(listOf(10L, 80L, 15L), 300f, 100f, 0f, 0f, includeZero = true)
        val zeroY = l.zeroY!!
        WaveGeometry.smooth(l.runs.single(), zeroY = zeroY).forEach { s ->
            assertTrue(s.c1.y <= zeroY && s.c2.y <= zeroY)
        }
    }

    @Test fun controlPointsStayInBandAndBounds() {
        val values = listOf(0L, 100L, 95L, 100L, 10L, 12L, 80L, 0L)
        val l = WaveGeometry.layout(values, 700f, 100f, 0f, 0f, includeZero = true)
        WaveGeometry.smooth(l.runs.single(), zeroY = l.zeroY, top = 0f, bottom = 100f).forEach { s ->
            val dy = abs(s.to.y - s.from.y)
            val lo = minOf(s.from.y, s.to.y) - 0.12f * dy - 0.01f
            val hi = maxOf(s.from.y, s.to.y) + 0.12f * dy + 0.01f
            listOf(s.c1, s.c2).forEach { c ->
                assertTrue("y=${c.y} outside [$lo, $hi]", c.y in lo..hi && c.y in 0f..100f)
                assertTrue(c.x in s.from.x..s.to.x)
            }
        }
    }

    @Test fun emptyValuesGiveNoRuns() =
        assertTrue(WaveGeometry.layout(emptyList(), 100f, 100f, 0f, 0f, false).runs.isEmpty())
}
