package com.yehor.finora.domain.operation

import com.yehor.finora.domain.account.Account
import com.yehor.finora.domain.account.AccountType
import com.yehor.finora.domain.model.AccountStatus
import com.yehor.finora.domain.money.Currency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FilterScopeTest {

    private val usd = Currency("USD", 2, "$", "US Dollar")
    private val eur = Currency("EUR", 2, "€", "Euro")

    private fun acc(id: Long, currency: Currency) = Account(
        id = id, name = "A$id", currency = currency, type = AccountType.BANK,
        icon = null, color = null, description = null, status = AccountStatus.ACTIVE, createdAt = 0L,
    )

    @Test
    fun scopeCurrencySingleAccount() {
        val accounts = listOf(acc(1, usd), acc(2, eur))
        val filter = TransactionFilter(accountIds = setOf(1))
        assertEquals(usd, FilterScope.scopeCurrency(filter, accounts))
    }

    @Test
    fun scopeCurrencyAllAccountsSameCurrency() {
        val accounts = listOf(acc(1, usd), acc(2, usd))
        val filter = TransactionFilter()
        assertEquals(usd, FilterScope.scopeCurrency(filter, accounts))
    }

    @Test
    fun scopeCurrencyMixedAccountsGivesNull() {
        val accounts = listOf(acc(1, usd), acc(2, eur))
        val filter = TransactionFilter()
        assertNull(FilterScope.scopeCurrency(filter, accounts))

        val filterSelectedBoth = TransactionFilter(accountIds = setOf(1, 2))
        assertNull(FilterScope.scopeCurrency(filterSelectedBoth, accounts))
    }

    @Test
    fun pruneMissingRemovesDeletedIds() {
        val filter = TransactionFilter(
            accountIds = setOf(1, 99),
            categoryIds = setOf(10, 88),
            tagIds = setOf(100, 200),
        )
        val pruned = FilterScope.pruneMissing(
            filter = filter,
            existingAccountIds = setOf(1),
            existingCategoryIds = setOf(10),
            existingTagIds = setOf(100),
        )
        assertEquals(setOf(1L), pruned.accountIds)
        assertEquals(setOf(10L), pruned.categoryIds)
        assertEquals(setOf(100L), pruned.tagIds)
    }
}
