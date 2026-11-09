package com.yehor.finora.ui.operations

import com.yehor.finora.ui.common.AppIcons
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
import com.yehor.finora.domain.money.Currency
import com.yehor.finora.domain.operation.TransferDraftError
import com.yehor.finora.ui.common.AccountPickerContent
import com.yehor.finora.ui.common.CategoryTreePickerContent
import com.yehor.finora.ui.common.DateTimeField
import com.yehor.finora.ui.common.PickerMode
import com.yehor.finora.ui.common.TagPicker
import java.time.LocalDate

private val SENT_ERRORS = setOf(
    TransferDraftError.SENT_AMOUNT_INVALID,
    TransferDraftError.SENT_AMOUNT_TOO_PRECISE,
    TransferDraftError.SENT_AMOUNT_NOT_POSITIVE,
    TransferDraftError.SENT_AMOUNT_OUT_OF_RANGE,
)

private val RECEIVED_ERRORS = setOf(
    TransferDraftError.RECEIVED_AMOUNT_INVALID,
    TransferDraftError.RECEIVED_AMOUNT_TOO_PRECISE,
    TransferDraftError.RECEIVED_AMOUNT_NOT_POSITIVE,
    TransferDraftError.RECEIVED_AMOUNT_OUT_OF_RANGE,
)

