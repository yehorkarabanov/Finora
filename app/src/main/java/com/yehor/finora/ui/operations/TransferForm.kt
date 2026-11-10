package com.yehor.finora.ui.operations

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
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
import com.yehor.finora.domain.operation.TransferAmountParser
import com.yehor.finora.domain.operation.TransferDraftError
import com.yehor.finora.ui.common.AccountPickerContent
import com.yehor.finora.ui.common.AppIcons
import com.yehor.finora.ui.common.CategoryTreePickerContent
import com.yehor.finora.ui.common.ColorPalette
import com.yehor.finora.ui.common.DateTimePickerFlow
import com.yehor.finora.ui.common.IconBadge
import com.yehor.finora.ui.common.IconSize
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

    TransferAmountCard(
        state = state,
        target = target,
        onTargetChange = onTargetChange,
        actions = actions,
        symbols = symbols,
        onPickSide = { pickSide = it.name },
    )

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        CategoryCard(
            shape = FormGroupTopShape,
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
                onCreate = onNavigateCreateCategory?.let { nav -> { pickCategory = false; nav(CategoryType.TRANSFER.name) } },
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
    onPickSide: (AccountSide) -> Unit,
) {
    val source = state.sourceAccount
    val dest = state.destAccount
    val sentCurrency = state.sentCurrency()
    val receivedCurrency = state.receivedCurrency()
    val editingSent = target == TransferTarget.SENT
    val activeCurrency = if (editingSent) sentCurrency else receivedCurrency
    val activeText = if (editingSent) state.sentText else state.receivedText
    val onActiveChange = if (editingSent) actions.onSentChange else actions.onReceivedChange

    val sameAccounts = TransferDraftError.SAME_ACCOUNTS in state.errors
    val sentFailed = state.errors.any { it in SENT_ERRORS } || (state.isOverflow && editingSent)
    val receivedFailed = state.errors.any { it in RECEIVED_ERRORS } || (state.isOverflow && !editingSent)

    fun amountError(sent: Boolean, failed: Boolean): String? {
        if (!failed) return null
        val e = state.errors
        val range = if (sent) TransferDraftError.SENT_AMOUNT_OUT_OF_RANGE in e else TransferDraftError.RECEIVED_AMOUNT_OUT_OF_RANGE in e
        val invalid = if (sent) TransferDraftError.SENT_AMOUNT_INVALID in e else TransferDraftError.RECEIVED_AMOUNT_INVALID in e
        val precise = if (sent) TransferDraftError.SENT_AMOUNT_TOO_PRECISE in e else TransferDraftError.RECEIVED_AMOUNT_TOO_PRECISE in e
        return when {
            state.isOverflow || range -> "range"
            invalid -> "invalid"
            precise -> "precise"
            else -> "positive"
        }
    }

    @Composable
    fun errorText(kind: String?): String? = kind?.let {
        stringResource(
            when (it) {
                "range" -> R.string.transaction_form_error_amount_range
                "invalid" -> R.string.transaction_form_error_amount_invalid
                "precise" -> R.string.transaction_form_error_amount_precise
                else -> R.string.transaction_form_error_amount_positive
            },
        )
    }

    val sentErrorText = errorText(if (editingSent) amountError(true, sentFailed) else null)
    val receivedErrorText = errorText(if (!editingSent) amountError(false, receivedFailed) else null)

    val sameCurrency = source != null && dest != null && source.currency.code == dest.currency.code
    val currenciesKnownEqual = source == null || dest == null || sameCurrency

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
        receivedErrorText != null -> null
        currenciesKnownEqual && state.receivedLinked -> stringResource(R.string.transfer_form_same_as_sent)
        !currenciesKnownEqual && state.receivedText.isEmpty() ->
            stringResource(R.string.transfer_form_enter_received, receivedCurrency.code)
        else -> null
    }
    val showRelink = !state.receivedLinked && currenciesKnownEqual && state.sentText.isNotEmpty()

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
            val swapLabel = stringResource(R.string.transfer_form_swap)
            Column(Modifier.fillMaxWidth()) {
                TransferTile(
                    label = stringResource(R.string.transfer_form_from),
                    account = source,
                    balance = sourceBalance,
                    balanceAfter = sourceAfter,
                    sign = FORM_MINUS,
                    amountText = state.sentText,
                    currency = sentCurrency,
                    symbols = symbols,
                    active = editingSent,
                    isError = sameAccounts || sentFailed,
                    amountFailed = sentFailed,
                    note = sentErrorText,
                    noteIsError = sentErrorText != null,
                    showRelink = false,
                    swapClearance = true,
                    onRelink = {},
                    onPickAccount = { onPickSide(AccountSide.SOURCE) },
                    onActivate = { onTargetChange(TransferTarget.SENT) },
                )
                // The 8dp gap between the tiles; the swap button overflows it symmetrically, so it stays
                // centred on the gap whatever the tile heights are.
                Box(
                    Modifier.fillMaxWidth().height(8.dp).zIndex(1f),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Surface(
                        onClick = actions.onSwap,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        border = BorderStroke(3.dp, MaterialTheme.colorScheme.surfaceContainer),
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .requiredSize(50.dp)
                            .semantics { contentDescription = swapLabel },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                AppIcons.SwapVertical,
                                contentDescription = null,
                                modifier = Modifier.size(IconSize.Default),
                            )
                        }
                    }
                }
                TransferTile(
                    label = stringResource(R.string.transfer_form_to),
                    account = dest,
                    balance = destBalance,
                    balanceAfter = destAfter,
                    sign = "+",
                    amountText = state.receivedText,
                    currency = receivedCurrency,
                    symbols = symbols,
                    active = !editingSent,
                    isError = sameAccounts || receivedFailed,
                    amountFailed = receivedFailed,
                    note = receivedErrorText ?: receivedHint,
                    noteIsError = receivedErrorText != null,
                    showRelink = showRelink,
                    swapClearance = false,
                    onRelink = {
                        actions.onRelinkReceived()
                        onTargetChange(TransferTarget.SENT)
                    },
                    onPickAccount = { onPickSide(AccountSide.DEST) },
                    onActivate = { onTargetChange(TransferTarget.RECEIVED) },
                )
            }

            if (sameAccounts) {
                Text(
                    stringResource(R.string.transfer_form_error_same_accounts),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            // The rate is only meaningful across currencies and follows both typed amounts live.
            val rate = state.effectiveRate
            if (rate != null && !currenciesKnownEqual) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        trimRate(rate.forward, symbols),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        trimRate(rate.inverse, symbols),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
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

/** "1 USD = 41.2500 UAH" shown as "1 USD = 41.25 UAH": trailing zeros trimmed, at least two decimals kept. */
internal fun trimRate(rate: String, symbols: DecimalFormatSymbols): String =
    Regex("""(\d+)\.(\d+)""").replace(rate) { m ->
        val fraction = m.groupValues[2].trimEnd('0').padEnd(2, '0')
        m.groupValues[1] + symbols.decimalSeparator + fraction
    }

// ---------------------------------------------------------------------------
// From / To tile
// ---------------------------------------------------------------------------

/**
 * One side of the transfer: a label, the account selector with its balance, and the amount that side
 * receives the keypad input for. The active tile has a 2dp primary border and the blinking caret.
 */
@Composable
private fun TransferTile(
    label: String,
    account: Account?,
    balance: Money?,
    balanceAfter: Money?,
    sign: String,
    amountText: String,
    currency: Currency,
    symbols: DecimalFormatSymbols,
    active: Boolean,
    isError: Boolean,
    amountFailed: Boolean,
    note: String?,
    noteIsError: Boolean,
    showRelink: Boolean,
    swapClearance: Boolean,
    onRelink: () -> Unit,
    onPickAccount: () -> Unit,
    onActivate: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = colors.surfaceContainerHigh,
        border = when {
            isError -> BorderStroke(2.dp, colors.error)
            active -> BorderStroke(2.dp, colors.primary)
            else -> null
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)

            Surface(
                onClick = onPickAccount,
                shape = RoundedCornerShape(50),
                color = colors.surfaceContainerHighest,
                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
            ) {
                Row(
                    Modifier.padding(start = 6.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IconBadge(account?.icon, account?.color, ColorPalette.ACCOUNT_DEFAULT, size = 32)
                    Text(
                        account?.name ?: stringResource(R.string.transaction_form_account),
                        style = MaterialTheme.typography.titleSmall,
                        color = if (account == null) colors.onSurfaceVariant else colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    account?.let { Pill(it.currency.code) }
                    Spacer(Modifier.weight(1f))
                    Icon(
                        AppIcons.Dropdown,
                        contentDescription = null,
                        modifier = Modifier.size(IconSize.Medium),
                        tint = colors.onSurfaceVariant,
                    )
                }
            }

            val subtitle = when {
                account?.status == AccountStatus.ARCHIVED -> stringResource(R.string.account_detail_archived_banner)
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
                    color = if (balanceAfter != null && balanceAfter.amountMinor < 0) colors.error else colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            TransferTileAmount(
                sign = sign,
                text = amountText,
                currency = currency,
                symbols = symbols,
                active = active,
                isError = amountFailed,
                swapClearance = swapClearance,
                onClick = onActivate,
            )

            if (note != null || showRelink) {
                Row(
                    // The From tile reserves room on the right for the swap button that overlaps its bottom edge.
                    Modifier.padding(end = if (swapClearance) 56.dp else 0.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (note != null) {
                        Text(
                            note,
                            style = if (noteIsError) MaterialTheme.typography.bodySmall else MaterialTheme.typography.labelSmall,
                            color = if (noteIsError) colors.error else colors.onSurfaceVariant,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                    if (showRelink) {
                        SmallPill(text = stringResource(R.string.txn_transfer_same_amount), onClick = onRelink)
                    }
                }
            }
        }
    }
}

/** The signed amount of one tile. Tapping it makes this side the keypad target. */
@Composable
private fun TransferTileAmount(
    sign: String,
    text: String,
    currency: Currency,
    symbols: DecimalFormatSymbols,
    active: Boolean,
    isError: Boolean,
    swapClearance: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val color = when {
        isError -> colors.error
        text.isEmpty() -> colors.onSurfaceVariant.copy(alpha = 0.6f)
        active -> colors.primary
        else -> colors.onSurface
    }
    val caretAlpha by rememberInfiniteTransition(label = "tileCaret").animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(530), RepeatMode.Reverse),
        label = "tileCaretAlpha",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = if (swapClearance) 56.dp else 0.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .then(if (active) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier),
    ) {
        BasicText(
            text = sign + transferAmountDisplay(text, currency, symbols),
            maxLines = 1,
            style = MaterialTheme.typography.displaySmall.copy(
                fontWeight = FontWeight.SemiBold,
                fontFeatureSettings = "tnum",
                letterSpacing = (-0.5).sp,
                color = color,
            ),
            autoSize = TextAutoSize.StepBased(minFontSize = 20.sp, maxFontSize = 36.sp),
            modifier = Modifier.weight(1f, fill = false),
        )
        if (active) {
            Box(
                Modifier
                    .padding(start = 4.dp)
                    .width(2.dp)
                    .height(32.dp)
                    .alpha(caretAlpha)
                    .background(colors.primary),
            )
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
        state.sentCurrency().code != state.receivedCurrency().code && state.receivedText.isNotEmpty() -> stringResource(
            R.string.transfer_form_cta_amounts,
            transferAmountDisplay(state.sentText, state.sentCurrency(), symbols),
            transferAmountDisplay(state.receivedText, state.receivedCurrency(), symbols),
        )
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
private fun TransferFormLightPreview() = TransferFormPreviewContent(dark = false, sameCurrency = false)

@Preview(showBackground = true, backgroundColor = 0xFF0F1412, heightDp = 1500)
@Composable
private fun TransferFormDarkPreview() = TransferFormPreviewContent(dark = true, sameCurrency = false)

@Preview(showBackground = true, heightDp = 1500)
@Composable
private fun TransferFormSameCurrencyLightPreview() = TransferFormPreviewContent(dark = false, sameCurrency = true)

@Preview(showBackground = true, backgroundColor = 0xFF0F1412, heightDp = 1500)
@Composable
private fun TransferFormSameCurrencyDarkPreview() = TransferFormPreviewContent(dark = true, sameCurrency = true)

@Composable
private fun TransferFormPreviewContent(dark: Boolean, sameCurrency: Boolean) {
    val pln = Currency("PLN", 2, "zł", "Zloty")
    val usd = Currency("USD", 2, "$", "US Dollar")
    fun account(id: Long, name: String, currency: Currency) =
        Account(id, name, currency, AccountType.CASH, null, null, null, AccountStatus.ACTIVE, 0L)
    val cash = account(1, "Cash", pln)
    val destCurrency = if (sameCurrency) pln else usd
    val savings = account(2, "Savings", destCurrency)
    val received = if (sameCurrency) "100" else "25"
    val state = TransferEditUiState(
        sourceAccountId = 1,
        destAccountId = 2,
        sentText = "100",
        receivedText = received,
        receivedLinked = sameCurrency,
        allAccounts = listOf(cash, savings),
        sourceChoices = listOf(PickableAccount(cash, false)),
        destChoices = listOf(PickableAccount(savings, false)),
        balances = mapOf(1L to Money(250_000, pln), 2L to Money(80_000, destCurrency)),
        effectiveRate = TransferFormLogic.effectiveRate("100", received, pln, destCurrency),
    )
    PreviewTheme(dark) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TransferFormContent(
                state = state,
                target = if (sameCurrency) TransferTarget.SENT else TransferTarget.RECEIVED,
                onTargetChange = {},
                actions = TransferFormActions(),
            )
        }
    }
}
