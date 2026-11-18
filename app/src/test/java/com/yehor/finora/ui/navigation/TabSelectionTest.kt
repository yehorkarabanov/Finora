package com.yehor.finora.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TabSelectionTest {

    @Test
    fun tabIsSelectedOnItsOwnRouteAndNestedRoutes() {
        assertTrue(isRouteUnder(Routes.ACCOUNTS, Routes.ACCOUNTS))
        assertTrue(isRouteUnder(Routes.ACCOUNT_DETAIL, Routes.ACCOUNTS))
        assertTrue(isRouteUnder(Routes.ACCOUNT_EDIT, Routes.ACCOUNTS))
        assertTrue(isRouteUnder(Routes.ACCOUNT_NEW, Routes.ACCOUNTS))
    }

    @Test
    fun fourTabs() = assertEquals(
        listOf("DASHBOARD", "TRANSACTIONS", "ACCOUNTS", "STATISTICS"),
        TopLevelDestination.entries.map { it.name },
    )

    @Test
    fun moreChildrenHighlightNoTab() {
        assertNull(tabFor(Routes.CATEGORIES))
        assertNull(tabFor(Routes.SETTINGS))
        assertNull(tabFor(Routes.RECURRING_DETAIL))
        assertNull(tabFor(null))
    }

    @Test
    fun accountDetailHighlightsAccounts() = assertEquals(TopLevelDestination.ACCOUNTS, tabFor(Routes.ACCOUNT_DETAIL))

    @Test
    fun moreRoutesStayUnderMorePrefix() {
        assertTrue(isRouteUnder(Routes.CATEGORIES, Routes.MORE))
        assertTrue(isRouteUnder(Routes.SETTINGS, Routes.MORE))
        assertTrue(isRouteUnder(Routes.RECURRING_EDIT, Routes.MORE))
    }

    @Test
    fun otherTabsAndSimilarPrefixesDoNotMatch() {
        assertFalse(isRouteUnder(Routes.ACCOUNTS, Routes.MORE))
        assertFalse(isRouteUnder(Routes.CATEGORIES, Routes.ACCOUNTS))
        assertFalse(isRouteUnder("accountsettings", Routes.ACCOUNTS))
        assertFalse(isRouteUnder("moreover", Routes.MORE))
        assertFalse(isRouteUnder(null, Routes.ACCOUNTS))
    }

    @Test
    fun transactionRoutesBelongToTransactionsTab() {
        assertTrue(isRouteUnder(Routes.TRANSACTIONS_PRESET, Routes.TRANSACTIONS))
        assertTrue(isRouteUnder(Routes.TRANSACTION_NEW, Routes.TRANSACTIONS))
        assertTrue(isRouteUnder(Routes.TRANSACTION_EDIT, Routes.TRANSACTIONS))
        assertFalse(isRouteUnder("transactionsarchive", Routes.TRANSACTIONS))
    }

    @Test
    fun routeBuildersMatchThePatterns() {
        assertTrue(Routes.accountDetail(5) == "accounts/5")
        assertTrue(Routes.accountEdit(5) == "accounts/5/edit")
        assertTrue(Routes.categoryNew("INCOME") == "more/categories/new?type=INCOME")
        assertTrue(Routes.categoryNew("EXPENSE", 9) == "more/categories/new?type=EXPENSE&parentId=9")
        assertTrue(Routes.categoryEdit(3) == "more/categories/3/edit")
        assertTrue(Routes.recurringDetail(7) == "more/recurring/7")
        assertTrue(Routes.recurringEdit(7) == "more/recurring/7/edit")
    }
}
