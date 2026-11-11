package com.yehor.finora.ui.operations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import com.yehor.finora.R
import com.yehor.finora.designsystem.component.button.PrimaryButton
import com.yehor.finora.designsystem.component.input.FilterPill
import com.yehor.finora.designsystem.component.sheet.FinoraSheet
import com.yehor.finora.domain.account.Account
import com.yehor.finora.domain.category.Category
import com.yehor.finora.domain.category.CategoryNode
import com.yehor.finora.domain.category.CategoryTree
import com.yehor.finora.domain.model.CategoryType
import com.yehor.finora.domain.model.OperationType
import com.yehor.finora.domain.money.AmountParseResult
import com.yehor.finora.domain.money.AmountParser
import com.yehor.finora.domain.money.Currency
import com.yehor.finora.domain.money.Money
import com.yehor.finora.domain.operation.AmountRange
import com.yehor.finora.domain.operation.FilterScope
import com.yehor.finora.domain.operation.TransactionFilter
import com.yehor.finora.domain.tag.Tag
import com.yehor.finora.ui.common.DateField
import com.yehor.finora.ui.common.TagFilterPicker
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal fun startOfDayMillis(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Long =
    date.atStartOfDay(zone).toInstant().toEpochMilli()

/** The end day is inclusive, so the stored bound is the start of the next local day. */
internal fun endExclusiveMillis(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Long =
    startOfDayMillis(date.plusDays(1), zone)

internal fun dateOf(millis: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
    Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

/** The parts of the filter sheet a caller can show. */
enum class FilterSection { TYPE, ACCOUNT, CATEGORY, TAG, DATE, AMOUNT, TEXT }

/** Clears only the dimensions the sheet shows, so a one-section sheet never wipes the other filters. */
internal fun TransactionFilter.resetSections(sections: Set<FilterSection>): TransactionFilter {
    val default = TransactionFilter()
    return copy(
        types = if (FilterSection.TYPE in sections) default.types else types,
        accountIds = if (FilterSection.ACCOUNT in sections) default.accountIds else accountIds,
        categoryIds = if (FilterSection.CATEGORY in sections) default.categoryIds else categoryIds,
        exactCategoryIds = if (FilterSection.CATEGORY in sections) default.exactCategoryIds else exactCategoryIds,
        categoryExact = if (FilterSection.CATEGORY in sections) default.categoryExact else categoryExact,
        includeUncategorized = if (FilterSection.CATEGORY in sections) default.includeUncategorized else includeUncategorized,
        tagIds = if (FilterSection.TAG in sections) default.tagIds else tagIds,
        tagMode = if (FilterSection.TAG in sections) default.tagMode else tagMode,
        from = if (FilterSection.DATE in sections) default.from else from,
        toExclusive = if (FilterSection.DATE in sections) default.toExclusive else toExclusive,
        amount = if (FilterSection.AMOUNT in sections) default.amount else amount,
    )
}

private fun FilterSection.titleRes(): Int = when (this) {
    FilterSection.TYPE -> R.string.filter_type
    FilterSection.ACCOUNT -> R.string.filter_accounts
    FilterSection.CATEGORY -> R.string.filter_categories
    FilterSection.TAG -> R.string.filter_tags
    FilterSection.DATE -> R.string.filter_date
    FilterSection.AMOUNT -> R.string.filter_amount
    FilterSection.TEXT -> R.string.filter_title
}

@Composable
fun TransactionFilterSheet(
    filter: TransactionFilter,
    accounts: List<Account>,
    categories: List<Category>,
    tags: List<Tag>,
    onApply: (TransactionFilter) -> Unit,
    onDismiss: () -> Unit,
    sections: Set<FilterSection> = FilterSection.entries.toSet(),
) {
    var current by remember { mutableStateOf(filter) }
    val scopeCurrency = FilterScope.scopeCurrency(current, accounts)
    val today = remember { LocalDate.now() }

    FinoraSheet(onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val single = sections.singleOrNull()
            Text(
                stringResource(single?.titleRes() ?: R.string.filter_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )

            if (FilterSection.TYPE in sections) {
                Text(stringResource(R.string.filter_type), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(OperationType.EXPENSE, OperationType.INCOME, OperationType.TRANSFER).forEach { type ->
                        val selected = type in current.types
                        FilterPill(
                            label = stringResource(
                                when (type) {
                                    OperationType.INCOME -> R.string.transaction_kind_income
                                    OperationType.TRANSFER -> R.string.transaction_kind_transfer
                                    else -> R.string.transaction_kind_expense
                                },
                            ),
                            active = selected,
                            onClick = {
                                current = current.copy(types = if (selected) current.types - type else current.types + type)
                            },
                        )
                    }
                }
            }

            if (FilterSection.ACCOUNT in sections) {
                Text(stringResource(R.string.filter_accounts), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    accounts.forEach { account ->
                        val selected = account.id in current.accountIds
                        FilterPill(
                            label = account.name,
                            active = selected,
                            onClick = {
                                current = current.copy(
                                    accountIds = if (selected) current.accountIds - account.id else current.accountIds + account.id,
                                )
                            },
                        )
                    }
                }
            }

            if (FilterSection.CATEGORY in sections) {
                CategoryFilter(
                    categories = categories,
                    selectedIds = current.categoryIds,
                    exactIds = current.exactCategoryIds,
                    isGlobalExact = current.categoryExact,
                    includeUncategorized = current.includeUncategorized,
                    onCycleCategory = { id, hasChildren ->
                        val isSelected = id in current.categoryIds
                        val isExact = isSelected && (id in current.exactCategoryIds || current.categoryExact)
                        current = if (!hasChildren) {
                            if (isSelected) {
                                current.copy(
                                    categoryIds = current.categoryIds - id,
                                    exactCategoryIds = current.exactCategoryIds - id,
                                )
                            } else {
                                current.copy(
                                    categoryIds = current.categoryIds + id,
                                    exactCategoryIds = current.exactCategoryIds - id,
                                )
                            }
                        } else {
                            when {
                                !isSelected -> {
                                    // Stage 1: Select direct only
                                    current.copy(
                                        categoryIds = current.categoryIds + id,
                                        exactCategoryIds = current.exactCategoryIds + id,
                                    )
                                }
                                isExact -> {
                                    // Stage 2: Select direct and all descendants
                                    current.copy(
                                        categoryIds = current.categoryIds,
                                        exactCategoryIds = current.exactCategoryIds - id,
                                        categoryExact = false,
                                    )
                                }
                                else -> {
                                    // Stage 3: Deselect all
                                    current.copy(
                                        categoryIds = current.categoryIds - id,
                                        exactCategoryIds = current.exactCategoryIds - id,
                                    )
                                }
                            }
                        }
                    },
                    onToggleUncategorized = { current = current.copy(includeUncategorized = !current.includeUncategorized) },
                    initiallyExpanded = single == FilterSection.CATEGORY,
                )
            }

            if (FilterSection.TAG in sections) {
                if (single == null) Text(stringResource(R.string.filter_tags), style = MaterialTheme.typography.labelLarge)
                TagFilterPicker(
                    allTags = tags,
                    selectedTagIds = current.tagIds,
                    tagMode = current.tagMode,
                    onToggleTag = { id ->
                        current = current.copy(
                            tagIds = if (id in current.tagIds) current.tagIds - id else current.tagIds + id,
                        )
                    },
                    onModeChange = { current = current.copy(tagMode = it) },
                )
            }

            if (FilterSection.DATE in sections) {
                Text(stringResource(R.string.filter_date), style = MaterialTheme.typography.labelLarge)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    DateField(
                        date = current.from?.let { dateOf(it) },
                        onDateChange = { current = current.copy(from = startOfDayMillis(it)) },
                        maxDate = today,
                        label = stringResource(R.string.filter_date_from),
                        modifier = Modifier.weight(1f),
                    )
                    DateField(
                        date = current.toExclusive?.let { dateOf(it - 1) },
                        onDateChange = { current = current.copy(toExclusive = endExclusiveMillis(it)) },
                        maxDate = today,
                        label = stringResource(R.string.filter_date_to),
                        modifier = Modifier.weight(1f),
                    )
                }
                if (current.from != null || current.toExclusive != null) {
                    TextButton(onClick = { current = current.copy(from = null, toExclusive = null) }) {
                        Text(stringResource(R.string.filter_clear_date))
                    }
                }
            }

            if (FilterSection.AMOUNT in sections) {
                if (single == null) Text(stringResource(R.string.filter_amount), style = MaterialTheme.typography.labelLarge)
                if (scopeCurrency != null) {
                    AmountRangeFields(
                        currency = scopeCurrency,
                        range = current.amount,
                        onChange = { current = current.copy(amount = it) },
                    )
                } else {
                    Text(
                        stringResource(R.string.filter_amount_disabled_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

        }
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { current = current.resetSections(sections) }) {
                Text(stringResource(R.string.filter_reset))
            }
            PrimaryButton(
                text = stringResource(R.string.filter_apply),
                onClick = {
                    // An amount range only means something while one currency is in scope.
                    onApply(if (scopeCurrency == null) current.copy(amount = null) else current)
                    onDismiss()
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun AmountRangeFields(
    currency: Currency,
    range: AmountRange?,
    onChange: (AmountRange?) -> Unit,
) {
    fun text(minor: Long?) = minor?.let { Money(it, currency).toPlainString() }.orEmpty()
    var minText by remember(currency) { mutableStateOf(text(range?.minMinor)) }
    var maxText by remember(currency) { mutableStateOf(text(range?.maxMinor)) }

    fun parse(value: String): Long? = (AmountParser.parse(value, currency) as? AmountParseResult.Value)?.minor

    fun publish(min: String, max: String) {
        val minMinor = parse(min)
        val maxMinor = parse(max)
        onChange(if (minMinor == null && maxMinor == null) null else AmountRange(currency, minMinor, maxMinor))
    }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(
            value = minText,
            onValueChange = {
                minText = it
                publish(it, maxText)
            },
            label = { Text("${stringResource(R.string.filter_amount_min)} (${currency.symbol})") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = maxText,
            onValueChange = {
                maxText = it
                publish(minText, it)
            },
            label = { Text("${stringResource(R.string.filter_amount_max)} (${currency.symbol})") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CategoryFilter(
    categories: List<Category>,
    selectedIds: Set<Long>,
    exactIds: Set<Long>,
    isGlobalExact: Boolean,
    includeUncategorized: Boolean,
    onCycleCategory: (id: Long, hasChildren: Boolean) -> Unit,
    onToggleUncategorized: () -> Unit,
    initiallyExpanded: Boolean = false,
) {
    val totalCount = selectedIds.size + if (includeUncategorized) 1 else 0
    var expanded by remember { mutableStateOf(initiallyExpanded || totalCount > 0) }
    val tree = remember(categories) { CategoryTree(categories) }

    TextButton(onClick = { expanded = !expanded }) {
        Text(
            if (totalCount > 0) stringResource(R.string.filter_categories_count, totalCount)
            else stringResource(R.string.filter_categories),
        )
    }
    if (expanded) {
        Column {
            listOf(CategoryType.EXPENSE, CategoryType.INCOME).forEach { type ->
                tree.rootsOf(type).forEach { node ->
                    CategoryCheckRow(
                        node = node,
                        level = 0,
                        selectedIds = selectedIds,
                        exactIds = exactIds,
                        isGlobalExact = isGlobalExact,
                        onCycleCategory = onCycleCategory,
                    )
                }
            }
            ListItem(
                headlineContent = { Text(stringResource(R.string.picker_uncategorized)) },
                trailingContent = { Checkbox(checked = includeUncategorized, onCheckedChange = null) },
                modifier = Modifier.clickable { onToggleUncategorized() },
            )
        }
    }
}

@Composable
private fun CategoryCheckRow(
    node: CategoryNode,
    level: Int,
    selectedIds: Set<Long>,
    exactIds: Set<Long>,
    isGlobalExact: Boolean,
    onCycleCategory: (id: Long, hasChildren: Boolean) -> Unit,
) {
    val id = node.category.id
    val hasChildren = node.children.isNotEmpty()
    val isSelected = id in selectedIds
    val isExact = isSelected && (isGlobalExact || id in exactIds)

    val checkboxState = when {
        !isSelected -> ToggleableState.Off
        hasChildren && isExact -> ToggleableState.Indeterminate
        else -> ToggleableState.On
    }

    val supportingText = if (hasChildren && isSelected) {
        if (isExact) stringResource(R.string.filter_category_stage_direct)
        else stringResource(R.string.filter_category_stage_recursive)
    } else null

    ListItem(
        headlineContent = { Text(node.category.name) },
        supportingContent = supportingText?.let { text ->
            { Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
        },
        trailingContent = {
            if (hasChildren) {
                TriStateCheckbox(state = checkboxState, onClick = null)
            } else {
                Checkbox(checked = isSelected, onCheckedChange = null)
            }
        },
        modifier = Modifier
            .padding(start = (level * 16).dp)
            .clickable { onCycleCategory(id, hasChildren) },
    )
    node.children.forEach {
        CategoryCheckRow(
            node = it,
            level = level + 1,
            selectedIds = selectedIds,
            exactIds = exactIds,
            isGlobalExact = isGlobalExact,
            onCycleCategory = onCycleCategory,
        )
    }
}
