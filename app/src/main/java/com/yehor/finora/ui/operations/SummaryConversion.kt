package com.yehor.finora.ui.operations

import com.yehor.finora.domain.money.Currency
import com.yehor.finora.domain.money.Estimate
import com.yehor.finora.domain.operation.CurrencySummary
import com.yehor.finora.domain.rate.RateConverter
import com.yehor.finora.domain.rate.RateTable

/** Income, expense and net of a multi-currency summary, converted to the Display Currency. */
data class ConvertedSummary(
    val income: Estimate?,
    val expense: Estimate?,
    val net: Estimate?,
    val excluded: List<Currency>,
    val oldestFetchedAt: Long?,
) {
    val outdated: Boolean
        get() = listOf(income, expense, net).any { it?.basis?.hasOutdatedRate == true }
}

object SummaryConversion {

    /** Null for an empty or single-currency summary, which stays native. */
    fun build(
        summary: List<CurrencySummary>,
        display: Currency,
        table: RateTable,
        now: Long,
    ): ConvertedSummary? {
        if (summary.size < 2) return null
        val converter = RateConverter(table, now)
        val income = converter.value(summary.map { it.income }, display)
        val expense = converter.value(summary.map { it.expense }, display)
        val net = income.total?.let { i ->
            expense.total?.let { e ->
                try {
                    Estimate(Math.subtractExact(i.amountMinor, e.amountMinor), display, i.basis + e.basis)
                } catch (ex: ArithmeticException) {
                    null
                }
            }
        }
        return ConvertedSummary(
            income = income.total,
            expense = expense.total,
            net = net,
            excluded = (income.excluded + expense.excluded).sortedBy { it.code },
            oldestFetchedAt = listOfNotNull(income.oldestFetchedAt, expense.oldestFetchedAt).minOrNull(),
        )
    }
}
