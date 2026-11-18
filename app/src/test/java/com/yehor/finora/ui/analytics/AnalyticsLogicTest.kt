package com.yehor.finora.ui.analytics

import com.yehor.finora.domain.account.Account
import com.yehor.finora.domain.account.AccountType
import com.yehor.finora.domain.analytics.Bucket
import com.yehor.finora.domain.analytics.Cell
import com.yehor.finora.domain.analytics.CurrencyScope
import com.yehor.finora.domain.analytics.DrillRow
import com.yehor.finora.domain.analytics.Measure
import com.yehor.finora.domain.analytics.NativeRow
import com.yehor.finora.domain.analytics.PeriodCalculator
import com.yehor.finora.domain.analytics.PeriodKind
import com.yehor.finora.domain.category.Category
import com.yehor.finora.domain.model.AccountStatus
import com.yehor.finora.domain.model.CategoryType
import com.yehor.finora.domain.model.OperationType
import com.yehor.finora.domain.money.CurrencyRegistry
import com.yehor.finora.domain.money.Estimate
import com.yehor.finora.domain.money.EstimateBasis
import com.yehor.finora.domain.money.Money
import com.yehor.finora.domain.operation.TransactionFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset

class AnalyticsLogicTest {

    private val pln = CurrencyRegistry.get("PLN")
    private val usd = CurrencyRegistry.get("USD")
    private val eur = CurrencyRegistry.get("EUR")

    private val period = PeriodCalculator.resolve(
        PeriodCalculator.preset(PeriodKind.MONTH, LocalDate.of(2026, 11, 15)),
        ZoneOffset.UTC,
        DayOfWeek.MONDAY,
    )

    private fun account(id: Long, currency: com.yehor.finora.domain.money.Currency) =
        Account(id, "A$id", currency, AccountType.BANK, null, null, null, AccountStatus.ACTIVE, 0)

    private val accounts = listOf(account(1, pln), account(2, usd), account(3, pln))

    private val food = Category(5, "Food", null, null, CategoryType.EXPENSE, null, 0)

    private fun cell(
        minor: Long,
        currency: com.yehor.finora.domain.money.Currency = pln,
        converted: Boolean = false,
        outdated: Boolean = false,
        excluded: Set<com.yehor.finora.domain.money.Currency> = emptySet(),
        overflowed: Boolean = false,
        fetchedAt: Long? = null,
    ) = Cell(
        estimate = Estimate(minor, currency, EstimateBasis(converted, outdated)),
        operationCount = 1,
        excluded = excluded,
        overflowed = overflowed,
        oldestFetchedAt = fetchedAt,
    )

    // ---- AnalyticsFilters ----

    @Test
    fun baseCoversThePeriodAndAllAccountsInDisplayScope() {
        val filter = AnalyticsFilters.base(period, CurrencyScope.Display(pln), accounts)
        assertEquals(period.from, filter.from)
        assertEquals(period.toExclusive, filter.toExclusive)
        assertTrue(filter.accountIds.isEmpty())
    }

    @Test
    fun baseInANativeScopeIsRestrictedToThatCurrencyAccounts() {
        val filter = AnalyticsFilters.base(period, CurrencyScope.Native(pln), accounts)
        assertEquals(setOf(1L, 3L), filter.accountIds)
        assertEquals(period.from, filter.from)
    }

    @Test
    fun aNativeScopeWithoutAccountsMatchesNothing() {
        val filter = AnalyticsFilters.base(period, CurrencyScope.Native(eur), accounts)
        assertFalse(filter.accountIds.isEmpty())
        assertTrue(filter.accountIds.none { id -> accounts.any { it.id == id } })
    }

    @Test
    fun periodFilterCarriesTypesAndDates() {
        val filter = AnalyticsFilters.period(period, setOf(OperationType.INCOME))
        assertEquals(setOf(OperationType.INCOME), filter.types)
        assertEquals(period.from, filter.from)
        assertEquals(period.toExclusive, filter.toExclusive)
    }

