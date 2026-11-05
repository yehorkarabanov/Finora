package com.yehor.finora.ui.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yehor.finora.R
import com.yehor.finora.domain.account.Balance
import com.yehor.finora.domain.model.AccountStatus
import com.yehor.finora.domain.operation.TransactionItem
import com.yehor.finora.ui.common.ColorPalette
import com.yehor.finora.ui.common.IconBadge
import com.yehor.finora.ui.common.TransactionRow
import java.text.DateFormat
import java.util.Date

internal fun formatDate(millis: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onTransfer: (Long) -> Unit,
    onOpenTransaction: (TransactionItem) -> Unit,
    onShowAllTransactions: (Long) -> Unit,
    viewModel: AccountDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var confirmArchive by rememberSaveable { mutableStateOf(false) }

    // The account disappears after a successful delete.
    LaunchedEffect(state) {
        if (state is AccountDetailUiState.Missing) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.account_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_back))
                    }
                },
            )
        },
    ) { padding ->
        val loaded = state as? AccountDetailUiState.Loaded
        if (loaded == null) {
            Box(Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }
        val detail = loaded.detail
        val account = detail.account
        val archived = account.status == AccountStatus.ARCHIVED

        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (archived) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Text(
                        stringResource(R.string.account_detail_archived_banner),
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(account.icon, account.color, ColorPalette.ACCOUNT_DEFAULT, size = 56)
                Column(Modifier.padding(start = 16.dp)) {
                    Text(account.name, style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${stringResource(account.type.labelRes())} · ${account.currency.code}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column {
                Text(
                    stringResource(R.string.account_detail_balance),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                BalanceText(detail.balance, large = true)
            }
            val opening = detail.openingBalance
            Text(
                if (opening == null) {
                    stringResource(R.string.account_detail_no_initial_balance)
                } else {
                    stringResource(R.string.account_detail_initial_balance, formatDate(opening.occurredAt), opening.money.format())
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            account.description?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

            HorizontalDivider()
            if (loaded.recentTransactions.isEmpty()) {
                Text(
                    stringResource(R.string.account_detail_history_placeholder),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column {
                    loaded.recentTransactions.forEach { item ->
                        TransactionRow(item = item, onClick = { onOpenTransaction(item) })
                    }
                    TextButton(onClick = { onShowAllTransactions(account.id) }) {
                        Text(stringResource(R.string.action_show_transactions))
                    }
                }
            }
            HorizontalDivider()

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Button(onClick = { onEdit(account.id) }) { Text(stringResource(R.string.action_edit)) }
                if (!archived) {
                    FilledTonalButton(onClick = { onTransfer(account.id) }) {
                        Text(stringResource(R.string.account_detail_transfer))
                    }
                }
                OutlinedButton(onClick = {
                    when {
                        archived -> viewModel.setArchived(false)
                        archiveNeedsConfirmation(detail.balance) -> confirmArchive = true
                        else -> viewModel.setArchived(true)
                    }
                }) {
                    Text(stringResource(if (archived) R.string.action_unarchive else R.string.action_archive))
                }
                OutlinedButton(onClick = { confirmDelete = true }, enabled = loaded.canDelete) {
                    Text(stringResource(R.string.action_delete))
                }
            }
            if (!loaded.canDelete) {
                Text(
                    pluralStringResource(
                        R.plurals.account_detail_delete_blocked, loaded.transactionCount, loaded.transactionCount,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text(stringResource(R.string.account_delete_title)) },
                text = { Text(stringResource(R.string.account_delete_body)) },
                confirmButton = {
                    TextButton(onClick = { confirmDelete = false; viewModel.delete() }) {
                        Text(stringResource(R.string.action_delete))
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
                text = { Text(stringResource(R.string.account_archive_body, balanceText)) },
                confirmButton = {
                    TextButton(onClick = { confirmArchive = false; viewModel.setArchived(true) }) {
                        Text(stringResource(R.string.action_archive))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { confirmArchive = false }) { Text(stringResource(R.string.action_cancel)) }
                },
            )
        }
    }
}
