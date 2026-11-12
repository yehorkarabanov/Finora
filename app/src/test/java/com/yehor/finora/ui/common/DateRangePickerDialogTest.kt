package com.yehor.finora.ui.common

import com.yehor.finora.ui.operations.dateOf
import com.yehor.finora.ui.operations.endExclusiveMillis
import com.yehor.finora.ui.operations.startOfDayMillis
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class DateRangePickerDialogTest {

    @Test
    fun `picker millis round trip through UTC`() {
        val date = LocalDate.of(2026, 11, 3)
        assertEquals(date, utcDate(utcMillis(date)))
    }

    @Test
    fun `custom range survives open confirm reopen in any zone`() {
        val start = LocalDate.of(2026, 3, 29) // DST change day in Europe
        val end = LocalDate.of(2026, 11, 9)
        listOf("Pacific/Kiritimati", "Europe/Warsaw", "UTC", "America/Los_Angeles", "Pacific/Pago_Pago").forEach { id ->
            val zone = ZoneId.of(id)
            val from = startOfDayMillis(start, zone)
            val toExclusive = endExclusiveMillis(end, zone)
            assertEquals(id, start, dateOf(from, zone))
            assertEquals(id, end, dateOf(toExclusive - 1, zone))
        }
    }

    @Test
    fun `inclusive day count`() {
        assertEquals(1, inclusiveDays(LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 9)))
        assertEquals(7, inclusiveDays(LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 9)))
    }

    @Test
    fun `compact range label`() {
        val l = Locale.ENGLISH
        assertEquals("9 Nov 2026", compactRangeLabel(LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 9), l))
        assertEquals("3 Nov – 9 Nov 2026", compactRangeLabel(LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 9), l))
        assertEquals("3 Nov 2025 – 9 Nov 2026", compactRangeLabel(LocalDate.of(2025, 11, 3), LocalDate.of(2026, 11, 9), l))
    }
}
