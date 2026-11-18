package com.yehor.finora.domain.money

import java.math.BigDecimal

sealed interface AmountParseResult {
    data object Empty : AmountParseResult
    data class Value(val minor: Long) : AmountParseResult
    data object InvalidFormat : AmountParseResult
    data object TooPrecise : AmountParseResult
    data object OutOfRange : AmountParseResult
}

object AmountParser {
    private val amountPattern = Regex("""^-?(\d+([.,]\d*)?|[.,]\d+)$""")

    /** Parses [text] into minor units of [currency] without rounding. */
    fun parse(text: String, currency: Currency): AmountParseResult {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return AmountParseResult.Empty
        if (!amountPattern.matches(trimmed)) return AmountParseResult.InvalidFormat
        val normalized = trimmed.replace(',', '.')
        val decimalStr = if (normalized.endsWith(".")) normalized.dropLast(1) else normalized
        val decimal = try {
            BigDecimal(decimalStr)
        } catch (_: Exception) {
            return AmountParseResult.InvalidFormat
        }
        if (decimal.stripTrailingZeros().scale() > currency.scale) {
            return AmountParseResult.TooPrecise
        }
        return try {
            val minor = Money.parse(decimal.toPlainString(), currency).amountMinor
            // Long.MIN_VALUE cannot be negated, so keep the range symmetric.
            if (minor == Long.MIN_VALUE) AmountParseResult.OutOfRange
            else AmountParseResult.Value(minor)
        } catch (_: MoneyOverflowException) {
            AmountParseResult.OutOfRange
        }
    }
}
