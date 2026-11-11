package com.yehor.finora.ui.analytics

import com.yehor.finora.designsystem.icon.AppIcons
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yehor.finora.R
import com.yehor.finora.domain.analytics.Bucket
import com.yehor.finora.domain.analytics.BucketUnit
import com.yehor.finora.domain.analytics.Cell
import com.yehor.finora.domain.analytics.DrillRow
import com.yehor.finora.domain.analytics.Measure
import com.yehor.finora.domain.analytics.TrendPoint
import com.yehor.finora.domain.analytics.delta
import com.yehor.finora.domain.model.AccountStatus
import com.yehor.finora.domain.model.OperationType
import com.yehor.finora.domain.money.Money
import com.yehor.finora.domain.operation.TransactionFilter
import com.yehor.finora.domain.operation.TransactionItem
import com.yehor.finora.ui.common.ColorPalette
import com.yehor.finora.ui.common.IconBadge
import com.yehor.finora.ui.common.TagFilterPicker
import com.yehor.finora.ui.common.TransactionRow
import com.yehor.finora.designsystem.chart.Bar
import com.yehor.finora.designsystem.chart.TrendBars
import com.yehor.finora.ui.operations.FilterSection
import com.yehor.finora.ui.operations.TransactionFilterSheet
import java.time.format.DateTimeFormatter

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
                is StatsContent.Tags -> tagItems(content.tab, state, vm, actions) { filterSheet = true }
                is StatsContent.Accounts -> accountItems(content.rows, actions)
                null -> Unit
            }
        }
    }

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

private fun Measure.labelRes(): Int = when (this) {
    Measure.EXPENSE -> R.string.stats_measure_expense
    Measure.INCOME -> R.string.stats_measure_income
    Measure.TRANSFER -> R.string.stats_measure_transfer
}

// ---- Expenses and Income ----

