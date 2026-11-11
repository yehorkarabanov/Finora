package com.yehor.finora.ui.operations

import android.text.format.DateFormat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yehor.finora.R
import com.yehor.finora.designsystem.component.button.PrimaryButton
import com.yehor.finora.designsystem.component.display.AmountText
import com.yehor.finora.designsystem.component.list.GroupedCard
import com.yehor.finora.designsystem.component.list.RoundIcon
import com.yehor.finora.designsystem.icon.AppIcons
import com.yehor.finora.designsystem.theme.finance
import com.yehor.finora.designsystem.theme.hairline
import com.yehor.finora.domain.model.OperationType
import com.yehor.finora.domain.money.Money
import com.yehor.finora.domain.operation.CurrencySummary
import com.yehor.finora.domain.operation.DayGroup
import com.yehor.finora.domain.operation.TransactionItem
import com.yehor.finora.ui.accounts.NotIncludedLine
import com.yehor.finora.ui.accounts.formatDate
import com.yehor.finora.ui.common.TransactionRow
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val MINUS = "−"

/** How many rows of a list without day headers (amount sort) share one card. */
private const val HEADERLESS_CHUNK = 20

/** The rows the list shows, decoupled from Paging so the list can be previewed and tested. */
internal class RowSource(
    val count: Int,
    /** Reads a row without asking Paging to load around it. */
    val peek: (Int) -> TransactionListRow?,
    /** Reads a row and lets Paging prefetch around it. */
    val get: (Int) -> TransactionListRow?,
    /** True once the last page is loaded; until then the last day may be incomplete. */
    val endReached: Boolean,
)

/** One card of the list: a day (with its header) or, for sorts that drop day headers, a chunk of rows. */
internal data class ListSection(
    val key: String,
    val group: DayGroup?,
    val start: Int,
    val endExclusive: Int,
    val net: Money?,
)

/** Splits the flat header/entry rows into cards. */
internal fun buildSections(source: RowSource, chunk: Int = HEADERLESS_CHUNK): List<ListSection> {
    val sections = mutableListOf<ListSection>()
    var i = 0
    while (i < source.count) {
        val row = source.peek(i)
        var j = i + 1
        if (row is TransactionListRow.Header) {
            while (j < source.count && source.peek(j) !is TransactionListRow.Header) j++
            val items = (i + 1 until j).mapNotNull { (source.peek(it) as? TransactionListRow.Entry)?.item }
            // The last day may still be getting rows from the next page, so its total is not final.
            val complete = j < source.count || source.endReached
            sections += ListSection(row.key, row.group, i + 1, j, if (complete) dayNet(items) else null)
        } else {
            while (j < source.count && j - i < chunk && source.peek(j) !is TransactionListRow.Header) j++
            sections += ListSection("chunk_${row?.key ?: i}", null, i, j, null)
        }
        i = j
    }
    return sections
}

/**
 * Net of the day's income and expenses in their own currency, or null when the day has none or mixes
 * currencies (never add different currencies). Transfers move money between accounts and are left out.
 */
