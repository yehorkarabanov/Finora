package com.yehor.finora.ui.operations

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yehor.finora.R
import com.yehor.finora.designsystem.component.input.SegmentedPill
import com.yehor.finora.designsystem.component.sheet.FinoraSheet
import com.yehor.finora.domain.operation.TransactionFilter
import com.yehor.finora.domain.tag.Tag
import com.yehor.finora.domain.tag.TagMatchMode
import com.yehor.finora.designsystem.theme.finance

/** Most used first, then by name. The order is fixed while the sheet is open, so rows never jump on tap. */
internal fun orderTags(tags: List<Tag>, usage: Map<Long, Int>): List<Tag> =
    tags.sortedWith(compareByDescending<Tag> { usage[it.id] ?: 0 }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })

/**
 * Tag filter in the same style as the category sheet: a Match any / Match all track (from two tags on,
 * with a one-line explanation), search for long lists, and one connected group of tag rows.
 */
@Composable
fun TagFilterSheet(
    filter: TransactionFilter,
    tags: List<Tag>,
    usage: Map<Long, Int>,
    onFilterChange: (TransactionFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val ordered = remember(tags) { orderTags(tags, usage) }
    val needle = query.trim()
    val rows = if (needle.isEmpty()) ordered else ordered.filter { it.name.contains(needle, ignoreCase = true) }

    FinoraSheet(onDismiss) {
        Column {
            FilterSheetHeader(
                title = stringResource(R.string.filter_tags),
                showClear = filter.tagIds.isNotEmpty(),
                onClear = { onFilterChange(filter.copy(tagIds = emptySet(), tagMode = TagMatchMode.ANY)) },
            )

            if (filter.tagIds.size >= 2) {
                SegmentedPill(
                    options = listOf(
                        stringResource(R.string.filter_tag_mode_any_short),
                        stringResource(R.string.filter_tag_mode_all_short),
                    ),
                    selected = if (filter.tagMode == TagMatchMode.ANY) 0 else 1,
                    onSelect = { onFilterChange(filter.copy(tagMode = if (it == 0) TagMatchMode.ANY else TagMatchMode.ALL)) },
                    modifier = Modifier.padding(vertical = 4.dp),
                )
                Text(
                    stringResource(
                        if (filter.tagMode == TagMatchMode.ALL) R.string.filter_tag_mode_all_hint else R.string.filter_tag_mode_any_hint,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            if (tags.size > FILTER_SEARCH_THRESHOLD) {
                FilterSearchField(
                    query,
                    { query = it },
                    stringResource(R.string.filter_tags_search),
                    Modifier.padding(vertical = 8.dp),
                )
            }

            LazyColumn(
                modifier = Modifier.weight(1f, fill = false),
                contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                itemsIndexed(rows, key = { _, tag -> tag.id }) { index, tag ->
                    TagRow(
                        tag = tag,
                        usage = usage[tag.id] ?: 0,
                        checked = tag.id in filter.tagIds,
                        shape = filterGroupShape(index, rows.size),
                        onToggle = {
                            val ids = filter.tagIds
                            onFilterChange(filter.copy(tagIds = if (tag.id in ids) ids - tag.id else ids + tag.id))
                        },
                    )
                }
                if (rows.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            stringResource(R.string.transactions_empty_filter_title),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
            FilterSheetDone(onDismiss)
        }
    }
}

@Composable
private fun TagRow(tag: Tag, usage: Int, checked: Boolean, shape: Shape, onToggle: () -> Unit) {
    val tint = tag.color?.let { MaterialTheme.finance.accent(it) } ?: MaterialTheme.colorScheme.onSurfaceVariant
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .clickable(role = Role.Checkbox, onClick = onToggle)
                .heightIn(min = 60.dp)
                .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(36.dp).background(tint.copy(alpha = 0.18f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("#", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = tint)
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    tag.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    pluralStringResource(R.plurals.analytics_operations, usage, usage),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(end = 4.dp))
        }
    }
}
