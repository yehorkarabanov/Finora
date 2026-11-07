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
    fun presetSetsOnlyGivenIds() {
        assertEquals(TransactionFilter(), FilterScope.preset(null, null, null))
        assertEquals(setOf(5L), FilterScope.preset(5, null, null).accountIds)
        assertEquals(setOf(6L), FilterScope.preset(null, 6, null).tagIds)
        assertEquals(setOf(7L), FilterScope.preset(null, null, 7).categoryIds)
    }

    @Test
    fun normalizeKeepsAmountRangeWhileSingleCurrency() {
        val accounts = listOf(acc(1, usd), acc(2, usd))
        val filter = TransactionFilter(amount = AmountRange(usd, 100, null))
        val n = FilterScope.normalize(filter, TransactionSort.AMOUNT_DESC, accounts, emptySet(), emptySet())
        assertEquals(filter, n.filter)
        assertEquals(TransactionSort.AMOUNT_DESC, n.sort)
        assertEquals(usd, n.scopeCurrency)
        assertEquals(false, n.amountRangeDropped)
    }

    @Test
    fun normalizeDropsAmountRangeAndKeepsAmountSortWhenScopeBecomesMultiCurrency() {
        val accounts = listOf(acc(1, usd), acc(2, eur))
        val filter = TransactionFilter(amount = AmountRange(usd, 100, null))
        val n = FilterScope.normalize(filter, TransactionSort.AMOUNT_ASC, accounts, emptySet(), emptySet())
        assertNull(n.filter.amount)
        assertEquals(TransactionSort.AMOUNT_ASC, n.sort)
        assertNull(n.scopeCurrency)
        assertEquals(true, n.amountRangeDropped)
    }

    @Test
    fun normalizeDropsAmountRangeInOtherCurrencyThanScope() {
        val accounts = listOf(acc(1, usd), acc(2, eur))
        val filter = TransactionFilter(accountIds = setOf(2), amount = AmountRange(usd, 100, null))
        val n = FilterScope.normalize(filter, TransactionSort.DATE_ASC, accounts, emptySet(), emptySet())
        assertNull(n.filter.amount)
        assertEquals(TransactionSort.DATE_ASC, n.sort)
        assertEquals(eur, n.scopeCurrency)
    }

    @Test
    fun normalizeIsIdempotentAndPrunesStaleIds() {
        val accounts = listOf(acc(1, usd))
        val filter = TransactionFilter(accountIds = setOf(1, 9), categoryIds = setOf(3, 4), exactCategoryIds = setOf(3, 4), tagIds = setOf(8))
        val once = FilterScope.normalize(filter, TransactionSort.DATE_DESC, accounts, setOf(3), emptySet())
        assertEquals(setOf(1L), once.filter.accountIds)
        assertEquals(setOf(3L), once.filter.categoryIds)
        assertEquals(setOf(3L), once.filter.exactCategoryIds)
        assertEquals(emptySet<Long>(), once.filter.tagIds)
        val twice = FilterScope.normalize(once.filter, once.sort, accounts, setOf(3), emptySet())
        assertEquals(once, twice)
    }

    @Test
    fun pruneMissingRemovesDeletedIds() {
        val filter = TransactionFilter(
            accountIds = setOf(1, 99),
            categoryIds = setOf(10, 88),
            exactCategoryIds = setOf(10, 88),
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
        assertEquals(setOf(10L), pruned.exactCategoryIds)
        assertEquals(setOf(100L), pruned.tagIds)
    }

    @Test
    fun categoryClosureHonorsExactCategoryIds() {
        val tree = com.yehor.finora.domain.category.CategoryTree(
            listOf(
                com.yehor.finora.domain.category.Category(1, "Food", null, null, com.yehor.finora.domain.model.CategoryType.EXPENSE, null, 0L),
                com.yehor.finora.domain.category.Category(2, "Groceries", null, null, com.yehor.finora.domain.model.CategoryType.EXPENSE, 1, 0L),
                com.yehor.finora.domain.category.Category(3, "Transport", null, null, com.yehor.finora.domain.model.CategoryType.EXPENSE, null, 0L),
                com.yehor.finora.domain.category.Category(4, "Bus", null, null, com.yehor.finora.domain.model.CategoryType.EXPENSE, 3, 0L),
            ),
        )
        // Stage 1 (Direct only): exactCategoryIds = {1}
        val directFilter = TransactionFilter(categoryIds = setOf(1), exactCategoryIds = setOf(1))
        assertEquals(setOf(1L), FilterScope.categoryClosure(directFilter, tree))

        // Stage 2 (Direct + Children): exactCategoryIds = empty
        val recursiveFilter = TransactionFilter(categoryIds = setOf(1), exactCategoryIds = emptySet())
        assertEquals(setOf(1L, 2L), FilterScope.categoryClosure(recursiveFilter, tree))

        // Mixed: 1 is direct only, 3 is recursive
        val mixedFilter = TransactionFilter(categoryIds = setOf(1, 3), exactCategoryIds = setOf(1))
        assertEquals(setOf(1L, 3L, 4L), FilterScope.categoryClosure(mixedFilter, tree))

        // Global exact overrides everything
        val globalExact = TransactionFilter(categoryIds = setOf(1, 3), categoryExact = true)
        assertEquals(setOf(1L, 3L), FilterScope.categoryClosure(globalExact, tree))
    }
}
