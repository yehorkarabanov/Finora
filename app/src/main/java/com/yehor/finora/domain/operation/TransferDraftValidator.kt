package com.yehor.finora.domain.operation

import com.yehor.finora.domain.money.Currency

object TransferDraftValidator {
    const val DESCRIPTION_MAX = 500

    /**
     * Validates the transfer form. A currency is null while its account is not chosen yet; the
     * amount is then only checked for being empty, the account error is reported separately.
     */
    fun validate(
        sentText: String,
        receivedText: String,
        sourceAccountId: Long?,
        destAccountId: Long?,
        sourceCurrency: Currency?,
        destCurrency: Currency?,
        categoryId: Long?,
        occurredAt: Long,
        description: String?,
        now: Long = System.currentTimeMillis(),
        isEdit: Boolean = false,
        hadCategoryBefore: Boolean = true,
    ): Set<TransferDraftError> {
        val errors = mutableSetOf<TransferDraftError>()

        if (sourceAccountId == null) errors += TransferDraftError.SOURCE_ACCOUNT_MISSING
        if (destAccountId == null) errors += TransferDraftError.DEST_ACCOUNT_MISSING
        if (sourceAccountId != null && sourceAccountId == destAccountId) errors += TransferDraftError.SAME_ACCOUNTS

        amountIssue(sentText, sourceCurrency)?.let { issue ->
            errors += when (issue) {
                AmountIssue.EMPTY -> TransferDraftError.SENT_AMOUNT_EMPTY
                AmountIssue.INVALID -> TransferDraftError.SENT_AMOUNT_INVALID
                AmountIssue.TOO_PRECISE -> TransferDraftError.SENT_AMOUNT_TOO_PRECISE
                AmountIssue.NOT_POSITIVE -> TransferDraftError.SENT_AMOUNT_NOT_POSITIVE
                AmountIssue.OUT_OF_RANGE -> TransferDraftError.SENT_AMOUNT_OUT_OF_RANGE
            }
        }
        amountIssue(receivedText, destCurrency)?.let { issue ->
            errors += when (issue) {
                AmountIssue.EMPTY -> TransferDraftError.RECEIVED_AMOUNT_EMPTY
                AmountIssue.INVALID -> TransferDraftError.RECEIVED_AMOUNT_INVALID
                AmountIssue.TOO_PRECISE -> TransferDraftError.RECEIVED_AMOUNT_TOO_PRECISE
                AmountIssue.NOT_POSITIVE -> TransferDraftError.RECEIVED_AMOUNT_NOT_POSITIVE
                AmountIssue.OUT_OF_RANGE -> TransferDraftError.RECEIVED_AMOUNT_OUT_OF_RANGE
            }
        }

        if (categoryId == null && (!isEdit || hadCategoryBefore)) {
            errors += TransferDraftError.CATEGORY_MISSING
        }
        if ((description?.trim()?.length ?: 0) > DESCRIPTION_MAX) {
            errors += TransferDraftError.DESCRIPTION_TOO_LONG
        }
        if (occurredAt > now) errors += TransferDraftError.DATE_IN_FUTURE
        return errors
    }

    private fun amountIssue(text: String, currency: Currency?): AmountIssue? {
        if (currency == null) return if (text.isBlank()) AmountIssue.EMPTY else null
        return (TransferAmountParser.parse(text, currency) as? TransferAmountParser.Result.Invalid)?.issue
    }
}
