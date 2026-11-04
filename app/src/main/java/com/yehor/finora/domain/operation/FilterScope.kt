package com.yehor.finora.domain.operation

import com.yehor.finora.domain.account.Account
import com.yehor.finora.domain.money.Currency

/** A filter and sort that are valid for the current accounts, categories and tags. */
data class NormalizedFilter(
    val filter: TransactionFilter,
    val sort: TransactionSort,
    val scopeCurrency: Currency?,
    /** True when an amount range was removed because it no longer has a single scope currency. */
    val amountRangeDropped: Boolean,
)

object FilterScope {

    /** History filter preset from the optional navigation arguments. */
    fun preset(accountId: Long?, tagId: Long?, categoryId: Long?): TransactionFilter = TransactionFilter(
        accountIds = setOfNotNull(accountId),
        tagIds = setOfNotNull(tagId),
        categoryIds = setOfNotNull(categoryId),
    )

    /**
     * Prunes ids of deleted entities, drops an amount range that has no matching scope currency,
     * and falls back from an amount sort to newest first when no scope currency exists. Idempotent.
     */
    fun normalize(
        filter: TransactionFilter,
        sort: TransactionSort,
        accounts: List<Account>,
        existingCategoryIds: Set<Long>,
        existingTagIds: Set<Long>,
    ): NormalizedFilter {
        val pruned = pruneMissing(
            filter = filter,
            existingAccountIds = accounts.map { it.id }.toSet(),
            existingCategoryIds = existingCategoryIds,
            existingTagIds = existingTagIds,
        )
        val scope = scopeCurrency(pruned, accounts)
        val amount = pruned.amount
        val dropAmount = amount != null && amount.currency.code != scope?.code
        val isAmountSort = sort == TransactionSort.AMOUNT_DESC || sort == TransactionSort.AMOUNT_ASC
        return NormalizedFilter(
            filter = if (dropAmount) pruned.copy(amount = null) else pruned,
            sort = if (isAmountSort && scope == null) TransactionSort.DATE_DESC else sort,
            scopeCurrency = scope,
            amountRangeDropped = dropAmount,
        )
    }

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
