package com.yehor.finora.ui.recurring

import com.yehor.finora.domain.account.Account
import com.yehor.finora.domain.account.AccountType
import com.yehor.finora.domain.model.AccountStatus
import com.yehor.finora.domain.money.CurrencyRegistry
import com.yehor.finora.domain.money.Money
import com.yehor.finora.domain.operation.TransactionKind
import com.yehor.finora.domain.recurring.GenerationIssue
import com.yehor.finora.domain.recurring.OccurrenceItem
import com.yehor.finora.domain.recurring.OccurrenceStatus
import com.yehor.finora.domain.recurring.RecurrenceRule
import com.yehor.finora.domain.recurring.RecurrenceUnit
import com.yehor.finora.domain.recurring.RecurringSchedule
import com.yehor.finora.domain.recurring.ScheduleDisplayState
import com.yehor.finora.domain.recurring.ScheduleStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

private val USD = CurrencyRegistry.get("USD")

private fun schedule(
    id: Long,
    name: String = "Rent",
    kind: TransactionKind = TransactionKind.EXPENSE,
    rule: RecurrenceRule = RecurrenceRule(RecurrenceUnit.MONTH, 1),
    start: LocalDate = LocalDate.of(2026, 1, 1),
    end: LocalDate? = null,
    next: LocalDate? = LocalDate.of(2026, 12, 1),
    status: ScheduleStatus = ScheduleStatus.ACTIVE,
) = RecurringSchedule(
    id = id,
    name = name,
    kind = kind,
    account = Account(1, "Main", USD, AccountType.BANK, null, null, null, AccountStatus.ACTIVE, 0),
    money = Money(12_50, USD),
    category = null,
    categoryPath = emptyList(),
    tags = emptyList(),
    rule = rule,
    startDate = start,
    endDate = end,
    nextDate = next,
    status = status,
    createdAt = 0,
    updatedAt = 0,
)

class RecurringListLogicTest {

    @Test
    fun groupsByDisplayState() {
        val sections = RecurringListLogic.sections(
            listOf(
                schedule(1),
                schedule(2, status = ScheduleStatus.PAUSED),
                schedule(3, next = null, end = LocalDate.of(2026, 5, 31)),
            ),
            emptyMap(),
        )
        assertEquals(listOf(1L), sections.active.map { it.schedule.id })
        assertEquals(listOf(2L), sections.paused.map { it.schedule.id })
        assertEquals(listOf(3L), sections.ended.map { it.schedule.id })
        assertEquals(ScheduleDisplayState.ENDED, sections.ended.single().state)
        assertFalse(sections.isEmpty)
    }

    @Test
    fun emptyInputGivesEmptySections() {
        assertTrue(RecurringListLogic.sections(emptyList(), emptyMap()).isEmpty)
    }

    @Test
    fun activeIsSortedByNextDateThenNameThenId() {
        val sections = RecurringListLogic.sections(
            listOf(
                schedule(1, name = "b", next = LocalDate.of(2026, 12, 5)),
                schedule(2, name = "Z", next = LocalDate.of(2026, 12, 1)),
                schedule(3, name = "a", next = LocalDate.of(2026, 12, 5)),
                schedule(4, name = "a", next = LocalDate.of(2026, 12, 5)),
            ),
            emptyMap(),
        )
        assertEquals(listOf(2L, 3L, 4L, 1L), sections.active.map { it.schedule.id })
    }

    @Test
    fun endedIsSortedByEndDateNewestFirst() {
        val sections = RecurringListLogic.sections(
            listOf(
                schedule(1, next = null, end = LocalDate.of(2026, 3, 1)),
                schedule(2, next = null, end = LocalDate.of(2026, 9, 1)),
            ),
            emptyMap(),
        )
        assertEquals(listOf(2L, 1L), sections.ended.map { it.schedule.id })
    }

