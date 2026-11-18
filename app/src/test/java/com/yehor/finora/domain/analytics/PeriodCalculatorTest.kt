package com.yehor.finora.domain.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

class PeriodCalculatorTest {

    private val utc = ZoneId.of("UTC")
    private val monday = DayOfWeek.MONDAY

    private fun resolve(kind: PeriodKind, date: LocalDate, first: DayOfWeek = monday) =
        PeriodCalculator.resolve(PeriodCalculator.preset(kind, date), utc, first)

    @Test
    fun todayIsOneDay() {
        val r = resolve(PeriodKind.TODAY, LocalDate.of(2026, 11, 7))
        assertEquals(LocalDate.of(2026, 11, 7), r.fromDate)
        assertEquals(LocalDate.of(2026, 11, 7), r.toDateInclusive)
        assertEquals(24L * 3600_000, r.toExclusive - r.from)
    }

    @Test
    fun weekStartsOnTheGivenDay() {
        val saturday = LocalDate.of(2026, 11, 7)
        assertEquals(LocalDate.of(2026, 11, 2), resolve(PeriodKind.WEEK, saturday, DayOfWeek.MONDAY).fromDate)
        assertEquals(LocalDate.of(2026, 11, 1), resolve(PeriodKind.WEEK, saturday, DayOfWeek.SUNDAY).fromDate)
        assertEquals(LocalDate.of(2026, 11, 8), resolve(PeriodKind.WEEK, saturday, DayOfWeek.MONDAY).toDateInclusive)
    }

    @Test
    fun monthLengths() {
        assertEquals(LocalDate.of(2027, 2, 28), resolve(PeriodKind.MONTH, LocalDate.of(2027, 2, 10)).toDateInclusive)
        assertEquals(LocalDate.of(2028, 2, 29), resolve(PeriodKind.MONTH, LocalDate.of(2028, 2, 10)).toDateInclusive)
        assertEquals(LocalDate.of(2026, 4, 30), resolve(PeriodKind.MONTH, LocalDate.of(2026, 4, 1)).toDateInclusive)
        assertEquals(LocalDate.of(2026, 12, 31), resolve(PeriodKind.MONTH, LocalDate.of(2026, 12, 15)).toDateInclusive)
    }

    @Test
    fun yearCoversJanuaryToDecember() {
        val r = resolve(PeriodKind.YEAR, LocalDate.of(2026, 6, 15))
        assertEquals(LocalDate.of(2026, 1, 1), r.fromDate)
        assertEquals(LocalDate.of(2026, 12, 31), r.toDateInclusive)
    }

    @Test
    fun resolveIsHalfOpenAtMidnight() {
        val r = resolve(PeriodKind.MONTH, LocalDate.of(2026, 11, 7))
        assertEquals(LocalDate.of(2026, 11, 1).atStartOfDay(utc).toInstant().toEpochMilli(), r.from)
        assertEquals(LocalDate.of(2026, 12, 1).atStartOfDay(utc).toInstant().toEpochMilli(), r.toExclusive)
    }

    @Test
    fun customOneDayAndManyDays() {
        val one = PeriodCalculator.resolve(Period(PeriodKind.CUSTOM, LocalDate.of(2026, 3, 5), LocalDate.of(2026, 3, 5)), utc, monday)
        assertEquals(1, one.dayCount)
        val many = PeriodCalculator.resolve(Period(PeriodKind.CUSTOM, LocalDate.of(2026, 3, 5), LocalDate.of(2026, 3, 14)), utc, monday)
        assertEquals(10, many.dayCount)
    }

