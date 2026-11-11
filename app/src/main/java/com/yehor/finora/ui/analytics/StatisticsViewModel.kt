package com.yehor.finora.ui.analytics

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yehor.finora.data.repo.AnalyticsRepository
import com.yehor.finora.data.repo.TransactionRepository
import com.yehor.finora.domain.account.Account
import com.yehor.finora.domain.analytics.AccountPeriodRow
import com.yehor.finora.domain.analytics.BucketPlan
import com.yehor.finora.domain.analytics.BucketUnit
import com.yehor.finora.domain.analytics.PeriodState
import com.yehor.finora.domain.analytics.Cell
import com.yehor.finora.domain.analytics.CurrencyScope
import com.yehor.finora.domain.analytics.Measure
import com.yehor.finora.domain.analytics.PeriodCalculator
import com.yehor.finora.domain.analytics.PeriodKind
import com.yehor.finora.domain.analytics.ResolvedPeriod
import com.yehor.finora.domain.analytics.Reports
import com.yehor.finora.domain.analytics.ScopeConverter
import com.yehor.finora.domain.operation.FilterCodec
import com.yehor.finora.domain.operation.TransactionFilter
import com.yehor.finora.domain.operation.TransactionItem
import com.yehor.finora.domain.tag.TagMatchMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

enum class StatsTab { EXPENSES, INCOME, TAGS, ACCOUNTS }

/** What the user chose on the Statistics shell. Survives rotation and process death. */
data class StatsControls(
    val tab: StatsTab = StatsTab.EXPENSES,
    val selectedAccountIds: Set<Long> = emptySet(),
    val compare: Boolean = false,
    /** Entered categories from the root down. */
    val path: List<Long> = emptyList(),
    /** Types, accounts, categories, tags and match mode of the Tags tab. Dates come from the period. */
    val tagFilter: TransactionFilter = TransactionFilter(),
    val tagMeasure: Measure = Measure.EXPENSE,
)

data class FlowTab(
    val report: FlowReport,
    /** The period filter the rows were counted with. */
    val base: TransactionFilter,
)

data class TagsTab(
    /** The full filter, with period and scope, for hand-offs to Transactions (R15). */
    val filter: TransactionFilter,
    /** Null while no tag is selected: nothing is computed. */
    val report: TagsReport?,
    val recent: List<TransactionItem>,
)

data class AccountPeriodView(val account: Account, val row: AccountPeriodRow)

sealed interface StatsContent {
    data class Flows(val tab: FlowTab) : StatsContent
    data class Tags(val tab: TagsTab) : StatsContent
    data class Accounts(val rows: List<AccountPeriodView>) : StatsContent
}

