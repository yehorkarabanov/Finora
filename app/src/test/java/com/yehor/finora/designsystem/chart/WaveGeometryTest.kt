package com.yehor.finora.designsystem.chart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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

    @Test fun emptyValuesGiveNoRuns() =
        assertTrue(WaveGeometry.layout(emptyList(), 100f, 100f, 0f, 0f, false).runs.isEmpty())
}
