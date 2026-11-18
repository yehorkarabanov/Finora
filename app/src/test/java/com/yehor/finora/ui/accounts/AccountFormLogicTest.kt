package com.yehor.finora.ui.accounts

import com.yehor.finora.domain.account.AccountDraftError
import com.yehor.finora.domain.account.Balance
import com.yehor.finora.domain.account.OpeningDatePolicy
import com.yehor.finora.domain.money.CurrencyRegistry
import com.yehor.finora.domain.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class AccountFormLogicTest {

    private val usd = CurrencyRegistry.get("USD")
    private val jpy = CurrencyRegistry.get("JPY")
    private val policy = OpeningDatePolicy(ZoneOffset.UTC)
    private val day = 24L * 60 * 60 * 1000
    private val now = 100 * day + 5_000
    private val today = 100 * day

    private fun state(
        name: String = "Main",
        amount: String = "",
        isEdit: Boolean = false,
        archived: Boolean = false,
        original: Long? = null,
        earliest: Long? = null,
        date: Long = today,
        currency: com.yehor.finora.domain.money.Currency = usd,
    ) = AccountFormState(
        isEdit = isEdit, loaded = true, name = name, currency = currency, amountText = amount,
        archived = archived, originalOpeningMinor = original,
        earliestOtherEntryAt = earliest, dateMillis = date,
    )

    @Test
    fun emptyOrZeroAmountMeansNoOpeningBalance() {
        assertNull(AccountFormLogic.openingMinor(state(amount = "")))
        assertNull(AccountFormLogic.openingMinor(state(amount = "0")))
        assertNull(AccountFormLogic.openingMinor(state(amount = "0.00")))
    }

    @Test
    fun signComesFromTheLeadingMinusAndCommaIsAccepted() {
        assertEquals(12_550L, AccountFormLogic.openingMinor(state(amount = "125,50")))
        assertEquals(-12_550L, AccountFormLogic.openingMinor(state(amount = "-125.5")))
        assertEquals(-300L, AccountFormLogic.openingMinor(state(amount = "-300", currency = jpy)))
    }

    @Test
    fun saveIsGatedByValidation() {
        assertTrue(AccountFormLogic.canSave(state(), policy, now))
        assertFalse(AccountFormLogic.canSave(state(name = "  "), policy, now))
        assertFalse(AccountFormLogic.canSave(state(name = "x".repeat(51)), policy, now))
        assertFalse(AccountFormLogic.canSave(state(amount = "abc"), policy, now))
        assertFalse(AccountFormLogic.canSave(state(amount = "1.234"), policy, now))
        assertFalse(AccountFormLogic.canSave(state(amount = "1.5", currency = jpy), policy, now))
        assertFalse(AccountFormLogic.canSave(state().copy(loaded = false), policy, now))
        assertFalse(AccountFormLogic.canSave(state().copy(saving = true), policy, now))
        assertTrue(AccountFormLogic.errors(state(amount = "1.234")).contains(AccountDraftError.AMOUNT_TOO_PRECISE))
    }

    @Test
    fun dateLaterThanEarliestEntryBlocksSaveOnlyWithAnAmount() {
        val entryDay = 90 * day + 3_600_000
        val tooLate = state(amount = "10", earliest = entryDay, date = today)
        assertTrue(AccountFormLogic.dateTooLate(tooLate, policy, now))
        assertFalse(AccountFormLogic.canSave(tooLate, policy, now))

        val onTheDay = state(amount = "10", earliest = entryDay, date = 90 * day)
        assertFalse(AccountFormLogic.dateTooLate(onTheDay, policy, now))

        // Without an amount the date is irrelevant (the opening balance is being removed or never added).
        assertFalse(AccountFormLogic.dateTooLate(tooLate.copy(amountText = ""), policy, now))
    }

    @Test
    fun archivedAccountWithoutOpeningBalanceCannotAddOne() {
        val s = state(isEdit = true, archived = true, original = null, amount = "50")
        assertFalse(AccountFormLogic.openingSectionEnabled(s))
        // The disabled section's amount is ignored by validation, so a stale value cannot block saving.
        assertTrue(AccountFormLogic.errors(s.copy(amountText = "garbage")).isEmpty())
    }

    @Test
    fun archivedAccountWithOpeningBalanceCanChangeIt() {
        assertTrue(AccountFormLogic.openingSectionEnabled(state(isEdit = true, archived = true, original = 500)))
        assertTrue(AccountFormLogic.openingSectionEnabled(state(isEdit = false, archived = false)))
        assertTrue(AccountFormLogic.openingSectionEnabled(state(isEdit = true, archived = false, original = null)))
    }

    @Test
    fun balanceDeltaOnlyForEditedAccountsWithOtherEntries() {
        val entry = 50 * day
        // 100.00 -> 150.00 gives +50.00
        assertEquals(
            Money(5_000, usd),
            AccountFormLogic.balanceDelta(state(isEdit = true, original = 10_000, amount = "150", earliest = entry)),
        )
        // Removing the opening balance gives -100.00
        assertEquals(
            Money(-10_000, usd),
            AccountFormLogic.balanceDelta(state(isEdit = true, original = 10_000, amount = "", earliest = entry)),
        )
        // Unchanged, no other entries, create mode, invalid amount: no warning
        assertNull(AccountFormLogic.balanceDelta(state(isEdit = true, original = 10_000, amount = "100", earliest = entry)))
        assertNull(AccountFormLogic.balanceDelta(state(isEdit = true, original = 10_000, amount = "150", earliest = null)))
        assertNull(AccountFormLogic.balanceDelta(state(isEdit = false, amount = "150", earliest = entry)))
        assertNull(AccountFormLogic.balanceDelta(state(isEdit = true, original = 10_000, amount = "x", earliest = entry)))
    }

    @Test
    fun amountTextPrefillIsSignedAndUsesCurrencyScale() {
        assertEquals("", AccountFormLogic.amountTextOf(null, usd))
        assertEquals("-12.34", AccountFormLogic.amountTextOf(-1_234, usd))
        assertEquals("500", AccountFormLogic.amountTextOf(500, jpy))
    }

    @Test
    fun blockerReportsTheFirstProblem() {
        fun b(s: AccountFormState) = AccountFormLogic.blocker(s, policy, now)
        assertNull(b(state()))
        assertEquals(AccountFormBlocker.LOADING, b(state().copy(loaded = false)))
        assertEquals(AccountFormBlocker.SAVING, b(state().copy(saving = true)))
        assertEquals(AccountFormBlocker.NAME_EMPTY, b(state(name = " ")))
        assertEquals(AccountFormBlocker.NAME_TOO_LONG, b(state(name = "x".repeat(51))))
        assertEquals(AccountFormBlocker.CURRENCY_MISSING, b(state().copy(currency = null)))
        assertEquals(AccountFormBlocker.AMOUNT_INVALID, b(state(amount = "abc")))
        assertEquals(AccountFormBlocker.AMOUNT_INVALID, b(state(amount = "1.234")))
        assertEquals(AccountFormBlocker.DESCRIPTION_TOO_LONG, b(state().copy(description = "d".repeat(501))))
        val entryDay = 90 * day + 3_600_000
        assertEquals(AccountFormBlocker.DATE_TOO_LATE, b(state(amount = "10", earliest = entryDay, date = today)))
        // Name problems win over later ones.
        assertEquals(AccountFormBlocker.NAME_EMPTY, b(state(name = "", amount = "abc").copy(currency = null)))
    }

    @Test
    fun canSaveIsExactlyNoBlocker() {
        val entryDay = 90 * day + 3_600_000
        listOf(
            state(), state(name = ""), state(amount = "x"), state().copy(saving = true),
            state(amount = "10", earliest = entryDay, date = today), state().copy(currency = null),
        ).forEach {
            assertEquals(AccountFormLogic.blocker(it, policy, now) == null, AccountFormLogic.canSave(it, policy, now))
        }
    }

    @Test
    fun toggleSignFlipsTheLeadingMinus() {
        assertEquals("-", AccountFormLogic.toggleSign(""))
        assertEquals("-", AccountFormLogic.toggleSign("  "))
        assertEquals("-12", AccountFormLogic.toggleSign("12"))
        assertEquals("12", AccountFormLogic.toggleSign("-12"))
        assertEquals("0.5", AccountFormLogic.toggleSign("-0.5"))
        assertEquals("-12", AccountFormLogic.toggleSign(" 12 "))
        assertEquals("12", AccountFormLogic.toggleSign(" - 12 "))
        assertEquals("", AccountFormLogic.toggleSign("-"))
    }

    @Test
    fun archiveConfirmationOnlyForNonZeroBalance() {
        assertFalse(archiveNeedsConfirmation(Balance.Value(Money(0, usd))))
        assertTrue(archiveNeedsConfirmation(Balance.Value(Money(-1, usd))))
        assertTrue(archiveNeedsConfirmation(Balance.Overflow))
    }
}
