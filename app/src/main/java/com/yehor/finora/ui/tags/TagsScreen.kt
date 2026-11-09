package com.yehor.finora.ui.tags

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yehor.finora.R
import com.yehor.finora.domain.tag.Tag
import com.yehor.finora.domain.tag.TagNameError
import com.yehor.finora.domain.tag.TagWithUsage
import com.yehor.finora.ui.common.AppIcons
import com.yehor.finora.ui.common.ColorPicker
import com.yehor.finora.ui.theme.PreviewTheme
import com.yehor.finora.ui.theme.finance

private val GroupRadius = 20.dp
private val GroupInnerRadius = 4.dp
private val GroupTopShape = RoundedCornerShape(GroupRadius, GroupRadius, GroupInnerRadius, GroupInnerRadius)
private val GroupMiddleShape = RoundedCornerShape(GroupInnerRadius)
private val GroupBottomShape = RoundedCornerShape(GroupInnerRadius, GroupInnerRadius, GroupRadius, GroupRadius)
private val SingleShape = RoundedCornerShape(GroupRadius)

private fun groupShape(index: Int, count: Int): Shape = when {
    count == 1 -> SingleShape
    index == 0 -> GroupTopShape
    index == count - 1 -> GroupBottomShape
    else -> GroupMiddleShape
}

@Composable
fun TagsScreen(
    onBack: () -> Unit,
    onShowTransactions: (Long) -> Unit,
    onAnalyze: (Long) -> Unit,
    viewModel: TagsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var sortOrder by rememberSaveable { mutableStateOf(TagSortOrder.MOST_USED) }

    TagsContent(
        state = state,
        query = query,
        sortOrder = sortOrder,
        onQueryChange = { query = it },
        onSortChange = { sortOrder = it },
        onBack = onBack,
        onAdd = viewModel::onAdd,
        onEdit = viewModel::onEdit,
        onDeleteRequest = viewModel::onDeleteRequest,
        onShowTransactions = onShowTransactions,
        onAnalyze = onAnalyze,
        onDismissDialog = viewModel::onDismissDialog,
        onSaveTag = viewModel::onSaveTag,
        onDismissDelete = viewModel::onDismissDelete,
        onConfirmDelete = viewModel::onConfirmDelete,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TagsContent(
    state: TagsUiState,
    query: String,
    sortOrder: TagSortOrder,
    onQueryChange: (String) -> Unit,
    onSortChange: (TagSortOrder) -> Unit,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (Tag) -> Unit,
    onDeleteRequest: (TagWithUsage) -> Unit,
    onShowTransactions: (Long) -> Unit,
    onAnalyze: (Long) -> Unit,
    onDismissDialog: () -> Unit,
    onSaveTag: (String, Long?) -> Unit,
    onDismissDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tags_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(AppIcons.Back, contentDescription = stringResource(R.string.navigate_back))
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        floatingActionButton = {
            if (state.tags.isNotEmpty()) {
                FloatingActionButton(onClick = onAdd) {
                    Icon(AppIcons.Add, contentDescription = stringResource(R.string.tags_add))
                }
            }
        },
    ) { padding ->
        if (state.tags.isEmpty()) {
            TagsEmptyState(onAdd = onAdd, modifier = Modifier.fillMaxSize().padding(padding))
        } else {
            TagsList(
                tags = state.tags,
                query = query,
                sortOrder = sortOrder,
                onQueryChange = onQueryChange,
                onSortChange = onSortChange,
                onEdit = onEdit,
                onDeleteRequest = onDeleteRequest,
                onShowTransactions = onShowTransactions,
                onAnalyze = onAnalyze,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        }

        state.dialogState?.let { dialogState ->
            TagEditSheet(state = dialogState, onDismiss = onDismissDialog, onSave = onSaveTag)
        }
        state.deleteState?.let { deleteState ->
            TagDeleteDialog(state = deleteState, onDismiss = onDismissDelete, onConfirm = onConfirmDelete)
        }
    }
}

@Composable
private fun TagsList(
    tags: List<TagWithUsage>,
    query: String,
    sortOrder: TagSortOrder,
    onQueryChange: (String) -> Unit,
    onSortChange: (TagSortOrder) -> Unit,
    onEdit: (Tag) -> Unit,
    onDeleteRequest: (TagWithUsage) -> Unit,
    onShowTransactions: (Long) -> Unit,
    onAnalyze: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val showSearch = TagsListLogic.showSearch(tags.size)
    val effectiveQuery = if (showSearch) query else ""
    val visible = TagsListLogic.visible(tags, effectiveQuery, sortOrder)

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (showSearch) {
            item(key = "search") {
                TagSearchField(
                    query = query,
                    onQueryChange = onQueryChange,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
            }
        }
        item(key = "header") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = pluralStringResource(R.plurals.tag_count, visible.size, visible.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                if (tags.size > 1) {
                    SortPill(sortOrder = sortOrder, onSortChange = onSortChange)
                }
            }
        }
        if (visible.isEmpty()) {
            item(key = "no-results") { NoResults(query = effectiveQuery) }
        } else {
            itemsIndexed(visible, key = { _, item -> item.tag.id }) { index, item ->
                TagRow(
                    item = item,
                    shape = groupShape(index, visible.size),
                    onClick = { onShowTransactions(item.tag.id) },
                    onEdit = { onEdit(item.tag) },
                    onDelete = { onDeleteRequest(item) },
                    onShowTransactions = { onShowTransactions(item.tag.id) },
                    onAnalyze = { onAnalyze(item.tag.id) },
                )
            }
        }
    }
}

@Composable
private fun TagSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { Text(stringResource(R.string.tag_search_hint)) },
        leadingIcon = { Icon(AppIcons.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(AppIcons.Close, contentDescription = stringResource(R.string.filter_clear))
                }
            }
        },
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            errorIndicatorColor = Color.Transparent,
        ),
    )
}

