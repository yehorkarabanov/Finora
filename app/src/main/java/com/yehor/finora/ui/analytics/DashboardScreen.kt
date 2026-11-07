package com.yehor.finora.ui.analytics

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yehor.finora.R
import com.yehor.finora.domain.analytics.Cell
import com.yehor.finora.domain.analytics.CurrencyScope
import com.yehor.finora.domain.account.AccountWithBalance
import com.yehor.finora.domain.model.CategoryType
import com.yehor.finora.domain.model.OperationType
import com.yehor.finora.domain.operation.TransactionFilter
import com.yehor.finora.domain.operation.TransactionItem
import com.yehor.finora.ui.accounts.BalanceText
import com.yehor.finora.ui.accounts.ValuationCard
import com.yehor.finora.ui.common.ColorPalette
import com.yehor.finora.ui.common.IconBadge
import com.yehor.finora.ui.common.TransactionRow
import com.yehor.finora.ui.recurring.formatScheduleDate
import com.yehor.finora.domain.analytics.UpcomingItem
import com.yehor.finora.domain.operation.TransactionKind

/** Where the Dashboard can send the user. */
data class DashboardActions(
    val onAddTransaction: () -> Unit,
    val onAddAccount: () -> Unit,
    val onOpenAccounts: () -> Unit,
    val onOpenAccountHistory: (Long) -> Unit,
    val onOpenTransactions: (TransactionFilter) -> Unit,
    val onOpenStatistics: () -> Unit,
    val onOpenTransaction: (TransactionItem) -> Unit,
    val onOpenSchedule: (Long) -> Unit,
    val onOpenRecurring: () -> Unit,
)

@Composable
fun DashboardScreen(actions: DashboardActions, viewModel: DashboardViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    DashboardContent(state, actions, viewModel::selectPeriod, viewModel::setCustomPeriod)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardContent(
    state: DashboardUiState,
    actions: DashboardActions,
    onSelectPeriod: (com.yehor.finora.domain.analytics.PeriodKind) -> Unit,
    onCustomPeriod: (java.time.LocalDate, java.time.LocalDate) -> Unit,
) {
    var currencySheet by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_dashboard)) }) },
        floatingActionButton = {
            if (state.hasAccounts) {
                FloatingActionButton(onClick = actions.onAddTransaction) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.transactions_add))
                }
            }
        },
    ) { padding ->
        val period = state.period
        when {
            state.loading || period == null -> Unit
            !state.hasAccounts -> FirstAccountCard(actions.onAddAccount, Modifier.padding(padding).padding(16.dp))
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "period") {
                    PeriodSelector(state.periodState, period, onSelectPeriod, onCustomPeriod)
                }
                state.valuation?.let { valuation ->
                    item(key = "valuation") {
                        ValuationCard(valuation, Modifier.clickable(onClick = actions.onOpenAccounts))
                    }
                }
                item(key = "flows") { FlowCards(state, period, actions) { currencySheet = true } }
                item(key = "accounts") { AccountsSection(state.activeAccounts, actions) }
                item(key = "distribution") { DistributionSection(state, actions) }
                item(key = "recent") { RecentSection(state.recent, actions) }
                if (state.upcoming.hasActiveSchedules) {
                    item(key = "upcoming") { UpcomingSection(state.upcoming, actions) }
                }
            }
        }
    }
    if (currencySheet) {
        CurrencyFlowSheet(state.currencyFlows) { currencySheet = false }
    }
}

@Composable
private fun FirstAccountCard(onAdd: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth().clickable(onClick = onAdd)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.dashboard_first_account_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.accounts_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FlowCards(
    state: DashboardUiState,
    period: com.yehor.finora.domain.analytics.ResolvedPeriod,
    actions: DashboardActions,
    onByCurrency: () -> Unit,
) {
    val display = state.display ?: return
    val scope = CurrencyScope.Display(display)
    val base = AnalyticsFilters.base(period, scope, emptyList())
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FlowCard(
                R.string.transaction_kind_income, state.income, Modifier.weight(1f),
                onClick = { actions.onOpenTransactions(base.copy(types = setOf(OperationType.INCOME))) },
            )
            FlowCard(
                R.string.transaction_kind_expense, state.expense, Modifier.weight(1f),
                onClick = { actions.onOpenTransactions(base.copy(types = setOf(OperationType.EXPENSE))) },
            )
        }
        FlowCard(
            R.string.dashboard_net, state.net, Modifier.fillMaxWidth(), signed = true,
            onClick = { actions.onOpenTransactions(base.copy(types = setOf(OperationType.INCOME, OperationType.EXPENSE))) },
        )
        ConversionNote(state.conversion, scope)
        if (state.currencyFlows.size > 1 || state.conversion.converted) {
            TextButton(onClick = onByCurrency) { Text(stringResource(R.string.dashboard_by_currency)) }
        }
    }
}

