package com.yehor.finora.ui.analytics

import com.yehor.finora.domain.analytics.BucketUnit
import com.yehor.finora.domain.analytics.PeriodKind
import com.yehor.finora.domain.analytics.PeriodState
import com.yehor.finora.domain.analytics.ResolvedPeriod
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToLong

/** Visible date range of the Statistics trend chart; [end] is inclusive and never after "today" after pan/zoom. */
data class ChartWindow(val start: LocalDate, val end: LocalDate) {

    val days: Long get() = ChronoUnit.DAYS.between(start, end) + 1

    /** [deltaFraction] of the window length; positive moves forward in time. */
    fun panned(deltaFraction: Float, today: LocalDate): ChartWindow {
        val shift = (days * deltaFraction.toDouble()).roundToLong()
        return ChartWindow(start.plusDays(shift), end.plusDays(shift)).cappedAt(today)
    }

    /** [scale] > 1 shows fewer days; the date under [focusFraction] (0..1 across the window) stays put. */
    fun zoomed(scale: Float, focusFraction: Float, today: LocalDate): ChartWindow {
        val newDays = (days / scale.toDouble()).roundToLong().coerceIn(MIN_DAYS, MAX_DAYS)
        val focus = start.plusDays(((days - 1) * focusFraction.toDouble()).roundToLong())
        val newStart = focus.minusDays(((newDays - 1) * focusFraction.toDouble()).roundToLong())
        return ChartWindow(newStart, newStart.plusDays(newDays - 1)).cappedAt(today)
    }

    /** Day buckets up to 92 days, months from 100; in between the [previous] unit sticks. */
    fun unit(previous: BucketUnit?): BucketUnit = when {
        days <= 92 -> BucketUnit.DAY
        days >= 100 -> BucketUnit.MONTH
        else -> previous ?: BucketUnit.DAY
    }

    /** An exact calendar week, month or year becomes that preset (with offset); anything else is custom. */
    fun toPeriodState(firstDayOfWeek: DayOfWeek, today: LocalDate): PeriodState {
        val weekStart = today.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
        if (days == 7L && start.dayOfWeek == firstDayOfWeek) {
            return PeriodState(PeriodKind.WEEK, offset = (ChronoUnit.DAYS.between(weekStart, start) / 7).toInt())
        }
        if (start.dayOfMonth == 1 && end == start.with(TemporalAdjusters.lastDayOfMonth())) {
            return PeriodState(PeriodKind.MONTH, offset = ChronoUnit.MONTHS.between(today.withDayOfMonth(1), start).toInt())
        }
        if (start.dayOfYear == 1 && end == start.with(TemporalAdjusters.lastDayOfYear())) {
            return PeriodState(PeriodKind.YEAR, offset = start.year - today.year)
        }
        return PeriodState.custom(start, end)
    }

    private fun cappedAt(today: LocalDate): ChartWindow =
        if (end.isAfter(today)) {
            val back = ChronoUnit.DAYS.between(today, end)
            ChartWindow(start.minusDays(back), today)
        } else this

    companion object {
        const val MIN_DAYS = 7L
        const val MAX_DAYS = 1827L

        fun of(period: ResolvedPeriod) = ChartWindow(period.fromDate, period.toDateInclusive)
    }
}
