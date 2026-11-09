package com.yehor.finora.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yehor.finora.R
import com.yehor.finora.domain.tag.Tag
import com.yehor.finora.domain.tag.TagMatchMode
import com.yehor.finora.domain.tag.TagNameRules

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagChip(tag: Tag, onRemove: (() -> Unit)? = null) {
    InputChip(
        selected = false,
        onClick = {},
        label = { Text(tag.name) },
        leadingIcon = {
            val color = tag.color?.let { Color(it) } ?: MaterialTheme.colorScheme.outline
            Box(Modifier.size(8.dp).background(color, CircleShape))
        },
        trailingIcon = onRemove?.let {
            {
                IconButton(onClick = it, modifier = Modifier.size(16.dp)) {
                    Icon(AppIcons.Close, contentDescription = stringResource(R.string.action_remove))
                }
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
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
                    InputChip(
                        selected = true,
                        onClick = {},
                        label = { Text(stringResource(R.string.tag_picker_new_suffix, name)) },
                        trailingIcon = {
                            IconButton(onClick = { onRemovePendingTag(name) }, modifier = Modifier.size(16.dp)) {
                                Icon(AppIcons.Close, contentDescription = stringResource(R.string.action_remove))
                            }
                        },
                    )
                }
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(stringResource(R.string.transaction_form_tags)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            matchingTags.take(10).forEach { tag ->
                AssistChip(
                    onClick = {
                        onToggleTag(tag.id)
                        query = ""
                    },
                    label = { Text(tag.name) },
                )
            }
            if (normalizedQuery.isNotBlank() && !exactMatchExists) {
                AssistChip(
                    onClick = {
                        onAddPendingTag(normalizedQuery)
                        query = ""
                    },
                    label = { Text(stringResource(R.string.tag_picker_create, normalizedQuery)) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
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
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = tagMode == TagMatchMode.ANY,
                    onClick = { onModeChange(TagMatchMode.ANY) },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                ) {
                    Text(stringResource(R.string.filter_tag_mode_any))
                }
                SegmentedButton(
                    selected = tagMode == TagMatchMode.ALL,
                    onClick = { onModeChange(TagMatchMode.ALL) },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                ) {
                    Text(stringResource(R.string.filter_tag_mode_all))
                }
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            allTags.forEach { tag ->
                FilterChip(
                    selected = tag.id in selectedTagIds,
                    onClick = { onToggleTag(tag.id) },
                    label = { Text(tag.name) },
                )
            }
        }
    }
}
