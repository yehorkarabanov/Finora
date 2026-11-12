package com.yehor.finora.designsystem.component.sheet

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test

class RangeWheelsLogicTest {
    private val today = LocalDate.of(2026, 11, 10)

    @Test fun crossYearRangeCountsMonths() =
        assertEquals(RangeValidity.Valid(4), RangeWheelsLogic.validate(YearMonth.of(2025, 11), YearMonth.of(2026, 2), today))

    @Test fun singleMonthIsOne() =
        assertEquals(RangeValidity.Valid(1), RangeWheelsLogic.validate(YearMonth.of(2026, 11), YearMonth.of(2026, 11), today))

    @Test fun endBeforeStart() =
        assertEquals(RangeValidity.EndBeforeStart, RangeWheelsLogic.validate(YearMonth.of(2026, 3), YearMonth.of(2026, 1), today))

    @Test fun futureEnd() =
        assertEquals(RangeValidity.InFuture, RangeWheelsLogic.validate(YearMonth.of(2026, 10), YearMonth.of(2026, 12), today))

    @Test fun yearsWithoutDataDefaultToFiveBack() = assertEquals((2022..2026).toList(), RangeWheelsLogic.years(today, null))

    @Test fun yearsStartAtEarliestData() = assertEquals((2024..2026).toList(), RangeWheelsLogic.years(today, LocalDate.of(2024, 5, 1)))
}
