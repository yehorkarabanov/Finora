package com.yehor.finora.domain.analytics

import com.yehor.finora.domain.money.Currency
import com.yehor.finora.domain.money.Money
import com.yehor.finora.domain.money.MoneyOverflowException
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

enum class BucketUnit { DAY, MONTH, YEAR }

data class Bucket(
    val index: Int,
    val fromDate: LocalDate,
    val toDateInclusive: LocalDate,
    /** Epoch ms, inclusive. */
    val from: Long,
    /** Epoch ms, exclusive. */
    val toExclusive: Long,
)

object BucketPlan {

    const val MAX_BUCKETS = 100
    private const val MAX_DAY_BUCKET_DAYS = 62L
    private const val MAX_MONTH_BUCKET_MONTHS = 36L

    /** DAY if the period is at most 62 days, MONTH if at most 36 months, else YEAR. First and last bucket are clipped. */
    fun auto(period: ResolvedPeriod, zone: ZoneId): Pair<BucketUnit, List<Bucket>> {
        val from = period.fromDate
        val to = period.toDateInclusive
        val days = ChronoUnit.DAYS.between(from, to) + 1
        val months = ChronoUnit.MONTHS.between(from.withDayOfMonth(1), to.withDayOfMonth(1)) + 1
        val unit = when {
            days <= MAX_DAY_BUCKET_DAYS -> BucketUnit.DAY
            months <= MAX_MONTH_BUCKET_MONTHS -> BucketUnit.MONTH
            else -> BucketUnit.YEAR
        }
        return unit to split(from, to, unit, zone)
    }

    /** Buckets of [unit] from [from] to [to] (inclusive); the first and last are clipped to the range. */
    fun split(from: LocalDate, to: LocalDate, unit: BucketUnit, zone: ZoneId): List<Bucket> {
        val ranges = ArrayList<Pair<LocalDate, LocalDate>>()
        var cursor = from
        while (!cursor.isAfter(to) && ranges.size < MAX_BUCKETS) {
            val naturalEnd = when (unit) {
                BucketUnit.DAY -> cursor
                BucketUnit.MONTH -> cursor.withDayOfMonth(cursor.lengthOfMonth())
                BucketUnit.YEAR -> cursor.withDayOfYear(cursor.lengthOfYear())
            }
            val end = if (naturalEnd.isAfter(to)) to else naturalEnd
            ranges += cursor to end
            cursor = end.plusDays(1)
        }
        return ranges.mapIndexed { i, (a, b) -> bucket(i, a, b, zone) }
    }

    /** [count] calendar months ending with the month that contains [endDate]. */
    fun months(endDate: LocalDate, count: Int = 12, zone: ZoneId): List<Bucket> {
        val n = count.coerceIn(1, MAX_BUCKETS)
        val last = endDate.withDayOfMonth(1)
        return (0 until n).map { i ->
            val first = last.minusMonths((n - 1 - i).toLong())
            bucket(i, first, first.withDayOfMonth(first.lengthOfMonth()), zone)
        }
    }

    /** [count] calendar years ending with the year that contains [endDate]. */
    fun years(endDate: LocalDate, count: Int = 5, zone: ZoneId): List<Bucket> {
        val n = count.coerceIn(1, MAX_BUCKETS)
        val last = endDate.withDayOfYear(1)
        return (0 until n).map { i ->
            val first = last.minusYears((n - 1 - i).toLong())
            bucket(i, first, first.withDayOfYear(first.lengthOfYear()), zone)
        }
    }

    private fun bucket(index: Int, from: LocalDate, toInclusive: LocalDate, zone: ZoneId) = Bucket(
        index = index,
        fromDate = from,
        toDateInclusive = toInclusive,
        from = PeriodCalculator.startOfDay(from, zone),
        toExclusive = PeriodCalculator.startOfDay(toInclusive.plusDays(1), zone),
    )
}

data class BalancePoint(val bucket: Bucket, val balance: Money)

object BalanceSeries {

    /**
     * [startMinor] is the balance before the first bucket. Each point is the balance at the end of
     * its bucket. Throws [MoneyOverflowException] when a running balance leaves the Long range.
     */
    fun build(
        startMinor: Long,
        currency: Currency,
        buckets: List<Bucket>,
        deltaByBucket: Map<Int, Long>,
    ): List<BalancePoint> {
        var running = startMinor
        return buckets.map { bucket ->
            running = try {
                Math.addExact(running, deltaByBucket[bucket.index] ?: 0L)
            } catch (e: ArithmeticException) {
                throw MoneyOverflowException("Balance series overflow", e)
            }
            BalancePoint(bucket, Money(running, currency))
        }
    }
}

data class AccountFigures(
    val currency: Currency,
    val balanceAtStart: Money,
    val balanceAtEnd: Money,
    val income: Money,
    val expense: Money,
    val transfersIn: Money,
    val transfersOut: Money,
    /** Zero unless the opening balance is dated inside the period. */
    val initialBalanceInPeriod: Money,
) {
    /** start + income - expense + in - out + initial == end. Never throws: overflow is "not reconciled". */
    fun reconciles(): Boolean = try {
        balanceAtStart + income - expense + transfersIn - transfersOut + initialBalanceInPeriod == balanceAtEnd
    } catch (e: MoneyOverflowException) {
        false
    }

    val netChange: Money? get() = try { balanceAtEnd - balanceAtStart } catch (e: MoneyOverflowException) { null }
}
