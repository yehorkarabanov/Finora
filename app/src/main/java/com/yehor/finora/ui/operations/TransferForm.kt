package com.yehor.finora.ui.operations

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import com.yehor.finora.designsystem.component.sheet.FinoraSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.yehor.finora.R
import com.yehor.finora.designsystem.component.button.PrimaryButton
import com.yehor.finora.designsystem.component.button.RoundIconButton
import com.yehor.finora.designsystem.component.list.GroupedCard
import com.yehor.finora.designsystem.icon.AppIcons
import com.yehor.finora.designsystem.theme.PreviewTheme
import com.yehor.finora.domain.account.Account
import com.yehor.finora.domain.account.AccountType
import com.yehor.finora.domain.account.PickableAccount
import com.yehor.finora.domain.category.CategoryTree
import com.yehor.finora.domain.model.AccountStatus
import com.yehor.finora.domain.model.CategoryType
import com.yehor.finora.domain.money.Currency
import com.yehor.finora.domain.money.CurrencyRegistry
import com.yehor.finora.domain.money.Money
import com.yehor.finora.domain.operation.TransferAmountParser
import com.yehor.finora.domain.operation.TransferDraftError
import com.yehor.finora.ui.common.AccountPickerContent
import com.yehor.finora.ui.common.CategoryTreePickerContent
import com.yehor.finora.ui.common.DateTimePickerFlow
import com.yehor.finora.ui.common.PickerMode
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.LocalTime

// The Transfer mode of the unified editor: keypad screen, details shelf (From, To, They receive, ...), CTA and dialogs.

/** Which of the two transfer amounts the amount block and keypad currently edit. */
internal enum class TransferTarget { SENT, RECEIVED }

internal class TransferFormActions(
    val onSentChange: (String) -> Unit = {},
    val onReceivedChange: (String) -> Unit = {},
    val onRelinkReceived: () -> Unit = {},
    val onSourceChange: (Long) -> Unit = {},
    val onDestChange: (Long) -> Unit = {},
    val onSwap: () -> Unit = {},
    val onCategoryChange: (Long?) -> Unit = {},
    val onDateChange: (LocalDate) -> Unit = {},
    val onTimeChange: (LocalTime) -> Unit = {},
    val onDescriptionChange: (String) -> Unit = {},
    val onToggleTag: (Long) -> Unit = {},
    val onAddPendingTag: (String) -> Unit = {},
    val onRemovePendingTag: (String) -> Unit = {},
)

internal fun TransferEditViewModel.formActions() = TransferFormActions(
    onSentChange = ::onSentChange,
    onReceivedChange = ::onReceivedChange,
    onRelinkReceived = ::onRelinkReceived,
    onSourceChange = ::onSourceChange,
    onDestChange = ::onDestChange,
    onSwap = ::onSwap,
    onCategoryChange = ::onCategoryChange,
    onDateChange = ::onDateChange,
    onTimeChange = ::onTimeChange,
    onDescriptionChange = ::onDescriptionChange,
    onToggleTag = ::onToggleTag,
    onAddPendingTag = ::onAddPendingTag,
    onRemovePendingTag = ::onRemovePendingTag,
)

private enum class AccountSide { SOURCE, DEST }

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

private fun fallbackCurrency(state: TransferEditUiState): Currency =
    state.sourceAccount?.currency ?: state.destAccount?.currency ?: CurrencyRegistry.all.first()

internal fun TransferEditUiState.sentCurrency(): Currency = sourceAccount?.currency ?: fallbackCurrency(this)

internal fun TransferEditUiState.receivedCurrency(): Currency = destAccount?.currency ?: fallbackCurrency(this)

internal fun transferAmountDisplay(text: String, currency: Currency, symbols: DecimalFormatSymbols): String =
    currency.symbol + AmountKeypad.format(text, symbols.groupingSeparator, symbols.decimalSeparator)

