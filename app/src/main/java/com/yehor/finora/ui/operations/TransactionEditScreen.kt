package com.yehor.finora.ui.operations

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yehor.finora.R
import com.yehor.finora.domain.category.CategoryTree
import com.yehor.finora.domain.model.AccountStatus
import com.yehor.finora.domain.model.CategoryType
import com.yehor.finora.domain.operation.TransactionDraftError
import com.yehor.finora.domain.operation.TransactionKind
import com.yehor.finora.ui.common.AccountPickerContent
import com.yehor.finora.ui.common.CategoryTreePickerContent
import com.yehor.finora.ui.common.DateTimeField
import com.yehor.finora.ui.common.PickerMode
import com.yehor.finora.ui.common.TagPicker
import java.math.BigDecimal
import java.time.LocalDate

private val AMOUNT_ERRORS = setOf(
    TransactionDraftError.AMOUNT_INVALID,
    TransactionDraftError.AMOUNT_TOO_PRECISE,
    TransactionDraftError.AMOUNT_NOT_POSITIVE,
    TransactionDraftError.AMOUNT_OUT_OF_RANGE,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TransactionEditScreen(
    onBack: () -> Unit,
    onSelectTransfer: (() -> Unit)? = null,
    onDuplicate: ((Long) -> Unit)? = null,
    onNavigateCreateCategory: ((String) -> Unit)? = null,
    onOpenSchedule: ((Long) -> Unit)? = null,
    // Not in the ViewModel yet: the button is shown only when this is provided.
    onSaveAndCreateAnother: (() -> Unit)? = null,
    viewModel: TransactionEditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) {
        if (state.saved) onBack()
    }

    var menuOpen by remember { mutableStateOf(false) }
    var pickAccount by rememberSaveable { mutableStateOf(false) }
    var pickCategory by rememberSaveable { mutableStateOf(false) }

    // UI-only for now: wire these to the ViewModel when recurring/split are implemented.
    var isRecurring by rememberSaveable { mutableStateOf(false) }
    var isSplit by rememberSaveable { mutableStateOf(false) }

    val categoryType = if (state.kind == TransactionKind.INCOME) CategoryType.INCOME else CategoryType.EXPENSE
    val tree = remember(state.categories) { CategoryTree(state.categories) }
    val hasCategoriesOfKind = remember(tree, categoryType) { tree.rootsOf(categoryType).isNotEmpty() }

    val selectedAccount = state.selectedAccount
    val currencySymbol = selectedAccount?.currency?.symbol.orEmpty()
    val amountError = state.isOverflow || state.errors.any { it in AMOUNT_ERRORS }
    val isIncome = state.kind == TransactionKind.INCOME
    val formattedAmount = "$currencySymbol${state.amountText.ifEmpty { "0" }}"

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (state.isEdit) R.string.transaction_form_edit_title else R.string.transaction_form_new_title,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_back))
                    }
                },
                actions = {
                    if (state.isEdit) {
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.action_more_options))
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                if (onDuplicate != null) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.transaction_form_duplicate)) },
                                        onClick = {
                                            menuOpen = false
                                            onDuplicate(state.operationId)
                                        },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_delete)) },
                                    onClick = {
                                        menuOpen = false
                                        viewModel.onDeleteRequest()
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val ctaLabel = stringResource(
                        when {
                            state.isEdit -> R.string.transaction_form_cta_save
                            isIncome -> R.string.transaction_form_cta_add_income
                            else -> R.string.transaction_form_cta_add_expense
                        },
                    )
                    Button(
                        onClick = { viewModel.save() },
                        enabled = state.canSave,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(26.dp),
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("$ctaLabel — $formattedAmount", fontWeight = FontWeight.SemiBold)
                    }
                    if (!state.isEdit && onSaveAndCreateAnother != null) {
                        TextButton(onClick = onSaveAndCreateAnother, enabled = state.canSave) {
                            Icon(Icons.Default.AddCircleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.transaction_form_save_and_another))
                        }
                    }
                }
            }
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
            val scheduleId = state.recurringScheduleId
            val scheduleName = state.recurringScheduleName
            if (scheduleId != null && scheduleName != null && onOpenSchedule != null) {
                TextButton(onClick = { onOpenSchedule(scheduleId) }) {
                    Icon(Icons.Default.Repeat, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text(stringResource(R.string.transaction_form_created_by_schedule, scheduleName))
                }
            }

            // Kind switcher
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val segmentCount = if (onSelectTransfer != null) 3 else 2
                SegmentedButton(
                    selected = state.kind == TransactionKind.EXPENSE,
                    onClick = { viewModel.onKindChange(TransactionKind.EXPENSE) },
                    enabled = !state.isEdit,
                    shape = SegmentedButtonDefaults.itemShape(0, segmentCount),
                ) { Text(stringResource(R.string.transaction_kind_expense)) }
                SegmentedButton(
                    selected = state.kind == TransactionKind.INCOME,
                    onClick = { viewModel.onKindChange(TransactionKind.INCOME) },
                    enabled = !state.isEdit,
                    shape = SegmentedButtonDefaults.itemShape(1, segmentCount),
                ) { Text(stringResource(R.string.transaction_kind_income)) }
                if (onSelectTransfer != null) {
                    SegmentedButton(
                        selected = false,
                        onClick = onSelectTransfer,
                        enabled = !state.isEdit,
                        shape = SegmentedButtonDefaults.itemShape(2, segmentCount),
                    ) { Text(stringResource(R.string.transaction_kind_transfer)) }
                }
            }

            // Amount hero + keypad
            AmountCard(
                amountText = state.amountText,
                isIncome = isIncome,
                currencySymbol = currencySymbol,
                accountLabel = selectedAccount?.let { acc ->
                    "${acc.currency.code} (${acc.currency.symbol}) • ${acc.name}"
                },
                isError = amountError,
                errorText = when {
                    state.isOverflow || TransactionDraftError.AMOUNT_OUT_OF_RANGE in state.errors ->
                        stringResource(R.string.transaction_form_error_amount_range)
                    TransactionDraftError.AMOUNT_INVALID in state.errors ->
                        stringResource(R.string.transaction_form_error_amount_invalid)
                    TransactionDraftError.AMOUNT_TOO_PRECISE in state.errors ->
                        stringResource(R.string.transaction_form_error_amount_precise)
                    TransactionDraftError.AMOUNT_NOT_POSITIVE in state.errors ->
                        stringResource(R.string.transaction_form_error_amount_positive)
                    else -> null
                },
                onAccountClick = { pickAccount = true },
                onAmountChange = viewModel::onAmountChange,
            )

            // Account card
            SelectorCard(
                icon = { LetterIcon(selectedAccount?.name, MaterialTheme.colorScheme.secondaryContainer) },
                title = selectedAccount?.name ?: stringResource(R.string.transaction_form_account),
                subtitle = if (selectedAccount?.status == AccountStatus.ARCHIVED) {
                    stringResource(R.string.account_detail_archived_banner)
                } else {
                    selectedAccount?.currency?.code
                },
                badge = selectedAccount?.currency?.code,
                onClick = { pickAccount = true },
            )

            // Category card with recent chips
            val category = state.selectedCategoryId?.let { tree.category(it) }
            val categoryName = category?.name
                ?: if (state.isEdit && !state.hadCategoryBefore) stringResource(R.string.picker_uncategorized) else null
            val showCategoryError = TransactionDraftError.CATEGORY_MISSING in state.errors && state.amountText.isNotBlank()
            val parentName = category?.parentId?.let { tree.category(it)?.name }
            SelectorCard(
                icon = { LetterIcon(categoryName, MaterialTheme.colorScheme.primaryContainer) },
                title = categoryName ?: stringResource(R.string.transaction_form_category),
                subtitle = when {
                    showCategoryError -> stringResource(R.string.transaction_form_error_category_missing)
                    parentName != null -> stringResource(R.string.transaction_form_parent_category, parentName)
                    else -> null
                },
                subtitleIsError = showCategoryError,
                badge = if (parentName != null) stringResource(R.string.transaction_form_sub_badge) else null,
                onClick = { pickCategory = true },
                footer = {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        state.recentCategoryIds
                            .mapNotNull { tree.category(it) }
                            .take(5)
                            .forEach { recent ->
                                SuggestionChip(
                                    onClick = { viewModel.onCategoryChange(recent.id) },
                                    label = { Text(recent.name) },
                                )
                            }
                        if (!hasCategoriesOfKind && onNavigateCreateCategory != null) {
                            SuggestionChip(
                                onClick = { onNavigateCreateCategory(categoryType.name) },
                                label = { Text(stringResource(R.string.transaction_form_create_category)) },
                            )
                        }
                    }
                },
            )

            // Date & time
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    DateTimeField(
                        date = state.date,
                        time = state.time,
                        onDateChange = viewModel::onDateChange,
                        onTimeChange = viewModel::onTimeChange,
                        maxDate = LocalDate.now(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (TransactionDraftError.DATE_IN_FUTURE in state.errors) {
                        Text(
                            stringResource(R.string.transaction_form_error_future_date),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            // Merchant & description (bound to the existing description field)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                OutlinedTextField(
                    value = state.description,
                    onValueChange = viewModel::onDescriptionChange,
                    label = { Text(stringResource(R.string.transaction_form_merchant_description)) },
                    isError = TransactionDraftError.DESCRIPTION_TOO_LONG in state.errors,
                    supportingText = {
                        if (TransactionDraftError.DESCRIPTION_TOO_LONG in state.errors) {
                            Text(stringResource(R.string.transaction_form_error_description_long))
                        } else {
                            Text("${state.description.length}/500")
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                )
            }

            // Labels & tags
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.transaction_form_labels_tags),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TagPicker(
                        allTags = state.allTags,
                        selectedTagIds = state.selectedTagIds,
                        pendingTagNames = state.pendingTagNames,
                        onToggleTag = viewModel::onToggleTag,
                        onAddPendingTag = viewModel::onAddPendingTag,
                        onRemovePendingTag = viewModel::onRemovePendingTag,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // Recurring / split toggles (UI only until the ViewModel supports them)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Column {
                    ToggleRow(
                        icon = Icons.Default.Repeat,
                        title = stringResource(R.string.transaction_form_recurring),
                        subtitle = stringResource(R.string.transaction_form_recurring_subtitle, state.date.dayOfMonth),
                        checked = isRecurring,
                        onChange = { isRecurring = it },
                    )
                    ToggleRow(
                        icon = Icons.AutoMirrored.Filled.CallSplit,
                        title = stringResource(R.string.transaction_form_split),
                        subtitle = stringResource(R.string.transaction_form_split_subtitle),
                        checked = isSplit,
                        onChange = { isSplit = it },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
        }

        if (pickAccount) {
            ModalBottomSheet(onDismissRequest = { pickAccount = false }) {
                AccountPickerContent(
                    accounts = state.accounts,
                    selectedAccountId = state.selectedAccountId,
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
                    selectedId = state.selectedCategoryId,
                    onSelect = {
                        viewModel.onCategoryChange(it)
                        pickCategory = false
                    },
                    modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp),
                )
            }
        }

        state.reassignmentInfo?.let { info ->
            AlertDialog(
                onDismissRequest = viewModel::onDismissReassignment,
                title = { Text(stringResource(R.string.currency_change_dialog_title)) },
                text = {
                    Text(
                        stringResource(
                            R.string.currency_change_dialog_body,
                            info.oldFormatted,
                            info.newFormatted,
                            info.oldAccountName,
                            if (info.isExpense) "+${info.oldFormatted}" else "−${info.oldFormatted}",
                            info.newAccountName,
                            if (info.isExpense) "−${info.newFormatted}" else "+${info.newFormatted}",
                        ),
                    )
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.save(confirmCurrencyChange = true) }) {
                        Text(stringResource(R.string.currency_change_dialog_confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::onDismissReassignment) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
            )
        }

        if (state.showOpeningOrderDialog) {
            AlertDialog(
                onDismissRequest = viewModel::onDismissOpeningOrder,
                title = { Text(stringResource(R.string.account_form_initial_date)) },
                text = { Text(stringResource(R.string.transaction_form_opening_order_body)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.onDismissOpeningOrder()
                            // The currency check runs before the opening-order check, so a currency
                            // change has already been confirmed when this dialog appears.
                            viewModel.save(confirmCurrencyChange = true, moveOpeningBalanceBack = true)
                        },
                    ) { Text(stringResource(R.string.action_confirm)) }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::onDismissOpeningOrder) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
            )
        }

        if (state.showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = viewModel::onDismissDelete,
                title = { Text(stringResource(R.string.transaction_form_delete_title)) },
                text = {
                    Text(
                        stringResource(
                            R.string.transaction_form_delete_body,
                            state.deleteBalanceChange?.format().orEmpty(),
                        ),
                    )
                },
                confirmButton = {
                    TextButton(onClick = viewModel::onConfirmDelete) {
                        Text(stringResource(R.string.action_delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::onDismissDelete) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Amount hero + keypad
// ---------------------------------------------------------------------------

@Composable
private fun AmountCard(
    amountText: String,
    isIncome: Boolean,
    currencySymbol: String,
    accountLabel: String?,
    isError: Boolean,
    errorText: String?,
    onAccountClick: () -> Unit,
    onAmountChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(onClick = onAccountClick) {
                Text(
                    accountLabel ?: stringResource(R.string.transaction_form_account),
                    style = MaterialTheme.typography.labelMedium,
                )
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(16.dp))
            }

            val amountColor = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (isIncome) "+" else "-",
                    style = MaterialTheme.typography.displayMedium,
                    color = if (isIncome) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                )
                Text(
                    text = currencySymbol + amountText.ifEmpty { "0" },
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = amountColor,
                )
            }
            if (errorText != null) {
                Text(errorText, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(10, 25, 50).forEach { inc ->
                    AssistChip(
                        onClick = { onAmountChange(addToAmount(amountText, inc)) },
                        label = { Text("+$inc") },
                    )
                }
                AssistChip(
                    onClick = { onAmountChange("") },
                    label = { Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp)) },
                )
            }

            NumberPad(onKey = { onAmountChange(applyKey(amountText, it)) })
        }
    }
}

@Composable
private fun NumberPad(onKey: (String) -> Unit) {
    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", ".", "0", "⌫")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        keys.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { key ->
                    FilledTonalButton(
                        onClick = { onKey(key) },
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(20.dp),
                    ) {
                        if (key == "⌫") {
                            Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = null)
                        } else {
                            Text(key, style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
            }
        }
    }
}

private fun applyKey(current: String, key: String): String = when (key) {
    "⌫" -> current.dropLast(1)
    "." -> if ('.' in current) current else current.ifEmpty { "0" } + "."
    else -> if (current == "0") key else current + key
}

private fun addToAmount(current: String, inc: Int): String =
    ((current.toBigDecimalOrNull() ?: BigDecimal.ZERO) + inc.toBigDecimal())
        .stripTrailingZeros()
        .toPlainString()

// ---------------------------------------------------------------------------
// Reusable pieces
// ---------------------------------------------------------------------------

@Composable
private fun SelectorCard(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitleIsError: Boolean = false,
    badge: String? = null,
    footer: (@Composable () -> Unit)? = null,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                icon()
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (subtitleIsError) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (badge != null) {
                    Text(
                        badge,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
                Icon(Icons.Default.ChevronRight, contentDescription = null)
            }
            footer?.invoke()
        }
    }
}

@Composable
private fun LetterIcon(name: String?, color: Color) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name?.firstOrNull()?.uppercase() ?: "?",
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun ToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    ListItem(
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}