private fun androidx.compose.foundation.lazy.LazyListScope.flowItems(
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
            committed = ChartWindow.of(env.period),
            today = env.today,
            currentName = periodShortName(env.period, env.today),
            previousName = state.previousPeriod?.let { periodShortName(it, env.today) },
            onPreview = vm::previewWindow,
            onCommit = vm::commitWindow,
            onOpenBucket = { t, bucket -> actions.onOpenTransactions(AnalyticsFilters.bucket(t.base, t.measure, bucket)) },
        )
    }
    item(key = "categories") {
        CategorySection(
            report = report.category,
            measure = report.measure,
            base = tab.base,
            compare = compare,
            onEnter = vm::enter,
            onBack = { vm.back(it) },
            onOpen = actions.onOpenTransactions,
        )
    }
    if (report.byAccount.isNotEmpty()) {
        item(key = "by_account") {
            Column {
                SectionTitle(stringResource(R.string.stats_by_account))
                report.byAccount.forEach { line ->
                    val account = env.allAccounts.firstOrNull { it.id == line.id }
                    AnalyticsRow(
                        label = account?.name.orEmpty(),
                        cell = line.cell,
                        icon = account?.icon,
                        color = account?.color,
                        share = line.share,
                        chevron = line.id != null,
                        onClick = line.id?.let { id ->
                            {
                                actions.onOpenTransactions(
                                    tab.base.copy(types = setOf(report.measure.operationType()), accountIds = setOf(id)),
                                )
                            }
                        },
                    )
                }
            }
        }
    }
    if (report.byTag.isNotEmpty()) {
        item(key = "by_tag") {
            Column {
                SectionTitle(stringResource(R.string.stats_by_tag))
                Text(
                    stringResource(R.string.stats_by_tag_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                report.byTag.forEach { line ->
                    val tag = env.tags.firstOrNull { it.id == line.id }
                    AnalyticsRow(
                        label = tag?.name ?: stringResource(R.string.stats_no_tag),
                        cell = line.cell,
                        color = tag?.color,
                        share = line.share,
                        chevron = tag != null,
                        onClick = tag?.let {
                            {
                                actions.onOpenTransactions(
                                    tab.base.copy(types = setOf(report.measure.operationType()), tagIds = setOf(it.id)),
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}

/** Breadcrumb, donut with legend, and the list of the current level. */
@Composable
private fun CategorySection(
    report: CategoryReport,
    measure: Measure,
    base: TransactionFilter,
    compare: Boolean,
    onEnter: (Long) -> Unit,
    onBack: (Int) -> Unit,
    onOpen: (TransactionFilter) -> Unit,
) {
    Column {
        if (report.breadcrumb.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onBack(report.breadcrumb.size - 1) }) {
                    Icon(AppIcons.Back, contentDescription = stringResource(R.string.stats_back))
                }
                FlowRow {
                    TextButton(onClick = { onBack(0) }) { Text(stringResource(R.string.stats_all_categories)) }
                    report.breadcrumb.forEachIndexed { index, category ->
                        val last = index == report.breadcrumb.lastIndex
                        TextButton(onClick = { onBack(index + 1) }, enabled = !last) { Text("› ${category.name}") }
                    }
                }
            }
        }
        if (report.lines.isEmpty()) {
            Text(
                stringResource(R.string.stats_no_data),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        val entries = donutEntries(report, measure.categoryType)
        if (entries.isNotEmpty()) {
            CategoryBreakdown(
                title = stringResource(measure.labelRes()),
                entries = entries,
                total = report.levelTotal,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        report.lines.forEach { line ->
            val row = line.row
            val previousOnly = compare && line.previous != null && row.cell.isEmpty
            val open: (() -> Unit)? = when {
                previousOnly -> null
                row.categoryId != null && row.hasChildren && !row.isDirect -> ({ onEnter(row.categoryId) })
                else -> ({ onOpen(AnalyticsFilters.drill(base, measure, row)) })
            }
            AnalyticsRow(
                label = rowLabel(row),
                cell = row.cell,
                icon = row.category?.icon,
                color = row.category?.color,
                fallbackColor = ColorPalette.defaultFor(measure.categoryType),
                share = line.share,
                delta = line.delta,
                previousOnly = previousOnly,
                chevron = open != null,
                onClick = open,
            )
        }
        val current = report.breadcrumb.lastOrNull()
        TextButton(
            onClick = {
                val levelRow = DrillRow(current?.id, current, report.total, isDirect = false, hasChildren = true)
                onOpen(
                    if (current == null) {
                        base.copy(types = setOf(measure.operationType()))
                    } else {
                        AnalyticsFilters.drill(base, measure, levelRow)
                    },
                )
            },
        ) { Text(stringResource(R.string.stats_show_transactions)) }
    }
}

@Composable
private fun TrendSection(points: List<TrendPoint>, unit: BucketUnit, onOpen: (Bucket) -> Unit) {
    val labels = points.map { bucketLabel(it.bucket, unit) }
    val texts = points.map { cellText(it.cell) }
    val description = points.indices.joinToString(". ") { "${labels[it]} ${texts[it]}" }
    TrendBars(
        bars = points.mapIndexed { i, p -> Bar(labels[i], p.cell.estimate?.amountMinor ?: 0L) },
        color = MaterialTheme.colorScheme.primary,
        description = description,
        onBarClick = { index -> points.getOrNull(index)?.let { onOpen(it.bucket) } },
        showLabels = points.size <= 12,
        modifier = Modifier.padding(vertical = 8.dp),
    )
    // The numbers behind the bars, as text.
    points.forEachIndexed { i, point ->
        if (point.cell.hasAmount || point.cell.partial || point.cell.overflowed) {
            Row(
                Modifier.fillMaxWidth().clickable { onOpen(point.bucket) }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(labels[i], style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                AmountText(point.cell, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private fun bucketLabel(bucket: Bucket, unit: BucketUnit): String = when (unit) {
    BucketUnit.DAY -> bucket.fromDate.dayOfMonth.toString()
    BucketUnit.MONTH -> bucket.fromDate.format(DateTimeFormatter.ofPattern("MMM yy"))
    BucketUnit.YEAR -> bucket.fromDate.year.toString()
}

// ---- Tags ----

private fun androidx.compose.foundation.lazy.LazyListScope.tagItems(
    tab: TagsTab,
    state: StatisticsUiState,
    vm: StatisticsViewModel,
    actions: StatisticsActions,
    onFilter: () -> Unit,
) {
    val env = state.env ?: return
    val controls = state.controls
    item(key = "tag_picker") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TagFilterPicker(
                allTags = env.tags,
                selectedTagIds = controls.tagFilter.tagIds,
                tagMode = controls.tagFilter.tagMode,
                onToggleTag = vm::toggleTag,
                onModeChange = vm::setTagMode,
            )
            OutlinedButton(onClick = onFilter) { Text(stringResource(R.string.stats_filter)) }
        }
    }
    val report = tab.report
    if (report == null) {
        item(key = "tag_hint") {
            Text(
                stringResource(R.string.stats_tags_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    val figures = report.figures
    val previous = report.previous
    item(key = "tag_figures") {
        Column {
            FigureRow(R.string.stats_total_income, figures.income, previous?.income, OperationType.INCOME, tab, actions, signed = false)
            FigureRow(R.string.stats_total_expense, figures.expense, previous?.expense, OperationType.EXPENSE, tab, actions, signed = false)
            FigureRow(R.string.stats_total_net, figures.net, previous?.net, null, tab, actions, signed = true)
            FigureRow(R.string.stats_total_transferred, figures.transferred, previous?.transferred, OperationType.TRANSFER, tab, actions, signed = false)
        }
    }
    if (controls.tagFilter.types.size != 1) {
        item(key = "tag_measure") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Measure.entries.forEach { measure ->
                    FilterChip(
                        selected = controls.tagMeasure == measure,
                        onClick = { vm.setTagMeasure(measure) },
                        label = { Text(stringResource(measure.labelRes())) },
                    )
                }
            }
        }
    }
    item(key = "tag_categories") {
        CategorySection(
            report = report.category,
            measure = report.measure,
            base = tab.filter,
            compare = controls.compare,
            onEnter = vm::enter,
            onBack = { vm.back(it) },
            onOpen = actions.onOpenTransactions,
        )
    }
    if (report.byAccount.isNotEmpty()) {
        item(key = "tag_accounts") {
            Column {
                SectionTitle(stringResource(R.string.stats_by_account))
                report.byAccount.forEach { line ->
                    val account = env.allAccounts.firstOrNull { it.id == line.id }
                    AnalyticsRow(
                        label = account?.name.orEmpty(),
                        cell = line.cell,
                        icon = account?.icon,
                        color = account?.color,
                        share = line.share,
                        chevron = line.id != null,
                        onClick = line.id?.let { id ->
                            {
                                actions.onOpenTransactions(
                                    tab.filter.copy(types = setOf(report.measure.operationType()), accountIds = setOf(id)),
                                )
                            }
                        },
                    )
                }
            }
        }
    }
    if (report.byDate.isNotEmpty()) {
        item(key = "tag_dates") {
            Column {
                SectionTitle(stringResource(R.string.stats_by_date))
                TrendSection(
                    points = report.byDate,
                    unit = report.dateUnit,
                    onOpen = { bucket ->
                        actions.onOpenTransactions(
                            tab.filter.copy(
                                types = setOf(report.measure.operationType()),
                                from = bucket.from,
                                toExclusive = bucket.toExclusive,
                            ),
                        )
                    },
                )
            }
        }
    }
    if (tab.recent.isNotEmpty()) {
        item(key = "tag_recent") {
            Column {
                SectionTitle(stringResource(R.string.stats_recent))
                tab.recent.forEach { item -> TransactionRow(item, onClick = { actions.onOpenTransaction(item) }) }
                TextButton(onClick = { actions.onOpenTransactions(tab.filter) }) {
                    Text(stringResource(R.string.stats_show_all, figures.operationCount))
                }
            }
        }
    }
}

@Composable
private fun FigureRow(
    labelRes: Int,
    cell: Cell?,
    previous: Cell?,
    type: OperationType?,
    tab: TagsTab,
    actions: StatisticsActions,
    signed: Boolean,
) {
    val deltaLine = if (cell != null && previous != null) deltaText(delta(cell.estimate, previous.estimate)) else ""
    ListItem(
        modifier = Modifier.clickable(enabled = cell != null) {
            val types = type?.let { setOf(it) } ?: setOf(OperationType.INCOME, OperationType.EXPENSE)
            actions.onOpenTransactions(tab.filter.copy(types = types))
        },
        headlineContent = { Text(stringResource(labelRes)) },
        supportingContent = if (deltaLine.isNotEmpty()) {
            { Text(deltaLine, style = MaterialTheme.typography.bodySmall) }
        } else {
            null
        },
        trailingContent = {
            AmountText(cell, style = MaterialTheme.typography.bodyLarge, color = if (signed) netColor(cell) else androidx.compose.ui.graphics.Color.Unspecified, signed = signed)
        },
    )
}

// ---- Accounts ----

private fun androidx.compose.foundation.lazy.LazyListScope.accountItems(
    rows: List<AccountPeriodView>,
    actions: StatisticsActions,
) {
    if (rows.isEmpty()) {
        item(key = "accounts_empty") {
            Text(
                stringResource(R.string.stats_accounts_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    items(rows.size, key = { rows[it].account.id }) { index ->
        val view = rows[index]
        val account = view.account
        val archived = account.status == AccountStatus.ARCHIVED
        ListItem(
            modifier = Modifier
                .alpha(if (archived) 0.6f else 1f)
                .clickable { actions.onOpenAccountAnalytics(account.id) },
            leadingContent = { IconBadge(account.icon, account.color, ColorPalette.ACCOUNT_DEFAULT, size = 36) },
            headlineContent = { Text(account.name, maxLines = 1) },
            supportingContent = {
                Text(
                    stringResource(R.string.stats_account_income) + " " + view.row.income.format() + " · " +
                        stringResource(R.string.stats_account_expense) + " " + view.row.expense.format(),
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            trailingContent = { Text(signedMoney(view.row.netChange), style = MaterialTheme.typography.bodyLarge) },
        )
    }
}

fun signedMoney(money: Money): String = if (money.amountMinor > 0) "+${money.format()}" else money.format()
