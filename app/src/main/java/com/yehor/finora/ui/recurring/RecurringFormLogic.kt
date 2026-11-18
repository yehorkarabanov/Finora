package com.yehor.finora.ui.recurring

import com.yehor.finora.data.repo.CurrencyChangeNotConfirmedException
import com.yehor.finora.data.repo.OpeningBalanceOrderException
import com.yehor.finora.data.repo.ScheduleBlockedException
import com.yehor.finora.domain.money.AmountParseResult
import com.yehor.finora.domain.money.AmountParser
import com.yehor.finora.domain.money.Currency
import com.yehor.finora.domain.money.Money
import com.yehor.finora.domain.money.MoneyOverflowException
import com.yehor.finora.domain.operation.TransactionKind
import com.yehor.finora.domain.recurring.RecurrenceCalculator
import com.yehor.finora.domain.recurring.RecurrenceRule
import com.yehor.finora.domain.recurring.RecurrenceUnit
import com.yehor.finora.domain.recurring.ScheduleDates
import com.yehor.finora.domain.recurring.ScheduleDraft
import com.yehor.finora.domain.recurring.ScheduleDraftError
import com.yehor.finora.domain.recurring.ScheduleDraftValidator
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// ---- Frequency input (design 7.3) ----

enum class FrequencyPreset { DAILY, WEEKLY, MONTHLY, YEARLY, CUSTOM }

/** What the user picked: a preset chip, or "Every [N] [unit]" for Custom. */
data class FrequencyInput(
    val preset: FrequencyPreset = FrequencyPreset.MONTHLY,
    val customUnit: RecurrenceUnit = RecurrenceUnit.MONTH,
    val customIntervalText: String = "2",
) {
    /** An unparsable or out-of-range custom number gives interval 0, which the validator reports. */
    fun rule(): RecurrenceRule = when (preset) {
        FrequencyPreset.DAILY -> RecurrenceRule(RecurrenceUnit.DAY, 1)
        FrequencyPreset.WEEKLY -> RecurrenceRule(RecurrenceUnit.WEEK, 1)
        FrequencyPreset.MONTHLY -> RecurrenceRule(RecurrenceUnit.MONTH, 1)
        FrequencyPreset.YEARLY -> RecurrenceRule(RecurrenceUnit.YEAR, 1)
        FrequencyPreset.CUSTOM -> RecurrenceRule(customUnit, customIntervalText.trim().toIntOrNull() ?: 0)
    }

    fun select(newPreset: FrequencyPreset): FrequencyInput {
        if (newPreset != FrequencyPreset.CUSTOM) return copy(preset = newPreset)
        if (preset == FrequencyPreset.CUSTOM) return this
        // Start Custom from the unit that was selected, so "Every 2 [weeks]" follows "Weekly".
        return copy(preset = FrequencyPreset.CUSTOM, customUnit = rule().unit, customIntervalText = "2")
    }

    companion object {
        /** Interval 1 is the preset of its unit; anything else is Custom. */
        fun of(rule: RecurrenceRule): FrequencyInput {
            if (rule.interval == 1) {
                val preset = when (rule.unit) {
                    RecurrenceUnit.DAY -> FrequencyPreset.DAILY
                    RecurrenceUnit.WEEK -> FrequencyPreset.WEEKLY
                    RecurrenceUnit.MONTH -> FrequencyPreset.MONTHLY
                    RecurrenceUnit.YEAR -> FrequencyPreset.YEARLY
                }
                return FrequencyInput(preset = preset, customUnit = rule.unit)
            }
            return FrequencyInput(FrequencyPreset.CUSTOM, rule.unit, rule.interval.toString())
        }
    }
}

// ---- Form state ----

/** The stored schedule an edit starts from. */
data class OriginalSchedule(
    val accountId: Long,
    val currency: Currency,
    val money: Money,
    val anchor: ScheduleDates.Anchor,
    val lastHandled: LocalDate?,
    /** A paused schedule creates nothing while saving (design 5.1 R8). */
    val active: Boolean,
)

