package com.yehor.finora.ui.analytics

import com.yehor.finora.ui.common.AppIcons
import com.yehor.finora.ui.common.IconSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yehor.finora.R
import com.yehor.finora.domain.account.AccountWithBalance
import com.yehor.finora.domain.account.Balance
import com.yehor.finora.domain.analytics.Cell
import com.yehor.finora.domain.analytics.PeriodKind
import com.yehor.finora.domain.analytics.ResolvedPeriod
import com.yehor.finora.domain.analytics.UpcomingItem
import com.yehor.finora.domain.model.CategoryType
import com.yehor.finora.domain.model.OperationType
import com.yehor.finora.domain.operation.TransactionFilter
import com.yehor.finora.domain.operation.TransactionItem
import com.yehor.finora.domain.operation.TransactionKind
import com.yehor.finora.ui.common.ColorPalette
import com.yehor.finora.ui.common.IconBadge
import com.yehor.finora.ui.common.TransactionRow
import com.yehor.finora.ui.recurring.formatScheduleDate
import com.yehor.finora.ui.theme.finance
import java.time.LocalDate
import com.yehor.finora.ui.theme.hairline

private val SectionShape = RoundedCornerShape(24.dp)
private val TileShape = RoundedCornerShape(16.dp)

/** Where the Dashboard can send the user. */
data class DashboardActions(
    val onAddTransaction: () -> Unit,
    val onAddIncome: () -> Unit,
    val onAddTransfer: () -> Unit,
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
    DashboardContent(
        state = state,
        actions = actions,
        onSelectPeriod = viewModel::selectPeriod,
        onCustomPeriod = viewModel::setCustomPeriod,
        onShiftPeriod = viewModel::shiftPeriod,
        onToggleAccount = viewModel::toggleAccount,
        onClearAccountSelection = viewModel::clearAccountSelection,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardContent(
    state: DashboardUiState,
    actions: DashboardActions,
    onSelectPeriod: (PeriodKind) -> Unit,
    onCustomPeriod: (LocalDate, LocalDate) -> Unit,
    onShiftPeriod: (Int) -> Unit,
    onToggleAccount: (Long) -> Unit,
    onClearAccountSelection: () -> Unit,
) {
    Scaffold(
        floatingActionButton = {
            if (state.hasAccounts) {
                FloatingActionButton(onClick = actions.onAddTransaction) {
                    Icon(AppIcons.Add, contentDescription = stringResource(R.string.transactions_add))
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
                // Bottom padding keeps the last card clear of the FAB.
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item(key = "title") {
                    Text(
                        stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp).semantics { heading() },
                    )
                }
                // One compact row that scopes the cash flow and the sections below the quick actions.
                item(key = "period") {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PeriodSelector(
                            state = state.periodState,
                            period = period,
                            onSelect = onSelectPeriod,
                            onCustom = onCustomPeriod,
                            modifier = Modifier.weight(1f),
                            arrows = true,
                            canShiftForward = state.canShiftForward,
                            onShift = onShiftPeriod,
                        )
                        if (state.activeAccounts.size > 1) {
                            AccountSelector(
                                accounts = state.activeAccounts.map { it.account },
                                selectedAccountIds = state.selectedAccountIds,
                                onToggleAccount = onToggleAccount,
                                onClearSelection = onClearAccountSelection,
                            )
                        }
                    }
                }
                item(key = "flows") { CashFlowCard(state, period, actions) }
                item(key = "quick") { QuickActions(state.canTransfer, actions) }
                item(key = "accounts") { AccountsCarousel(state.activeAccounts, actions) }
                item(key = "distribution") { DistributionSection(state, actions) }
                if (state.upcoming.hasActiveSchedules) {
                    item(key = "upcoming") { UpcomingSection(state.upcoming, actions) }
                }
                item(key = "recent") { RecentSection(state.recent, state.selectedAccountIds, actions) }
            }
        }
    }
}

// ---- Empty state ----

@Composable
private fun FirstAccountCard(onAdd: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier.fillMaxWidth(),
        shape = SectionShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(64.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    AppIcons.Account,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Text(
                stringResource(R.string.dashboard_first_account_title),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                stringResource(R.string.accounts_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
            )
            Button(onClick = onAdd) {
                Icon(AppIcons.Add, contentDescription = null, modifier = Modifier.size(IconSize.Small))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.dashboard_first_account_action))
            }
        }
    }
}

// ---- Quick actions ----

