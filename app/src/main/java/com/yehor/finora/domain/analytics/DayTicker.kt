package com.yehor.finora.domain.analytics

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transformLatest

/**
 * Emits today's date on collection and again at each local midnight plus one second. A value from
 * [zoneChanges] makes it recompute straight away with the zone returned by [zone].
 */
class DayTicker(
    private val nowMillis: () -> Long,
    private val zone: () -> ZoneId,
    private val zoneChanges: Flow<Unit> = emptyFlow(),
) {

    @OptIn(ExperimentalCoroutinesApi::class)
    fun days(): Flow<LocalDate> = zoneChanges
        .onStart { emit(Unit) }
        .transformLatest {
            while (true) {
                val currentZone = zone()
                val now = nowMillis()
                val today = Instant.ofEpochMilli(now).atZone(currentZone).toLocalDate()
                emit(today)
                val wakeAt = today.plusDays(1).atStartOfDay(currentZone).toInstant().toEpochMilli() + WAKE_OFFSET_MS
                delay((wakeAt - now).coerceAtLeast(1L))
            }
        }
        .distinctUntilChanged()

    private companion object {
        const val WAKE_OFFSET_MS = 1_000L
    }
}
