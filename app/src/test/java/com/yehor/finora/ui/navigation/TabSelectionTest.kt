package com.yehor.finora.ui.navigation

import org.junit.Assert.assertFalse
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
    fun moreTabCoversItsScreens() {
        assertTrue(isRouteUnder(Routes.CATEGORIES, Routes.MORE))
        assertTrue(isRouteUnder(Routes.CATEGORY_NEW, Routes.MORE))
        assertTrue(isRouteUnder(Routes.CATEGORY_EDIT, Routes.MORE))
        assertTrue(isRouteUnder(Routes.SETTINGS, Routes.MORE))
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
    fun routeBuildersMatchThePatterns() {
        assertTrue(Routes.accountDetail(5) == "accounts/5")
        assertTrue(Routes.accountEdit(5) == "accounts/5/edit")
        assertTrue(Routes.categoryNew("INCOME") == "more/categories/new?type=INCOME")
        assertTrue(Routes.categoryNew("EXPENSE", 9) == "more/categories/new?type=EXPENSE&parentId=9")
        assertTrue(Routes.categoryEdit(3) == "more/categories/3/edit")
    }
}
