package com.yehor.finora.ui.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yehor.finora.R
import com.yehor.finora.domain.analytics.Cell
import com.yehor.finora.domain.analytics.DrillRow
import com.yehor.finora.domain.analytics.Share
import com.yehor.finora.domain.analytics.share
import com.yehor.finora.domain.model.CategoryType
import com.yehor.finora.ui.common.ColorPalette
import com.yehor.finora.designsystem.chart.DonutChart
import com.yehor.finora.designsystem.chart.DonutSlice
import com.yehor.finora.designsystem.theme.finance

/** "Food", "Food (direct)" or "Uncategorized". */
@Composable
fun rowLabel(row: DrillRow): String {
    val name = row.category?.name ?: return stringResource(R.string.picker_uncategorized)
    return if (row.isDirect) stringResource(R.string.analytics_direct_suffix, name) else name
}

/** One legend or donut entry. */
data class DonutEntry(val label: String, val cell: Cell, val color: Color, val share: Share?)

private const val OTHER_COLOR = 0xFF71717AL
private const val UNCATEGORIZED_COLOR = 0xFF52525BL

/** Top rows, "Other" and Uncategorized of a report, each with a color and a share of what the donut shows. */
@Composable
fun donutEntries(report: CategoryReport, type: CategoryType): List<DonutEntry> {
    val split = report.split
    val otherLabel = stringResource(R.string.analytics_other)
    val uncategorizedLabel = stringResource(R.string.picker_uncategorized)
    val finance = MaterialTheme.finance
    val entries = ArrayList<Triple<String, Cell, Color>>()
    split.top.forEachIndexed { index, row ->
        entries += Triple(rowLabelPlain(row), row.cell, finance.accent(sliceColor(row, index, type)))
    }
    split.other?.let { entries += Triple(otherLabel, it, finance.accent(OTHER_COLOR)) }
    split.uncategorized?.let { entries += Triple(uncategorizedLabel, it.cell, finance.accent(UNCATEGORIZED_COLOR)) }
    val total = entries.mapNotNull { it.second.estimate }.reduceOrNull { a, b -> a + b }
    return entries.map { (label, cell, color) ->
        DonutEntry(label, cell, color, total?.let { t -> cell.estimate?.let { share(it, t) } })
    }
}

/** The category's own color, else the type default for the first slice and distinct palette colors after it. */
private fun sliceColor(row: DrillRow, index: Int, type: CategoryType): Long =
    row.category?.color
        ?: if (index == 0) ColorPalette.defaultFor(type) else ColorPalette.colors[(index * 11) % ColorPalette.colors.size]

private fun rowLabelPlain(row: DrillRow): String = row.category?.name.orEmpty()

/**
 * Ring with the total in the middle and a full-width legend below. Tapping a segment or a legend
 * row highlights that category and shows its amount and share in the middle; tapping it again
 * clears the highlight.
 */
@Composable
fun CategoryBreakdown(
    title: String,
    entries: List<DonutEntry>,
    total: Cell?,
    modifier: Modifier = Modifier,
) {
    var selected by rememberSaveable(entries.size) { mutableStateOf<Int?>(null) }
    val selection = selected?.takeIf { it in entries.indices }
    val toggle: (Int?) -> Unit = { index -> selected = if (index == null || index == selection) null else index }

    val description = buildString {
        append(title)
        entries.forEach { entry ->
            append(". ")
            append(entry.label)
            append(' ')
            append(cellText(entry.cell))
            entry.share?.let { append(", ").append(formatPermille(it)) }
        }
    }
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        DonutChart(
            slices = entries.map { DonutSlice(it.cell.estimate?.amountMinor ?: 0L, it.color) },
            description = description,
            selectedIndex = selection,
            onSliceClick = toggle,
            center = { DonutCenter(title, total, selection?.let { entries[it] }) },
        )
        Column(
            Modifier.fillMaxWidth().padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            entries.forEachIndexed { index, entry ->
                LegendRow(
                    entry = entry,
                    selected = index == selection,
                    dimmed = selection != null && index != selection,
                    onClick = { toggle(index) },
                )
            }
        }
    }
}

@Composable
private fun DonutCenter(title: String, total: Cell?, entry: DonutEntry?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            entry?.label ?: title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        val amount = cellText(entry?.cell ?: total)
        Text(
            amount,
            style = if (amount.length > 11) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            textAlign = TextAlign.Center,
        )
        entry?.share?.let {
            Text(
                formatPermille(it),
                style = MaterialTheme.typography.labelMedium,
                color = entry.color,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun LegendRow(entry: DonutEntry, selected: Boolean, dimmed: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent,
        modifier = Modifier.fillMaxWidth().semantics { this.selected = selected },
    ) {
        val alpha = if (dimmed) 0.5f else 1f
        Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(entry.color.copy(alpha = alpha), CircleShape))
                Text(
                    entry.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = 10.dp),
                )
                AmountText(
                    entry.cell,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShareBar(entry.share, entry.color.copy(alpha = alpha), Modifier.weight(1f).padding(start = 20.dp))
                Text(
                    entry.share?.let { formatPermille(it) }.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                    textAlign = TextAlign.End,
                    modifier = Modifier.padding(start = 8.dp).size(width = 44.dp, height = 16.dp),
                )
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, backgroundColor = 0xFF0F1412)
@Composable
private fun CategoryBreakdownDarkPreview() {
    val pln = com.yehor.finora.domain.money.CurrencyRegistry.get("PLN")
    fun cell(minor: Long) = Cell(
        com.yehor.finora.domain.money.Estimate(minor, pln, com.yehor.finora.domain.money.EstimateBasis.NATIVE),
        2, emptySet(), false, null,
    )
    com.yehor.finora.designsystem.theme.PreviewTheme(dark = true) {
        val f = MaterialTheme.finance
        val raw = listOf(
            "Food" to (70_000L to 0xFFDC2626L),
            "Housing" to (45_000L to 0xFF4F46E5L),
            "Transport" to (12_000L to 0xFF0D9488L),
            "Entertainment" to (8_000L to 0xFF9C3FE0L),
            "Other" to (3_000L to OTHER_COLOR),
        )
        val total = raw.sumOf { it.second.first }
        val entries = raw.map { (label, v) ->
            DonutEntry(label, cell(v.first), f.accent(v.second), Share((v.first * 1000 / total).toInt()))
        }
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            CategoryBreakdown("Spent", entries, cell(total), Modifier.padding(16.dp))
        }
    }
}

@Composable
private fun ShareBar(share: Share?, color: Color, modifier: Modifier = Modifier) {
    val fraction = ((share?.permille ?: 0) / 1000f).coerceIn(0f, 1f)
    Box(
        modifier
            .height(4.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        if (fraction > 0f) {
            Box(Modifier.fillMaxWidth(fraction).height(4.dp).clip(CircleShape).background(color))
        }
    }
}
