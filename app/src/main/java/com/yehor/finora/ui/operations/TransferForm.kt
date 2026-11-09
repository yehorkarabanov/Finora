package com.yehor.finora.ui.operations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.yehor.finora.R
import com.yehor.finora.domain.account.Account
import com.yehor.finora.domain.account.PickableAccount
import com.yehor.finora.domain.category.CategoryTree
import com.yehor.finora.domain.model.AccountStatus
import com.yehor.finora.domain.account.AccountType
import com.yehor.finora.domain.model.CategoryType
import com.yehor.finora.domain.money.Currency
import com.yehor.finora.domain.money.CurrencyRegistry
import com.yehor.finora.domain.money.Money
import com.yehor.finora.domain.operation.TransferDraftError
import com.yehor.finora.ui.common.AccountPickerContent
import com.yehor.finora.ui.common.AppIcons
import com.yehor.finora.ui.common.CategoryTreePickerContent
import com.yehor.finora.ui.common.ColorPalette
import com.yehor.finora.ui.common.DateTimePickerFlow
import com.yehor.finora.ui.common.IconBadge
import com.yehor.finora.ui.common.PickerMode
import com.yehor.finora.ui.theme.PreviewTheme
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.LocalTime

// The Transfer mode of the unified editor: hero (amount + keypad), grouped fields, CTA and dialogs.

/** Which of the two transfer amounts the hero card and keypad currently edit. */
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
    AmountKeypad.format(text, symbols.groupingSeparator, symbols.decimalSeparator) + " " + currency.symbol

