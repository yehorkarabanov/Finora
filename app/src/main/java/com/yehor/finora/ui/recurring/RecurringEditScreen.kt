package com.yehor.finora.ui.recurring

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yehor.finora.R
import com.yehor.finora.designsystem.component.button.PrimaryButton
import com.yehor.finora.designsystem.component.input.FilterPill
import com.yehor.finora.designsystem.component.list.GroupedCard
import com.yehor.finora.designsystem.component.list.SectionHeader
import com.yehor.finora.designsystem.component.sheet.FinoraSheet
import com.yehor.finora.designsystem.icon.AppIcons
import com.yehor.finora.designsystem.icon.IconSize
import com.yehor.finora.designsystem.theme.PillShape
import com.yehor.finora.designsystem.theme.PreviewTheme
import com.yehor.finora.domain.category.CategoryTree
import com.yehor.finora.domain.model.AccountStatus
import com.yehor.finora.domain.model.CategoryType
import com.yehor.finora.domain.operation.TransactionKind
import com.yehor.finora.domain.recurring.RecurrenceUnit
import com.yehor.finora.domain.recurring.ScheduleDraftError
import com.yehor.finora.domain.recurring.ScheduleDraftValidator
import com.yehor.finora.ui.common.AccountPickerContent
import com.yehor.finora.ui.common.CategoryTreePickerContent
import com.yehor.finora.ui.common.FieldNote
import com.yehor.finora.ui.common.FormCtaBar
import com.yehor.finora.ui.common.FormScreen
import com.yehor.finora.ui.common.FormTextRow
import com.yehor.finora.ui.common.PickerMode
import com.yehor.finora.ui.common.errorOutline
import com.yehor.finora.ui.operations.AmountKeypad
import com.yehor.finora.ui.operations.CategoryShelfRow
import com.yehor.finora.ui.operations.FORM_MINUS
import com.yehor.finora.ui.operations.RecentCategoriesRow
import com.yehor.finora.ui.operations.ShelfDivider
import com.yehor.finora.ui.operations.ShelfIcon
import com.yehor.finora.ui.operations.ShelfRow
import com.yehor.finora.ui.operations.ShelfValue
import com.yehor.finora.ui.operations.SmallPill
import com.yehor.finora.ui.operations.TagsShelfRow
import java.text.DecimalFormatSymbols
import java.time.LocalDate

/** Everything the stateless form content can ask for; defaults make previews trivial. */
class RecurringEditCallbacks(
    val onBack: () -> Unit = {},
    val onKindChange: (TransactionKind) -> Unit = {},
    val onNameChange: (String) -> Unit = {},
    val onAmountChange: (String) -> Unit = {},
    val onAccountChange: (Long) -> Unit = {},
    val onCategoryChange: (Long?) -> Unit = {},
    val onToggleTag: (Long) -> Unit = {},
    val onAddPendingTag: (String) -> Unit = {},
    val onRemovePendingTag: (String) -> Unit = {},
    val onPresetChange: (FrequencyPreset) -> Unit = {},
    val onCustomUnitChange: (RecurrenceUnit) -> Unit = {},
    val onCustomIntervalChange: (String) -> Unit = {},
    val onFirstDateChange: (LocalDate) -> Unit = {},
    val onEndDateChange: (LocalDate?) -> Unit = {},
    val onSave: () -> Unit = {},
    val onNavigateCreateCategory: ((String) -> Unit)? = null,
)

@Composable
fun RecurringEditScreen(
    onBack: () -> Unit,
    onNavigateCreateCategory: ((String) -> Unit)? = null,
    viewModel: RecurringEditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) {
        if (state.saved) onBack()
    }

    val callbacks = remember(viewModel, onBack, onNavigateCreateCategory) {
        RecurringEditCallbacks(
            onBack = onBack,
            onKindChange = viewModel::onKindChange,
            onNameChange = viewModel::onNameChange,
            onAmountChange = viewModel::onAmountChange,
            onAccountChange = viewModel::onAccountChange,
            onCategoryChange = viewModel::onCategoryChange,
            onToggleTag = viewModel::onToggleTag,
            onAddPendingTag = viewModel::onAddPendingTag,
            onRemovePendingTag = viewModel::onRemovePendingTag,
            onPresetChange = viewModel::onPresetChange,
            onCustomUnitChange = viewModel::onCustomUnitChange,
            onCustomIntervalChange = viewModel::onCustomIntervalChange,
            onFirstDateChange = viewModel::onFirstDateChange,
            onEndDateChange = viewModel::onEndDateChange,
            onSave = viewModel::save,
            onNavigateCreateCategory = onNavigateCreateCategory,
        )
    }

    RecurringEditContent(state, callbacks)
    SaveDialogs(state, viewModel)
}

