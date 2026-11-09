package com.yehor.finora.ui.recurring

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
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
import com.yehor.finora.ui.common.AppIcons
import com.yehor.finora.ui.common.CategoryTreePickerContent
import com.yehor.finora.ui.common.ColorPalette
import com.yehor.finora.ui.common.IconBadge
import com.yehor.finora.ui.common.IconSize
import com.yehor.finora.ui.common.PickerMode
import com.yehor.finora.ui.common.TagChip
import com.yehor.finora.ui.common.TagPicker
import com.yehor.finora.ui.operations.AmountKeypad
import com.yehor.finora.ui.theme.PreviewTheme
import com.yehor.finora.ui.theme.finance
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringEditContent(state: RecurringEditUiState, callbacks: RecurringEditCallbacks) {
    var pickAccount by rememberSaveable { mutableStateOf(false) }
    var pickCategory by rememberSaveable { mutableStateOf(false) }

    val form = state.form
    val isIncome = form.kind == TransactionKind.INCOME
    val categoryType = if (isIncome) CategoryType.INCOME else CategoryType.EXPENSE
    val tree = remember(state.categories) { CategoryTree(state.categories) }
    val hasCategoriesOfKind = remember(tree, categoryType) { tree.rootsOf(categoryType).isNotEmpty() }

    val currency = state.currency
    val selectedAccount = state.selectedAccount
    val symbols = remember { DecimalFormatSymbols.getInstance() }
    val formattedAmount = AmountKeypad.format(form.amountText, symbols.groupingSeparator, symbols.decimalSeparator)
    val amountDisplay = formattedAmount + " " + currency.symbol

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

    Scaffold(
        modifier = Modifier.imePadding(),
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
                    IconButton(onClick = callbacks.onBack) {
                        Icon(AppIcons.Back, contentDescription = stringResource(R.string.navigate_back))
                    }
                },
            )
        },
        bottomBar = {
            FormCtaBar(
                cta = RecurringCtaLogic.cta(state),
                amountSuffix = form.amountText.takeIf { it.isNotEmpty() }?.let { currency.symbol + formattedAmount },
                onSave = callbacks.onSave,
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FormKindTabs(kind = form.kind, locked = form.typeLocked, onKindChange = callbacks.onKindChange)

            FormAmountCard(
                amountText = form.amountText,
                amountDisplay = amountDisplay,
                sign = if (isIncome) "+" else FORM_MINUS,
                signColor = if (isIncome) MaterialTheme.finance.income else MaterialTheme.finance.expense,
                currencyScale = currency.scale,
                currencySymbol = currency.symbol,
                accountLabel = selectedAccount?.let { "${it.currency.code} (${it.currency.symbol}) • ${it.name}" },
                archivedNote = archivedNote,
                errorText = amountErrorText,
                decimalSeparator = symbols.decimalSeparator,
                onAccountClick = { pickAccount = true },
                onAmountChange = callbacks.onAmountChange,
            )

            // Name → category → tags: one connected group.
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                NameCard(state, callbacks.onNameChange)
                CategoryCard(
                    state = state,
                    tree = tree,
                    categoryType = categoryType,
                    hasCategoriesOfKind = hasCategoriesOfKind,
                    onOpen = { pickCategory = true },
                    callbacks = callbacks,
                )
                TagsCard(state, callbacks)
            }

            Text(
                stringResource(R.string.recurring_form_schedule_section),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp).semantics { heading() },
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                RepeatsCard(state, callbacks)
                StartDateCard(state, callbacks)
                EndDateCard(state, callbacks)
            }
            PreviewRow(state)

            Spacer(Modifier.height(4.dp))
        }

        if (pickAccount) {
            ModalBottomSheet(onDismissRequest = { pickAccount = false }) {
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
            ModalBottomSheet(onDismissRequest = { pickCategory = false }) {
                CategoryTreePickerContent(
                    tree = tree,
                    type = categoryType,
                    mode = PickerMode.Assign(),
                    selectedId = form.categoryId,
                    onSelect = {
                        callbacks.onCategoryChange(it)
                        pickCategory = false
                    },
                    modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Group 1: name, category, tags
// ---------------------------------------------------------------------------

@Composable
private fun FieldError(text: String) {
    Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun NameCard(state: RecurringEditUiState, onNameChange: (String) -> Unit) {
    val form = state.form
    val tooLong = ScheduleDraftError.NAME_TOO_LONG in state.errors
    val showEmpty = ScheduleDraftError.NAME_EMPTY in state.errors && form.amountText.isNotBlank()
    FormSectionCard(shape = FormGroupTopShape, isError = tooLong || showEmpty) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FormLeadingIcon(AppIcons.Edit)
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.recurring_form_name),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                BasicTextField(
                    value = form.name,
                    onValueChange = onNameChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        Box {
                            if (form.name.isEmpty()) {
                                Text(
                                    stringResource(R.string.recurring_form_name_hint),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                )
                            }
                            inner()
                        }
                    },
                )
                when {
                    tooLong -> FieldError(stringResource(R.string.recurring_form_error_name_long))
                    showEmpty -> FieldError(stringResource(R.string.recurring_form_error_name_empty))
                }
            }
        }
    }
}

