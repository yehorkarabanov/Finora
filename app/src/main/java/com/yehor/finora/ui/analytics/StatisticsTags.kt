package com.yehor.finora.ui.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yehor.finora.R
import com.yehor.finora.designsystem.chart.WaveChart
import com.yehor.finora.designsystem.component.input.FilterPill
import com.yehor.finora.designsystem.component.input.SegmentedPill
import com.yehor.finora.designsystem.component.list.GroupedCard
import com.yehor.finora.designsystem.icon.AppIcons
import com.yehor.finora.designsystem.icon.IconCatalog
import com.yehor.finora.designsystem.theme.finance
import com.yehor.finora.designsystem.theme.hairline
import com.yehor.finora.domain.account.Account
import com.yehor.finora.domain.analytics.Bucket
import com.yehor.finora.domain.analytics.BucketUnit
import com.yehor.finora.domain.analytics.Cell
import com.yehor.finora.domain.analytics.Delta
import com.yehor.finora.domain.analytics.Measure
import com.yehor.finora.domain.analytics.ResolvedPeriod
import com.yehor.finora.domain.analytics.TrendPoint
import com.yehor.finora.domain.analytics.delta
import com.yehor.finora.domain.model.OperationType
import com.yehor.finora.domain.tag.Tag
import com.yehor.finora.domain.tag.TagMatchMode
import com.yehor.finora.ui.common.ColorPalette
import com.yehor.finora.ui.common.TransactionRow
import java.time.LocalDate
import java.util.Locale

// The Tags tab with tags picked: their figures, wave, categories, accounts and recent transactions.

internal fun LazyListScope.tagDetailItems(
    tab: TagsTab,
    state: StatisticsUiState,
    vm: StatisticsViewModel,
    actions: StatisticsActions,
) {
    val env = state.env ?: return
    val controls = state.controls
    val report = tab.report
    val figures = report.figures
    val measureEnabled = controls.tagFilter.types.size != 1
    item(key = "tag_chips") {
        TagChips(env.tags, controls.tagFilter.tagIds, controls.tagFilter.tagMode, vm::toggleTag, vm::setTagMode)
    }
    item(key = "tag_figures") {
        val measureFigure: (Figures) -> Cell? = { f ->
            when (report.measure) {
                Measure.EXPENSE -> f.expense
                Measure.INCOME -> f.income
                Measure.TRANSFER -> f.transferred
            }
        }
        val current = measureFigure(figures)
        val before = report.previous?.let(measureFigure)
        TagFigures(
            figures = figures,
            measure = report.measure,
            delta = if (current != null && before != null) delta(current.estimate, before.estimate) else null,
            previous = state.previousPeriod,
            today = env.today,
            onOpen = { types -> actions.onOpenTransactions(tab.filter.copy(types = types)) },
        )
    }
    if (report.byDate.isNotEmpty()) {
        item(key = "tag_over_time") {
            OverTimeCard(
                points = report.byDate,
                unit = report.dateUnit,
                measure = report.measure,
                measureEnabled = measureEnabled,
                today = env.today,
                onMeasure = vm::setTagMeasure,
                onOpenBucket = { bucket ->
                    actions.onOpenTransactions(
                        tab.filter.copy(
                            types = setOf(report.measure.operationType()),
                            from = bucket.from,
                            toExclusive = bucket.toExclusive,
                        ),
                    )
                },
            )
        }
    }
    item(key = "tag_categories") {
        CategoryCard(
            report = report.category,
            measure = report.measure,
            base = tab.filter,
            compare = controls.compare,
            categories = env.categories,
            onEnter = vm::enter,
            onBack = { vm.back(it) },
            onOpen = actions.onOpenTransactions,
        )
    }
    if (report.byAccount.isNotEmpty()) {
        item(key = "tag_accounts") {
            BreakdownCard(
                title = stringResource(R.string.stats_by_account),
                items = accountBreakdown(report.byAccount, env.allAccounts) { id ->
                    actions.onOpenTransactions(tab.filter.copy(types = setOf(report.measure.operationType()), accountIds = setOf(id)))
                },
            )
        }
    }
    if (tab.recent.isNotEmpty()) {
        item(key = "tag_recent") {
            Column {
                StatsSectionTitle(
                    stringResource(R.string.stats_recent),
                    link = stringResource(R.string.stats_all_n, figures.operationCount),
                    onLink = { actions.onOpenTransactions(tab.filter) },
                )
                GroupedCard(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    tab.recent.forEach { item -> TransactionRow(item, onClick = { actions.onOpenTransaction(item) }) }
                }
            }
        }
    }
}

