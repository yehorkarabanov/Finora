package com.yehor.finora.ui.operations

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yehor.finora.R
import com.yehor.finora.designsystem.component.button.PrimaryButton
import com.yehor.finora.designsystem.component.list.GroupedCard
import com.yehor.finora.designsystem.icon.AppIcons
import com.yehor.finora.designsystem.theme.PreviewTheme
import com.yehor.finora.domain.account.Account
import com.yehor.finora.domain.account.AccountType
import com.yehor.finora.domain.account.PickableAccount
import com.yehor.finora.domain.category.CategoryTree
import com.yehor.finora.domain.model.AccountStatus
import com.yehor.finora.domain.model.CategoryType
import com.yehor.finora.domain.money.AmountParseResult
import com.yehor.finora.domain.money.AmountParser
import com.yehor.finora.domain.money.Currency
import com.yehor.finora.domain.money.Money
import com.yehor.finora.domain.operation.TransactionDraftError
import com.yehor.finora.domain.operation.TransactionKind
import com.yehor.finora.ui.common.AccountPickerContent
import com.yehor.finora.ui.common.CategoryTreePickerContent
import com.yehor.finora.ui.common.DateTimePickerFlow
import com.yehor.finora.ui.common.PickerMode
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.LocalTime

/**
 * The unified editor for Expense, Income and Transfer. Two view models back it, both created from the same
 * back-stack entry: [TransactionEditViewModel] (Expense/Income) and [TransferEditViewModel] (Transfer).
 * The screen shows the one that matches the current tab and carries typed values over when the tab changes.
 *
 * Layout: a keypad screen ([EditorStage.AMOUNT]) and a details shelf raised over the keypad ([EditorStage.DETAILS]).
 * New operations start on the keypad, existing ones with the shelf up.
 */
@Composable
fun TransactionEditScreen(
    onBack: () -> Unit,
    onDuplicate: ((Long, EditorMode) -> Unit)? = null,
    onNavigateCreateCategory: ((String) -> Unit)? = null,
    onOpenSchedule: ((Long) -> Unit)? = null,
    viewModel: TransactionEditViewModel = hiltViewModel(),
    transferViewModel: TransferEditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val transferState by transferViewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved, transferState.saved) {
        if (state.saved || transferState.saved) onBack()
    }

    // Expense/Income is derived from the loaded kind (an edited income opens with an unspecific route argument).
    var transferSelected by rememberSaveable { mutableStateOf(viewModel.initialMode == EditorMode.TRANSFER) }
    var transferTarget by rememberSaveable { mutableStateOf(TransferTarget.SENT) }
    val mode = when {
        transferSelected -> EditorMode.TRANSFER
        state.kind == TransactionKind.INCOME -> EditorMode.INCOME
        else -> EditorMode.EXPENSE
    }
    val isTransfer = mode == EditorMode.TRANSFER
    val isEdit = if (isTransfer) transferState.isEdit else state.isEdit
    val operationId = if (isTransfer) transferState.operationId else state.operationId

    var stage by rememberSaveable { mutableStateOf(initialStage(isEdit)) }
    // The view models do not persist the "save and another" choice, so it lives for this editor session.
    var addAnother by rememberSaveable { mutableStateOf(false) }
    val shelfScroll = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }
    val savedAnotherMessage = stringResource(R.string.transaction_form_saved_another)
    // Remembered so a configuration change does not replay the confirmation or drop the shelf again.
    var handledSavedAnother by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(state.savedAnotherCount) {
        if (state.savedAnotherCount > 0 && state.savedAnotherCount != handledSavedAnother) {
            handledSavedAnother = state.savedAnotherCount
            // Ready for the next entry: back to the keypad.
            stage = EditorStage.AMOUNT
            shelfScroll.scrollTo(0)
            snackbarHostState.showSnackbar(savedAnotherMessage)
        }
    }

    fun changeMode(new: EditorMode) {
        if (isEdit || new == mode) return
        when {
            new == EditorMode.TRANSFER -> {
                transferViewModel.prefill(
                    amountText = state.amountText,
                    accountId = state.selectedAccountId,
                    date = state.date.takeIf { state.dateTouched },
                    time = state.time.takeIf { state.dateTouched },
                    description = state.description,
                )
                transferTarget = TransferTarget.SENT
                transferSelected = true
            }
            isTransfer -> {
                viewModel.onKindChange(if (new == EditorMode.INCOME) TransactionKind.INCOME else TransactionKind.EXPENSE)
                viewModel.prefill(
                    amountText = transferState.sentText,
                    accountId = transferState.sourceAccountId,
                    date = transferState.date.takeIf { transferState.dateTouched },
                    time = transferState.time.takeIf { transferState.dateTouched },
                    description = transferState.description,
                )
                transferSelected = false
            }
            else -> viewModel.onKindChange(if (new == EditorMode.INCOME) TransactionKind.INCOME else TransactionKind.EXPENSE)
        }
    }

    val topBar: @Composable () -> Unit = {
        EditorTopBar(
            mode = mode,
            isEdit = isEdit,
            onClose = onBack,
            onModeChange = ::changeMode,
            onDuplicate = onDuplicate?.let { duplicate -> { duplicate(operationId, mode) } },
            onDelete = { if (isTransfer) transferViewModel.onDeleteRequest() else viewModel.onDeleteRequest() },
        )
    }
    val overlay: @Composable () -> Unit = { SnackbarHost(snackbarHostState) }

    if (isTransfer) {
        TransferEditor(
            state = transferState,
            target = transferTarget,
            onTargetChange = { transferTarget = it },
            actions = transferViewModel.formActions(),
            stage = stage,
            onStageChange = { stage = it },
            topBar = topBar,
            onSave = { transferViewModel.save() },
            shelfScroll = shelfScroll,
            onNavigateCreateCategory = onNavigateCreateCategory,
            overlay = overlay,
        )
        TransferDialogs(transferState, transferViewModel)
    } else {
        ExpenseIncomeEditor(
            state = state,
            actions = viewModel.formActions(),
            stage = stage,
            onStageChange = { stage = it },
            topBar = topBar,
            addAnother = addAnother,
            onAddAnotherChange = { addAnother = it },
            onSave = { viewModel.save(createAnother = addAnother && !state.isEdit) },
            shelfScroll = shelfScroll,
            onNavigateCreateCategory = onNavigateCreateCategory,
            onOpenSchedule = onOpenSchedule,
            overlay = overlay,
        )
        ExpenseIncomeDialogs(state, viewModel)
    }
}

