package com.yehor.finora.domain.rate

import com.yehor.finora.domain.money.CurrencyRegistry
import com.yehor.finora.domain.money.Money
import com.yehor.finora.domain.money.MoneyOverflowException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class RateConverterTest {

    private val usd = CurrencyRegistry.get("USD")
    private val pln = CurrencyRegistry.get("PLN")
    private val uah = CurrencyRegistry.get("UAH")
    private val jpy = CurrencyRegistry.get("JPY")
    private val bhd = CurrencyRegistry.get("BHD")
    private val eur = CurrencyRegistry.get("EUR")
    private val now = 1_000_000_000_000L

    private fun table(vararg entries: Triple<kotlin.Pair<com.yehor.finora.domain.money.Currency, com.yehor.finora.domain.money.Currency>, String, Long>) =
        RateTable(
            entries.associate { (pair, rate, fetchedAt) ->
                CurrencyPair(pair.first, pair.second) to RateQuote(BigDecimal(rate), LocalDate.of(2026, 11, 6), fetchedAt)
            },
        )

    private fun rate(from: com.yehor.finora.domain.money.Currency, to: com.yehor.finora.domain.money.Currency, rate: String, fetchedAt: Long = now) =
        Triple(from to to, rate, fetchedAt)

    private fun convert(table: RateTable, money: Money, to: com.yehor.finora.domain.money.Currency) =
        (RateConverter(table, now).convert(money, to) as ConversionResult.Converted).estimate

    @Test
    fun directPair() {
        val e = convert(table(rate(usd, pln, "3.9126")), Money(100_000, usd), pln)
        assertEquals(391_260L, e.amountMinor)
        assertEquals("≈ 3912.60 zł".replace("zł", pln.symbol), e.format())
    }

    @Test
    fun directPairScaleZeroToTwo() {
        val e = convert(table(rate(jpy, pln, "0.024721")), Money(10_000, jpy), pln)
        assertEquals(24_721L, e.amountMinor)
    }

    @Test
    fun roundsHalfUpAwayFromZero() {
        val t = table(rate(uah, pln, "0.086029"))
        assertEquals(43_015L, convert(t, Money(500_000, uah), pln).amountMinor)
        assertEquals(-43_015L, convert(t, Money(-500_000, uah), pln).amountMinor)
        // 499_999 * 0.086029 = 43014.41... rounds down
        assertEquals(43_014L, convert(t, Money(499_999, uah), pln).amountMinor)
    }

    @Test
    fun scales() {
        assertEquals(350L, convert(table(rate(usd, jpy, "3.5")), Money(10_000, usd), jpy).amountMinor)
        assertEquals(35_000L, convert(table(rate(jpy, usd, "3.5")), Money(100, jpy), usd).amountMinor)
        assertEquals(23_757L, convert(table(rate(jpy, bhd, "0.0023757")), Money(10_000, jpy), bhd).amountMinor)
    }

    @Test
    fun roundsOnlyOnce() {
        // 1 * 0.5 = 0.5 -> 1 in one step; double rounding of a 0.45 intermediate would give 0.
        val t = table(rate(usd, pln, "0.4999"))
        assertEquals(50L, convert(t, Money(100, usd), pln).amountMinor)
    }

    @Test
    fun sameCurrencyNeedsNoTable() {
        val e = convert(RateTable.EMPTY, Money(12_345, usd), usd)
        assertEquals(12_345L, e.amountMinor)
        assertFalse(e.basis.usesConversion)
    }

    @Test
    fun inverseAndPivotAreNotUsed() {
        val inverseOnly = table(rate(pln, usd, "0.25"))
        assertEquals(ConversionResult.Unavailable(usd, pln), RateConverter(inverseOnly, now).convert(Money(100, usd), pln))
        val pivot = table(rate(usd, eur, "0.9"), rate(eur, pln, "4.3"))
        assertEquals(ConversionResult.Unavailable(usd, pln), RateConverter(pivot, now).convert(Money(100, usd), pln))
    }

    @Test
    fun conversionOverflowThrows() {
        val t = table(rate(usd, pln, "1000"))
        assertThrows(MoneyOverflowException::class.java) {
            RateConverter(t, now).convert(Money(Long.MAX_VALUE / 2, usd), pln)
        }
        val v = RateConverter(t, now).value(listOf(Money(Long.MAX_VALUE / 2, usd)), pln)
        assertTrue(v.overflowed)
        assertNull(v.total)
    }

    @Test
    fun sumOverflowIsReported() {
        val t = table(rate(usd, pln, "1"))
        val v = RateConverter(t, now).value(listOf(Money(Long.MAX_VALUE, usd), Money(1, usd)), pln)
        assertTrue(v.overflowed)
        assertNull(v.total)
    }

    @Test
    fun outdatedFlag() {
        val old = now - RateFreshness.OUTDATED_AFTER_MS - 1
        assertTrue(convert(table(rate(usd, pln, "4", old)), Money(100, usd), pln).basis.hasOutdatedRate)
        val ok = now - RateFreshness.OUTDATED_AFTER_MS
        assertFalse(convert(table(rate(usd, pln, "4", ok)), Money(100, usd), pln).basis.hasOutdatedRate)
    }

    // ---- Valuation ----

    @Test
    fun valuationAllInTarget() {
        val v = RateConverter(RateTable.EMPTY, now).value(listOf(Money(100, pln), Money(250, pln)), pln)
        assertEquals(350L, v.total!!.amountMinor)
        assertFalse(v.total!!.basis.usesConversion)
        assertTrue(v.excluded.isEmpty())
        assertNull(v.oldestFetchedAt)
    }

    @Test
    fun valuationTotalIsSumOfRoundedEstimates() {
        val t = table(rate(usd, pln, "0.4999"))
        // Each 0.01 USD converts to 0.00 PLN (0.004999 rounds to 0); three of them still total 0.
        val v = RateConverter(t, now).value(listOf(Money(1, usd), Money(1, usd), Money(1, usd), Money(10, pln)), pln)
        assertEquals(10L, v.total!!.amountMinor)
        assertTrue(v.total!!.basis.usesConversion)
    }

    @Test
    fun valuationPartialAndNothingConvertible() {
        val t = table(rate(usd, pln, "4"))
        val partial = RateConverter(t, now).value(listOf(Money(100, usd), Money(500, uah)), pln)
        assertEquals(400L, partial.total!!.amountMinor)
        assertEquals(setOf(uah), partial.excluded)

        val none = RateConverter(RateTable.EMPTY, now).value(listOf(Money(100, usd), Money(500, uah)), pln)
        assertNull(none.total)
        assertEquals(setOf(usd, uah), none.excluded)
        assertFalse(none.overflowed)
    }

    @Test
    fun valuationEmptyAndOldest() {
        val empty = RateConverter(RateTable.EMPTY, now).value(emptyList(), pln)
        assertEquals(0L, empty.total!!.amountMinor)

        val t = table(rate(usd, pln, "4", now - 5), rate(eur, pln, "4", now - 50))
        val v = RateConverter(t, now).value(listOf(Money(100, usd), Money(100, eur)), pln)
        assertEquals(now - 50, v.oldestFetchedAt)
    }
}
