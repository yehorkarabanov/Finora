package com.yehor.finora.ui.settings

import com.yehor.finora.domain.money.CurrencyRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsLogicTest {
    private val all = CurrencyRegistry.all

    @Test fun blankQueryKeepsAll() {
        assertEquals(all, SettingsLogic.filterCurrencies(all, "   "))
    }

    @Test fun matchesCodeCaseInsensitiveAndTrimmed() {
        val result = SettingsLogic.filterCurrencies(all, "  usd ")
        assertTrue(result.isNotEmpty())
        assertEquals("USD", result.first().code)
    }

    @Test fun matchesNameCaseInsensitive() {
        val usd = CurrencyRegistry.get("USD")
        val result = SettingsLogic.filterCurrencies(all, usd.name.uppercase())
        assertTrue(result.any { it.code == "USD" })
        assertTrue(result.all { it.name.contains(usd.name, ignoreCase = true) })
    }

    @Test fun exactCodeComesFirst() {
        val result = SettingsLogic.filterCurrencies(all, "eur")
        assertEquals("EUR", result.first().code)
    }

    @Test fun noMatchIsEmpty() {
        assertTrue(SettingsLogic.filterCurrencies(all, "zzzzqq").isEmpty())
    }
}
