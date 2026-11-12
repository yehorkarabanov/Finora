package com.yehor.finora.ui.analytics

import com.yehor.finora.domain.analytics.Bucket
import com.yehor.finora.domain.analytics.BucketUnit
import com.yehor.finora.domain.analytics.Cell
import com.yehor.finora.domain.analytics.NetSeries
import java.time.LocalDate
import java.time.YearMonth

/** The net wave of one Home period. [values] are minor units of the chart currency; null = not drawn. */
data class HomeChart(
    val unit: BucketUnit,
    val buckets: List<Bucket>,
    val values: List<Long?>,
    val net: Cell,
    val income: Cell,
    val expense: Cell,
    /** True when some amount was converted to the display currency, so figures read "≈". */
    val currencyApprox: Boolean,
    /** Rate age, excluded currencies and overflow, for the note under the chart. */
    val conversion: ConversionInfo = ConversionInfo.NONE,
)

/**
 * DAYS: running net from 0 across the range; MONTHS: each bucket's own net. Buckets after [lastIndex]
 * are null (future). A quiet bucket counts as 0; a bucket that could not be converted is null.
 */
fun homeChartValues(mode: HomeMode, income: List<Cell>, expense: List<Cell>, lastIndex: Int): List<Long?> {
    val inc = income.map { it.chartMinor() }
    val exp = expense.map { it.chartMinor() }
    return when (mode) {
        HomeMode.DAYS -> NetSeries.running(inc, exp, lastIndex)
        HomeMode.MONTHS -> NetSeries.perBucket(inc, exp, lastIndex)
    }
}

private fun Cell.chartMinor(): Long? = when {
    isEmpty -> 0L
    else -> estimate?.amountMinor
}

/**
 * How many steps of this period's length lead forward to the latest period of the same chain that
 * does not start after today's month. That latest period is Home's page 0; this period is page [stepsToLatest].
 */
fun HomePeriod.stepsToLatest(today: LocalDate): Int {
    val current = YearMonth.from(today)
    val gap = (current.year - from.year) * 12 + (current.monthValue - from.monthValue)
    return if (gap <= 0) 0 else gap / monthCount
}

/** The TO month the range sheet opens on: the period's end, but never a future month (the wheels reject those). */
fun HomePeriod.sheetTo(today: LocalDate): YearMonth = minOf(to, YearMonth.from(today))

/**
 * The period a range-sheet confirm of [from]..[to] produces. Confirming what the sheet opened with keeps
 * [period] unchanged (its end may have been clamped to today). In MONTHS a calendar year ending at today's
 * month means that whole year (Jan–Dec), not an 11-month range.
 */
fun confirmedRange(period: HomePeriod, from: YearMonth, to: YearMonth, today: LocalDate): HomePeriod {
    val current = YearMonth.from(today)
    return when {
        from == period.from && to == period.sheetTo(today) -> period
        period.mode == HomeMode.MONTHS && from.monthValue == 1 && from.year == to.year && to == current ->
            HomePeriod(HomeMode.MONTHS, from, YearMonth.of(from.year, 12))
        else -> HomePeriod(period.mode, from, to)
    }
}

/** "MODE|yyyy-MM|yyyy-MM", the SavedStateHandle form of a Home period. */
fun HomePeriod.toSaved(): String = "${mode.name}|$from|$to"

fun parseHomePeriod(saved: String?): HomePeriod? {
    val parts = saved?.split('|')?.takeIf { it.size == 3 } ?: return null
    val mode = HomeMode.entries.firstOrNull { it.name == parts[0] } ?: return null
    return try {
        val from = YearMonth.parse(parts[1])
        val to = YearMonth.parse(parts[2])
        if (to.isBefore(from)) null else HomePeriod(mode, from, to)
    } catch (e: java.time.format.DateTimeParseException) {
        null
    }
}
