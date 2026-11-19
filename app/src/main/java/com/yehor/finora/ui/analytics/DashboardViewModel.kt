package com.yehor.finora.ui.analytics

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yehor.finora.data.repo.AnalyticsRepository
import com.yehor.finora.data.repo.RecurringRepository
import com.yehor.finora.data.repo.TransactionRepository
import com.yehor.finora.domain.account.Account
import com.yehor.finora.domain.analytics.Bucket
import com.yehor.finora.domain.analytics.BucketUnit
import com.yehor.finora.domain.analytics.Measure
import com.yehor.finora.domain.analytics.Reports
import com.yehor.finora.domain.analytics.ScopeConverter
import com.yehor.finora.domain.analytics.UpcomingItem
import com.yehor.finora.domain.analytics.UpcomingOccurrences
import com.yehor.finora.domain.analytics.netCashFlow
import com.yehor.finora.domain.analytics.orZero
import com.yehor.finora.domain.analytics.sumCells
import com.yehor.finora.domain.model.AccountStatus
import com.yehor.finora.domain.money.Currency
import com.yehor.finora.domain.operation.TransactionFilter
import com.yehor.finora.domain.operation.TransactionItem
import com.yehor.finora.domain.recurring.ScheduleStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

private const val RECENT_LIMIT = 5
private const val KEY_HOME_PERIOD = "home_period"

/** Charts of recently shown pages kept warm so paging back and forth does not reload them. */
private const val CHART_CACHE_SIZE = 16

data class UpcomingState(
    val items: List<UpcomingItem> = emptyList(),
    /** Schedules that are blocked by a generation issue. */
    val attentionCount: Int = 0,
    /** False hides the whole card (PRD 11.2 "if applicable"). */
    val hasActiveSchedules: Boolean = false,
)

