package com.yehor.finora.ui.accounts

import com.yehor.finora.domain.account.AccountWithBalance
import com.yehor.finora.domain.account.Balance
import com.yehor.finora.domain.analytics.BalancePoint
import com.yehor.finora.domain.analytics.Bucket
import com.yehor.finora.domain.analytics.BucketPlan
import com.yehor.finora.domain.analytics.BucketUnit
import com.yehor.finora.domain.money.Currency
import com.yehor.finora.domain.rate.RateConverter
import com.yehor.finora.domain.rate.RateTable
import java.time.LocalDate
import java.time.ZoneId

/**
 * Each account's share of the total, from converted balances in minor units. Negative and null balances count
 * as 0; when nothing is positive the result is empty.
 */
fun accountShares(items: List<Pair<Long, Long?>>): List<Pair<Long, Float>> {
    val positive = items.map { (id, minor) -> id to (minor ?: 0L).coerceAtLeast(0L) }
    val total = positive.sumOf { it.second.toDouble() }
    if (total <= 0.0) return emptyList()
    return positive.map { (id, minor) -> id to (minor / total).toFloat() }
}

/** Every active account's balance converted to [display]; null when it cannot be converted (no rate, overflow). */
fun convertedBalances(
    active: List<AccountWithBalance>,
    display: Currency,
    table: RateTable,
    now: Long,
): List<Pair<Long, Long?>> {
    val converter = RateConverter(table, now)
    return active.map { item ->
        val money = (item.balance as? Balance.Value)?.money
        item.account.id to money?.let { converter.value(listOf(it), display).total?.amountMinor }
    }
}

/** Balance trend of one account: [sparkline] in minor units (oldest first) and the change since the month start. */
data class AccountTrend(val sparkline: List<Long>, val monthChange: Long?)

const val SPARKLINE_DAYS = 30

/** Daily buckets from 30 days ago (or the day before this month began, if earlier) to [today]. */
fun trendBuckets(today: LocalDate, zone: ZoneId): List<Bucket> {
    val monthBaseline = today.withDayOfMonth(1).minusDays(1)
    val from = minOf(today.minusDays(SPARKLINE_DAYS - 1L), monthBaseline)
    return BucketPlan.split(from, today, BucketUnit.DAY, zone)
}

/**
 * [points] are end-of-day balances. The month change is the last balance minus the balance at the end of the day
 * before [monthStart]; null when that baseline is not in the series.
 */
fun accountTrend(points: List<BalancePoint>, monthStart: LocalDate): AccountTrend {
    if (points.isEmpty()) return AccountTrend(emptyList(), null)
    val baseline = points.firstOrNull { it.bucket.fromDate == monthStart.minusDays(1) }
    val change = baseline?.let { points.last().balance.amountMinor - it.balance.amountMinor }
    return AccountTrend(points.takeLast(SPARKLINE_DAYS).map { it.balance.amountMinor }, change)
}
