package com.yehor.finora.ui.analytics

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yehor.finora.R
import com.yehor.finora.domain.money.Money
import com.yehor.finora.domain.operation.TransactionFilter
import com.yehor.finora.domain.operation.TransactionItem
import com.yehor.finora.ui.operations.FilterSection
import com.yehor.finora.ui.operations.TransactionFilterSheet

/** Where Statistics can send the user. */
data class StatisticsActions(
    val onOpenTransactions: (TransactionFilter) -> Unit,
    val onOpenAccountAnalytics: (Long) -> Unit,
    val onOpenTransaction: (TransactionItem) -> Unit,
)

@Composable
fun StatisticsScreen(actions: StatisticsActions, viewModel: StatisticsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val trend by viewModel.trend.collectAsStateWithLifecycle()
    StatisticsBody(state, trend, viewModel, actions)
}

private val TagSheetSections = setOf(FilterSection.TYPE, FilterSection.ACCOUNT, FilterSection.CATEGORY, FilterSection.DATE)

private val ScreenMargin = 18.dp

@Composable
private fun StatisticsBody(state: StatisticsUiState, trend: StatsTrend?, vm: StatisticsViewModel, actions: StatisticsActions) {
    val env = state.env
    val scope = state.scope
    val controls = state.controls
    var filterSheet by rememberSaveable { mutableStateOf(false) }
    var optionsSheet by rememberSaveable { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        if ((state.loading || env == null) || scope == null) return@Box
        val hasOptions = controls.tab != StatsTab.ACCOUNTS
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = ScreenMargin, end = ScreenMargin, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "top") { StatsTopBar(onOptions = if (hasOptions) ({ optionsSheet = true }) else null) }
            item(key = "tabs") { StatsTabs(controls.tab, vm::selectTab) }
            item(key = "period") {
                StatsPeriodBlock(
                    state = env.periodState,
                    period = env.period,
                    today = env.today,
                    canShiftForward = env.canShiftForward,
                    onSelect = vm::selectPeriod,
                    onShift = vm::shiftPeriod,
                    onMonths = vm::selectMonths,
                    onCustom = vm::setCustomPeriod,
                )
            }
            if (controls.tab != StatsTab.ACCOUNTS) {
                item(key = "note") { ConversionNote(state.conversion, scope) }
            }
            when (val content = state.content) {
                is StatsContent.Flows -> flowItems(content.tab, trend, state, vm, actions)
                is StatsContent.Tags -> tagDetailItems(content.tab, state, vm, actions)
                is StatsContent.TagRanking -> tagRankingItems(content, state, vm)
                is StatsContent.Accounts -> accountItems(content.rows, actions)
                null -> Unit
            }
        }
    }

    // System back climbs the category trail before it leaves the screen.
    val drilling = state.content is StatsContent.Flows || state.content is StatsContent.Tags
    BackHandler(enabled = drilling && controls.path.isNotEmpty()) { vm.back() }

    if (optionsSheet && env != null && controls.tab != StatsTab.ACCOUNTS) {
        StatsOptionsSheet(
            accounts = env.allAccounts,
            selectedAccountIds = controls.selectedAccountIds,
            onToggleAccount = vm::toggleAccount,
            onClearAccounts = vm::clearAccounts,
            compare = controls.compare,
            previousLabel = state.previousPeriod?.let { rangeLabel(it) },
            onCompare = vm::setCompare,
            onDismiss = { optionsSheet = false },
            onFilter = if (controls.tab == StatsTab.TAGS) {
                {
                    optionsSheet = false
                    filterSheet = true
                }
            } else {
                null
            },
        )
    }

    if (filterSheet && env != null) {
        TransactionFilterSheet(
            filter = controls.tagFilter.copy(from = env.period.from, toExclusive = env.period.toExclusive),
            accounts = env.allAccounts,
            categories = env.categories,
            tags = env.tags,
            onApply = { filter ->
                filterSheet = false
                vm.applyTagSheet(filter, env.period)
            },
            onDismiss = { filterSheet = false },
            sections = TagSheetSections,
        )
    }
}

// ---- Expenses and Income ----

private fun LazyListScope.flowItems(
    tab: FlowTab,
    trend: StatsTrend?,
    state: StatisticsUiState,
    vm: StatisticsViewModel,
    actions: StatisticsActions,
) {
    val report = tab.report
    val env = state.env ?: return
    val compare = state.controls.compare
    item(key = "total") {
        StatsHero(
            measure = report.measure,
            state = env.periodState,
            period = env.period,
            previous = state.previousPeriod,
            today = env.today,
            total = report.category.total,
            delta = if (compare) report.category.totalDelta else null,
        )
    }
    item(key = "trend") {
        StatsTrendCard(
            trend = trend,
            committed = displayWindow(env.period, env.today),
            today = env.today,
            currentName = periodShortName(env.period, env.today),
            previousName = state.previousPeriod?.let { periodShortName(it, env.today) },
            onPreview = vm::previewWindow,
            onCommit = vm::commitWindow,
            onOpenBucket = { t, bucket -> actions.onOpenTransactions(AnalyticsFilters.bucket(t.base, t.measure, bucket)) },
        )
    }
    item(key = "categories") {
        CategoryCard(
            report = report.category,
            measure = report.measure,
            base = tab.base,
            compare = compare,
            categories = env.categories,
            onEnter = vm::enter,
            onBack = { vm.back(it) },
            onOpen = actions.onOpenTransactions,
        )
    }
    val type = setOf(report.measure.operationType())
    if (report.byAccount.isNotEmpty()) {
        item(key = "by_account") {
            BreakdownCard(
                title = stringResource(R.string.stats_by_account),
                items = accountBreakdown(report.byAccount, env.allAccounts) { id ->
                    actions.onOpenTransactions(tab.base.copy(types = type, accountIds = setOf(id)))
                },
            )
        }
    }
    if (report.byTag.isNotEmpty()) {
        item(key = "by_tag") {
            BreakdownCard(
                title = stringResource(R.string.stats_by_tag),
                note = stringResource(R.string.stats_by_tag_note),
                items = tagBreakdown(report.byTag, env.tags) { id ->
                    actions.onOpenTransactions(tab.base.copy(types = type, tagIds = setOf(id)))
                },
            )
        }
    }
}

fun signedMoney(money: Money): String = if (money.amountMinor > 0) "+${money.format()}" else money.format()
