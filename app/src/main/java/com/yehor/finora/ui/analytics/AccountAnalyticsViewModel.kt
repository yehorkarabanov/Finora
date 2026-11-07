package com.yehor.finora.ui.analytics

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yehor.finora.data.repo.AccountRepository
import com.yehor.finora.data.repo.AnalyticsRepository
import com.yehor.finora.data.repo.TransactionRepository
import com.yehor.finora.domain.account.AccountDetail
import com.yehor.finora.domain.analytics.AccountFigures
import com.yehor.finora.domain.analytics.BalancePoint
import com.yehor.finora.domain.analytics.BucketPlan
import com.yehor.finora.domain.analytics.BucketUnit
import com.yehor.finora.domain.analytics.PeriodKind
import com.yehor.finora.domain.money.MoneyOverflowException
import com.yehor.finora.domain.operation.TransactionFilter
import com.yehor.finora.domain.operation.TransactionItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

private const val ACCOUNT_RECENT = 10

sealed interface AccountAnalyticsUiState {
    data object Loading : AccountAnalyticsUiState
    data object Missing : AccountAnalyticsUiState

    data class Loaded(
        val env: AnalyticsEnv,
        val detail: AccountDetail,
        /** Null when a sum did not fit in a Long (R13). */
        val figures: AccountFigures?,
        /** Empty when a running balance did not fit in a Long. */
        val series: List<BalancePoint>,
        val seriesUnit: BucketUnit,
        val recent: List<TransactionItem>,
        /** The account and the period, for "Show all". */
        val filter: TransactionFilter,
    ) : AccountAnalyticsUiState
}

/** Account statistics. Always native: nothing here is converted (PRD 12.6). Read-only (R1). */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AccountAnalyticsViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val environment: AnalyticsEnvironment,
    private val analytics: AnalyticsRepository,
    accounts: AccountRepository,
    private val transactions: TransactionRepository,
) : ViewModel() {

    private val accountId: Long = checkNotNull(savedState["accountId"])

    val state: StateFlow<AccountAnalyticsUiState> =
        combine(environment.observe(), accounts.observe(accountId)) { env, detail -> env to detail }
            .flatMapLatest { (env, detail) ->
                if (detail == null) flowOf(AccountAnalyticsUiState.Missing) else loaded(env, detail)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountAnalyticsUiState.Loading)

    private fun loaded(env: AnalyticsEnv, detail: AccountDetail): Flow<AccountAnalyticsUiState> {
        val (unit, buckets) = BucketPlan.auto(env.period, env.zone)
        val filter = TransactionFilter(
            accountIds = setOf(accountId),
            from = env.period.from,
            toExclusive = env.period.toExclusive,
        )
        val figures: Flow<AccountFigures?> = analytics.observeAccountFigures(accountId, env.period)
            .catch { e -> if (e is MoneyOverflowException) emit(null) else throw e }
        val series: Flow<List<BalancePoint>> = analytics.observeBalanceSeries(accountId, buckets)
            .catch { e -> if (e is MoneyOverflowException) emit(emptyList()) else throw e }
        return combine(figures, series, transactions.observeRecent(filter, ACCOUNT_RECENT)) { f, s, recent ->
            AccountAnalyticsUiState.Loaded(env, detail, f, s, unit, recent, filter)
        }
    }

    fun selectPeriod(kind: PeriodKind) = environment.selectPeriod(kind)

    fun setCustomPeriod(start: LocalDate, end: LocalDate) = environment.setCustomPeriod(start, end)

    fun shiftPeriod(steps: Int) = environment.shiftPeriod(steps)
}
