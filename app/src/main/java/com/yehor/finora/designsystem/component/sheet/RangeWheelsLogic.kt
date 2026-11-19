package com.yehor.finora.designsystem.component.sheet

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

sealed interface RangeValidity {
    data class Valid(val months: Int) : RangeValidity
    data object EndBeforeStart : RangeValidity
    data object InFuture : RangeValidity
}

object RangeWheelsLogic {
    private const val DEFAULT_YEARS_BACK = 4

    fun validate(from: YearMonth, to: YearMonth, today: LocalDate): RangeValidity = when {
        to.isBefore(from) -> RangeValidity.EndBeforeStart
        to.isAfter(YearMonth.from(today)) -> RangeValidity.InFuture
        else -> RangeValidity.Valid(ChronoUnit.MONTHS.between(from, to).toInt() + 1)
    }

    /** Earliest data year (or today-4) through the current year. */
    fun years(today: LocalDate, earliest: LocalDate?): List<Int> {
        val start = (earliest?.year ?: (today.year - DEFAULT_YEARS_BACK)).coerceAtMost(today.year)
        return (start..today.year).toList()
    }
}
