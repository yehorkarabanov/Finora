package com.yehor.finora.ui.analytics

import com.yehor.finora.domain.account.Account
import com.yehor.finora.domain.analytics.Bucket
import com.yehor.finora.domain.analytics.Cell
import com.yehor.finora.domain.analytics.CurrencyScope
import com.yehor.finora.domain.analytics.DrillRow
import com.yehor.finora.domain.analytics.Measure
import com.yehor.finora.domain.analytics.NativeRow
import com.yehor.finora.domain.analytics.Reports
import com.yehor.finora.domain.analytics.ResolvedPeriod
import com.yehor.finora.domain.analytics.ScopedFilter
import com.yehor.finora.domain.model.OperationType
import com.yehor.finora.domain.money.Currency
import com.yehor.finora.domain.money.Money
import com.yehor.finora.domain.money.MoneyOverflowException
import com.yehor.finora.domain.operation.TransactionFilter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

fun Measure.operationType(): OperationType = when (this) {
    Measure.EXPENSE -> OperationType.EXPENSE
    Measure.INCOME -> OperationType.INCOME
    Measure.TRANSFER -> OperationType.TRANSFER
}

fun CurrencyScope.currency(): Currency = when (this) {
    is CurrencyScope.Display -> currency
    is CurrencyScope.Native -> currency
}

/** Native rows of one aggregate, or the fact that a SUM did not fit in a Long (R13). */
sealed interface Rows {
    data class Ok(val rows: List<NativeRow>) : Rows
    data object Overflow : Rows

    fun list(): List<NativeRow> = (this as? Ok)?.rows.orEmpty()
    val overflowed: Boolean get() = this is Overflow
}

fun Flow<List<NativeRow>>.asRows(): Flow<Rows> =
    map<List<NativeRow>, Rows> { Rows.Ok(it) }.catch { e ->
        if (e is MoneyOverflowException) emit(Rows.Overflow) else throw e
    }

/** The filters analytics rows hand to the Transactions screen (R15). */
object AnalyticsFilters {

    /** The period, restricted to the accounts of one currency for a native scope. */
    fun base(period: ResolvedPeriod, scope: CurrencyScope, accounts: List<Account>): TransactionFilter {
        val filter = TransactionFilter(from = period.from, toExclusive = period.toExclusive)
        return scoped(filter, scope, accounts)
    }

    fun scoped(filter: TransactionFilter, scope: CurrencyScope, accounts: List<Account>): TransactionFilter =
        if (scope is CurrencyScope.Native) ScopedFilter.forCurrency(filter, accounts, scope.currency) else filter

    fun period(period: ResolvedPeriod, types: Set<OperationType>): TransactionFilter =
        TransactionFilter(types = types, from = period.from, toExclusive = period.toExclusive)

    /** Exactly the transactions that make up the number of [row]. */
    fun drill(base: TransactionFilter, measure: Measure, row: DrillRow): TransactionFilter {
        val typed = base.copy(types = setOf(measure.operationType()))
        val id = row.categoryId
        return when {
            id == null -> typed.copy(includeUncategorized = true)
            row.isDirect -> typed.copy(categoryIds = setOf(id), categoryExact = true)
            else -> typed.copy(categoryIds = setOf(id))
        }
    }

    /** One trend bucket, keeping the category the trend follows. */
    fun bucket(trendBase: TransactionFilter, measure: Measure, bucket: Bucket): TransactionFilter =
        trendBase.copy(
            types = setOf(measure.operationType()),
            from = bucket.from,
            toExclusive = bucket.toExclusive,
        )
}

/** What the conversion note under a screen must say (R5, R6, R9). */
data class ConversionInfo(
    val converted: Boolean,
    val outdated: Boolean,
    val oldestFetchedAt: Long?,
    val excluded: List<Currency>,
    val overflowed: Boolean,
) {
    companion object {
        val NONE = ConversionInfo(false, false, null, emptyList(), false)
    }
}

fun conversionInfo(cells: Collection<Cell>): ConversionInfo {
    var converted = false
    var outdated = false
    var overflowed = false
    var oldest: Long? = null
    val excluded = sortedSetOf<Currency>(compareBy { it.code })
    for (cell in cells) {
        val basis = cell.estimate?.basis
        if (basis?.usesConversion == true) converted = true
        if (basis?.hasOutdatedRate == true) outdated = true
        if (cell.overflowed) overflowed = true
        excluded += cell.excluded
        cell.oldestFetchedAt?.let { oldest = minOf(oldest ?: it, it) }
    }
    return ConversionInfo(converted, outdated, oldest, excluded.toList(), overflowed)
}

/** "+12.4%" for 124, "-5.0%" for -50. The value is tenths of a percent. */
fun formatPercentTimesTen(value: Int): String {
    val magnitude = Math.abs(value.toLong())
    val sign = when {
        value > 0 -> "+"
        value < 0 -> "-"
        else -> ""
    }
    return "$sign${magnitude / 10}.${magnitude % 10}%"
}

/** Exact income, expense and net of one currency, for the "By currency" sheet. [null] = does not fit in a Long. */
data class CurrencyFlow(val currency: Currency, val income: Money?, val expense: Money?) {
    val net: Money?
        get() {
            val inc = income ?: return null
            val exp = expense ?: return null
            return try {
                inc - exp
            } catch (e: MoneyOverflowException) {
                null
            }
        }
}

fun currencyFlows(income: List<NativeRow>, expense: List<NativeRow>): List<CurrencyFlow> {
    val incomeByCurrency = Reports.nativeTotals(income).associateBy { it.currency }
    val expenseByCurrency = Reports.nativeTotals(expense).associateBy { it.currency }
    return (incomeByCurrency.keys + expenseByCurrency.keys)
        .sortedBy { it.code }
        .map { currency ->
            CurrencyFlow(
                currency = currency,
                income = if (currency in incomeByCurrency) incomeByCurrency.getValue(currency).money else Money.zero(currency),
                expense = if (currency in expenseByCurrency) expenseByCurrency.getValue(currency).money else Money.zero(currency),
            )
        }
}
