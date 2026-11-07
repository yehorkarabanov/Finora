package com.yehor.finora.domain.analytics

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

enum class PeriodKind { TODAY, WEEK, MONTH, YEAR, CUSTOM }

/**
 * For presets [anchor] is any date inside the period. For [PeriodKind.CUSTOM], [anchor] is the first
 * day and [customEnd] the last day, both inclusive.
 */
data class Period(val kind: PeriodKind, val anchor: LocalDate, val customEnd: LocalDate? = null)

data class ResolvedPeriod(
    val period: Period,
    val fromDate: LocalDate,
    val toDateInclusive: LocalDate,
    /** Epoch ms, inclusive. */
    val from: Long,
    /** Epoch ms, exclusive. */
    val toExclusive: Long,
) {
    val dayCount: Long get() = ChronoUnit.DAYS.between(fromDate, toDateInclusive) + 1
}

object PeriodCalculator {

    fun preset(kind: PeriodKind, today: LocalDate): Period =
        if (kind == PeriodKind.CUSTOM) Period(kind, today, today) else Period(kind, today)

    fun resolve(period: Period, zone: ZoneId, firstDayOfWeek: DayOfWeek): ResolvedPeriod {
        val (fromDate, toDate) = dates(period, firstDayOfWeek)
        return ResolvedPeriod(
            period = period,
            fromDate = fromDate,
            toDateInclusive = toDate,
            from = startOfDay(fromDate, zone),
            toExclusive = startOfDay(toDate.plusDays(1), zone),
        )
    }

    /** -1 previous, +1 next. A custom range shifts by its own length in days. */
    fun shift(period: Period, steps: Int): Period = when (period.kind) {
        PeriodKind.TODAY -> period.copy(anchor = period.anchor.plusDays(steps.toLong()))
        PeriodKind.WEEK -> period.copy(anchor = period.anchor.plusWeeks(steps.toLong()))
        PeriodKind.MONTH -> period.copy(anchor = period.anchor.withDayOfMonth(1).plusMonths(steps.toLong()))
        PeriodKind.YEAR -> period.copy(anchor = period.anchor.withDayOfYear(1).plusYears(steps.toLong()))
        PeriodKind.CUSTOM -> {
            val (from, to) = customDates(period)
            val length = ChronoUnit.DAYS.between(from, to) + 1
            val delta = length * steps
            Period(PeriodKind.CUSTOM, from.plusDays(delta), to.plusDays(delta))
        }
    }

    fun previous(period: Period): Period = shift(period, -1)

    /** False when the next period would start after [today]. */
    fun canShiftForward(period: Period, today: LocalDate, zone: ZoneId, firstDayOfWeek: DayOfWeek): Boolean {
        val next = resolve(shift(period, 1), zone, firstDayOfWeek)
        return !next.fromDate.isAfter(today)
    }

    private fun dates(period: Period, firstDayOfWeek: DayOfWeek): Pair<LocalDate, LocalDate> {
        val anchor = period.anchor
        return when (period.kind) {
            PeriodKind.TODAY -> anchor to anchor
            PeriodKind.WEEK -> {
                val start = anchor.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
                start to start.plusDays(6)
            }
            PeriodKind.MONTH -> anchor.withDayOfMonth(1) to anchor.with(TemporalAdjusters.lastDayOfMonth())
            PeriodKind.YEAR -> anchor.withDayOfYear(1) to anchor.with(TemporalAdjusters.lastDayOfYear())
            PeriodKind.CUSTOM -> customDates(period)
        }
    }

    /** An invalid pair is coerced to a one-day period at the anchor instead of throwing. */
    private fun customDates(period: Period): Pair<LocalDate, LocalDate> {
        val end = period.customEnd ?: period.anchor
        return if (end.isBefore(period.anchor)) period.anchor to period.anchor else period.anchor to end
    }

    internal fun startOfDay(date: LocalDate, zone: ZoneId): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()
}
