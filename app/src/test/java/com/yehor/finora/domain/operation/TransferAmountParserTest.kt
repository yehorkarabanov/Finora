package com.yehor.finora.domain.operation

import com.yehor.finora.domain.money.Currency
import org.junit.Assert.assertEquals
import org.junit.Test

class TransferAmountParserTest {

    private val usd = Currency("USD", 2, "$", "US Dollar")
    private val jpy = Currency("JPY", 0, "¥", "Yen")
    private val bhd = Currency("BHD", 3, "BHD", "Bahraini Dinar")

    @Test
    fun parsesPositiveAmountsPerScale() {
        assertEquals(TransferAmountParser.Result.Value(1050), TransferAmountParser.parse("10.50", usd))
        assertEquals(TransferAmountParser.Result.Value(1050), TransferAmountParser.parse("10,5", usd))
        assertEquals(TransferAmountParser.Result.Value(1000), TransferAmountParser.parse("1000", jpy))
        assertEquals(TransferAmountParser.Result.Value(1234), TransferAmountParser.parse("1.234", bhd))
    }

    @Test
    fun rejectsTooPrecise() {
        assertEquals(invalid(AmountIssue.TOO_PRECISE), TransferAmountParser.parse("1.5", jpy))
        assertEquals(invalid(AmountIssue.TOO_PRECISE), TransferAmountParser.parse("1.005", usd))
        assertEquals(invalid(AmountIssue.TOO_PRECISE), TransferAmountParser.parse("1.2345", bhd))
    }

    @Test
    fun rejectsEmptyInvalidAndNonPositive() {
        assertEquals(invalid(AmountIssue.EMPTY), TransferAmountParser.parse("  ", usd))
        assertEquals(invalid(AmountIssue.INVALID), TransferAmountParser.parse("x1", usd))
        assertEquals(invalid(AmountIssue.NOT_POSITIVE), TransferAmountParser.parse("0", usd))
        assertEquals(invalid(AmountIssue.NOT_POSITIVE), TransferAmountParser.parse("-1", usd))
    }

    @Test
    fun rejectsOutOfRange() {
        assertEquals(invalid(AmountIssue.OUT_OF_RANGE), TransferAmountParser.parse("99999999999999999999", usd))
    }

    private fun invalid(issue: AmountIssue) = TransferAmountParser.Result.Invalid(issue)
}
