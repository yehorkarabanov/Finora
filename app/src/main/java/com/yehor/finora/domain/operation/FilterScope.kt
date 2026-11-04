package com.yehor.finora.domain.operation

import com.yehor.finora.domain.account.Account
import com.yehor.finora.domain.money.Currency

object FilterScope {

    fun scopeCurrency(filter: TransactionFilter, accounts: List<Account>): Currency? {
        val relevant = if (filter.accountIds.isEmpty()) {
            accounts
        } else {
            accounts.filter { it.id in filter.accountIds }
        }
        val distinctCurrencies = relevant.map { it.currency }.distinct()
        return if (distinctCurrencies.size == 1) distinctCurrencies.single() else null
    }

    fun pruneMissing(
        filter: TransactionFilter,
        existingAccountIds: Set<Long>,
        existingCategoryIds: Set<Long>,
        existingTagIds: Set<Long>,
    ): TransactionFilter = filter.copy(
        accountIds = filter.accountIds.filter { it in existingAccountIds }.toSet(),
        categoryIds = filter.categoryIds.filter { it in existingCategoryIds }.toSet(),
        tagIds = filter.tagIds.filter { it in existingTagIds }.toSet(),
    )
}