@Composable
fun RecurringEditContent(state: RecurringEditUiState, callbacks: RecurringEditCallbacks) {
    var pickAccount by rememberSaveable { mutableStateOf(false) }
    var pickCategory by rememberSaveable { mutableStateOf(false) }

    val form = state.form
    val isIncome = form.kind == TransactionKind.INCOME
    val categoryType = if (isIncome) CategoryType.INCOME else CategoryType.EXPENSE
    val tree = remember(state.categories) { CategoryTree(state.categories) }

    val currency = state.currency
    val selectedAccount = state.selectedAccount
    val symbols = remember { DecimalFormatSymbols.getInstance() }
    val formattedAmount = AmountKeypad.format(form.amountText, symbols.groupingSeparator, symbols.decimalSeparator)

    val amountErrorText = when {
        state.isOverflow -> stringResource(R.string.recurring_form_error_overflow)
        ScheduleDraftError.AMOUNT_OUT_OF_RANGE in state.errors -> stringResource(R.string.transaction_form_error_amount_range)
        ScheduleDraftError.AMOUNT_INVALID in state.errors -> stringResource(R.string.transaction_form_error_amount_invalid)
        ScheduleDraftError.AMOUNT_TOO_PRECISE in state.errors -> stringResource(R.string.transaction_form_error_amount_precise)
        ScheduleDraftError.AMOUNT_NOT_POSITIVE in state.errors -> stringResource(R.string.transaction_form_error_amount_positive)
        else -> null
    }
    val archivedNote = if (selectedAccount?.status == AccountStatus.ARCHIVED) {
        stringResource(R.string.account_detail_archived_banner)
    } else {
        null
    }
    val cta = RecurringCtaLogic.cta(state)

    FormScreen(
        title = stringResource(if (state.isEdit) R.string.recurring_form_edit_title else R.string.recurring_form_new_title),
        onBack = callbacks.onBack,
        bottomBar = {
            FormCtaBar(
                label = recurringCtaLabel(
                    cta = cta,
                    amountSuffix = form.amountText.takeIf { it.isNotEmpty() }?.let { currency.symbol + formattedAmount },
                ),
                enabled = cta is RecurringCta.Ready,
                onClick = callbacks.onSave,
            )
        },
    ) {
        FormKindTabs(kind = form.kind, locked = form.typeLocked, onKindChange = callbacks.onKindChange)

        FormAmountSection(
            amountText = form.amountText,
            sign = if (isIncome) "+" else FORM_MINUS,
            currencyScale = currency.scale,
            currencySymbol = currency.symbol,
            account = selectedAccount,
            archivedNote = archivedNote,
            errorText = amountErrorText,
            symbols = symbols,
            onAccountClick = { pickAccount = true },
            onAmountChange = callbacks.onAmountChange,
        )

        // Name → category → tags: one card.
        val nameTooLong = ScheduleDraftError.NAME_TOO_LONG in state.errors
        val nameEmpty = ScheduleDraftError.NAME_EMPTY in state.errors && form.amountText.isNotBlank()
        val categoryMissing = ScheduleDraftError.CATEGORY_MISSING in state.errors && form.amountText.isNotBlank()
        GroupedCard(Modifier.fillMaxWidth().padding(top = 8.dp).errorOutline(nameTooLong || nameEmpty || categoryMissing)) {
            NameRow(state, callbacks.onNameChange)
            ShelfDivider()
            CategoryShelfRow(
                tree = tree,
                categoryType = categoryType,
                selectedCategoryId = form.categoryId,
                showUncategorized = form.isEdit && !form.hadCategoryBefore,
                showError = categoryMissing,
                onClick = { pickCategory = true },
            )
            ShelfDivider()
            TagsShelfRow(
                allTags = state.allTags,
                selectedTagIds = form.selectedTagIds,
                pendingTagNames = form.pendingTagNames,
                onToggleTag = callbacks.onToggleTag,
                onAddPendingTag = callbacks.onAddPendingTag,
                onRemovePendingTag = callbacks.onRemovePendingTag,
            )
        }
        RecentCategoriesRow(
            tree = tree,
            categoryType = categoryType,
            selectedCategoryId = form.categoryId,
            recentCategoryIds = state.recentCategoryIds,
            onSelectRecent = callbacks.onCategoryChange,
            onCreateCategory = callbacks.onNavigateCreateCategory?.let { nav -> { nav(categoryType.name) } },
        )

        SectionHeader(stringResource(R.string.recurring_form_schedule_section), Modifier.padding(top = 8.dp).semantics { heading() })
        val rangeError = ScheduleDraftError.DATE_OUT_OF_RANGE in state.errors
        val tooMany = ScheduleDraftError.TOO_MANY_PAST_OCCURRENCES in state.errors
        val endBeforeStart = ScheduleDraftError.END_BEFORE_START in state.errors
        val intervalError = ScheduleDraftError.INTERVAL_OUT_OF_RANGE in state.errors
        GroupedCard(
            Modifier.fillMaxWidth().errorOutline(
                intervalError || rangeError || tooMany || endBeforeStart || state.handledConflict != null,
            ),
        ) {
            RepeatsRow(state, callbacks)
            ShelfDivider()
            StartDateRow(state, callbacks)
            ShelfDivider()
            EndDateRow(state, callbacks)
        }
        PreviewRow(state)
    }

    if (pickAccount) {
        FinoraSheet(onDismiss = { pickAccount = false }) {
            AccountPickerContent(
                accounts = state.accounts,
                selectedAccountId = form.accountId,
                onSelect = {
                    callbacks.onAccountChange(it)
                    pickAccount = false
                },
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }
    }

    if (pickCategory) {
        FinoraSheet(onDismiss = { pickCategory = false }) {
            CategoryTreePickerContent(
                tree = tree,
                type = categoryType,
                mode = PickerMode.Assign(),
                selectedId = form.categoryId,
                onSelect = {
                    callbacks.onCategoryChange(it)
                    pickCategory = false
                },
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Group 1: name
// ---------------------------------------------------------------------------

@Composable
private fun NameRow(state: RecurringEditUiState, onNameChange: (String) -> Unit) {
    val form = state.form
    val tooLong = ScheduleDraftError.NAME_TOO_LONG in state.errors
    val showEmpty = ScheduleDraftError.NAME_EMPTY in state.errors && form.amountText.isNotBlank()
    FormTextRow(
        icon = { ShelfIcon(AppIcons.Edit) },
        label = stringResource(R.string.recurring_form_name),
        value = form.name,
        onValueChange = onNameChange,
        hint = stringResource(R.string.recurring_form_name_hint),
        notes = {
            when {
                tooLong -> FieldNote(stringResource(R.string.recurring_form_error_name_long), isError = true)
                showEmpty -> FieldNote(stringResource(R.string.recurring_form_error_name_empty), isError = true)
            }
        },
    )
}

// ---------------------------------------------------------------------------
// Group 2: schedule
// ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RepeatsRow(state: RecurringEditUiState, callbacks: RecurringEditCallbacks) {
    val frequency = state.form.frequency
    val intervalError = ScheduleDraftError.INTERVAL_OUT_OF_RANGE in state.errors
    ShelfRow(
        icon = { ShelfIcon(AppIcons.Recurring) },
        label = stringResource(R.string.recurring_form_repeats),
        onClick = null,
        supporting = if (intervalError) stringResource(R.string.recurring_form_error_interval) else null,
        supportingIsError = intervalError,
        trailing = { if (!intervalError) ShelfValue(ruleText(frequency.rule())) },
        below = {
            Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FrequencyPreset.entries.forEach { preset ->
                        FilterPill(
                            label = presetLabel(preset),
                            active = frequency.preset == preset,
                            onClick = { callbacks.onPresetChange(preset) },
                        )
                    }
                }
                if (frequency.preset == FrequencyPreset.CUSTOM) {
                    IntervalStepper(
                        text = frequency.customIntervalText,
                        onChange = callbacks.onCustomIntervalChange,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RecurrenceUnit.entries.forEach { unit ->
                            FilterPill(
                                label = unitLabel(unit),
                                active = frequency.customUnit == unit,
                                onClick = { callbacks.onCustomUnitChange(unit) },
                            )
                        }
                    }
                }
            }
        },
    )
}

/** "Every  [-]  N  [+]": the number can also be typed; all changes go through [onChange] as text. */
@Composable
private fun IntervalStepper(text: String, onChange: (String) -> Unit) {
    val current = text.trim().toIntOrNull()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(R.string.recurring_form_every),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 4.dp),
        )
        FormStepButton(
            icon = AppIcons.Remove,
            description = stringResource(R.string.recurring_form_interval_decrease),
            enabled = (current ?: 2) > 1,
            onClick = { onChange(((current ?: 2) - 1).coerceAtLeast(1).toString()) },
        )
        BasicTextField(
            value = text,
            onValueChange = onChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.titleLarge.copy(
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            modifier = Modifier.width(56.dp),
        )
        FormStepButton(
            icon = AppIcons.Add,
            description = stringResource(R.string.recurring_form_interval_increase),
            enabled = (current ?: 0) < 365,
            onClick = { onChange(((current ?: 0) + 1).coerceAtMost(365).toString()) },
        )
    }
}