private enum class AccountSide { SOURCE, DEST }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferEditScreen(
    onBack: () -> Unit,
    onDuplicate: ((Long) -> Unit)? = null,
    viewModel: TransferEditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) {
        if (state.saved) onBack()
    }

    var menuOpen by remember { mutableStateOf(false) }
    var pickSide by rememberSaveable { mutableStateOf<String?>(null) }
    var pickCategory by rememberSaveable { mutableStateOf(false) }

    val tree = remember(state.categories) { CategoryTree(state.categories) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (state.isEdit) R.string.transfer_form_edit_title else R.string.transfer_form_new_title,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(AppIcons.Back, contentDescription = stringResource(R.string.navigate_back))
                    }
                },
                actions = {
                    if (state.isEdit) {
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(AppIcons.More, contentDescription = stringResource(R.string.action_more_options))
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
            val sameAccounts = TransferDraftError.SAME_ACCOUNTS in state.errors

            AccountField(
                label = stringResource(R.string.transfer_form_from),
                name = state.sourceAccount?.name.orEmpty(),
                archived = state.sourceAccount?.status == AccountStatus.ARCHIVED,
                isError = sameAccounts,
                onClick = { pickSide = AccountSide.SOURCE.name },
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                IconButton(onClick = viewModel::onSwap) {
                    Icon(AppIcons.SwapVertical, contentDescription = stringResource(R.string.transfer_form_swap))
                }
            }
            AccountField(
                label = stringResource(R.string.transfer_form_to),
                name = state.destAccount?.name.orEmpty(),
                archived = state.destAccount?.status == AccountStatus.ARCHIVED,
                isError = sameAccounts,
                supportingError = if (sameAccounts) stringResource(R.string.transfer_form_error_same_accounts) else null,
                onClick = { pickSide = AccountSide.DEST.name },
            )

            AmountField(
                label = stringResource(R.string.transfer_form_amount_sent),
                currency = state.sourceAccount?.currency,
                value = state.sentText,
                onValueChange = viewModel::onSentChange,
                isError = state.isOverflow || state.errors.any { it in SENT_ERRORS },
                errorText = amountErrorText(
                    overflow = state.isOverflow,
                    range = TransferDraftError.SENT_AMOUNT_OUT_OF_RANGE in state.errors,
                    invalid = TransferDraftError.SENT_AMOUNT_INVALID in state.errors,
                    precise = TransferDraftError.SENT_AMOUNT_TOO_PRECISE in state.errors,
                    notPositive = TransferDraftError.SENT_AMOUNT_NOT_POSITIVE in state.errors,
                ),
            )
            AmountField(
                label = stringResource(R.string.transfer_form_amount_received),
                currency = state.destAccount?.currency,
                value = state.receivedText,
                onValueChange = viewModel::onReceivedChange,
                isError = state.isOverflow || state.errors.any { it in RECEIVED_ERRORS },
                errorText = amountErrorText(
                    overflow = state.isOverflow,
                    range = TransferDraftError.RECEIVED_AMOUNT_OUT_OF_RANGE in state.errors,
                    invalid = TransferDraftError.RECEIVED_AMOUNT_INVALID in state.errors,
                    precise = TransferDraftError.RECEIVED_AMOUNT_TOO_PRECISE in state.errors,
                    notPositive = TransferDraftError.RECEIVED_AMOUNT_NOT_POSITIVE in state.errors,
                ),
            )
            state.effectiveRate?.let { rate ->
                Text(
                    stringResource(R.string.transfer_form_rate, rate.forward, rate.inverse),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            val categoryName = state.selectedCategoryId?.let { tree.category(it)?.name }
                ?: if (state.isEdit && !state.hadCategoryBefore) stringResource(R.string.picker_uncategorized) else ""
            val showCategoryError = TransferDraftError.CATEGORY_MISSING in state.errors
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
            if (TransferDraftError.DATE_IN_FUTURE in state.errors) {
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
                isError = TransferDraftError.DESCRIPTION_TOO_LONG in state.errors,
                supportingText = {
                    if (TransferDraftError.DESCRIPTION_TOO_LONG in state.errors) {
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

        pickSide?.let { sideName ->
            val side = AccountSide.valueOf(sideName)
            ModalBottomSheet(onDismissRequest = { pickSide = null }) {
                AccountPickerContent(
                    accounts = if (side == AccountSide.SOURCE) state.sourceChoices else state.destChoices,
                    selectedAccountId = if (side == AccountSide.SOURCE) state.sourceAccountId else state.destAccountId,
                    onSelect = {
                        if (side == AccountSide.SOURCE) viewModel.onSourceChange(it) else viewModel.onDestChange(it)
                        pickSide = null
                    },
                    modifier = Modifier.padding(bottom = 16.dp),
                )
            }
        }

        if (pickCategory) {
            ModalBottomSheet(onDismissRequest = { pickCategory = false }) {
                CategoryTreePickerContent(
                    tree = tree,
                    type = CategoryType.TRANSFER,
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

        state.currencyChangeLines?.let { lines ->
            AlertDialog(
                onDismissRequest = viewModel::onDismissCurrencyChange,
                title = { Text(stringResource(R.string.currency_change_dialog_title)) },
                text = {
                    Text(stringResource(R.string.transfer_currency_change_body, lines.joinToString("\n")))
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.save(confirmCurrencyChange = true) }) {
                        Text(stringResource(R.string.currency_change_dialog_confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::onDismissCurrencyChange) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
            )
        }

        if (state.showOpeningOrderDialog) {
            AlertDialog(
                onDismissRequest = viewModel::onDismissOpeningOrder,
                title = { Text(stringResource(R.string.account_form_initial_date)) },
                text = { Text(stringResource(R.string.transfer_form_opening_order_body)) },
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
                title = { Text(stringResource(R.string.transfer_form_delete_title)) },
                text = {
                    Text(
                        stringResource(
                            R.string.transfer_form_delete_body,
                            state.deleteSentBack?.format().orEmpty(),
                            state.deleteReceivedRemoved?.format().orEmpty(),
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

@Composable
private fun AccountField(
    label: String,
    name: String,
    archived: Boolean,
    isError: Boolean,
    onClick: () -> Unit,
    supportingError: String? = null,
) {
    Box {
        OutlinedTextField(
            value = name,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            isError = isError,
            supportingText = when {
                supportingError != null -> {
                    { Text(supportingError) }
                }
                archived -> {
                    { Text(stringResource(R.string.account_detail_archived_banner)) }
                }
                else -> null
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Box(Modifier.matchParentSize().clickable(onClick = onClick))
    }
}

@Composable
private fun AmountField(
    label: String,
    currency: Currency?,
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean,
    errorText: Int?,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = {
            // The currency is only known once an account is selected.
            Text(currency?.let { "$label (${it.symbol} ${it.code})" } ?: label)
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        isError = isError,
        supportingText = errorText?.let { { Text(stringResource(it)) } },
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun amountErrorText(
    overflow: Boolean,
    range: Boolean,
    invalid: Boolean,
    precise: Boolean,
    notPositive: Boolean,
): Int? = when {
    overflow || range -> R.string.transaction_form_error_amount_range
    invalid -> R.string.transaction_form_error_amount_invalid
    precise -> R.string.transaction_form_error_amount_precise
    notPositive -> R.string.transaction_form_error_amount_positive
    else -> null
}