internal fun dayNet(items: List<TransactionItem>): Money? {
    val entries = items.filter { it.type != OperationType.TRANSFER }.flatMap { it.entries }
    if (entries.isEmpty()) return null
    val currency = entries.first().money.currency
    if (entries.any { it.money.currency != currency }) return null
    return Money(entries.sumOf { it.money.amountMinor }, currency)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TransactionsList(
    state: TransactionsUiState,
    source: RowSource,
    listState: LazyListState,
    onOpenTransaction: (TransactionItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Day rows only change when items are added or removed, the last page arrives, or the order changes.
    val sections = remember(source.count, source.endReached, state.sort, state.filter) { buildSections(source) }
    LazyColumn(modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(top = 4.dp, bottom = 8.dp)) {
        if (state.filter.isActive && state.summary.isNotEmpty()) {
            item(key = "summary") {
                SummaryCard(state, Modifier.padding(horizontal = 18.dp, vertical = 8.dp))
            }
        }
        sections.forEach { section ->
            if (section.group != null) {
                stickyHeader(key = section.key) { DayHeader(section.group, section.net) }
            }
            item(key = "card_${section.key}") {
                GroupedCard(Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(top = if (section.group == null) 8.dp else 0.dp)) {
                    var first = true
                    for (index in section.start until section.endExclusive) {
                        val entry = source.get(index) as? TransactionListRow.Entry ?: continue
                        if (!first) RowDivider()
                        first = false
                        TransactionRow(item = entry.item, onClick = { onOpenTransaction(entry.item) })
                    }
                }
            }
        }
    }
}

// ---- Day header ----

@Composable
private fun DayHeader(group: DayGroup, net: Money?) {
    val locale = Locale.getDefault()
    // Locale-aware field order ("Nov 10" / "10 нояб.").
    val format = remember(locale) { DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "MMMd"), locale) }
    val yearFormat = remember(locale) { DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "yMMMd"), locale) }
    val today = LocalDate.now()
    val text = when (group) {
        DayGroup.Today -> stringResource(R.string.transactions_day_today) + " · " + today.format(format)
        DayGroup.Yesterday -> stringResource(R.string.transactions_day_yesterday) + " · " + today.minusDays(1).format(format)
        is DayGroup.Date ->
            group.localDate.format(if (group.localDate.year == today.year) format else yearFormat)
    }.uppercase(locale)
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(start = 22.dp, end = 22.dp, top = 14.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (net != null) {
            val minor = net.amountMinor
            val amount = when {
                minor > 0 -> "+" + net.format()
                minor < 0 -> MINUS + Money(-minor, net.currency).format()
                else -> net.format()
            }
            AmountText(
                amount,
                MaterialTheme.typography.labelLarge,
                when {
                    minor > 0 -> MaterialTheme.finance.income
                    minor < 0 -> MaterialTheme.finance.expense
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                animate = false,
            )
        }
    }
}

// ---- Summary ----

/** Totals of what the filter matches: income, expenses and net per currency, plus an estimate across currencies. */
@Composable
internal fun SummaryCard(state: TransactionsUiState, modifier: Modifier = Modifier) {
    val count = state.summary.sumOf { it.operationCount }
    GroupedCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 4.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                pluralStringResource(R.plurals.analytics_operations, count, count),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 14.dp).semantics { heading() },
            )
            val multiCurrency = state.summary.size > 1
            state.summary.forEach { summary -> SummaryRow(summary, showCurrency = multiCurrency) }
            state.convertedSummary?.let { converted ->
                Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
            Text(
                currency.code,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 14.dp),
            )
        }
        Row(Modifier.fillMaxWidth()) {
            SummaryFigure(
                stringResource(R.string.transactions_summary_income_label),
                "+" + summary.income.format(),
                MaterialTheme.finance.income,
                Modifier.weight(1f),
            )
            SummaryDivider()
            SummaryFigure(
                stringResource(R.string.transactions_summary_expense_label),
                MINUS + summary.expense.format(),
                MaterialTheme.colorScheme.onSurface,
                Modifier.weight(1f),
            )
            SummaryDivider()
            SummaryFigure(stringResource(R.string.dashboard_net), netText, netColor, Modifier.weight(1f))
        }
    }
}

@Composable
private fun RowScope.SummaryDivider() {
    Box(
        Modifier
            .width(1.dp)
            .height(36.dp)
            .align(Alignment.CenterVertically)
            .background(hairline(MaterialTheme.finance.isDark).brush),
    )
}

@Composable
private fun SummaryFigure(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        AmountText(value, MaterialTheme.typography.titleMedium, color, animate = false)
    }
}

// ---- Empty ----

@Composable
internal fun EmptyState(filtered: Boolean, onClearFilters: () -> Unit, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RoundIcon(
                if (filtered) AppIcons.SearchEmpty else AppIcons.Receipt,
                MaterialTheme.colorScheme.onSurfaceVariant,
                size = 64.dp,
            )
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
            Box(Modifier.padding(top = 12.dp).width(240.dp)) {
                PrimaryButton(
                    text = stringResource(if (filtered) R.string.transactions_clear_filters else R.string.transactions_add),
                    onClick = if (filtered) onClearFilters else onAdd,
                )
            }
        }
    }
}
