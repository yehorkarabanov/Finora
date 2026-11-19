package com.yehor.finora.ui.operations

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.yehor.finora.R
import com.yehor.finora.designsystem.component.button.RoundIconButton
import com.yehor.finora.designsystem.component.navigation.SpeedDialItem
import com.yehor.finora.designsystem.icon.AppIcons
import com.yehor.finora.designsystem.theme.PreviewTheme
import com.yehor.finora.domain.account.Account
import com.yehor.finora.domain.account.AccountType
import com.yehor.finora.domain.category.Category
import com.yehor.finora.domain.model.AccountStatus
import com.yehor.finora.domain.model.CategoryType
import com.yehor.finora.domain.model.OperationType
import com.yehor.finora.domain.money.CurrencyRegistry
import com.yehor.finora.domain.money.Money
import com.yehor.finora.domain.operation.CurrencySummary
import com.yehor.finora.domain.operation.DayGroup
import com.yehor.finora.domain.operation.TransactionEntryView
import com.yehor.finora.domain.operation.TransactionFilter
import com.yehor.finora.domain.operation.TransactionItem
import com.yehor.finora.domain.tag.Tag
import com.yehor.finora.ui.navigation.BindShellFab
import com.yehor.finora.ui.navigation.ShellFabClearance
import java.time.LocalDate

@Composable
fun TransactionsScreen(
    onAddExpense: () -> Unit,
    onAddIncome: () -> Unit,
    onAddTransfer: () -> Unit,
    onOpenTransaction: (TransactionItem) -> Unit,
    onExportCsv: (TransactionFilter) -> Unit = {},
    viewModel: TransactionsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pagedItems = viewModel.pagedTransactions.collectAsLazyPagingItems()
    var sortSheetOpen by rememberSaveable { mutableStateOf(false) }
    var categorySheetOpen by rememberSaveable { mutableStateOf(false) }
    var tagSheetOpen by rememberSaveable { mutableStateOf(false) }
    var sectionSheet by rememberSaveable { mutableStateOf<FilterSection?>(null) }
    var dateSheetOpen by rememberSaveable { mutableStateOf(false) }
    var searchInput by rememberSaveable { mutableStateOf(state.filter.text) }
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val amountDroppedMessage = stringResource(R.string.filter_amount_dropped)

    LaunchedEffect(Unit) {
        viewModel.amountRangeDropped.collect { snackbarHostState.showSnackbar(amountDroppedMessage) }
    }
    LaunchedEffect(state.filter.text) {
        if (state.filter.text.isEmpty()) searchInput = ""
    }

    // The shell draws the floating add button; this screen only says what it opens.
    val latestAddExpense by rememberUpdatedState(onAddExpense)
    val latestAddIncome by rememberUpdatedState(onAddIncome)
    val latestAddTransfer by rememberUpdatedState(onAddTransfer)
    // A transfer needs two different active accounts.
    val canTransfer = state.accounts.count { it.status == AccountStatus.ACTIVE } >= 2
    val expenseLabel = stringResource(R.string.transaction_kind_expense)
    val incomeLabel = stringResource(R.string.transaction_kind_income)
    val transferLabel = stringResource(R.string.transaction_kind_transfer)
    val speedDial = remember(canTransfer, expenseLabel, incomeLabel, transferLabel) {
        // Listed top to bottom; the last item sits nearest the button.
        buildList {
            if (canTransfer) add(SpeedDialItem(transferLabel, AppIcons.Transfer, { latestAddTransfer() }, primary = false))
            add(SpeedDialItem(incomeLabel, AppIcons.Add, { latestAddIncome() }, primary = false))
            add(SpeedDialItem(expenseLabel, AppIcons.Remove, { latestAddExpense() }, primary = true))
        }
    }
    BindShellFab(listState, speedDial, stringResource(R.string.transactions_add)) { latestAddExpense() }

    val source = RowSource(
        count = pagedItems.itemCount,
        peek = { pagedItems.peek(it) },
        get = { pagedItems[it] },
        endReached = pagedItems.loadState.append.endOfPaginationReached,
    )

    Box(Modifier.fillMaxSize()) {
        TransactionsContent(
            state = state,
            source = source,
            isLoading = pagedItems.loadState.refresh is LoadState.Loading,
            searchInput = searchInput,
            listState = listState,
            onSearchInput = {
                searchInput = it
                viewModel.onSearchText(it)
            },
            onFilterChange = viewModel::onFilterChange,
            onClearFilters = viewModel::clearFilters,
            onOpenSort = { sortSheetOpen = true },
            onOpenDate = { dateSheetOpen = true },
            onOpenCategories = { categorySheetOpen = true },
            onOpenTags = { tagSheetOpen = true },
            onOpenSection = { sectionSheet = it },
            onAdd = onAddExpense,
            onOpenTransaction = onOpenTransaction,
            onExportCsv = { onExportCsv(state.filter) },
        )
        // Above the shell FAB (drawn over this screen), so the message never hides under it.
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).padding(bottom = ShellFabClearance))
    }

    if (categorySheetOpen) {
        CategoryFilterSheet(
            filter = state.filter,
            categories = state.categories,
            onFilterChange = viewModel::onFilterChange,
            onDismiss = { categorySheetOpen = false },
        )
    }
    if (tagSheetOpen) {
        TagFilterSheet(
            filter = state.filter,
            tags = state.tags,
            usage = state.tagUsage,
            onFilterChange = viewModel::onFilterChange,
            onDismiss = { tagSheetOpen = false },
        )
    }
    if (sortSheetOpen) {
        SortSheet(
            sort = state.sort,
            amountAvailable = state.scopeCurrency != null,
            onSelect = viewModel::onSortChange,
            onDismiss = { sortSheetOpen = false },
        )
    }
    sectionSheet?.let { section ->
        TransactionFilterSheet(
            filter = state.filter,
            accounts = state.accounts,
            categories = state.categories,
            tags = state.tags,
            onApply = viewModel::onFilterChange,
            onDismiss = { sectionSheet = null },
            sections = setOf(section),
        )
    }
    if (dateSheetOpen) {
        DateFilterSheet(
            filter = state.filter,
            onApply = { from, to -> viewModel.onFilterChange(state.filter.copy(from = from, toExclusive = to)) },
            onDismiss = { dateSheetOpen = false },
        )
    }
}

