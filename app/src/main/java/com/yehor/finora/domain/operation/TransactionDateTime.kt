package com.yehor.finora.domain.operation

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

data class SplitDateTime(val date: LocalDate, val time: LocalTime)

object TransactionDateTime {

    fun combine(pickerUtcMidnight: Long, hour: Int, minute: Int, zone: ZoneId): Long {
        val utcDate = Instant.ofEpochMilli(pickerUtcMidnight).atZone(ZoneId.of("UTC")).toLocalDate()
        val localTime = LocalTime.of(hour, minute, 0, 0)
        return ZonedDateTime.of(utcDate, localTime, zone).toInstant().toEpochMilli()
    }

    fun split(instant: Long, zone: ZoneId): SplitDateTime {
        val zdt = Instant.ofEpochMilli(instant).atZone(zone)
        return SplitDateTime(zdt.toLocalDate(), zdt.toLocalTime().withSecond(0).withNano(0))
    }

    fun isFuture(instant: Long, now: Long): Boolean = instant > now

    fun latestSelectableDay(now: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(now).atZone(zone).toLocalDate()

    fun pickerUtcMidnight(date: LocalDate): Long =
        date.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
}
