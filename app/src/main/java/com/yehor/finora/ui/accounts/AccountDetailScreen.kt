package com.yehor.finora.ui.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
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
import androidx.compose.ui.graphics.Color
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
import com.yehor.finora.domain.account.Balance
import com.yehor.finora.domain.model.AccountStatus
import com.yehor.finora.domain.money.CurrencyRegistry
import com.yehor.finora.domain.money.Money
import com.yehor.finora.domain.operation.TransactionItem
import com.yehor.finora.designsystem.icon.AppIcons
import com.yehor.finora.ui.common.ColorPalette
import com.yehor.finora.ui.common.IconBadge
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        account.name.ifBlank { stringResource(R.string.account_detail_title) },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(AppIcons.Back, contentDescription = stringResource(R.string.navigate_back))
                    }
                },
                actions = {
                    IconButton(onClick = { onEdit(account.id) }) {
                        Icon(AppIcons.Edit, contentDescription = stringResource(R.string.action_edit))
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(AppIcons.More, contentDescription = stringResource(R.string.action_more_options))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = {
                                    Text(stringResource(if (archived) R.string.action_unarchive else R.string.action_archive))
                                },
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
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AccountHeaderCard(detail, archived)

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

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AccountQuickAction(
                    label = stringResource(R.string.account_detail_transfer),
                    icon = AppIcons.Transfer,
                    container = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    enabled = !archived,
                    onClick = { onTransfer(account.id) },
                    modifier = Modifier.weight(1f),
                )
                AccountQuickAction(
                    label = stringResource(R.string.action_statistics),
                    icon = AppIcons.NavStatistics,
                    container = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    onClick = { onOpenStatistics(account.id) },
                    modifier = Modifier.weight(1f),
                )
                AccountQuickAction(
                    label = stringResource(R.string.action_edit),
                    icon = AppIcons.Edit,
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    onClick = { onEdit(account.id) },
                    modifier = Modifier.weight(1f),
                )
            }

            AccountsCard {
                val hasTransactions = state.recentTransactions.isNotEmpty()
                AccountsSectionHeader(
                    title = stringResource(R.string.account_detail_recent_title),
                    actionLabel = if (hasTransactions) stringResource(R.string.dashboard_see_all) else null,
                    onAction = if (hasTransactions) ({ onShowAllTransactions(account.id) }) else null,
                )
                if (!hasTransactions) {
                    Row(
                        Modifier.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            AppIcons.Calendar,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(IconSize.Default),
                        )
                        Text(
                            stringResource(R.string.account_detail_history_placeholder),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    state.recentTransactions.forEach { item ->
                        TransactionRow(
                            item = item,
                            onClick = { onOpenTransaction(item) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
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

@Composable
private fun AccountHeaderCard(detail: AccountDetail, archived: Boolean) {
    val account = detail.account
    AccountsCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            IconBadge(account.icon, account.color, ColorPalette.ACCOUNT_DEFAULT, size = 56)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(account.name, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "${stringResource(account.type.labelRes())} · ${account.currency.code}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (archived) AccountPill(stringResource(R.string.account_status_archived))
                }
            }
        }
        Text(
            stringResource(R.string.account_detail_balance),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp),
        )
        BalanceText(detail.balance, large = true)
        val opening = detail.openingBalance
        Text(
            if (opening == null) {
                stringResource(R.string.account_detail_no_initial_balance)
            } else {
                stringResource(R.string.account_detail_initial_balance, formatDate(opening.occurredAt), opening.money.format())
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
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
                detail = AccountDetail(account, Balance.Value(Money(-12_345, usd)), null, null),
                transactionCount = 0,
            ),
            onBack = {}, onEdit = {}, onTransfer = {}, onOpenTransaction = {},
            onShowAllTransactions = {}, onOpenStatistics = {}, onSetArchived = {}, onDelete = {},
        )
    }
}
