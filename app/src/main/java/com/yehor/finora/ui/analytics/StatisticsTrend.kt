package com.yehor.finora.ui.analytics

import com.yehor.finora.domain.analytics.Bucket
import com.yehor.finora.domain.analytics.BucketPlan
import com.yehor.finora.domain.analytics.BucketUnit
import com.yehor.finora.domain.analytics.Measure
import com.yehor.finora.domain.analytics.PeriodState
import com.yehor.finora.domain.analytics.ResolvedPeriod
import com.yehor.finora.domain.analytics.TrendPoint
import com.yehor.finora.domain.analytics.orZero
import com.yehor.finora.domain.analytics.sumCells
import com.yehor.finora.domain.money.Currency
import com.yehor.finora.domain.money.Estimate
import com.yehor.finora.domain.operation.TransactionFilter
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

/** The Statistics trend of one window: a wave of [values] (minor units), the previous period dashed behind it. */
data class StatsTrend(
    val window: ChartWindow,
    val measure: Measure,
    val unit: BucketUnit,
    val buckets: List<Bucket>,
    /** Null = not drawn (a future bucket, or one that could not be converted). */
    val values: List<Long?>,
    /** Same length as [values]; null while Compare is off. */
    val compare: List<Long?>?,
    /** Mean over the buckets that have started. */
    val average: Estimate?,
    /** The currency the values are in (the scope currency), for the point bubbles. */
    val currency: Currency,
    /** The filter behind the wave, without dates; a bucket's transactions add its dates. */
    val base: TransactionFilter,
)

/**
 * The window the trend shows for a selected [period]: the period itself, but a period still running
 * ends at [today] (the chart never shows future days, and a gesture starts from what is visible).
 */
fun displayWindow(period: ResolvedPeriod, today: LocalDate): ChartWindow {
    val end = minOf(period.toDateInclusive, today)
    return ChartWindow(period.fromDate, if (end.isBefore(period.fromDate)) period.fromDate else end)
}

/** A window the trend chart is being panned or pinched to; [base] is the period state it started from. */
data class TrendPreview(val window: ChartWindow, val base: PeriodState?)

/**
 * What the trend shows: the [preview] while the state it started from (or the one it was committed as)
 * is still [selected], else the [committed] window of the selected period.
 */
fun shownWindow(committed: ChartWindow, selected: PeriodState, preview: TrendPreview?): ChartWindow =
    preview?.takeIf { it.base == selected || it.window == committed }?.window ?: committed

/** The period state a gesture that ended on [window] selects; null when [window] is what [period] already shows. */
fun commitTarget(window: ChartWindow, period: ResolvedPeriod, today: LocalDate, firstDayOfWeek: DayOfWeek): PeriodState? =
    if (window == displayWindow(period, today)) null else window.toPeriodState(firstDayOfWeek, today)

/** Day buckets while the window is short enough for days ([ChartWindow.unit]), month buckets otherwise. */
fun trendBuckets(period: ResolvedPeriod, zone: ZoneId, previousUnit: BucketUnit?): Pair<BucketUnit, List<Bucket>> {
    val unit = ChartWindow.of(period).unit(previousUnit)
    return unit to BucketPlan.split(period.fromDate, period.toDateInclusive, unit, zone)
}

/** For each bucket of [current], the bucket of [previous] with the same ordinal, or null when [previous] is shorter. */
fun alignPrevious(current: List<Bucket>, previous: List<Bucket>): List<Int?> =
    current.indices.map { i -> i.takeIf { it < previous.size } }

/** A quiet bucket is 0, a bucket that has not started yet (after [today]) or could not be converted is null. */
fun trendValues(points: List<TrendPoint>, today: LocalDate): List<Long?> = points.map { point ->
    when {
        point.bucket.fromDate.isAfter(today) -> null
        point.cell.isEmpty -> 0L
        point.cell.overflowed -> null
        else -> point.cell.estimate?.amountMinor
    }
}

/** The mean of the buckets that have started by [today]; null when none has or the sum cannot be shown. */
fun trendAverage(points: List<TrendPoint>, today: LocalDate, currency: Currency): Estimate? {
    val elapsed = points.filter { !it.bucket.fromDate.isAfter(today) }
    if (elapsed.isEmpty()) return null
    val total = elapsed.map { it.cell }.sumCells().orZero(currency)
    if (total.overflowed) return null
    val sum = total.estimate ?: return null
    return sum.copy(amountMinor = sum.amountMinor / elapsed.size)
}

/**
 * Turns the many small pan/pinch steps of one gesture into whole-day window changes. Fractions of a day
 * and zoom factors too small to change the day count are carried to the next step instead of being lost.
 */
class WindowGesture {
    private var panDays = 0.0

    /** The part of a day the fingers have moved that has not become a whole-day window change yet. */
    val pendingDays: Double get() = panDays
    private var scale = 1.0

    /** [deltaFraction] of the window length; positive moves forward in time, never past [today]. */
    fun pan(window: ChartWindow, deltaFraction: Float, today: LocalDate): ChartWindow {
        if (deltaFraction > 0f && !window.end.isBefore(today)) {
            panDays = 0.0
            return window
        }
        panDays += deltaFraction.toDouble() * window.days
        // The epsilon keeps float noise (0.3 × 10 = 2.9999…) from losing a day.
        val whole = (panDays + Math.copySign(1e-4, panDays)).toLong()
        if (whole == 0L) return window
        panDays -= whole
        return window.panned(whole.toFloat() / window.days, today)
    }

    /** [factor] > 1 shows fewer days around [focusFraction]. */
    fun zoom(window: ChartWindow, factor: Float, focusFraction: Float, today: LocalDate): ChartWindow {
        scale *= factor
        val atMin = window.days <= ChartWindow.MIN_DAYS && scale > 1.0
        val atMax = window.days >= ChartWindow.MAX_DAYS && scale < 1.0
        if (atMin || atMax) {
            scale = 1.0
            return window
        }
        val next = window.zoomed(scale.toFloat(), focusFraction.coerceIn(0f, 1f), today)
        if (next.days == window.days) return window
        scale = 1.0
        return next
    }

    fun reset() {
        panDays = 0.0
        scale = 1.0
    }
}
