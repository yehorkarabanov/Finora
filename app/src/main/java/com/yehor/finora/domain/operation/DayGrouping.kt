package com.yehor.finora.domain.operation

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

sealed interface DayGroup {
    data object Today : DayGroup
    data object Yesterday : DayGroup
    data class Date(val localDate: LocalDate) : DayGroup
}

object DayGrouping {
    fun group(instant: Long, now: Long, zone: ZoneId): DayGroup {
        val itemDate = Instant.ofEpochMilli(instant).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val yesterday = today.minusDays(1)
        return when (itemDate) {
            today -> DayGroup.Today
            yesterday -> DayGroup.Yesterday
            else -> DayGroup.Date(itemDate)
        }
    }
}
