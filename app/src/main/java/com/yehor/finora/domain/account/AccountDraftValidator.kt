package com.yehor.finora.domain.account

import com.yehor.finora.domain.money.AmountParseResult
import com.yehor.finora.domain.money.AmountParser
import com.yehor.finora.domain.money.Currency

enum class AccountDraftError {
    NAME_EMPTY,
    NAME_TOO_LONG,
    DESCRIPTION_TOO_LONG,
    AMOUNT_INVALID,
    AMOUNT_TOO_PRECISE,
    AMOUNT_OUT_OF_RANGE,
}

/** Result of parsing the unsigned amount text of the opening balance field. */
sealed interface AmountParse {
    /** Blank text: no opening balance. */
    data object Empty : AmountParse
    data class Value(val minor: Long) : AmountParse
    data class Invalid(val error: AccountDraftError) : AmountParse
}

/** Pure validation of the account form. ViewModels map [AccountDraftError] to messages. */
object AccountDraftValidator {
    const val NAME_MAX = 50
    const val DESCRIPTION_MAX = 500

    /**
     * [amountText] is the signed amount: digits, one decimal separator (`.` or `,`) and an optional
     * leading `-` for a negative balance.
     */
    fun validate(
        name: String,
        description: String?,
        amountText: String,
        currency: Currency,
    ): Set<AccountDraftError> {
        val errors = mutableSetOf<AccountDraftError>()
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) errors += AccountDraftError.NAME_EMPTY
        if (trimmedName.length > NAME_MAX) errors += AccountDraftError.NAME_TOO_LONG
        if ((description?.trim()?.length ?: 0) > DESCRIPTION_MAX) {
            errors += AccountDraftError.DESCRIPTION_TOO_LONG
        }
        val amount = parseAmount(amountText, currency)
        if (amount is AmountParse.Invalid) errors += amount.error
        return errors
    }

    /** Parses [text] into minor units of [currency] without rounding. */
    fun parseAmount(text: String, currency: Currency): AmountParse =
        when (val result = AmountParser.parse(text, currency)) {
            is AmountParseResult.Empty -> AmountParse.Empty
            is AmountParseResult.Value -> AmountParse.Value(result.minor)
            is AmountParseResult.InvalidFormat -> AmountParse.Invalid(AccountDraftError.AMOUNT_INVALID)
            is AmountParseResult.TooPrecise -> AmountParse.Invalid(AccountDraftError.AMOUNT_TOO_PRECISE)
            is AmountParseResult.OutOfRange -> AmountParse.Invalid(AccountDraftError.AMOUNT_OUT_OF_RANGE)
        }
}