/** The hero card and every field card of Transfer mode, plus the pickers that belong to them. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TransferFormContent(
    state: TransferEditUiState,
    target: TransferTarget,
    onTargetChange: (TransferTarget) -> Unit,
    actions: TransferFormActions,
    onNavigateCreateCategory: ((String) -> Unit)? = null,
) {
    var pickSide by rememberSaveable { mutableStateOf<String?>(null) }
    var pickCategory by rememberSaveable { mutableStateOf(false) }
    var pickDateTime by rememberSaveable { mutableStateOf(false) }
    val tree = remember(state.categories) { CategoryTree(state.categories) }
    val symbols = remember { DecimalFormatSymbols.getInstance() }

    TransferAmountCard(state, target, onTargetChange, actions, symbols)

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        val sameAccounts = TransferDraftError.SAME_ACCOUNTS in state.errors
        Box {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TransferAccountRow(
                    shape = FormGroupTopShape,
                    label = stringResource(R.string.transfer_form_from),
                    account = state.sourceAccount,
                    balance = state.sourceAccount?.let { state.balances[it.id] },
                    isError = sameAccounts,
                    errorText = null,
                    symbols = symbols,
                    onClick = { pickSide = AccountSide.SOURCE.name },
                )
                TransferAccountRow(
                    shape = FormGroupMiddleShape,
                    label = stringResource(R.string.transfer_form_to),
                    account = state.destAccount,
                    balance = state.destAccount?.let { state.balances[it.id] },
                    isError = sameAccounts,
                    errorText = if (sameAccounts) stringResource(R.string.transfer_form_error_same_accounts) else null,
                    symbols = symbols,
                    onClick = { pickSide = AccountSide.DEST.name },
                )
            }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp),
            ) {
                IconButton(onClick = actions.onSwap, modifier = Modifier.size(44.dp)) {
                    Icon(AppIcons.SwapVertical, contentDescription = stringResource(R.string.transfer_form_swap))
                }
            }
        }

        CategoryCard(
            shape = FormGroupMiddleShape,
            tree = tree,
            categoryType = CategoryType.TRANSFER,
            selectedCategoryId = state.selectedCategoryId,
            showUncategorized = state.isEdit && !state.hadCategoryBefore,
            showError = TransferDraftError.CATEGORY_MISSING in state.errors && state.sentText.isNotBlank(),
            recentCategoryIds = state.recentCategoryIds,
            onClick = { pickCategory = true },
            onSelectRecent = actions.onCategoryChange,
            onCreateCategory = onNavigateCreateCategory?.let { create -> { create(CategoryType.TRANSFER.name) } },
        )

        DateTimeCard(
            shape = FormGroupMiddleShape,
            date = state.date,
            time = state.time,
            isFuture = TransferDraftError.DATE_IN_FUTURE in state.errors,
            onOpenPicker = { pickDateTime = true },
            onDateChange = actions.onDateChange,
            onTimeChange = actions.onTimeChange,
        )

        DescriptionCard(
            shape = FormGroupMiddleShape,
            label = stringResource(R.string.transaction_form_description),
            hint = stringResource(R.string.txn_transfer_note_hint),
            description = state.description,
            isTooLong = TransferDraftError.DESCRIPTION_TOO_LONG in state.errors,
            onChange = actions.onDescriptionChange,
        )

        TagsCard(
            allTags = state.allTags,
            selectedTagIds = state.selectedTagIds,
            pendingTagNames = state.pendingTagNames,
            onToggleTag = actions.onToggleTag,
            onAddPendingTag = actions.onAddPendingTag,
            onRemovePendingTag = actions.onRemovePendingTag,
        )
    }

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
        ModalBottomSheet(onDismissRequest = { pickSide = null }) {
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
        ModalBottomSheet(onDismissRequest = { pickCategory = false }) {
            CategoryTreePickerContent(
                tree = tree,
                type = CategoryType.TRANSFER,
                mode = PickerMode.Assign(),
                selectedId = state.selectedCategoryId,
                onSelect = {
                    actions.onCategoryChange(it)
                    pickCategory = false
                },
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Hero
// ---------------------------------------------------------------------------

@Composable
private fun TransferAmountCard(
    state: TransferEditUiState,
    target: TransferTarget,
    onTargetChange: (TransferTarget) -> Unit,
    actions: TransferFormActions,
    symbols: DecimalFormatSymbols,
) {
    val source = state.sourceAccount
    val dest = state.destAccount
    val sentCurrency = state.sentCurrency()
    val receivedCurrency = state.receivedCurrency()
    val editingSent = target == TransferTarget.SENT
    val activeCurrency = if (editingSent) sentCurrency else receivedCurrency
    val activeText = if (editingSent) state.sentText else state.receivedText
    val onActiveChange = if (editingSent) actions.onSentChange else actions.onReceivedChange

    val sentFailed = state.errors.any { it in SENT_ERRORS }
    val receivedFailed = state.errors.any { it in RECEIVED_ERRORS }
    val activeFailed = if (editingSent) sentFailed else receivedFailed
    val otherFailed = if (editingSent) receivedFailed else sentFailed

    val errorText: String? = if (state.isOverflow || activeFailed) {
        val e = state.errors
        val range: Boolean
        val invalid: Boolean
        val precise: Boolean
        if (editingSent) {
            range = TransferDraftError.SENT_AMOUNT_OUT_OF_RANGE in e
            invalid = TransferDraftError.SENT_AMOUNT_INVALID in e
            precise = TransferDraftError.SENT_AMOUNT_TOO_PRECISE in e
        } else {
            range = TransferDraftError.RECEIVED_AMOUNT_OUT_OF_RANGE in e
            invalid = TransferDraftError.RECEIVED_AMOUNT_INVALID in e
            precise = TransferDraftError.RECEIVED_AMOUNT_TOO_PRECISE in e
        }
        stringResource(
            when {
                state.isOverflow || range -> R.string.transaction_form_error_amount_range
                invalid -> R.string.transaction_form_error_amount_invalid
                precise -> R.string.transaction_form_error_amount_precise
                else -> R.string.transaction_form_error_amount_positive
            },
        )
    } else {
        null
    }

    val sameCurrency = source != null && dest != null && source.currency.code == dest.currency.code
    val currenciesKnownEqual = source == null || dest == null || sameCurrency
    val sourceName = source?.name ?: stringResource(R.string.transfer_form_from)
    val destName = dest?.name ?: stringResource(R.string.transfer_form_to)

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
            Text(
                text = if (editingSent) {
                    source?.let { stringResource(R.string.txn_transfer_sent_from, it.name) }
                        ?: stringResource(R.string.txn_transfer_sent)
                } else {
                    dest?.let { stringResource(R.string.txn_transfer_received_in, it.name) }
                        ?: stringResource(R.string.txn_transfer_received)
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            AmountDisplay(
                sign = "",
                signColor = MaterialTheme.colorScheme.primary,
                amountDisplay = transferAmountDisplay(activeText, activeCurrency, symbols),
                isError = errorText != null,
                isEmpty = activeText.isEmpty(),
            )
            if (errorText != null) {
                Text(errorText, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            state.effectiveRate?.let { rate ->
                Text(
                    stringResource(R.string.transfer_form_rate, rate.forward, rate.inverse),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            // The other amount: a tappable line that moves the keypad to it.
            val otherLine = if (editingSent) {
                when {
                    state.receivedLinked && currenciesKnownEqual -> stringResource(R.string.txn_transfer_received_same)
                    state.receivedText.isEmpty() -> stringResource(R.string.txn_transfer_received_enter, destName)
                    else -> stringResource(
                        R.string.txn_transfer_received_line,
                        destName,
                        transferAmountDisplay(state.receivedText, receivedCurrency, symbols),
                    )
                }
            } else {
                stringResource(
                    R.string.txn_transfer_sent_line,
                    sourceName,
                    if (state.sentText.isEmpty()) "—" else transferAmountDisplay(state.sentText, sentCurrency, symbols),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    onClick = { onTargetChange(if (editingSent) TransferTarget.RECEIVED else TransferTarget.SENT) },
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.weight(1f, fill = false),
                ) {
                    Text(
                        otherLine,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (otherFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
                // A hand-edited received amount can be handed back to the sent amount again.
                if (!editingSent && !state.receivedLinked && currenciesKnownEqual && state.sentText.isNotEmpty()) {
                    SmallPill(
                        text = stringResource(R.string.txn_transfer_same_amount),
                        onClick = {
                            actions.onRelinkReceived()
                            onTargetChange(TransferTarget.SENT)
                        },
                    )
                }
            }

            QuickAmountRow(activeText, activeCurrency.symbol, onActiveChange)

            NumberPad(
                decimalLabel = symbols.decimalSeparator.toString(),
                decimalEnabled = activeCurrency.scale > 0,
                onKey = { onActiveChange(AmountKeypad.applyKey(activeText, it, activeCurrency.scale)) },
                onClear = { onActiveChange("") },
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Account rows
// ---------------------------------------------------------------------------

@Composable
private fun TransferAccountRow(
    shape: androidx.compose.ui.graphics.Shape,
    label: String,
    account: Account?,
    balance: Money?,
    isError: Boolean,
    errorText: String?,
    symbols: DecimalFormatSymbols,
    onClick: () -> Unit,
) {
    SectionCard(shape = shape, onClick = onClick, isError = isError) {
        Row(
            // Leaves room for the swap control that floats over the right edge of both rows.
            modifier = Modifier.padding(end = 56.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconBadge(account?.icon, account?.color, ColorPalette.ACCOUNT_DEFAULT, size = 44)
            Column(Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        account?.name ?: stringResource(R.string.transaction_form_account),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (account == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    account?.let {
                        Spacer(Modifier.width(8.dp))
                        Pill(it.currency.code)
                    }
                }
                val subtitle = when {
                    errorText != null -> errorText
                    account?.status == AccountStatus.ARCHIVED -> stringResource(R.string.account_detail_archived_banner)
                    balance != null -> stringResource(R.string.transaction_form_balance, formatMoney(balance, symbols))
                    else -> null
                }
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (errorText != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// CTA
// ---------------------------------------------------------------------------

@Composable
internal fun transferCtaLabel(state: TransferEditUiState, blocker: TransferBlocker?): String {
    val symbols = remember { DecimalFormatSymbols.getInstance() }
    return when {
        blocker != null -> stringResource(
            when (blocker) {
                TransferBlocker.SENT_EMPTY -> R.string.transaction_form_error_amount_empty
                TransferBlocker.SOURCE_MISSING -> R.string.txn_error_from_missing
                TransferBlocker.DEST_MISSING -> R.string.txn_error_to_missing
                TransferBlocker.SAME_ACCOUNTS -> R.string.transfer_form_error_same_accounts
                TransferBlocker.RECEIVED_EMPTY -> R.string.txn_error_received_empty
                TransferBlocker.CATEGORY_MISSING -> R.string.transaction_form_error_category_missing
            },
        )
        state.isEdit -> stringResource(R.string.transaction_form_cta_save)
        else -> stringResource(R.string.txn_transfer_cta) + " — " +
            transferAmountDisplay(state.sentText, state.sentCurrency(), symbols)
    }
}

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

@Preview(showBackground = true, heightDp = 1500)
@Composable
private fun TransferFormLightPreview() = TransferFormPreviewContent(dark = false)

@Preview(showBackground = true, backgroundColor = 0xFF0F1412, heightDp = 1500)
@Composable
private fun TransferFormDarkPreview() = TransferFormPreviewContent(dark = true)

@Composable
private fun TransferFormPreviewContent(dark: Boolean) {
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
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TransferFormContent(
                state = state,
                target = TransferTarget.SENT,
                onTargetChange = {},
                actions = TransferFormActions(),
            )
        }
    }
}