/** The screen without ViewModel, sheets or shell: title row, search, filter pills, then the list or an empty state. */
@Composable
internal fun TransactionsContent(
    state: TransactionsUiState,
    source: RowSource,
    isLoading: Boolean,
    searchInput: String,
    listState: LazyListState,
    onSearchInput: (String) -> Unit,
    onFilterChange: (TransactionFilter) -> Unit,
    onClearFilters: () -> Unit,
    onOpenSort: () -> Unit,
    onOpenDate: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenTags: () -> Unit,
    onOpenSection: (FilterSection) -> Unit,
    onAdd: () -> Unit,
    onOpenTransaction: (TransactionItem) -> Unit,
    onExportCsv: () -> Unit,
) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TitleRow(onOpenSort = onOpenSort, onExportCsv = onExportCsv)
        SearchField(
            value = searchInput,
            onValueChange = onSearchInput,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
        )
        FilterBar(
            state = state,
            onFilterChange = onFilterChange,
            onOpenDate = onOpenDate,
            onOpenCategories = onOpenCategories,
            onOpenTags = onOpenTags,
            onOpenSection = onOpenSection,
            onClearAll = onClearFilters,
        )
        val isEmpty = source.count == 0
        when {
            isEmpty && isLoading -> Box(Modifier.fillMaxSize())
            isEmpty -> EmptyState(filtered = state.filter.isActive, onClearFilters = onClearFilters, onAdd = onAdd)
            else -> TransactionsList(state, source, listState, onOpenTransaction)
        }
    }
}

@Composable
private fun TitleRow(onOpenSort: () -> Unit, onExportCsv: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier.padding(start = 18.dp, end = 12.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.transactions_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        RoundIconButton(AppIcons.Sort, stringResource(R.string.sort_title), onOpenSort)
        Box {
            RoundIconButton(AppIcons.More, stringResource(R.string.action_more_options), { menuOpen = true })
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.transactions_export_csv)) },
                    onClick = {
                        menuOpen = false
                        onExportCsv()
                    },
                )
            }
        }
    }
}

// ---- Previews ----

private const val PREVIEW_DAY_MS = 86_400_000L

