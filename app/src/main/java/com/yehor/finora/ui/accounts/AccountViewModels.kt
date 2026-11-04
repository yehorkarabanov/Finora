package com.yehor.finora.ui.accounts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yehor.finora.data.repo.AccountRepository
import com.yehor.finora.data.repo.OpeningBalanceOrderException
import com.yehor.finora.data.repo.SettingsRepository
import com.yehor.finora.data.repo.TransactionRepository
import com.yehor.finora.domain.account.AccountDetail
import com.yehor.finora.domain.account.AccountType
import com.yehor.finora.domain.account.AccountWithBalance
import com.yehor.finora.domain.account.OpeningDatePolicy
import com.yehor.finora.domain.model.AccountStatus
import com.yehor.finora.domain.money.Currency
import com.yehor.finora.domain.operation.TransactionFilter
import com.yehor.finora.domain.operation.TransactionItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId
import javax.inject.Inject

// ---- Accounts list ----

data class AccountsUiState(
    val loading: Boolean = true,
    val active: List<AccountWithBalance> = emptyList(),
    val archived: List<AccountWithBalance> = emptyList(),
)

@HiltViewModel
class AccountsViewModel @Inject constructor(repository: AccountRepository) : ViewModel() {
    val state: StateFlow<AccountsUiState> = repository.observeAll()
        .map { list ->
            val (active, archived) = list.partition { it.account.status == AccountStatus.ACTIVE }
            AccountsUiState(loading = false, active = active, archived = archived)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountsUiState())
}

// ---- Account detail ----

sealed interface AccountDetailUiState {
    data object Loading : AccountDetailUiState
    data object Missing : AccountDetailUiState
    data class Loaded(
        val detail: AccountDetail,
        val transactionCount: Int,
        val recentTransactions: List<TransactionItem> = emptyList(),
    ) : AccountDetailUiState {
        /** Rule A5. */
        val canDelete: Boolean get() = transactionCount == 0
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AccountDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: AccountRepository,
    transactionRepository: TransactionRepository,
) : ViewModel() {
    private val accountId: Long = checkNotNull(savedStateHandle["accountId"])

    val state: StateFlow<AccountDetailUiState> = combine(
        repository.observe(accountId),
        transactionRepository.observeRecent(TransactionFilter(accountIds = setOf(accountId)), RECENT_LIMIT),
    ) { detail, recent ->
        if (detail == null) {
            AccountDetailUiState.Missing
        } else {
            AccountDetailUiState.Loaded(detail, repository.nonOpeningEntryCount(accountId), recent)
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
        viewModelScope.launch {
            // A refusal (history appeared meanwhile) leaves the account in place; the state refreshes.
            runCatching { repository.delete(accountId) }
        }
    }
}

// ---- Account form ----

@HiltViewModel
class AccountEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: AccountRepository,
    private val settings: SettingsRepository,
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
            val currency = settings.displayCurrency.first().currency
            _state.update {
                it.copy(
                    loaded = true,
                    currency = currency,
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
                        name = name, currencyCode = s.currency.code, type = s.type.name,
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