@Composable
private fun QuickActions(canTransfer: Boolean, actions: DashboardActions) {
    val scheme = MaterialTheme.colorScheme
    val finance = MaterialTheme.finance
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        QuickAction(
            stringResource(R.string.transaction_kind_expense), AppIcons.Expense,
            tint = finance.expense, container = finance.expenseContainer, Modifier.weight(1f),
            onClick = actions.onAddTransaction,
        )
        QuickAction(
            stringResource(R.string.transaction_kind_income), AppIcons.Income,
            tint = finance.income, container = finance.incomeContainer, Modifier.weight(1f),
            onClick = actions.onAddIncome,
        )
        QuickAction(
            stringResource(R.string.transaction_kind_transfer), AppIcons.Transfer,
            tint = scheme.onPrimaryContainer, container = scheme.primaryContainer, Modifier.weight(1f),
            enabled = canTransfer, onClick = actions.onAddTransfer,
        )
        QuickAction(
            stringResource(R.string.nav_accounts), AppIcons.Account,
            tint = scheme.onTertiaryContainer, container = scheme.tertiaryContainer, Modifier.weight(1f),
            onClick = actions.onOpenAccounts,
        )
    }
}

@Composable
private fun QuickAction(
    label: String,
    icon: ImageVector,
    tint: Color,
    container: Color,
    modifier: Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = TileShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            Modifier.padding(vertical = 12.dp, horizontal = 4.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val alpha = if (enabled) 1f else 0.38f
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(container.copy(alpha = alpha)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = tint.copy(alpha = alpha))
            }
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ---- Accounts (PRD 11.4) ----

@Composable
private fun AccountsCarousel(accounts: List<AccountWithBalance>, actions: DashboardActions) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(
            stringResource(R.string.nav_accounts),
            actionLabel = stringResource(R.string.dashboard_manage),
            onAction = actions.onOpenAccounts,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(accounts, key = { it.account.id }) { item -> AccountTile(item, actions.onOpenAccountHistory) }
            item(key = "add") { AddAccountTile(actions.onAddAccount) }
        }
    }
}

