package com.yehor.finora.ui.analytics

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yehor.finora.R
import com.yehor.finora.designsystem.component.button.PrimaryButton
import com.yehor.finora.designsystem.component.list.GroupedCard
import com.yehor.finora.designsystem.component.list.ListRow
import com.yehor.finora.designsystem.component.list.RoundIcon
import com.yehor.finora.designsystem.component.list.SectionHeader
import com.yehor.finora.designsystem.icon.AppIcons
import com.yehor.finora.designsystem.motion.pressScale
import com.yehor.finora.designsystem.theme.AmountStyle
import com.yehor.finora.designsystem.theme.finance
import com.yehor.finora.designsystem.theme.hairline
import com.yehor.finora.domain.analytics.UpcomingItem
import com.yehor.finora.domain.model.OperationType
import com.yehor.finora.domain.operation.TransactionFilter
import com.yehor.finora.domain.operation.TransactionItem
import com.yehor.finora.domain.operation.TransactionKind
import com.yehor.finora.ui.common.TransactionRow
import com.yehor.finora.ui.recurring.formatScheduleDate
import java.time.YearMonth
import kotlinx.coroutines.flow.Flow

private val ScreenMargin = 18.dp

/** Where Home can send the user. */
data class DashboardActions(
    val onAddTransaction: () -> Unit,
    val onAddIncome: () -> Unit,
    val onAddTransfer: () -> Unit,
    val onAddAccount: () -> Unit,
    val onOpenTransactions: (TransactionFilter) -> Unit,
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
        chartFor = viewModel::chart,
        onMode = viewModel::setHomeMode,
        onShow = viewModel::showHomePeriod,
        onRange = viewModel::setHomeRange,
        onToggleAccount = viewModel::toggleAccount,
        onClearAccountSelection = viewModel::clearAccountSelection,
    )
}

@Composable
fun DashboardContent(
    state: DashboardUiState,
    actions: DashboardActions,
    chartFor: (HomePeriod) -> Flow<HomeChart>,
    onMode: (HomeMode) -> Unit,
    onShow: (HomePeriod) -> Unit,
    onRange: (YearMonth, YearMonth) -> Unit,
    onToggleAccount: (Long) -> Unit,
    onClearAccountSelection: () -> Unit,
) {
    // No FAB on Home (quick actions instead); the shell already keeps content clear of the floating bar.
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        val period = state.homePeriod
        val today = state.today
        when {
            state.loading || period == null || today == null -> Unit
            !state.hasAccounts -> Column(
                Modifier.padding(padding).padding(horizontal = ScreenMargin),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                HomeTopBar()
                FirstAccountCard(actions.onAddAccount)
            }
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = ScreenMargin, end = ScreenMargin, top = 4.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "top") { HomeTopBar() }
                item(key = "period") {
                    HomePeriodControls(
                        period = period,
                        today = today,
                        accounts = state.accounts,
                        selectedAccountIds = state.selectedAccountIds,
                        onMode = onMode,
                        onRange = onRange,
                        onToggleAccount = onToggleAccount,
                        onClearAccounts = onClearAccountSelection,
                    )
                }
                item(key = "hero") {
                    HomeHero(period, today, state.chart) {
                        periodFilter(state)?.let { actions.onOpenTransactions(it) }
                    }
                }
                item(key = "chart") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        HomeChartPager(period, today, state.display, chartFor, onShow)
                        val chart = state.chart
                        val display = state.display
                        if (chart != null && display != null) {
                            ConversionNote(
                                chart.conversion,
                                resolveScope(state.selectedAccountIds, state.accounts, display),
                                Modifier.padding(horizontal = 4.dp),
                            )
                        }
                    }
                }
                item(key = "quick") { QuickActions(state.canTransfer, actions) }
                if (state.upcoming.hasActiveSchedules) {
                    item(key = "upcoming") { UpcomingSection(state.upcoming, actions) }
                }
                item(key = "recent") { RecentSection(state.recent, state.selectedAccountIds, actions) }
            }
        }
    }
}

/** The income and expense of Home's period for the selected accounts. */
private fun periodFilter(state: DashboardUiState): TransactionFilter? {
    val buckets = state.chart?.buckets?.takeIf { it.isNotEmpty() } ?: return null
    return TransactionFilter(
        types = setOf(OperationType.INCOME, OperationType.EXPENSE),
        from = buckets.first().from,
        toExclusive = buckets.last().toExclusive,
        accountIds = state.selectedAccountIds,
    )
}

