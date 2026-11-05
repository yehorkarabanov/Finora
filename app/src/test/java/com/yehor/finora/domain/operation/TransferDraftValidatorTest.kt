package com.yehor.finora.domain.operation

import com.yehor.finora.domain.money.Currency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferDraftValidatorTest {

    private val pln = Currency("PLN", 2, "zł", "Zloty")
    private val jpy = Currency("JPY", 0, "¥", "Yen")
    private val now = 1_000_000L

    private fun validate(
        sent: String = "100",
        received: String = "100",
        source: Long? = 1L,
        dest: Long? = 2L,
        sourceCurrency: Currency? = pln,
        destCurrency: Currency? = pln,
        category: Long? = 5L,
        occurredAt: Long = now,
        description: String? = null,
        isEdit: Boolean = false,
        hadCategoryBefore: Boolean = true,
    ) = TransferDraftValidator.validate(
        sentText = sent,
        receivedText = received,
        sourceAccountId = source,
        destAccountId = dest,
        sourceCurrency = sourceCurrency,
        destCurrency = destCurrency,
        categoryId = category,
        occurredAt = occurredAt,
        description = description,
        now = now,
        isEdit = isEdit,
        hadCategoryBefore = hadCategoryBefore,
    )

    @Test
    fun validDraftPasses() {
        assertTrue(validate().isEmpty())
    }

    @Test
    fun differentAmountsInSameCurrencyAreValid() {
        assertTrue(validate(sent = "100", received = "95").isEmpty())
    }

    @Test
    fun sameAccountsRejected() {
        assertEquals(setOf(TransferDraftError.SAME_ACCOUNTS), validate(source = 1L, dest = 1L))
    }

    @Test
    fun missingAccounts() {
        assertEquals(setOf(TransferDraftError.SOURCE_ACCOUNT_MISSING), validate(source = null, sourceCurrency = null))
        assertEquals(setOf(TransferDraftError.DEST_ACCOUNT_MISSING), validate(dest = null, destCurrency = null))
    }

    @Test
    fun sentAmountErrors() {
        assertEquals(setOf(TransferDraftError.SENT_AMOUNT_EMPTY), validate(sent = ""))
        assertEquals(setOf(TransferDraftError.SENT_AMOUNT_INVALID), validate(sent = "abc"))
        assertEquals(setOf(TransferDraftError.SENT_AMOUNT_TOO_PRECISE), validate(sent = "1.005"))
        assertEquals(setOf(TransferDraftError.SENT_AMOUNT_NOT_POSITIVE), validate(sent = "0"))
        assertEquals(setOf(TransferDraftError.SENT_AMOUNT_NOT_POSITIVE), validate(sent = "-5"))
        assertEquals(setOf(TransferDraftError.SENT_AMOUNT_OUT_OF_RANGE), validate(sent = "999999999999999999999"))
    }

    @Test
    fun receivedAmountErrors() {
        assertEquals(setOf(TransferDraftError.RECEIVED_AMOUNT_EMPTY), validate(received = ""))
        assertEquals(setOf(TransferDraftError.RECEIVED_AMOUNT_INVALID), validate(received = "abc"))
        assertEquals(setOf(TransferDraftError.RECEIVED_AMOUNT_TOO_PRECISE), validate(received = "1.005"))
        assertEquals(setOf(TransferDraftError.RECEIVED_AMOUNT_NOT_POSITIVE), validate(received = "0"))
        assertEquals(setOf(TransferDraftError.RECEIVED_AMOUNT_OUT_OF_RANGE), validate(received = "999999999999999999999"))
    }

    @Test
    fun scaleIsCheckedPerSideCurrency() {
        // 12.5 is fine for PLN (scale 2) but too precise for JPY (scale 0).
        assertEquals(
            setOf(TransferDraftError.RECEIVED_AMOUNT_TOO_PRECISE),
            validate(sent = "12.5", received = "12.5", destCurrency = jpy),
        )
        assertEquals(
            setOf(TransferDraftError.SENT_AMOUNT_TOO_PRECISE),
            validate(sent = "12.5", received = "12", sourceCurrency = jpy),
        )
    }

    @Test
    fun categoryRequiredOnCreate() {
        assertEquals(setOf(TransferDraftError.CATEGORY_MISSING), validate(category = null))
    }

    @Test
    fun uncategorizedAllowedOnlyOnEditOfUncategorizedTransfer() {
        assertTrue(validate(category = null, isEdit = true, hadCategoryBefore = false).isEmpty())
        assertEquals(
            setOf(TransferDraftError.CATEGORY_MISSING),
            validate(category = null, isEdit = true, hadCategoryBefore = true),
        )
    }

    @Test
    fun descriptionLength() {
        assertTrue(validate(description = "a".repeat(500)).isEmpty())
        assertEquals(setOf(TransferDraftError.DESCRIPTION_TOO_LONG), validate(description = "a".repeat(501)))
    }

    @Test
    fun futureDateRefused() {
        assertTrue(validate(occurredAt = now).isEmpty())
        assertTrue(validate(occurredAt = now - 1).isEmpty())
        assertEquals(setOf(TransferDraftError.DATE_IN_FUTURE), validate(occurredAt = now + 1))
    }
}
