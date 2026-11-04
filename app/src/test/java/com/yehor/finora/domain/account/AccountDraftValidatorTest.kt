package com.yehor.finora.domain.account

import com.yehor.finora.domain.money.CurrencyRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountDraftValidatorTest {
    private val usd = CurrencyRegistry.get("USD")
    private val jpy = CurrencyRegistry.get("JPY")
    private val kwd = CurrencyRegistry.get("KWD")

    private fun validate(
        name: String = "Main",
        description: String? = null,
        amount: String = "",
        currency: com.yehor.finora.domain.money.Currency = usd,
    ) = AccountDraftValidator.validate(name, description, amount, currency)

    @Test
    fun validDraftHasNoErrors() {
        assertTrue(validate().isEmpty())
        assertTrue(validate(amount = "12.50").isEmpty())
    }

    @Test
    fun emptyAndWhitespaceNamesAreRejected() {
        assertEquals(setOf(AccountDraftError.NAME_EMPTY), validate(name = ""))
        assertEquals(setOf(AccountDraftError.NAME_EMPTY), validate(name = "   "))
    }

    @Test
    fun nameLengthIsMeasuredAfterTrim() {
        assertTrue(validate(name = "a".repeat(50)).isEmpty())
        assertTrue(validate(name = "  " + "a".repeat(50) + "  ").isEmpty())
        assertEquals(setOf(AccountDraftError.NAME_TOO_LONG), validate(name = "a".repeat(51)))
    }

    @Test
    fun descriptionLimitIs500() {
        assertTrue(validate(description = "d".repeat(500)).isEmpty())
        assertEquals(
            setOf(AccountDraftError.DESCRIPTION_TOO_LONG),
            validate(description = "d".repeat(501)),
        )
        assertTrue(validate(description = null).isEmpty())
    }

    @Test
    fun amountAcceptsDotAndComma() {
        assertEquals(AmountParse.Value(1250), AccountDraftValidator.parseAmount("12.50", usd))
        assertEquals(AmountParse.Value(1250), AccountDraftValidator.parseAmount("12,50", usd))
        assertEquals(AmountParse.Value(50), AccountDraftValidator.parseAmount(".5", usd))
        assertEquals(AmountParse.Value(1200), AccountDraftValidator.parseAmount("12.", usd))
        assertEquals(AmountParse.Value(0), AccountDraftValidator.parseAmount("0", usd))
    }

    @Test
    fun leadingMinusMakesTheAmountNegative() {
        assertEquals(AmountParse.Value(-1250), AccountDraftValidator.parseAmount("-12.50", usd))
        assertEquals(AmountParse.Value(-50), AccountDraftValidator.parseAmount("-,5", usd))
    }

    @Test
    fun blankAmountMeansNoOpeningBalance() {
        assertEquals(AmountParse.Empty, AccountDraftValidator.parseAmount("", usd))
        assertEquals(AmountParse.Empty, AccountDraftValidator.parseAmount("  ", usd))
    }

    @Test
    fun malformedAmountIsInvalid() {
        listOf("abc", "1.2.3", "1,2,3", "--5", "+5", "1e5", ".", ",", "-", "1 000", "NaN").forEach {
            assertEquals(
                it,
                AmountParse.Invalid(AccountDraftError.AMOUNT_INVALID),
                AccountDraftValidator.parseAmount(it, usd),
            )
        }
        assertEquals(setOf(AccountDraftError.AMOUNT_INVALID), validate(amount = "abc"))
    }

    @Test
    fun tooManyFractionalDigitsAreRejectedForEachScale() {
        assertEquals(
            AmountParse.Invalid(AccountDraftError.AMOUNT_TOO_PRECISE),
            AccountDraftValidator.parseAmount("1.5", jpy),
        )
        assertEquals(
            AmountParse.Invalid(AccountDraftError.AMOUNT_TOO_PRECISE),
            AccountDraftValidator.parseAmount("1.005", usd),
        )
        assertEquals(
            AmountParse.Invalid(AccountDraftError.AMOUNT_TOO_PRECISE),
            AccountDraftValidator.parseAmount("1.0005", kwd),
        )
        assertEquals(AmountParse.Value(1005), AccountDraftValidator.parseAmount("1.005", kwd))
    }

    @Test
    fun trailingZerosBeyondScaleAreAccepted() {
        assertEquals(AmountParse.Value(10), AccountDraftValidator.parseAmount("10.0", jpy))
        assertEquals(AmountParse.Value(1250), AccountDraftValidator.parseAmount("12.5000", usd))
    }

    @Test
    fun overflowIsOutOfRange() {
        assertEquals(
            AmountParse.Invalid(AccountDraftError.AMOUNT_OUT_OF_RANGE),
            AccountDraftValidator.parseAmount("92233720368547758.08", usd),
        )
        assertEquals(
            AmountParse.Invalid(AccountDraftError.AMOUNT_OUT_OF_RANGE),
            AccountDraftValidator.parseAmount("9".repeat(30), jpy),
        )
        assertEquals(
            AmountParse.Value(Long.MAX_VALUE),
            AccountDraftValidator.parseAmount("92233720368547758.07", usd),
        )
    }

    @Test
    fun errorsAreCombined() {
        assertEquals(
            setOf(AccountDraftError.NAME_EMPTY, AccountDraftError.AMOUNT_INVALID),
            validate(name = "", amount = "x"),
        )
    }
}