data class RecurringFormState(
    val kind: TransactionKind = TransactionKind.EXPENSE,
    val name: String = "",
    val amountText: String = "",
    val accountId: Long? = null,
    val categoryId: Long? = null,
    val hadCategoryBefore: Boolean = true,
    val selectedTagIds: Set<Long> = emptySet(),
    val pendingTagNames: List<String> = emptyList(),
    val frequency: FrequencyInput = FrequencyInput(),
    val firstDate: LocalDate = LocalDate.now(),
    val endDate: LocalDate? = null,
    /** A schedule on an archived account creates nothing while saving (design 5.1 R12). */
    val accountArchived: Boolean = false,
    /** Null when creating. */
    val original: OriginalSchedule? = null,
    val scheduleId: Long = 0,
) {
    val isEdit: Boolean get() = original != null

    /** The type cannot change once a schedule exists. */
    val typeLocked: Boolean get() = isEdit
}

// ---- Preview ----

sealed interface FormPreview {
    data object None : FormPreview
    data class Next(val date: LocalDate) : FormPreview
    data class Due(val count: Int, val first: LocalDate, val last: LocalDate) : FormPreview
}

// ---- Save gates ----

data class CurrencyChangeInfo(val newFormatted: String, val oldFormatted: String)

sealed interface SaveGate {
    data class CurrencyChange(val info: CurrencyChangeInfo) : SaveGate
    data class PastOccurrences(val count: Int, val first: LocalDate, val last: LocalDate) : SaveGate
}

data class SaveConfirmations(
    val currencyChange: Boolean = false,
    val pastOccurrences: Boolean = false,
    val moveOpeningBalanceBack: Boolean = false,
)

sealed interface SaveResult {
    data object Saved : SaveResult

    /** Ask the user; repeat with the matching confirmation set. Nothing was written. */
    data class Gate(val gate: SaveGate) : SaveResult

    /** The Accounts 4.6 dialog; confirming repeats with `moveOpeningBalanceBack = true`. */
    data object OpeningOrder : SaveResult

    data object Overflow : SaveResult
    data object AccountArchived : SaveResult
    data object Invalid : SaveResult
}

/** The two writes the form needs; the ViewModel adapts `RecurringRepository` to it. */
interface ScheduleWriter {
    suspend fun create(draft: ScheduleDraft, moveOpeningBalanceBack: Boolean)
    suspend fun update(
        id: Long,
        draft: ScheduleDraft,
        moveOpeningBalanceBack: Boolean,
        confirmCurrencyChange: Boolean,
    )
}

object RecurringFormLogic {

    fun rule(state: RecurringFormState): RecurrenceRule = state.frequency.rule()

