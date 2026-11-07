package com.yehor.finora.domain.analytics

import com.yehor.finora.domain.money.Currency
import com.yehor.finora.domain.money.Estimate
import com.yehor.finora.domain.money.EstimateBasis

/**
 * Net cash flow = income minus expense in the same scope (R3). A side with no estimate counts as
 * zero and keeps its warning flags. An overflow on either side, or in the subtraction, is an
 * overflow. When neither side has an estimate, the result has none either.
 */
fun netCashFlow(income: Cell, expense: Cell): Cell {
    val count = income.operationCount + expense.operationCount
    val excluded = income.excluded + expense.excluded
    val oldest = listOfNotNull(income.oldestFetchedAt, expense.oldestFetchedAt).minOrNull()
    val overflow = income.overflowed || expense.overflowed
    val currency: Currency? = (income.estimate ?: expense.estimate)?.currency
    if (overflow || currency == null) {
        return Cell(null, count, excluded, overflow, oldest)
    }
    val inc = income.estimate ?: Estimate.zero(currency)
    val exp = expense.estimate ?: Estimate.zero(currency)
    val minor = try {
        Math.subtractExact(inc.amountMinor, exp.amountMinor)
    } catch (e: ArithmeticException) {
        return Cell(null, count, excluded, overflowed = true, oldestFetchedAt = oldest)
    }
    val basis: EstimateBasis = inc.basis + exp.basis
    return Cell(Estimate(minor, currency, basis), count, excluded, false, oldest)
}

/** Adds up cells. An empty collection gives [Cell.NONE]. */
fun Collection<Cell>.sumCells(): Cell = fold(Cell.NONE) { acc, cell -> acc + cell }

/**
 * A cell with nothing to show and no warning becomes an exact zero in [currency], so a quiet
 * period reads "0.00" instead of "Unavailable".
 */
fun Cell.orZero(currency: Currency): Cell =
    if (estimate == null && excluded.isEmpty() && !overflowed) copy(estimate = Estimate.zero(currency)) else this
