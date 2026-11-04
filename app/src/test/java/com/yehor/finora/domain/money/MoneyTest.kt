package com.yehor.finora.domain.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MoneyTest {
    private val usd = CurrencyRegistry.get("USD")
    private val jpy = CurrencyRegistry.get("JPY")
    private val kwd = CurrencyRegistry.get("KWD")
    private val clf = CurrencyRegistry.get("CLF")
    private val eur = CurrencyRegistry.get("EUR")

    @Test
    fun parseRespectsScale() {
        assertEquals(1250L, Money.parse("12.50", usd).amountMinor)
        assertEquals(1200L, Money.parse("12", usd).amountMinor)
        assertEquals(-5L, Money.parse("-0.05", usd).amountMinor)
        assertEquals(1000L, Money.parse("1000", jpy).amountMinor)
        assertEquals(5L, Money.parse("0.005", kwd).amountMinor)
        assertEquals(12345L, Money.parse("1.2345", clf).amountMinor)
    }

    @Test
    fun parseAcceptsTrailingZerosBeyondScale() {
        assertEquals(1250L, Money.parse("12.500", usd).amountMinor)
        assertEquals(10L, Money.parse("10.0", jpy).amountMinor)
    }

    @Test
    fun parseRejectsTooManyFractionDigitsInsteadOfRounding() {
        assertThrows(InvalidMoneyException::class.java) { Money.parse("12.505", usd) }
        assertThrows(InvalidMoneyException::class.java) { Money.parse("0.5", jpy) }
    }

    @Test
    fun parseRejectsGarbage() {
        assertThrows(InvalidMoneyException::class.java) { Money.parse("abc", usd) }
        assertThrows(InvalidMoneyException::class.java) { Money.parse("", usd) }
    }

    @Test
    fun parseOutOfRangeIsOverflow() {
        assertThrows(MoneyOverflowException::class.java) { Money.parse("92233720368547758.08", usd) }
        assertEquals(Long.MAX_VALUE, Money.parse("92233720368547758.07", usd).amountMinor)
    }

    @Test
    fun formatPerScale() {
        assertEquals("12.50", Money(1250, usd).toPlainString())
        assertEquals("-0.05", Money(-5, usd).toPlainString())
        assertEquals("1000", Money(1000, jpy).toPlainString())
        assertEquals("0.005", Money(5, kwd).toPlainString())
        assertEquals("1.2345", Money(12345, clf).toPlainString())
        assertEquals("12.50 $", Money(1250, usd).format())
    }

    @Test
    fun parseThenFormatRoundTrips() {
        listOf("0.00", "12.50", "-99999.99").forEach {
            assertEquals(it, Money.parse(it, usd).toPlainString())
        }
    }

    @Test
    fun arithmetic() {
        assertEquals(Money(300, usd), Money(100, usd) + Money(200, usd))
        assertEquals(Money(-100, usd), Money(100, usd) - Money(200, usd))
        assertEquals(Money(-100, usd), -Money(100, usd))
        assertEquals(Money(300, usd), Money(100, usd) * 3)
    }

    @Test
    fun overflowThrowsInsteadOfWrapping() {
        assertThrows(MoneyOverflowException::class.java) { Money(Long.MAX_VALUE, usd) + Money(1, usd) }
        assertThrows(MoneyOverflowException::class.java) { Money(Long.MIN_VALUE, usd) - Money(1, usd) }
        assertThrows(MoneyOverflowException::class.java) { -Money(Long.MIN_VALUE, usd) }
        assertThrows(MoneyOverflowException::class.java) { Money(Long.MAX_VALUE, usd) * 2 }
    }

    @Test
    fun crossCurrencyArithmeticIsRejected() {
        assertThrows(CurrencyMismatchException::class.java) { Money(1, usd) + Money(1, eur) }
        assertThrows(CurrencyMismatchException::class.java) { Money(1, usd) - Money(1, eur) }
    }

    /** Static check: the money package must not use Float or Double anywhere. */
    @Test
    fun moneyPackageContainsNoFloatOrDouble() {
        val dir = File("src/main/java/com/yehor/finora/domain/money")
        val files = dir.listFiles { f -> f.extension == "kt" }.orEmpty()
        assertTrue("money sources not found at ${dir.absolutePath}", files.isNotEmpty())
        val pattern = Regex("""\b(Float|Double)\b|\d[fF]\b|toDouble|toFloat""")
        files.forEach { file ->
            file.readLines().forEachIndexed { i, line ->
                val code = line.substringBefore("//").trim()
                if (code.startsWith("*") || code.startsWith("/*")) return@forEachIndexed
                assertTrue("${file.name}:${i + 1} uses floating point: $line", !pattern.containsMatchIn(code))
            }
        }
    }
}
