package com.yehor.finora.ui.operations

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.yehor.finora.R
import com.yehor.finora.designsystem.component.input.FilterPill
import com.yehor.finora.designsystem.component.list.GroupedCard
import com.yehor.finora.designsystem.component.list.ListRow
import com.yehor.finora.designsystem.component.sheet.FinoraSheet
import com.yehor.finora.designsystem.icon.AppIcons
import com.yehor.finora.designsystem.theme.finance
import com.yehor.finora.designsystem.theme.hairline
import com.yehor.finora.domain.model.OperationType
import com.yehor.finora.domain.money.Money
import com.yehor.finora.domain.operation.TransactionFilter
import com.yehor.finora.domain.operation.TransactionSort
import com.yehor.finora.domain.tag.TagMatchMode
import com.yehor.finora.ui.analytics.AccountSelector
import com.yehor.finora.ui.common.DateRangePickerDialog
import com.yehor.finora.ui.common.compactRangeLabel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

// Filter row, search field, sort and date sheets of the Transactions screen.

internal fun TransactionSort.labelRes(): Int = when (this) {
    TransactionSort.DATE_DESC -> R.string.sort_date_desc
    TransactionSort.DATE_ASC -> R.string.sort_date_asc
    TransactionSort.AMOUNT_DESC -> R.string.sort_amount_desc
    TransactionSort.AMOUNT_ASC -> R.string.sort_amount_asc
}

private fun TransactionSort.isAmount(): Boolean =
    this == TransactionSort.AMOUNT_DESC || this == TransactionSort.AMOUNT_ASC

/** The filter dimensions that differ from "no filter", counting the search text. */
internal fun TransactionFilter.activeCount(): Int = listOf(
    types.isNotEmpty(),
    accountIds.isNotEmpty(),
    categoryIds.isNotEmpty() || includeUncategorized,
    tagIds.isNotEmpty(),
    from != null || toExclusive != null,
    amount != null,
    text.isNotEmpty(),
).count { it }

@Composable
internal fun SortSheet(
    sort: TransactionSort,
    amountAvailable: Boolean,
    onSelect: (TransactionSort) -> Unit,
    onDismiss: () -> Unit,
) {
    FinoraSheet(onDismiss) {
        SheetTitle(stringResource(R.string.sort_title))
        GroupedCard(Modifier.fillMaxWidth()) {
            TransactionSort.entries.forEachIndexed { index, option ->
                val enabled = amountAvailable || !option.isAmount()
                if (index > 0) RowDivider()
                ListRow(
                    icon = { RadioButton(selected = option == sort, onClick = null, enabled = enabled) },
                    title = stringResource(option.labelRes()),
                    subtitle = null,
                    trailing = {},
                    onClick = if (enabled) {
                        {
                            onSelect(option)
                            onDismiss()
                        }
                    } else {
                        null
                    },
                    modifier = Modifier
                        .semantics { role = Role.RadioButton; selected = option == sort }
                        .alpha(if (enabled) 1f else 0.38f),
                )
            }
        }
        if (!amountAvailable) {
            Text(
                stringResource(R.string.filter_amount_disabled_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            )
        }
        Box(Modifier.height(16.dp))
    }
}

@Composable
internal fun SheetTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 12.dp).semantics { heading() },
    )
}

@Composable
internal fun RowDivider() {
    Box(Modifier.fillMaxWidth().padding(start = 14.dp).height(1.dp).background(hairline(MaterialTheme.finance.isDark).brush))
}

// ---- Search ----

@Composable
internal fun SearchField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val focusManager = LocalFocusManager.current
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().border(hairline(MaterialTheme.finance.isDark), CircleShape),
        placeholder = { Text(stringResource(R.string.transactions_search_hint)) },
        leadingIcon = { Icon(AppIcons.Search, contentDescription = null) },
        trailingIcon = if (value.isNotEmpty()) {
            {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(AppIcons.Close, contentDescription = stringResource(R.string.action_remove))
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
            focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
            focusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )
}

// ---- Filter bar ----

/**
 * One horizontally scrolling row of pills, one per filter dimension. An active pill shows its value in the
 * primary tint with a clear glyph; each opens a sheet for just that dimension. "Clear all" appears from two
 * active filters on.
 */
