package com.yehor.finora.domain.account

import com.yehor.finora.domain.money.Currency
import com.yehor.finora.domain.money.Money
import com.yehor.finora.domain.money.MoneyOverflowException
import java.math.BigDecimal

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

    private val amountPattern = Regex("""^-?(\d+([.,]\d*)?|[.,]\d+)$""")

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
    fun parseAmount(text: String, currency: Currency): AmountParse {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return AmountParse.Empty
        if (!amountPattern.matches(trimmed)) return AmountParse.Invalid(AccountDraftError.AMOUNT_INVALID)
        val normalized = trimmed.replace(',', '.')
        val decimal = BigDecimal(if (normalized.endsWith(".")) normalized.dropLast(1) else normalized)
        if (decimal.stripTrailingZeros().scale() > currency.scale) {
            return AmountParse.Invalid(AccountDraftError.AMOUNT_TOO_PRECISE)
        }
        return try {
            val minor = Money.parse(decimal.toPlainString(), currency).amountMinor
            // Long.MIN_VALUE cannot be negated, so keep the range symmetric.
            if (minor == Long.MIN_VALUE) AmountParse.Invalid(AccountDraftError.AMOUNT_OUT_OF_RANGE)
            else AmountParse.Value(minor)
        } catch (_: MoneyOverflowException) {
            AmountParse.Invalid(AccountDraftError.AMOUNT_OUT_OF_RANGE)
        }
    }
}