@Composable
private fun FlowCard(
    titleRes: Int,
    cell: Cell?,
    modifier: Modifier,
    signed: Boolean = false,
    onClick: () -> Unit,
) {
    Card(modifier.clickable(onClick = onClick), colors = CardDefaults.cardColors()) {
        Column(Modifier.padding(12.dp)) {
            Text(
                stringResource(titleRes),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AmountText(
                cell,
                style = MaterialTheme.typography.titleLarge,
                color = if (signed) netColor(cell) else androidx.compose.ui.graphics.Color.Unspecified,
                signed = signed,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CurrencyFlowSheet(flows: List<CurrencyFlow>, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.dashboard_by_currency), style = MaterialTheme.typography.titleLarge)
            flows.forEach { flow ->
                Column {
                    Text(flow.currency.code, style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(R.string.valuation_converted_income, flow.income?.format() ?: stringResource(R.string.valuation_unavailable)),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        stringResource(R.string.valuation_converted_expense, flow.expense?.format() ?: stringResource(R.string.valuation_unavailable)),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        stringResource(R.string.valuation_converted_net, flow.net?.format() ?: stringResource(R.string.valuation_unavailable)),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountsSection(accounts: List<AccountWithBalance>, actions: DashboardActions) {
    Column {
        SectionTitle(stringResource(R.string.nav_accounts))
        accounts.forEach { item ->
            val account = item.account
            ListItem(
                modifier = Modifier.clickable { actions.onOpenAccountHistory(account.id) },
                leadingContent = { IconBadge(account.icon, account.color, ColorPalette.ACCOUNT_DEFAULT, size = 36) },
                headlineContent = { Text(account.name, maxLines = 1) },
                supportingContent = { Text(account.currency.code, style = MaterialTheme.typography.bodySmall) },
                trailingContent = { BalanceText(item.balance) },
            )
        }
        TextButton(onClick = actions.onOpenAccounts) { Text(stringResource(R.string.dashboard_manage_accounts)) }
    }
}

@Composable
private fun DistributionSection(state: DashboardUiState, actions: DashboardActions) {
    val report = state.distribution ?: return
    Column {
        SectionTitle(stringResource(R.string.dashboard_distribution))
        val entries = donutEntries(report, CategoryType.EXPENSE)
        if (entries.isEmpty()) {
            Text(
                stringResource(R.string.dashboard_no_expenses),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                CategoryDonut(
                    title = stringResource(R.string.transaction_kind_expense),
                    entries = entries,
                    centerText = cellText(report.total),
                )
                DonutLegend(entries, Modifier.weight(1f))
            }
            TextButton(onClick = actions.onOpenStatistics) { Text(stringResource(R.string.dashboard_details)) }
        }
    }
}

@Composable
private fun RecentSection(recent: List<TransactionItem>, actions: DashboardActions) {
    Column {
        SectionTitle(stringResource(R.string.dashboard_recent))
        if (recent.isEmpty()) {
            Text(
                stringResource(R.string.transactions_empty_title),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            recent.forEach { item -> TransactionRow(item, onClick = { actions.onOpenTransaction(item) }) }
            TextButton(onClick = { actions.onOpenTransactions(TransactionFilter()) }) {
                Text(stringResource(R.string.dashboard_see_all))
            }
        }
    }
}

@Composable
private fun UpcomingSection(upcoming: UpcomingState, actions: DashboardActions) {
    Column {
        SectionTitle(stringResource(R.string.dashboard_upcoming))
        if (upcoming.attentionCount > 0) {
            TextButton(onClick = actions.onOpenRecurring) {
                Text(pluralStringResource(R.plurals.dashboard_schedules_attention, upcoming.attentionCount, upcoming.attentionCount))
            }
        }
        if (upcoming.items.isEmpty()) {
            Text(
                stringResource(R.string.dashboard_upcoming_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        upcoming.items.forEach { item -> UpcomingRow(item, actions.onOpenSchedule) }
    }
}

@Composable
private fun UpcomingRow(item: UpcomingItem, onOpen: (Long) -> Unit) {
    val schedule = item.schedule
    val money = schedule.money
    val amount = if (schedule.kind == TransactionKind.INCOME) "+${money.format()}" else "−${money.format()}"
    ListItem(
        modifier = Modifier.clickable { onOpen(schedule.id) },
        overlineContent = { Text(formatScheduleDate(item.date)) },
        headlineContent = { Text(schedule.name, maxLines = 1) },
        supportingContent = { Text(schedule.account.name, style = MaterialTheme.typography.bodySmall) },
        trailingContent = { Text(amount, style = MaterialTheme.typography.bodyLarge) },
    )
}
