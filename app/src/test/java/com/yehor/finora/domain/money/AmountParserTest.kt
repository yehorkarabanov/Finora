package com.yehor.finora.domain.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AmountParserTest {

    private val usd = Currency(code = "USD", scale = 2, symbol = "$", name = "US Dollar")
    private val jpy = Currency(code = "JPY", scale = 0, symbol = "¥", name = "Japanese Yen")
    private val bhd = Currency(code = "BHD", scale = 3, symbol = "BD", name = "Bahraini Dinar")

    @Test
    fun blankInputGivesEmpty() {
        assertEquals(AmountParseResult.Empty, AmountParser.parse("", usd))
        assertEquals(AmountParseResult.Empty, AmountParser.parse("   ", usd))
    }

    @Test
    fun validDecimalWithDotAndComma() {
        assertEquals(AmountParseResult.Value(1250L), AmountParser.parse("12.50", usd))
        assertEquals(AmountParseResult.Value(1250L), AmountParser.parse("12,50", usd))
        assertEquals(AmountParseResult.Value(1200L), AmountParser.parse("12", usd))
        assertEquals(AmountParseResult.Value(50L), AmountParser.parse(".50", usd))
        assertEquals(AmountParseResult.Value(50L), AmountParser.parse(",50", usd))
    }

    @Test
    fun invalidPattern() {
        assertTrue(AmountParser.parse("abc", usd) is AmountParseResult.InvalidFormat)
        assertTrue(AmountParser.parse("12.34.56", usd) is AmountParseResult.InvalidFormat)
        assertTrue(AmountParser.parse("12..34", usd) is AmountParseResult.InvalidFormat)
    }

    @Test
    fun scalePrecisionChecks() {
        assertEquals(AmountParseResult.Value(100L), AmountParser.parse("100", jpy))
        assertTrue(AmountParser.parse("100.5", jpy) is AmountParseResult.TooPrecise)
        assertEquals(AmountParseResult.Value(12345L), AmountParser.parse("12.345", bhd))
        assertTrue(AmountParser.parse("12.3456", bhd) is AmountParseResult.TooPrecise)
    }

    @Test
    fun overflowAndMinRange() {
        assertTrue(AmountParser.parse("9999999999999999999999999", usd) is AmountParseResult.OutOfRange)
    }
}
