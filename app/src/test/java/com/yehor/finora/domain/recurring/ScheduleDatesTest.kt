package com.yehor.finora.domain.recurring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ScheduleDatesTest {

    @Test
    fun `unchanged rule keeps anchor and advances nextDate past lastHandled`() {
        val anchor = ScheduleDates.Anchor(
            startDate = LocalDate.of(2026, 1, 1),
            rule = RecurrenceRule(RecurrenceUnit.MONTH, 1),
            endDate = null,
            nextDate = LocalDate.of(2026, 3, 1),
        )
        val edited = ScheduleDates.Edited(
            firstDate = LocalDate.of(2026, 3, 1),
            rule = RecurrenceRule(RecurrenceUnit.MONTH, 1),
            endDate = null,
        )
        val lastHandled = LocalDate.of(2026, 2, 1)

        val result = ScheduleDates.reschedule(anchor, edited, lastHandled)
        assertTrue(result is ScheduleDates.Result.Accepted)
        val accepted = result as ScheduleDates.Result.Accepted
        assertEquals(LocalDate.of(2026, 1, 1), accepted.startDate)
        assertEquals(LocalDate.of(2026, 3, 1), accepted.nextDate)
    }

    @Test
    fun `rule change re-anchors to firstDate`() {
        val anchor = ScheduleDates.Anchor(
            startDate = LocalDate.of(2026, 1, 1),
            rule = RecurrenceRule(RecurrenceUnit.MONTH, 1),
            endDate = null,
            nextDate = LocalDate.of(2026, 3, 1),
        )
        val edited = ScheduleDates.Edited(
            firstDate = LocalDate.of(2026, 3, 15),
            rule = RecurrenceRule(RecurrenceUnit.WEEK, 2),
            endDate = null,
        )
        val lastHandled = LocalDate.of(2026, 2, 1)

        val result = ScheduleDates.reschedule(anchor, edited, lastHandled)
        assertTrue(result is ScheduleDates.Result.Accepted)
        val accepted = result as ScheduleDates.Result.Accepted
        assertEquals(LocalDate.of(2026, 3, 15), accepted.startDate)
        assertEquals(LocalDate.of(2026, 3, 15), accepted.nextDate)
    }

    @Test
    fun `next-date change re-anchors to firstDate`() {
        val anchor = ScheduleDates.Anchor(
            startDate = LocalDate.of(2026, 1, 1),
            rule = RecurrenceRule(RecurrenceUnit.MONTH, 1),
            endDate = null,
            nextDate = LocalDate.of(2026, 3, 1),
        )
        val edited = ScheduleDates.Edited(
            firstDate = LocalDate.of(2026, 3, 10),
            rule = RecurrenceRule(RecurrenceUnit.MONTH, 1),
            endDate = null,
        )
        val lastHandled = LocalDate.of(2026, 2, 1)

        val result = ScheduleDates.reschedule(anchor, edited, lastHandled)
        assertTrue(result is ScheduleDates.Result.Accepted)
        val accepted = result as ScheduleDates.Result.Accepted
        assertEquals(LocalDate.of(2026, 3, 10), accepted.startDate)
        assertEquals(LocalDate.of(2026, 3, 10), accepted.nextDate)
    }

    @Test
    fun `firstDate equal to or before lastHandled is rejected`() {
        val anchor = ScheduleDates.Anchor(
            startDate = LocalDate.of(2026, 1, 1),
            rule = RecurrenceRule(RecurrenceUnit.MONTH, 1),
            endDate = null,
            nextDate = LocalDate.of(2026, 3, 1),
        )
        val lastHandled = LocalDate.of(2026, 2, 1)

        val editedEqual = ScheduleDates.Edited(
            firstDate = LocalDate.of(2026, 2, 1),
            rule = RecurrenceRule(RecurrenceUnit.WEEK, 1),
            endDate = null,
        )
        val resultEqual = ScheduleDates.reschedule(anchor, editedEqual, lastHandled)
        assertEquals(ScheduleDates.Result.Rejected(ScheduleDates.Result.Reason.NEXT_NOT_AFTER_HANDLED), resultEqual)

        val editedBefore = ScheduleDates.Edited(
            firstDate = LocalDate.of(2026, 1, 15),
            rule = RecurrenceRule(RecurrenceUnit.WEEK, 1),
            endDate = null,
        )
        val resultBefore = ScheduleDates.reschedule(anchor, editedBefore, lastHandled)
        assertEquals(ScheduleDates.Result.Rejected(ScheduleDates.Result.Reason.NEXT_NOT_AFTER_HANDLED), resultBefore)
    }

    @Test
    fun `end date shortened ends the schedule`() {
        val anchor = ScheduleDates.Anchor(
            startDate = LocalDate.of(2026, 1, 1),
            rule = RecurrenceRule(RecurrenceUnit.MONTH, 1),
            endDate = LocalDate.of(2026, 12, 1),
            nextDate = LocalDate.of(2026, 5, 1),
        )
        val edited = ScheduleDates.Edited(
            firstDate = LocalDate.of(2026, 5, 1),
            rule = RecurrenceRule(RecurrenceUnit.MONTH, 1),
            endDate = LocalDate.of(2026, 4, 1), // Shortened before nextDate
        )
        val lastHandled = LocalDate.of(2026, 4, 1)

        val result = ScheduleDates.reschedule(anchor, edited, lastHandled)
        assertTrue(result is ScheduleDates.Result.Accepted)
        val accepted = result as ScheduleDates.Result.Accepted
        assertEquals(LocalDate.of(2026, 1, 1), accepted.startDate)
        assertEquals(null, accepted.nextDate)
    }

    @Test
    fun `end date extended revives an ended schedule`() {
        val anchor = ScheduleDates.Anchor(
            startDate = LocalDate.of(2026, 1, 1),
            rule = RecurrenceRule(RecurrenceUnit.MONTH, 1),
            endDate = LocalDate.of(2026, 3, 1),
            nextDate = null, // Ended
        )
        val lastHandled = LocalDate.of(2026, 3, 1)
        val naturalNext = LocalDate.of(2026, 4, 1)

        val edited = ScheduleDates.Edited(
            firstDate = naturalNext,
            rule = RecurrenceRule(RecurrenceUnit.MONTH, 1),
            endDate = LocalDate.of(2026, 6, 1),
        )

        val result = ScheduleDates.reschedule(anchor, edited, lastHandled)
        assertTrue(result is ScheduleDates.Result.Accepted)
        val accepted = result as ScheduleDates.Result.Accepted
        assertEquals(LocalDate.of(2026, 1, 1), accepted.startDate)
        assertEquals(LocalDate.of(2026, 4, 1), accepted.nextDate)
    }

    @Test
    fun `nothing handled yet keeps nextDate equal to firstDate or startDate`() {
        val anchor = ScheduleDates.Anchor(
            startDate = LocalDate.of(2026, 1, 1),
            rule = RecurrenceRule(RecurrenceUnit.MONTH, 1),
            endDate = null,
            nextDate = LocalDate.of(2026, 1, 1),
        )
        val edited = ScheduleDates.Edited(
            firstDate = LocalDate.of(2026, 1, 1),
            rule = RecurrenceRule(RecurrenceUnit.MONTH, 1),
            endDate = null,
        )

        val result = ScheduleDates.reschedule(anchor, edited, lastHandled = null)
        assertTrue(result is ScheduleDates.Result.Accepted)
        val accepted = result as ScheduleDates.Result.Accepted
        assertEquals(LocalDate.of(2026, 1, 1), accepted.startDate)
        assertEquals(LocalDate.of(2026, 1, 1), accepted.nextDate)
    }
}
