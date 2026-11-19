package com.yehor.finora.ui.accounts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yehor.finora.data.repo.AccountRepository
import com.yehor.finora.data.repo.AnalyticsRepository
import com.yehor.finora.data.repo.OpeningBalanceOrderException
import com.yehor.finora.data.repo.RateRepository
import com.yehor.finora.data.repo.SettingsRepository
import com.yehor.finora.data.repo.TransactionRepository
import com.yehor.finora.domain.account.AccountDetail
import com.yehor.finora.domain.account.AccountType
import com.yehor.finora.domain.account.AccountWithBalance
import com.yehor.finora.domain.account.OpeningDatePolicy
import com.yehor.finora.domain.model.AccountStatus
import com.yehor.finora.domain.analytics.BalancePoint
import com.yehor.finora.domain.money.Currency
import com.yehor.finora.domain.money.MoneyOverflowException
import com.yehor.finora.domain.operation.TransactionFilter
import com.yehor.finora.domain.operation.TransactionItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

// ---- Accounts list ----

data class AccountsUiState(
    val loading: Boolean = true,
    val active: List<AccountWithBalance> = emptyList(),
    val archived: List<AccountWithBalance> = emptyList(),
    val valuation: ValuationCardState? = null,
    val displayCurrency: Currency? = null,
    /** Share of each active account in the total, for the split bar. */
    val shares: List<Pair<Long, Float>> = emptyList(),
    /** Sparkline and this-month change per active account; missing until the series arrive. */
    val trends: Map<Long, AccountTrend> = emptyMap(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val repository: AccountRepository,
    rates: RateRepository,
    settings: SettingsRepository,
    analytics: AnalyticsRepository,
) : ViewModel() {

    // One flow for all accounts' series, so rows never run queries of their own.
    private val trends: Flow<Map<Long, AccountTrend>> = repository.observeAll()
        .map { list -> list.filter { it.account.status == AccountStatus.ACTIVE }.map { it.account.id } }
        .distinctUntilChanged()
        .flatMapLatest { ids ->
            if (ids.isEmpty()) {
                flowOf(emptyMap())
            } else {
                val zone = ZoneId.systemDefault()
                val today = LocalDate.now(zone)
                val buckets = trendBuckets(today, zone)
                val monthStart = today.withDayOfMonth(1)
                combine(
                    ids.map { id ->
                        analytics.observeBalanceSeries(id, buckets)
                            .catch { e -> if (e is MoneyOverflowException) emit(emptyList<BalancePoint>()) else throw e }
                            .map { id to accountTrend(it, monthStart) }
                    },
                ) { it.toMap() }
            }
        }
        .onStart { emit(emptyMap()) }

    val state: StateFlow<AccountsUiState> = combine(
        repository.observeAll(),
        rates.observeTable(),
        settings.displayCurrency,
        trends,
    ) { list, table, display, trendMap ->
        val (active, archived) = list.partition { it.account.status == AccountStatus.ACTIVE }
        val now = System.currentTimeMillis()
        AccountsUiState(
            loading = false,
            active = active,
            archived = archived,
            valuation = ValuationCardLogic.build(active, display.currency, table, now),
            displayCurrency = display.currency,
            shares = accountShares(convertedBalances(active, display.currency, table, now)),
            trends = trendMap,
        )
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountsUiState())

    fun restore(accountId: Long) {
        viewModelScope.launch { repository.setArchived(accountId, false) }
    }
}

// ---- Account detail ----

sealed interface AccountDetailUiState {
    data object Loading : AccountDetailUiState
    data object Missing : AccountDetailUiState
    data class Loaded(
        val detail: AccountDetail,
        val transactionCount: Int,
        val scheduleCount: Int = 0,
        val recentTransactions: List<TransactionItem> = emptyList(),
        val trend: AccountTrend = AccountTrend(emptyList(), null),
    ) : AccountDetailUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AccountDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: AccountRepository,
    transactionRepository: TransactionRepository,
    analytics: AnalyticsRepository,
) : ViewModel() {
    private val accountId: Long = checkNotNull(savedStateHandle["accountId"])

    private val trend: Flow<AccountTrend> = run {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        analytics.observeBalanceSeries(accountId, trendBuckets(today, zone))
            .catch { e -> if (e is MoneyOverflowException) emit(emptyList<BalancePoint>()) else throw e }
            .map { accountTrend(it, today.withDayOfMonth(1)) }
            .onStart { emit(AccountTrend(emptyList(), null)) }
    }

    val state: StateFlow<AccountDetailUiState> = combine(
        repository.observe(accountId),
        transactionRepository.observeRecent(TransactionFilter(accountIds = setOf(accountId)), RECENT_LIMIT),
        trend,
    ) { detail, recent, trend ->
        if (detail == null) {
            AccountDetailUiState.Missing
        } else {
            AccountDetailUiState.Loaded(
                detail,
                repository.nonOpeningEntryCount(accountId),
                repository.scheduleCount(accountId),
                recent,
                trend,
            )
        }
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountDetailUiState.Loading)

    private companion object {
        const val RECENT_LIMIT = 10
    }

    fun setArchived(archived: Boolean) {
        viewModelScope.launch { repository.setArchived(accountId, archived) }
    }

    fun delete() {
        viewModelScope.launch { repository.delete(accountId) }
    }
}

// ---- Account form ----

@HiltViewModel
class AccountEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: AccountRepository,
) : ViewModel() {
    private val accountId: Long? = savedStateHandle.get<Long>("accountId")
    private val policy = OpeningDatePolicy(ZoneId.systemDefault())

    private val _state = MutableStateFlow(AccountFormState(isEdit = accountId != null))
    val state: StateFlow<AccountFormState> = _state.asStateFlow()

    init {
        viewModelScope.launch { load() }
    }

    fun policy(): OpeningDatePolicy = policy

    private suspend fun load() {
        if (accountId == null) {
            _state.update {
                it.copy(
                    loaded = true,
                    dateMillis = policy.defaultForNewAccount(System.currentTimeMillis()),
                )
            }
            return
        }
        val detail = repository.observe(accountId).first() ?: return
        val account = detail.account
        val opening = detail.openingBalance
        val now = System.currentTimeMillis()
        _state.update {
            it.copy(
                loaded = true,
                name = account.name,
                type = account.type,
                currency = account.currency,
                icon = account.icon,
                color = account.color,
                description = account.description.orEmpty(),
                archived = account.status == AccountStatus.ARCHIVED,
                originalOpeningMinor = opening?.money?.amountMinor,
                earliestOtherEntryAt = detail.earliestOtherEntryAt,
                amountText = AccountFormLogic.amountTextOf(opening?.money?.amountMinor, account.currency),
                dateMillis = opening?.occurredAt ?: policy.defaultForExistingAccount(now, detail.earliestOtherEntryAt),
            )
        }
    }

    private fun edit(block: (AccountFormState) -> AccountFormState) {
        _state.update { block(it).copy(dateOrderRejected = false, saveFailed = false) }
    }

    fun onName(v: String) = edit { it.copy(name = v) }
    fun onType(v: AccountType) = edit { it.copy(type = v) }
    fun onCurrency(v: Currency) = edit { it.copy(currency = v) }
    fun onIcon(v: String) = edit { it.copy(icon = v) }
    fun onColor(v: Long) = edit { it.copy(color = v) }
    fun onDescription(v: String) = edit { it.copy(description = v) }
    fun onAmount(v: String) = edit { it.copy(amountText = v) }
    fun onDate(startOfDayMillis: Long) = edit { it.copy(dateMillis = startOfDayMillis) }

    fun save() {
        val s = _state.value
        val now = System.currentTimeMillis()
        if (!AccountFormLogic.canSave(s, policy, now)) return
        _state.update { it.copy(saving = true, dateOrderRejected = false, saveFailed = false) }
        viewModelScope.launch {
            val name = s.name.trim()
            val description = s.description.trim().ifEmpty { null }
            val openingMinor = if (AccountFormLogic.openingSectionEnabled(s)) AccountFormLogic.openingMinor(s) else null
            try {
                if (accountId == null) {
                    repository.create(
                        name = name, currencyCode = checkNotNull(s.currency).code, type = s.type.name,
                        icon = s.icon, color = s.color, description = description,
                        openingMinor = openingMinor, openingDate = policy.defaultForNewAccount(now), now = now,
                    )
                } else {
                    repository.updateWithOpeningBalance(
                        id = accountId, name = name, type = s.type.name, icon = s.icon, color = s.color,
                        description = description, openingMinor = openingMinor, openingDate = s.dateMillis, now = now,
                    )
                }
                _state.update { it.copy(saving = false, saved = true) }
            } catch (e: OpeningBalanceOrderException) {
                _state.update { it.copy(saving = false, dateOrderRejected = true) }
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, saveFailed = true) }
            }
        }
    }
}
