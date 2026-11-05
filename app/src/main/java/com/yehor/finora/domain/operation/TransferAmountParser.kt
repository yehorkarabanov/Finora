package com.yehor.finora.domain.operation

import com.yehor.finora.domain.money.AmountParseResult
import com.yehor.finora.domain.money.AmountParser
import com.yehor.finora.domain.money.Currency

enum class AmountIssue { EMPTY, INVALID, TOO_PRECISE, NOT_POSITIVE, OUT_OF_RANGE }

/** Parses one transfer amount field: a strictly positive amount with at most the currency's scale. */
object TransferAmountParser {

    sealed interface Result {
        data class Value(val minor: Long) : Result
        data class Invalid(val issue: AmountIssue) : Result
    }

    fun parse(text: String, currency: Currency): Result = when (val parsed = AmountParser.parse(text, currency)) {
        AmountParseResult.Empty -> Result.Invalid(AmountIssue.EMPTY)
        AmountParseResult.InvalidFormat -> Result.Invalid(AmountIssue.INVALID)
        AmountParseResult.TooPrecise -> Result.Invalid(AmountIssue.TOO_PRECISE)
        AmountParseResult.OutOfRange -> Result.Invalid(AmountIssue.OUT_OF_RANGE)
        is AmountParseResult.Value ->
            if (parsed.minor <= 0) Result.Invalid(AmountIssue.NOT_POSITIVE) else Result.Value(parsed.minor)
    }
}
