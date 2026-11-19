package com.yehor.finora.ui.analytics

import com.yehor.finora.domain.analytics.Bucket
import com.yehor.finora.domain.analytics.BucketUnit
import com.yehor.finora.domain.analytics.PeriodCalculator
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

enum class HomeMode { DAYS, MONTHS }

/** Inclusive month range. DAYS default = current month; MONTHS default = Jan..Dec of current year. */
data class HomePeriod(val mode: HomeMode, val from: YearMonth, val to: YearMonth) {

    val monthCount: Int get() = (to.year - from.year) * 12 + (to.monthValue - from.monthValue) + 1

    /** Moves both ends by [monthCount] * [steps] months. */
    fun shifted(steps: Int): HomePeriod {
        val delta = monthCount.toLong() * steps
        return copy(from = from.plusMonths(delta), to = to.plusMonths(delta))
    }

    fun canShiftForward(today: LocalDate): Boolean = to.isBefore(YearMonth.from(today))

    /** Never starts after today's month. */
    fun clampedTo(today: LocalDate): HomePeriod {
        val current = YearMonth.from(today)
        if (!from.isAfter(current)) return this
        val overshoot = (from.year - current.year) * 12 + (from.monthValue - current.monthValue)
        val steps = (overshoot + monthCount - 1) / monthCount
        return shifted(-steps)
    }

    fun withMode(mode: HomeMode, today: LocalDate): HomePeriod = when (mode) {
        HomeMode.DAYS -> HomePeriod(mode, to, to).clampedTo(today)
        HomeMode.MONTHS -> HomePeriod(mode, YearMonth.of(to.year, 1), YearMonth.of(to.year, 12)).clampedTo(today)
    }

    /**
     * MONTHS, or DAYS over more than [MAX_DAY_MONTHS] months, give one MONTH bucket per month;
     * otherwise one DAY bucket per day. Buckets are not clipped to today.
     */
    fun buckets(zone: ZoneId, today: LocalDate): Pair<BucketUnit, List<Bucket>> {
        val first = from.atDay(1)
        val last = to.atEndOfMonth()
        val ranges = ArrayList<Pair<LocalDate, LocalDate>>()
        val unit: BucketUnit
        if (mode == HomeMode.DAYS && monthCount <= MAX_DAY_MONTHS) {
            unit = BucketUnit.DAY
            var d = first
            while (!d.isAfter(last)) {
                ranges += d to d
                d = d.plusDays(1)
            }
        } else {
            unit = BucketUnit.MONTH
            var m = from
            while (!m.isAfter(to)) {
                ranges += m.atDay(1) to m.atEndOfMonth()
                m = m.plusMonths(1)
            }
        }
        return unit to ranges.mapIndexed { i, (a, b) ->
            Bucket(
                index = i,
                fromDate = a,
                toDateInclusive = b,
                from = PeriodCalculator.startOfDay(a, zone),
                toExclusive = PeriodCalculator.startOfDay(b.plusDays(1), zone),
            )
        }
    }

    /** Index of the bucket containing [today]; lastIndex if the range is past; -1 if it is in the future. */
    fun lastElapsedIndex(today: LocalDate): Int {
        val unitIsDay = mode == HomeMode.DAYS && monthCount <= MAX_DAY_MONTHS
        val first = from.atDay(1)
        val last = to.atEndOfMonth()
        val count = if (unitIsDay) {
            (java.time.temporal.ChronoUnit.DAYS.between(first, last) + 1).toInt()
        } else {
            monthCount
        }
        return when {
            today.isBefore(first) -> -1
            today.isAfter(last) -> count - 1
            unitIsDay -> (today.toEpochDay() - first.toEpochDay()).toInt()
            else -> (today.year - from.year) * 12 + (today.monthValue - from.monthValue)
        }
    }

    companion object {
        const val MAX_DAY_MONTHS = 3

        fun current(mode: HomeMode, today: LocalDate): HomePeriod = when (mode) {
            HomeMode.DAYS -> YearMonth.from(today).let { HomePeriod(mode, it, it) }
            HomeMode.MONTHS -> HomePeriod(mode, YearMonth.of(today.year, 1), YearMonth.of(today.year, 12))
        }
    }
}
