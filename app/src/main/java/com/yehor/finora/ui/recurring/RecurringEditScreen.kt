package com.yehor.finora.ui.recurring

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yehor.finora.R
import com.yehor.finora.domain.category.CategoryTree
import com.yehor.finora.domain.model.AccountStatus
import com.yehor.finora.domain.model.CategoryType
import com.yehor.finora.domain.operation.TransactionKind
import com.yehor.finora.domain.recurring.RecurrenceUnit
import com.yehor.finora.domain.recurring.ScheduleDraftError
import com.yehor.finora.domain.recurring.ScheduleDraftValidator
import com.yehor.finora.ui.common.AccountPickerContent
import com.yehor.finora.ui.common.CategoryTreePickerContent
import com.yehor.finora.ui.common.DateField
import com.yehor.finora.ui.common.PickerMode
import com.yehor.finora.ui.common.TagPicker

private val AMOUNT_ERRORS = setOf(
    ScheduleDraftError.AMOUNT_INVALID,
    ScheduleDraftError.AMOUNT_TOO_PRECISE,
    ScheduleDraftError.AMOUNT_NOT_POSITIVE,
    ScheduleDraftError.AMOUNT_OUT_OF_RANGE,
)

@OptIn(ExperimentalMaterial3Api::class)
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

    var pickAccount by rememberSaveable { mutableStateOf(false) }
    var pickCategory by rememberSaveable { mutableStateOf(false) }

    val form = state.form
    val categoryType = if (form.kind == TransactionKind.INCOME) CategoryType.INCOME else CategoryType.EXPENSE
    val tree = remember(state.categories) { CategoryTree(state.categories) }
    val hasCategoriesOfKind = remember(tree, categoryType) { tree.rootsOf(categoryType).isNotEmpty() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (state.isEdit) R.string.recurring_form_edit_title else R.string.recurring_form_new_title,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_back))
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::save, enabled = state.canSave) {
                        Text(stringResource(R.string.action_save))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = form.kind == TransactionKind.EXPENSE,
                    onClick = { viewModel.onKindChange(TransactionKind.EXPENSE) },
                    enabled = !form.typeLocked,
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                ) { Text(stringResource(R.string.transaction_kind_expense)) }
                SegmentedButton(
                    selected = form.kind == TransactionKind.INCOME,
                    onClick = { viewModel.onKindChange(TransactionKind.INCOME) },
                    enabled = !form.typeLocked,
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                ) { Text(stringResource(R.string.transaction_kind_income)) }
            }

            val showNameEmptyError = ScheduleDraftError.NAME_EMPTY in state.errors && form.amountText.isNotBlank()
            OutlinedTextField(
                value = form.name,
                onValueChange = viewModel::onNameChange,
                label = { Text(stringResource(R.string.recurring_form_name)) },
                singleLine = true,
                isError = ScheduleDraftError.NAME_TOO_LONG in state.errors || showNameEmptyError,
                supportingText = {
                    when {
                        ScheduleDraftError.NAME_TOO_LONG in state.errors ->
                            Text(stringResource(R.string.recurring_form_error_name_long))
                        showNameEmptyError -> Text(stringResource(R.string.recurring_form_error_name_empty))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            val amountLabel = stringResource(R.string.recurring_form_amount)
            OutlinedTextField(
                value = form.amountText,
                onValueChange = viewModel::onAmountChange,
                label = {
                    Text(
                        state.selectedAccount?.currency
                            ?.let { "$amountLabel (${it.symbol} ${it.code})" }
                            ?: amountLabel,
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                isError = state.isOverflow || state.errors.any { it in AMOUNT_ERRORS },
                supportingText = {
                    when {
                        state.isOverflow -> Text(stringResource(R.string.recurring_form_error_overflow))
                        ScheduleDraftError.AMOUNT_OUT_OF_RANGE in state.errors ->
                            Text(stringResource(R.string.transaction_form_error_amount_range))
                        ScheduleDraftError.AMOUNT_INVALID in state.errors ->
                            Text(stringResource(R.string.transaction_form_error_amount_invalid))
                        ScheduleDraftError.AMOUNT_TOO_PRECISE in state.errors ->
                            Text(stringResource(R.string.transaction_form_error_amount_precise))
                        ScheduleDraftError.AMOUNT_NOT_POSITIVE in state.errors ->
                            Text(stringResource(R.string.transaction_form_error_amount_positive))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            val selectedAccount = state.selectedAccount
            Box {
                OutlinedTextField(
                    value = selectedAccount?.name.orEmpty(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.transaction_form_account)) },
                    supportingText = if (selectedAccount?.status == AccountStatus.ARCHIVED) {
                        { Text(stringResource(R.string.account_detail_archived_banner)) }
                    } else null,
                    modifier = Modifier.fillMaxWidth(),
                )
                Box(Modifier.matchParentSize().clickable { pickAccount = true })
            }

            val categoryName = form.categoryId?.let { tree.category(it)?.name }
                ?: if (form.isEdit && !form.hadCategoryBefore) stringResource(R.string.picker_uncategorized) else ""
            val showCategoryError = ScheduleDraftError.CATEGORY_MISSING in state.errors && form.amountText.isNotBlank()
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box {
                    OutlinedTextField(
                        value = categoryName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.transaction_form_category)) },
                        isError = showCategoryError,
                        supportingText = if (showCategoryError) {
                            { Text(stringResource(R.string.transaction_form_error_category_missing)) }
                        } else null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Box(Modifier.matchParentSize().clickable { pickCategory = true })
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.recentCategoryIds
                        .mapNotNull { tree.category(it) }
                        .take(5)
                        .forEach { category ->
                            SuggestionChip(
                                onClick = { viewModel.onCategoryChange(category.id) },
                                label = { Text(category.name) },
                            )
                        }
                    if (!hasCategoriesOfKind && onNavigateCreateCategory != null) {
                        SuggestionChip(
                            onClick = { onNavigateCreateCategory(categoryType.name) },
                            label = { Text(stringResource(R.string.transaction_form_create_category)) },
                        )
                    }
                }
            }

            TagPicker(
                allTags = state.allTags,
                selectedTagIds = form.selectedTagIds,
                pendingTagNames = form.pendingTagNames,
                onToggleTag = viewModel::onToggleTag,
                onAddPendingTag = viewModel::onAddPendingTag,
                onRemovePendingTag = viewModel::onRemovePendingTag,
                modifier = Modifier.fillMaxWidth(),
            )

            FrequencySection(state, viewModel)

            DateField(
                date = form.firstDate,
                onDateChange = viewModel::onFirstDateChange,
                maxDate = ScheduleDraftValidator.MAX_DATE,
                label = stringResource(
                    if (form.isEdit) R.string.recurring_form_next_occurrence else R.string.recurring_form_starts_on,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            DateErrors(state)

            Row(verticalAlignment = Alignment.CenterVertically) {
                DateField(
                    date = form.endDate,
                    onDateChange = { viewModel.onEndDateChange(it) },
                    maxDate = ScheduleDraftValidator.MAX_DATE,
                    label = stringResource(R.string.recurring_form_ends_on),
                    emptyPickerDate = form.firstDate,
                    modifier = Modifier.weight(1f),
                )
                if (form.endDate != null) {
                    TextButton(onClick = { viewModel.onEndDateChange(null) }) {
                        Text(stringResource(R.string.recurring_form_clear_end))
                    }
                }
            }
            if (ScheduleDraftError.END_BEFORE_START in state.errors) {
                ErrorText(stringResource(R.string.recurring_form_error_end_before_start))
            }

            PreviewLine(state)
        }

        if (pickAccount) {
            ModalBottomSheet(onDismissRequest = { pickAccount = false }) {
                AccountPickerContent(
                    accounts = state.accounts,
                    selectedAccountId = form.accountId,
                    onSelect = {
                        viewModel.onAccountChange(it)
                        pickAccount = false
                    },
                    modifier = Modifier.padding(bottom = 16.dp),
                )
            }
        }

        if (pickCategory) {
            ModalBottomSheet(onDismissRequest = { pickCategory = false }) {
                CategoryTreePickerContent(
                    tree = tree,
                    type = categoryType,
                    mode = PickerMode.Assign(),
                    selectedId = form.categoryId,
                    onSelect = {
                        viewModel.onCategoryChange(it)
                        pickCategory = false
                    },
                    modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp),
                )
            }
        }

        SaveDialogs(state, viewModel)
    }
}

@Composable
private fun FrequencySection(state: RecurringEditUiState, viewModel: RecurringEditViewModel) {
    val frequency = state.form.frequency
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.recurring_form_frequency), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FrequencyPreset.entries.forEach { preset ->
                FilterChip(
                    selected = frequency.preset == preset,
                    onClick = { viewModel.onPresetChange(preset) },
                    label = { Text(presetLabel(preset)) },
                )
            }
        }
        if (frequency.preset == FrequencyPreset.CUSTOM) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                OutlinedTextField(
                    value = frequency.customIntervalText,
                    onValueChange = viewModel::onCustomIntervalChange,
                    label = { Text(stringResource(R.string.recurring_form_every)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = ScheduleDraftError.INTERVAL_OUT_OF_RANGE in state.errors,
                    supportingText = {
                        if (ScheduleDraftError.INTERVAL_OUT_OF_RANGE in state.errors) {
                            Text(stringResource(R.string.recurring_form_error_interval))
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(2f).padding(top = 8.dp),
                ) {
                    RecurrenceUnit.entries.forEach { unit ->
                        FilterChip(
                            selected = frequency.customUnit == unit,
                            onClick = { viewModel.onCustomUnitChange(unit) },
                            label = { Text(unitLabel(unit)) },
                        )
                    }
                }
            }
        }
        if (ScheduleDraftError.INTERVAL_OUT_OF_RANGE !in state.errors) {
            Text(
                stringResource(
                    R.string.recurring_form_summary,
                    ruleText(frequency.rule()),
                    formatScheduleDate(state.form.firstDate),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DateErrors(state: RecurringEditUiState) {
    if (ScheduleDraftError.DATE_OUT_OF_RANGE in state.errors) {
        ErrorText(stringResource(R.string.recurring_form_error_date_range))
    }
    state.handledConflict?.let {
        ErrorText(stringResource(R.string.recurring_form_error_not_after_handled, formatScheduleDate(it)))
    }
    if (ScheduleDraftError.TOO_MANY_PAST_OCCURRENCES in state.errors) {
        ErrorText(stringResource(R.string.recurring_form_error_too_many))
    }
}

@Composable
private fun ErrorText(text: String) {
    Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun PreviewLine(state: RecurringEditUiState) {
    val text = when (val preview = state.preview) {
        FormPreview.None -> return
        is FormPreview.Next -> stringResource(R.string.recurring_form_preview_next, formatScheduleDate(preview.date))
        is FormPreview.Due -> pluralStringResource(
            R.plurals.recurring_form_preview_due,
            preview.count,
            preview.count,
            rangeText(preview.first, preview.last),
        )
    }
    Text(text, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun SaveDialogs(state: RecurringEditUiState, viewModel: RecurringEditViewModel) {
    when (val gate = state.gate) {
        null -> Unit
        is SaveGate.CurrencyChange -> AlertDialog(
            onDismissRequest = viewModel::onDismissGate,
            title = { Text(stringResource(R.string.recurring_currency_title)) },
            text = {
                Text(stringResource(R.string.recurring_currency_body, gate.info.newFormatted, gate.info.oldFormatted))
            },
            confirmButton = {
                TextButton(onClick = viewModel::onConfirmGate) { Text(stringResource(R.string.recurring_currency_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onDismissGate) { Text(stringResource(R.string.action_cancel)) }
            },
        )
        is SaveGate.PastOccurrences -> AlertDialog(
            onDismissRequest = viewModel::onDismissGate,
            title = { Text(pluralStringResource(R.plurals.recurring_past_title, gate.count, gate.count)) },
            text = { Text(stringResource(R.string.recurring_past_body, rangeText(gate.first, gate.last))) },
            confirmButton = {
                TextButton(onClick = viewModel::onConfirmGate) { Text(stringResource(R.string.recurring_past_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onDismissGate) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    if (state.showOpeningOrderDialog) {
        AlertDialog(
            onDismissRequest = viewModel::onDismissOpeningOrder,
            title = { Text(stringResource(R.string.account_form_initial_date)) },
            text = { Text(stringResource(R.string.recurring_opening_order_body)) },
            confirmButton = {
                TextButton(onClick = viewModel::onConfirmOpeningOrder) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onDismissOpeningOrder) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    if (state.showArchivedError) {
        AlertDialog(
            onDismissRequest = viewModel::onDismissArchivedError,
            title = { Text(stringResource(R.string.recurring_error_title)) },
            text = { Text(stringResource(R.string.recurring_error_archived)) },
            confirmButton = {
                TextButton(onClick = viewModel::onDismissArchivedError) { Text(stringResource(R.string.recurring_error_ok)) }
            },
        )
    }
}
