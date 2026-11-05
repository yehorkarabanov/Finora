package com.yehor.finora.ui.operations

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import androidx.paging.map
import com.yehor.finora.data.repo.AccountRepository
import com.yehor.finora.data.repo.CategoryRepository
import com.yehor.finora.data.repo.TagRepository
import com.yehor.finora.data.repo.TransactionRepository
import com.yehor.finora.domain.account.Account
import com.yehor.finora.domain.category.Category
import com.yehor.finora.domain.model.OperationType
import com.yehor.finora.domain.money.Currency
import com.yehor.finora.domain.operation.CurrencySummary
import com.yehor.finora.domain.operation.DayGroup
import com.yehor.finora.domain.operation.DayGrouping
import com.yehor.finora.domain.operation.FilterScope
import com.yehor.finora.domain.operation.NormalizedFilter
import com.yehor.finora.domain.operation.TransactionFilter
import com.yehor.finora.domain.operation.TransactionItem
import com.yehor.finora.domain.operation.TransactionSort
import com.yehor.finora.domain.tag.Tag
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId
import javax.inject.Inject

/** A row of the history list: a day header or a transaction. */
sealed interface TransactionListRow {
    val key: String

    data class Header(val group: DayGroup) : TransactionListRow {
        override val key: String get() = "header_$group"
    }

    data class Entry(val item: TransactionItem) : TransactionListRow {
        override val key: String get() = "entry_${item.id}"
    }
}

data class TransactionsUiState(
    val filter: TransactionFilter = TransactionFilter(),
    val sort: TransactionSort = TransactionSort.DATE_DESC,
    /** The single currency in scope, or null when amount filter and amount sort are unavailable. */
    val scopeCurrency: Currency? = null,
    val summary: List<CurrencySummary> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val tags: List<Tag> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class TransactionsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val transactionRepository: TransactionRepository,
    accountRepository: AccountRepository,
    categoryRepository: CategoryRepository,
    tagRepository: TagRepository,
) : ViewModel() {

    private val initialFilter = FilterScope.preset(
        accountId = savedStateHandle.get<Long>("accountId")?.takeIf { it > 0 },
        tagId = savedStateHandle.get<Long>("tagId")?.takeIf { it > 0 },
        categoryId = savedStateHandle.get<Long>("categoryId")?.takeIf { it > 0 },
        types = if (savedStateHandle.get<String>("types") == OperationType.TRANSFER.name) {
            setOf(OperationType.TRANSFER)
        } else {
            emptySet()
        },
    )

    private val _filter = MutableStateFlow(initialFilter)
    private val _sort = MutableStateFlow(TransactionSort.DATE_DESC)
    private val _searchText = MutableStateFlow("")
    private val _amountRangeDropped = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emits when an amount range was removed because more than one currency is now in scope. */
    val amountRangeDropped: SharedFlow<Unit> = _amountRangeDropped

    private val accounts: Flow<List<Account>> = accountRepository.observeAll()
        .map { list -> list.map { it.account } }
        .distinctUntilChanged()
    private val categories: Flow<List<Category>> = categoryRepository.observeAll()
    private val tags: Flow<List<Tag>> = tagRepository.observeAllWithUsage()
        .map { list -> list.map { it.tag } }
        .distinctUntilChanged()

    private val normalized: Flow<NormalizedFilter> = combine(_filter, _sort, accounts, categories, tags) { f, s, a, c, t ->
        FilterScope.normalize(f, s, a, c.map { it.id }.toSet(), t.map { it.id }.toSet())
    }.distinctUntilChanged()

    private val summary: Flow<List<CurrencySummary>> = normalized
        .map { it.filter }
        .distinctUntilChanged()
        .flatMapLatest { filter ->
            if (filter.isActive) transactionRepository.observeSummary(filter) else flowOf(emptyList())
        }

    val state: StateFlow<TransactionsUiState> = combine(normalized, summary, accounts, categories, tags) { n, s, a, c, t ->
        TransactionsUiState(
            filter = n.filter,
            sort = n.sort,
            scopeCurrency = n.scopeCurrency,
            summary = s,
            accounts = a,
            categories = c,
            tags = t,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TransactionsUiState(filter = initialFilter))

    val pagedTransactions: Flow<PagingData<TransactionListRow>> = normalized
        .map { it.filter to it.sort }
        .distinctUntilChanged()
        .flatMapLatest { (filter, sort) ->
            transactionRepository.observePage(filter, sort).map { withDayHeaders(it, sort) }
        }
        .cachedIn(viewModelScope)

    init {
        viewModelScope.launch {
            _searchText.debounce(300).distinctUntilChanged().collect { text ->
                _filter.value = _filter.value.copy(text = text)
            }
        }
        // Write the repaired filter back, so the sheet and the chips start from what is shown.
        viewModelScope.launch {
            normalized.collect { n ->
                if (n.filter != _filter.value) _filter.value = n.filter
                if (n.sort != _sort.value) _sort.value = n.sort
                if (n.amountRangeDropped) _amountRangeDropped.tryEmit(Unit)
            }
        }
    }

    fun onSearchText(text: String) {
        _searchText.value = text
    }

    fun onFilterChange(filter: TransactionFilter) {
        _filter.value = filter
    }

    fun onSortChange(sort: TransactionSort) {
        _sort.value = sort
    }

    fun clearFilters() {
        _searchText.value = ""
        _filter.value = TransactionFilter()
    }

    /** Day headers only make sense when the list is ordered by date. */
    private fun withDayHeaders(data: PagingData<TransactionItem>, sort: TransactionSort): PagingData<TransactionListRow> {
        val rows = data.map<TransactionItem, TransactionListRow> { TransactionListRow.Entry(it) }
        if (sort != TransactionSort.DATE_DESC && sort != TransactionSort.DATE_ASC) return rows
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        return rows.insertSeparators { before, after ->
            val next = (after as? TransactionListRow.Entry)?.item ?: return@insertSeparators null
            val nextGroup = DayGrouping.group(next.occurredAt, now, zone)
            val previous = (before as? TransactionListRow.Entry)?.item
            if (previous == null || DayGrouping.group(previous.occurredAt, now, zone) != nextGroup) {
                TransactionListRow.Header(nextGroup)
            } else {
                null
            }
        }
    }
}