    @Test
    fun drillIntoACategoryRowIncludesItsDescendants() {
        val base = AnalyticsFilters.base(period, CurrencyScope.Display(pln), accounts)
        val row = DrillRow(5, food, cell(100), isDirect = false, hasChildren = true)
        val filter = AnalyticsFilters.drill(base, Measure.EXPENSE, row)
        assertEquals(setOf(5L), filter.categoryIds)
        assertFalse(filter.categoryExact)
        assertFalse(filter.includeUncategorized)
        assertEquals(setOf(OperationType.EXPENSE), filter.types)
    }

    @Test
    fun drillIntoADirectRowIsExactlyThatCategory() {
        val base = AnalyticsFilters.base(period, CurrencyScope.Display(pln), accounts)
        val row = DrillRow(5, food, cell(100), isDirect = true, hasChildren = false)
        val filter = AnalyticsFilters.drill(base, Measure.INCOME, row)
        assertEquals(setOf(5L), filter.categoryIds)
        assertTrue(filter.categoryExact)
        assertEquals(setOf(OperationType.INCOME), filter.types)
    }

    @Test
    fun drillIntoUncategorizedHasNoCategoryButTheUncategorizedFlag() {
        val base = AnalyticsFilters.base(period, CurrencyScope.Display(pln), accounts)
        val row = DrillRow(null, null, cell(100), isDirect = false, hasChildren = false)
        val filter = AnalyticsFilters.drill(base, Measure.EXPENSE, row)
        assertTrue(filter.includeUncategorized)
        assertTrue(filter.categoryIds.isEmpty())
    }

    @Test
    fun drillKeepsTheNativeScopeAccounts() {
        val base = AnalyticsFilters.base(period, CurrencyScope.Native(usd), accounts)
        val row = DrillRow(5, food, cell(100), isDirect = false, hasChildren = false)
        assertEquals(setOf(2L), AnalyticsFilters.drill(base, Measure.EXPENSE, row).accountIds)
    }

