package com.yehor.finora.domain.recurring

import java.time.LocalDate

object ScheduleDates {
    data class Anchor(
        val startDate: LocalDate,
        val rule: RecurrenceRule,
        val endDate: LocalDate?,
        val nextDate: LocalDate?,
    )

    data class Edited(
        val firstDate: LocalDate,
        val rule: RecurrenceRule,
        val endDate: LocalDate?,
    )

    sealed interface Result {
        data class Accepted(val startDate: LocalDate, val nextDate: LocalDate?) : Result
        data class Rejected(val reason: Reason) : Result

        enum class Reason {
            NEXT_NOT_AFTER_HANDLED,
        }
    }

    fun reschedule(current: Anchor, edited: Edited, lastHandled: LocalDate?): Result {
        val ruleChanged = current.rule != edited.rule
        val dateChanged = if (current.nextDate != null) {
            current.nextDate != edited.firstDate
        } else {
            val naturalNext = if (lastHandled != null) {
                RecurrenceCalculator.firstAfter(current.startDate, current.rule, lastHandled)
            } else {
                current.startDate
            }
            edited.firstDate != naturalNext
        }

        if (ruleChanged || dateChanged) {
            if (lastHandled != null && edited.firstDate <= lastHandled) {
                return Result.Rejected(Result.Reason.NEXT_NOT_AFTER_HANDLED)
            }
            val nextDate = if (edited.endDate != null && edited.firstDate > edited.endDate) {
                null
            } else {
                edited.firstDate
            }
            return Result.Accepted(startDate = edited.firstDate, nextDate = nextDate)
        } else {
            val anchor = current.startDate
            val next = if (lastHandled != null) {
                RecurrenceCalculator.firstAfter(anchor, current.rule, lastHandled)
            } else {
                current.nextDate ?: current.startDate
            }
            val finalNextDate = if (edited.endDate != null && next > edited.endDate) {
                null
            } else {
                next
            }
            return Result.Accepted(startDate = anchor, nextDate = finalNextDate)
        }
    }
}
