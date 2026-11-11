package com.yehor.finora.ui.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yehor.finora.R
import com.yehor.finora.domain.account.Account
import com.yehor.finora.domain.account.AccountDetail
import com.yehor.finora.domain.account.AccountType
import com.yehor.finora.domain.account.OpeningBalance
import com.yehor.finora.domain.account.Balance
import com.yehor.finora.domain.model.AccountStatus
import com.yehor.finora.domain.money.CurrencyRegistry
import com.yehor.finora.domain.money.Money
import com.yehor.finora.domain.operation.TransactionItem
import com.yehor.finora.designsystem.chart.WaveChart
import com.yehor.finora.designsystem.component.button.RoundIconButton
import com.yehor.finora.designsystem.component.list.GroupedCard
import com.yehor.finora.designsystem.icon.AppIcons
import com.yehor.finora.designsystem.icon.IconCatalog
import com.yehor.finora.designsystem.theme.finance
import com.yehor.finora.designsystem.theme.grainyBackground
import com.yehor.finora.designsystem.theme.hairline
import com.yehor.finora.ui.analytics.signedMoney
import com.yehor.finora.designsystem.icon.IconSize
import com.yehor.finora.ui.common.TransactionRow
import com.yehor.finora.designsystem.theme.PreviewTheme
import java.text.DateFormat
import java.util.Date

internal fun formatDate(millis: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis))

