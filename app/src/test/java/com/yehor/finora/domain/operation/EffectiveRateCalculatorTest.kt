package com.yehor.finora.domain.operation

import com.yehor.finora.domain.money.Currency
import com.yehor.finora.domain.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EffectiveRateCalculatorTest {

    private val pln = Currency("PLN", 2, "zł", "Zloty")
    private val usd = Currency("USD", 2, "$", "US Dollar")
    private val eur = Currency("EUR", 2, "€", "Euro")
    private val jpy = Currency("JPY", 0, "¥", "Yen")

    @Test
    fun sameCurrencyReturnsNull() {
        assertNull(EffectiveRateCalculator.calculate(Money(10000, pln), Money(9500, pln)))
    }

    @Test
    fun zeroAmountReturnsNull() {
        assertNull(EffectiveRateCalculator.calculate(Money(0, pln), Money(100, usd)))
        assertNull(EffectiveRateCalculator.calculate(Money(100, pln), Money(0, usd)))
    }

    @Test
    fun plnToUsd() {
        val rate = EffectiveRateCalculator.calculate(Money(100000, pln), Money(25000, usd))!!
        assertEquals("1 PLN = 0.2500 USD", rate.forward)
        assertEquals("1 USD = 4.0000 PLN", rate.inverse)
    }

    @Test
    fun eurToUsd() {
        val rate = EffectiveRateCalculator.calculate(Money(10000, eur), Money(10850, usd))!!
        assertEquals("1 EUR = 1.0850 USD", rate.forward)
        assertEquals("1 USD = 0.9217 EUR", rate.inverse)
    }

    @Test
    fun zeroScaleCurrencyUsesMajorUnits() {
        // 100.00 PLN -> 3000 JPY: 1 PLN = 30 JPY.
        val rate = EffectiveRateCalculator.calculate(Money(10000, pln), Money(3000, jpy))!!
        assertEquals("1 PLN = 30.0000 JPY", rate.forward)
        assertEquals("1 JPY = 0.0333 PLN", rate.inverse)
    }
}