    @Test
    fun bucketReplacesTheDatesAndKeepsTheTrendCategory() {
        val trendBase = TransactionFilter(categoryIds = setOf(7))
        val bucket = Bucket(0, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), 1_000L, 2_000L)
        val filter = AnalyticsFilters.bucket(trendBase, Measure.EXPENSE, bucket)
        assertEquals(1_000L, filter.from)
        assertEquals(2_000L, filter.toExclusive)
        assertEquals(setOf(7L), filter.categoryIds)
        assertEquals(setOf(OperationType.EXPENSE), filter.types)
    }

    // ---- conversionInfo ----

    @Test
    fun conversionInfoOfNothingIsQuiet() {
        val info = conversionInfo(emptyList())
        assertFalse(info.converted)
        assertFalse(info.outdated)
        assertFalse(info.overflowed)
        assertTrue(info.excluded.isEmpty())
        assertNull(info.oldestFetchedAt)
    }

    @Test
    fun conversionInfoFlagsConversionAndOutdatedRates() {
        val info = conversionInfo(listOf(cell(10), cell(20, converted = true, outdated = true, fetchedAt = 500)))
        assertTrue(info.converted)
        assertTrue(info.outdated)
        assertEquals(500L, info.oldestFetchedAt)
    }

    @Test
    fun conversionInfoTakesTheOldestFetchTimeAndSortsExcludedCurrencies() {
        val info = conversionInfo(
            listOf(
                cell(10, converted = true, fetchedAt = 900, excluded = setOf(usd)),
                cell(10, converted = true, fetchedAt = 300, excluded = setOf(eur, usd)),
            ),
        )
        assertEquals(300L, info.oldestFetchedAt)
        assertEquals(listOf("EUR", "USD"), info.excluded.map { it.code })
    }

    @Test
    fun conversionInfoReportsAnOverflow() {
        assertTrue(conversionInfo(listOf(cell(10, overflowed = true))).overflowed)
    }

    @Test
    fun aPlainNativeCellIsNotConverted() {
        assertFalse(conversionInfo(listOf(cell(10))).converted)
    }

    // ---- currencyFlows ----

    private fun row(key: Long?, minor: Long, currency: com.yehor.finora.domain.money.Currency) =
        NativeRow(key, Money(minor, currency), 1)

    @Test
    fun currencyFlowsListsEveryCurrencyWithAnExactNet() {
        val flows = currencyFlows(
            income = listOf(row(1, 1000, usd), row(2, 500, pln)),
            expense = listOf(row(1, 300, pln)),
        )
        assertEquals(listOf("PLN", "USD"), flows.map { it.currency.code })
        val plnFlow = flows[0]
        assertEquals(500L, plnFlow.income!!.amountMinor)
        assertEquals(300L, plnFlow.expense!!.amountMinor)
        assertEquals(200L, plnFlow.net!!.amountMinor)
        val usdFlow = flows[1]
        assertEquals(1000L, usdFlow.income!!.amountMinor)
        assertEquals(0L, usdFlow.expense!!.amountMinor)
        assertEquals(1000L, usdFlow.net!!.amountMinor)
    }

    @Test
    fun currencyFlowsOfNothingIsEmpty() {
        assertTrue(currencyFlows(emptyList(), emptyList()).isEmpty())
    }

    @Test
    fun aNetThatDoesNotFitIsNull() {
        val flow = CurrencyFlow(pln, Money(Long.MAX_VALUE, pln), Money(-1, pln))
        assertNull(flow.net)
    }

    @Test
    fun aMissingSideMakesTheNetNull() {
        assertNull(CurrencyFlow(pln, null, Money(1, pln)).net)
    }

    // ---- formatPercentTimesTen ----

    @Test
    fun percentIsFormattedWithOneDecimalAndAnExplicitSign() {
        assertEquals("+12.4%", formatPercentTimesTen(124))
        assertEquals("-5.0%", formatPercentTimesTen(-50))
        assertEquals("0.0%", formatPercentTimesTen(0))
        assertEquals("-0.5%", formatPercentTimesTen(-5))
        assertEquals("+100.0%", formatPercentTimesTen(1000))
    }

    @Test
    fun percentOfTheSmallestIntDoesNotOverflow() {
        assertEquals("-214748364.8%", formatPercentTimesTen(Int.MIN_VALUE))
    }

    // ---- resolveScope ----

    @Test
    fun resolveScopeWithNoSelectionUsesSameCurrencyWhenAllAccountsMatch() {
        val sameAccounts = listOf(account(1, pln), account(3, pln))
        val scope = resolveScope(emptySet(), sameAccounts, usd)
        assertEquals(CurrencyScope.Display(pln), scope)
    }

    @Test
    fun resolveScopeWithNoSelectionUsesDisplayCurrencyWhenAccountsDiffer() {
        val scope = resolveScope(emptySet(), accounts, usd)
        assertEquals(CurrencyScope.Display(usd), scope)
    }

    @Test
    fun resolveScopeWithSingleAccountSelectionUsesAccountCurrency() {
        val scope = resolveScope(setOf(2L), accounts, pln)
        assertEquals(CurrencyScope.Display(usd), scope)
    }

    @Test
    fun resolveScopeWithMultipleAccountsOfSameCurrencyUsesTheirCurrency() {
        val scope = resolveScope(setOf(1L, 3L), accounts, usd)
        assertEquals(CurrencyScope.Display(pln), scope)
    }

    @Test
    fun resolveScopeWithMultipleAccountsOfDifferentCurrenciesUsesDisplayCurrency() {
        val scope = resolveScope(setOf(1L, 2L), accounts, eur)
        assertEquals(CurrencyScope.Display(eur), scope)
    }

    @Test
    fun resolveScopeWithEmptyAccountsListUsesDisplayCurrency() {
        val scope = resolveScope(emptySet(), emptyList(), pln)
        assertEquals(CurrencyScope.Display(pln), scope)
    }
}
