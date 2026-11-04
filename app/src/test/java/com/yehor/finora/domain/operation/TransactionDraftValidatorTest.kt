package com.yehor.finora.domain.operation

import com.yehor.finora.domain.money.Currency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionDraftValidatorTest {

    private val usd = Currency("USD", 2, "$", "US Dollar")
    private val now = 1_000_000L

    @Test
    fun validCreateDraftPasses() {
        val errors = TransactionDraftValidator.validate(
            amountText = "10.50",
            accountId = 1L,
            categoryId = 2L,
            occurredAt = now,
            description = "Lunch",
            currency = usd,
            now = now,
            isEdit = false,
            hadCategoryBefore = false,
        )
        assertTrue(errors.isEmpty())
    }

    @Test
    fun amountErrors() {
        assertEquals(
            setOf(TransactionDraftError.AMOUNT_EMPTY),
            TransactionDraftValidator.validate("", 1L, 2L, now, null, usd, now, false, false),
        )
        assertEquals(
            setOf(TransactionDraftError.AMOUNT_INVALID),
            TransactionDraftValidator.validate("abc", 1L, 2L, now, null, usd, now, false, false),
        )
        assertEquals(
            setOf(TransactionDraftError.AMOUNT_TOO_PRECISE),
            TransactionDraftValidator.validate("10.555", 1L, 2L, now, null, usd, now, false, false),
        )
        assertEquals(
            setOf(TransactionDraftError.AMOUNT_NOT_POSITIVE),
            TransactionDraftValidator.validate("0", 1L, 2L, now, null, usd, now, false, false),
        )
        assertEquals(
            setOf(TransactionDraftError.AMOUNT_NOT_POSITIVE),
            TransactionDraftValidator.validate("-5.00", 1L, 2L, now, null, usd, now, false, false),
        )
    }

    @Test
    fun accountAndCategoryRequirements() {
        val missingAcc = TransactionDraftValidator.validate("10", null, 2L, now, null, usd, now, false, false)
        assertTrue(TransactionDraftError.ACCOUNT_MISSING in missingAcc)

        val missingCatCreate = TransactionDraftValidator.validate("10", 1L, null, now, null, usd, now, false, false)
        assertTrue(TransactionDraftError.CATEGORY_MISSING in missingCatCreate)

        val editUncategorizedAllowed = TransactionDraftValidator.validate("10", 1L, null, now, null, usd, now, true, false)
        assertTrue(TransactionDraftError.CATEGORY_MISSING !in editUncategorizedAllowed)

        val editCategorizedRequiresCategory = TransactionDraftValidator.validate("10", 1L, null, now, null, usd, now, true, true)
        assertTrue(TransactionDraftError.CATEGORY_MISSING in editCategorizedRequiresCategory)
    }

    @Test
    fun descriptionLength() {
        val ok = TransactionDraftValidator.validate("10", 1L, 2L, now, "a".repeat(500), usd, now, false, false)
        assertTrue(TransactionDraftError.DESCRIPTION_TOO_LONG !in ok)

        val tooLong = TransactionDraftValidator.validate("10", 1L, 2L, now, "a".repeat(501), usd, now, false, false)
        assertTrue(TransactionDraftError.DESCRIPTION_TOO_LONG in tooLong)
    }

    @Test
    fun futureDateRefused() {
        val atNow = TransactionDraftValidator.validate("10", 1L, 2L, now, null, usd, now, false, false)
        assertTrue(TransactionDraftError.DATE_IN_FUTURE !in atNow)

        val past = TransactionDraftValidator.validate("10", 1L, 2L, now - 1, null, usd, now, false, false)
        assertTrue(TransactionDraftError.DATE_IN_FUTURE !in past)

        val future = TransactionDraftValidator.validate("10", 1L, 2L, now + 1, null, usd, now, false, false)
        assertTrue(TransactionDraftError.DATE_IN_FUTURE in future)
    }
}
