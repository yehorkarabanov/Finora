package com.yehor.finora.domain.operation

import com.yehor.finora.domain.money.AmountParseResult
import com.yehor.finora.domain.money.AmountParser
import com.yehor.finora.domain.money.Currency

enum class TransactionDraftError {
    AMOUNT_EMPTY,
    AMOUNT_INVALID,
    AMOUNT_TOO_PRECISE,
    AMOUNT_OUT_OF_RANGE,
    AMOUNT_NOT_POSITIVE,
    ACCOUNT_MISSING,
    CATEGORY_MISSING,
    DESCRIPTION_TOO_LONG,
    DATE_IN_FUTURE,
}

object TransactionDraftValidator {
    const val DESCRIPTION_MAX = 500

    fun validate(
        amountText: String,
        accountId: Long?,
        categoryId: Long?,
        occurredAt: Long,
        description: String?,
        currency: Currency,
        now: Long = System.currentTimeMillis(),
        isEdit: Boolean = false,
        hadCategoryBefore: Boolean = true,
    ): Set<TransactionDraftError> {
        val errors = mutableSetOf<TransactionDraftError>()

        when (val parse = AmountParser.parse(amountText, currency)) {
            is AmountParseResult.Empty -> errors += TransactionDraftError.AMOUNT_EMPTY
            is AmountParseResult.InvalidFormat -> errors += TransactionDraftError.AMOUNT_INVALID
            is AmountParseResult.TooPrecise -> errors += TransactionDraftError.AMOUNT_TOO_PRECISE
            is AmountParseResult.OutOfRange -> errors += TransactionDraftError.AMOUNT_OUT_OF_RANGE
            is AmountParseResult.Value -> {
                if (parse.minor <= 0) {
                    errors += TransactionDraftError.AMOUNT_NOT_POSITIVE
                }
            }
        }

        if (accountId == null) {
            errors += TransactionDraftError.ACCOUNT_MISSING
        }

        if (categoryId == null) {
            if (!isEdit || hadCategoryBefore) {
                errors += TransactionDraftError.CATEGORY_MISSING
            }
        }

        if ((description?.trim()?.length ?: 0) > DESCRIPTION_MAX) {
            errors += TransactionDraftError.DESCRIPTION_TOO_LONG
        }

        if (occurredAt > now) {
            errors += TransactionDraftError.DATE_IN_FUTURE
        }

        return errors
    }
}