// ---- Empty state ----

@Composable
private fun FirstAccountCard(onAdd: () -> Unit) {
    GroupedCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RoundIcon(AppIcons.Account, MaterialTheme.colorScheme.primary, size = 64.dp)
            Text(
                stringResource(R.string.dashboard_first_account_title),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                stringResource(R.string.accounts_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PrimaryButton(stringResource(R.string.dashboard_first_account_action), onAdd, Modifier.padding(top = 4.dp))
        }
    }
}

// ---- Quick actions ----

@Composable
private fun QuickActions(canTransfer: Boolean, actions: DashboardActions) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        QuickAction(stringResource(R.string.transaction_kind_expense), AppIcons.Expense, primary = true, onClick = actions.onAddTransaction)
        QuickAction(stringResource(R.string.transaction_kind_income), AppIcons.Income, onClick = actions.onAddIncome)
        QuickAction(stringResource(R.string.transaction_kind_transfer), AppIcons.Transfer, enabled = canTransfer, onClick = actions.onAddTransfer)
        QuickAction(stringResource(R.string.quick_recurring), AppIcons.Recurring, onClick = actions.onOpenRecurring)
    }
}

@Composable
private fun QuickAction(
    label: String,
    icon: ImageVector,
    primary: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    Column(
        Modifier
            .widthIn(min = 72.dp)
            .pressScale(interaction)
            .clip(MaterialTheme.shapes.medium)
            .clickable(interactionSource = interaction, indication = LocalIndication.current, enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else 0.38f)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .size(56.dp)
                .background(if (primary) scheme.primary else scheme.surfaceContainer, CircleShape)
                .then(if (primary) Modifier else Modifier.border(hairline(MaterialTheme.finance.isDark), CircleShape)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = if (primary) scheme.onPrimary else scheme.onSurface)
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ---- Upcoming ----

@Composable
private fun UpcomingSection(upcoming: UpcomingState, actions: DashboardActions) {
    Column {
        SectionHeader(stringResource(R.string.dashboard_upcoming), trailing = { SeeAll(actions.onOpenRecurring) })
        GroupedCard(Modifier.fillMaxWidth()) {
            if (upcoming.attentionCount > 0) {
                ListRow(
                    icon = { RoundIcon(AppIcons.Warning, MaterialTheme.colorScheme.error) },
                    title = pluralStringResource(R.plurals.dashboard_schedules_attention, upcoming.attentionCount, upcoming.attentionCount),
                    subtitle = null,
                    trailing = {},
                    onClick = actions.onOpenRecurring,
                )
            }
            if (upcoming.items.isEmpty()) {
                EmptyLine(stringResource(R.string.dashboard_upcoming_none))
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
    val finance = MaterialTheme.finance
    ListRow(
        icon = { RoundIcon(AppIcons.Recurring, if (income) finance.income else MaterialTheme.colorScheme.onSurfaceVariant) },
        title = schedule.name,
        subtitle = "${formatScheduleDate(item.date)} · ${schedule.account.name}",
        trailing = {
            Text(
                if (income) "+${money.format()}" else "−${money.format()}",
                style = AmountStyle,
                color = if (income) finance.income else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        },
        onClick = { onOpen(schedule.id) },
    )
}

// ---- Recent ----

@Composable
private fun RecentSection(recent: List<TransactionItem>, selectedAccountIds: Set<Long>, actions: DashboardActions) {
    Column {
        SectionHeader(
            stringResource(R.string.dashboard_recent),
            trailing = if (recent.isEmpty()) {
                null
            } else {
                { SeeAll { actions.onOpenTransactions(TransactionFilter(accountIds = selectedAccountIds)) } }
            },
        )
        GroupedCard(Modifier.fillMaxWidth()) {
            if (recent.isEmpty()) {
                ListRow(
                    icon = { RoundIcon(AppIcons.Receipt, MaterialTheme.colorScheme.onSurfaceVariant) },
                    title = stringResource(R.string.dashboard_no_recent),
                    subtitle = null,
                    trailing = {},
                )
            } else {
                recent.forEach { item ->
                    TransactionRow(
                        item,
                        onClick = { actions.onOpenTransaction(item) },
                    )
                }
            }
        }
    }
}

// ---- Building blocks ----

@Composable
private fun SeeAll(onClick: () -> Unit) {
    TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 8.dp)) {
        Text(stringResource(R.string.dashboard_see_all), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun EmptyLine(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
    )
}