    fun errors(
        state: RecurringFormState,
        currency: Currency,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Set<ScheduleDraftError> = ScheduleDraftValidator.validate(
        name = state.name,
        amountText = state.amountText,
        currency = currency,
        accountId = state.accountId,
        categoryId = state.categoryId,
        rule = rule(state),
        firstDate = state.firstDate,
        endDate = state.endDate,
        now = now,
        zone = zone,
        isEdit = state.isEdit,
        hadCategoryBefore = state.hadCategoryBefore,
    )

    /**
     * An edit that changes the rule or the next date must start after the last handled occurrence.
     * Returns that last handled date when it does not, so the form can say so.
     */
    fun handledConflict(state: RecurringFormState): LocalDate? {
        val original = state.original ?: return null
        val rule = rule(state)
        if (rule.interval !in 1..365) return null
        val result = ScheduleDates.reschedule(
            original.anchor,
            ScheduleDates.Edited(state.firstDate, rule, state.endDate),
            original.lastHandled,
        )
        return if (result is ScheduleDates.Result.Rejected) original.lastHandled else null
    }

    fun canSave(state: RecurringFormState, currency: Currency, now: Long, zone: ZoneId): Boolean =
        state.accountId != null &&
            errors(state, currency, now, zone).isEmpty() &&
            handledConflict(state) == null

    /** The anchor and the next date the schedule has after saving, or null when the input is not valid yet. */
    private fun plan(state: RecurringFormState): ScheduleDates.Result.Accepted? {
        val rule = rule(state)
        if (rule.interval !in 1..365) return null
        val original = state.original
            ?: return ScheduleDates.Result.Accepted(startDate = state.firstDate, nextDate = state.firstDate)
        return ScheduleDates.reschedule(
            original.anchor,
            ScheduleDates.Edited(state.firstDate, rule, state.endDate),
            original.lastHandled,
        ) as? ScheduleDates.Result.Accepted
    }

    /** Occurrences that saving creates right away: dated today or earlier (design 5.3). */
    fun preview(state: RecurringFormState, today: LocalDate): FormPreview {
        val plan = plan(state) ?: return FormPreview.None
        val next = plan.nextDate ?: return FormPreview.None
        val generates = (state.original?.active ?: true) && !state.accountArchived
        if (generates && next <= today) {
            val to = state.endDate?.let { minOf(it, today) } ?: today
            val due = RecurrenceCalculator.between(
                plan.startDate,
                rule(state),
                next,
                to,
                ScheduleDraftValidator.MAX_PAST_OCCURRENCES + 1,
            )
            if (due.isNotEmpty()) return FormPreview.Due(due.size, due.first(), due.last())
        }
        return FormPreview.Next(next)
    }

    fun today(now: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()

    /** The draft to write, or null when the amount is not a valid positive number in [currency]. */
    fun draft(state: RecurringFormState, currency: Currency): ScheduleDraft? {
        val accountId = state.accountId ?: return null
        val amount = AmountParser.parse(state.amountText, currency) as? AmountParseResult.Value ?: return null
        return ScheduleDraft(
            name = state.name,
            kind = state.kind,
            accountId = accountId,
            amountMinor = amount.minor,
            categoryId = state.categoryId,
            tagIds = state.selectedTagIds,
            newTagNames = state.pendingTagNames,
            rule = rule(state),
            firstDate = state.firstDate,
            endDate = state.endDate,
        )
    }

    /** Only an edit that moves the schedule to an account with another currency asks (design 7.3 gate 1). */
    fun currencyChange(state: RecurringFormState, newCurrency: Currency, amountMinor: Long): CurrencyChangeInfo? {
        val original = state.original ?: return null
        if (original.currency.code == newCurrency.code) return null
        return CurrencyChangeInfo(
            newFormatted = "${Money(amountMinor, newCurrency).toPlainString()} ${newCurrency.code}",
            oldFormatted = "${original.money.toPlainString()} ${original.money.currency.code}",
        )
    }

    /** The first gate that still needs an answer, in the order of the design: currency, then past occurrences. */
    fun pendingGate(
        state: RecurringFormState,
        currency: Currency,
        amountMinor: Long,
        today: LocalDate,
        confirmed: SaveConfirmations,
    ): SaveGate? {
        if (!confirmed.currencyChange) {
            currencyChange(state, currency, amountMinor)?.let { return SaveGate.CurrencyChange(it) }
        }
        if (!confirmed.pastOccurrences) {
            val preview = preview(state, today)
            if (preview is FormPreview.Due) return SaveGate.PastOccurrences(preview.count, preview.first, preview.last)
        }
        return null
    }

    /**
     * Runs the gates and then the write. Nothing is written until every gate has been answered;
     * the opening-order question comes from the write itself and rolls back with it.
     */
    suspend fun save(
        state: RecurringFormState,
        currency: Currency,
        now: Long,
        zone: ZoneId,
        confirmed: SaveConfirmations,
        writer: ScheduleWriter,
    ): SaveResult {
        if (!canSave(state, currency, now, zone)) return SaveResult.Invalid
        val draft = draft(state, currency) ?: return SaveResult.Invalid
        pendingGate(state, currency, draft.amountMinor, today(now, zone), confirmed)?.let {
            return SaveResult.Gate(it)
        }
        return try {
            if (state.isEdit) {
                writer.update(
                    id = state.scheduleId,
                    draft = draft,
                    moveOpeningBalanceBack = confirmed.moveOpeningBalanceBack,
                    confirmCurrencyChange = confirmed.currencyChange,
                )
            } else {
                writer.create(draft, confirmed.moveOpeningBalanceBack)
            }
            SaveResult.Saved
        } catch (_: OpeningBalanceOrderException) {
            SaveResult.OpeningOrder
        } catch (_: MoneyOverflowException) {
            SaveResult.Overflow
        } catch (_: ScheduleBlockedException) {
            SaveResult.AccountArchived
        } catch (_: CurrencyChangeNotConfirmedException) {
            // The gate above already asked; this only happens if the stored account changed underneath the form.
            SaveResult.Invalid
        }
    }
}