private fun previewData(): Pair<TransactionsUiState, List<TransactionListRow>> {
    val usd = CurrencyRegistry.get("USD")
    val uah = CurrencyRegistry.get("UAH")
    fun account(id: Long, name: String, currency: com.yehor.finora.domain.money.Currency) =
        Account(id, name, currency, AccountType.BANK, null, null, null, AccountStatus.ACTIVE, 0)
    fun category(id: Long, name: String, icon: String, color: Long, type: CategoryType = CategoryType.EXPENSE) =
        Category(id, name, icon, color, type, null, 0)
    val mono = account(1, "Monobank", usd)
    val cash = account(2, "Cash", usd)
    val uahCard = account(3, "Privat", uah)
    val groceries = category(1, "Groceries", "shopping", 0xFF00E599)
    val cafes = category(2, "Cafes", "food", 0xFFC4A0FF)
    val salary = category(3, "Salary", "income", 0xFF4DA3FF, CategoryType.INCOME)
    val trip = Tag(1, "trip", null, 0)
    val now = System.currentTimeMillis()
    fun tx(
        id: Long,
        type: OperationType,
        daysAgo: Long,
        description: String?,
        category: Category?,
        vararg entries: TransactionEntryView,
        tags: List<Tag> = emptyList(),
    ) = TransactionItem(
        id, type, now - daysAgo * PREVIEW_DAY_MS, description, category, listOfNotNull(category), tags,
        entries.toList(), 0, 0,
    )
    fun usd(minor: Long) = Money(minor, usd)

    val rows = listOf(
        TransactionListRow.Header(DayGroup.Today),
        TransactionListRow.Entry(tx(1, OperationType.EXPENSE, 0, "Silpo", groceries, TransactionEntryView(mono, usd(-6420)), tags = listOf(trip))),
        TransactionListRow.Entry(tx(2, OperationType.EXPENSE, 0, "Coffee", cafes, TransactionEntryView(cash, usd(-1000)))),
        TransactionListRow.Header(DayGroup.Yesterday),
        TransactionListRow.Entry(tx(3, OperationType.INCOME, 1, "Salary", salary, TransactionEntryView(mono, usd(260000)))),
        TransactionListRow.Entry(
            tx(
                4, OperationType.TRANSFER, 1, null, null,
                TransactionEntryView(uahCard, Money(-400000, uah)), TransactionEntryView(cash, usd(9650)),
            ),
        ),
        TransactionListRow.Header(DayGroup.Date(LocalDate.now().minusDays(5))),
        TransactionListRow.Entry(tx(5, OperationType.EXPENSE, 5, null, null, TransactionEntryView(cash, usd(-2500)))),
    )
    val state = TransactionsUiState(
        accounts = listOf(mono, cash, uahCard),
        categories = listOf(groceries, cafes, salary),
        tags = listOf(trip),
    )
    return state to rows
}

@Composable
private fun TransactionsPreviewContent(dark: Boolean, mode: PreviewMode) {
    val (base, allRows) = remember { previewData() }
    val usd = CurrencyRegistry.get("USD")
    val state = when (mode) {
        PreviewMode.LIST -> base
        PreviewMode.EMPTY -> base
        PreviewMode.FILTERED_EMPTY -> base.copy(filter = TransactionFilter(types = setOf(OperationType.INCOME), text = "rent"))
    }
    val rows = if (mode == PreviewMode.LIST) allRows else emptyList()
    val withSummary = if (mode == PreviewMode.LIST) {
        state.copy(
            filter = TransactionFilter(types = setOf(OperationType.EXPENSE)),
            summary = listOf(CurrencySummary(usd, Money(266000, usd), Money(142000, usd), 5)),
        )
    } else {
        state
    }
    PreviewTheme(dark) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            TransactionsContent(
                state = withSummary,
                source = RowSource(rows.size, { rows.getOrNull(it) }, { rows.getOrNull(it) }, endReached = true),
                isLoading = false,
                searchInput = withSummary.filter.text,
                listState = rememberLazyListState(),
                onSearchInput = {},
                onFilterChange = {},
                onClearFilters = {},
                onOpenSort = {},
                onOpenDate = {},
                onOpenCategories = {},
                onOpenTags = {},
                onOpenSection = {},
                onAdd = {},
                onOpenTransaction = {},
                onExportCsv = {},
            )
        }
    }
}

private enum class PreviewMode { LIST, EMPTY, FILTERED_EMPTY }

@Preview(showBackground = true, heightDp = 780)
@Composable
private fun TransactionsListLightPreview() = TransactionsPreviewContent(dark = false, PreviewMode.LIST)

@Preview(showBackground = true, backgroundColor = 0xFF121314, heightDp = 780)
@Composable
private fun TransactionsListDarkPreview() = TransactionsPreviewContent(dark = true, PreviewMode.LIST)

@Preview(showBackground = true, heightDp = 560)
@Composable
private fun TransactionsEmptyLightPreview() = TransactionsPreviewContent(dark = false, PreviewMode.EMPTY)

@Preview(showBackground = true, backgroundColor = 0xFF121314, heightDp = 560)
@Composable
private fun TransactionsEmptyDarkPreview() = TransactionsPreviewContent(dark = true, PreviewMode.EMPTY)

@Preview(showBackground = true, heightDp = 560)
@Composable
private fun TransactionsFilteredEmptyLightPreview() = TransactionsPreviewContent(dark = false, PreviewMode.FILTERED_EMPTY)

@Preview(showBackground = true, backgroundColor = 0xFF121314, heightDp = 560)
@Composable
private fun TransactionsFilteredEmptyDarkPreview() = TransactionsPreviewContent(dark = true, PreviewMode.FILTERED_EMPTY)
