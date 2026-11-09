package com.yehor.finora.ui.operations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yehor.finora.R
import com.yehor.finora.domain.category.CategoryTree
import com.yehor.finora.domain.model.AccountStatus
import com.yehor.finora.domain.model.CategoryType
import com.yehor.finora.domain.money.AmountParseResult
import com.yehor.finora.domain.money.AmountParser
import com.yehor.finora.domain.money.Money
import com.yehor.finora.domain.operation.TransactionDraftError
import com.yehor.finora.domain.operation.TransactionKind
import com.yehor.finora.ui.common.AccountPickerContent
import com.yehor.finora.ui.common.AppIcons
import com.yehor.finora.ui.common.CategoryTreePickerContent
import com.yehor.finora.ui.common.ColorPalette
import com.yehor.finora.ui.common.DateTimePickerFlow
import com.yehor.finora.ui.common.IconBadge
import com.yehor.finora.ui.common.IconSize
import com.yehor.finora.ui.common.PickerMode
import com.yehor.finora.ui.theme.finance
import java.text.DecimalFormatSymbols
import java.time.LocalDate

/**
 * The unified editor for Expense, Income and Transfer. Two view models back it, both created from the same
 * back-stack entry: [TransactionEditViewModel] (Expense/Income) and [TransferEditViewModel] (Transfer).
 * The screen shows the one that matches the current tab and carries typed values over when the tab changes.
 */
@OptIn(ExperimentalMaterial3Api::class)
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

    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }
    val savedAnotherMessage = stringResource(R.string.transaction_form_saved_another)
    LaunchedEffect(state.savedAnotherCount) {
        if (state.savedAnotherCount > 0) {
            scrollState.animateScrollTo(0)
            snackbarHostState.showSnackbar(savedAnotherMessage)
        }
    }

    var menuOpen by remember { mutableStateOf(false) }

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

    Scaffold(
        modifier = Modifier.imePadding(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            when {
                                !isEdit -> R.string.transaction_form_new_title
                                isTransfer -> R.string.transfer_form_edit_title
                                else -> R.string.transaction_form_edit_title
                            },
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(AppIcons.Back, contentDescription = stringResource(R.string.navigate_back))
                    }
                },
                actions = {
                    if (isEdit) {
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
                                            onDuplicate(operationId, mode)
                                        },
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_delete)) },
                                    onClick = {
                                        menuOpen = false
                                        if (isTransfer) transferViewModel.onDeleteRequest() else viewModel.onDeleteRequest()
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (isTransfer) {
                val blocker = TransferFormLogic.blocker(transferState.errors)
                FormCtaBar(
                    label = transferCtaLabel(transferState, blocker),
                    enabled = transferState.canSave,
                    showCheck = blocker == null,
                    onClick = { transferViewModel.save() },
                    bottomPadding = 12.dp,
                )
            } else {
                ExpenseIncomeCtaBar(state, viewModel)
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val scheduleId = state.recurringScheduleId
            val scheduleName = state.recurringScheduleName
            if (!isTransfer && scheduleId != null && scheduleName != null && onOpenSchedule != null) {
                TextButton(onClick = { onOpenSchedule(scheduleId) }) {
                    Icon(AppIcons.Recurring, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text(stringResource(R.string.transaction_form_created_by_schedule, scheduleName))
                }
            }

            KindTabs(mode = mode, enabled = !isEdit, onModeChange = ::changeMode)

            if (isTransfer) {
                TransferFormContent(
                    state = transferState,
                    target = transferTarget,
                    onTargetChange = { transferTarget = it },
                    actions = transferViewModel.formActions(),
                    onNavigateCreateCategory = onNavigateCreateCategory,
                )
            } else {
                ExpenseIncomeContent(state, viewModel, onNavigateCreateCategory)
            }

            Spacer(Modifier.height(4.dp))
        }

        if (isTransfer) {
            TransferDialogs(transferState, transferViewModel)
        } else {
            ExpenseIncomeDialogs(state, viewModel)
        }
    }
}