    @Test
    fun statusLineForActivePausedAndEnded() {
        val sections = RecurringListLogic.sections(
            listOf(
                schedule(1),
                schedule(2, status = ScheduleStatus.PAUSED),
                schedule(3, next = null, end = LocalDate.of(2026, 5, 31)),
            ),
            emptyMap(),
        )
        assertEquals(StatusLine.Next(LocalDate.of(2026, 12, 1)), RecurringListLogic.statusLine(sections.active.single()))
        assertEquals(StatusLine.Paused, RecurringListLogic.statusLine(sections.paused.single()))
        assertEquals(StatusLine.Ended(LocalDate.of(2026, 5, 31)), RecurringListLogic.statusLine(sections.ended.single()))
    }

    @Test
    fun statusLineShowsEachIssueForAnActiveSchedule() {
        GenerationIssue.entries.forEach { issue ->
            val row = RecurringListLogic.sections(listOf(schedule(1)), mapOf(1L to issue)).active.single()
            assertEquals(issue, row.issue)
            assertEquals(StatusLine.Blocked(issue), RecurringListLogic.statusLine(row))
        }
    }

    @Test
    fun issueIsIgnoredWhenPausedOrEnded() {
        val sections = RecurringListLogic.sections(
            listOf(
                schedule(2, status = ScheduleStatus.PAUSED),
                schedule(3, next = null, end = LocalDate.of(2026, 5, 31)),
            ),
            mapOf(2L to GenerationIssue.ACCOUNT_ARCHIVED, 3L to GenerationIssue.BALANCE_OVERFLOW),
        )
        assertNull(sections.paused.single().issue)
        assertNull(sections.ended.single().issue)
        assertEquals(StatusLine.Paused, RecurringListLogic.statusLine(sections.paused.single()))
    }

    @Test
    fun blockedCountCountsOnlyActiveSchedulesWithAnIssue() {
        val sections = RecurringListLogic.sections(
            listOf(
                schedule(1),
                schedule(2),
                schedule(3, status = ScheduleStatus.PAUSED),
            ),
            mapOf(1L to GenerationIssue.ACCOUNT_ARCHIVED, 3L to GenerationIssue.BALANCE_OVERFLOW),
        )
        assertEquals(1, RecurringListLogic.blockedCount(sections))
        assertEquals(0, RecurringListLogic.blockedCount(RecurringSections()))
    }

    @Test
    fun signedAmountUsesPlusForIncomeAndMinusForExpense() {
        val money = Money(12_50, USD)
        assertEquals("+" + money.format(), RecurringListLogic.signedAmount(TransactionKind.INCOME, money))
        assertEquals("−" + money.format(), RecurringListLogic.signedAmount(TransactionKind.EXPENSE, money))
    }
}

class RecurringDetailLogicTest {

    @Test
    fun upcomingListsFiveDatesFromNextDate() {
        val dates = RecurringDetailLogic.upcoming(
            schedule(1, start = LocalDate.of(2026, 1, 31), next = LocalDate.of(2026, 2, 28)),
        )
        assertEquals(
            listOf(
                LocalDate.of(2026, 2, 28),
                LocalDate.of(2026, 3, 31),
                LocalDate.of(2026, 4, 30),
                LocalDate.of(2026, 5, 31),
                LocalDate.of(2026, 6, 30),
            ),
            dates,
        )
    }

    @Test
    fun upcomingHonorsEndDateAndEndedHasNone() {
        val limited = RecurringDetailLogic.upcoming(
            schedule(1, end = LocalDate.of(2027, 1, 15), next = LocalDate.of(2026, 12, 1)),
        )
        assertEquals(listOf(LocalDate.of(2026, 12, 1), LocalDate.of(2027, 1, 1)), limited)
        assertTrue(RecurringDetailLogic.upcoming(schedule(2, next = null, end = LocalDate.of(2026, 5, 31))).isEmpty())
    }

