package com.yehor.finora.domain.analytics

import com.yehor.finora.domain.account.Account
import com.yehor.finora.domain.account.AccountType
import com.yehor.finora.domain.model.AccountStatus
import com.yehor.finora.domain.model.OperationType
import com.yehor.finora.domain.money.CurrencyRegistry
import com.yehor.finora.domain.operation.TransactionFilter
import org.junit.Assert.assertEquals
import org.junit.Test

class ScopedFilterTest {

    private val pln = CurrencyRegistry.get("PLN")
    private val usd = CurrencyRegistry.get("USD")

    private fun account(id: Long, currency: com.yehor.finora.domain.money.Currency) =
        Account(id, "A$id", currency, AccountType.BANK, null, null, null, AccountStatus.ACTIVE, 0)

    private val accounts = listOf(account(1, pln), account(2, usd), account(3, pln), account(4, usd))

    @Test
    fun noSelectionBecomesAllAccountsOfTheCurrency() {
        val scoped = ScopedFilter.forCurrency(TransactionFilter(), accounts, pln)
        assertEquals(setOf(1L, 3L), scoped.accountIds)
    }

    @Test
    fun selectionIsIntersectedWithTheCurrency() {
        val scoped = ScopedFilter.forCurrency(TransactionFilter(accountIds = setOf(1, 2)), accounts, pln)
        assertEquals(setOf(1L), scoped.accountIds)
    }

    @Test
    fun emptyIntersectionMatchesNothing() {
        val scoped = ScopedFilter.forCurrency(TransactionFilter(accountIds = setOf(2, 4)), accounts, pln)
        assertEquals(ScopedFilter.NO_ACCOUNT, scoped.accountIds)
    }

    @Test
    fun currencyWithoutAccountsMatchesNothing() {
        val eur = CurrencyRegistry.get("EUR")
        assertEquals(ScopedFilter.NO_ACCOUNT, ScopedFilter.forCurrency(TransactionFilter(), accounts, eur).accountIds)
    }

    @Test
    fun otherFieldsAreKept() {
        val filter = TransactionFilter(types = setOf(OperationType.EXPENSE), categoryIds = setOf(5), categoryExact = true, from = 10)
        val scoped = ScopedFilter.forCurrency(filter, accounts, usd)
        assertEquals(filter.copy(accountIds = setOf(2L, 4L)), scoped)
    }
}
