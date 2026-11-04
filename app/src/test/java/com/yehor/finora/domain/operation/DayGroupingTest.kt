package com.yehor.finora.domain.operation

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class DayGroupingTest {

    private val zone = ZoneId.of("UTC")

    @Test
    fun groupLabelsForTodayYesterdayAndDate() {
        val today = LocalDate.of(2026, 11, 4)
        val now = ZonedDateTime.of(today, java.time.LocalTime.of(12, 0), zone).toInstant().toEpochMilli()

        val todayInstant = ZonedDateTime.of(today, java.time.LocalTime.of(9, 0), zone).toInstant().toEpochMilli()
        val yesterdayInstant = ZonedDateTime.of(today.minusDays(1), java.time.LocalTime.of(18, 0), zone).toInstant().toEpochMilli()
        val olderInstant = ZonedDateTime.of(today.minusDays(3), java.time.LocalTime.of(10, 0), zone).toInstant().toEpochMilli()

        assertEquals(DayGroup.Today, DayGrouping.group(todayInstant, now, zone))
        assertEquals(DayGroup.Yesterday, DayGrouping.group(yesterdayInstant, now, zone))
        assertEquals(DayGroup.Date(today.minusDays(3)), DayGrouping.group(olderInstant, now, zone))
    }
}