/**
 * The picked tags first, as mint chips with ✕, then the others to add; with two or more picked, the
 * Any / All switch. Removing the last picked tag returns to the ranking.
 */
@Composable
internal fun TagChips(
    tags: List<Tag>,
    picked: Set<Long>,
    mode: TagMatchMode,
    onToggle: (Long) -> Unit,
    onMode: (TagMatchMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        val (on, off) = tags.partition { it.id in picked }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            on.forEach { tag ->
                FilterPill(label = "# ${tag.name}", active = true, onClick = { onToggle(tag.id) }, onClear = { onToggle(tag.id) })
            }
            off.forEach { tag ->
                FilterPill(label = "# ${tag.name}", active = false, onClick = { onToggle(tag.id) })
            }
        }
        if (on.size >= 2) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(R.string.stats_tags_match),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SegmentedPill(
                    options = listOf(
                        stringResource(R.string.filter_tag_mode_any_short),
                        stringResource(R.string.filter_tag_mode_all_short),
                    ),
                    selected = TagMatchMode.entries.indexOf(mode),
                    onSelect = { onMode(TagMatchMode.entries[it]) },
                    modifier = Modifier.width(220.dp),
                )
            }
        }
    }
}

/** In / Out / Net, each opening its transactions, then Transferred, the count and the comparison. */
@Composable
internal fun TagFigures(
    figures: Figures,
    measure: Measure,
    delta: Delta?,
    previous: ResolvedPeriod?,
    today: LocalDate,
    onOpen: (Set<OperationType>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val finance = MaterialTheme.finance
    val scheme = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth().padding(top = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        GroupedCard(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Figure(R.string.stats_total_income, figures.income, finance.income, signed = false) { onOpen(setOf(OperationType.INCOME)) }
                FigureDivider()
                Figure(R.string.stats_total_expense, figures.expense, scheme.onSurface, signed = false) { onOpen(setOf(OperationType.EXPENSE)) }
                FigureDivider()
                val net = figures.net?.estimate?.amountMinor ?: 0L
                val netTint = when {
                    net > 0 -> finance.income
                    net < 0 -> finance.negative
                    else -> scheme.onSurface
                }
                Figure(R.string.stats_total_net, figures.net, netTint, signed = true) {
                    onOpen(setOf(OperationType.INCOME, OperationType.EXPENSE))
                }
            }
            val transferred = figures.transferred
            if (transferred != null) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(setOf(OperationType.TRANSFER)) }
                        .heightIn(min = 48.dp)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.stats_total_transferred),
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    AmountText(transferred, color = scheme.onSurface)
                }
            }
        }
        val count = figures.operationCount
        val compare = if (delta != null && previous != null) comparison(measure, delta, previous, today) else null
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(pluralStringResource(R.plurals.analytics_operations, count, count), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
            if (compare != null) {
                Text(
                    " · " + compare.first,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = compare.second,
                )
            }
        }
    }
}

@Composable
private fun RowScope.Figure(label: Int, cell: Cell?, color: Color, signed: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .weight(1f)
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = cell != null, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(label).uppercase(LocalConfiguration.current.locales[0]),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        AmountText(cell, color = color, signed = signed)
    }
}

@Composable
private fun RowScope.FigureDivider() {
    Box(
        Modifier
            .align(Alignment.CenterVertically)
            .width(1.dp)
            .height(36.dp)
            .background(hairline(MaterialTheme.finance.isDark).brush),
    )
}