// ---------------------------------------------------------------------------
// Expense / Income mode
// ---------------------------------------------------------------------------

internal class ExpenseIncomeActions(
    val onAmountChange: (String) -> Unit = {},
    val onAccountChange: (Long) -> Unit = {},
    val onCategoryChange: (Long?) -> Unit = {},
    val onDateChange: (LocalDate) -> Unit = {},
    val onTimeChange: (LocalTime) -> Unit = {},
    val onDescriptionChange: (String) -> Unit = {},
    val onToggleTag: (Long) -> Unit = {},
    val onAddPendingTag: (String) -> Unit = {},
    val onRemovePendingTag: (String) -> Unit = {},
)

internal fun TransactionEditViewModel.formActions() = ExpenseIncomeActions(
    onAmountChange = ::onAmountChange,
    onAccountChange = ::onAccountChange,
    onCategoryChange = ::onCategoryChange,
    onDateChange = ::onDateChange,
    onTimeChange = ::onTimeChange,
    onDescriptionChange = ::onDescriptionChange,
    onToggleTag = ::onToggleTag,
    onAddPendingTag = ::onAddPendingTag,
    onRemovePendingTag = ::onRemovePendingTag,
)

/** Keypad screen + details shelf for Expense and Income, with the pickers that belong to them. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExpenseIncomeEditor(
    state: TransactionEditUiState,
    actions: ExpenseIncomeActions,
    stage: EditorStage,
    onStageChange: (EditorStage) -> Unit,
    topBar: @Composable () -> Unit,
    addAnother: Boolean,
    onAddAnotherChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    shelfScroll: ScrollState,
    onNavigateCreateCategory: ((String) -> Unit)? = null,
    onOpenSchedule: ((Long) -> Unit)? = null,
    overlay: @Composable () -> Unit = {},
) {
    var pickAccount by rememberSaveable { mutableStateOf(false) }
    var pickCategory by rememberSaveable { mutableStateOf(false) }
    var pickDateTime by rememberSaveable { mutableStateOf(false) }

    val isIncome = state.kind == TransactionKind.INCOME
    val categoryType = if (isIncome) CategoryType.INCOME else CategoryType.EXPENSE
    val tree = remember(state.categories) { CategoryTree(state.categories) }
    val selectedAccount = state.selectedAccount
    val currency = state.currency
    val symbols = remember { DecimalFormatSymbols.getInstance() }

    val parsedMinor = (AmountParser.parse(state.amountText, currency) as? AmountParseResult.Value)?.minor
    val balance = selectedAccount?.let { state.balances[it.id] }
    val balanceAfter = if (!state.isEdit && balance != null && parsedMinor != null && parsedMinor > 0) {
        runCatching {
            val delta = Money(parsedMinor, balance.currency)
            if (isIncome) balance + delta else balance - delta
        }.getOrNull()
    } else {
        null
    }

    val amountError = when {
        state.isOverflow || TransactionDraftError.AMOUNT_OUT_OF_RANGE in state.errors ->
            stringResource(R.string.transaction_form_error_amount_range)
        TransactionDraftError.AMOUNT_INVALID in state.errors ->
            stringResource(R.string.transaction_form_error_amount_invalid)
        TransactionDraftError.AMOUNT_TOO_PRECISE in state.errors ->
            stringResource(R.string.transaction_form_error_amount_precise)
        TransactionDraftError.AMOUNT_NOT_POSITIVE in state.errors ->
            stringResource(R.string.transaction_form_error_amount_positive)
        else -> null
    }
    // A disabled button says what is missing instead of just greying out.
    val blocker = when {
        state.canSave -> null
        TransactionDraftError.AMOUNT_EMPTY in state.errors -> R.string.transaction_form_error_amount_empty
        state.selectedAccountId == null -> R.string.transaction_form_error_account_missing
        TransactionDraftError.CATEGORY_MISSING in state.errors -> R.string.transaction_form_error_category_missing
        else -> null
    }
    val ctaLabel = stringResource(blocker ?: if (isIncome) R.string.editor_save_income else R.string.editor_save_expense)

    EditorScaffold(
        stage = stage,
        onStageChange = onStageChange,
        topBar = topBar,
        amount = {
            EditorAmount(
                sign = if (isIncome) "+" else FORM_MINUS,
                amountText = state.amountText,
                currencySymbol = currency.symbol,
                symbols = symbols,
                isError = amountError != null,
            )
            if (amountError != null) {
                Text(amountError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            AccountPill(selectedAccount, balance, symbols, onClick = { pickAccount = true })
        },
        keypad = {
            QuickAmountRow(state.amountText, currency.symbol, actions.onAmountChange)
            NumberPad(
                decimalLabel = symbols.decimalSeparator.toString(),
                decimalEnabled = currency.scale > 0,
                onKey = { actions.onAmountChange(AmountKeypad.applyKey(state.amountText, it, currency.scale)) },
                onClear = { actions.onAmountChange("") },
            )
        },
        shelfKindLabel = stringResource(if (isIncome) R.string.transaction_kind_income else R.string.transaction_kind_expense),
        shelfScroll = shelfScroll,
        shelfContent = {
            val scheduleId = state.recurringScheduleId
            val scheduleName = state.recurringScheduleName
            if (scheduleId != null && scheduleName != null && onOpenSchedule != null) {
                TextButton(onClick = { onOpenSchedule(scheduleId) }) {
                    Icon(AppIcons.Recurring, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text(stringResource(R.string.transaction_form_created_by_schedule, scheduleName))
                }
            }
            GroupedCard(Modifier.fillMaxWidth()) {
                val (accountNote, accountNoteIsError) = accountSupporting(
                    archived = selectedAccount?.status == AccountStatus.ARCHIVED,
                    balance = balance,
                    balanceAfter = balanceAfter,
                    symbols = symbols,
                )
                AccountShelfRow(
                    label = stringResource(R.string.transaction_form_account),
                    account = selectedAccount,
                    balance = balance,
                    symbols = symbols,
                    supporting = accountNote,
                    supportingIsError = accountNoteIsError,
                    onClick = { pickAccount = true },
                )
                ShelfDivider()
                CategoryShelfRow(
                    tree = tree,
                    categoryType = categoryType,
                    selectedCategoryId = state.selectedCategoryId,
                    showUncategorized = state.isEdit && !state.hadCategoryBefore,
                    showError = TransactionDraftError.CATEGORY_MISSING in state.errors && state.amountText.isNotBlank(),
                    onClick = { pickCategory = true },
                )
                ShelfDivider()
                DateShelfRow(
                    date = state.date,
                    time = state.time,
                    isFuture = TransactionDraftError.DATE_IN_FUTURE in state.errors,
                    onOpenPicker = { pickDateTime = true },
                    onDateChange = actions.onDateChange,
                    onTimeChange = actions.onTimeChange,
                )
                ShelfDivider()
                NoteShelfRow(
                    label = stringResource(R.string.transaction_form_merchant_description),
                    hint = stringResource(R.string.transaction_form_description_hint),
                    description = state.description,
                    isTooLong = TransactionDraftError.DESCRIPTION_TOO_LONG in state.errors,
                    onChange = actions.onDescriptionChange,
                )
                ShelfDivider()
                TagsShelfRow(
                    allTags = state.allTags,
                    selectedTagIds = state.selectedTagIds,
                    pendingTagNames = state.pendingTagNames,
                    onToggleTag = actions.onToggleTag,
                    onAddPendingTag = actions.onAddPendingTag,
                    onRemovePendingTag = actions.onRemovePendingTag,
                )
            }
            RecentCategoriesRow(
                tree = tree,
                categoryType = categoryType,
                selectedCategoryId = state.selectedCategoryId,
                recentCategoryIds = state.recentCategoryIds,
                onSelectRecent = actions.onCategoryChange,
                onCreateCategory = onNavigateCreateCategory?.let { create -> { create(categoryType.name) } },
            )
        },
        shelfFooter = {
            if (!state.isEdit) AddAnotherRow(addAnother, onAddAnotherChange)
            Spacer(Modifier.height(8.dp))
            PrimaryButton(ctaLabel, onClick = onSave, enabled = state.canSave)
        },
        overlay = overlay,
    )

    DateTimePickerFlow(
        open = pickDateTime,
        date = state.date,
        time = state.time,
        maxDate = LocalDate.now(),
        onDismiss = { pickDateTime = false },
        onPicked = { date, time ->
            actions.onDateChange(date)
            actions.onTimeChange(time)
            pickDateTime = false
        },
    )

    if (pickAccount) {
        ModalBottomSheet(onDismissRequest = { pickAccount = false }) {
            AccountPickerContent(
                accounts = state.accounts,
                selectedAccountId = state.selectedAccountId,
                onSelect = {
                    actions.onAccountChange(it)
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
                onCreate = onNavigateCreateCategory?.let { nav -> { pickCategory = false; nav(categoryType.name) } },
                onSelect = {
                    actions.onCategoryChange(it)
                    pickCategory = false
                },
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp),
            )
        }
    }
}

@Composable
private fun ExpenseIncomeDialogs(state: TransactionEditUiState, viewModel: TransactionEditViewModel) {
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
                TextButton(onClick = viewModel::onConfirmCurrencyChange) {
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
                TextButton(onClick = viewModel::onConfirmOpeningOrder) {
                    Text(stringResource(R.string.action_confirm))
                }
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

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

@Preview(name = "New expense · light", showBackground = true, widthDp = 380, heightDp = 800)
@Composable
private fun NewExpenseLightPreview() = ExpenseIncomePreviewContent(dark = false, stage = EditorStage.AMOUNT)

@Preview(name = "New expense · dark", showBackground = true, widthDp = 380, heightDp = 800)
@Composable
private fun NewExpenseDarkPreview() = ExpenseIncomePreviewContent(dark = true, stage = EditorStage.AMOUNT)

@Preview(name = "Shelf open · light", showBackground = true, widthDp = 380, heightDp = 800)
@Composable
private fun ShelfOpenLightPreview() = ExpenseIncomePreviewContent(dark = false, stage = EditorStage.DETAILS)

@Preview(name = "Shelf open · dark", showBackground = true, widthDp = 380, heightDp = 800)
@Composable
private fun ShelfOpenDarkPreview() = ExpenseIncomePreviewContent(dark = true, stage = EditorStage.DETAILS)

@Composable
private fun ExpenseIncomePreviewContent(dark: Boolean, stage: EditorStage) {
    val usd = Currency("USD", 2, "$", "US Dollar")
    val account = Account(1, "Monobank", usd, AccountType.CASH, null, null, null, AccountStatus.ACTIVE, 0L)
    val state = TransactionEditUiState(
        amountText = "64.20",
        selectedAccountId = 1,
        accounts = listOf(PickableAccount(account, false)),
        balances = mapOf(1L to Money(112_000, usd)),
    )
    PreviewTheme(dark) {
        ExpenseIncomeEditor(
            state = state,
            actions = ExpenseIncomeActions(),
            stage = stage,
            onStageChange = {},
            topBar = { EditorTopBar(EditorMode.EXPENSE, isEdit = false, onClose = {}, onModeChange = {}, onDuplicate = null, onDelete = {}) },
            addAnother = false,
            onAddAnotherChange = {},
            onSave = {},
            shelfScroll = rememberScrollState(),
        )
    }
}