@Composable
private fun AccountTile(item: AccountWithBalance, onOpen: (Long) -> Unit) {
    val account = item.account
    Surface(
        onClick = { onOpen(account.id) },
        shape = TileShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.width(156.dp).heightIn(min = 112.dp),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(account.icon, account.color, ColorPalette.ACCOUNT_DEFAULT, size = 32)
                Spacer(Modifier.weight(1f))
                Text(
                    account.currency.code,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                account.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Native currency only: account balances are never converted (PRD 10.6).
            when (val balance = item.balance) {
                Balance.Overflow -> Text(
                    stringResource(R.string.accounts_balance_overflow),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                is Balance.Value -> Text(
                    balance.money.format(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (balance.money.amountMinor < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun AddAccountTile(onAdd: () -> Unit) {
    OutlinedCard(
        onClick = onAdd,
        shape = TileShape,
        modifier = Modifier.width(112.dp).height(112.dp),
    ) {
        Column(
            Modifier.fillMaxSize().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        ) {
            Icon(AppIcons.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                stringResource(R.string.dashboard_add_account),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ---- Cash flow (PRD 11.5) ----

@Composable
private fun CashFlowCard(state: DashboardUiState, period: ResolvedPeriod, actions: DashboardActions) {
    val display = state.display ?: return
    val scope = resolveScope(state.selectedAccountIds, state.activeAccounts.map { it.account }, display)
    val base = TransactionFilter(
        from = period.from,
        toExclusive = period.toExclusive,
        accountIds = state.selectedAccountIds,
    )
    DashboardCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.dashboard_cash_flow),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowTile(
                    stringResource(R.string.transaction_kind_income), state.income, AppIcons.Income,
                    MaterialTheme.finance.income, MaterialTheme.finance.incomeContainer, Modifier.weight(1f),
                    onClick = { actions.onOpenTransactions(base.copy(types = setOf(OperationType.INCOME))) },
                )
                FlowTile(
                    stringResource(R.string.transaction_kind_expense), state.expense, AppIcons.Expense,
                    MaterialTheme.finance.expense, MaterialTheme.finance.expenseContainer, Modifier.weight(1f),
                    onClick = { actions.onOpenTransactions(base.copy(types = setOf(OperationType.EXPENSE))) },
                )
            }
            NetRow(
                state.net, state.income, state.expense,
                onClick = { actions.onOpenTransactions(base.copy(types = setOf(OperationType.INCOME, OperationType.EXPENSE))) },
            )
            ConversionNote(state.conversion, scope)
        }
    }
}

@Composable
private fun FlowTile(
    title: String,
    cell: Cell?,
    icon: ImageVector,
    tint: Color,
    container: Color,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = TileShape,
        color = container,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(24.dp).clip(CircleShape).background(tint.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
                }
                Text(
                    title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            AmountText(cell, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** Net = income − expense, transfers excluded (PRD 11.5). The bar shows how much of the income was spent. */
@Composable
private fun NetRow(net: Cell?, income: Cell?, expense: Cell?, onClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(onClick = onClick, shape = TileShape, color = Color.Transparent) {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.dashboard_net_cash_flow),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                AmountText(net, style = MaterialTheme.typography.titleMedium, color = netColor(net), signed = true)
                Icon(
                    AppIcons.Next,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        FlowBar(income, expense)
    }
}

@Composable
private fun FlowBar(income: Cell?, expense: Cell?) {
    val incomeMinor = income?.estimate?.takeIf { !income.overflowed }?.amountMinor ?: return
    val expenseMinor = expense?.estimate?.takeIf { !expense.overflowed }?.amountMinor ?: return
    val total = incomeMinor.toDouble() + expenseMinor.toDouble()
    if (incomeMinor < 0 || expenseMinor < 0 || total <= 0.0) return
    val incomeWeight = (incomeMinor / total).toFloat()
    val incomeColor = MaterialTheme.finance.income
    val expenseColor = MaterialTheme.finance.expense
    Canvas(Modifier.fillMaxWidth().height(10.dp)) {
        val r = size.height / 2f
        val gap = 2.dp.toPx()
        if (incomeWeight <= 0f || incomeWeight >= 1f) {
            drawRoundRect(
                color = if (incomeWeight >= 1f) incomeColor else expenseColor,
                size = size,
                cornerRadius = CornerRadius(r, r),
            )
        } else {
            val incomeWidth = (size.width - gap) * incomeWeight
            val expenseLeft = incomeWidth + gap
            val expenseWidth = size.width - expenseLeft
            val incomeRadius = CornerRadius(minOf(r, incomeWidth))
            val expenseRadius = CornerRadius(minOf(r, expenseWidth))
            val incomePath = Path().apply {
                addRoundRect(
                    RoundRect(
                        left = 0f,
                        top = 0f,
                        right = incomeWidth,
                        bottom = size.height,
                        topLeftCornerRadius = incomeRadius,
                        bottomLeftCornerRadius = incomeRadius,
                        topRightCornerRadius = CornerRadius.Zero,
                        bottomRightCornerRadius = CornerRadius.Zero,
                    ),
                )
            }
            val expensePath = Path().apply {
                addRoundRect(
                    RoundRect(
                        left = expenseLeft,
                        top = 0f,
                        right = size.width,
                        bottom = size.height,
                        topLeftCornerRadius = CornerRadius.Zero,
                        bottomLeftCornerRadius = CornerRadius.Zero,
                        topRightCornerRadius = expenseRadius,
                        bottomRightCornerRadius = expenseRadius,
                    ),
                )
            }
            drawPath(incomePath, incomeColor)
            drawPath(expensePath, expenseColor)
        }
    }
}

// ---- Distribution ----

@Composable
private fun DistributionSection(state: DashboardUiState, actions: DashboardActions) {
    val report = state.distribution ?: return
    val entries = donutEntries(report, CategoryType.EXPENSE)
    DashboardCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader(
                stringResource(R.string.dashboard_distribution),
                actionLabel = if (entries.isEmpty()) null else stringResource(R.string.dashboard_details),
                onAction = actions.onOpenStatistics,
            )
            if (entries.isEmpty()) {
                EmptyLine(stringResource(R.string.dashboard_no_expenses))
            } else {
                CategoryBreakdown(
                    title = stringResource(R.string.dashboard_spent),
                    entries = entries,
                    total = report.total,
                )
            }
        }
    }
}

// ---- Upcoming ----

@Composable
private fun UpcomingSection(upcoming: UpcomingState, actions: DashboardActions) {
    DashboardCard {
        Column(Modifier.padding(vertical = 16.dp)) {
            SectionHeader(
                stringResource(R.string.dashboard_upcoming),
                actionLabel = stringResource(R.string.dashboard_see_all),
                onAction = actions.onOpenRecurring,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            if (upcoming.attentionCount > 0) {
                Surface(
                    onClick = actions.onOpenRecurring,
                    shape = TileShape,
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(AppIcons.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                        Text(
                            pluralStringResource(R.plurals.dashboard_schedules_attention, upcoming.attentionCount, upcoming.attentionCount),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
            if (upcoming.items.isEmpty()) {
                EmptyLine(stringResource(R.string.dashboard_upcoming_none), Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
            upcoming.items.forEach { item -> UpcomingRow(item, actions.onOpenSchedule) }
        }
    }
}

@Composable
private fun UpcomingRow(item: UpcomingItem, onOpen: (Long) -> Unit) {
    val schedule = item.schedule
    val money = schedule.money
    val income = schedule.kind == TransactionKind.INCOME
    val amount = if (income) "+${money.format()}" else "−${money.format()}"
    ListItem(
        modifier = Modifier.clickable { onOpen(schedule.id) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        overlineContent = { Text(formatScheduleDate(item.date)) },
        headlineContent = { Text(schedule.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(schedule.account.name, style = MaterialTheme.typography.bodySmall) },
        trailingContent = {
            Text(
                amount,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (income) MaterialTheme.finance.income else MaterialTheme.colorScheme.onSurface,
            )
        },
    )
}

// ---- Recent ----

@Composable
private fun RecentSection(recent: List<TransactionItem>, selectedAccountIds: Set<Long>, actions: DashboardActions) {
    DashboardCard {
        Column(Modifier.padding(vertical = 16.dp)) {
            SectionHeader(
                stringResource(R.string.dashboard_recent),
                actionLabel = if (recent.isEmpty()) null else stringResource(R.string.dashboard_see_all),
                onAction = { actions.onOpenTransactions(TransactionFilter(accountIds = selectedAccountIds)) },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            if (recent.isEmpty()) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        AppIcons.Receipt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    EmptyLine(stringResource(R.string.dashboard_no_recent), Modifier.padding(start = 12.dp))
                }
            } else {
                recent.forEach { item ->
                    TransactionRow(
                        item,
                        onClick = { actions.onOpenTransaction(item) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
        }
    }
}

// ---- Building blocks ----

@Composable
private fun DashboardCard(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = SectionShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = hairline(MaterialTheme.finance.isDark),
        content = content,
    )
}

@Composable
private fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Row(modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (actionLabel != null) {
            TextButton(onClick = onAction, contentPadding = PaddingValues(start = 12.dp, end = 4.dp)) {
                Text(actionLabel)
                Icon(AppIcons.Next, contentDescription = null, modifier = Modifier.size(IconSize.Small))
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, heightDp = 1400)
@Composable
private fun DashboardPreview() = DashboardPreviewContent(dark = false)

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, heightDp = 1400, backgroundColor = 0xFF0F1412)
@Composable
private fun DashboardDarkPreview() = DashboardPreviewContent(dark = true)

@Composable
private fun DashboardPreviewContent(dark: Boolean) {
    val usd = com.yehor.finora.domain.money.CurrencyRegistry.get("USD")
    val pln = com.yehor.finora.domain.money.CurrencyRegistry.get("PLN")
    fun acc(id: Long, name: String, minor: Long, currency: com.yehor.finora.domain.money.Currency) = AccountWithBalance(
        com.yehor.finora.domain.account.Account(
            id, name, currency, com.yehor.finora.domain.account.AccountType.BANK, "wallet", null, null,
            com.yehor.finora.domain.model.AccountStatus.ACTIVE, 0,
        ),
        Balance.Value(com.yehor.finora.domain.money.Money(minor, currency)),
    )
    fun cell(minor: Long) = Cell(
        com.yehor.finora.domain.money.Estimate(minor, pln, com.yehor.finora.domain.money.EstimateBasis.NATIVE),
        3, emptySet(), false, null,
    )
    val today = LocalDate.of(2026, 11, 9)
    val periodState = com.yehor.finora.domain.analytics.PeriodState()
    val accounts = listOf(acc(1, "Santander", 500_000, pln), acc(2, "IBKR", 200_000, usd), acc(3, "Cash", -3_000, pln))
    com.yehor.finora.ui.theme.PreviewTheme(dark) {
        DashboardContent(
            state = DashboardUiState(
                loading = false,
                hasAccounts = true,
                periodState = periodState,
                period = periodState.resolve(today, java.time.ZoneId.of("UTC"), java.time.DayOfWeek.MONDAY),
                today = today,
                display = pln,
                income = cell(842_000),
                expense = cell(319_045),
                net = cell(522_955),
                activeAccounts = accounts,
            ),
            actions = DashboardActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}),
            onSelectPeriod = {}, onCustomPeriod = { _, _ -> }, onShiftPeriod = {}, onToggleAccount = {}, onClearAccountSelection = {},
        )
    }
}

@Composable
private fun EmptyLine(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}
