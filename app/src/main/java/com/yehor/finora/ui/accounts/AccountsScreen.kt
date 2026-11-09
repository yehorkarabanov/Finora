package com.yehor.finora.ui.accounts

import androidx.annotation.StringRes
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.yehor.finora.ui.common.AppIcons
import com.yehor.finora.ui.common.ColorPalette
import com.yehor.finora.ui.common.IconBadge
import com.yehor.finora.ui.common.IconSize
import com.yehor.finora.ui.theme.PreviewTheme

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
    AccountsContent(state, onOpen, onAdd, onAddTransfer)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsContent(state: AccountsUiState, onOpen: (Long) -> Unit, onAdd: () -> Unit, onAddTransfer: () -> Unit) {
    var archivedExpanded by rememberSaveable { mutableStateOf(false) }
    val isEmpty = !state.loading && state.active.isEmpty() && state.archived.isEmpty()
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.accounts_title)) }) },
        floatingActionButton = {
            if (!isEmpty) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // A transfer needs two different active accounts.
                    if (state.active.size >= 2) {
                        SmallFloatingActionButton(onClick = onAddTransfer) {
                            Icon(AppIcons.Transfer, contentDescription = stringResource(R.string.accounts_add_transfer))
                        }
                    }
                    ExtendedFloatingActionButton(
                        onClick = onAdd,
                        icon = { Icon(AppIcons.Add, contentDescription = null) },
                        text = { Text(stringResource(R.string.accounts_add)) },
                    )
                }
            }
        },
    ) { padding ->
        if (isEmpty) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(16.dp),
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
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                state.valuation?.let { valuation ->
                    item(key = "valuation") {
                        ValuationCard(valuation, accountCount = state.active.size)
                    }
                }
                if (state.active.isNotEmpty()) {
                    item(key = "active") {
                        Column {
                            AccountsSectionHeader(title = stringResource(R.string.account_list_section_title))
                            AccountGroup(state.active, onOpen, muted = false)
                        }
                    }
                } else if (state.archived.isNotEmpty()) {
                    item(key = "no-active") {
                        AccountsCard {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(
                                    AppIcons.Account,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(IconSize.Default),
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
                        Column {
                            ArchivedHeader(
                                count = state.archived.size,
                                expanded = archivedExpanded,
                                onToggle = { archivedExpanded = !archivedExpanded },
                            )
                            if (archivedExpanded) {
                                AccountGroup(state.archived, onOpen, muted = true)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArchivedHeader(count: Int, expanded: Boolean, onToggle: () -> Unit) {
    val stateText = stringResource(
        if (expanded) R.string.account_list_archived_expanded else R.string.account_list_archived_collapsed,
    )
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .semantics(mergeDescendants = true) {
                heading()
                stateDescription = stateText
            }
            .clickable(role = Role.Button, onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.accounts_archived_section, count),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        Icon(
            if (expanded) AppIcons.Collapse else AppIcons.Expand,
            contentDescription = stringResource(if (expanded) R.string.action_collapse else R.string.action_expand),
        )
    }
}

/** One connected group of account rows. */
@Composable
private fun AccountGroup(items: List<AccountWithBalance>, onOpen: (Long) -> Unit, muted: Boolean) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        items.forEachIndexed { index, item ->
            AccountRow(item, groupShape(index, items.size), onOpen, muted)
        }
    }
}

@Composable
private fun AccountRow(
    item: AccountWithBalance,
    shape: androidx.compose.ui.graphics.Shape,
    onOpen: (Long) -> Unit,
    muted: Boolean,
) {
    val a = item.account
    Box(Modifier.alpha(if (muted) 0.6f else 1f)) {
        AccountSectionCard(shape = shape, onClick = { onOpen(a.id) }) {
            Row(
                Modifier.semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                IconBadge(a.icon, a.color, ColorPalette.ACCOUNT_DEFAULT, size = 44)
                Column(Modifier.weight(1f)) {
                    Text(
                        a.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            "${stringResource(a.type.labelRes())} · ${a.currency.code}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (a.status == AccountStatus.ARCHIVED) {
                            AccountPill(stringResource(R.string.account_status_archived))
                        }
                    }
                }
                BalanceText(item.balance)
                Icon(
                    AppIcons.Next,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(IconSize.Default),
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AccountsLightPreview() = AccountsPreviewContent(dark = false, empty = false)

@Preview(showBackground = true, backgroundColor = 0xFF0F1412)
@Composable
private fun AccountsDarkPreview() = AccountsPreviewContent(dark = true, empty = false)

@Preview(showBackground = true)
@Composable
private fun AccountsEmptyLightPreview() = AccountsPreviewContent(dark = false, empty = true)

@Preview(showBackground = true, backgroundColor = 0xFF0F1412)
@Composable
private fun AccountsEmptyDarkPreview() = AccountsPreviewContent(dark = true, empty = true)

@Composable
private fun AccountsPreviewContent(dark: Boolean, empty: Boolean) {
    fun acc(id: Long, name: String, code: String, type: AccountType, status: AccountStatus = AccountStatus.ACTIVE) =
        Account(id, name, CurrencyRegistry.get(code), type, "wallet", null, null, status, 0)
    PreviewTheme(dark) {
        AccountsContent(
            if (empty) {
                AccountsUiState(loading = false)
            } else {
                AccountsUiState(
                    loading = false,
                    active = listOf(
                        AccountWithBalance(acc(1, "Main card", "USD", AccountType.BANK), Balance.Value(Money(123_456, CurrencyRegistry.get("USD")))),
                        AccountWithBalance(acc(2, "Credit", "PLN", AccountType.BANK), Balance.Value(Money(-50_000, CurrencyRegistry.get("PLN")))),
                        AccountWithBalance(acc(3, "Cash", "EUR", AccountType.CASH), Balance.Value(Money(8_000, CurrencyRegistry.get("EUR")))),
                    ),
                    archived = listOf(
                        AccountWithBalance(acc(4, "Old savings", "USD", AccountType.SAVINGS, AccountStatus.ARCHIVED), Balance.Value(Money(0, CurrencyRegistry.get("USD")))),
                    ),
                )
            },
            onOpen = {}, onAdd = {}, onAddTransfer = {},
        )
    }
}