@Composable
private fun SortPill(sortOrder: TagSortOrder, onSortChange: (TagSortOrder) -> Unit) {
    val next = if (sortOrder == TagSortOrder.MOST_USED) TagSortOrder.NAME else TagSortOrder.MOST_USED
    Surface(
        onClick = { onSortChange(next) },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.heightIn(min = 44.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(AppIcons.Sort, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                text = stringResource(
                    if (sortOrder == TagSortOrder.MOST_USED) R.string.tag_sort_most_used else R.string.tag_sort_name,
                ),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun NoResults(query: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            AppIcons.SearchEmpty,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.tag_no_results, query.trim()),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TagsEmptyState(onAdd: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.padding(16.dp), contentAlignment = Alignment.Center) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier.size(64.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(AppIcons.Tag, contentDescription = null, modifier = Modifier.size(32.dp))
                }
                Text(
                    stringResource(R.string.tags_empty_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    stringResource(R.string.tags_empty_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = onAdd) {
                    Icon(AppIcons.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.tags_add))
                }
            }
        }
    }
}

@Composable
private fun TagBadge(color: Long?, size: Int = 44, modifier: Modifier = Modifier) {
    val tint = color?.let { MaterialTheme.finance.accent(it) }
    val background = tint?.copy(alpha = 0.18f) ?: MaterialTheme.colorScheme.surfaceContainerHighest
    Box(
        modifier.size(size.dp).background(background, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            AppIcons.Tag,
            contentDescription = null,
            tint = tint ?: MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size((size * 0.5f).dp),
        )
    }
}

@Composable
private fun TagRow(
    item: TagWithUsage,
    shape: Shape,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onShowTransactions: () -> Unit,
    onAnalyze: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 4.dp).heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            TagBadge(color = item.tag.color)
            Column(Modifier.weight(1f)) {
                Text(
                    item.tag.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    pluralStringResource(R.plurals.tags_usage_count, item.usageCount, item.usageCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(AppIcons.More, contentDescription = stringResource(R.string.action_more_options))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_edit)) },
                        leadingIcon = { Icon(AppIcons.Edit, contentDescription = null) },
                        onClick = { menuOpen = false; onEdit() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_show_transactions)) },
                        leadingIcon = { Icon(AppIcons.Next, contentDescription = null) },
                        onClick = { menuOpen = false; onShowTransactions() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_analyze)) },
                        leadingIcon = { Icon(AppIcons.Sort, contentDescription = null) },
                        onClick = { menuOpen = false; onAnalyze() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(AppIcons.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        },
                        onClick = { menuOpen = false; onDelete() },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TagEditSheet(
    state: TagDialogState,
    onDismiss: () -> Unit,
    onSave: (String, Long?) -> Unit,
) {
    var name by rememberSaveable(state.tagId, state.isEdit) { mutableStateOf(state.name) }
    var color by rememberSaveable(state.tagId, state.isEdit) { mutableStateOf(state.color) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(state.tagId, state.isEdit) { focusRequester.requestFocus() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        TagEditSheetContent(
            state = state,
            name = name,
            color = color,
            onNameChange = { name = it },
            onColorChange = { color = it },
            onSave = { onSave(name, color) },
            nameFocusRequester = focusRequester,
            modifier = Modifier.imePadding(),
        )
    }
}

@Composable
internal fun TagEditSheetContent(
    state: TagDialogState,
    name: String,
    color: Long?,
    onNameChange: (String) -> Unit,
    onColorChange: (Long?) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    nameFocusRequester: FocusRequester? = null,
) {
    val action = TagFormLogic.action(name, state.isEdit)
    val enabled = TagFormLogic.isEnabled(action)
    val hasError = state.error != null || state.isDuplicate

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(if (state.isEdit) R.string.tag_form_edit_title else R.string.tag_form_new_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Surface(shape = GroupTopShape, color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        TagBadge(color = color)
                        val trimmed = name.trim()
                        Text(
                            text = trimmed.ifEmpty { stringResource(R.string.tag_form_preview_placeholder) },
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (trimmed.isEmpty()) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    TextField(
                        value = name,
                        onValueChange = onNameChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .let { if (nameFocusRequester != null) it.focusRequester(nameFocusRequester) else it },
                        label = { Text(stringResource(R.string.tag_form_name)) },
                        singleLine = true,
                        isError = hasError,
                        shape = RoundedCornerShape(16.dp),
                        supportingText = {
                            when {
                                state.isDuplicate -> Text(stringResource(R.string.tag_form_error_duplicate))
                                state.error == TagNameError.EMPTY -> Text(stringResource(R.string.tag_form_error_empty))
                                state.error == TagNameError.TOO_LONG -> Text(stringResource(R.string.tag_form_error_too_long))
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { if (enabled) onSave() }),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            errorContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            errorIndicatorColor = Color.Transparent,
                        ),
                    )
                }
            }
            Surface(shape = GroupBottomShape, color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.picker_color),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                        )
                        NoColorPill(selected = color == null, onClick = { onColorChange(null) })
                    }
                    ColorPicker(selected = color, onSelect = { onColorChange(it) })
                }
            }
        }

        Button(
            onClick = onSave,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(),
        ) {
            Text(
                stringResource(
                    when (action) {
                        TagFormAction.CREATE -> R.string.tag_form_create
                        TagFormAction.SAVE -> R.string.action_save
                        TagFormAction.ENTER_NAME -> R.string.tag_form_error_empty
                        TagFormAction.NAME_TOO_LONG -> R.string.tag_form_error_too_long
                    },
                ),
                style = MaterialTheme.typography.titleSmall,
            )
        }
    }
}

