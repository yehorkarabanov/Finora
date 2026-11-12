package com.yehor.finora.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.yehor.finora.R
import com.yehor.finora.designsystem.component.input.FilterPill
import com.yehor.finora.designsystem.component.input.SegmentedPill
import com.yehor.finora.designsystem.icon.AppIcons
import com.yehor.finora.designsystem.theme.PillShape
import com.yehor.finora.designsystem.theme.PreviewTheme
import com.yehor.finora.designsystem.theme.finance
import com.yehor.finora.designsystem.theme.hairline
import com.yehor.finora.domain.tag.Tag
import com.yehor.finora.domain.tag.TagMatchMode
import com.yehor.finora.domain.tag.TagNameRules
import com.yehor.finora.ui.operations.FilterSearchField

/** Compact pill for a selected tag: color dot, name and, when [onRemove] is given, a clear glyph. */
@Composable
fun TagChip(tag: Tag, onRemove: (() -> Unit)? = null) {
    val dot = tag.color?.let { MaterialTheme.finance.accent(it) } ?: MaterialTheme.colorScheme.outline
    TagPill(label = tag.name, dot = dot, onRemove = onRemove)
}

@Composable
private fun TagPill(label: String, dot: Color?, onRemove: (() -> Unit)?) {
    Row(
        Modifier
            .heightIn(min = 32.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, PillShape)
            .border(hairline(MaterialTheme.finance.isDark), PillShape)
            .padding(start = 12.dp, end = if (onRemove == null) 12.dp else 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) {
            Box(Modifier.padding(end = 8.dp).size(8.dp).background(dot, CircleShape))
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
        if (onRemove != null) {
            // 48dp touch target around the 14dp glyph.
            Box(
                Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    AppIcons.Close,
                    contentDescription = stringResource(R.string.action_remove),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

/** A tag typed in the picker that does not exist yet; removable until the form is saved. */
@Composable
internal fun PendingTagChip(name: String, onRemove: () -> Unit) {
    TagPill(label = stringResource(R.string.tag_picker_new_suffix, name), dot = null, onRemove = onRemove)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagPicker(
    allTags: List<Tag>,
    selectedTagIds: Set<Long>,
    pendingTagNames: List<String>,
    onToggleTag: (Long) -> Unit,
    onAddPendingTag: (String) -> Unit,
    onRemovePendingTag: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    val normalizedQuery = TagNameRules.normalize(query)
    val queryKey = TagNameRules.key(query)

    val selectedTags = allTags.filter { it.id in selectedTagIds }
    val matchingTags = if (query.isBlank()) {
        allTags.filter { it.id !in selectedTagIds }
    } else {
        allTags.filter { it.id !in selectedTagIds && TagNameRules.key(it.name).contains(queryKey) }
    }
    val exactMatchExists = allTags.any { TagNameRules.key(it.name) == queryKey } ||
        pendingTagNames.any { TagNameRules.key(it) == queryKey }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (selectedTags.isNotEmpty() || pendingTagNames.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                selectedTags.forEach { tag ->
                    TagChip(tag = tag, onRemove = { onToggleTag(tag.id) })
                }
                pendingTagNames.forEach { name ->
                    PendingTagChip(name = name, onRemove = { onRemovePendingTag(name) })
                }
            }
        }

        FilterSearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = stringResource(R.string.transaction_form_tags),
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            matchingTags.take(10).forEach { tag ->
                FilterPill(
                    label = tag.name,
                    active = false,
                    onClick = {
                        onToggleTag(tag.id)
                        query = ""
                    },
                )
            }
            if (normalizedQuery.isNotBlank() && !exactMatchExists) {
                FilterPill(
                    label = stringResource(R.string.tag_picker_create, normalizedQuery),
                    active = true,
                    onClick = {
                        onAddPendingTag(normalizedQuery)
                        query = ""
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagFilterPicker(
    allTags: List<Tag>,
    selectedTagIds: Set<Long>,
    tagMode: TagMatchMode,
    onToggleTag: (Long) -> Unit,
    onModeChange: (TagMatchMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (selectedTagIds.size >= 2) {
            SegmentedPill(
                options = listOf(
                    stringResource(R.string.filter_tag_mode_any),
                    stringResource(R.string.filter_tag_mode_all),
                ),
                selected = if (tagMode == TagMatchMode.ANY) 0 else 1,
                onSelect = { onModeChange(if (it == 0) TagMatchMode.ANY else TagMatchMode.ALL) },
            )
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            allTags.forEach { tag ->
                FilterPill(
                    label = tag.name,
                    active = tag.id in selectedTagIds,
                    onClick = { onToggleTag(tag.id) },
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

private val PreviewTags = listOf(
    Tag(1, "groceries", 0xFF2E7D32L, 0L),
    Tag(2, "trip", null, 0L),
    Tag(3, "work", 0xFF1565C0L, 0L),
    Tag(4, "gifts", null, 0L),
)

@Composable
private fun TagPickerPreview(dark: Boolean) {
    PreviewTheme(dark) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                TagPicker(PreviewTags, setOf(1L), listOf("holiday"), {}, {}, {})
                TagFilterPicker(PreviewTags, setOf(1L, 3L), TagMatchMode.ANY, {}, {})
            }
        }
    }
}

@Preview(showBackground = true, name = "Tag pickers light")
@Composable
private fun TagPickerLightPreview() = TagPickerPreview(dark = false)

@Preview(showBackground = true, backgroundColor = 0xFF0F1412, name = "Tag pickers dark")
@Composable
private fun TagPickerDarkPreview() = TagPickerPreview(dark = true)
