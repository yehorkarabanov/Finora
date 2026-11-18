package com.yehor.finora.ui.recurring

import com.yehor.finora.domain.operation.TransactionKind
import com.yehor.finora.domain.recurring.ScheduleDraftError
import java.time.LocalDate

/** Why the bottom button of the schedule form is disabled. */
enum class RecurringCtaMissing {
    LOADING,
    ENTER_AMOUNT,
    CHOOSE_ACCOUNT,
    ENTER_NAME,
    CHOOSE_CATEGORY,
    FIX_ERRORS,
}

/** State of the bottom button: ready to save, or disabled with a reason. */
sealed interface RecurringCta {
    data class Ready(val isEdit: Boolean, val kind: TransactionKind) : RecurringCta
    data class Missing(val reason: RecurringCtaMissing) : RecurringCta
}

object RecurringCtaLogic {
    /**
     * Mirrors `RecurringEditUiState.canSave`: [RecurringCta.Ready] exactly when not loading, an account is chosen,
     * there are no validation errors and no conflict with handled occurrences. Reasons follow the order in which
     * the form is filled: amount, account, name, category, everything else.
     */
    fun cta(
        loading: Boolean,
        accountId: Long?,
        amountText: String,
        errors: Set<ScheduleDraftError>,
        handledConflict: LocalDate?,
        isEdit: Boolean,
        kind: TransactionKind,
    ): RecurringCta = when {
        loading -> RecurringCta.Missing(RecurringCtaMissing.LOADING)
        amountText.isBlank() || ScheduleDraftError.AMOUNT_EMPTY in errors ->
            RecurringCta.Missing(RecurringCtaMissing.ENTER_AMOUNT)
        accountId == null -> RecurringCta.Missing(RecurringCtaMissing.CHOOSE_ACCOUNT)
        ScheduleDraftError.NAME_EMPTY in errors -> RecurringCta.Missing(RecurringCtaMissing.ENTER_NAME)
        ScheduleDraftError.CATEGORY_MISSING in errors -> RecurringCta.Missing(RecurringCtaMissing.CHOOSE_CATEGORY)
        errors.isNotEmpty() || handledConflict != null -> RecurringCta.Missing(RecurringCtaMissing.FIX_ERRORS)
        else -> RecurringCta.Ready(isEdit, kind)
    }

    fun cta(state: RecurringEditUiState): RecurringCta = cta(
        loading = state.loading,
        accountId = state.form.accountId,
        amountText = state.form.amountText,
        errors = state.errors,
        handledConflict = state.handledConflict,
        isEdit = state.isEdit,
        kind = state.form.kind,
    )
}
