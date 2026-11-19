package com.yehor.finora.ui.recurring

import com.yehor.finora.domain.money.CurrencyRegistry
import com.yehor.finora.domain.operation.TransactionKind
import com.yehor.finora.domain.recurring.ScheduleDraftError
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class RecurringCtaLogicTest {

    private fun cta(
        loading: Boolean = false,
        accountId: Long? = 1L,
        amountText: String = "12.50",
        errors: Set<ScheduleDraftError> = emptySet(),
        handledConflict: LocalDate? = null,
        isEdit: Boolean = false,
        kind: TransactionKind = TransactionKind.EXPENSE,
    ) = RecurringCtaLogic.cta(loading, accountId, amountText, errors, handledConflict, isEdit, kind)

    private fun missing(reason: RecurringCtaMissing) = RecurringCta.Missing(reason)

    @Test
    fun readyCarriesModeAndKind() {
        assertEquals(RecurringCta.Ready(false, TransactionKind.EXPENSE), cta())
        assertEquals(
            RecurringCta.Ready(true, TransactionKind.INCOME),
            cta(isEdit = true, kind = TransactionKind.INCOME),
        )
    }

    @Test
    fun loadingComesFirst() {
        val all = setOf(ScheduleDraftError.AMOUNT_EMPTY, ScheduleDraftError.NAME_EMPTY)
        assertEquals(missing(RecurringCtaMissing.LOADING), cta(loading = true, accountId = null, amountText = "", errors = all))
    }

    @Test
    fun emptyAmountBeforeAccountAndName() {
        val errors = setOf(ScheduleDraftError.AMOUNT_EMPTY, ScheduleDraftError.NAME_EMPTY, ScheduleDraftError.CATEGORY_MISSING)
        assertEquals(missing(RecurringCtaMissing.ENTER_AMOUNT), cta(accountId = null, amountText = "", errors = errors))
    }

    @Test
    fun missingAccountBeforeName() {
        val errors = setOf(ScheduleDraftError.ACCOUNT_MISSING, ScheduleDraftError.NAME_EMPTY)
        assertEquals(missing(RecurringCtaMissing.CHOOSE_ACCOUNT), cta(accountId = null, errors = errors))
    }

    @Test
    fun missingNameBeforeCategory() {
        val errors = setOf(ScheduleDraftError.NAME_EMPTY, ScheduleDraftError.CATEGORY_MISSING)
        assertEquals(missing(RecurringCtaMissing.ENTER_NAME), cta(errors = errors))
    }

    @Test
    fun missingCategory() {
        assertEquals(
            missing(RecurringCtaMissing.CHOOSE_CATEGORY),
            cta(errors = setOf(ScheduleDraftError.CATEGORY_MISSING)),
        )
    }

    @Test
    fun otherErrorsAndHandledConflictAskToFix() {
        for (error in listOf(
            ScheduleDraftError.NAME_TOO_LONG,
            ScheduleDraftError.AMOUNT_INVALID,
            ScheduleDraftError.INTERVAL_OUT_OF_RANGE,
            ScheduleDraftError.DATE_OUT_OF_RANGE,
            ScheduleDraftError.END_BEFORE_START,
            ScheduleDraftError.TOO_MANY_PAST_OCCURRENCES,
        )) {
            assertEquals(missing(RecurringCtaMissing.FIX_ERRORS), cta(errors = setOf(error)))
        }
        assertEquals(
            missing(RecurringCtaMissing.FIX_ERRORS),
            cta(handledConflict = LocalDate.of(2026, 11, 1)),
        )
    }

    @Test
    fun readyExactlyWhenStateCanSave() {
        val usd = CurrencyRegistry.get("USD")
        val now = System.currentTimeMillis()
        val zone = java.time.ZoneId.systemDefault()
        val forms = listOf(
            RecurringFormState(),
            RecurringFormState(accountId = 1, amountText = "5", name = "Rent", categoryId = 3),
            RecurringFormState(accountId = 1, amountText = "5", name = "Rent"),
            RecurringFormState(accountId = 1, amountText = "0", name = "Rent", categoryId = 3),
            RecurringFormState(accountId = 1, amountText = "5", name = "", categoryId = 3),
        )
        for (loading in listOf(true, false)) {
            for (form in forms) {
                val state = RecurringEditUiState(
                    form = form,
                    loading = loading,
                    errors = RecurringFormLogic.errors(form, usd, now, zone),
                )
                assertEquals(
                    "loading=$loading form=$form",
                    state.canSave,
                    RecurringCtaLogic.cta(state) is RecurringCta.Ready,
                )
            }
        }
    }
}
