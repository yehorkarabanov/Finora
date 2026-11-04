package com.yehor.finora.domain.operation

import com.yehor.finora.domain.money.Currency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CurrencyReassignmentTest {

    private val pln = Currency("PLN", 2, "zł", "Polish Zloty")
    private val usd = Currency("USD", 2, "$", "US Dollar")

    @Test
    fun sameCurrencyRequiresNoConfirmation() {
        assertFalse(CurrencyReassignment.needsConfirmation(fromCurrency = pln, toCurrency = pln))
    }

    @Test
    fun differentCurrencyRequiresConfirmation() {
        assertTrue(CurrencyReassignment.needsConfirmation(fromCurrency = pln, toCurrency = usd))
    }

    @Test
    fun buildsInfoForDialog() {
        val info = CurrencyReassignment.buildInfo(
            oldAmountMinor = 10000L,
            oldCurrency = pln,
            newCurrency = usd,
            oldAccountName = "Santander",
            newAccountName = "Chase",
            isExpense = true,
        )
        assertEquals("100.00 PLN", info.oldFormatted)
        assertEquals("100.00 USD", info.newFormatted)
        assertEquals("Santander", info.oldAccountName)
        assertEquals("Chase", info.newAccountName)
    }
}
