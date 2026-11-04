package com.yehor.finora.domain.operation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class TransactionDateTimeTest {

    private val zone = ZoneId.of("Europe/Warsaw")

    @Test
    fun combineAndSplitRoundTrip() {
        val localDate = LocalDate.of(2026, 11, 4)
        val localTime = LocalTime.of(15, 30)
        val pickerUtcMidnight = localDate.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()

        val instant = TransactionDateTime.combine(pickerUtcMidnight, localTime.hour, localTime.minute, zone)
        val split = TransactionDateTime.split(instant, zone)

        assertEquals(localDate, split.date)
        assertEquals(LocalTime.of(15, 30, 0), split.time)
    }

    @Test
    fun futureCheck() {
        val now = 1000L
        assertFalse(TransactionDateTime.isFuture(1000L, now))
        assertFalse(TransactionDateTime.isFuture(999L, now))
        assertTrue(TransactionDateTime.isFuture(1001L, now))
    }
}