/** The amount error of one side, or null; the overflow flag belongs to the side being edited. */
@Composable
private fun transferAmountError(state: TransferEditUiState, sent: Boolean, editingSent: Boolean): String? {
    val e = state.errors
    val overflow = state.isOverflow && editingSent == sent
    val failed = e.any { it in (if (sent) SENT_ERRORS else RECEIVED_ERRORS) } || overflow
    if (!failed) return null
    val range = if (sent) TransferDraftError.SENT_AMOUNT_OUT_OF_RANGE in e else TransferDraftError.RECEIVED_AMOUNT_OUT_OF_RANGE in e
    val invalid = if (sent) TransferDraftError.SENT_AMOUNT_INVALID in e else TransferDraftError.RECEIVED_AMOUNT_INVALID in e
    val precise = if (sent) TransferDraftError.SENT_AMOUNT_TOO_PRECISE in e else TransferDraftError.RECEIVED_AMOUNT_TOO_PRECISE in e
    return stringResource(
        when {
            overflow || range -> R.string.transaction_form_error_amount_range
            invalid -> R.string.transaction_form_error_amount_invalid
            precise -> R.string.transaction_form_error_amount_precise
            else -> R.string.transaction_form_error_amount_positive
        },
    )
}

/**
 * Keypad screen + details shelf for Transfer. The keypad edits the sent amount, or the received one while
 * [target] is [TransferTarget.RECEIVED] (chosen from the "They receive" row or the pill under the amount).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TransferEditor(
    state: TransferEditUiState,
    target: TransferTarget,
    onTargetChange: (TransferTarget) -> Unit,
    actions: TransferFormActions,
    stage: EditorStage,
    onStageChange: (EditorStage) -> Unit,
    topBar: @Composable () -> Unit,
    onSave: () -> Unit,
    shelfScroll: ScrollState,
    onNavigateCreateCategory: ((String) -> Unit)? = null,
    overlay: @Composable () -> Unit = {},
) {
    var pickSide by rememberSaveable { mutableStateOf<String?>(null) }
    var pickCategory by rememberSaveable { mutableStateOf(false) }
    var pickDateTime by rememberSaveable { mutableStateOf(false) }
    val tree = remember(state.categories) { CategoryTree(state.categories) }
    val symbols = remember { DecimalFormatSymbols.getInstance() }

    val source = state.sourceAccount
    val dest = state.destAccount
    val sentCurrency = state.sentCurrency()
    val receivedCurrency = state.receivedCurrency()
    val editingSent = target == TransferTarget.SENT
    val activeCurrency = if (editingSent) sentCurrency else receivedCurrency
    val activeText = if (editingSent) state.sentText else state.receivedText
    val onActiveChange = if (editingSent) actions.onSentChange else actions.onReceivedChange

    val sameAccounts = TransferDraftError.SAME_ACCOUNTS in state.errors
    val sentError = transferAmountError(state, sent = true, editingSent = editingSent)
    val receivedError = transferAmountError(state, sent = false, editingSent = editingSent)
    val activeError = if (editingSent) sentError else receivedError

    val sameCurrency = source != null && dest != null && source.currency.code == dest.currency.code
    val currenciesKnownEqual = source == null || dest == null || sameCurrency
    // "They receive" is its own row across currencies, and also while a same-currency amount was unlinked by hand;
    // a linked same-currency transfer offers "Different amount" (e.g. a fee) on the To row instead.
    val showReceiveRow = TransferFormLogic.showReceivedRow(source?.currency, dest?.currency, state.receivedLinked)
    val showDifferentAmount = TransferFormLogic.showDifferentAmountAction(source?.currency, dest?.currency, state.receivedLinked)

    val sentMinor = (TransferAmountParser.parse(state.sentText, sentCurrency) as? TransferAmountParser.Result.Value)?.minor
    val receivedMinor = (TransferAmountParser.parse(state.receivedText, receivedCurrency) as? TransferAmountParser.Result.Value)?.minor
    val sourceBalance = source?.let { state.balances[it.id] }
    val destBalance = dest?.let { state.balances[it.id] }
    val sourceAfter = if (!state.isEdit && sourceBalance != null && sentMinor != null) {
        runCatching { Money(Math.subtractExact(sourceBalance.amountMinor, sentMinor), sourceBalance.currency) }.getOrNull()
    } else {
        null
    }
    val destAfter = if (!state.isEdit && destBalance != null && receivedMinor != null) {
        runCatching { Money(Math.addExact(destBalance.amountMinor, receivedMinor), destBalance.currency) }.getOrNull()
    } else {
        null
    }

    val receivedHint: String? = when {
        receivedError != null -> null
        !currenciesKnownEqual && state.receivedText.isEmpty() ->
            stringResource(R.string.transfer_form_enter_received, receivedCurrency.code)
        else -> null
    }
    // The rate is only meaningful across currencies and follows both typed amounts live.
    val rateText = state.effectiveRate?.takeIf { !currenciesKnownEqual }?.let {
        trimRate(it.forward, symbols) + " · " + trimRate(it.inverse, symbols)
    }
    val showRelink = !state.receivedLinked && currenciesKnownEqual && state.sentText.isNotEmpty()
    val theyReceive = stringResource(R.string.editor_they_receive)
    val blocker = TransferFormLogic.blocker(state.errors)

    EditorScaffold(
        stage = stage,
        onStageChange = onStageChange,
        topBar = topBar,
        amount = { layout ->
            EditorAmount(
                sign = if (editingSent) FORM_MINUS else "+",
                amountText = activeText,
                currencySymbol = activeCurrency.symbol,
                symbols = symbols,
                isError = activeError != null,
                label = if (editingSent) null else theyReceive,
                maxFontSize = layout.amountMaxFontSize,
            )
            if (activeError != null) {
                Text(activeError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            AccountPill(
                account = if (editingSent) source else dest,
                balance = if (editingSent) sourceBalance else destBalance,
                symbols = symbols,
                onClick = { pickSide = (if (editingSent) AccountSide.SOURCE else AccountSide.DEST).name },
            )
            // Switches the keypad between the two amounts (also the way back after choosing "Different amount").
            if (showReceiveRow || !editingSent) {
                SmallPill(
                    text = if (editingSent) {
                        "$theyReceive  +" + transferAmountDisplay(state.receivedText, receivedCurrency, symbols)
                    } else {
                        stringResource(R.string.transfer_form_from) + "  " + FORM_MINUS +
                            transferAmountDisplay(state.sentText, sentCurrency, symbols)
                    },
                    onClick = { onTargetChange(if (editingSent) TransferTarget.RECEIVED else TransferTarget.SENT) },
                )
            }
        },
        keypad = { layout ->
            if (layout.showQuickAmounts) QuickAmountRow(activeText, activeCurrency.symbol, onActiveChange)
            NumberPad(
                decimalLabel = symbols.decimalSeparator.toString(),
                decimalEnabled = activeCurrency.scale > 0,
                onKey = { onActiveChange(AmountKeypad.applyKey(activeText, it, activeCurrency.scale)) },
                onClear = { onActiveChange("") },
                keyHeight = layout.keyHeight,
            )
        },
        shelfKindLabel = stringResource(R.string.transaction_kind_transfer),
        shelfScroll = shelfScroll,
        shelfContent = {
            GroupedCard(Modifier.fillMaxWidth()) {
                val (sourceNote, sourceNoteIsError) = accountSupporting(
                    archived = source?.status == AccountStatus.ARCHIVED,
                    balance = sourceBalance,
                    balanceAfter = sourceAfter,
                    symbols = symbols,
                )
                val sameAccountsText = stringResource(R.string.transfer_form_error_same_accounts)
                AccountShelfRow(
                    label = stringResource(R.string.transfer_form_from),
                    account = source,
                    balance = sourceBalance,
                    symbols = symbols,
                    supporting = if (sameAccounts) sameAccountsText else sourceNote,
                    supportingIsError = sameAccounts || sourceNoteIsError,
                    onClick = { pickSide = AccountSide.SOURCE.name },
                )
                ShelfDivider()
                val (destNote, destNoteIsError) = accountSupporting(
                    archived = dest?.status == AccountStatus.ARCHIVED,
                    balance = destBalance,
                    balanceAfter = destAfter,
                    symbols = symbols,
                )
                AccountShelfRow(
                    label = stringResource(R.string.transfer_form_to),
                    account = dest,
                    balance = destBalance,
                    symbols = symbols,
                    supporting = destNote,
                    supportingIsError = destNoteIsError,
                    onClick = { pickSide = AccountSide.DEST.name },
                    extraTrailing = {
                        RoundIconButton(AppIcons.SwapVertical, stringResource(R.string.transfer_form_swap), actions.onSwap)
                    },
                    below = if (showDifferentAmount) {
                        {
                            // Typing the received amount on the keypad unlinks it from the sent one.
                            SmallPill(
                                text = stringResource(R.string.editor_different_amount),
                                onClick = {
                                    onTargetChange(TransferTarget.RECEIVED)
                                    onStageChange(EditorStage.AMOUNT)
                                },
                                modifier = Modifier.padding(start = 48.dp),
                            )
                        }
                    } else {
                        null
                    },
                )
                if (showReceiveRow) {
                    ShelfDivider()
                    ShelfRow(
                        icon = { ShelfIcon(AppIcons.Currency) },
                        label = theyReceive,
                        onClick = {
                            onTargetChange(TransferTarget.RECEIVED)
                            onStageChange(EditorStage.AMOUNT)
                        },
                        supporting = receivedError ?: receivedHint ?: rateText,
                        supportingIsError = receivedError != null,
                        trailing = {
                            if (showRelink) {
                                SmallPill(
                                    text = stringResource(R.string.txn_transfer_same_amount),
                                    onClick = {
                                        actions.onRelinkReceived()
                                        onTargetChange(TransferTarget.SENT)
                                    },
                                )
                            }
                            ShelfValue(
                                "+" + transferAmountDisplay(state.receivedText, receivedCurrency, symbols),
                                muted = state.receivedText.isEmpty(),
                                color = if (receivedError != null) MaterialTheme.colorScheme.error else null,
                            )
                        },
                    )
                }
                ShelfDivider()
                CategoryShelfRow(
                    tree = tree,
                    categoryType = CategoryType.TRANSFER,
                    selectedCategoryId = state.selectedCategoryId,
                    showUncategorized = state.isEdit && !state.hadCategoryBefore,
                    showError = TransferDraftError.CATEGORY_MISSING in state.errors && state.sentText.isNotBlank(),
                    onClick = { pickCategory = true },
                )
                ShelfDivider()
                DateShelfRow(
                    date = state.date,
                    time = state.time,
                    isFuture = TransferDraftError.DATE_IN_FUTURE in state.errors,
                    onOpenPicker = { pickDateTime = true },
                    onDateChange = actions.onDateChange,
                    onTimeChange = actions.onTimeChange,
                )
                ShelfDivider()
                NoteShelfRow(
                    label = stringResource(R.string.transaction_form_description),
                    hint = stringResource(R.string.txn_transfer_note_hint),
                    description = state.description,
                    isTooLong = TransferDraftError.DESCRIPTION_TOO_LONG in state.errors,
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
                categoryType = CategoryType.TRANSFER,
                selectedCategoryId = state.selectedCategoryId,
                recentCategoryIds = state.recentCategoryIds,
                onSelectRecent = actions.onCategoryChange,
                onCreateCategory = onNavigateCreateCategory?.let { create -> { create(CategoryType.TRANSFER.name) } },
            )
        },
        shelfFooter = {
            PrimaryButton(transferCtaLabel(blocker), onClick = onSave, enabled = state.canSave)
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

    pickSide?.let { sideName ->
        val side = AccountSide.valueOf(sideName)
        FinoraSheet(onDismiss = { pickSide = null }) {
            AccountPickerContent(
                accounts = if (side == AccountSide.SOURCE) state.sourceChoices else state.destChoices,
                selectedAccountId = if (side == AccountSide.SOURCE) state.sourceAccountId else state.destAccountId,
                onSelect = {
                    if (side == AccountSide.SOURCE) actions.onSourceChange(it) else actions.onDestChange(it)
                    pickSide = null
                },
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }
    }

    if (pickCategory) {
        FinoraSheet(onDismiss = { pickCategory = false }) {
            CategoryTreePickerContent(
                tree = tree,
                type = CategoryType.TRANSFER,
                mode = PickerMode.Assign(),
                selectedId = state.selectedCategoryId,
                onCreate = onNavigateCreateCategory?.let { nav -> { pickCategory = false; nav(CategoryType.TRANSFER.name) } },
                onSelect = {
                    actions.onCategoryChange(it)
                    pickCategory = false
                },
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }
    }
}

/** "1 USD = 41.2500 UAH" shown as "1 USD = 41.25 UAH": trailing zeros trimmed, at least two decimals kept. */
internal fun trimRate(rate: String, symbols: DecimalFormatSymbols): String =
    Regex("""(\d+)\.(\d+)""").replace(rate) { m ->
        val fraction = m.groupValues[2].trimEnd('0').padEnd(2, '0')
        m.groupValues[1] + symbols.decimalSeparator + fraction
    }