    @Test
    fun upcomingIsShownWhilePaused() {
        val dates = RecurringDetailLogic.upcoming(schedule(1, status = ScheduleStatus.PAUSED))
        assertEquals(RecurringDetailLogic.UPCOMING_COUNT, dates.size)
    }

    @Test
    fun resumeNeedsNoChoiceWhenNothingCameDue() {
        val paused = schedule(1, status = ScheduleStatus.PAUSED, next = LocalDate.of(2026, 12, 1))
        assertNull(RecurringDetailLogic.resumeInfo(paused, today = LocalDate.of(2026, 11, 30)))
        assertNull(RecurringDetailLogic.resumeInfo(schedule(2, next = null), today = LocalDate.of(2026, 11, 30)))
    }

    @Test
    fun resumeCountsWhatCameDueAndNamesTheContinuation() {
        val paused = schedule(
            1,
            rule = RecurrenceRule(RecurrenceUnit.WEEK, 1),
            start = LocalDate.of(2026, 11, 6),
            next = LocalDate.of(2026, 11, 6),
            status = ScheduleStatus.PAUSED,
        )
        val info = RecurringDetailLogic.resumeInfo(paused, today = LocalDate.of(2026, 11, 22))!!
        assertEquals(3, info.count)
        assertEquals(LocalDate.of(2026, 11, 6), info.first)
        assertEquals(LocalDate.of(2026, 11, 20), info.last)
        assertEquals(LocalDate.of(2026, 11, 27), info.continueFrom)
    }

    @Test
    fun resumeEndsTheScheduleWhenTheEndDatePassed() {
        val paused = schedule(
            1,
            rule = RecurrenceRule(RecurrenceUnit.DAY, 1),
            start = LocalDate.of(2026, 11, 1),
            next = LocalDate.of(2026, 11, 1),
            end = LocalDate.of(2026, 11, 3),
            status = ScheduleStatus.PAUSED,
        )
        val info = RecurringDetailLogic.resumeInfo(paused, today = LocalDate.of(2026, 12, 1))!!
        assertEquals(3, info.count)
        assertEquals(LocalDate.of(2026, 11, 3), info.last)
        assertNull(info.continueFrom)
    }

    @Test
    fun historyMapsEachOccurrenceKind() {
        val generated = OccurrenceItem(LocalDate.of(2026, 3, 1), OccurrenceStatus.GENERATED, 7, 0, Money(-12_50, USD))
        val deleted = OccurrenceItem(LocalDate.of(2026, 2, 1), OccurrenceStatus.GENERATED, null, 0)
        val skipped = OccurrenceItem(LocalDate.of(2026, 1, 1), OccurrenceStatus.SKIPPED, null, 0)
        val view = RecurringDetailLogic.history(listOf(generated, deleted, skipped))
        assertEquals(
            listOf(
                HistoryRow.Generated(LocalDate.of(2026, 3, 1), 7, Money(-12_50, USD)),
                HistoryRow.Deleted(LocalDate.of(2026, 2, 1)),
                HistoryRow.Skipped(LocalDate.of(2026, 1, 1)),
            ),
            view.rows,
        )
        assertFalse(view.hasMore)
    }

    @Test
    fun historyShowsTheLatestHundredAndSaysWhenMoreExist() {
        fun item(i: Int) = OccurrenceItem(LocalDate.of(2026, 1, 1).plusDays(i.toLong()), OccurrenceStatus.SKIPPED, null, 0)
        val exactly = RecurringDetailLogic.history((0 until RecurringDetailLogic.HISTORY_LIMIT).map(::item))
        assertEquals(RecurringDetailLogic.HISTORY_LIMIT, exactly.rows.size)
        assertFalse(exactly.hasMore)

        val more = RecurringDetailLogic.history((0..RecurringDetailLogic.HISTORY_LIMIT).map(::item))
        assertEquals(RecurringDetailLogic.HISTORY_LIMIT, more.rows.size)
        assertTrue(more.hasMore)
    }
}
