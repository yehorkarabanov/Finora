package com.yehor.finora.domain.recurring

import com.yehor.finora.domain.account.Account
import com.yehor.finora.domain.category.Category
import com.yehor.finora.domain.money.Money
import com.yehor.finora.domain.operation.TransactionKind
import com.yehor.finora.domain.tag.Tag
import java.time.LocalDate

enum class RecurrenceUnit { DAY, WEEK, MONTH, YEAR }

data class RecurrenceRule(
    val unit: RecurrenceUnit,
    val interval: Int, // interval in 1..365
)

enum class ScheduleStatus { ACTIVE, PAUSED }

enum class OccurrenceStatus { GENERATED, SKIPPED }

data class ScheduleDraft(
    val name: String,
    val kind: TransactionKind,
    val accountId: Long,
    val amountMinor: Long, // positive, in the account's currency
    val categoryId: Long?,
    val tagIds: Set<Long>,
    val newTagNames: List<String>,
    val rule: RecurrenceRule,
    val firstDate: LocalDate, // create: "Starts on". edit: "Next occurrence"
    val endDate: LocalDate?,
)

data class RecurringSchedule(
    val id: Long,
    val name: String,
    val kind: TransactionKind,
    val account: Account,
    val money: Money, // positive amount in account currency
    val category: Category?,
    val categoryPath: List<Category>,
    val tags: List<Tag>,
    val rule: RecurrenceRule,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val nextDate: LocalDate?,
    val status: ScheduleStatus,
    val createdAt: Long,
    val updatedAt: Long,
)

data class OccurrenceItem(
    val date: LocalDate,
    val status: OccurrenceStatus,
    val operationId: Long?,
    val handledAt: Long,
    /** Signed ledger amount of the generated transaction, null when there is none. */
    val money: Money? = null,
)

enum class GenerationIssue {
    ACCOUNT_ARCHIVED,
    BEFORE_OPENING_BALANCE,
    BALANCE_OVERFLOW,
}

enum class ScheduleDisplayState {
    ACTIVE,
    PAUSED,
    ENDED,
}

fun RecurringSchedule.displayState(): ScheduleDisplayState = when {
    nextDate == null -> ScheduleDisplayState.ENDED
    status == ScheduleStatus.PAUSED -> ScheduleDisplayState.PAUSED
    else -> ScheduleDisplayState.ACTIVE
}
