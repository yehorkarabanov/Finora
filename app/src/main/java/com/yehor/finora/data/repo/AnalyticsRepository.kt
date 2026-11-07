package com.yehor.finora.data.repo

import com.yehor.finora.data.db.AnalyticsQuery
import com.yehor.finora.data.db.FinoraDatabase
import com.yehor.finora.domain.analytics.AccountFigures
import com.yehor.finora.domain.analytics.AccountPeriodRow
import com.yehor.finora.domain.analytics.BalancePoint
import com.yehor.finora.domain.analytics.BalanceSeries
import com.yehor.finora.domain.analytics.Bucket
import com.yehor.finora.domain.analytics.Measure
import com.yehor.finora.domain.analytics.NativeRow
import com.yehor.finora.domain.analytics.ResolvedPeriod
import com.yehor.finora.domain.category.CategoryTree
import com.yehor.finora.domain.money.Currency
import com.yehor.finora.domain.money.CurrencyRegistry
import com.yehor.finora.domain.money.Money
import com.yehor.finora.domain.money.MoneyOverflowException
import com.yehor.finora.domain.operation.FilterScope
import com.yehor.finora.domain.operation.TransactionFilter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Read-only aggregates, returned as **native** rows. Conversion needs the rate table and the Display
 * Currency, so it happens in the ViewModels through `ScopeConverter`.
 *
 * A SQLite "integer overflow" is thrown as [MoneyOverflowException].
 */
@Singleton
class AnalyticsRepository @Inject constructor(
    private val db: FinoraDatabase,
    private val categoryRepository: CategoryRepository,
) {

    private fun invalidations(): Flow<Set<String>> =
        db.invalidationTracker.createFlow("operation", "ledger_entry", "operation_tag", "category", "account")

    fun observeByCategory(filter: TransactionFilter, measure: Measure): Flow<List<NativeRow>> =
        observeGrouped(filter, measure) { f, m, closure -> AnalyticsQuery.byCategory(f, m, closure) }

    fun observeByAccount(filter: TransactionFilter, measure: Measure): Flow<List<NativeRow>> =
        observeGrouped(filter, measure) { f, m, closure -> AnalyticsQuery.byAccount(f, m, closure) }

    fun observeByTag(filter: TransactionFilter, measure: Measure): Flow<List<NativeRow>> =
        observeGrouped(filter, measure) { f, m, closure -> AnalyticsQuery.byTag(f, m, closure) }

    /** [NativeRow.key] is the bucket index. */
    fun observeByBucket(filter: TransactionFilter, measure: Measure, buckets: List<Bucket>): Flow<List<NativeRow>> =
        observeGrouped(filter, measure) { f, m, closure -> AnalyticsQuery.byBucket(f, m, closure, buckets) }

    /** Null while the account does not exist. */
    fun observeAccountFigures(accountId: Long, period: ResolvedPeriod): Flow<AccountFigures?> =
        invalidations().map { figures(accountId, period) }.distinctUntilChanged()

    /** Empty while the account does not exist. Each point is the balance at the end of its bucket. */
    fun observeBalanceSeries(accountId: Long, buckets: List<Bucket>): Flow<List<BalancePoint>> =
        invalidations().map { balanceSeries(accountId, buckets) }.distinctUntilChanged()

    /** One row per account, archived included. */
    fun observeAccountsOverview(period: ResolvedPeriod): Flow<List<AccountPeriodRow>> =
        combine(invalidations(), db.accountDao().observeAll()) { _, accounts ->
            val overview = guarded { db.operationDao().overview(AnalyticsQuery.accountsOverview(period.from, period.toExclusive)) }
                .groupBy { it.accountId }
            accounts.map { account ->
                val currency = currency(account.currencyCode)
                val rows = overview[account.id].orEmpty()
                var income = Money.zero(currency)
                var expense = Money.zero(currency)
                var net = Money.zero(currency)
                for (row in rows) {
                    val amount = Money(row.totalMinor, currency)
                    net += amount
                    when (row.opType) {
                        "INCOME" -> income += amount
                        "EXPENSE" -> expense -= amount
                    }
                }
                AccountPeriodRow(account.id, income, expense, net)
            }
        }.distinctUntilChanged()

    private fun observeGrouped(
        filter: TransactionFilter,
        measure: Measure,
        build: (TransactionFilter, Measure, Set<Long>) -> androidx.sqlite.db.SimpleSQLiteQuery,
    ): Flow<List<NativeRow>> =
        combine(invalidations(), categoryRepository.observeAll()) { _, categories ->
            val closure = FilterScope.categoryClosure(filter, CategoryTree(categories))
            guarded { db.operationDao().aggregates(build(filter, measure, closure)) }.map { row ->
                NativeRow(row.key, Money(row.totalMinor, currency(row.currencyCode)), row.operationCount)
            }
        }.distinctUntilChanged()

    private suspend fun figures(accountId: Long, period: ResolvedPeriod): AccountFigures? {
        val account = db.accountDao().getById(accountId) ?: return null
        val currency = currency(account.currencyCode)
        val zero = Money.zero(currency)
        val rows = guarded { db.operationDao().figures(AnalyticsQuery.accountFigures(accountId, period.from, period.toExclusive)) }
        var income = zero
        var expense = zero
        var transfersIn = zero
        var transfersOut = zero
        var initial = zero
        for (row in rows) {
            val amount = Money(row.totalMinor, currency)
            when (row.opType) {
                "INCOME" -> income += amount
                "EXPENSE" -> expense -= amount
                "TRANSFER" -> if (row.positive == 1) transfersIn += amount else transfersOut -= amount
                "OPENING_BALANCE" -> initial += amount
            }
        }
        return AccountFigures(
            currency = currency,
            balanceAtStart = balanceBefore(accountId, currency, period.from),
            balanceAtEnd = balanceBefore(accountId, currency, period.toExclusive),
            income = income,
            expense = expense,
            transfersIn = transfersIn,
            transfersOut = transfersOut,
            initialBalanceInPeriod = initial,
        )
    }

    private suspend fun balanceSeries(accountId: Long, buckets: List<Bucket>): List<BalancePoint> {
        if (buckets.isEmpty()) return emptyList()
        val account = db.accountDao().getById(accountId) ?: return emptyList()
        val currency = currency(account.currencyCode)
        val start = balanceBefore(accountId, currency, buckets.first().from)
        val deltas = guarded { db.operationDao().deltas(AnalyticsQuery.balanceDeltas(accountId, buckets)) }
            .associate { it.bucketIndex to it.totalMinor }
        return BalanceSeries.build(start.amountMinor, currency, buckets, deltas)
    }

    private suspend fun balanceBefore(accountId: Long, currency: Currency, beforeExclusive: Long): Money {
        val value = guarded { db.operationDao().longValue(AnalyticsQuery.balanceBefore(accountId, beforeExclusive)) }
            .firstOrNull()?.value ?: 0L
        return Money(value, currency)
    }

    private fun currency(code: String): Currency =
        CurrencyRegistry.findOrNull(code) ?: throw InvariantViolationException("Unknown currency $code")

    /** Maps SQLite's "integer overflow" from a SUM to [MoneyOverflowException]. */
    private suspend inline fun <T> guarded(block: () -> T): T = try {
        block()
    } catch (e: MoneyOverflowException) {
        throw e
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        if (e.causes().any { it.message?.contains("integer overflow", ignoreCase = true) == true }) {
            throw MoneyOverflowException("Aggregate does not fit in a Long", e)
        }
        throw e
    }

    private fun Throwable.causes(): Sequence<Throwable> = generateSequence(this) { it.cause }.take(8)
}
