package com.yehor.finora.ui.operations

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
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
    viewModel: TransactionEditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) {
        if (state.saved) onBack()
    }

    var menuOpen by remember { mutableStateOf(false) }
    var pickAccount by rememberSaveable { mutableStateOf(false) }
    var pickCategory by rememberSaveable { mutableStateOf(false) }

    val categoryType = if (state.kind == TransactionKind.INCOME) CategoryType.INCOME else CategoryType.EXPENSE
    val tree = remember(state.categories) { CategoryTree(state.categories) }
    val hasCategoriesOfKind = remember(tree, categoryType) { tree.rootsOf(categoryType).isNotEmpty() }

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
                    TextButton(onClick = { viewModel.save() }, enabled = state.canSave) {
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

            val amountError = state.isOverflow || state.errors.any { it in AMOUNT_ERRORS }
            val amountLabel = stringResource(R.string.transaction_form_amount)
            OutlinedTextField(
                value = state.amountText,
                onValueChange = viewModel::onAmountChange,
                label = {
                    // The currency is only known once an account is selected.
                    Text(
                        state.selectedAccount?.currency
                            ?.let { "$amountLabel (${it.symbol} ${it.code})" }
                            ?: amountLabel,
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                isError = amountError,
                supportingText = {
                    when {
                        state.isOverflow || TransactionDraftError.AMOUNT_OUT_OF_RANGE in state.errors ->
                            Text(stringResource(R.string.transaction_form_error_amount_range))
                        TransactionDraftError.AMOUNT_INVALID in state.errors ->
                            Text(stringResource(R.string.transaction_form_error_amount_invalid))
                        TransactionDraftError.AMOUNT_TOO_PRECISE in state.errors ->
                            Text(stringResource(R.string.transaction_form_error_amount_precise))
                        TransactionDraftError.AMOUNT_NOT_POSITIVE in state.errors ->
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

            val categoryName = state.selectedCategoryId?.let { tree.category(it)?.name }
                ?: if (state.isEdit && !state.hadCategoryBefore) stringResource(R.string.picker_uncategorized) else ""
            val showCategoryError = TransactionDraftError.CATEGORY_MISSING in state.errors && state.amountText.isNotBlank()
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

            OutlinedTextField(
                value = state.description,
                onValueChange = viewModel::onDescriptionChange,
                label = { Text(stringResource(R.string.transaction_form_description)) },
                isError = TransactionDraftError.DESCRIPTION_TOO_LONG in state.errors,
                supportingText = {
                    if (TransactionDraftError.DESCRIPTION_TOO_LONG in state.errors) {
                        Text(stringResource(R.string.transaction_form_error_description_long))
                    } else {
                        Text("${state.description.length}/500")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
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
