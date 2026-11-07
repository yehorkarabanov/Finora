package com.yehor.finora.ui.operations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import com.yehor.finora.R
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var current by remember { mutableStateOf(filter) }
    val scopeCurrency = FilterScope.scopeCurrency(current, accounts)
    val today = remember { LocalDate.now() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.filter_title), style = MaterialTheme.typography.titleLarge)

            if (FilterSection.TYPE in sections) {
                Text(stringResource(R.string.filter_type), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(OperationType.EXPENSE, OperationType.INCOME, OperationType.TRANSFER).forEach { type ->
                        val selected = type in current.types
                        FilterChip(
                            selected = selected,
                            onClick = {
                                current = current.copy(types = if (selected) current.types - type else current.types + type)
                            },
                            label = {
                                Text(
                                    stringResource(
                                        when (type) {
                                            OperationType.INCOME -> R.string.transaction_kind_income
                                            OperationType.TRANSFER -> R.string.transaction_kind_transfer
                                            else -> R.string.transaction_kind_expense
                                        },
                                    ),
                                )
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
                        FilterChip(
                            selected = selected,
                            onClick = {
                                current = current.copy(
                                    accountIds = if (selected) current.accountIds - account.id else current.accountIds + account.id,
                                )
                            },
                            label = { Text(account.name) },
                        )
                    }
                }
            }

            if (FilterSection.CATEGORY in sections) {
                CategoryFilter(
                    categories = categories,
                    selectedIds = current.categoryIds,
                    includeUncategorized = current.includeUncategorized,
                    onToggle = { id ->
                        current = current.copy(
                            categoryIds = if (id in current.categoryIds) current.categoryIds - id else current.categoryIds + id,
                        )
                    },
                    onToggleUncategorized = { current = current.copy(includeUncategorized = !current.includeUncategorized) },
                )
            }

            if (FilterSection.TAG in sections) {
                Text(stringResource(R.string.filter_tags), style = MaterialTheme.typography.labelLarge)
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
                Text(stringResource(R.string.filter_amount), style = MaterialTheme.typography.labelLarge)
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

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedButton(
                    onClick = { current = TransactionFilter(text = current.text) },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.filter_reset)) }
                Button(
                    onClick = {
                        // An amount range only means something while one currency is in scope.
                        onApply(if (scopeCurrency == null) current.copy(amount = null) else current)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.filter_apply)) }
            }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryFilter(
    categories: List<Category>,
    selectedIds: Set<Long>,
    includeUncategorized: Boolean,
    onToggle: (Long) -> Unit,
    onToggleUncategorized: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val tree = remember(categories) { CategoryTree(categories) }
    val count = selectedIds.size

    TextButton(onClick = { expanded = !expanded }) {
        Text(
            if (count > 0) stringResource(R.string.filter_categories_count, count)
            else stringResource(R.string.filter_categories),
        )
    }
    FilterChip(
        selected = includeUncategorized,
        onClick = onToggleUncategorized,
        label = { Text(stringResource(R.string.picker_uncategorized)) },
    )
    if (expanded) {
        Column {
            listOf(CategoryType.EXPENSE, CategoryType.INCOME).forEach { type ->
                tree.rootsOf(type).forEach { node -> CategoryCheckRow(node, 0, selectedIds, onToggle) }
            }
        }
    }
}

@Composable
private fun CategoryCheckRow(
    node: CategoryNode,
    level: Int,
    selectedIds: Set<Long>,
    onToggle: (Long) -> Unit,
) {
    val id = node.category.id
    ListItem(
        headlineContent = { Text(node.category.name) },
        trailingContent = { Checkbox(checked = id in selectedIds, onCheckedChange = null) },
        modifier = Modifier
            .padding(start = (level * 16).dp)
            .clickable { onToggle(id) },
    )
    node.children.forEach { CategoryCheckRow(it, level + 1, selectedIds, onToggle) }
}