@Composable
fun AccountDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onTransfer: (Long) -> Unit,
    onAddExpense: (Long) -> Unit,
    onAddIncome: (Long) -> Unit,
    onOpenTransaction: (TransactionItem) -> Unit,
    onShowAllTransactions: (Long) -> Unit,
    onOpenStatistics: (Long) -> Unit,
    viewModel: AccountDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // The account disappears after a successful delete.
    LaunchedEffect(state) {
        if (state is AccountDetailUiState.Missing) onBack()
    }

    val loaded = state as? AccountDetailUiState.Loaded
    if (loaded == null) {
        AccountDetailLoading(onBack)
        return
    }
    AccountDetailContent(
        state = loaded,
        onBack = onBack,
        onEdit = onEdit,
        onTransfer = onTransfer,
        onAddExpense = onAddExpense,
        onAddIncome = onAddIncome,
        onOpenTransaction = onOpenTransaction,
        onShowAllTransactions = onShowAllTransactions,
        onOpenStatistics = onOpenStatistics,
        onSetArchived = viewModel::setArchived,
        onDelete = viewModel::delete,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountDetailLoading(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.account_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(AppIcons.Back, contentDescription = stringResource(R.string.navigate_back))
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AccountDetailContent(
    state: AccountDetailUiState.Loaded,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onTransfer: (Long) -> Unit,
    onAddExpense: (Long) -> Unit,
    onAddIncome: (Long) -> Unit,
    onOpenTransaction: (TransactionItem) -> Unit,
    onShowAllTransactions: (Long) -> Unit,
    onOpenStatistics: (Long) -> Unit,
    onSetArchived: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var confirmArchive by rememberSaveable { mutableStateOf(false) }
    var menuOpen by rememberSaveable { mutableStateOf(false) }

    val detail = state.detail
    val account = detail.account
    val archived = account.status == AccountStatus.ARCHIVED

    val toggleArchive = {
        when {
            archived -> onSetArchived(false)
            archiveNeedsConfirmation(detail.balance) || state.scheduleCount > 0 -> confirmArchive = true
            else -> onSetArchived(true)
        }
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundIconButton(AppIcons.Back, stringResource(R.string.navigate_back), onBack)
                Text(
                    account.name.ifBlank { stringResource(R.string.account_detail_title) },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp).semantics { heading() },
                )
                Box {
                    RoundIconButton(AppIcons.More, stringResource(R.string.action_more_options), { menuOpen = true })
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_edit)) },
                            leadingIcon = { Icon(AppIcons.Edit, contentDescription = null) },
                            onClick = { menuOpen = false; onEdit(account.id) },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(stringResource(if (archived) R.string.action_unarchive else R.string.action_archive))
                            },
                            leadingIcon = { Icon(AppIcons.Archive, contentDescription = null) },
                            onClick = { menuOpen = false; toggleArchive() },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                            },
                            leadingIcon = {
                                Icon(AppIcons.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            },
                            onClick = { menuOpen = false; confirmDelete = true },
                        )
                    }
                }
            }
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                AccountHeaderCard(detail, archived, state.trend)

                if (archived) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(R.string.account_detail_archived_banner),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { onSetArchived(false) }) {
                                Text(stringResource(R.string.action_unarchive))
                            }
                        }
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    AccountRoundAction(
                        label = stringResource(R.string.transaction_kind_expense),
                        icon = AppIcons.Remove,
                        enabled = !archived,
                        onClick = { onAddExpense(account.id) },
                        modifier = Modifier.weight(1f),
                    )
                    AccountRoundAction(
                        label = stringResource(R.string.transaction_kind_income),
                        icon = AppIcons.Add,
                        enabled = !archived,
                        onClick = { onAddIncome(account.id) },
                        modifier = Modifier.weight(1f),
                    )
                    AccountRoundAction(
                        label = stringResource(R.string.account_detail_transfer),
                        icon = AppIcons.Transfer,
                        enabled = !archived,
                        onClick = { onTransfer(account.id) },
                        modifier = Modifier.weight(1f),
                    )
                    AccountRoundAction(
                        label = stringResource(R.string.action_statistics),
                        icon = AppIcons.NavStatistics,
                        onClick = { onOpenStatistics(account.id) },
                        modifier = Modifier.weight(1f),
                    )
                }

                val hasTransactions = state.recentTransactions.isNotEmpty()
                Row(
                    Modifier.fillMaxWidth().padding(start = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.account_detail_recent_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f).semantics { heading() },
                    )
                    if (hasTransactions) {
                        TextButton(onClick = { onShowAllTransactions(account.id) }) {
                            Text(stringResource(R.string.dashboard_see_all))
                        }
                    }
                }
                GroupedCard(Modifier.fillMaxWidth()) {
                    if (!hasTransactions) {
                        Row(
                            Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                AppIcons.Calendar,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(IconSize.Medium),
                            )
                            Text(
                                stringResource(R.string.account_detail_history_placeholder),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        state.recentTransactions.forEachIndexed { index, item ->
                            if (index > 0) {
                                HorizontalDivider(
                                    Modifier.padding(horizontal = 14.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                )
                            }
                            TransactionRow(item = item, onClick = { onOpenTransaction(item) })
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.account_delete_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.transactionCount == 0) {
                        Text(stringResource(R.string.account_delete_body))
                    } else {
                        Text(
                            pluralStringResource(
                                R.plurals.account_delete_body_with_transactions,
                                state.transactionCount, state.transactionCount,
                            ),
                        )
                    }
                    if (state.scheduleCount > 0) {
                        Text(
                            pluralStringResource(
                                R.plurals.account_delete_schedules,
                                state.scheduleCount, state.scheduleCount,
                            ),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
    if (confirmArchive) {
        val balanceText = (detail.balance as? Balance.Value)?.money?.format()
            ?: stringResource(R.string.accounts_balance_overflow)
        AlertDialog(
            onDismissRequest = { confirmArchive = false },
            title = { Text(stringResource(R.string.account_archive_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.account_archive_body, balanceText))
                    if (state.scheduleCount > 0) {
                        Text(stringResource(R.string.account_archive_schedules_note))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { confirmArchive = false; onSetArchived(true) }) {
                    Text(stringResource(R.string.action_archive))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmArchive = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

/** Card with a vertical gradient from the account color to the surface: type, balance, wave, opening, month change. */
@Composable
private fun AccountHeaderCard(detail: AccountDetail, archived: Boolean, trend: AccountTrend) {
    val account = detail.account
    val scheme = MaterialTheme.colorScheme
    val tint = accountTint(account)
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .grainyBackground(Brush.verticalGradient(listOf(tint.copy(alpha = 0.25f), scheme.surfaceContainerLow)), shape)
            .border(hairline(MaterialTheme.finance.isDark), shape)
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(36.dp).background(tint.copy(alpha = 0.18f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(IconCatalog.get(account.icon), contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            }
            Text(
                "${stringResource(account.type.labelRes())} · ${account.currency.code}".uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (archived) AccountPill(stringResource(R.string.account_status_archived))
        }
        Text(
            stringResource(R.string.account_detail_balance),
            style = MaterialTheme.typography.labelMedium,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 20.dp),
        )
        AccountBalanceAmount(
            detail.balance,
            MaterialTheme.typography.displaySmall.copy(fontSize = 34.sp, lineHeight = 42.sp, fontFeatureSettings = "tnum"),
        )
        if (trend.sparkline.size >= 2) {
            WaveChart(
                values = trend.sparkline,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(56.dp),
                color = tint,
                label = { Money(trend.sparkline[it], account.currency).format() },
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.Top) {
            val opening = detail.openingBalance
            Text(
                if (opening == null) {
                    stringResource(R.string.account_detail_no_initial_balance)
                } else {
                    stringResource(R.string.account_detail_initial_balance, formatDate(opening.occurredAt), opening.money.format())
                },
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).padding(end = 8.dp),
            )
            trend.monthChange?.takeIf { it != 0L }?.let { change ->
                val color = if (change > 0) MaterialTheme.finance.income else MaterialTheme.finance.negative
                Text(
                    stringResource(R.string.account_detail_this_month, signedMoney(Money(change, account.currency))),
                    style = MaterialTheme.typography.bodySmall,
                    color = color,
                )
            }
        }
        account.description?.takeIf { it.isNotBlank() }?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun AccountDetailLightPreview() = AccountDetailPreviewContent(dark = false, archived = false)

@Preview(showBackground = true, backgroundColor = 0xFF0F1412, heightDp = 900)
@Composable
private fun AccountDetailDarkPreview() = AccountDetailPreviewContent(dark = true, archived = true)

@Composable
private fun AccountDetailPreviewContent(dark: Boolean, archived: Boolean) {
    val usd = CurrencyRegistry.get("USD")
    val account = Account(
        1, "Main card", usd, AccountType.BANK, "wallet", null, "Everyday spending",
        if (archived) AccountStatus.ARCHIVED else AccountStatus.ACTIVE, 0,
    )
    PreviewTheme(dark) {
        AccountDetailContent(
            state = AccountDetailUiState.Loaded(
                detail = AccountDetail(account, Balance.Value(Money(4_620_000, usd)), OpeningBalance(1, Money(1_200_000, usd), 1_735_689_600_000L), null),
                transactionCount = 0,
                trend = AccountTrend(listOf(30, 28, 34, 26, 18, 22, 8, 6).map { 5_000_000L - it * 10_000L }, 324_000),
            ),
            onBack = {}, onEdit = {}, onTransfer = {}, onAddExpense = {}, onAddIncome = {}, onOpenTransaction = {},
            onShowAllTransactions = {}, onOpenStatistics = {}, onSetArchived = {}, onDelete = {},
        )
    }
}
