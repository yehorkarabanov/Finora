package com.yehor.finora.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yehor.finora.data.repo.AnalyticsRepository
import com.yehor.finora.data.repo.RecurringRepository
import com.yehor.finora.data.repo.TransactionRepository
import com.yehor.finora.domain.account.AccountWithBalance
import com.yehor.finora.domain.analytics.Cell
import com.yehor.finora.domain.analytics.CurrencyScope
import com.yehor.finora.domain.analytics.Measure
import com.yehor.finora.domain.analytics.PeriodKind
import com.yehor.finora.domain.analytics.PeriodState
import com.yehor.finora.domain.analytics.ResolvedPeriod
import com.yehor.finora.domain.analytics.UpcomingItem
import com.yehor.finora.domain.analytics.UpcomingOccurrences
import com.yehor.finora.domain.model.AccountStatus
import com.yehor.finora.domain.money.Currency
import com.yehor.finora.domain.operation.TransactionFilter
import com.yehor.finora.domain.operation.TransactionItem
import com.yehor.finora.domain.recurring.ScheduleStatus
import com.yehor.finora.ui.accounts.ValuationCardLogic
import com.yehor.finora.ui.accounts.ValuationCardState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

private const val RECENT_LIMIT = 5

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
    val periodState: PeriodState = PeriodState(),
    val period: ResolvedPeriod? = null,
    val today: LocalDate? = null,
    val display: Currency? = null,
    val valuation: ValuationCardState? = null,
    val income: Cell? = null,
    val expense: Cell? = null,
    val net: Cell? = null,
    val conversion: ConversionInfo = ConversionInfo.NONE,
    val currencyFlows: List<CurrencyFlow> = emptyList(),
    val activeAccounts: List<AccountWithBalance> = emptyList(),
    val distribution: CategoryReport? = null,
    val recent: List<TransactionItem> = emptyList(),
    val upcoming: UpcomingState = UpcomingState(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val environment: AnalyticsEnvironment,
    private val analytics: AnalyticsRepository,
    transactions: TransactionRepository,
    recurring: RecurringRepository,
) : ViewModel() {

    private class PeriodRows(val income: Rows, val expense: Rows)

    private val env: Flow<AnalyticsEnv> = environment.observe()

    private val periodRows: Flow<PeriodRows> = env
        .map { it.period.from to it.period.toExclusive }
        .distinctUntilChanged()
        .flatMapLatest { (from, to) ->
            val filter = TransactionFilter(from = from, toExclusive = to)
            combine(
                analytics.observeByCategory(filter, Measure.INCOME).asRows(),
                analytics.observeByCategory(filter, Measure.EXPENSE).asRows(),
            ) { income, expense -> PeriodRows(income, expense) }
        }

    private val upcoming: Flow<UpcomingState> = combine(
        recurring.observeSchedules(),
        recurring.issues,
        environment.observe().map { it.today }.distinctUntilChanged(),
    ) { schedules, issues, today ->
        val items = UpcomingOccurrences.build(schedules, issues.keys, today)
        val active = schedules.any { it.nextDate != null && it.status == ScheduleStatus.ACTIVE }
        UpcomingState(items, attentionCount = issues.size, hasActiveSchedules = active)
    }

    val state: StateFlow<DashboardUiState> = combine(
        env,
        periodRows,
        transactions.observeRecent(TransactionFilter(), RECENT_LIMIT),
        upcoming,
    ) { env, rows, recent, upcoming -> build(env, rows, recent, upcoming) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    private fun build(env: AnalyticsEnv, rows: PeriodRows, recent: List<TransactionItem>, upcoming: UpcomingState): DashboardUiState {
        val scope = CurrencyScope.Display(env.display)
        val active = env.accounts.filter { it.account.status == AccountStatus.ACTIVE }
        val figures = ReportBuilder.figures(env, scope, emptySet(), rows.income, rows.expense, Rows.Ok(emptyList()))
        val distribution = ReportBuilder.category(env, scope, Measure.EXPENSE, rows.expense, previous = null, path = emptyList())
        val cells = listOfNotNull(figures.income, figures.expense, figures.net, distribution.total) +
            distribution.lines.map { it.row.cell }
        return DashboardUiState(
            loading = false,
            hasAccounts = env.accounts.isNotEmpty(),
            periodState = env.periodState,
            period = env.period,
            today = env.today,
            display = env.display,
            valuation = ValuationCardLogic.build(active, env.display, env.table, env.now),
            income = figures.income,
            expense = figures.expense,
            net = figures.net,
            conversion = conversionInfo(cells),
            currencyFlows = currencyFlows(rows.income.list(), rows.expense.list()),
            activeAccounts = active,
            distribution = distribution,
            recent = recent,
            upcoming = upcoming,
        )
    }

    fun selectPeriod(kind: PeriodKind) = environment.selectPeriod(kind)

    fun setCustomPeriod(start: LocalDate, end: LocalDate) = environment.setCustomPeriod(start, end)
}
