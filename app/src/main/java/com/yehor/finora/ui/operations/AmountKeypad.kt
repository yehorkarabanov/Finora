package com.yehor.finora.ui.operations

import java.math.BigDecimal

/**
 * Pure editing rules for the on-screen amount keypad. The amount is kept as plain text with
 * '.' as the decimal separator; [AmountParser][com.yehor.finora.domain.money.AmountParser]
 * stays the single source of validation.
 */
object AmountKeypad {
    const val BACKSPACE = "⌫"
    const val DECIMAL = "."

    /** Integer digits accepted from the keypad; larger amounts are out of range anyway. */
    const val MAX_INTEGER_DIGITS = 12

    /**
     * Applies one key press. Keys that would make the text invalid for a currency with [scale]
     * fraction digits are ignored instead of producing an error state.
     */
    fun applyKey(current: String, key: String, scale: Int): String {
        val dot = current.indexOf('.')
        return when (key) {
            BACKSPACE -> current.dropLast(1)
            DECIMAL -> when {
                scale <= 0 || dot >= 0 -> current
                current.isEmpty() -> "0."
                else -> "$current."
            }
            else -> {
                if (key.length != 1 || !key[0].isDigit()) return current
                when {
                    dot >= 0 && current.length - dot - 1 >= scale -> current
                    current == "0" -> key
                    dot < 0 && current.length >= MAX_INTEGER_DIGITS -> current
                    else -> current + key
                }
            }
        }
    }

    /** Adds a whole [increment] to the current amount, keeping whatever fraction was typed. */
    fun add(current: String, increment: Int): String {
        val base = current.trimEnd('.').toBigDecimalOrNull() ?: BigDecimal.ZERO
        val sum = base + increment.toBigDecimal()
        return if (sum.signum() == 0) "0" else sum.stripTrailingZeros().toPlainString()
    }

    /**
     * Formats typed text for display with locale grouping, keeping a trailing separator and
     * trailing fraction zeros exactly as typed (so "12." and "12.50" render as typed).
     */
    fun format(text: String, groupingSeparator: Char, decimalSeparator: Char): String {
        val source = text.ifEmpty { "0" }
        val dot = source.indexOf('.')
        val integer = if (dot >= 0) source.substring(0, dot) else source
        val fraction = if (dot >= 0) source.substring(dot + 1) else null
        val grouped = integer.ifEmpty { "0" }
            .reversed()
            .chunked(3)
            .joinToString(groupingSeparator.toString())
            .reversed()
        return if (fraction == null) grouped else "$grouped$decimalSeparator$fraction"
    }
}
