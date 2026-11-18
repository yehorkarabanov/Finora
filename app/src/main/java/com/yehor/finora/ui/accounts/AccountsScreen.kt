package com.yehor.finora.ui.accounts

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yehor.finora.R
import com.yehor.finora.designsystem.chart.SplitBar
import com.yehor.finora.designsystem.chart.WaveChart
import com.yehor.finora.designsystem.component.button.RoundIconButton
import com.yehor.finora.designsystem.component.display.AmountText
import com.yehor.finora.designsystem.component.list.GroupedCard
import com.yehor.finora.designsystem.icon.AppIcons
import com.yehor.finora.designsystem.icon.IconCatalog
import com.yehor.finora.designsystem.motion.FinoraMotion
import com.yehor.finora.designsystem.motion.rememberReduceMotion
import com.yehor.finora.designsystem.theme.PillShape
import com.yehor.finora.designsystem.theme.PreviewTheme
import com.yehor.finora.designsystem.theme.finance
import com.yehor.finora.designsystem.theme.hairline
import com.yehor.finora.domain.account.Account
import com.yehor.finora.domain.account.AccountType
import com.yehor.finora.domain.account.AccountWithBalance
import com.yehor.finora.domain.account.Balance
import com.yehor.finora.domain.model.AccountStatus
import com.yehor.finora.domain.money.CurrencyRegistry
import com.yehor.finora.domain.money.Estimate
import com.yehor.finora.domain.money.EstimateBasis
import com.yehor.finora.domain.money.Money
import com.yehor.finora.ui.common.ColorPalette
import com.yehor.finora.ui.navigation.BindShellFab
import com.yehor.finora.ui.navigation.ShellFabClearance
import java.math.BigDecimal

@StringRes
fun AccountType.labelRes(): Int = when (this) {
    AccountType.BANK -> R.string.account_type_bank
    AccountType.CASH -> R.string.account_type_cash
    AccountType.SAVINGS -> R.string.account_type_savings
    AccountType.INVESTMENT -> R.string.account_type_investment
    AccountType.OTHER -> R.string.account_type_other
}

