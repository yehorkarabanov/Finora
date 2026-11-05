package com.yehor.finora.ui.accounts

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yehor.finora.R
import com.yehor.finora.domain.account.Account
import com.yehor.finora.domain.account.AccountType
import com.yehor.finora.domain.account.AccountWithBalance
import com.yehor.finora.domain.account.Balance
import com.yehor.finora.domain.model.AccountStatus
import com.yehor.finora.domain.money.CurrencyRegistry
import com.yehor.finora.domain.money.Money
import com.yehor.finora.ui.common.ColorPalette
import com.yehor.finora.ui.common.IconBadge

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
            style = style,
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
    AccountsContent(state, onOpen, onAdd, onAddTransfer)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsContent(state: AccountsUiState, onOpen: (Long) -> Unit, onAdd: () -> Unit, onAddTransfer: () -> Unit) {
    var archivedExpanded by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.accounts_title)) }) },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // A transfer needs two different active accounts.
                if (state.active.size >= 2) {
                    SmallFloatingActionButton(onClick = onAddTransfer) {
                        Icon(Icons.Filled.SwapHoriz, contentDescription = stringResource(R.string.accounts_add_transfer))
                    }
                }
                FloatingActionButton(onClick = onAdd) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.accounts_add))
                }
            }
        },
    ) { padding ->
        if (!state.loading && state.active.isEmpty() && state.archived.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.accounts_empty_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.accounts_empty_body),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                TextButton(onClick = onAdd) { Text(stringResource(R.string.accounts_add)) }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.active, key = { it.account.id }) { AccountCard(it, onOpen) }
                if (state.archived.isNotEmpty()) {
                    item(key = "archived-header") {
                        Row(
                            Modifier.fillMaxWidth().clickable { archivedExpanded = !archivedExpanded }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(R.string.accounts_archived_section, state.archived.size),
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                if (archivedExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = stringResource(
                                    if (archivedExpanded) R.string.action_collapse else R.string.action_expand,
                                ),
                            )
                        }
                    }
                    if (archivedExpanded) {
                        items(state.archived, key = { it.account.id }) { AccountCard(it, onOpen, muted = true) }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountCard(item: AccountWithBalance, onOpen: (Long) -> Unit, muted: Boolean = false) {
    val a = item.account
    Card(
        onClick = { onOpen(a.id) },
        modifier = Modifier.fillMaxWidth().alpha(if (muted) 0.6f else 1f),
        colors = CardDefaults.cardColors(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(a.icon, a.color, ColorPalette.ACCOUNT_DEFAULT)
            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Text(a.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(a.type.labelRes()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            BalanceText(item.balance)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AccountsPreview() {
    fun acc(id: Long, name: String, code: String, type: AccountType, status: AccountStatus = AccountStatus.ACTIVE) =
        Account(id, name, CurrencyRegistry.get(code), type, "wallet", null, null, status, 0)
    MaterialTheme {
        AccountsContent(
            AccountsUiState(
                loading = false,
                active = listOf(
                    AccountWithBalance(acc(1, "Main card", "USD", AccountType.BANK), Balance.Value(Money(123_456, CurrencyRegistry.get("USD")))),
                    AccountWithBalance(acc(2, "Credit", "PLN", AccountType.BANK), Balance.Value(Money(-50_000, CurrencyRegistry.get("PLN")))),
                ),
                archived = emptyList(),
            ),
            onOpen = {}, onAdd = {}, onAddTransfer = {},
        )
    }
}
