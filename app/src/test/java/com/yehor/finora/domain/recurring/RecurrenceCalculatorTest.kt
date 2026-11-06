package com.yehor.finora.domain.recurring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class RecurrenceCalculatorTest {

    @Test
    fun `nth with each unit and interval 1`() {
        val anchor = LocalDate.of(2026, 1, 15)
        assertEquals(LocalDate.of(2026, 1, 15), RecurrenceCalculator.nth(anchor, RecurrenceRule(RecurrenceUnit.DAY, 1), 0))
        assertEquals(LocalDate.of(2026, 1, 16), RecurrenceCalculator.nth(anchor, RecurrenceRule(RecurrenceUnit.DAY, 1), 1))
        assertEquals(LocalDate.of(2026, 1, 22), RecurrenceCalculator.nth(anchor, RecurrenceRule(RecurrenceUnit.WEEK, 1), 1))
        assertEquals(LocalDate.of(2026, 2, 15), RecurrenceCalculator.nth(anchor, RecurrenceRule(RecurrenceUnit.MONTH, 1), 1))
        assertEquals(LocalDate.of(2027, 1, 15), RecurrenceCalculator.nth(anchor, RecurrenceRule(RecurrenceUnit.YEAR, 1), 1))
    }

    @Test
    fun `nth with each unit and interval N`() {
        val anchor = LocalDate.of(2026, 1, 15)
        assertEquals(LocalDate.of(2026, 1, 21), RecurrenceCalculator.nth(anchor, RecurrenceRule(RecurrenceUnit.DAY, 3), 2))
        assertEquals(LocalDate.of(2026, 2, 12), RecurrenceCalculator.nth(anchor, RecurrenceRule(RecurrenceUnit.WEEK, 2), 2))
        assertEquals(LocalDate.of(2026, 7, 15), RecurrenceCalculator.nth(anchor, RecurrenceRule(RecurrenceUnit.MONTH, 3), 2))
        assertEquals(LocalDate.of(2036, 1, 15), RecurrenceCalculator.nth(anchor, RecurrenceRule(RecurrenceUnit.YEAR, 5), 2))
    }

    @Test
    fun `monthly anchoring on Jan 31 does not drift`() {
        val anchor = LocalDate.of(2026, 1, 31)
        val rule = RecurrenceRule(RecurrenceUnit.MONTH, 1)

        assertEquals(LocalDate.of(2026, 1, 31), RecurrenceCalculator.nth(anchor, rule, 0))
        assertEquals(LocalDate.of(2026, 2, 28), RecurrenceCalculator.nth(anchor, rule, 1))
        assertEquals(LocalDate.of(2026, 3, 31), RecurrenceCalculator.nth(anchor, rule, 2))
        assertEquals(LocalDate.of(2026, 4, 30), RecurrenceCalculator.nth(anchor, rule, 3))
        assertEquals(LocalDate.of(2026, 5, 31), RecurrenceCalculator.nth(anchor, rule, 4))
    }

    @Test
    fun `monthly anchoring on Jan 31 every 2 months`() {
        val anchor = LocalDate.of(2026, 1, 31)
        val rule = RecurrenceRule(RecurrenceUnit.MONTH, 2)

        assertEquals(LocalDate.of(2026, 1, 31), RecurrenceCalculator.nth(anchor, rule, 0))
        assertEquals(LocalDate.of(2026, 3, 31), RecurrenceCalculator.nth(anchor, rule, 1))
        assertEquals(LocalDate.of(2026, 5, 31), RecurrenceCalculator.nth(anchor, rule, 2))
    }

    @Test
    fun `yearly anchoring on Feb 29 leap year`() {
        val anchor = LocalDate.of(2024, 2, 29)
        val rule = RecurrenceRule(RecurrenceUnit.YEAR, 1)

        assertEquals(LocalDate.of(2024, 2, 29), RecurrenceCalculator.nth(anchor, rule, 0))
        assertEquals(LocalDate.of(2025, 2, 28), RecurrenceCalculator.nth(anchor, rule, 1))
        assertEquals(LocalDate.of(2026, 2, 28), RecurrenceCalculator.nth(anchor, rule, 2))
        assertEquals(LocalDate.of(2027, 2, 28), RecurrenceCalculator.nth(anchor, rule, 3))
        assertEquals(LocalDate.of(2028, 2, 29), RecurrenceCalculator.nth(anchor, rule, 4))
    }

    @Test
    fun `weekly recurrence preserves day of week`() {
        val anchor = LocalDate.of(2026, 11, 6) // Friday
        val rule = RecurrenceRule(RecurrenceUnit.WEEK, 3)

        for (k in 0..10L) {
            val date = RecurrenceCalculator.nth(anchor, rule, k)
            assertEquals(DayOfWeek.FRIDAY, date.dayOfWeek)
        }
    }

    @Test
    fun `firstAfter on exact occurrence, one day before, one day after, far from anchor`() {
        val anchor = LocalDate.of(2026, 1, 10)
        val rule = RecurrenceRule(RecurrenceUnit.MONTH, 1)

        // Before anchor
        assertEquals(LocalDate.of(2026, 1, 10), RecurrenceCalculator.firstAfter(anchor, rule, LocalDate.of(2026, 1, 1)))

        // Exactly on anchor (Jan 10) -> next is Feb 10
        assertEquals(LocalDate.of(2026, 2, 10), RecurrenceCalculator.firstAfter(anchor, rule, LocalDate.of(2026, 1, 10)))

        // One day before occurrence (Feb 9) -> next is Feb 10
        assertEquals(LocalDate.of(2026, 2, 10), RecurrenceCalculator.firstAfter(anchor, rule, LocalDate.of(2026, 2, 9)))

        // One day after occurrence (Feb 11) -> next is Mar 10
        assertEquals(LocalDate.of(2026, 3, 10), RecurrenceCalculator.firstAfter(anchor, rule, LocalDate.of(2026, 2, 11)))

        // Far from anchor (e.g. 5 years later, 2031-06-15) -> next is 2031-07-10
        assertEquals(LocalDate.of(2031, 7, 10), RecurrenceCalculator.firstAfter(anchor, rule, LocalDate.of(2031, 6, 15)))
    }

    @Test
    fun `next returns null past end date`() {
        val anchor = LocalDate.of(2026, 1, 1)
        val rule = RecurrenceRule(RecurrenceUnit.MONTH, 1)
        val end = LocalDate.of(2026, 3, 1)

        assertEquals(LocalDate.of(2026, 2, 1), RecurrenceCalculator.next(anchor, rule, LocalDate.of(2026, 1, 1), end))
        assertEquals(LocalDate.of(2026, 3, 1), RecurrenceCalculator.next(anchor, rule, LocalDate.of(2026, 2, 1), end))
        assertNull(RecurrenceCalculator.next(anchor, rule, LocalDate.of(2026, 3, 1), end))
    }

    @Test
    fun `between returns occurrences within range up to limit`() {
        val anchor = LocalDate.of(2026, 1, 1)
        val rule = RecurrenceRule(RecurrenceUnit.MONTH, 1)

        val list = RecurrenceCalculator.between(
            anchor = anchor,
            rule = rule,
            from = LocalDate.of(2026, 2, 15),
            toInclusive = LocalDate.of(2026, 6, 1),
            limit = 3,
        )
        assertEquals(
            listOf(
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 5, 1),
            ),
            list,
        )
    }

    @Test
    fun `startOfDay handles missing midnight in DST gap`() {
        // America/Sao_Paulo on 2018-11-04 moved clock from 00:00 to 01:00 (midnight did not exist)
        val zone = ZoneId.of("America/Sao_Paulo")
        val date = LocalDate.of(2018, 11, 4)
        val epochMs = RecurrenceCalculator.startOfDay(date, zone)

        val zdt = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(epochMs), zone)
        assertEquals(2018, zdt.year)
        assertEquals(11, zdt.monthValue)
        assertEquals(4, zdt.dayOfMonth)
        assertEquals(1, zdt.hour) // Starts at 01:00
        assertEquals(0, zdt.minute)
    }

    @Test
    fun `startOfDay handles normal zone and DST overlap`() {
        val zone = ZoneId.of("Europe/London")
        val date = LocalDate.of(2026, 10, 25) // DST fall back
        val epochMs = RecurrenceCalculator.startOfDay(date, zone)

        val zdt = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(epochMs), zone)
        assertEquals(2026, zdt.year)
        assertEquals(10, zdt.monthValue)
        assertEquals(25, zdt.dayOfMonth)
        assertEquals(0, zdt.hour)
    }
}
