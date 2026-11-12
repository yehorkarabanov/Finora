package com.yehor.finora.ui.analytics

import com.yehor.finora.domain.analytics.Cell
import com.yehor.finora.domain.money.CurrencyRegistry
import com.yehor.finora.domain.money.Estimate
import com.yehor.finora.domain.money.EstimateBasis
import com.yehor.finora.ui.analytics.HomeMode.DAYS
import com.yehor.finora.ui.analytics.HomeMode.MONTHS
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeChartLogicTest {
    private val pln = CurrencyRegistry.get("PLN")
    private val nov10 = LocalDate.of(2026, 11, 10)
    private fun ym(y: Int, m: Int) = YearMonth.of(y, m)

    private fun cells(vararg minor: Long) = minor.map { Cell(Estimate(it, pln, EstimateBasis.NATIVE), 1, emptySet(), false, null) }
    private fun unconvertible() = Cell(null, 1, setOf(CurrencyRegistry.get("JPY")), false, null)

    @Test fun daysUsesRunningNet() = assertEquals(listOf(-10L, 90L), homeChartValues(DAYS, cells(0, 100), cells(10, 0), lastIndex = 1))
    @Test fun monthsUsesPerBucket() = assertEquals(listOf(-10L, 100L), homeChartValues(MONTHS, cells(0, 100), cells(10, 0), lastIndex = 1))
    @Test fun emptyCellCountsAsZero() = assertEquals(listOf(0L), homeChartValues(DAYS, listOf(Cell.NONE), listOf(Cell.NONE), 0))
    @Test fun unconvertibleCellIsNull() = assertEquals(listOf(null), homeChartValues(MONTHS, listOf(unconvertible()), listOf(Cell.NONE), 0))
    @Test fun futureBucketsAreNull() = assertEquals(listOf(5L, null), homeChartValues(DAYS, cells(5, 7), cells(0, 0), lastIndex = 0))

    @Test fun currentPeriodIsPageZero() = assertEquals(0, HomePeriod.current(DAYS, nov10).stepsToLatest(nov10))
    @Test fun pastMonthIsItsDistanceBack() = assertEquals(2, HomePeriod(DAYS, ym(2026, 9), ym(2026, 9)).stepsToLatest(nov10))
    @Test fun pastYearIsOnePageBack() = assertEquals(1, HomePeriod(MONTHS, ym(2025, 1), ym(2025, 12)).stepsToLatest(nov10))
    @Test fun rangeStepsByItsLength() {
        val range = HomePeriod(DAYS, ym(2025, 9), ym(2025, 12))
        assertEquals(3, range.stepsToLatest(nov10))
        assertEquals(HomePeriod(DAYS, ym(2026, 9), ym(2026, 12)), range.shifted(range.stepsToLatest(nov10)))
    }

    @Test fun savedPeriodRoundTrips() {
        val p = HomePeriod(MONTHS, ym(2025, 11), ym(2026, 2))
        assertEquals(p, parseHomePeriod(p.toSaved()))
    }
    @Test fun badSavedPeriodIsNull() {
        assertNull(parseHomePeriod("DAYS|nope"))
        assertNull(parseHomePeriod("WEEKS|2026-01|2026-01"))
        assertNull(parseHomePeriod("DAYS|2026-03|2026-01"))
    }

    @Test fun unchangedConfirmKeepsCurrentYear() {
        val year = HomePeriod.current(MONTHS, nov10)
        assertEquals(ym(2026, 11), year.sheetTo(nov10))
        assertEquals(year, confirmedRange(year, ym(2026, 1), ym(2026, 11), nov10))
    }
    @Test fun unchangedConfirmKeepsRangeRunningPastToday() {
        val range = HomePeriod(DAYS, ym(2026, 9), ym(2026, 12))
        assertEquals(range, confirmedRange(range, ym(2026, 9), ym(2026, 11), nov10))
    }
    @Test fun monthsCalendarYearToTodayIsWholeYear() {
        val past = HomePeriod(MONTHS, ym(2025, 1), ym(2025, 12))
        assertEquals(HomePeriod.current(MONTHS, nov10), confirmedRange(past, ym(2026, 1), ym(2026, 11), nov10))
    }
    @Test fun daysClampedYearStaysARange() {
        val month = HomePeriod.current(DAYS, nov10)
        assertEquals(HomePeriod(DAYS, ym(2026, 1), ym(2026, 11)), confirmedRange(month, ym(2026, 1), ym(2026, 11), nov10))
    }
    @Test fun changedConfirmTakesTheNewRange() {
        val year = HomePeriod.current(MONTHS, nov10)
        assertEquals(HomePeriod(MONTHS, ym(2025, 11), ym(2026, 2)), confirmedRange(year, ym(2025, 11), ym(2026, 2), nov10))
    }
}