// ---------------------------------------------------------------------------
// Expense / Income mode
// ---------------------------------------------------------------------------

@Composable
private fun ExpenseIncomeCtaBar(state: TransactionEditUiState, viewModel: TransactionEditViewModel) {
    val isIncome = state.kind == TransactionKind.INCOME
    val symbols = remember { DecimalFormatSymbols.getInstance() }
    val amountDisplay = AmountKeypad.format(state.amountText, symbols.groupingSeparator, symbols.decimalSeparator) +
        " " + state.currency.symbol
    // A disabled button says what is missing instead of just greying out.
    val blocker = when {
        state.canSave -> null
        TransactionDraftError.AMOUNT_EMPTY in state.errors -> R.string.transaction_form_error_amount_empty
        state.selectedAccountId == null -> R.string.transaction_form_error_account_missing
        TransactionDraftError.CATEGORY_MISSING in state.errors -> R.string.transaction_form_error_category_missing
        else -> null
    }
    val ctaLabel = when {
        blocker != null -> stringResource(blocker)
        state.isEdit -> stringResource(R.string.transaction_form_cta_save)
        else -> stringResource(
            if (isIncome) R.string.transaction_form_cta_add_income else R.string.transaction_form_cta_add_expense,
        ) + " — " + amountDisplay
    }
    FormCtaBar(
        label = ctaLabel,
        enabled = state.canSave,
        showCheck = blocker == null,
        onClick = { viewModel.save() },
        bottomPadding = if (state.isEdit) 12.dp else 0.dp,
        secondary = if (state.isEdit) {
            null
        } else {
            {
                TextButton(onClick = { viewModel.save(createAnother = true) }, enabled = state.canSave) {
                    Icon(AppIcons.AddCircle, contentDescription = null, modifier = Modifier.size(IconSize.Small))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.transaction_form_save_and_another))
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpenseIncomeContent(
    state: TransactionEditUiState,
    viewModel: TransactionEditViewModel,
    onNavigateCreateCategory: ((String) -> Unit)?,
) {
    var pickAccount by rememberSaveable { mutableStateOf(false) }
    var pickCategory by rememberSaveable { mutableStateOf(false) }
    var pickDateTime by rememberSaveable { mutableStateOf(false) }

    val isIncome = state.kind == TransactionKind.INCOME
    val categoryType = if (isIncome) CategoryType.INCOME else CategoryType.EXPENSE
    val tree = remember(state.categories) { CategoryTree(state.categories) }

    val selectedAccount = state.selectedAccount
    val currency = state.currency
    val kindColor = if (isIncome) MaterialTheme.finance.income else MaterialTheme.finance.expense
    val symbols = remember { DecimalFormatSymbols.getInstance() }
    val amountDisplay = AmountKeypad.format(state.amountText, symbols.groupingSeparator, symbols.decimalSeparator) +
        " " + currency.symbol

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

    AmountCard(
        amountText = state.amountText,
        amountDisplay = amountDisplay,
        sign = if (isIncome) "+" else FORM_MINUS,
        signColor = kindColor,
        currencyScale = currency.scale,
        currencySymbol = currency.symbol,
        accountLabel = selectedAccount?.let { "${it.currency.code} (${it.currency.symbol}) • ${it.name}" },
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
        decimalSeparator = symbols.decimalSeparator,
        onAccountClick = { pickAccount = true },
        onAmountChange = viewModel::onAmountChange,
    )

    // Account → tags: one connected group with a thin divider gap between rows.
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        SectionCard(shape = FormGroupTopShape, onClick = { pickAccount = true }) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconBadge(selectedAccount?.icon, selectedAccount?.color, ColorPalette.ACCOUNT_DEFAULT, size = 44)
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            selectedAccount?.name ?: stringResource(R.string.transaction_form_account),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        selectedAccount?.let {
                            Spacer(Modifier.width(8.dp))
                            Pill(it.currency.code)
                        }
                    }
                    val subtitle = when {
                        selectedAccount?.status == AccountStatus.ARCHIVED ->
                            stringResource(R.string.account_detail_archived_banner)
                        balance != null && balanceAfter != null -> stringResource(
                            R.string.transaction_form_balance_after,
                            formatMoney(balance, symbols),
                            formatMoney(balanceAfter, symbols),
                        )
                        balance != null -> stringResource(R.string.transaction_form_balance, formatMoney(balance, symbols))
                        else -> null
                    }
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (balanceAfter != null && balanceAfter.amountMinor < 0) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Icon(AppIcons.Next, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        CategoryCard(
            shape = FormGroupMiddleShape,
            tree = tree,
            categoryType = categoryType,
            selectedCategoryId = state.selectedCategoryId,
            showUncategorized = state.isEdit && !state.hadCategoryBefore,
            showError = TransactionDraftError.CATEGORY_MISSING in state.errors && state.amountText.isNotBlank(),
            recentCategoryIds = state.recentCategoryIds,
            onClick = { pickCategory = true },
            onSelectRecent = viewModel::onCategoryChange,
            onCreateCategory = onNavigateCreateCategory?.let { create -> { create(categoryType.name) } },
        )

        DateTimeCard(
            shape = FormGroupMiddleShape,
            date = state.date,
            time = state.time,
            isFuture = TransactionDraftError.DATE_IN_FUTURE in state.errors,
            onOpenPicker = { pickDateTime = true },
            onDateChange = viewModel::onDateChange,
            onTimeChange = viewModel::onTimeChange,
        )

        DescriptionCard(
            shape = FormGroupMiddleShape,
            label = stringResource(R.string.transaction_form_merchant_description),
            hint = stringResource(R.string.transaction_form_description_hint),
            description = state.description,
            isTooLong = TransactionDraftError.DESCRIPTION_TOO_LONG in state.errors,
            onChange = viewModel::onDescriptionChange,
        )

        TagsCard(
            allTags = state.allTags,
            selectedTagIds = state.selectedTagIds,
            pendingTagNames = state.pendingTagNames,
            onToggleTag = viewModel::onToggleTag,
            onAddPendingTag = viewModel::onAddPendingTag,
            onRemovePendingTag = viewModel::onRemovePendingTag,
        )
    }

    DateTimePickerFlow(
        open = pickDateTime,
        date = state.date,
        time = state.time,
        maxDate = LocalDate.now(),
        onDismiss = { pickDateTime = false },
        onPicked = { date, time ->
            viewModel.onDateChange(date)
            viewModel.onTimeChange(time)
            pickDateTime = false
        },
    )

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
// Amount hero + keypad (Expense / Income)
// ---------------------------------------------------------------------------

@Composable
private fun AmountCard(
    amountText: String,
    amountDisplay: String,
    sign: String,
    signColor: Color,
    currencyScale: Int,
    currencySymbol: String,
    accountLabel: String?,
    errorText: String?,
    decimalSeparator: Char,
    onAccountClick: () -> Unit,
    onAmountChange: (String) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val changeAccount = stringResource(R.string.transaction_form_change_account)
            Surface(
                onClick = onAccountClick,
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.semantics { contentDescription = "${accountLabel.orEmpty()}. $changeAccount" },
            ) {
                Row(
                    Modifier.padding(start = 14.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        accountLabel ?: stringResource(R.string.transaction_form_account),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Icon(
                        AppIcons.Dropdown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            AmountDisplay(
                sign = sign,
                signColor = signColor,
                amountDisplay = amountDisplay,
                isError = errorText != null,
                isEmpty = amountText.isEmpty(),
            )
            if (errorText != null) {
                Text(errorText, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            QuickAmountRow(amountText, currencySymbol, onAmountChange)

            NumberPad(
                decimalLabel = decimalSeparator.toString(),
                decimalEnabled = currencyScale > 0,
                onKey = { onAmountChange(AmountKeypad.applyKey(amountText, it, currencyScale)) },
                onClear = { onAmountChange("") },
            )
        }
    }
}
