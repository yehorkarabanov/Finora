package com.yehor.finora.designsystem.component.display

import org.junit.Assert.assertEquals
import org.junit.Test

class AmountTextTest {
    @Test
    fun groupedWholeNumbersHaveNoFraction() {
        assertEquals(AmountParts("1,234", "", ""), splitAmount("1,234"))
        assertEquals(AmountParts("12 345", "", ""), splitAmount("12 345"))
        assertEquals(AmountParts("1.234.567", "", ""), splitAmount("1.234.567"))
        assertEquals(AmountParts("$64", "", ""), splitAmount("$64"))
        assertEquals(AmountParts("1,234 $", "", ""), splitAmount("1,234 $"))
    }

    @Test
    fun oneOrTwoTrailingDigitsAreTheFraction() {
        assertEquals(AmountParts("1 234", ",56", ""), splitAmount("1 234,56"))
        assertEquals(AmountParts("-48", ".20", ""), splitAmount("-48.20"))
        assertEquals(AmountParts("$1,234", ".5", ""), splitAmount("$1,234.5"))
        assertEquals(AmountParts("$48", ".20", ""), splitAmount("$48.20"))
    }

    @Test
    fun currencySuffixAfterTheFractionIsKeptSeparately() {
        assertEquals(AmountParts("48", ".20", " $"), splitAmount("48.20 $"))
        assertEquals(AmountParts("1 234", ",56", " ₴"), splitAmount("1 234,56 ₴"))
        assertEquals(AmountParts("-1 234", ",56", " ₴"), splitAmount("-1 234,56 ₴"))
        assertEquals(AmountParts("≈ 12", ".00", " USD"), splitAmount("≈ 12.00 USD"))
    }

    @Test
    fun separatorWithoutDigitsIsNotAFraction() {
        assertEquals(AmountParts("12.", "", ""), splitAmount("12."))
        assertEquals(AmountParts("", "", ""), splitAmount(""))
    }
}