    @Test
    fun invalidCustomPairIsCoercedToOneDay() {
        val r = PeriodCalculator.resolve(Period(PeriodKind.CUSTOM, LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 1)), utc, monday)
        assertEquals(LocalDate.of(2026, 3, 10), r.fromDate)
        assertEquals(LocalDate.of(2026, 3, 10), r.toDateInclusive)
    }

    @Test
    fun shiftMonthBackFromMarchEndIsFebruary() {
        val march = PeriodCalculator.preset(PeriodKind.MONTH, LocalDate.of(2027, 3, 31))
        val back = PeriodCalculator.previous(march)
        val r = PeriodCalculator.resolve(back, utc, monday)
        assertEquals(LocalDate.of(2027, 2, 1), r.fromDate)
        assertEquals(LocalDate.of(2027, 2, 28), r.toDateInclusive)
    }

    @Test
    fun shiftAcrossYearEnd() {
        val january = PeriodCalculator.preset(PeriodKind.MONTH, LocalDate.of(2027, 1, 15))
        assertEquals(LocalDate.of(2026, 12, 1), PeriodCalculator.resolve(PeriodCalculator.previous(january), utc, monday).fromDate)
        val year = PeriodCalculator.preset(PeriodKind.YEAR, LocalDate.of(2027, 5, 5))
        assertEquals(LocalDate.of(2026, 1, 1), PeriodCalculator.resolve(PeriodCalculator.previous(year), utc, monday).fromDate)
    }

    @Test
    fun shiftWeekAndDay() {
        val week = PeriodCalculator.preset(PeriodKind.WEEK, LocalDate.of(2026, 11, 7))
        assertEquals(LocalDate.of(2026, 10, 26), PeriodCalculator.resolve(PeriodCalculator.previous(week), utc, monday).fromDate)
        val day = PeriodCalculator.preset(PeriodKind.TODAY, LocalDate.of(2026, 11, 1))
        assertEquals(LocalDate.of(2026, 10, 31), PeriodCalculator.previous(day).anchor)
    }

    @Test
    fun customShiftsByItsOwnLength() {
        val custom = Period(PeriodKind.CUSTOM, LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 14)) // 5 days
        val back = PeriodCalculator.shift(custom, -1)
        assertEquals(LocalDate.of(2026, 3, 5), back.anchor)
        assertEquals(LocalDate.of(2026, 3, 9), back.customEnd)
        val forward = PeriodCalculator.shift(custom, 1)
        assertEquals(LocalDate.of(2026, 3, 15), forward.anchor)
        assertEquals(LocalDate.of(2026, 3, 19), forward.customEnd)
    }

    @Test
    fun canShiftForwardExactlyAtTheBoundary() {
        val month = PeriodCalculator.preset(PeriodKind.MONTH, LocalDate.of(2026, 10, 15))
        // Next month starts 1 Nov: allowed on 1 Nov, not on 31 Oct.
        assertTrue(PeriodCalculator.canShiftForward(month, LocalDate.of(2026, 11, 1), utc, monday))
        assertFalse(PeriodCalculator.canShiftForward(month, LocalDate.of(2026, 10, 31), utc, monday))
        val current = PeriodCalculator.preset(PeriodKind.MONTH, LocalDate.of(2026, 11, 7))
        assertFalse(PeriodCalculator.canShiftForward(current, LocalDate.of(2026, 11, 7), utc, monday))
    }

    @Test
    fun missingMidnightZoneStillResolves() {
        // America/Sao_Paulo skipped midnight on 2018-11-04 (clocks went 00:00 -> 01:00).
        val zone = ZoneId.of("America/Sao_Paulo")
        val r = PeriodCalculator.resolve(Period(PeriodKind.TODAY, LocalDate.of(2018, 11, 4)), zone, monday)
        assertEquals(23L * 3600_000, r.toExclusive - r.from)
    }

    @Test
    fun dstOverlapDayIsTwentyFiveHours() {
        val zone = ZoneId.of("Europe/Warsaw")
        val r = PeriodCalculator.resolve(Period(PeriodKind.TODAY, LocalDate.of(2026, 10, 25)), zone, monday)
        assertEquals(25L * 3600_000, r.toExclusive - r.from)
    }
}
