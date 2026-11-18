package com.yehor.finora.domain.analytics

import com.yehor.finora.domain.money.CurrencyRegistry
import com.yehor.finora.domain.money.Money
import com.yehor.finora.domain.rate.CurrencyPair
import com.yehor.finora.domain.rate.RateQuote
import com.yehor.finora.domain.rate.RateTable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class ScopeConverterTest {

    private val pln = CurrencyRegistry.get("PLN")
    private val usd = CurrencyRegistry.get("USD")
    private val eur = CurrencyRegistry.get("EUR")
    private val now = 1_000_000_000_000L
    private val hour = 3_600_000L

    private fun quote(rate: String, fetchedAt: Long = now) =
        RateQuote(BigDecimal(rate), LocalDate.of(2026, 11, 6), fetchedAt)

    private val table = RateTable(mapOf(CurrencyPair(usd, pln) to quote("4.00")))

    private fun amount(minor: Long, c: com.yehor.finora.domain.money.Currency, count: Int = 1) =
        NativeAmount(Money(minor, c), count)

    @Test
    fun sameCurrencyStaysNative() {
        val cell = ScopeConverter(CurrencyScope.Display(pln), table, now).cell(listOf(amount(1000, pln, 2)))
        assertEquals(1000L, cell.estimate!!.amountMinor)
        assertFalse(cell.estimate!!.basis.usesConversion)
        assertEquals(2, cell.operationCount)
    }

    @Test
    fun directPairConverts() {
        val cell = ScopeConverter(CurrencyScope.Display(pln), table, now).cell(listOf(amount(1000, usd)))
        assertEquals(4000L, cell.estimate!!.amountMinor)
        assertTrue(cell.estimate!!.basis.usesConversion)
        assertEquals(now, cell.oldestFetchedAt)
    }

    @Test
    fun missingPairGoesToExcludedNeverToZero() {
        val cell = ScopeConverter(CurrencyScope.Display(pln), table, now).cell(listOf(amount(500, eur)))
        assertNull(cell.estimate)
        assertEquals(setOf(eur), cell.excluded)
        assertTrue(cell.partial)
    }

    @Test
    fun partialGroupKeepsConvertedPartAndNamesTheRest() {
        val cell = ScopeConverter(CurrencyScope.Display(pln), table, now)
            .cell(listOf(amount(1000, pln), amount(500, eur)))
        assertEquals(1000L, cell.estimate!!.amountMinor)
        assertEquals(setOf(eur), cell.excluded)
    }

    @Test
    fun outdatedRateIsFlagged() {
        val old = RateTable(mapOf(CurrencyPair(usd, pln) to quote("4.00", fetchedAt = now - 73 * hour)))
        val cell = ScopeConverter(CurrencyScope.Display(pln), old, now).cell(listOf(amount(100, usd)))
        assertTrue(cell.estimate!!.basis.hasOutdatedRate)
        assertEquals(now - 73 * hour, cell.oldestFetchedAt)
    }

    @Test
    fun overflowSetsTheFlag() {
        val cell = ScopeConverter(CurrencyScope.Display(pln), table, now)
            .cell(listOf(amount(Long.MAX_VALUE, pln), amount(1, pln)))
        assertTrue(cell.overflowed)
        assertNull(cell.estimate)
    }

    @Test
    fun conversionOverflowSetsTheFlag() {
        val cell = ScopeConverter(CurrencyScope.Display(pln), table, now).cell(listOf(amount(Long.MAX_VALUE, usd)))
        assertTrue(cell.overflowed)
    }

    @Test
    fun nativeScopeNeverReadsTheTableAndIsNotMarkedConverted() {
        val exploding = RateTable(emptyMap())
        val cell = ScopeConverter(CurrencyScope.Native(usd), exploding, now)
            .cell(listOf(amount(700, usd, 2), amount(300, usd, 1)))
        assertEquals(1000L, cell.estimate!!.amountMinor)
        assertFalse(cell.estimate!!.basis.usesConversion)
        assertFalse(cell.estimate!!.basis.hasOutdatedRate)
        assertEquals(3, cell.operationCount)
        assertTrue(cell.excluded.isEmpty())
        assertNull(cell.oldestFetchedAt)
    }

    @Test
    fun nativeScopeSkipsOtherCurrencies() {
        val cell = ScopeConverter(CurrencyScope.Native(usd), table, now).cell(listOf(amount(700, usd), amount(900, pln)))
        assertEquals(700L, cell.estimate!!.amountMinor)
    }

    @Test
    fun emptyGroupIsZero() {
        val cell = ScopeConverter(CurrencyScope.Display(pln), table, now).cell(emptyList())
        assertNotNull(cell.estimate)
        assertEquals(0L, cell.estimate!!.amountMinor)
        assertEquals(0, cell.operationCount)
    }

    @Test
    fun sameCurrencyRowsAreSummedBeforeRounding() {
        // 1 minor unit at rate 0.4 rounds to 0 on its own. Two summed make 0.8, which rounds to 1.
        val rate = RateTable(mapOf(CurrencyPair(usd, pln) to quote("0.4")))
        val cell = ScopeConverter(CurrencyScope.Display(pln), rate, now).cell(listOf(amount(1, usd), amount(1, usd)))
        assertEquals(1L, cell.estimate!!.amountMinor)
    }
}