/** Balance text, negative in the error color. Overflow is an error text, never a number. */
@Composable
fun BalanceText(balance: Balance, modifier: Modifier = Modifier, large: Boolean = false) {
    val style = if (large) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium
    when (balance) {
        Balance.Overflow -> Text(
            stringResource(R.string.accounts_balance_overflow),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = modifier,
        )
        is Balance.Value -> Text(
            balance.money.format(),
            style = style.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.SemiBold,
            color = if (balance.money.amountMinor < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            modifier = modifier,
        )
    }
}

@Composable
fun AccountsScreen(
    onOpen: (Long) -> Unit,
    onAdd: () -> Unit,
    onAddTransfer: () -> Unit,
    viewModel: AccountsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AccountsContent(state, onOpen, onAdd, onAddTransfer, onRestore = viewModel::restore)
}

@Composable
fun AccountsContent(
    state: AccountsUiState,
    onOpen: (Long) -> Unit,
    onAdd: () -> Unit,
    onAddTransfer: () -> Unit,
    onRestore: (Long) -> Unit,
    initiallyExpanded: Boolean = false,
) {
    var archivedExpanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    val listState = rememberLazyListState()
    val isEmpty = !state.loading && state.active.isEmpty() && state.archived.isEmpty()
    if (!isEmpty) BindShellFab(listState, null, stringResource(R.string.fab_account), onAdd)

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.padding(start = 18.dp, end = 12.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.accounts_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            // A transfer needs two different active accounts.
            RoundIconButton(
                AppIcons.Transfer,
                stringResource(R.string.accounts_add_transfer),
                onAddTransfer,
                enabled = state.active.size >= 2,
            )
        }
        if (isEmpty) {
            Column(
                Modifier.fillMaxSize().padding(18.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                AccountsEmptyState(
                    icon = AppIcons.Account,
                    title = stringResource(R.string.accounts_empty_title),
                    body = stringResource(R.string.accounts_empty_body),
                    actionLabel = stringResource(R.string.accounts_add),
                    onAction = onAdd,
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 8.dp + ShellFabClearance),
            ) {
                state.valuation?.let { valuation ->
                    item(key = "header") { AccountsTotalHeader(state, valuation) }
                }
                if (state.active.isNotEmpty()) {
                    item(key = "active") {
                        GroupedCard(Modifier.fillMaxWidth()) {
                            state.active.forEachIndexed { index, item ->
                                if (index > 0) RowDivider()
                                AccountRow(item, state.trends[item.account.id], onOpen, onRestore = null)
                            }
                        }
                    }
                } else if (state.archived.isNotEmpty()) {
                    item(key = "no-active") {
                        GroupedCard(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(
                                    AppIcons.Account,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                                Text(
                                    stringResource(R.string.account_list_no_active_hint),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                if (state.archived.isNotEmpty()) {
                    item(key = "archived") {
                        ArchivedSection(
                            archived = state.archived,
                            expanded = archivedExpanded,
                            onToggle = { archivedExpanded = !archivedExpanded },
                            onOpen = onOpen,
                            onRestore = onRestore,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        Modifier.padding(horizontal = 14.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
}

/** "TOTAL · USD", the total, the account count and the split bar. No card behind it. */
@Composable
private fun AccountsTotalHeader(state: AccountsUiState, valuation: ValuationCardState) {
    val scheme = MaterialTheme.colorScheme
    val total = valuation.total
    val currencyCode = total?.currency?.code ?: state.displayCurrency?.code.orEmpty()
    Column(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 16.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.accounts_total, currencyCode).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
                val style = MaterialTheme.typography.displaySmall.copy(fontSize = 32.sp, lineHeight = 40.sp)
                if (total != null) {
                    val number = (if (valuation.estimated) "≈ " else "") +
                        BigDecimal.valueOf(total.amountMinor, total.currency.scale).toPlainString()
                    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.Bottom) {
                        AmountText(number, style, scheme.onSurface)
                        Text(
                            " ${total.currency.symbol}",
                            style = style.copy(fontSize = style.fontSize * 0.6f),
                            color = scheme.onSurface,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                } else {
                    Text(
                        stringResource(R.string.valuation_unavailable),
                        style = style,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            Text(
                pluralStringResource(R.plurals.accounts_count, state.active.size, state.active.size),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        ValuationNotes(valuation)
        if (state.shares.isNotEmpty()) {
            val finance = MaterialTheme.finance
            val parts = remember(state.active, state.shares, finance) {
                val colors = state.active.associate { it.account.id to finance.accent(ColorPalette.resolve(it.account.color, ColorPalette.ACCOUNT_DEFAULT)) }
                state.shares.mapNotNull { (id, share) -> colors[id]?.let { share to it } }
            }
            SplitBar(parts = parts, modifier = Modifier.padding(top = 14.dp))
        }
    }
}

/** Same minimum height for active and archived rows: the 48dp Restore box plus 2 x 8dp padding = 64dp. */
private val AccountRowMinHeight = 64.dp

@Composable
private fun AccountRow(
    item: AccountWithBalance,
    trend: AccountTrend?,
    onOpen: (Long) -> Unit,
    /** Non-null for archived rows: they are greyed, have no sparkline and offer Restore. */
    onRestore: ((Long) -> Unit)?,
) {
    val a = item.account
    val archived = onRestore != null
    val tint = if (archived) MaterialTheme.colorScheme.onSurfaceVariant else accountTint(a)
    val dim = if (archived) Modifier.alpha(0.6f) else Modifier
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) { onOpen(a.id) }
            .heightIn(min = AccountRowMinHeight)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            dim.size(36.dp).background(tint.copy(alpha = 0.14f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(IconCatalog.get(a.icon), contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        }
        Column(dim.weight(1f)) {
            Text(
                a.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${stringResource(a.type.labelRes())} · ${a.currency.code}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!archived) {
            val points = trend?.sparkline.orEmpty()
            // Fixed slot so rows don't jump when the series arrives. The wave's 16dp bleed holds only line caps.
            Box(Modifier.size(width = 52.dp, height = 22.dp)) {
                if (points.size >= 2) {
                    WaveChart(
                        values = points,
                        modifier = Modifier.fillMaxSize(),
                        sparkline = true,
                        color = tint,
                        label = { "" },
                    )
                }
            }
        }
        val balanceStyle = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum")
        if (onRestore != null) {
            // Beside the balance, so an archived row is no taller than an active one.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AccountBalanceAmount(item.balance, balanceStyle, dim.widthIn(min = 64.dp))
                RestoreChip(onClick = { onRestore(a.id) })
            }
        } else {
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.widthIn(min = 78.dp)) {
                AccountBalanceAmount(item.balance, balanceStyle)
                AccountChangeText(trend?.monthChange, a.currency)
            }
        }
    }
}

/** Mint "Restore" pill inside a 48dp touch target; the box, not the row, consumes the tap. */
@Composable
private fun RestoreChip(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier
            .heightIn(min = 48.dp)
            .widthIn(min = 48.dp)
            .clip(PillShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(R.string.accounts_restore),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = primary,
            maxLines = 1,
            modifier = Modifier
                .background(primary.copy(alpha = 0.12f), PillShape)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/** Quiet bottom row that expands into the greyed list of archived accounts. */
@Composable
private fun ArchivedSection(
    archived: List<AccountWithBalance>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpen: (Long) -> Unit,
    onRestore: (Long) -> Unit,
) {
    val reduce = rememberReduceMotion()
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = if (reduce) snap() else FinoraMotion.bouncy,
        label = "archivedChevron",
    )
    val stateText = stringResource(
        if (expanded) R.string.account_list_archived_expanded else R.string.account_list_archived_collapsed,
    )
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(20.dp)
    Column(Modifier.fillMaxWidth().padding(top = 18.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .border(hairline(MaterialTheme.finance.isDark), shape)
                .semantics(mergeDescendants = true) { stateDescription = stateText }
                .clickable(role = Role.Button, onClick = onToggle)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(36.dp).background(scheme.surfaceContainer, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.Archive, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.accounts_archived_row),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurfaceVariant,
                )
                Text(
                    pluralStringResource(R.plurals.accounts_hidden_from_totals, archived.size, archived.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            Icon(
                AppIcons.Expand,
                contentDescription = stringResource(if (expanded) R.string.action_collapse else R.string.action_expand),
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.rotate(rotation),
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = if (reduce) fadeIn(snap()) else expandVertically() + fadeIn(),
            exit = if (reduce) fadeOut(snap()) else shrinkVertically() + fadeOut(),
        ) {
            GroupedCard(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                archived.forEachIndexed { index, item ->
                    if (index > 0) RowDivider()
                    AccountRow(item, trend = null, onOpen = onOpen, onRestore = onRestore)
                }
            }
        }
    }
}

// ---- Previews ----

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun AccountsLightPreview() = AccountsPreviewContent(dark = false, sample = AccountsSample.Several)

@Preview(showBackground = true, backgroundColor = 0xFF0F1412, heightDp = 800)
@Composable
private fun AccountsDarkPreview() = AccountsPreviewContent(dark = true, sample = AccountsSample.Several)

@Preview(showBackground = true, heightDp = 500)
@Composable
private fun AccountsOneLightPreview() = AccountsPreviewContent(dark = false, sample = AccountsSample.One)

@Preview(showBackground = true, backgroundColor = 0xFF0F1412, heightDp = 500)
@Composable
private fun AccountsOneDarkPreview() = AccountsPreviewContent(dark = true, sample = AccountsSample.One)

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun AccountsArchivedLightPreview() =
    AccountsPreviewContent(dark = false, sample = AccountsSample.Several, archivedOpen = true)

@Preview(showBackground = true, backgroundColor = 0xFF0F1412, heightDp = 800)
@Composable
private fun AccountsArchivedDarkPreview() =
    AccountsPreviewContent(dark = true, sample = AccountsSample.Several, archivedOpen = true)

@Preview(showBackground = true)
@Composable
private fun AccountsEmptyLightPreview() = AccountsPreviewContent(dark = false, sample = AccountsSample.Empty)

@Preview(showBackground = true, backgroundColor = 0xFF0F1412)
@Composable
private fun AccountsEmptyDarkPreview() = AccountsPreviewContent(dark = true, sample = AccountsSample.Empty)

private enum class AccountsSample { Several, One, Empty }

private fun previewAccount(
    id: Long, name: String, code: String, type: AccountType, color: Long?,
    status: AccountStatus = AccountStatus.ACTIVE,
) = Account(id, name, CurrencyRegistry.get(code), type, null, color, null, status, 0)

@Composable
private fun AccountsPreviewContent(dark: Boolean, sample: AccountsSample, archivedOpen: Boolean = false) {
    fun bal(minor: Long, code: String) = Balance.Value(Money(minor, CurrencyRegistry.get(code)))
    val usd = CurrencyRegistry.get("USD")
    fun total(minor: Long) = ValuationCardState(Estimate(minor, usd, EstimateBasis.NATIVE), emptyList(), null)
    val state = when (sample) {
        AccountsSample.Empty -> AccountsUiState(loading = false)
        AccountsSample.One -> AccountsUiState(
            loading = false,
            active = listOf(AccountWithBalance(previewAccount(1, "Wallet", "USD", AccountType.CASH, 0xFFFFA05A), bal(47_800, "USD"))),
            valuation = total(47_800),
            displayCurrency = usd,
            shares = listOf(1L to 1f),
            trends = mapOf(1L to AccountTrend(listOf(40_000, 40_000, 44_000, 43_000, 47_800), 0L)),
        )
        AccountsSample.Several -> {
            val accounts = listOf(
                previewAccount(1, "Monobank", "USD", AccountType.BANK, 0xFF00E599),
                previewAccount(2, "Revolut", "USD", AccountType.BANK, 0xFFC4A0FF),
                previewAccount(3, "Wallet", "USD", AccountType.CASH, 0xFFFFA05A),
                previewAccount(4, "Savings", "USD", AccountType.SAVINGS, 0xFF78B4FF),
            )
            val balances = listOf(11_080_000L, 421_000L, 47_800L, 1_901_200L)
            AccountsUiState(
                loading = false,
                active = accounts.mapIndexed { i, acc -> AccountWithBalance(acc, bal(balances[i], "USD")) },
                archived = listOf(
                    AccountWithBalance(previewAccount(5, "Old PrivatBank", "USD", AccountType.BANK, null, AccountStatus.ARCHIVED), bal(0, "USD")),
                    AccountWithBalance(previewAccount(6, "Travel cash", "EUR", AccountType.CASH, null, AccountStatus.ARCHIVED), bal(3_500, "EUR")),
                ),
                valuation = total(balances.sum()),
                displayCurrency = usd,
                shares = accounts.mapIndexed { i, acc -> acc.id to balances[i].toFloat() / balances.sum() },
                trends = mapOf(
                    1L to AccountTrend(listOf(10, 12, 9, 15, 18, 22, 26), 324_000),
                    2L to AccountTrend(listOf(20, 18, 14, 15, 11, 9, 8), -12_000),
                    3L to AccountTrend(listOf(14, 14, 14, 14, 14), 0),
                    4L to AccountTrend(listOf(4, 6, 10, 16, 22, 24), 40_000),
                ),
            )
        }
    }
    PreviewTheme(dark) {
        AccountsContent(state, onOpen = {}, onAdd = {}, onAddTransfer = {}, onRestore = {}, initiallyExpanded = archivedOpen)
    }
}
