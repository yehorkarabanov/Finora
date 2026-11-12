package com.yehor.finora.ui.operations

import com.yehor.finora.ui.operations.AmountKeypad.BACKSPACE
import com.yehor.finora.ui.operations.AmountKeypad.DECIMAL
import org.junit.Assert.assertEquals
import org.junit.Test

class AmountKeypadTest {

    private fun type(keys: String, scale: Int = 2): String =
        keys.fold("") { acc, c -> AmountKeypad.applyKey(acc, c.toString(), scale) }

    @Test
    fun `digits append and a leading zero is replaced`() {
        assertEquals("123", type("123"))
        assertEquals("5", type("05"))
    }

    @Test
    fun `decimal starts with zero and is accepted once`() {
        assertEquals("0.", type("."))
        assertEquals("1.5", type("1..5"))
    }

    @Test
    fun `fraction digits are limited to the currency scale`() {
        assertEquals("1.25", type("1.257"))
        assertEquals("1.257", type("1.2579", scale = 3))
    }

    @Test
    fun `decimal is ignored for zero-scale currencies`() {
        assertEquals("15", type("1.5", scale = 0))
    }

    @Test
    fun `integer part is capped`() {
        assertEquals("1".repeat(AmountKeypad.MAX_INTEGER_DIGITS), type("1".repeat(AmountKeypad.MAX_INTEGER_DIGITS + 3)))
        // The fraction can still be typed after a capped integer part.
        assertEquals("1".repeat(AmountKeypad.MAX_INTEGER_DIGITS) + ".5", type("1".repeat(AmountKeypad.MAX_INTEGER_DIGITS) + ".5"))
    }

    @Test
    fun `backspace removes the last character`() {
        assertEquals("1.", AmountKeypad.applyKey("1.5", BACKSPACE, 2))
        assertEquals("", AmountKeypad.applyKey("", BACKSPACE, 2))
    }

    @Test
    fun `non-digit keys are ignored`() {
        assertEquals("1", AmountKeypad.applyKey("1", "x", 2))
        assertEquals("1", AmountKeypad.applyKey("1", DECIMAL, 0))
    }

    @Test
    fun `add keeps the fraction`() {
        assertEquals("10", AmountKeypad.add("", 10))
        assertEquals("35.5", AmountKeypad.add("10.5", 25))
        assertEquals("62", AmountKeypad.add("12.", 50))
        assertEquals("100", AmountKeypad.add("50", 50))
    }

    @Test
    fun `format groups integer digits and keeps typed fraction`() {
        assertEquals("0", AmountKeypad.format("", ',', '.'))
        assertEquals("1,234,567", AmountKeypad.format("1234567", ',', '.'))
        assertEquals("1 234,50", AmountKeypad.format("1234.50", ' ', ','))
        assertEquals("12.", AmountKeypad.format("12.", ',', '.'))
        assertEquals("0.5", AmountKeypad.format(".5", ',', '.'))
    }
}
