package com.yehor.finora.ui.analytics

import com.yehor.finora.domain.analytics.BucketUnit
import com.yehor.finora.ui.analytics.HomeMode.DAYS
import com.yehor.finora.ui.analytics.HomeMode.MONTHS
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomePeriodTest {
    private val nov10 = LocalDate.of(2026, 11, 10)
    private fun ym(y: Int, m: Int) = YearMonth.of(y, m)

    @Test fun currentDaysIsThisMonth() = assertEquals(HomePeriod(DAYS, ym(2026, 11), ym(2026, 11)), HomePeriod.current(DAYS, nov10))
    @Test fun currentMonthsIsThisYear() = assertEquals(HomePeriod(MONTHS, ym(2026, 1), ym(2026, 12)), HomePeriod.current(MONTHS, nov10))
    @Test fun shiftByRangeLength() = assertEquals(HomePeriod(DAYS, ym(2025, 7), ym(2025, 10)), HomePeriod(DAYS, ym(2025, 11), ym(2026, 2)).shifted(-1))
    @Test fun cannotShiftPastToday() = assertFalse(HomePeriod.current(DAYS, nov10).canShiftForward(nov10))
    @Test fun canShiftForwardFromPast() = assertTrue(HomePeriod(DAYS, ym(2026, 9), ym(2026, 9)).canShiftForward(nov10))
    @Test fun crossYearDayBuckets() {
        val (u, b) = HomePeriod(DAYS, ym(2025, 12), ym(2026, 1)).buckets(ZoneOffset.UTC, nov10)
        assertEquals(BucketUnit.DAY, u); assertEquals(62, b.size)
    }
    @Test fun leapFebruary() = assertEquals(29, HomePeriod(DAYS, ym(2028, 2), ym(2028, 2)).buckets(ZoneOffset.UTC, LocalDate.of(2028, 3, 1)).second.size)
    @Test fun longDaysRangeFallsBackToMonths() = assertEquals(BucketUnit.MONTH, HomePeriod(DAYS, ym(2025, 1), ym(2025, 12)).buckets(ZoneOffset.UTC, nov10).first)
    @Test fun lastElapsedIndexIsToday() = assertEquals(9, HomePeriod.current(DAYS, nov10).lastElapsedIndex(nov10))

    @Test fun monthsBucketsAndElapsedIndex() {
        val p = HomePeriod.current(MONTHS, nov10)
        assertEquals(12, p.buckets(ZoneOffset.UTC, nov10).second.size)
        assertEquals(10, p.lastElapsedIndex(nov10))
    }
    @Test fun pastAndFutureElapsedIndex() {
        val past = HomePeriod(DAYS, ym(2026, 9), ym(2026, 9))
        assertEquals(29, past.lastElapsedIndex(nov10))
        assertEquals(-1, HomePeriod(DAYS, ym(2027, 1), ym(2027, 1)).lastElapsedIndex(nov10))
    }
    @Test fun clampShiftsBackUntilNotAfterToday() {
        assertEquals(HomePeriod(DAYS, ym(2026, 9), ym(2026, 11)), HomePeriod(DAYS, ym(2026, 12), ym(2027, 2)).clampedTo(nov10))
    }
    @Test fun withModeConversions() {
        val months = HomePeriod(MONTHS, ym(2025, 1), ym(2025, 12))
        assertEquals(HomePeriod(DAYS, ym(2025, 12), ym(2025, 12)), months.withMode(DAYS, nov10))
        assertEquals(HomePeriod(MONTHS, ym(2026, 1), ym(2026, 12)), HomePeriod.current(DAYS, nov10).withMode(MONTHS, nov10))
    }
}