/** The CTA says what is missing while it is disabled, otherwise "Save transfer". */
@Composable
internal fun transferCtaLabel(blocker: TransferBlocker?): String = stringResource(
    when (blocker) {
        TransferBlocker.SENT_EMPTY -> R.string.transaction_form_error_amount_empty
        TransferBlocker.SOURCE_MISSING -> R.string.txn_error_from_missing
        TransferBlocker.DEST_MISSING -> R.string.txn_error_to_missing
        TransferBlocker.SAME_ACCOUNTS -> R.string.transfer_form_error_same_accounts
        TransferBlocker.RECEIVED_EMPTY -> R.string.txn_error_received_empty
        TransferBlocker.CATEGORY_MISSING -> R.string.transaction_form_error_category_missing
        null -> R.string.editor_save_transfer
    },
)

// ---------------------------------------------------------------------------
// Dialogs
// ---------------------------------------------------------------------------

@Composable
internal fun TransferDialogs(state: TransferEditUiState, viewModel: TransferEditViewModel) {
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

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

@Preview(name = "Transfer, two currencies · light", showBackground = true, widthDp = 380, heightDp = 800)
@Composable
private fun TransferCrossCurrencyLightPreview() = TransferPreviewContent(dark = false, stage = EditorStage.DETAILS)

@Preview(name = "Transfer, two currencies · dark", showBackground = true, widthDp = 380, heightDp = 800)
@Composable
private fun TransferCrossCurrencyDarkPreview() = TransferPreviewContent(dark = true, stage = EditorStage.DETAILS)

@Preview(name = "Transfer keypad · light", showBackground = true, widthDp = 380, heightDp = 800)
@Composable
private fun TransferKeypadLightPreview() = TransferPreviewContent(dark = false, stage = EditorStage.AMOUNT)

@Preview(name = "Transfer keypad · dark", showBackground = true, widthDp = 380, heightDp = 800)
@Composable
private fun TransferKeypadDarkPreview() = TransferPreviewContent(dark = true, stage = EditorStage.AMOUNT)

@Preview(name = "Transfer keypad, 360×640 · light", showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun TransferKeypadSmallLightPreview() = TransferPreviewContent(dark = false, stage = EditorStage.AMOUNT)

@Preview(name = "Transfer keypad, 360×640 · dark", showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun TransferKeypadSmallDarkPreview() = TransferPreviewContent(dark = true, stage = EditorStage.AMOUNT)

@Composable
private fun TransferPreviewContent(dark: Boolean, stage: EditorStage) {
    val pln = Currency("PLN", 2, "zł", "Zloty")
    val usd = Currency("USD", 2, "$", "US Dollar")
    fun account(id: Long, name: String, currency: Currency) =
        Account(id, name, currency, AccountType.CASH, null, null, null, AccountStatus.ACTIVE, 0L)
    val cash = account(1, "Cash", pln)
    val savings = account(2, "Savings", usd)
    val state = TransferEditUiState(
        sourceAccountId = 1,
        destAccountId = 2,
        sentText = "100",
        receivedText = "25",
        receivedLinked = false,
        allAccounts = listOf(cash, savings),
        sourceChoices = listOf(PickableAccount(cash, false)),
        destChoices = listOf(PickableAccount(savings, false)),
        balances = mapOf(1L to Money(250_000, pln), 2L to Money(80_000, usd)),
        effectiveRate = TransferFormLogic.effectiveRate("100", "25", pln, usd),
    )
    PreviewTheme(dark) {
        TransferEditor(
            state = state,
            target = TransferTarget.SENT,
            onTargetChange = {},
            actions = TransferFormActions(),
            stage = stage,
            onStageChange = {},
            topBar = { EditorTopBar(EditorMode.TRANSFER, isEdit = false, onClose = {}, onModeChange = {}, onDuplicate = null, onDelete = {}) },
            onSave = {},
            shelfScroll = rememberScrollState(),
        )
    }
}
