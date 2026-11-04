package com.yehor.finora.domain.settings

import com.yehor.finora.domain.model.AccountStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplayCurrencyResolverTest {

    private fun account(id: Long, code: String, status: AccountStatus = AccountStatus.ACTIVE) =
        AccountCurrencyInfo(id, code, status)

    @Test
    fun explicitSettingWins() {
        val r = DisplayCurrencyResolver.resolve("EUR", listOf(account(1, "PLN")), "GBP")
        assertEquals("EUR", r.currency.code)
        assertFalse(r.isAutomatic)
    }

    @Test
    fun unknownExplicitCodeFallsThrough() {
        val r = DisplayCurrencyResolver.resolve("XXX", listOf(account(1, "PLN")), "GBP")
        assertEquals("PLN", r.currency.code)
        assertTrue(r.isAutomatic)
    }

    @Test
    fun firstActiveAccountByLowestIdWhenNoExplicit() {
        val accounts = listOf(account(5, "EUR"), account(2, "UAH"), account(9, "USD"))
        val r = DisplayCurrencyResolver.resolve(null, accounts, "GBP")
        assertEquals("UAH", r.currency.code)
        assertTrue(r.isAutomatic)
    }

    @Test
    fun archivedAccountsAreSkipped() {
        val accounts = listOf(account(1, "UAH", AccountStatus.ARCHIVED), account(2, "EUR"))
        assertEquals("EUR", DisplayCurrencyResolver.resolve(null, accounts, "GBP").currency.code)
    }

    @Test
    fun archivedOnlyAccountsFallToLocale() {
        val accounts = listOf(account(1, "UAH", AccountStatus.ARCHIVED))
        assertEquals("GBP", DisplayCurrencyResolver.resolve(null, accounts, "GBP").currency.code)
    }

    @Test
    fun localeCurrencyUsedWhenNoAccounts() {
        val r = DisplayCurrencyResolver.resolve(null, emptyList(), "PLN")
        assertEquals("PLN", r.currency.code)
        assertTrue(r.isAutomatic)
    }

    @Test
    fun localeCurrencyMissingFromRegistryFallsToUsd() {
        assertEquals("USD", DisplayCurrencyResolver.resolve(null, emptyList(), "XXX").currency.code)
    }

    @Test
    fun nullLocaleFallsToUsd() {
        val r = DisplayCurrencyResolver.resolve(null, emptyList(), null)
        assertEquals("USD", r.currency.code)
        assertTrue(r.isAutomatic)
    }
}
