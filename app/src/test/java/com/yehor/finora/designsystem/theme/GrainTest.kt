package com.yehor.finora.designsystem.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GrainTest {
    @Test
    fun pixelsHaveRequestedSize() {
        assertEquals(16384, grainPixels(128).size)
    }

    @Test
    fun deterministicForSeed() {
        assertTrue(grainPixels(32, 1L).contentEquals(grainPixels(32, 1L)))
        assertFalse(grainPixels(32, 1L).contentEquals(grainPixels(32, 2L)))
    }

    @Test
    fun opaqueAndGrey() {
        grainPixels(32).forEach { p ->
            val a = (p ushr 24) and 0xFF
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            assertEquals(255, a)
            assertTrue(r == g && g == b)
        }
    }
}
