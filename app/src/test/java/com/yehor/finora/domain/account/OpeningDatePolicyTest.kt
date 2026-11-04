package com.yehor.finora.domain.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class OpeningDatePolicyTest {
    private val warsaw = ZoneId.of("Europe/Warsaw")
    private val policy = OpeningDatePolicy(warsaw)

    private fun at(zone: ZoneId, y: Int, m: Int, d: Int, h: Int = 0, min: Int = 0): Long =
        LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant().toEpochMilli()

    private fun warsawAt(y: Int, m: Int, d: Int, h: Int = 0, min: Int = 0) = at(warsaw, y, m, d, h, min)

    @Test
    fun startOfDayIsLocalMidnight() {
        assertEquals(warsawAt(2026, 11, 4), policy.startOfDay(warsawAt(2026, 11, 4, 15, 30)))
        assertEquals(warsawAt(2026, 11, 4), policy.startOfDay(warsawAt(2026, 11, 4, 0, 0)))
        assertEquals(warsawAt(2026, 11, 4), policy.startOfDay(warsawAt(2026, 11, 4, 23, 59)))
    }

    @Test
    fun startOfDayOnDstTransitionDays() {
        // Spring forward in Warsaw: 2026-03-29, the day is 23 hours long.
        val springStart = warsawAt(2026, 3, 29)
        assertEquals(springStart, policy.startOfDay(warsawAt(2026, 3, 29, 12, 0)))
        assertEquals(springStart, policy.startOfDay(warsawAt(2026, 3, 29, 3, 30)))
        // Fall back: 2026-10-25, the day is 25 hours long.
        val fallStart = warsawAt(2026, 10, 25)
        assertEquals(fallStart, policy.startOfDay(warsawAt(2026, 10, 25, 23, 0)))
        assertEquals(fallStart, policy.startOfDay(fallStart + (24L * 3_600_000)))
    }

    @Test
    fun startOfDayWhenMidnightDoesNotExist() {
        // Sao Paulo skipped 00:00 on 2018-11-04; the day starts at 01:00 local.
        val sp = ZoneId.of("America/Sao_Paulo")
        val spPolicy = OpeningDatePolicy(sp)
        val start = LocalDate.of(2018, 11, 4).atStartOfDay(sp).toInstant().toEpochMilli()
        assertEquals(start, spPolicy.startOfDay(at(sp, 2018, 11, 4, 18, 0)))
        assertEquals(LocalDateTime.of(2018, 11, 4, 3, 0).toInstant(ZoneOffset.UTC).toEpochMilli(), start)
    }

    @Test
    fun newAccountUsesStartOfCreationDay() {
        val now = warsawAt(2026, 11, 4, 19, 40)
        assertEquals(warsawAt(2026, 11, 4), policy.defaultForNewAccount(now))
    }

    @Test
    fun existingAccountDefaultsToEarliestEntryDayWhenEarlierThanToday() {
        val now = warsawAt(2026, 11, 4, 19, 40)
        val earliest = warsawAt(2026, 10, 1, 14, 0)
        assertEquals(warsawAt(2026, 10, 1), policy.defaultForExistingAccount(now, earliest))
        assertEquals(warsawAt(2026, 10, 1), policy.latestAllowed(now, earliest))
    }

    @Test
    fun existingAccountWithEarliestEntryToday() {
        val now = warsawAt(2026, 11, 4, 19, 40)
        val earliest = warsawAt(2026, 11, 4, 8, 0)
        assertEquals(warsawAt(2026, 11, 4), policy.defaultForExistingAccount(now, earliest))
        assertEquals(warsawAt(2026, 11, 4), policy.latestAllowed(now, earliest))
    }

    @Test
    fun existingAccountWithoutEntries() {
        val now = warsawAt(2026, 11, 4, 19, 40)
        assertEquals(warsawAt(2026, 11, 4), policy.defaultForExistingAccount(now, null))
        assertEquals(warsawAt(2026, 11, 4), policy.latestAllowed(now, null))
    }

    @Test
    fun futureEntryNeverRaisesTheMaximumAboveToday() {
        val now = warsawAt(2026, 11, 4, 19, 40)
        val earliest = warsawAt(2026, 12, 1, 9, 0)
        assertEquals(warsawAt(2026, 11, 4), policy.defaultForExistingAccount(now, earliest))
        assertEquals(warsawAt(2026, 11, 4), policy.latestAllowed(now, earliest))
    }

    @Test
    fun anyAllowedDayIsNotAfterTheEarliestEntry() {
        val now = warsawAt(2026, 11, 4, 19, 40)
        val earliest = warsawAt(2026, 11, 4, 0, 0) // exactly midnight
        assertTrue(policy.latestAllowed(now, earliest) <= earliest)
        val lateEarliest = warsawAt(2026, 11, 4, 23, 59)
        assertTrue(policy.latestAllowed(now, lateEarliest) <= lateEarliest)
    }

    @Test
    fun pickerUtcMidnightMapsToLocalDayStart() {
        val utcMidnight = LocalDate.of(2026, 3, 29).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        assertEquals(warsawAt(2026, 3, 29), policy.fromPickerUtcMidnight(utcMidnight))
    }

    @Test
    fun pickerRoundTrip() {
        val local = warsawAt(2026, 3, 29)
        assertEquals(local, policy.fromPickerUtcMidnight(policy.toPickerUtcMidnight(local)))
        val utcMidnight = LocalDate.of(2026, 10, 25).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        assertEquals(utcMidnight, policy.toPickerUtcMidnight(policy.fromPickerUtcMidnight(utcMidnight)))
    }

    @Test
    fun pickerMappingInZoneBehindUtc() {
        val la = ZoneId.of("America/Los_Angeles")
        val laPolicy = OpeningDatePolicy(la)
        val utcMidnight = LocalDate.of(2026, 11, 4).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        // Picker day 2026-11-04 stays 2026-11-04 locally, not the previous day.
        assertEquals(at(la, 2026, 11, 4), laPolicy.fromPickerUtcMidnight(utcMidnight))
    }
}
