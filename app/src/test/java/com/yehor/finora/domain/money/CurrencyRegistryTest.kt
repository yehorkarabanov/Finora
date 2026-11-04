package com.yehor.finora.domain.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CurrencyRegistryTest {

    /** Pinned expected scales. Changing a registry scale must change this table in the same commit. */
    private val pinnedScales = mapOf(
        "USD" to 2, "EUR" to 2, "GBP" to 2, "PLN" to 2, "UAH" to 2,
        "JPY" to 0, "KRW" to 0,
        "BHD" to 3, "KWD" to 3,
        "CLF" to 4,
    )

    @Test
    fun pinnedScalesMatchRegistry() {
        pinnedScales.forEach { (code, scale) ->
            assertEquals("scale of $code", scale, CurrencyRegistry.get(code).scale)
        }
    }

    @Test
    fun everyEntryHasCodeSymbolAndName() {
        assertTrue(CurrencyRegistry.all.isNotEmpty())
        CurrencyRegistry.all.forEach {
            assertTrue("code ${it.code}", Regex("[A-Z]{3}").matches(it.code))
            assertTrue("symbol of ${it.code}", it.symbol.isNotBlank())
            assertTrue("name of ${it.code}", it.name.isNotBlank())
            assertTrue("scale of ${it.code}", it.scale in 0..4)
        }
    }

    @Test
    fun codesAreUnique() {
        val codes = CurrencyRegistry.all.map { it.code }
        assertEquals(codes.size, codes.toSet().size)
    }

    @Test
    fun unknownCodeIsRejected() {
        assertNull(CurrencyRegistry.findOrNull("XXX"))
        assertNull(CurrencyRegistry.findOrNull("usd"))
        try {
            CurrencyRegistry.get("XXX")
            fail("expected UnknownCurrencyException")
        } catch (_: UnknownCurrencyException) {
        }
    }

    @Test
    fun knownCodeResolves() {
        assertNotNull(CurrencyRegistry.findOrNull("USD"))
    }

    /**
     * Non-gating cross-check against the platform. Prints every code whose scale differs from
     * `java.util.Currency` and never fails, because platform data may be older or newer.
     */
    @Test
    fun crossCheckAgainstPlatformReportsOnly() {
        CurrencyRegistry.all.forEach { c ->
            val platform = try {
                java.util.Currency.getInstance(c.code).defaultFractionDigits
            } catch (_: IllegalArgumentException) {
                println("CROSS-CHECK ${c.code}: not known to platform")
                return@forEach
            }
            if (platform != c.scale) {
                println("CROSS-CHECK ${c.code}: registry=${c.scale} platform=$platform")
            }
        }
    }
}