@Composable
internal fun FilterBar(
    state: TransactionsUiState,
    onFilterChange: (TransactionFilter) -> Unit,
    onOpenDate: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenTags: () -> Unit,
    onOpenSection: (FilterSection) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val filter = state.filter
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item(key = "type") {
            val names = filter.types.map { stringResource(it.labelRes()) }
            FilterPill(
                label = summarize(names) ?: stringResource(R.string.filter_type),
                active = names.isNotEmpty(),
                onClick = { onOpenSection(FilterSection.TYPE) },
                onClear = { onFilterChange(filter.copy(types = emptySet())) },
            )
        }
        item(key = "date") {
            FilterPill(
                label = dateLabel(filter) ?: stringResource(R.string.filter_date_any),
                active = filter.from != null || filter.toExclusive != null,
                onClick = onOpenDate,
                onClear = { onFilterChange(filter.copy(from = null, toExclusive = null)) },
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
                onClick = onOpenCategories,
                onClear = {
                    onFilterChange(
                        filter.copy(
                            categoryIds = emptySet(),
                            exactCategoryIds = emptySet(),
                            categoryExact = false,
                            includeUncategorized = false,
                        ),
                    )
                },
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
                    onClick = onOpenTags,
                    onClear = { onFilterChange(filter.copy(tagIds = emptySet(), tagMode = TagMatchMode.ANY)) },
                )
            }
        }
        item(key = "amount") {
            FilterPill(
                label = amountLabel(filter) ?: stringResource(R.string.filter_amount_short),
                active = filter.amount != null,
                onClick = { onOpenSection(FilterSection.AMOUNT) },
                onClear = { onFilterChange(filter.copy(amount = null)) },
            )
        }
        if (filter.activeCount() >= 2) {
            item(key = "clear_all") {
                FilterPill(label = stringResource(R.string.filter_clear_all), active = false, onClick = onClearAll)
            }
        }
    }
}

private fun OperationType.labelRes(): Int = when (this) {
    OperationType.INCOME -> R.string.filter_type_income
    OperationType.TRANSFER -> R.string.filter_type_transfers
    else -> R.string.filter_type_expenses
}

/** "Food", "Food +2", or null when nothing is selected. */
@Composable
private fun summarize(names: List<String>): String? = when (names.size) {
    0 -> null
    1 -> names.single()
    else -> stringResource(R.string.filter_more, names.first(), names.size - 1)
}

@Composable
internal fun dateLabel(filter: TransactionFilter): String? {
    if (filter.from == null && filter.toExclusive == null) return null
    val preset = DatePreset.entries.firstOrNull { it.range(LocalDate.now()) == (filter.from to filter.toExclusive) }
    if (preset != null) return stringResource(preset.labelRes)
    val format = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    val fromDate = filter.from?.let { dateOf(it) }
    val toDate = filter.toExclusive?.let { dateOf(it - 1) }
    val from = fromDate?.format(format)
    val to = toDate?.format(format)
    return when {
        // Same compact format as the period pill on the analytics screens.
        fromDate != null && toDate != null -> compactRangeLabel(fromDate, toDate)
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
@Composable
internal fun DateFilterSheet(
    filter: TransactionFilter,
    onApply: (from: Long?, toExclusive: Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    val today = remember { LocalDate.now() }
    val current = filter.from to filter.toExclusive
    val selectedPreset = DatePreset.entries.firstOrNull { it.range(today) == current }
    val isAny = filter.from == null && filter.toExclusive == null
    var showPicker by rememberSaveable { mutableStateOf(false) }

    if (!showPicker) {
        FinoraSheet(onDismiss) {
            SheetTitle(stringResource(R.string.filter_date))
            GroupedCard(Modifier.fillMaxWidth()) {
                DateOption(stringResource(R.string.filter_date_any), selected = isAny) {
                    onApply(null, null)
                    onDismiss()
                }
                DatePreset.entries.forEach { preset ->
                    RowDivider()
                    DateOption(stringResource(preset.labelRes), selected = preset == selectedPreset) {
                        val (from, to) = preset.range(today)
                        onApply(from, to)
                        onDismiss()
                    }
                }
                RowDivider()
                val customSelected = !isAny && selectedPreset == null
                DateOption(
                    stringResource(R.string.period_custom_range),
                    selected = customSelected,
                    supporting = if (customSelected) dateLabel(filter) else null,
                ) { showPicker = true }
            }
            Box(Modifier.height(16.dp))
        }
    } else {
        // Local day <-> filter instants: `from` is the start of the first day, `toExclusive` the start of the day
        // after the last one, so the inclusive end day is dateOf(toExclusive - 1).
        DateRangePickerDialog(
            initialStart = filter.from?.let { dateOf(it) },
            initialEnd = filter.toExclusive?.let { dateOf(it - 1) },
            // Cancelling goes back to the list of quick ranges.
            onDismiss = { showPicker = false },
            onConfirm = { start, end ->
                onApply(startOfDayMillis(start), endExclusiveMillis(end))
                onDismiss()
            },
        )
    }
}

@Composable
private fun DateOption(label: String, selected: Boolean, supporting: String? = null, onClick: () -> Unit) {
    ListRow(
        icon = { RadioButton(selected = selected, onClick = null) },
        title = label,
        subtitle = supporting,
        trailing = {},
        onClick = onClick,
        modifier = Modifier.semantics { role = Role.RadioButton; this.selected = selected },
    )
}
