package com.yehor.finora.ui.operations

import com.yehor.finora.domain.money.Currency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TransferFormLogicTest {

    private val pln = Currency("PLN", 2, "zł", "Zloty")
    private val usd = Currency("USD", 2, "$", "US Dollar")

    @Test
    fun swapExchangesAccountsAndFollowsCurrencyForAmounts() {
        val swapped = TransferFormLogic.swap(TransferSides(1, 2, "1000", "250"), pln, usd)
        assertEquals(TransferSides(2, 1, "250", "1000"), swapped)
    }

    @Test
    fun swapKeepsTypedAmountsWhenCurrenciesMatch() {
        val swapped = TransferFormLogic.swap(TransferSides(1, 2, "100", "95"), pln, pln)
        assertEquals(TransferSides(2, 1, "100", "95"), swapped)
    }

    @Test
    fun swapWithUnknownAccountKeepsAmountsWithTheirCurrencies() {
        val swapped = TransferFormLogic.swap(TransferSides(1, null, "100", ""), pln, null)
        assertEquals(TransferSides(null, 1, "", "100"), swapped)
    }

    @Test
    fun rateShownOnlyForCrossCurrency() {
        val rate = TransferFormLogic.effectiveRate("1000", "250", pln, usd)
        assertNotNull(rate)
        assertEquals("1 PLN = 0.2500 USD", rate!!.forward)
        assertEquals("1 USD = 4.0000 PLN", rate.inverse)
        assertNull(TransferFormLogic.effectiveRate("100", "95", pln, pln))
    }

    @Test
    fun rateHiddenForIncompleteOrInvalidInput() {
        assertNull(TransferFormLogic.effectiveRate("", "250", pln, usd))
        assertNull(TransferFormLogic.effectiveRate("1000", "abc", pln, usd))
        assertNull(TransferFormLogic.effectiveRate("0", "250", pln, usd))
        assertNull(TransferFormLogic.effectiveRate("1000", "250", null, usd))
    }
}