data class StatisticsUiState(
    val loading: Boolean = true,
    val env: AnalyticsEnv? = null,
    val controls: StatsControls = StatsControls(),
    val scope: CurrencyScope? = null,
    val conversion: ConversionInfo = ConversionInfo.NONE,
    val content: StatsContent? = null,
    /** The previous period of the same kind, for the Compare label. */
    val previousPeriod: ResolvedPeriod? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val environment: AnalyticsEnvironment,
    private val analytics: AnalyticsRepository,
    private val transactions: TransactionRepository,
) : ViewModel() {

    private val _controls = MutableStateFlow(restore())

    private fun restore(): StatsControls {
        val tab = savedState.get<String>(KEY_TAB)?.let { name -> StatsTab.entries.firstOrNull { it.name == name } }
            ?: StatsTab.EXPENSES
        val savedFilter = savedState.get<String>(KEY_TAG_FILTER)
        val argTag = savedState.get<Long>("tagId")?.takeIf { it > 0 }
        val tagFilter = when {
            savedFilter != null -> FilterCodec.decode(savedFilter)
            argTag != null -> TransactionFilter(tagIds = setOf(argTag))
            else -> TransactionFilter()
        }
        val accounts = savedState.get<String>(KEY_ACCOUNTS).orEmpty().split(',').mapNotNull { it.toLongOrNull() }.toSet()
        return StatsControls(
            tab = tab,
            selectedAccountIds = accounts,
            compare = savedState.get<Boolean>(KEY_COMPARE) ?: false,
            path = savedState.get<String>(KEY_PATH).orEmpty().split(',').mapNotNull { it.toLongOrNull() },
            tagFilter = tagFilter,
            tagMeasure = savedState.get<String>(KEY_TAG_MEASURE)
                ?.let { name -> Measure.entries.firstOrNull { it.name == name } } ?: Measure.EXPENSE,
        )
    }

    private fun change(block: (StatsControls) -> StatsControls) {
        _controls.update(block)
        val c = _controls.value
        savedState[KEY_TAB] = c.tab.name
        savedState[KEY_ACCOUNTS] = c.selectedAccountIds.joinToString(",")
        savedState[KEY_COMPARE] = c.compare
        savedState[KEY_PATH] = c.path.joinToString(",")
        savedState[KEY_TAG_FILTER] = FilterCodec.encode(c.tagFilter)
        savedState[KEY_TAG_MEASURE] = c.tagMeasure.name
    }

    /** The newest environment, for turning a chart window into a period. */
    @Volatile private var latestEnv: AnalyticsEnv? = null

    private val env: Flow<AnalyticsEnv> = environment.observe()
        .onEach { latestEnv = it }
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    val state: StateFlow<StatisticsUiState> = combine(env, _controls) { env, controls -> env to controls }
        .flatMapLatest { (env, controls) -> content(env, controls) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatisticsUiState())

    /** A window the trend chart is being panned or pinched to; [base] is the period it started from. */
    private data class Preview(val window: ChartWindow, val base: PeriodState?)

    private val preview = MutableStateFlow<Preview?>(null)

    /** The bucket unit last drawn, so a window in the 93 to 99 day band keeps it while pinching. */
    @Volatile private var lastUnit: BucketUnit? = null

    /**
     * The trend wave of Expenses or Income (null on the other tabs): the selected period or, while the
     * chart is being panned or pinched, the window under the fingers. Previews are debounced a little
     * so a fast gesture does not query on every frame.
     */
    @OptIn(FlowPreview::class)
    val trend: StateFlow<StatsTrend?> = combine(
        env,
        _controls,
        preview.debounce { if (it == null) 0L else PREVIEW_DEBOUNCE_MS },
    ) { env, controls, preview -> Triple(env, controls, preview) }
        .flatMapLatest { (env, controls, preview) -> trend(env, controls, preview) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // ---- Controls ----

    /** Changing tab clears the drill-down path. */
    fun selectTab(tab: StatsTab) = change { it.copy(tab = tab, path = emptyList()) }

    fun toggleAccount(accountId: Long) = change { c ->
        c.copy(
            selectedAccountIds = if (accountId in c.selectedAccountIds) c.selectedAccountIds - accountId else c.selectedAccountIds + accountId,
            path = emptyList(),
        )
    }

    fun clearAccounts() = change {
        it.copy(selectedAccountIds = emptySet(), path = emptyList())
    }

    fun setCompare(on: Boolean) = change { it.copy(compare = on) }

    /** Redraws the trend over [window] without changing the selected period. */
    fun previewWindow(window: ChartWindow) {
        preview.value = Preview(window, latestEnv?.periodState)
    }

    /** Makes [window] the selected period: a calendar week, month or year when it is one, else a custom range. */
    fun commitWindow(window: ChartWindow) {
        val env = latestEnv ?: return
        if (window == ChartWindow.of(env.period)) return
        val state = window.toPeriodState(env.firstDayOfWeek, env.today)
        preview.value = Preview(window, state)
        environment.setPeriod(state)
        change { it.copy(path = emptyList()) }
    }

    fun enter(categoryId: Long) = change { it.copy(path = it.path + categoryId) }

    /** Goes up one level, or to [depth] entered categories when it is given. */
    fun back(depth: Int = _controls.value.path.size - 1) =
        change { it.copy(path = it.path.take(depth.coerceAtLeast(0))) }

    fun selectPeriod(kind: PeriodKind) {
        preview.value = null
        environment.selectPeriod(kind)
        change { it.copy(path = emptyList()) }
    }

    fun setCustomPeriod(start: LocalDate, end: LocalDate) {
        preview.value = null
        environment.setCustomPeriod(start, end)
        change { it.copy(path = emptyList()) }
    }

    /**
     * The month range picked on the range wheels. One month is that month, January to the current month
     * is the whole current year (the wheels cannot reach future months), anything else a custom range.
     */
    fun selectMonths(from: YearMonth, to: YearMonth) {
        val env = latestEnv ?: return
        val wholeYear = from.monthValue == 1 && from.year == to.year && to == YearMonth.from(env.today)
        val end = if (wholeYear) LocalDate.of(from.year, 12, 31) else to.atEndOfMonth()
        preview.value = null
        environment.setPeriod(ChartWindow(from.atDay(1), end).toPeriodState(env.firstDayOfWeek, env.today))
        change { it.copy(path = emptyList()) }
    }

    fun shiftPeriod(steps: Int) {
        preview.value = null
        environment.shiftPeriod(steps)
        change { it.copy(path = emptyList()) }
    }

    fun toggleTag(tagId: Long) = changeTags { f ->
        f.copy(tagIds = if (tagId in f.tagIds) f.tagIds - tagId else f.tagIds + tagId)
    }

    fun setTagMode(mode: TagMatchMode) = changeTags { it.copy(tagMode = mode) }

    fun setTagMeasure(measure: Measure) = change { it.copy(tagMeasure = measure, path = emptyList()) }

    /**
     * Applies the filter sheet. The sheet's date range becomes the shared period (as a Custom
     * range); everything else is kept in the Tags filter. The tags themselves are picked on screen.
     */
    fun applyTagSheet(filter: TransactionFilter, period: ResolvedPeriod) {
        val from = filter.from?.let { dateOf(it) }
        val to = filter.toExclusive?.let { dateOf(it - 1) }
        if (from != null || to != null) {
            val start = from ?: period.fromDate
            val end = to ?: period.toDateInclusive
            environment.setCustomPeriod(start, if (end.isBefore(start)) start else end)
        }
        changeTags {
            it.copy(
                types = filter.types,
                accountIds = filter.accountIds,
                categoryIds = filter.categoryIds,
                includeUncategorized = filter.includeUncategorized,
            )
        }
    }

    private fun changeTags(block: (TransactionFilter) -> TransactionFilter) =
        change { it.copy(tagFilter = block(it.tagFilter), path = emptyList()) }

    private fun dateOf(millis: Long): LocalDate =
        java.time.Instant.ofEpochMilli(millis).atZone(environment.zone()).toLocalDate()

    // ---- Content ----

    private fun content(env: AnalyticsEnv, controls: StatsControls): Flow<StatisticsUiState> {
        val scope: CurrencyScope = resolveScope(controls.selectedAccountIds, env.allAccounts, env.display)
        val previous = if (controls.compare && controls.tab != StatsTab.ACCOUNTS) {
            PeriodCalculator.resolve(PeriodCalculator.previous(env.period.period), env.zone, env.firstDayOfWeek)
        } else {
            null
        }
        val tabContent: Flow<Pair<StatsContent, Collection<Cell>>> = when (controls.tab) {
            StatsTab.EXPENSES -> flows(env, controls, scope, Measure.EXPENSE, previous)
            StatsTab.INCOME -> flows(env, controls, scope, Measure.INCOME, previous)
            StatsTab.TAGS -> tags(env, controls, scope, previous)
            StatsTab.ACCOUNTS -> accounts(env, controls)
        }
        return tabContent.map { (content, cells) ->
            StatisticsUiState(
                loading = false,
                env = env,
                controls = controls,
                scope = scope,
                conversion = conversionInfo(cells),
                content = content,
                previousPeriod = previous,
            )
        }
    }

    private fun flows(
        env: AnalyticsEnv,
        controls: StatsControls,
        scope: CurrencyScope,
        measure: Measure,
        previous: ResolvedPeriod?,
    ): Flow<Pair<StatsContent, Collection<Cell>>> {
        val base = TransactionFilter(
            from = env.period.from,
            toExclusive = env.period.toExclusive,
            accountIds = controls.selectedAccountIds,
        )
        val previousBase = previous?.let {
            TransactionFilter(from = it.from, toExclusive = it.toExclusive, accountIds = controls.selectedAccountIds)
        }
        val path = controls.path
        val current = analytics.observeByCategory(base, measure).asRows()
        val before = previousBase?.let { analytics.observeByCategory(it, measure).asRows() } ?: flowOf(null)
        return combine(
            current,
            before,
            analytics.observeByAccount(base, measure).asRows(),
            analytics.observeByTag(base, measure).asRows(),
        ) { cur, prev, byAccount, byTag ->
            val report = ReportBuilder.flow(env, scope, measure, cur, prev, byAccount, byTag, path)
            StatsContent.Flows(FlowTab(report, base)) to cellsOf(report)
        }
    }

    private fun trend(env: AnalyticsEnv, controls: StatsControls, preview: Preview?): Flow<StatsTrend?> {
        val measure = when (controls.tab) {
            StatsTab.EXPENSES -> Measure.EXPENSE
            StatsTab.INCOME -> Measure.INCOME
            else -> return flowOf(null)
        }
        val committed = ChartWindow.of(env.period)
        // A preview counts while the period it started from (or the one it was committed as) is still selected.
        val window = preview?.takeIf { it.base == env.periodState || it.window == committed }?.window ?: committed
        val period = if (window == committed) {
            env.period
        } else {
            window.toPeriodState(env.firstDayOfWeek, env.today).resolve(env.today, env.zone, env.firstDayOfWeek)
        }
        val (unit, buckets) = trendBuckets(period, env.zone, lastUnit)
        lastUnit = unit
        val previousBuckets = if (controls.compare) {
            val previous = PeriodCalculator.resolve(PeriodCalculator.previous(period.period), env.zone, env.firstDayOfWeek)
            BucketPlan.split(previous.fromDate, previous.toDateInclusive, unit, env.zone)
        } else {
            null
        }
        val scope = resolveScope(controls.selectedAccountIds, env.allAccounts, env.display)
        val base = TransactionFilter(
            accountIds = controls.selectedAccountIds,
            categoryIds = controls.path.lastOrNull()?.let { setOf(it) } ?: emptySet(),
        )
        val converter = ScopeConverter(scope, env.table, env.now)
        val before = previousBuckets?.let { analytics.observeByBucket(base, measure, it).asRows() } ?: flowOf(null)
        return combine(analytics.observeByBucket(base, measure, buckets).asRows(), before) { rows, previousRows ->
            val points = Reports.trend(buckets, rows.list(), converter)
            val compare = if (previousBuckets != null && previousRows != null) {
                val previousValues = trendValues(Reports.trend(previousBuckets, previousRows.list(), converter), env.today)
                alignPrevious(buckets, previousBuckets).map { index -> index?.let { previousValues[it] } }
            } else {
                null
            }
            StatsTrend(
                window = window,
                measure = measure,
                unit = unit,
                buckets = buckets,
                values = trendValues(points, env.today),
                compare = compare,
                average = trendAverage(points, env.today, scope.currency()),
                base = base,
            )
        }
    }

    private fun tags(
        env: AnalyticsEnv,
        controls: StatsControls,
        scope: CurrencyScope,
        previous: ResolvedPeriod?,
    ): Flow<Pair<StatsContent, Collection<Cell>>> {
        val chosen = controls.tagFilter
        val effectiveAccountIds = if (chosen.accountIds.isNotEmpty()) chosen.accountIds else controls.selectedAccountIds
        val filter = chosen.copy(
            from = env.period.from,
            toExclusive = env.period.toExclusive,
            accountIds = effectiveAccountIds,
        )
        if (filter.tagIds.isEmpty()) {
            return flowOf(StatsContent.Tags(TagsTab(filter, null, emptyList())) to emptyList())
        }
        val measure = chosen.types.singleOrNull()?.let { type ->
            Measure.entries.firstOrNull { it.operationType() == type }
        } ?: controls.tagMeasure
        val previousFilter = previous?.let { filter.copy(from = it.from, toExclusive = it.toExclusive) }
        val (unit, buckets) = BucketPlan.auto(env.period, env.zone)

        fun totals(f: TransactionFilter) = combine(
            analytics.observeByCategory(f, Measure.INCOME).asRows(),
            analytics.observeByCategory(f, Measure.EXPENSE).asRows(),
            analytics.observeByCategory(f, Measure.TRANSFER).asRows(),
        ) { income, expense, transfer -> Triple(income, expense, transfer) }

        val currentTotals = totals(filter)
        val previousTotals = previousFilter?.let { totals(it) } ?: flowOf(null)
        val measureRows = analytics.observeByCategory(filter, measure).asRows()
        val previousMeasureRows = previousFilter?.let { analytics.observeByCategory(it, measure).asRows() } ?: flowOf(null)
        val byAccount = analytics.observeByAccount(filter, measure).asRows()
        val byDate = analytics.observeByBucket(filter, measure, buckets).asRows()
        val recent = transactions.observeRecent(filter, TAG_RECENT)

        val reports = combine(currentTotals, previousTotals, measureRows, previousMeasureRows) { t, pt, rows, prevRows ->
            TagParts(t, pt, rows, prevRows)
        }
        return combine(reports, byAccount, byDate, recent) { parts, accountRows, dateRows, items ->
            val types = filter.types
            val figures = ReportBuilder.figures(env, scope, types, parts.totals.first, parts.totals.second, parts.totals.third)
            val previousFigures = parts.previousTotals?.let {
                ReportBuilder.figures(env, scope, types, it.first, it.second, it.third)
            }
            val category = ReportBuilder.category(env, scope, measure, parts.rows, parts.previousRows, controls.path)
            val converter = ScopeConverter(scope, env.table, env.now)
            val report = TagsReport(
                figures = figures,
                previous = previousFigures,
                measure = measure,
                category = category,
                byAccount = ReportBuilder.breakdown(accountRows, converter, category.total),
                byDate = Reports.trend(buckets, dateRows.list(), converter),
                dateUnit = unit,
            )
            val cells = listOfNotNull(figures.income, figures.expense, figures.net, figures.transferred) +
                category.lines.map { it.row.cell } + report.byAccount.map { it.cell } + report.byDate.map { it.cell }
            StatsContent.Tags(TagsTab(filter, report, items)) to cells
        }
    }

    private class TagParts(
        val totals: Triple<Rows, Rows, Rows>,
        val previousTotals: Triple<Rows, Rows, Rows>?,
        val rows: Rows,
        val previousRows: Rows?,
    )

    private fun accounts(env: AnalyticsEnv, controls: StatsControls): Flow<Pair<StatsContent, Collection<Cell>>> =
        analytics.observeAccountsOverview(env.period).map { rows ->
            val byId = rows.associateBy { it.accountId }
            val filteredAccounts = if (controls.selectedAccountIds.isEmpty()) {
                env.accounts
            } else {
                env.accounts.filter { it.account.id in controls.selectedAccountIds }
            }
            val views = filteredAccounts
                .map { it.account }
                .sortedWith(compareBy({ it.status }, { it.name.lowercase() }))
                .mapNotNull { account -> byId[account.id]?.let { AccountPeriodView(account, it) } }
            StatsContent.Accounts(views) to emptyList()
        }

    private fun cellsOf(report: FlowReport): List<Cell> =
        listOf(report.category.total) +
            report.category.lines.map { it.row.cell } +
            report.byAccount.map { it.cell } +
            report.byTag.map { it.cell }

    companion object {
        private const val KEY_TAB = "tab"
        private const val KEY_ACCOUNTS = "stats_accounts"
        private const val KEY_COMPARE = "stats_compare"
        private const val KEY_PATH = "stats_path"
        private const val KEY_TAG_FILTER = "stats_tag_filter"
        private const val KEY_TAG_MEASURE = "stats_tag_measure"
        private const val TAG_RECENT = 5
        private const val PREVIEW_DEBOUNCE_MS = 50L
    }
}
