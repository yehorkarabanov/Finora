package com.yehor.finora.ui.operations

import com.yehor.finora.domain.money.Currency
import com.yehor.finora.domain.money.Money
import com.yehor.finora.domain.operation.TransferDraftError
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

    // --- received amount linking ---

    @Test
    fun syncReceivedMirrorsSentWhenLinkedAndSameCurrency() {
        assertEquals("100", TransferFormLogic.syncReceived("100", "55", true, pln, pln))
    }

    @Test
    fun syncReceivedMirrorsSentWhenLinkedAndCurrencyUnknown() {
        assertEquals("100", TransferFormLogic.syncReceived("100", "", true, null, usd))
        assertEquals("100", TransferFormLogic.syncReceived("100", "", true, pln, null))
        assertEquals("100", TransferFormLogic.syncReceived("100", "", true, null, null))
    }

    @Test
    fun syncReceivedIsEmptyWhenLinkedAcrossCurrencies() {
        assertEquals("", TransferFormLogic.syncReceived("100", "25", true, pln, usd))
    }

    @Test
    fun syncReceivedKeepsUserTextWhenNotLinked() {
        assertEquals("95", TransferFormLogic.syncReceived("100", "95", false, pln, pln))
        assertEquals("25", TransferFormLogic.syncReceived("100", "25", false, pln, usd))
        assertEquals("", TransferFormLogic.syncReceived("100", "", false, pln, pln))
    }

    @Test
    fun linkedSwapKeepsSentTextAndResyncsReceived() {
        val sameCurrency = TransferFormLogic.swap(TransferSides(1, 2, "100", "100"), pln, pln, linked = true)
        assertEquals(TransferSides(2, 1, "100", "100"), sameCurrency)

        // Linked across currencies the received amount is still unknown: nothing jumps between the fields.
        val crossCurrency = TransferFormLogic.swap(TransferSides(1, 2, "100", ""), pln, usd, linked = true)
        assertEquals(TransferSides(2, 1, "100", ""), crossCurrency)
    }

    @Test
    fun unlinkedSwapKeepsTheOriginalBehaviour() {
        val swapped = TransferFormLogic.swap(TransferSides(1, 2, "1000", "250"), pln, usd, linked = false)
        assertEquals(TransferSides(2, 1, "250", "1000"), swapped)
    }

    @Test
    fun isLinkedNeedsSameCurrencyAndSameAmount() {
        assertEquals(true, TransferFormLogic.isLinked(Money(10_000, pln), Money(10_000, pln)))
        assertEquals(false, TransferFormLogic.isLinked(Money(10_000, pln), Money(9_500, pln)))
        assertEquals(false, TransferFormLogic.isLinked(Money(10_000, pln), Money(10_000, usd)))
    }

    // --- CTA blocker priority ---

    @Test
    fun blockerIsNullWhenValid() {
        assertNull(TransferFormLogic.blocker(emptySet()))
    }

    @Test
    fun blockerFollowsPriority() {
        val all = TransferDraftError.entries.toSet()
        assertEquals(TransferBlocker.SENT_EMPTY, TransferFormLogic.blocker(all))
        val noSent = all - TransferDraftError.SENT_AMOUNT_EMPTY
        assertEquals(TransferBlocker.SOURCE_MISSING, TransferFormLogic.blocker(noSent))
        val noSource = noSent - TransferDraftError.SOURCE_ACCOUNT_MISSING
        assertEquals(TransferBlocker.DEST_MISSING, TransferFormLogic.blocker(noSource))
        val noDest = noSource - TransferDraftError.DEST_ACCOUNT_MISSING
        assertEquals(TransferBlocker.SAME_ACCOUNTS, TransferFormLogic.blocker(noDest))
        val noSame = noDest - TransferDraftError.SAME_ACCOUNTS
        assertEquals(TransferBlocker.RECEIVED_EMPTY, TransferFormLogic.blocker(noSame))
        val noReceived = noSame - TransferDraftError.RECEIVED_AMOUNT_EMPTY
        assertEquals(TransferBlocker.CATEGORY_MISSING, TransferFormLogic.blocker(noReceived))
    }

    @Test
    fun blockerIgnoresErrorsShownInline() {
        assertNull(TransferFormLogic.blocker(setOf(TransferDraftError.DATE_IN_FUTURE)))
        assertNull(TransferFormLogic.blocker(setOf(TransferDraftError.SENT_AMOUNT_INVALID)))
    }

    @Test
    fun receivedRowHiddenAndDifferentAmountOfferedWhenSameCurrencyAndLinked() {
        assertEquals(false, TransferFormLogic.showReceivedRow(pln, pln, linked = true))
        assertEquals(true, TransferFormLogic.showDifferentAmountAction(pln, pln, linked = true))
    }

    @Test
    fun receivedRowShownWhenSameCurrencyButUnlinked() {
        assertEquals(true, TransferFormLogic.showReceivedRow(pln, pln, linked = false))
        assertEquals(false, TransferFormLogic.showDifferentAmountAction(pln, pln, linked = false))
    }

    @Test
    fun receivedRowShownAcrossCurrencies() {
        assertEquals(true, TransferFormLogic.showReceivedRow(pln, usd, linked = true))
        assertEquals(true, TransferFormLogic.showReceivedRow(pln, usd, linked = false))
        assertEquals(false, TransferFormLogic.showDifferentAmountAction(pln, usd, linked = true))
    }

    @Test
    fun unknownCurrencyShowsNeitherWhileLinked() {
        assertEquals(false, TransferFormLogic.showReceivedRow(pln, null, linked = true))
        assertEquals(false, TransferFormLogic.showDifferentAmountAction(pln, null, linked = true))
        assertEquals(false, TransferFormLogic.showDifferentAmountAction(null, usd, linked = true))
        assertEquals(true, TransferFormLogic.showReceivedRow(null, usd, linked = false))
    }
}
