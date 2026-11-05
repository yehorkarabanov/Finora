package com.yehor.finora.ui.operations

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.yehor.finora.R
import com.yehor.finora.domain.model.OperationType
import com.yehor.finora.domain.operation.DayGroup
import com.yehor.finora.domain.operation.TransactionFilter
import com.yehor.finora.domain.operation.TransactionItem
import com.yehor.finora.domain.operation.TransactionSort
import com.yehor.finora.ui.common.TransactionRow
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TransactionsScreen(
    onAdd: () -> Unit,
    onAddTransfer: () -> Unit,
    onOpenTransaction: (TransactionItem) -> Unit,
    viewModel: TransactionsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pagedItems = viewModel.pagedTransactions.collectAsLazyPagingItems()
    var sortMenuOpen by remember { mutableStateOf(false) }
    var filterSheetOpen by rememberSaveable { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var searchInput by rememberSaveable { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    val amountDroppedMessage = stringResource(R.string.filter_amount_dropped)

    LaunchedEffect(Unit) {
        viewModel.amountRangeDropped.collect { snackbarHostState.showSnackbar(amountDroppedMessage) }
    }
    LaunchedEffect(state.filter.text) {
        if (state.filter.text.isEmpty()) searchInput = ""
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    if (searchOpen) {
                        OutlinedTextField(
                            value = searchInput,
                            onValueChange = {
                                searchInput = it
                                viewModel.onSearchText(it)
                            },
                            placeholder = { Text(stringResource(R.string.action_search)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Text(stringResource(R.string.transactions_title))
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            searchOpen = !searchOpen
                            if (!searchOpen) {
                                searchInput = ""
                                viewModel.onSearchText("")
                            }
                        },
                    ) {
                        Icon(
                            if (searchOpen) Icons.Default.Clear else Icons.Default.Search,
                            contentDescription = stringResource(R.string.action_search),
                        )
                    }
                    IconButton(onClick = { filterSheetOpen = true }) {
                        Icon(Icons.Default.FilterList, contentDescription = stringResource(R.string.filter_title))
                    }
                    Box {
                        IconButton(onClick = { sortMenuOpen = true }) {
                            Icon(Icons.Default.Sort, contentDescription = stringResource(R.string.sort_title))
                        }
                        DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                            TransactionSort.entries.forEach { sort ->
                                val isAmountSort = sort == TransactionSort.AMOUNT_DESC || sort == TransactionSort.AMOUNT_ASC
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            stringResource(
                                                when (sort) {
                                                    TransactionSort.DATE_DESC -> R.string.sort_date_desc
                                                    TransactionSort.DATE_ASC -> R.string.sort_date_asc
                                                    TransactionSort.AMOUNT_DESC -> R.string.sort_amount_desc
                                                    TransactionSort.AMOUNT_ASC -> R.string.sort_amount_asc
                                                },
                                            ),
                                            fontWeight = if (sort == state.sort) FontWeight.Bold else null,
                                        )
                                    },
                                    enabled = !isAmountSort || state.scopeCurrency != null,
                                    onClick = {
                                        viewModel.onSortChange(sort)
                                        sortMenuOpen = false
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SmallFloatingActionButton(onClick = onAddTransfer) {
                    Icon(Icons.Default.SwapHoriz, contentDescription = stringResource(R.string.transfers_add))
                }
                FloatingActionButton(onClick = onAdd) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.transactions_add))
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ActiveFilterChips(
                state = state,
                onFilterChange = viewModel::onFilterChange,
                onClearAll = viewModel::clearFilters,
                onClearSearch = {
                    searchInput = ""
                    viewModel.onSearchText("")
                },
            )

            if (state.filter.isActive && state.summary.isNotEmpty()) {
                SummaryBar(state)
            }

            val isEmpty = pagedItems.itemCount == 0
            val isLoading = pagedItems.loadState.refresh is LoadState.Loading
            when {
                isEmpty && isLoading -> Box(Modifier.fillMaxSize())
                isEmpty -> EmptyState(
                    filtered = state.filter.isActive,
                    onClearFilters = viewModel::clearFilters,
                    onAdd = onAdd,
                )
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    for (index in 0 until pagedItems.itemCount) {
                        val row = pagedItems.peek(index)
                        if (row is TransactionListRow.Header) {
                            stickyHeader(key = row.key) { DayHeader(row.group) }
                        } else {
                            item(key = row?.key ?: "placeholder_$index") {
                                val entry = pagedItems[index] as? TransactionListRow.Entry
                                if (entry != null) {
                                    TransactionRow(item = entry.item, onClick = { onOpenTransaction(entry.item) })
                                }
                            }
                        }
                    }
                }
            }
        }

        if (filterSheetOpen) {
            TransactionFilterSheet(
                filter = state.filter,
                accounts = state.accounts,
                categories = state.categories,
                tags = state.tags,
                onApply = viewModel::onFilterChange,
                onDismiss = { filterSheetOpen = false },
            )
        }
    }
}

@Composable
private fun DayHeader(group: DayGroup) {
    val text = when (group) {
        DayGroup.Today -> stringResource(R.string.transactions_day_today)
        DayGroup.Yesterday -> stringResource(R.string.transactions_day_yesterday)
        is DayGroup.Date -> group.localDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    }
    Box(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun SummaryBar(state: TransactionsUiState) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            state.summary.forEach { summary ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        stringResource(R.string.transactions_summary_count, summary.operationCount),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "${stringResource(R.string.transactions_summary_income, summary.income.format())}  " +
                            stringResource(R.string.transactions_summary_expense, summary.expense.format()),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState(filtered: Boolean, onClearFilters: () -> Unit, onAdd: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (filtered) {
                Text(stringResource(R.string.transactions_empty_filter_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.transactions_empty_filter_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onClearFilters) { Text(stringResource(R.string.transactions_clear_filters)) }
            } else {
                Text(stringResource(R.string.transactions_empty_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.transactions_empty_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onAdd) { Text(stringResource(R.string.transactions_add)) }
            }
        }
    }
}

/** One removable chip per active filter value, plus "Clear all". */
@Composable
private fun ActiveFilterChips(
    state: TransactionsUiState,
    onFilterChange: (TransactionFilter) -> Unit,
    onClearAll: () -> Unit,
    onClearSearch: () -> Unit,
) {
    val filter = state.filter
    if (!filter.isActive) return
    val dateFormat = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }

    @Composable
    fun Chip(label: String, onRemove: () -> Unit) {
        InputChip(
            selected = true,
            onClick = onRemove,
            label = { Text(label) },
            trailingIcon = {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_remove))
            },
        )
    }

    LazyRow(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        filter.types.forEach { type ->
            item(key = "type_$type") {
                Chip(
                    stringResource(
                        when (type) {
                            OperationType.INCOME -> R.string.transaction_kind_income
                            OperationType.TRANSFER -> R.string.transaction_kind_transfer
                            else -> R.string.transaction_kind_expense
                        },
                    ),
                ) { onFilterChange(filter.copy(types = filter.types - type)) }
            }
        }
        filter.accountIds.forEach { id ->
            item(key = "account_$id") {
                Chip(state.accounts.find { it.id == id }?.name.orEmpty()) {
                    onFilterChange(filter.copy(accountIds = filter.accountIds - id))
                }
            }
        }
        filter.categoryIds.forEach { id ->
            item(key = "category_$id") {
                Chip(state.categories.find { it.id == id }?.name.orEmpty()) {
                    onFilterChange(filter.copy(categoryIds = filter.categoryIds - id))
                }
            }
        }
        if (filter.includeUncategorized) {
            item(key = "uncategorized") {
                Chip(stringResource(R.string.picker_uncategorized)) {
                    onFilterChange(filter.copy(includeUncategorized = false))
                }
            }
        }
        filter.tagIds.forEach { id ->
            item(key = "tag_$id") {
                Chip("#" + state.tags.find { it.id == id }?.name.orEmpty()) {
                    onFilterChange(filter.copy(tagIds = filter.tagIds - id))
                }
            }
        }
        if (filter.from != null || filter.toExclusive != null) {
            item(key = "date") {
                val from = filter.from?.let { dateOf(it).format(dateFormat) }
                val to = filter.toExclusive?.let { dateOf(it - 1).format(dateFormat) }
                val label = when {
                    from != null && to != null -> stringResource(R.string.filter_chip_date_range, from, to)
                    from != null -> stringResource(R.string.filter_chip_date_from, from)
                    else -> stringResource(R.string.filter_chip_date_to, to.orEmpty())
                }
                Chip(label) { onFilterChange(filter.copy(from = null, toExclusive = null)) }
            }
        }
        filter.amount?.let { range ->
            item(key = "amount") {
                val currency = range.currency
                val min = range.minMinor?.let { com.yehor.finora.domain.money.Money(it, currency).format() }
                val max = range.maxMinor?.let { com.yehor.finora.domain.money.Money(it, currency).format() }
                val label = when {
                    min != null && max != null -> stringResource(R.string.filter_chip_amount, min, max)
                    min != null -> stringResource(R.string.filter_chip_amount_min, min)
                    else -> stringResource(R.string.filter_chip_amount_max, max.orEmpty())
                }
                Chip(label) { onFilterChange(filter.copy(amount = null)) }
            }
        }
        if (filter.text.isNotBlank()) {
            item(key = "text") {
                Chip(stringResource(R.string.filter_chip_search, filter.text), onClearSearch)
            }
        }
        item(key = "clear_all") {
            TextButton(onClick = onClearAll) { Text(stringResource(R.string.filter_clear_all)) }
        }
    }
}
