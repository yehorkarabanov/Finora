package com.yehor.finora.domain.analytics

import com.yehor.finora.domain.model.CategoryType
import com.yehor.finora.domain.money.Currency
import com.yehor.finora.domain.money.Estimate
import com.yehor.finora.domain.money.EstimateBasis
import com.yehor.finora.domain.money.Money
import com.yehor.finora.domain.money.MoneyOverflowException
import com.yehor.finora.domain.rate.RateConverter
import com.yehor.finora.domain.rate.RateTable

enum class Measure {
    EXPENSE,
    INCOME,
    TRANSFER;

    val categoryType: CategoryType
        get() = when (this) {
            EXPENSE -> CategoryType.EXPENSE
            INCOME -> CategoryType.INCOME
            TRANSFER -> CategoryType.TRANSFER
        }
}

sealed interface CurrencyScope {
    /** Every amount converted to [currency] and shown as an estimate. */
    data class Display(val currency: Currency) : CurrencyScope

    /** Only amounts of accounts in [currency]. Exact, never converted. */
    data class Native(val currency: Currency) : CurrencyScope
}

/** The native sum of one group in one currency. */
data class NativeAmount(val money: Money, val operationCount: Int)

/** One converted group. [estimate] is null when nothing in the group could be converted. */
data class Cell(
    val estimate: Estimate?,
    val operationCount: Int,
    /** Currencies of this group with no direct rate. */
    val excluded: Set<Currency>,
    val overflowed: Boolean,
    /** Oldest fetch time over the rates actually used. */
    val oldestFetchedAt: Long?,
) {
    val partial: Boolean get() = excluded.isNotEmpty()

    /** No amount, no warning: nothing happened here. */
    val isEmpty: Boolean
        get() = estimate == null && operationCount == 0 && excluded.isEmpty() && !overflowed

    /** True when the estimate exists and is not zero. */
    val hasAmount: Boolean get() = estimate != null && estimate.amountMinor != 0L

    /** Additive combination. A missing estimate counts as absent, an overflow stays an overflow. */
    operator fun plus(other: Cell): Cell {
        val overflow = overflowed || other.overflowed
        val sum: Estimate? = when {
            overflow -> null
            estimate == null -> other.estimate
            other.estimate == null -> estimate
            else -> try {
                estimate + other.estimate
            } catch (e: MoneyOverflowException) {
                return Cell(
                    estimate = null,
                    operationCount = operationCount + other.operationCount,
                    excluded = excluded + other.excluded,
                    overflowed = true,
                    oldestFetchedAt = oldest(oldestFetchedAt, other.oldestFetchedAt),
                )
            }
        }
        return Cell(
            estimate = sum,
            operationCount = operationCount + other.operationCount,
            excluded = excluded + other.excluded,
            overflowed = overflow,
            oldestFetchedAt = oldest(oldestFetchedAt, other.oldestFetchedAt),
        )
    }

    companion object {
        val NONE = Cell(estimate = null, operationCount = 0, excluded = emptySet(), overflowed = false, oldestFetchedAt = null)

        private fun oldest(a: Long?, b: Long?): Long? = when {
            a == null -> b
            b == null -> a
            else -> minOf(a, b)
        }
    }
}

/**
 * Turns the native rows of one group into a [Cell]. Rows of one currency are summed first, so a
 * group is converted and rounded once per currency.
 */
class ScopeConverter(private val scope: CurrencyScope, private val table: RateTable, private val now: Long) {

    fun cell(nativeRows: List<NativeAmount>): Cell {
        val count = nativeRows.sumOf { it.operationCount }
        return when (scope) {
            is CurrencyScope.Native -> nativeCell(scope.currency, nativeRows, count)
            is CurrencyScope.Display -> displayCell(scope.currency, nativeRows, count)
        }
    }

    private fun nativeCell(currency: Currency, rows: List<NativeAmount>, count: Int): Cell {
        val own = rows.filter { it.money.currency == currency }
        return try {
            val total = own.fold(Money.zero(currency)) { acc, row -> acc + row.money }
            Cell(
                estimate = Estimate(total.amountMinor, currency, EstimateBasis.NATIVE),
                operationCount = count,
                excluded = emptySet(),
                overflowed = false,
                oldestFetchedAt = null,
            )
        } catch (e: MoneyOverflowException) {
            Cell(null, count, emptySet(), overflowed = true, oldestFetchedAt = null)
        }
    }

    private fun displayCell(target: Currency, rows: List<NativeAmount>, count: Int): Cell {
        val sums = LinkedHashMap<Currency, Money>()
        try {
            for (row in rows) {
                val money = row.money
                sums[money.currency] = sums[money.currency]?.plus(money) ?: money
            }
        } catch (e: MoneyOverflowException) {
            return Cell(null, count, emptySet(), overflowed = true, oldestFetchedAt = null)
        }
        val valuation = RateConverter(table, now).value(sums.values.toList(), target)
        return Cell(
            estimate = valuation.total,
            operationCount = count,
            excluded = valuation.excluded,
            overflowed = valuation.overflowed,
            oldestFetchedAt = valuation.oldestFetchedAt,
        )
    }
}