data class DashboardUiState(
    val loading: Boolean = true,
    val hasAccounts: Boolean = false,
    val today: LocalDate? = null,
    val display: Currency? = null,
    /** The period [chart] belongs to. */
    val homePeriod: HomePeriod? = null,
    val chart: HomeChart? = null,
    /** Active accounts, for the account filter and the transfer action. */
    val accounts: List<Account> = emptyList(),
    val selectedAccountIds: Set<Long> = emptySet(),
    val recent: List<TransactionItem> = emptyList(),
    val upcoming: UpcomingState = UpcomingState(),
) {
    /** A transfer needs two different active accounts. */
    val canTransfer: Boolean get() = accounts.size >= 2
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val environment: AnalyticsEnvironment,
    private val analytics: AnalyticsRepository,
    private val savedState: SavedStateHandle,
    transactions: TransactionRepository,
    recurring: RecurringRepository,
) : ViewModel() {

    private val _selectedAccountIds = MutableStateFlow<Set<Long>>(emptySet())

    private val _homePeriod = MutableStateFlow(
        (parseHomePeriod(savedState.get<String>(KEY_HOME_PERIOD)) ?: HomePeriod.current(HomeMode.DAYS, environment.today()))
            .clampedTo(environment.today()),
    )

    /** Home's own period (not shared with Statistics). */
    val homePeriod: StateFlow<HomePeriod> = _homePeriod.asStateFlow()

    private val env: Flow<AnalyticsEnv> = environment.observe()
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    private val upcoming: Flow<UpcomingState> = combine(
        recurring.observeSchedules(),
        recurring.issues,
        env.map { it.today }.distinctUntilChanged(),
    ) { schedules, issues, today ->
        val items = UpcomingOccurrences.build(schedules, issues.keys, today)
        val active = schedules.any { it.nextDate != null && it.status == ScheduleStatus.ACTIVE }
        UpcomingState(items, attentionCount = issues.size, hasActiveSchedules = active)
    }

    private val recent: Flow<List<TransactionItem>> = _selectedAccountIds.flatMapLatest { accountIds ->
        transactions.observeRecent(TransactionFilter(accountIds = accountIds), RECENT_LIMIT)
    }

    /** A cached chart flow with its own sharing scope, cancelled when the entry is evicted. */
    private class CachedChart(val job: Job, val flow: Flow<HomeChart>)

    private val chartCache = object : LinkedHashMap<HomePeriod, CachedChart>(CHART_CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<HomePeriod, CachedChart>?): Boolean {
            val evict = size > CHART_CACHE_SIZE
            if (evict) eldest?.value?.job?.cancel()
            return evict
        }
    }

    private val currentChart: Flow<Pair<HomePeriod, HomeChart>> =
        _homePeriod.flatMapLatest { period -> chart(period).map { period to it } }

    val state: StateFlow<DashboardUiState> = combine(
        env,
        _selectedAccountIds,
        currentChart,
        recent,
        upcoming,
    ) { env, selectedAccounts, (period, chart), recent, upcoming ->
        DashboardUiState(
            loading = false,
            hasAccounts = env.accounts.isNotEmpty(),
            today = env.today,
            display = env.display,
            homePeriod = period,
            chart = chart,
            accounts = env.activeAccounts(),
            selectedAccountIds = selectedAccounts,
            recent = recent,
            upcoming = upcoming,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    /** The net wave of [period] for the selected accounts; each chart page observes its own. */
    fun chart(period: HomePeriod): Flow<HomeChart> = synchronized(chartCache) {
        chartCache.getOrPut(period) {
            val job = SupervisorJob(viewModelScope.coroutineContext[Job])
            val flow = combine(
                env.map { it.today to it.zone }.distinctUntilChanged(),
                _selectedAccountIds,
            ) { day, accountIds -> day to accountIds }
                .flatMapLatest { (day, accountIds) ->
                    val (today, zone) = day
                    val (unit, buckets) = period.buckets(zone, today)
                    val filter = TransactionFilter(
                        from = buckets.first().from,
                        toExclusive = buckets.last().toExclusive,
                        accountIds = accountIds,
                    )
                    combine(
                        env,
                        analytics.observeByBucket(filter, Measure.INCOME, buckets).asRows(),
                        analytics.observeByBucket(filter, Measure.EXPENSE, buckets).asRows(),
                    ) { env, income, expense ->
                        buildChart(env, accountIds, period, today, unit, buckets, income, expense)
                    }
                }
                .distinctUntilChanged()
                .shareIn(CoroutineScope(viewModelScope.coroutineContext + job), SharingStarted.WhileSubscribed(5_000), replay = 1)
            CachedChart(job, flow)
        }.flow
    }

    private fun buildChart(
        env: AnalyticsEnv,
        accountIds: Set<Long>,
        period: HomePeriod,
        today: LocalDate,
        unit: BucketUnit,
        buckets: List<Bucket>,
        income: Rows,
        expense: Rows,
    ): HomeChart {
        val scope = resolveScope(accountIds, env.activeAccounts(), env.display)
        val converter = ScopeConverter(scope, env.table, env.now)
        fun cells(rows: Rows) = if (rows is Rows.Overflow) {
            buckets.map { ReportBuilder.OVERFLOW }
        } else {
            Reports.trend(buckets, rows.list(), converter).map { it.cell }
        }
        val incomeCells = cells(income)
        val expenseCells = cells(expense)
        val currency = scope.currency()
        val incomeTotal = incomeCells.sumCells().orZero(currency)
        val expenseTotal = expenseCells.sumCells().orZero(currency)
        val conversion = conversionInfo(incomeCells + expenseCells)
        return HomeChart(
            unit = unit,
            buckets = buckets,
            values = homeChartValues(period.mode, incomeCells, expenseCells, period.lastElapsedIndex(today)),
            net = netCashFlow(incomeTotal, expenseTotal),
            income = incomeTotal,
            expense = expenseTotal,
            currencyApprox = conversion.converted,
            conversion = conversion,
        )
    }

    private fun AnalyticsEnv.activeAccounts(): List<Account> =
        accounts.filter { it.account.status == AccountStatus.ACTIVE }.map { it.account }

    private fun setHome(period: HomePeriod) {
        _homePeriod.value = period
        savedState[KEY_HOME_PERIOD] = period.toSaved()
    }

    fun setHomeMode(mode: HomeMode) {
        val current = _homePeriod.value
        if (current.mode != mode) setHome(current.withMode(mode, environment.today()))
    }

    /** Steps by the period's own length; never past today's month. */
    fun shiftHome(steps: Int) {
        val next = _homePeriod.value.shifted(steps)
        if (steps != 0 && !next.from.isAfter(YearMonth.from(environment.today()))) setHome(next)
    }

    /** A range-sheet confirm; confirming the range the sheet opened with keeps the period (see [confirmedRange]). */
    fun setHomeRange(from: YearMonth, to: YearMonth) {
        if (to.isBefore(from)) return
        val today = environment.today()
        val next = confirmedRange(_homePeriod.value, from, to, today).clampedTo(today)
        if (next != _homePeriod.value) setHome(next)
    }

    /** Jumps to [period] (a settled chart page); ignored when it starts after today's month. */
    fun showHomePeriod(period: HomePeriod) {
        if (period != _homePeriod.value && !period.from.isAfter(YearMonth.from(environment.today()))) setHome(period)
    }

    fun toggleAccount(accountId: Long) {
        _selectedAccountIds.update { if (accountId in it) it - accountId else it + accountId }
    }

    fun clearAccountSelection() {
        _selectedAccountIds.value = emptySet()
    }
}
