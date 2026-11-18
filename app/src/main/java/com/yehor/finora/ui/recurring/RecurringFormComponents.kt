package com.yehor.finora.ui.recurring

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yehor.finora.R
import com.yehor.finora.designsystem.component.button.RoundIconButton
import com.yehor.finora.designsystem.component.input.SegmentedPill
import com.yehor.finora.domain.account.Account
import com.yehor.finora.domain.operation.TransactionDateTime
import com.yehor.finora.domain.operation.TransactionKind
import com.yehor.finora.ui.common.SingleDateDialog
import com.yehor.finora.ui.operations.AccountPill
import com.yehor.finora.ui.operations.AmountKeypad
import com.yehor.finora.ui.operations.EditorAmount
import com.yehor.finora.ui.operations.NumberPad
import com.yehor.finora.ui.operations.QuickAmountRow
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// Building blocks of the schedule form. Rows, pills, the amount hero and the keypad are the transaction
// editor's (operations package), so both forms look and behave alike.

// ---------------------------------------------------------------------------
// Kind tabs
// ---------------------------------------------------------------------------

/** Expense / Income only: schedules do not support transfers. A [locked] kind keeps the selection readable. */
@Composable
internal fun FormKindTabs(kind: TransactionKind, locked: Boolean, onKindChange: (TransactionKind) -> Unit) {
    SegmentedPill(
        options = listOf(
            stringResource(R.string.transaction_kind_expense),
            stringResource(R.string.transaction_kind_income),
        ),
        selected = if (kind == TransactionKind.INCOME) 1 else 0,
        onSelect = { onKindChange(if (it == 1) TransactionKind.INCOME else TransactionKind.EXPENSE) },
        enabled = !locked,
    )
}

// ---------------------------------------------------------------------------
// Amount hero + keypad
// ---------------------------------------------------------------------------

/** The editor's amount block: huge amount, account pill, quick-add pills and the shared keypad. */
@Composable
internal fun FormAmountSection(
    amountText: String,
    sign: String,
    currencyScale: Int,
    currencySymbol: String,
    account: Account?,
    archivedNote: String?,
    errorText: String?,
    symbols: DecimalFormatSymbols,
    onAccountClick: () -> Unit,
    onAmountChange: (String) -> Unit,
) {
    Column(
        Modifier.padding(top = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        EditorAmount(
            sign = sign,
            amountText = amountText,
            currencySymbol = currencySymbol,
            symbols = symbols,
            isError = errorText != null,
        )
        if (errorText != null) {
            Text(errorText, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        AccountPill(account, balance = null, symbols = symbols, onClick = onAccountClick)
        if (archivedNote != null) {
            Text(
                archivedNote,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        QuickAmountRow(amountText, currencySymbol, onAmountChange)
        NumberPad(
            decimalLabel = symbols.decimalSeparator.toString(),
            decimalEnabled = currencyScale > 0,
            onKey = { onAmountChange(AmountKeypad.applyKey(amountText, it, currencyScale)) },
            onClear = { onAmountChange("") },
        )
    }
}

// ---------------------------------------------------------------------------
// Interval stepper
// ---------------------------------------------------------------------------

/** Round 48dp stepper button. */
@Composable
internal fun FormStepButton(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    RoundIconButton(icon, description, onClick, enabled = enabled)
}

// ---------------------------------------------------------------------------
// Bottom CTA label
// ---------------------------------------------------------------------------

@Composable
internal fun recurringCtaLabel(cta: RecurringCta, amountSuffix: String?): String = when (cta) {
    is RecurringCta.Missing -> stringResource(
        when (cta.reason) {
            RecurringCtaMissing.LOADING -> R.string.recurring_form_cta_loading
            RecurringCtaMissing.ENTER_AMOUNT -> R.string.recurring_form_cta_enter_amount
            RecurringCtaMissing.CHOOSE_ACCOUNT -> R.string.recurring_form_cta_choose_account
            RecurringCtaMissing.ENTER_NAME -> R.string.recurring_form_cta_enter_name
            RecurringCtaMissing.CHOOSE_CATEGORY -> R.string.recurring_form_cta_choose_category
            RecurringCtaMissing.FIX_ERRORS -> R.string.recurring_form_cta_fix_errors
        },
    )
    is RecurringCta.Ready -> stringResource(
        when {
            cta.isEdit -> R.string.recurring_form_cta_save
            cta.kind == TransactionKind.INCOME -> R.string.recurring_form_cta_add_income
            else -> R.string.recurring_form_cta_add_expense
        },
    ) + (amountSuffix?.let { " · $it" } ?: "")
}

// ---------------------------------------------------------------------------
// Date picker (date only)
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FormDatePickerDialog(
    initial: LocalDate,
    maxDate: LocalDate,
    onDismiss: () -> Unit,
    onPicked: (LocalDate) -> Unit,
) {
    val maxUtcMidnight = TransactionDateTime.pickerUtcMidnight(maxDate)
    SingleDateDialog(
        initialUtcMillis = TransactionDateTime.pickerUtcMidnight(initial),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= maxUtcMidnight
        },
        onDismiss = onDismiss,
        onConfirm = {
            onPicked(Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate())
            onDismiss()
        },
    )
}
