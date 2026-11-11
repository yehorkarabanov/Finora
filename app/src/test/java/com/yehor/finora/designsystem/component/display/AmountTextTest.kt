package com.yehor.finora.designsystem.component.display

import org.junit.Assert.assertEquals
import org.junit.Test

class AmountTextTest {
    @Test
    fun groupedWholeNumbersHaveNoFraction() {
        assertEquals("1,234" to "", splitAmount("1,234"))
        assertEquals("12 345" to "", splitAmount("12 345"))
        assertEquals("1.234.567" to "", splitAmount("1.234.567"))
        assertEquals("$64" to "", splitAmount("$64"))
    }

    @Test
    fun oneOrTwoTrailingDigitsAreTheFraction() {
        assertEquals("1 234" to ",56", splitAmount("1 234,56"))
        assertEquals("-48" to ".20", splitAmount("-48.20"))
        assertEquals("$1,234" to ".5", splitAmount("$1,234.5"))
    }

    @Test
    fun separatorNotAtTheEndIsNotAFraction() {
        assertEquals("12." to "", splitAmount("12."))
        assertEquals("48.20 $" to "", splitAmount("48.20 $"))
    }
}
