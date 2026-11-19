package com.yehor.finora.ui.recurring

import com.yehor.finora.domain.money.Money
import com.yehor.finora.domain.operation.TransactionKind
import com.yehor.finora.domain.recurring.GenerationIssue
import com.yehor.finora.domain.recurring.OccurrenceItem
import com.yehor.finora.domain.recurring.OccurrenceStatus
import com.yehor.finora.domain.recurring.RecurrenceCalculator
import com.yehor.finora.domain.recurring.RecurringSchedule
import com.yehor.finora.domain.recurring.ScheduleDisplayState
import com.yehor.finora.domain.recurring.ScheduleDraftValidator
import com.yehor.finora.domain.recurring.displayState
import java.time.LocalDate

// ---- List (design 7.1) ----

data class RecurringRow(
    val schedule: RecurringSchedule,
    val state: ScheduleDisplayState,
    /** Only an active schedule can be blocked; a paused or ended one creates nothing anyway. */
    val issue: GenerationIssue?,
)

data class RecurringSections(
    val active: List<RecurringRow> = emptyList(),
    val paused: List<RecurringRow> = emptyList(),
    val ended: List<RecurringRow> = emptyList(),
) {
    val isEmpty: Boolean get() = active.isEmpty() && paused.isEmpty() && ended.isEmpty()
}

sealed interface StatusLine {
    data class Next(val date: LocalDate) : StatusLine
    data object Paused : StatusLine
    data class Ended(val date: LocalDate) : StatusLine
    data class Blocked(val issue: GenerationIssue) : StatusLine
}

object RecurringListLogic {

    fun sections(schedules: List<RecurringSchedule>, issues: Map<Long, GenerationIssue>): RecurringSections {
        val rows = schedules.map { schedule ->
            val state = schedule.displayState()
            RecurringRow(schedule, state, if (state == ScheduleDisplayState.ACTIVE) issues[schedule.id] else null)
        }
        val byNextDate = compareBy<RecurringRow>(
            { it.schedule.nextDate ?: LocalDate.MAX },
            { it.schedule.name.lowercase() },
            { it.schedule.id },
        )
        val byEndDateDescending = compareByDescending<RecurringRow> { it.schedule.endDate ?: it.schedule.startDate }
            .thenBy { it.schedule.name.lowercase() }
            .thenBy { it.schedule.id }
        return RecurringSections(
            active = rows.filter { it.state == ScheduleDisplayState.ACTIVE }.sortedWith(byNextDate),
            paused = rows.filter { it.state == ScheduleDisplayState.PAUSED }.sortedWith(byNextDate),
            ended = rows.filter { it.state == ScheduleDisplayState.ENDED }.sortedWith(byEndDateDescending),
        )
    }

    fun statusLine(row: RecurringRow): StatusLine = when (row.state) {
        ScheduleDisplayState.ACTIVE ->
            row.issue?.let { StatusLine.Blocked(it) }
                ?: StatusLine.Next(row.schedule.nextDate ?: row.schedule.startDate)
        ScheduleDisplayState.PAUSED -> StatusLine.Paused
        ScheduleDisplayState.ENDED -> StatusLine.Ended(row.schedule.endDate ?: row.schedule.startDate)
    }

    /** How many schedules are blocked by a generation issue (shown as the attention banner). */
    fun blockedCount(sections: RecurringSections): Int =
        sections.active.count { statusLine(it) is StatusLine.Blocked }

    /** "+12.50 $" for income, "−12.50 $" for expense. The schedule amount itself is always positive. */
    fun signedAmount(kind: TransactionKind, money: Money): String =
        (if (kind == TransactionKind.INCOME) "+" else "−") + money.format()
}

// ---- Detail (design 7.2) ----

data class ResumeInfo(
    val count: Int,
    val first: LocalDate,
    val last: LocalDate,
    /** The first date after the skipped ones, null when the schedule ends with them. */
    val continueFrom: LocalDate?,
)

sealed interface HistoryRow {
    val date: LocalDate

    data class Generated(override val date: LocalDate, val operationId: Long, val money: Money?) : HistoryRow

    /** The occurrence was handled, but its transaction was deleted later (R7). */
    data class Deleted(override val date: LocalDate) : HistoryRow

    data class Skipped(override val date: LocalDate) : HistoryRow
}

data class HistoryView(val rows: List<HistoryRow>, val hasMore: Boolean)

object RecurringDetailLogic {
    const val UPCOMING_COUNT = 5
    const val HISTORY_LIMIT = 100
    private const val RESUME_COUNT_LIMIT = 100_000

    /** The next dates from `next_date`, honoring the end date. Also meaningful while paused. */
    fun upcoming(schedule: RecurringSchedule): List<LocalDate> {
        val next = schedule.nextDate ?: return emptyList()
        val to = schedule.endDate ?: ScheduleDraftValidator.MAX_DATE
        return RecurrenceCalculator.between(schedule.startDate, schedule.rule, next, to, UPCOMING_COUNT)
    }

    /** What came due while the schedule was paused, or null when nothing did (no choice is needed). */
    fun resumeInfo(schedule: RecurringSchedule, today: LocalDate): ResumeInfo? {
        val next = schedule.nextDate ?: return null
        if (next > today) return null
        val to = schedule.endDate?.let { minOf(it, today) } ?: today
        val due = RecurrenceCalculator.between(schedule.startDate, schedule.rule, next, to, RESUME_COUNT_LIMIT)
        if (due.isEmpty()) return null
        val last = due.last()
        return ResumeInfo(
            count = due.size,
            first = due.first(),
            last = last,
            continueFrom = RecurrenceCalculator.next(schedule.startDate, schedule.rule, last, schedule.endDate),
        )
    }

    /** [items] come newest first; one more than [HISTORY_LIMIT] is enough to know that more exist. */
    fun history(items: List<OccurrenceItem>): HistoryView = HistoryView(
        rows = items.take(HISTORY_LIMIT).map(::historyRow),
        hasMore = items.size > HISTORY_LIMIT,
    )

    private fun historyRow(item: OccurrenceItem): HistoryRow = when {
        item.status == OccurrenceStatus.SKIPPED -> HistoryRow.Skipped(item.date)
        item.operationId == null -> HistoryRow.Deleted(item.date)
        else -> HistoryRow.Generated(item.date, item.operationId, item.money)
    }
}