@Composable
private fun CategoryCard(
    state: RecurringEditUiState,
    tree: CategoryTree,
    categoryType: CategoryType,
    hasCategoriesOfKind: Boolean,
    onOpen: () -> Unit,
    callbacks: RecurringEditCallbacks,
) {
    val form = state.form
    val category = form.categoryId?.let { tree.category(it) }
    // Resolve composable values unconditionally so the slot table keeps the same shape.
    val uncategorizedLabel = stringResource(R.string.picker_uncategorized)
    val categoryName = category?.name ?: if (form.isEdit && !form.hadCategoryBefore) uncategorizedLabel else null
    val showError = ScheduleDraftError.CATEGORY_MISSING in state.errors && form.amountText.isNotBlank()
    val recents = state.recentCategoryIds.mapNotNull { tree.category(it) }.take(5)
    val canCreate = !hasCategoriesOfKind && callbacks.onNavigateCreateCategory != null

    FormSectionCard(shape = FormGroupMiddleShape, onClick = onOpen, isError = showError) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (category != null) {
                IconBadge(category.icon, category.color, ColorPalette.defaultFor(categoryType), size = 44)
            } else {
                FormLeadingIcon(AppIcons.Category)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    categoryName ?: stringResource(R.string.transaction_form_category),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (categoryName == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (showError) FieldError(stringResource(R.string.transaction_form_error_category_missing))
            }
            Icon(AppIcons.Next, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (recents.isNotEmpty() || canCreate) {
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (recents.isNotEmpty()) {
                    Text(
                        stringResource(R.string.transaction_form_recent),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                recents.forEach { recent ->
                    FormSmallPill(
                        text = recent.name,
                        selected = recent.id == form.categoryId,
                        onClick = { callbacks.onCategoryChange(recent.id) },
                    )
                }
                if (canCreate) {
                    FormSmallPill(
                        onClick = { callbacks.onNavigateCreateCategory?.invoke(categoryType.name) },
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(AppIcons.Add, contentDescription = null, modifier = Modifier.size(IconSize.Small))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                stringResource(R.string.transaction_form_create_category),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun TagsCard(state: RecurringEditUiState, callbacks: RecurringEditCallbacks) {
    var editing by rememberSaveable { mutableStateOf(false) }
    val form = state.form
    FormSectionCard(shape = FormGroupBottomShape) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                AppIcons.Tag,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.transaction_form_labels_tags),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            if (editing) {
                TextButton(onClick = { editing = false }) { Text(stringResource(R.string.transaction_form_tags_done)) }
            } else {
                Text(
                    stringResource(R.string.transaction_form_tags_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        if (editing) {
            TagPicker(
                allTags = state.allTags,
                selectedTagIds = form.selectedTagIds,
                pendingTagNames = form.pendingTagNames,
                onToggleTag = callbacks.onToggleTag,
                onAddPendingTag = callbacks.onAddPendingTag,
                onRemovePendingTag = callbacks.onRemovePendingTag,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                state.allTags.filter { it.id in form.selectedTagIds }.forEach { tag ->
                    TagChip(tag = tag, onRemove = { callbacks.onToggleTag(tag.id) })
                }
                form.pendingTagNames.forEach { name ->
                    InputChip(
                        selected = true,
                        onClick = {},
                        label = { Text(stringResource(R.string.tag_picker_new_suffix, name)) },
                        trailingIcon = {
                            IconButton(onClick = { callbacks.onRemovePendingTag(name) }, modifier = Modifier.size(16.dp)) {
                                Icon(AppIcons.Close, contentDescription = stringResource(R.string.action_remove))
                            }
                        },
                    )
                }
                FilterChip(
                    selected = false,
                    onClick = { editing = true },
                    label = { Text(stringResource(R.string.transaction_form_add_tag)) },
                    leadingIcon = { Icon(AppIcons.Add, contentDescription = null, modifier = Modifier.size(IconSize.Small)) },
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Group 2: schedule
// ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RepeatsCard(state: RecurringEditUiState, callbacks: RecurringEditCallbacks) {
    val frequency = state.form.frequency
    val intervalError = ScheduleDraftError.INTERVAL_OUT_OF_RANGE in state.errors
    FormSectionCard(shape = FormGroupTopShape, isError = intervalError) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FormLeadingIcon(AppIcons.Recurring)
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.recurring_form_repeats),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (intervalError) {
                    FieldError(stringResource(R.string.recurring_form_error_interval))
                } else {
                    Text(ruleText(frequency.rule()), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FrequencyPreset.entries.forEach { preset ->
                FormSmallPill(
                    text = presetLabel(preset),
                    selected = frequency.preset == preset,
                    onClick = { callbacks.onPresetChange(preset) },
                )
            }
        }
        if (frequency.preset == FrequencyPreset.CUSTOM) {
            Spacer(Modifier.height(12.dp))
            IntervalStepper(
                text = frequency.customIntervalText,
                onChange = callbacks.onCustomIntervalChange,
            )
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RecurrenceUnit.entries.forEach { unit ->
                    FormSmallPill(
                        text = unitLabel(unit),
                        selected = frequency.customUnit == unit,
                        onClick = { callbacks.onCustomUnitChange(unit) },
                    )
                }
            }
        }
    }
}

/** "Every  [-]  N  [+]": the number can also be typed; all changes go through [onChange] as text. */
@Composable
private fun IntervalStepper(text: String, onChange: (String) -> Unit) {
    val current = text.trim().toIntOrNull()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.recurring_form_every),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
private fun StartDateCard(state: RecurringEditUiState, callbacks: RecurringEditCallbacks) {
    var picking by rememberSaveable { mutableStateOf(false) }
    val form = state.form
    val rangeError = ScheduleDraftError.DATE_OUT_OF_RANGE in state.errors
    val tooMany = ScheduleDraftError.TOO_MANY_PAST_OCCURRENCES in state.errors
    val conflict = state.handledConflict
    FormSectionCard(
        shape = FormGroupMiddleShape,
        onClick = { picking = true },
        isError = rangeError || tooMany || conflict != null,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FormLeadingIcon(AppIcons.Calendar)
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(
                        if (form.isEdit) R.string.recurring_form_next_occurrence else R.string.recurring_form_starts_on,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(formatScheduleDate(form.firstDate), style = MaterialTheme.typography.titleMedium)
                if (rangeError) FieldError(stringResource(R.string.recurring_form_error_date_range))
                if (conflict != null) {
                    FieldError(stringResource(R.string.recurring_form_error_not_after_handled, formatScheduleDate(conflict)))
                }
                if (tooMany) FieldError(stringResource(R.string.recurring_form_error_too_many))
            }
            Icon(AppIcons.Next, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
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
private fun EndDateCard(state: RecurringEditUiState, callbacks: RecurringEditCallbacks) {
    var picking by rememberSaveable { mutableStateOf(false) }
    val form = state.form
    val endBeforeStart = ScheduleDraftError.END_BEFORE_START in state.errors
    FormSectionCard(shape = FormGroupBottomShape, onClick = { picking = true }, isError = endBeforeStart) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FormLeadingIcon(AppIcons.Calendar)
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.recurring_form_ends_on),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    form.endDate?.let { formatScheduleDate(it) } ?: stringResource(R.string.recurring_form_end_never),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (form.endDate == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                if (endBeforeStart) FieldError(stringResource(R.string.recurring_form_error_end_before_start))
            }
            if (form.endDate != null) {
                FormSmallPill(
                    text = stringResource(R.string.recurring_form_clear_end),
                    onClick = { callbacks.onEndDateChange(null) },
                )
            } else {
                Icon(AppIcons.Next, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
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
