package com.yehor.finora.ui.operations

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material3.Button
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.yehor.finora.R
import com.yehor.finora.domain.model.OperationType
import com.yehor.finora.domain.money.Money
import com.yehor.finora.domain.operation.CurrencySummary
import com.yehor.finora.domain.operation.DayGroup
import com.yehor.finora.domain.operation.TransactionFilter
import com.yehor.finora.domain.operation.TransactionItem
import com.yehor.finora.domain.operation.TransactionSort
import com.yehor.finora.domain.tag.TagMatchMode
import com.yehor.finora.ui.accounts.NotIncludedLine
import com.yehor.finora.ui.accounts.formatDate
import com.yehor.finora.ui.analytics.AccountSelector
import com.yehor.finora.ui.common.TransactionRow
import com.yehor.finora.ui.theme.finance
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

// A day of transactions is one connected group: only its outer corners are rounded (design system §5.3).
private val GroupRadius = 20.dp
private val GroupInnerRadius = 4.dp
private val GroupSingleShape = RoundedCornerShape(GroupRadius)
private val GroupTopShape = RoundedCornerShape(
    topStart = GroupRadius, topEnd = GroupRadius,
    bottomStart = GroupInnerRadius, bottomEnd = GroupInnerRadius,
)
private val GroupMiddleShape = RoundedCornerShape(GroupInnerRadius)
private val GroupBottomShape = RoundedCornerShape(
    topStart = GroupInnerRadius, topEnd = GroupInnerRadius,
    bottomStart = GroupRadius, bottomEnd = GroupRadius,
)
private val SectionShape = RoundedCornerShape(24.dp)
private const val MINUS = "−"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TransactionsScreen(
    onAdd: () -> Unit,
    onOpenTransaction: (TransactionItem) -> Unit,
    onExportCsv: (TransactionFilter) -> Unit = {},
    viewModel: TransactionsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pagedItems = viewModel.pagedTransactions.collectAsLazyPagingItems()
    var overflowMenuOpen by remember { mutableStateOf(false) }
    var sortSheetOpen by rememberSaveable { mutableStateOf(false) }
    var categorySheetOpen by rememberSaveable { mutableStateOf(false) }
    var tagSheetOpen by rememberSaveable { mutableStateOf(false) }
    var sectionSheet by rememberSaveable { mutableStateOf<FilterSection?>(null) }
    var dateSheetOpen by rememberSaveable { mutableStateOf(false) }
    var searchInput by rememberSaveable { mutableStateOf(state.filter.text) }
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
                title = { Text(stringResource(R.string.transactions_title), fontWeight = FontWeight.SemiBold) },
                actions = {
                    // Filters and sort live in the pill row under the search field; only rare actions stay here.
                    Box {
                        IconButton(onClick = { overflowMenuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more_options))
                        }
                        DropdownMenu(expanded = overflowMenuOpen, onDismissRequest = { overflowMenuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.transactions_export_csv)) },
                                onClick = {
                                    overflowMenuOpen = false
                                    onExportCsv(state.filter)
                                },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.transactions_add))
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SearchField(
                value = searchInput,
                onValueChange = {
                    searchInput = it
                    viewModel.onSearchText(it)
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            FilterBar(
                state = state,
                onFilterChange = viewModel::onFilterChange,
                onOpenDate = { dateSheetOpen = true },
                onOpenCategories = { categorySheetOpen = true },
                onOpenTags = { tagSheetOpen = true },
                onOpenSection = { sectionSheet = it },
                onOpenSort = { sortSheetOpen = true },
                onClearAll = viewModel::clearFilters,
            )

            val isEmpty = pagedItems.itemCount == 0
            val isLoading = pagedItems.loadState.refresh is LoadState.Loading
            when {
                isEmpty && isLoading -> Box(Modifier.fillMaxSize())
                isEmpty -> EmptyState(
                    filtered = state.filter.isActive,
                    onClearFilters = viewModel::clearFilters,
                    onAdd = onAdd,
                )
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    // Bottom padding keeps the last row clear of the FAB.
                    contentPadding = PaddingValues(top = 4.dp, bottom = 88.dp),
                ) {
                    if (state.filter.isActive && state.summary.isNotEmpty()) {
                        item(key = "summary") {
                            SummaryCard(state, Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                        }
                    }
                    val count = pagedItems.itemCount
                    for (index in 0 until count) {
                        val row = pagedItems.peek(index)
                        if (row is TransactionListRow.Header) {
                            stickyHeader(key = row.key) { DayHeader(row.group) }
                        } else {
                            val first = index == 0 || pagedItems.peek(index - 1) is TransactionListRow.Header
                            val last = index == count - 1 || pagedItems.peek(index + 1) is TransactionListRow.Header
                            val shape = when {
                                first && last -> GroupSingleShape
                                first -> GroupTopShape
                                last -> GroupBottomShape
                                else -> GroupMiddleShape
                            }
                            item(key = row?.key ?: "placeholder_$index") {
                                val entry = pagedItems[index] as? TransactionListRow.Entry
                                Surface(
                                    shape = shape,
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 16.dp, end = 16.dp, top = if (first) 0.dp else 2.dp),
                                ) {
                                    if (entry != null) {
                                        TransactionRow(
                                            item = entry.item,
                                            onClick = { onOpenTransaction(entry.item) },
                                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                        )
                                    } else {
                                        Box(Modifier.fillMaxWidth().height(72.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
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
}

private fun TransactionSort.labelRes(): Int = when (this) {
    TransactionSort.DATE_DESC -> R.string.sort_date_desc
    TransactionSort.DATE_ASC -> R.string.sort_date_asc
    TransactionSort.AMOUNT_DESC -> R.string.sort_amount_desc
    TransactionSort.AMOUNT_ASC -> R.string.sort_amount_asc
}

private fun TransactionSort.isAmount(): Boolean =
    this == TransactionSort.AMOUNT_DESC || this == TransactionSort.AMOUNT_ASC

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortSheet(
    sort: TransactionSort,
    amountAvailable: Boolean,
    onSelect: (TransactionSort) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 8.dp)) {
            Text(
                stringResource(R.string.sort_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp).semantics { heading() },
            )
            TransactionSort.entries.forEach { option ->
                val enabled = amountAvailable || !option.isAmount()
                ListItem(
                    modifier = Modifier
                        .clickable(enabled = enabled, role = Role.RadioButton) {
                            onSelect(option)
                            onDismiss()
                        }
                        .alpha(if (enabled) 1f else 0.38f),
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    leadingContent = { RadioButton(selected = option == sort, onClick = null, enabled = enabled) },
                    headlineContent = { Text(stringResource(option.labelRes())) },
                )
            }
            if (!amountAvailable) {
                Text(
                    stringResource(R.string.filter_amount_disabled_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
        }
    }
}

// ---- Search ----

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val focusManager = LocalFocusManager.current
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(stringResource(R.string.transactions_search_hint)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = if (value.isNotEmpty()) {
            {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_remove))
                }
            }
        } else {
            null
        },
        singleLine = true,
        shape = CircleShape,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}

// ---- Filter bar ----

/**
 * One scrollable row: type pills (single tap, no sheet), then one pill per filter dimension.
 * An active pill shows its value and uses `secondaryContainer`; each opens a sheet for just that dimension.
 */
@Composable
private fun FilterBar(
    state: TransactionsUiState,
    onFilterChange: (TransactionFilter) -> Unit,
    onOpenDate: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenTags: () -> Unit,
    onOpenSection: (FilterSection) -> Unit,
    onOpenSort: () -> Unit,
    onClearAll: () -> Unit,
) {
    val filter = state.filter
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item(key = "type_all") {
            TypePill(stringResource(R.string.filter_type_all), selected = filter.types.isEmpty()) {
                onFilterChange(filter.copy(types = emptySet()))
            }
        }
        listOf(
            OperationType.EXPENSE to R.string.filter_type_expenses,
            OperationType.INCOME to R.string.filter_type_income,
            OperationType.TRANSFER to R.string.filter_type_transfers,
        ).forEach { (type, label) ->
            item(key = "type_$type") {
                val selected = type in filter.types
                TypePill(stringResource(label), selected = selected) {
                    val next = if (selected) filter.types - type else filter.types + type
                    // All three selected is the same as no type filter.
                    onFilterChange(filter.copy(types = if (next.size == 3) emptySet() else next))
                }
            }
        }
        item(key = "divider") {
            Box(
                Modifier
                    .padding(horizontal = 4.dp)
                    .width(1.dp)
                    .height(20.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant),
            )
        }
        item(key = "date") {
            FilterPill(
                label = dateLabel(filter) ?: stringResource(R.string.filter_date_any),
                active = filter.from != null || filter.toExclusive != null,
                icon = Icons.Outlined.CalendarMonth,
                onClick = onOpenDate,
            )
        }
        if (state.accounts.size > 1) {
            item(key = "accounts") {
                AccountSelector(
                    accounts = state.accounts,
                    selectedAccountIds = filter.accountIds,
                    onToggleAccount = { id ->
                        val ids = filter.accountIds
                        onFilterChange(filter.copy(accountIds = if (id in ids) ids - id else ids + id))
                    },
                    onClearSelection = { onFilterChange(filter.copy(accountIds = emptySet())) },
                )
            }
        }
        item(key = "categories") {
            val names = buildList {
                filter.categoryIds.forEach { id -> state.categories.find { it.id == id }?.let { add(it.name) } }
                if (filter.includeUncategorized) add(stringResource(R.string.picker_uncategorized))
            }
            FilterPill(
                label = summarize(names) ?: stringResource(R.string.filter_categories),
                active = names.isNotEmpty(),
                icon = Icons.Outlined.Category,
                onClick = onOpenCategories,
            )
        }
        if (state.tags.isNotEmpty() || filter.tagIds.isNotEmpty()) {
            item(key = "tags") {
                val names = filter.tagIds.mapNotNull { id -> state.tags.find { it.id == id }?.let { "#${it.name}" } }
                val summary = summarize(names)
                FilterPill(
                    label = when {
                        summary == null -> stringResource(R.string.filter_tags)
                        // Make "must have every tag" visible without opening the sheet.
                        names.size > 1 && filter.tagMode == TagMatchMode.ALL -> stringResource(R.string.filter_tags_pill_all, summary)
                        else -> summary
                    },
                    active = names.isNotEmpty(),
                    icon = Icons.Outlined.Sell,
                    onClick = onOpenTags,
                )
            }
        }
        item(key = "amount") {
            FilterPill(
                label = amountLabel(filter) ?: stringResource(R.string.filter_amount_short),
                active = filter.amount != null,
                onClick = { onOpenSection(FilterSection.AMOUNT) },
            )
        }
        item(key = "sort") {
            // Not a filter, so it never looks "active"; it shows the order because amount sort drops day headers.
            FilterPill(
                label = stringResource(state.sort.labelRes()),
                active = false,
                icon = Icons.AutoMirrored.Filled.Sort,
                onClick = onOpenSort,
            )
        }
        if (filter.isActive) {
            item(key = "clear_all") {
                TextButton(onClick = onClearAll) { Text(stringResource(R.string.filter_clear_all)) }
            }
        }
    }
}

/** "Food", "Food +2", or null when nothing is selected. */
@Composable
private fun summarize(names: List<String>): String? = when (names.size) {
    0 -> null
    1 -> names.single()
    else -> stringResource(R.string.filter_more, names.first(), names.size - 1)
}

@Composable
private fun dateLabel(filter: TransactionFilter): String? {
    if (filter.from == null && filter.toExclusive == null) return null
    val preset = DatePreset.entries.firstOrNull { it.range(LocalDate.now()) == (filter.from to filter.toExclusive) }
    if (preset != null) return stringResource(preset.labelRes)
    val format = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    val from = filter.from?.let { dateOf(it).format(format) }
    val to = filter.toExclusive?.let { dateOf(it - 1).format(format) }
    return when {
        from != null && to != null -> stringResource(R.string.filter_chip_date_range, from, to)
        from != null -> stringResource(R.string.filter_chip_date_from, from)
        else -> stringResource(R.string.filter_chip_date_to, to.orEmpty())
    }
}

@Composable
private fun amountLabel(filter: TransactionFilter): String? {
    val range = filter.amount ?: return null
    val min = range.minMinor?.let { Money(it, range.currency).format() }
    val max = range.maxMinor?.let { Money(it, range.currency).format() }
    return when {
        min != null && max != null -> stringResource(R.string.filter_chip_amount, min, max)
        min != null -> stringResource(R.string.filter_chip_amount_min, min)
        max != null -> stringResource(R.string.filter_chip_amount_max, max)
        else -> null
    }
}

@Composable
private fun TypePill(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        selected = selected,
        onClick = onClick,
        modifier = Modifier.heightIn(min = 44.dp),
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Box(Modifier.heightIn(min = 44.dp).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else null,
            )
        }
    }
}

@Composable
private fun FilterPill(label: String, active: Boolean, onClick: () -> Unit, icon: ImageVector? = null) {
    Surface(
        onClick = onClick,
        modifier = Modifier.heightIn(min = 44.dp).semantics { selected = active },
        shape = CircleShape,
        color = if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            Modifier.heightIn(min = 44.dp).padding(start = if (icon != null) 12.dp else 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = if (icon != null) 8.dp else 0.dp).widthIn(max = 160.dp),
            )
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}

// ---- Date filter ----

/** Quick date ranges. Each is `[from, toExclusive)` in local days; the end is the period end, not today. */
private enum class DatePreset(val labelRes: Int) {
    TODAY(R.string.period_today),
    THIS_WEEK(R.string.period_this_week),
    THIS_MONTH(R.string.period_this_month),
    LAST_MONTH(R.string.filter_date_last_month),
    LAST_30_DAYS(R.string.filter_date_last_30_days),
    THIS_YEAR(R.string.period_this_year),
    ;

    fun range(today: LocalDate): Pair<Long, Long> {
        val (from, to) = when (this) {
            TODAY -> today to today
            THIS_WEEK -> {
                val start = today.with(TemporalAdjusters.previousOrSame(WeekFields.of(Locale.getDefault()).firstDayOfWeek))
                start to start.plusDays(6)
            }
            THIS_MONTH -> today.withDayOfMonth(1) to today.with(TemporalAdjusters.lastDayOfMonth())
            LAST_MONTH -> {
                val start = today.minusMonths(1).withDayOfMonth(1)
                start to start.with(TemporalAdjusters.lastDayOfMonth())
            }
            LAST_30_DAYS -> today.minusDays(29) to today
            THIS_YEAR -> today.withDayOfYear(1) to today.with(TemporalAdjusters.lastDayOfYear())
        }
        return startOfDayMillis(from) to endExclusiveMillis(to)
    }
}

/** A radio list of quick ranges plus "Custom range…" (design system §5.2, §6: sheets for choosing). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateFilterSheet(
    filter: TransactionFilter,
    onApply: (from: Long?, toExclusive: Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    val today = remember { LocalDate.now() }
    val current = filter.from to filter.toExclusive
    val selectedPreset = DatePreset.entries.firstOrNull { it.range(today) == current }
    val isAny = filter.from == null && filter.toExclusive == null
    var showPicker by rememberSaveable { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    if (!showPicker) {
        ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
            Column(Modifier.navigationBarsPadding().padding(bottom = 8.dp)) {
                Text(
                    stringResource(R.string.filter_date),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp).semantics { heading() },
                )
                DateOption(stringResource(R.string.filter_date_any), selected = isAny) {
                    onApply(null, null)
                    onDismiss()
                }
                DatePreset.entries.forEach { preset ->
                    DateOption(stringResource(preset.labelRes), selected = preset == selectedPreset) {
                        val (from, to) = preset.range(today)
                        onApply(from, to)
                        onDismiss()
                    }
                }
                val customSelected = !isAny && selectedPreset == null
                DateOption(
                    stringResource(R.string.period_custom_range),
                    selected = customSelected,
                    supporting = if (customSelected) dateLabel(filter) else null,
                ) { showPicker = true }
            }
        }
    } else {
        val pickerState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = filter.from?.let { utcMillis(dateOf(it)) },
            initialSelectedEndDateMillis = filter.toExclusive?.let { utcMillis(dateOf(it - 1)) },
        )
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(
                    enabled = pickerState.selectedStartDateMillis != null,
                    onClick = {
                        val start = pickerState.selectedStartDateMillis?.let(::utcDate)
                        val end = pickerState.selectedEndDateMillis?.let(::utcDate) ?: start
                        if (start != null && end != null) onApply(startOfDayMillis(start), endExclusiveMillis(end))
                        onDismiss()
                    },
                ) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            },
        ) {
            DateRangePicker(state = pickerState, modifier = Modifier.height(480.dp))
        }
    }
}

@Composable
private fun DateOption(label: String, selected: Boolean, supporting: String? = null, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(role = Role.RadioButton, onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { RadioButton(selected = selected, onClick = null) },
        headlineContent = { Text(label) },
        supportingContent = supporting?.let { { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
    )
}

private fun utcMillis(date: LocalDate): Long = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun utcDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

// ---- List ----

@Composable
private fun DayHeader(group: DayGroup) {
    val format = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    val text = when (group) {
        DayGroup.Today -> stringResource(R.string.transactions_day_today) + " · " + LocalDate.now().format(format)
        DayGroup.Yesterday ->
            stringResource(R.string.transactions_day_yesterday) + " · " + LocalDate.now().minusDays(1).format(format)
        is DayGroup.Date -> group.localDate.format(format)
    }
    Box(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { heading() },
        )
    }
}

/** Totals of what the filter matches: income, expenses and net per currency, plus an estimate across currencies. */
@Composable
private fun SummaryCard(state: TransactionsUiState, modifier: Modifier = Modifier) {
    val count = state.summary.sumOf { it.operationCount }
    Surface(modifier = modifier.fillMaxWidth(), shape = SectionShape, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                pluralStringResource(R.plurals.analytics_operations, count, count),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            val multiCurrency = state.summary.size > 1
            state.summary.forEach { summary -> SummaryRow(summary, showCurrency = multiCurrency) }
            state.convertedSummary?.let { converted ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    val parts = listOfNotNull(
                        converted.income?.let { stringResource(R.string.valuation_converted_income, it.format()) },
                        converted.expense?.let { stringResource(R.string.valuation_converted_expense, it.format()) },
                        converted.net?.let { stringResource(R.string.valuation_converted_net, it.format()) },
                    )
                    Text(
                        if (parts.isEmpty()) stringResource(R.string.valuation_unavailable) else parts.joinToString("  "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (converted.outdated) {
                        Text(
                            stringResource(R.string.valuation_outdated, formatDate(converted.oldestFetchedAt)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    NotIncludedLine(converted.excluded)
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(summary: CurrencySummary, showCurrency: Boolean) {
    val currency = summary.currency
    val net = summary.income.amountMinor - summary.expense.amountMinor
    val netText = when {
        net > 0 -> "+" + Money(net, currency).format()
        net < 0 -> MINUS + Money(-net, currency).format()
        else -> Money(0, currency).format()
    }
    val netColor = when {
        net > 0 -> MaterialTheme.finance.income
        net < 0 -> MaterialTheme.finance.expense
        else -> MaterialTheme.colorScheme.onSurface
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (showCurrency) {
            Text(currency.code, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryFigure(
                stringResource(R.string.transactions_summary_income_label),
                "+" + summary.income.format(),
                MaterialTheme.finance.income,
                Modifier.weight(1f),
            )
            SummaryFigure(
                stringResource(R.string.transactions_summary_expense_label),
                MINUS + summary.expense.format(),
                MaterialTheme.finance.expense,
                Modifier.weight(1f),
            )
            SummaryFigure(stringResource(R.string.dashboard_net), netText, netColor, Modifier.weight(1f))
        }
    }
}

@Composable
private fun SummaryFigure(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun EmptyState(filtered: Boolean, onClearFilters: () -> Unit, onAdd: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.size(64.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (filtered) Icons.Filled.SearchOff else Icons.AutoMirrored.Outlined.ReceiptLong,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
            Text(
                stringResource(if (filtered) R.string.transactions_empty_filter_title else R.string.transactions_empty_title),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                stringResource(if (filtered) R.string.transactions_empty_filter_body else R.string.transactions_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (filtered) {
                FilledTonalButton(onClick = onClearFilters, modifier = Modifier.padding(top = 8.dp)) {
                    Text(stringResource(R.string.transactions_clear_filters))
                }
            } else {
                Button(onClick = onAdd, modifier = Modifier.padding(top = 8.dp)) {
                    Text(stringResource(R.string.transactions_add))
                }
            }
        }
    }
}
