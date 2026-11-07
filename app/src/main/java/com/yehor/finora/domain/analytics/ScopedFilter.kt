package com.yehor.finora.domain.analytics

import com.yehor.finora.domain.account.Account
import com.yehor.finora.domain.money.Currency
import com.yehor.finora.domain.operation.TransactionFilter

object ScopedFilter {

    /**
     * Restricts [filter] to the accounts of [currency]: the intersection with any selected accounts,
     * or all accounts of that currency when none were selected. An empty intersection yields a
     * filter that matches nothing (a sentinel account id that no row has).
     */
    fun forCurrency(filter: TransactionFilter, accounts: List<Account>, currency: Currency): TransactionFilter {
        val ofCurrency = accounts.filter { it.currency == currency }.map { it.id }.toSet()
        val ids = if (filter.accountIds.isEmpty()) ofCurrency else filter.accountIds.intersect(ofCurrency)
        return filter.copy(accountIds = ids.ifEmpty { NO_ACCOUNT })
    }

    /** No row has this account id (ids start at 1). */
    val NO_ACCOUNT: Set<Long> = setOf(-1L)
}
