package com.yehor.finora.domain.analytics

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class DayTickerTest {

    private val utc = ZoneId.of("UTC")

    private fun millis(date: LocalDate, hour: Int = 0, minute: Int = 0, second: Int = 0): Long =
        date.atTime(hour, minute, second).atZone(utc).toInstant().toEpochMilli()

    @Test
    fun emitsTodayThenTheNextDayAfterMidnightPlusOneSecond() = runTest(StandardTestDispatcher()) {
        val start = millis(LocalDate.of(2026, 11, 7), 23, 59, 0)
        val ticker = DayTicker(nowMillis = { start + currentTime }, zone = { utc })
        val seen = mutableListOf<LocalDate>()
        val job = launch { ticker.days().toList(seen) }

        runCurrent()
        assertEquals(listOf(LocalDate.of(2026, 11, 7)), seen)

        advanceTimeBy(60_000) // exactly midnight: not yet
        runCurrent()
        assertEquals(1, seen.size)

        advanceTimeBy(1_000) // midnight + 1 s
        runCurrent()
        assertEquals(listOf(LocalDate.of(2026, 11, 7), LocalDate.of(2026, 11, 8)), seen)
        job.cancel()
    }

    @Test
    fun recomputesAfterAZoneChange() = runTest(StandardTestDispatcher()) {
        val instant = millis(LocalDate.of(2026, 11, 7), 23, 30, 0) // 23:30 UTC = 00:30 next day in Warsaw
        var zone = utc
        val changes = MutableSharedFlow<Unit>()
        val ticker = DayTicker(nowMillis = { instant }, zone = { zone }, zoneChanges = changes)
        val seen = mutableListOf<LocalDate>()
        val job = launch { ticker.days().toList(seen) }

        runCurrent()
        assertEquals(listOf(LocalDate.of(2026, 11, 7)), seen)

        zone = ZoneId.of("Europe/Warsaw")
        changes.emit(Unit)
        runCurrent()
        assertEquals(listOf(LocalDate.of(2026, 11, 7), LocalDate.of(2026, 11, 8)), seen)
        job.cancel()
    }
}