@Composable
private fun StartDateRow(state: RecurringEditUiState, callbacks: RecurringEditCallbacks) {
    var picking by rememberSaveable { mutableStateOf(false) }
    val form = state.form
    val rangeError = ScheduleDraftError.DATE_OUT_OF_RANGE in state.errors
    val tooMany = ScheduleDraftError.TOO_MANY_PAST_OCCURRENCES in state.errors
    val conflict = state.handledConflict
    val errors = listOfNotNull(
        if (rangeError) stringResource(R.string.recurring_form_error_date_range) else null,
        conflict?.let { stringResource(R.string.recurring_form_error_not_after_handled, formatScheduleDate(it)) },
        if (tooMany) stringResource(R.string.recurring_form_error_too_many) else null,
    )
    ShelfRow(
        icon = { ShelfIcon(AppIcons.Calendar) },
        label = stringResource(if (form.isEdit) R.string.recurring_form_next_occurrence else R.string.recurring_form_starts_on),
        onClick = { picking = true },
        supporting = errors.joinToString("\n").ifEmpty { null },
        supportingIsError = errors.isNotEmpty(),
        trailing = {
            ShelfValue(formatScheduleDate(form.firstDate))
            Icon(AppIcons.Next, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
    )
    if (picking) {
        FormDatePickerDialog(
            initial = form.firstDate,
            maxDate = ScheduleDraftValidator.MAX_DATE,
            onDismiss = { picking = false },
            onPicked = callbacks.onFirstDateChange,
        )
    }
}

@Composable
private fun EndDateRow(state: RecurringEditUiState, callbacks: RecurringEditCallbacks) {
    var picking by rememberSaveable { mutableStateOf(false) }
    val form = state.form
    val endBeforeStart = ScheduleDraftError.END_BEFORE_START in state.errors
    ShelfRow(
        icon = { ShelfIcon(AppIcons.Calendar) },
        label = stringResource(R.string.recurring_form_ends_on),
        onClick = { picking = true },
        supporting = if (endBeforeStart) stringResource(R.string.recurring_form_error_end_before_start) else null,
        supportingIsError = endBeforeStart,
        trailing = {
            val end = form.endDate
            ShelfValue(
                end?.let { formatScheduleDate(it) } ?: stringResource(R.string.recurring_form_end_never),
                muted = end == null,
            )
            if (end != null) {
                SmallPill(
                    text = stringResource(R.string.recurring_form_clear_end),
                    onClick = { callbacks.onEndDateChange(null) },
                )
            } else {
                Icon(AppIcons.Next, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    )
    if (picking) {
        FormDatePickerDialog(
            initial = form.endDate ?: form.firstDate,
            maxDate = ScheduleDraftValidator.MAX_DATE,
            onDismiss = { picking = false },
            onPicked = { callbacks.onEndDateChange(it) },
        )
    }
}

@Composable
private fun PreviewRow(state: RecurringEditUiState) {
    val (icon, text) = when (val preview = state.preview) {
        FormPreview.None -> return
        is FormPreview.Next ->
            AppIcons.Calendar to stringResource(R.string.recurring_form_preview_next, formatScheduleDate(preview.date))
        is FormPreview.Due -> AppIcons.Recurring to pluralStringResource(
            R.plurals.recurring_form_preview_due,
            preview.count,
            preview.count,
            rangeText(preview.first, preview.last),
        )
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(IconSize.Small),
        )
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------------------------------------------------------------------------
// Dialogs
// ---------------------------------------------------------------------------

/** Obsidian alert: 28dp raised container, mint pill confirm, text-pill dismiss. */
@Composable
private fun RecurringAlert(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String? = stringResource(R.string.action_cancel),
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { PrimaryButton(text = confirmLabel, onClick = onConfirm, fillWidth = false) },
        dismissButton = dismissLabel?.let { label ->
            { TextButton(onClick = onDismiss, shape = PillShape) { Text(label) } }
        },
    )
}

@Composable
private fun SaveDialogs(state: RecurringEditUiState, viewModel: RecurringEditViewModel) {
    when (val gate = state.gate) {
        null -> Unit
        is SaveGate.CurrencyChange -> RecurringAlert(
            title = stringResource(R.string.recurring_currency_title),
            body = stringResource(R.string.recurring_currency_body, gate.info.newFormatted, gate.info.oldFormatted),
            confirmLabel = stringResource(R.string.recurring_currency_confirm),
            onConfirm = viewModel::onConfirmGate,
            onDismiss = viewModel::onDismissGate,
        )
        is SaveGate.PastOccurrences -> RecurringAlert(
            title = pluralStringResource(R.plurals.recurring_past_title, gate.count, gate.count),
            body = stringResource(R.string.recurring_past_body, rangeText(gate.first, gate.last)),
            confirmLabel = stringResource(R.string.recurring_past_confirm),
            onConfirm = viewModel::onConfirmGate,
            onDismiss = viewModel::onDismissGate,
        )
    }

    if (state.showOpeningOrderDialog) {
        RecurringAlert(
            title = stringResource(R.string.account_form_initial_date),
            body = stringResource(R.string.recurring_opening_order_body),
            confirmLabel = stringResource(R.string.action_confirm),
            onConfirm = viewModel::onConfirmOpeningOrder,
            onDismiss = viewModel::onDismissOpeningOrder,
        )
    }

    if (state.showArchivedError) {
        RecurringAlert(
            title = stringResource(R.string.recurring_error_title),
            body = stringResource(R.string.recurring_error_archived),
            confirmLabel = stringResource(R.string.recurring_error_ok),
            onConfirm = viewModel::onDismissArchivedError,
            onDismiss = viewModel::onDismissArchivedError,
            dismissLabel = null,
        )
    }
}

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

private fun previewState() = RecurringEditUiState(
    form = RecurringFormState(
        name = "Rent",
        amountText = "1250.5",
        frequency = FrequencyInput(FrequencyPreset.CUSTOM, RecurrenceUnit.WEEK, "2"),
        firstDate = LocalDate.of(2026, 12, 1),
    ),
    loading = false,
    preview = FormPreview.Next(LocalDate.of(2026, 12, 1)),
)

@Preview(name = "Recurring form light", showBackground = true, heightDp = 1500)
@Composable
private fun RecurringEditLightPreview() {
    PreviewTheme(dark = false) { RecurringEditContent(previewState(), RecurringEditCallbacks()) }
}

@Preview(name = "Recurring form dark", showBackground = true, backgroundColor = 0xFF0F1412, heightDp = 1500)
@Composable
private fun RecurringEditDarkPreview() {
    PreviewTheme(dark = true) { RecurringEditContent(previewState(), RecurringEditCallbacks()) }
}
