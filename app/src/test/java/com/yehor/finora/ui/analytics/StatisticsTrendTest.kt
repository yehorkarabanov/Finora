package com.yehor.finora.ui.analytics

import com.yehor.finora.domain.analytics.Bucket
import com.yehor.finora.domain.analytics.BucketUnit
import com.yehor.finora.domain.analytics.PeriodCalculator
import com.yehor.finora.domain.analytics.PeriodKind
import com.yehor.finora.domain.analytics.ResolvedPeriod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset

class StatisticsTrendTest {
    private val UTC = ZoneOffset.UTC
    private val nov10 = LocalDate.of(2026, 11, 10)
    private val MONTH = PeriodKind.MONTH
    private val YEAR = PeriodKind.YEAR
    private val WEEK = PeriodKind.WEEK

    private fun resolved(kind: PeriodKind, date: LocalDate): ResolvedPeriod =
        PeriodCalculator.resolve(PeriodCalculator.preset(kind, date), UTC, DayOfWeek.MONDAY)

    private fun buckets(n: Int): List<Bucket> = (0 until n).map { i ->
        val day = nov10.plusDays(i.toLong())
        Bucket(i, day, day, 0L, 0L)
    }

    @Test fun monthUsesDayBuckets() = assertEquals(BucketUnit.DAY, trendBuckets(resolved(MONTH, nov10), UTC, null).first)

    @Test fun yearUsesMonthBuckets() = assertEquals(12, trendBuckets(resolved(YEAR, nov10), UTC, null).second.size)

    @Test fun alignShorterPrevious() = assertEquals(listOf(0, 1, null), alignPrevious(buckets(3), buckets(2)))

    @Test fun weekUsesSevenDays() = assertEquals(7, trendBuckets(resolved(WEEK, nov10), UTC, null).second.size)

    @Test fun monthBucketsCoverEveryDay() {
        val (_, list) = trendBuckets(resolved(MONTH, nov10), UTC, null)
        assertEquals(30, list.size)
        assertEquals(LocalDate.of(2026, 11, 1), list.first().fromDate)
        assertEquals(LocalDate.of(2026, 11, 30), list.last().toDateInclusive)
    }

    @Test fun customBandKeepsThePreviousUnit() {
        val start = LocalDate.of(2026, 7, 1)
        val period = PeriodCalculator.resolve(
            com.yehor.finora.domain.analytics.Period(PeriodKind.CUSTOM, start, start.plusDays(94)), UTC, DayOfWeek.MONDAY,
        )
        assertEquals(BucketUnit.MONTH, trendBuckets(period, UTC, BucketUnit.MONTH).first)
        assertEquals(BucketUnit.DAY, trendBuckets(period, UTC, BucketUnit.DAY).first)
    }

    @Test fun monthBucketsAreClippedToThePeriod() {
        val period = PeriodCalculator.resolve(
            com.yehor.finora.domain.analytics.Period(PeriodKind.CUSTOM, LocalDate.of(2026, 3, 15), LocalDate.of(2026, 9, 10)),
            UTC, DayOfWeek.MONDAY,
        )
        val (unit, list) = trendBuckets(period, UTC, null)
        assertEquals(BucketUnit.MONTH, unit)
        assertEquals(7, list.size)
        assertEquals(LocalDate.of(2026, 3, 15), list.first().fromDate)
        assertEquals(LocalDate.of(2026, 9, 10), list.last().toDateInclusive)
    }

    // ---- gesture accumulation ----

    private val sep = ChartWindow(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30))

    @Test fun smallPansAddUpToWholeDays() {
        val gesture = WindowGesture()
        var w = sep
        repeat(10) { w = gesture.pan(w, -0.01f, nov10) } // 10 × 0.3 day = 3 days
        assertEquals(LocalDate.of(2026, 8, 29), w.start)
    }

    @Test fun panForwardAtTodayDoesNothing() {
        val atToday = ChartWindow(nov10.minusDays(29), nov10)
        assertEquals(atToday, WindowGesture().pan(atToday, 0.5f, nov10))
    }

    @Test fun smallPinchesAddUp() {
        val gesture = WindowGesture()
        var w = ChartWindow(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 7))
        repeat(20) { w = gesture.zoom(w, 0.98f, 0.5f, nov10) }
        assertTrue(w.days > 7)
    }

    @Test fun pinchAtTheLimitDoesNotWindUp() {
        val gesture = WindowGesture()
        var w = ChartWindow(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 7))
        repeat(50) { w = gesture.zoom(w, 1.2f, 0.5f, nov10) }
        assertEquals(7L, w.days)
        w = gesture.zoom(w, 0.8f, 0.5f, nov10)
        assertTrue(w.days > 7)
    }

    // ---- values ----

    private fun cell(minor: Long?, count: Int = 1) = com.yehor.finora.domain.analytics.Cell(
        estimate = minor?.let { com.yehor.finora.domain.money.Estimate(it, pln, com.yehor.finora.domain.money.EstimateBasis.NATIVE) },
        operationCount = count,
        excluded = emptySet(),
        overflowed = false,
        oldestFetchedAt = null,
    )
    private val pln = com.yehor.finora.domain.money.CurrencyRegistry.get("PLN")

    @Test fun futureBucketsAreNotDrawnAndQuietOnesAreZero() {
        val points = buckets(3).zip(listOf(cell(null, count = 0), cell(5), cell(7))) { b, c ->
            com.yehor.finora.domain.analytics.TrendPoint(b, c)
        }
        assertEquals(listOf(0L, 5L, null), trendValues(points, nov10.plusDays(1)))
    }

    @Test fun averageCountsOnlyElapsedBuckets() {
        val points = buckets(3).zip(listOf(cell(4), cell(6), cell(100))) { b, c ->
            com.yehor.finora.domain.analytics.TrendPoint(b, c)
        }
        assertEquals(5L, trendAverage(points, nov10.plusDays(1), pln)?.amountMinor)
    }
}
