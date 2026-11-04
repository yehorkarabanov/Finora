package com.yehor.finora.domain.account

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Date rules for an account's opening balance, which is the account's starting point (design 4.6).
 * All results are start-of-local-day instants in epoch milliseconds, so any chosen day is never
 * after a timestamp within the same day.
 */
class OpeningDatePolicy(private val zone: ZoneId) {

    /** Start of the local day that contains [epochMillis]. Handles days whose midnight does not exist (DST). */
    fun startOfDay(epochMillis: Long): Long =
        localDate(epochMillis).atStartOfDay(zone).toInstant().toEpochMilli()

    /** A new account's opening balance is dated at the start of its creation day. */
    fun defaultForNewAccount(now: Long): Long = startOfDay(now)

    /**
     * Adding an opening balance to an account that already has entries: the start of the day of the
     * earliest entry when that is earlier than today, otherwise the start of today.
     */
    fun defaultForExistingAccount(now: Long, earliestOtherEntryAt: Long?): Long {
        val today = startOfDay(now)
        if (earliestOtherEntryAt == null) return today
        val entryDay = startOfDay(earliestOtherEntryAt)
        return if (entryDay < today) entryDay else today
    }

    /** The latest day the picker may offer: the earlier of today and the day of the earliest other entry. */
    fun latestAllowed(now: Long, earliestOtherEntryAt: Long?): Long {
        val today = startOfDay(now)
        if (earliestOtherEntryAt == null) return today
        return minOf(today, startOfDay(earliestOtherEntryAt))
    }

    /** The Material date picker returns UTC midnight of the chosen calendar day. Maps it to a local day start. */
    fun fromPickerUtcMidnight(utcMillis: Long): Long =
        Instant.ofEpochMilli(utcMillis).atOffset(ZoneOffset.UTC).toLocalDate()
            .atStartOfDay(zone).toInstant().toEpochMilli()

    /** Inverse of [fromPickerUtcMidnight], for preselecting the picker. */
    fun toPickerUtcMidnight(epochMillis: Long): Long =
        localDate(epochMillis).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    private fun localDate(epochMillis: Long): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
}
