package com.yehor.finora.ui.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yehor.finora.R
import com.yehor.finora.domain.analytics.Cell
import com.yehor.finora.domain.analytics.DrillRow
import com.yehor.finora.domain.analytics.Share
import com.yehor.finora.domain.analytics.share
import com.yehor.finora.domain.model.CategoryType
import com.yehor.finora.ui.common.ColorPalette
import com.yehor.finora.ui.common.charts.DonutChart
import com.yehor.finora.ui.common.charts.DonutSlice

/** "Food", "Food (direct)" or "Uncategorized". */
@Composable
fun rowLabel(row: DrillRow): String {
    val name = row.category?.name ?: return stringResource(R.string.picker_uncategorized)
    return if (row.isDirect) stringResource(R.string.analytics_direct_suffix, name) else name
}

/** One legend or donut entry. */
data class DonutEntry(val label: String, val cell: Cell, val color: Color, val share: Share?)

/** Top rows, "Other" and Uncategorized of a report, each with a color and a share of what the donut shows. */
@Composable
fun donutEntries(report: CategoryReport, type: CategoryType): List<DonutEntry> {
    val split = report.split
    val otherLabel = stringResource(R.string.analytics_other)
    val uncategorizedLabel = stringResource(R.string.picker_uncategorized)
    val entries = ArrayList<Triple<String, Cell, Color>>()
    split.top.forEachIndexed { index, row ->
        entries += Triple(rowLabelPlain(row), row.cell, Color(sliceColor(row, index, type)))
    }
    split.other?.let { entries += Triple(otherLabel, it, Color(0xFF9E9E9E)) }
    split.uncategorized?.let { entries += Triple(uncategorizedLabel, it.cell, Color(0xFF757575)) }
    val total = entries.mapNotNull { it.second.estimate }.reduceOrNull { a, b -> a + b }
    return entries.map { (label, cell, color) ->
        DonutEntry(label, cell, color, total?.let { t -> cell.estimate?.let { share(it, t) } })
    }
}

/** The category's own color, else the type default for the first slice and distinct palette colors after it. */
private fun sliceColor(row: DrillRow, index: Int, type: CategoryType): Long =
    row.category?.color
        ?: if (index == 0) ColorPalette.defaultFor(type) else ColorPalette.colors[(index * 3) % ColorPalette.colors.size]

private fun rowLabelPlain(row: DrillRow): String = row.category?.name.orEmpty()

@Composable
fun CategoryDonut(
    title: String,
    entries: List<DonutEntry>,
    centerText: String?,
    modifier: Modifier = Modifier,
) {
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
    DonutChart(
        slices = entries.map { DonutSlice(it.cell.estimate?.amountMinor ?: 0L, it.color) },
        description = description,
        centerText = centerText,
        modifier = modifier,
    )
}

/** The legend list next to a donut: color, label, share and amount. */
@Composable
fun DonutLegend(entries: List<DonutEntry>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        entries.forEach { entry ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).background(entry.color, CircleShape))
                Text(
                    entry.label,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                )
                entry.share?.let {
                    Text(
                        formatPermille(it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
                AmountText(entry.cell, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