/** "Over time" with the Spent / Earned / Moved switch, and a wave of the period's buckets. */
@Composable
internal fun OverTimeCard(
    points: List<TrendPoint>,
    unit: BucketUnit,
    measure: Measure,
    measureEnabled: Boolean,
    today: LocalDate,
    onMeasure: (Measure) -> Unit,
    onOpenBucket: (Bucket) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    var selected by rememberSaveable(points.size) { mutableStateOf<Int?>(null) }
    Column(modifier.fillMaxWidth()) {
        StatsSectionTitle(stringResource(R.string.stats_over_time)) {
            SegmentedPill(
                options = listOf(
                    stringResource(R.string.stats_measure_spent),
                    stringResource(R.string.stats_measure_earned),
                    stringResource(R.string.stats_measure_moved),
                ),
                selected = Measure.entries.indexOf(measure),
                onSelect = { onMeasure(Measure.entries[it]) },
                enabled = measureEnabled,
                modifier = Modifier.width(232.dp),
            )
        }
        val values = trendValues(points, today)
        val amounts = points.map { cellText(it.cell) }
        GroupedCard(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            // The 16dp padding leaves room for the wave's glow, which the card clips.
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 14.dp).height(110.dp)) {
                WaveChart(
                    values = values,
                    modifier = Modifier.fillMaxSize(),
                    selectedIndex = selected?.takeIf { it in values.indices },
                    onSelect = { selected = it },
                    label = { i -> "${dateBucketName(points[i].bucket, unit, today, locale)} · ${amounts[i]}" },
                )
            }
            val picks = listOf(0, points.lastIndex / 2, points.lastIndex).distinct()
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = if (picks.size == 1) Arrangement.Center else Arrangement.SpaceBetween,
            ) {
                picks.forEach { i ->
                    Text(
                        dateBucketName(points[i].bucket, unit, today, locale),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val pick = selected?.let { points.getOrNull(it) }
            if (pick != null) {
                FilterPill(
                    label = stringResource(R.string.stats_open_bucket, dateBucketName(pick.bucket, unit, today, locale)),
                    active = false,
                    onClick = { onOpenBucket(pick.bucket) },
                    modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
                )
            }
        }
    }
}

/** "Nov 18", "Nov 2026" or "2026". */
private fun dateBucketName(bucket: Bucket, unit: BucketUnit, today: LocalDate, locale: Locale): String = when (unit) {
    BucketUnit.DAY -> shortDate(bucket.fromDate, today, locale)
    BucketUnit.MONTH -> bucket.fromDate.format(bestPattern(locale, if (bucket.fromDate.year == today.year) "LLL" else "MMMyy"))
    BucketUnit.YEAR -> bucket.fromDate.year.toString()
}

/** By-account rows of a breakdown; a row with an account opens its transactions through [onOpen]. */
@Composable
internal fun accountBreakdown(
    lines: List<BreakdownLine>,
    accounts: List<Account>,
    onOpen: (Long) -> Unit,
): List<BreakdownItem> = lines.mapIndexed { index, line ->
    val account = accounts.firstOrNull { it.id == line.id }
    BreakdownItem(
        key = line.id ?: -1L,
        label = account?.name.orEmpty(),
        icon = IconCatalog.get(account?.icon),
        tint = MaterialTheme.finance.accent(account?.color ?: if (index == 0) ColorPalette.ACCOUNT_DEFAULT else fallbackColor(index)),
        cell = line.cell,
        share = line.share,
        onClick = line.id?.let { id -> { onOpen(id) } },
    )
}

/** By-tag rows of a breakdown; "No tag" is grey and does not open anything. */
@Composable
internal fun tagBreakdown(lines: List<BreakdownLine>, tags: List<Tag>, onOpen: (Long) -> Unit): List<BreakdownItem> =
    lines.mapIndexed { index, line ->
        val tag = tags.firstOrNull { it.id == line.id }
        BreakdownItem(
            key = line.id ?: -1L,
            label = tag?.name ?: stringResource(R.string.stats_no_tag),
            icon = AppIcons.Tag,
            tint = when {
                tag == null -> MaterialTheme.colorScheme.outline
                tag.color != null -> MaterialTheme.finance.accent(tag.color)
                else -> MaterialTheme.finance.accent(fallbackColor(index + 1))
            },
            cell = line.cell,
            share = line.share,
            onClick = tag?.let { { onOpen(it.id) } },
        )
    }
