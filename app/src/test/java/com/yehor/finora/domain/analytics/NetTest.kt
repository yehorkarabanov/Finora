package com.yehor.finora.domain.analytics

import com.yehor.finora.domain.money.CurrencyRegistry
import com.yehor.finora.domain.money.Estimate
import com.yehor.finora.domain.money.EstimateBasis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetTest {

    private val pln = CurrencyRegistry.get("PLN")
    private val usd = CurrencyRegistry.get("USD")

    private fun cell(
        minor: Long?,
        count: Int = 1,
        excluded: Set<com.yehor.finora.domain.money.Currency> = emptySet(),
        overflowed: Boolean = false,
        fetched: Long? = null,
        converted: Boolean = false,
    ) = Cell(
        estimate = minor?.let { Estimate(it, pln, EstimateBasis(converted, false)) },
        operationCount = count,
        excluded = excluded,
        overflowed = overflowed,
        oldestFetchedAt = fetched,
    )

    @Test
    fun netIsIncomeMinusExpense() {
        val net = netCashFlow(cell(1000, 2), cell(400, 3))
        assertEquals(600L, net.estimate!!.amountMinor)
        assertEquals(5, net.operationCount)
    }

    @Test
    fun netCanBeNegative() {
        assertEquals(-300L, netCashFlow(cell(100), cell(400)).estimate!!.amountMinor)
    }

    @Test
    fun anEmptySideCountsAsZero() {
        assertEquals(1000L, netCashFlow(cell(1000), Cell.NONE).estimate!!.amountMinor)
        assertEquals(-400L, netCashFlow(Cell.NONE, cell(400)).estimate!!.amountMinor)
    }

    @Test
    fun bothSidesEmptyGiveNoEstimate() {
        assertNull(netCashFlow(Cell.NONE, Cell.NONE).estimate)
    }

    @Test
    fun overflowOnEitherSideIsAnOverflow() {
        assertTrue(netCashFlow(cell(null, overflowed = true), cell(5)).overflowed)
        assertTrue(netCashFlow(cell(5), cell(null, overflowed = true)).overflowed)
    }

    @Test
    fun overflowInTheSubtractionIsAnOverflow() {
        val net = netCashFlow(cell(Long.MAX_VALUE), cell(-5))
        assertTrue(net.overflowed)
        assertNull(net.estimate)
    }

    @Test
    fun partialFlagsAndOldestRateSurvive() {
        val net = netCashFlow(cell(1000, excluded = setOf(usd), fetched = 50L), cell(400, fetched = 20L))
        assertEquals(setOf(usd), net.excluded)
        assertEquals(20L, net.oldestFetchedAt)
        assertTrue(net.partial)
    }

    @Test
    fun conversionBasisIsCombined() {
        val net = netCashFlow(cell(1000, converted = true), cell(400))
        assertTrue(net.estimate!!.basis.usesConversion)
    }

    @Test
    fun sumCellsOfNothingIsNone() {
        assertEquals(Cell.NONE, emptyList<Cell>().sumCells())
    }

    @Test
    fun sumCellsAddsAmounts() {
        assertEquals(30L, listOf(cell(10), cell(20)).sumCells().estimate!!.amountMinor)
    }

    @Test
    fun orZeroTurnsAQuietCellIntoAnExactZero() {
        val zero = Cell.NONE.orZero(pln)
        assertNotNull(zero.estimate)
        assertEquals(0L, zero.estimate!!.amountMinor)
        assertFalse(zero.estimate!!.basis.usesConversion)
    }

    @Test
    fun orZeroKeepsWarnings() {
        val partial = cell(null, excluded = setOf(usd))
        assertNull(partial.orZero(pln).estimate)
        val over = cell(null, overflowed = true)
        assertNull(over.orZero(pln).estimate)
    }

    @Test
    fun orZeroKeepsARealAmount() {
        assertEquals(7L, cell(7).orZero(pln).estimate!!.amountMinor)
    }
}