@Composable
private fun NoColorPill(selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        modifier = Modifier.heightIn(min = 32.dp),
    ) {
        Box(Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.tag_form_no_color), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun TagDeleteDialog(
    state: TagDeleteDialogState,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(AppIcons.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(stringResource(R.string.tag_delete_title_named, state.tag.name)) },
        text = {
            if (state.usageCount > 0) {
                Text(pluralStringResource(R.plurals.tag_delete_body, state.usageCount, state.usageCount))
            } else {
                Text(stringResource(R.string.tag_delete_body_unused))
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(R.string.action_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

// region Previews

private fun sampleTags(): List<TagWithUsage> = listOf(
    TagWithUsage(Tag(1, "Vacation", 0xFF2563EB, 0), 12),
    TagWithUsage(Tag(2, "Work", 0xFF16A34A, 0), 5),
    TagWithUsage(Tag(3, "Gifts", null, 0), 1),
    TagWithUsage(Tag(4, "Unused", 0xFFDC2626, 0), 0),
)

@Composable
private fun PreviewList(dark: Boolean, state: TagsUiState) {
    PreviewTheme(dark) {
        TagsContent(
            state = state, query = "", sortOrder = TagSortOrder.MOST_USED,
            onQueryChange = {}, onSortChange = {}, onBack = {}, onAdd = {}, onEdit = {},
            onDeleteRequest = {}, onShowTransactions = {}, onAnalyze = {}, onDismissDialog = {},
            onSaveTag = { _, _ -> }, onDismissDelete = {}, onConfirmDelete = {},
        )
    }
}

@Preview(name = "Tags light", showBackground = true)
@Composable
private fun TagsListLightPreview() = PreviewList(false, TagsUiState(tags = sampleTags()))

@Preview(name = "Tags dark", showBackground = true, backgroundColor = 0xFF0F1412)
@Composable
private fun TagsListDarkPreview() = PreviewList(true, TagsUiState(tags = sampleTags()))

@Preview(name = "Tags empty light", showBackground = true)
@Composable
private fun TagsEmptyLightPreview() = PreviewList(false, TagsUiState())

@Preview(name = "Tags empty dark", showBackground = true, backgroundColor = 0xFF0F1412)
@Composable
private fun TagsEmptyDarkPreview() = PreviewList(true, TagsUiState())

@Composable
private fun PreviewSheet(dark: Boolean) {
    PreviewTheme(dark) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            TagEditSheetContent(
                state = TagDialogState(isEdit = true, tagId = 1, name = "Vacation", color = 0xFF2563EB),
                name = "Vacation",
                color = 0xFF2563EB,
                onNameChange = {},
                onColorChange = {},
                onSave = {},
            )
        }
    }
}

@Preview(name = "Tag sheet light", showBackground = true)
@Composable
private fun TagSheetLightPreview() = PreviewSheet(false)

@Preview(name = "Tag sheet dark", showBackground = true, backgroundColor = 0xFF0F1412)
@Composable
private fun TagSheetDarkPreview() = PreviewSheet(true)

// endregion
