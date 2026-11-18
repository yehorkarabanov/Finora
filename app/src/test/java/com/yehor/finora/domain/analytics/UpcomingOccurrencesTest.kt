package com.yehor.finora.domain.analytics

import com.yehor.finora.domain.account.Account
import com.yehor.finora.domain.account.AccountType
import com.yehor.finora.domain.model.AccountStatus
import com.yehor.finora.domain.money.CurrencyRegistry
import com.yehor.finora.domain.money.Money
import com.yehor.finora.domain.operation.TransactionKind
import com.yehor.finora.domain.recurring.RecurrenceRule
import com.yehor.finora.domain.recurring.RecurrenceUnit
import com.yehor.finora.domain.recurring.RecurringSchedule
import com.yehor.finora.domain.recurring.ScheduleStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class UpcomingOccurrencesTest {

    private val usd = CurrencyRegistry.get("USD")
    private val today = LocalDate.of(2026, 11, 7)

    private fun schedule(
        id: Long,
        rule: RecurrenceRule = RecurrenceRule(RecurrenceUnit.MONTH, 1),
        start: LocalDate = LocalDate.of(2026, 1, 15),
        end: LocalDate? = null,
        next: LocalDate? = LocalDate.of(2026, 11, 15),
        status: ScheduleStatus = ScheduleStatus.ACTIVE,
    ) = RecurringSchedule(
        id = id,
        name = "S$id",
        kind = TransactionKind.EXPENSE,
        account = Account(1, "Main", usd, AccountType.BANK, null, null, null, AccountStatus.ACTIVE, 0),
        money = Money(1000, usd),
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

    private fun build(vararg s: RecurringSchedule, blocked: Set<Long> = emptySet(), limit: Int = 5) =
        UpcomingOccurrences.build(s.toList(), blocked, today, horizonDays = 30, limit = limit)

    @Test
    fun monthlyScheduleHasOneOccurrenceInThirtyDays() {
        val items = build(schedule(1))
        assertEquals(listOf(LocalDate.of(2026, 11, 15)), items.map { it.date })
    }

    @Test
    fun dailyScheduleIsCappedByTheLimit() {
        val items = build(schedule(1, rule = RecurrenceRule(RecurrenceUnit.DAY, 1), start = today, next = today))
        assertEquals(5, items.size)
        assertEquals(today, items.first().date)
    }

    @Test
    fun orderedAcrossSchedulesByDateThenId() {
        val items = build(
            schedule(2, next = LocalDate.of(2026, 11, 20), start = LocalDate.of(2026, 11, 20)),
            schedule(1, next = LocalDate.of(2026, 11, 20), start = LocalDate.of(2026, 11, 20)),
            schedule(3, next = LocalDate.of(2026, 11, 10), start = LocalDate.of(2026, 11, 10)),
        )
        assertEquals(listOf(3L, 1L, 2L), items.map { it.schedule.id })
    }

    @Test
    fun occurrenceBeyondTheHorizonIsLeftOut() {
        // Horizon ends 7 Dec; 8 Dec is outside.
        val items = build(schedule(1, next = LocalDate.of(2026, 12, 8), start = LocalDate.of(2026, 12, 8)))
        assertTrue(items.isEmpty())
    }

    @Test
    fun occurrenceOnTheLastHorizonDayIsIncluded() {
        val items = build(schedule(1, next = LocalDate.of(2026, 12, 7), start = LocalDate.of(2026, 12, 7)))
        assertEquals(1, items.size)
    }

    @Test
    fun pausedEndedAndBlockedSchedulesAreExcluded() {
        val items = build(
            schedule(1, status = ScheduleStatus.PAUSED),
            schedule(2, next = null, end = LocalDate.of(2026, 10, 1)),
            schedule(3),
            schedule(4),
            blocked = setOf(3L),
        )
        assertEquals(listOf(4L), items.map { it.schedule.id })
    }

    @Test
    fun endDateStopsOccurrences() {
        val items = build(
            schedule(
                1,
                rule = RecurrenceRule(RecurrenceUnit.WEEK, 1),
                start = LocalDate.of(2026, 11, 9),
                next = LocalDate.of(2026, 11, 9),
                end = LocalDate.of(2026, 11, 20),
            ),
        )
        assertEquals(listOf(LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 16)), items.map { it.date })
    }

    @Test
    fun monthEndAnchoringKeepsTheAnchorDay() {
        // Anchored on 31 Jan: each month uses the anchor, so 31 Oct then 30 Nov (clamped) then 31 Dec.
        val items = UpcomingOccurrences.build(
            listOf(
                schedule(
                    1,
                    start = LocalDate.of(2026, 1, 31),
                    next = LocalDate.of(2026, 11, 30),
                ),
            ),
            emptySet(),
            LocalDate.of(2026, 11, 7),
            horizonDays = 60,
            limit = 5,
        )
        assertEquals(listOf(LocalDate.of(2026, 11, 30), LocalDate.of(2026, 12, 31)), items.map { it.date })
    }
}
