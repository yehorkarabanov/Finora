package com.yehor.finora.domain.operation

import com.yehor.finora.domain.money.Currency
import com.yehor.finora.domain.money.Money

data class ReassignmentDialogInfo(
    val oldFormatted: String,
    val newFormatted: String,
    val oldAccountName: String,
    val newAccountName: String,
    val isExpense: Boolean,
)

object CurrencyReassignment {

    fun needsConfirmation(fromCurrency: Currency, toCurrency: Currency): Boolean =
        fromCurrency.code != toCurrency.code

    fun buildInfo(
        oldAmountMinor: Long,
        oldCurrency: Currency,
        newCurrency: Currency,
        oldAccountName: String,
        newAccountName: String,
        isExpense: Boolean,
    ): ReassignmentDialogInfo {
        val oldMoney = Money(oldAmountMinor, oldCurrency)
        val newMoney = Money(oldAmountMinor, newCurrency)
        return ReassignmentDialogInfo(
            oldFormatted = "${oldMoney.toPlainString()} ${oldCurrency.code}",
            newFormatted = "${newMoney.toPlainString()} ${newCurrency.code}",
            oldAccountName = oldAccountName,
            newAccountName = newAccountName,
            isExpense = isExpense,
        )
    }
}
