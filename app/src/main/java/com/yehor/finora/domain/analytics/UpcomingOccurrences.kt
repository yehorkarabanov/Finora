package com.yehor.finora.domain.analytics

import com.yehor.finora.domain.recurring.RecurrenceCalculator
import com.yehor.finora.domain.recurring.RecurringSchedule
import com.yehor.finora.domain.recurring.ScheduleDisplayState
import com.yehor.finora.domain.recurring.displayState
import java.time.LocalDate

data class UpcomingItem(val schedule: RecurringSchedule, val date: LocalDate)

object UpcomingOccurrences {

    /**
     * Next occurrences of active schedules from their `nextDate` up to [today] + [horizonDays],
     * ordered by date, at most [limit]. Paused, ended and [blocked] schedules are left out.
     */
    fun build(
        schedules: List<RecurringSchedule>,
        blocked: Set<Long>,
        today: LocalDate,
        horizonDays: Int = 30,
        limit: Int = 5,
    ): List<UpcomingItem> {
        val horizonEnd = today.plusDays(horizonDays.toLong())
        return schedules
            .asSequence()
            .filter { it.displayState() == ScheduleDisplayState.ACTIVE && it.id !in blocked }
            .flatMap { schedule ->
                val start = schedule.nextDate ?: return@flatMap emptySequence()
                val end = schedule.endDate?.let { minOf(it, horizonEnd) } ?: horizonEnd
                RecurrenceCalculator.between(schedule.startDate, schedule.rule, start, end, limit)
                    .asSequence()
                    .map { UpcomingItem(schedule, it) }
            }
            .sortedWith(compareBy<UpcomingItem> { it.date }.thenBy { it.schedule.id })
            .take(limit)
            .toList()
    }
}
