package com.yehor.finora.domain.analytics

import com.yehor.finora.domain.money.CurrencyMismatchException
import com.yehor.finora.domain.money.CurrencyRegistry
import com.yehor.finora.domain.money.Estimate
import com.yehor.finora.domain.money.EstimateBasis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareAndDeltaTest {

    private val pln = CurrencyRegistry.get("PLN")
    private val usd = CurrencyRegistry.get("USD")

    private fun est(minor: Long, converted: Boolean = false, outdated: Boolean = false) =
        Estimate(minor, pln, EstimateBasis(converted, outdated))

    @Test
    fun shareOfAZeroTotalIsNull() {
        assertNull(share(est(10), est(0)))
    }

    @Test
    fun shareOfDifferentCurrenciesIsNull() {
        assertNull(share(est(10), Estimate(100, usd, EstimateBasis.NATIVE)))
    }

    @Test
    fun shareRoundsHalfUpAtATie() {
        // 1/8 = 125 permille exactly; 1/16 = 62.5 permille, ties up to 63.
        assertEquals(125, share(est(1), est(8))!!.permille)
        assertEquals(63, share(est(1), est(16))!!.permille)
    }

    @Test
    fun wholeIsOneThousandPermille() {
        assertEquals(1000, share(est(500), est(500))!!.permille)
    }

    @Test
    fun deltaAgainstZeroIsNew() {
        val d = delta(est(100), est(0))!!
        assertEquals(100L, d.amount.amountMinor)
        assertNull(d.percentTimesTen)
    }

    @Test
    fun deltaAgainstMissingPreviousIsNew() {
        val d = delta(est(100), null)!!
        assertEquals(100L, d.amount.amountMinor)
        assertNull(d.percentTimesTen)
    }

    @Test
    fun positiveDeltaPercentIsTimesTen() {
        // 1120 vs 1000: +120, +12.0 percent -> 120
        val d = delta(est(1120), est(1000))!!
        assertEquals(120L, d.amount.amountMinor)
        assertEquals(120, d.percentTimesTen)
    }

    @Test
    fun negativeDelta() {
        val d = delta(est(750), est(1000))!!
        assertEquals(-250L, d.amount.amountMinor)
        assertEquals(-250, d.percentTimesTen)
    }

    @Test
    fun rowOnlyInThePreviousPeriodIsAFullDrop() {
        val d = delta(null, est(400))!!
        assertEquals(-400L, d.amount.amountMinor)
        assertEquals(-1000, d.percentTimesTen)
    }

    @Test
    fun deltaRoundsHalfUp() {
        // +1 on 16 = 6.25 percent = 62.5 -> 63
        assertEquals(63, delta(est(17), est(16))!!.percentTimesTen)
    }

    @Test
    fun differentCurrenciesGiveNoDelta() {
        assertNull(delta(est(10), Estimate(10, usd, EstimateBasis.NATIVE)))
    }

    @Test
    fun bothMissingGivesNoDelta() {
        assertNull(delta(null, null))
    }

    @Test
    fun basisFlagsMerge() {
        val d = delta(est(10, converted = true), est(5, outdated = true))!!
        assertTrue(d.amount.basis.usesConversion)
        assertTrue(d.amount.basis.hasOutdatedRate)
        assertFalse(delta(est(10), est(5))!!.amount.basis.usesConversion)
    }

    @Test
    fun estimatesOfDifferentCurrenciesCannotBeAdded() {
        var thrown = false
        try {
            est(1) + Estimate(1, usd, EstimateBasis.NATIVE)
        } catch (e: CurrencyMismatchException) {
            thrown = true
        }
        assertTrue(thrown)
    }
}
