package com.yehor.finora.ui.tags

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yehor.finora.R
import com.yehor.finora.domain.tag.TagNameError
import com.yehor.finora.domain.tag.TagWithUsage
import com.yehor.finora.ui.common.ColorPicker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagsScreen(
    onBack: () -> Unit,
    onShowTransactions: (Long) -> Unit,
    viewModel: TagsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tags_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_back))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::onAdd) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.tags_add))
            }
        },
    ) { padding ->
        if (state.tags.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.tags_empty_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.tags_empty_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(state.tags, key = { it.tag.id }) { item ->
                    TagRow(
                        item = item,
                        onClick = { onShowTransactions(item.tag.id) },
                        onEdit = { viewModel.onEdit(item.tag) },
                        onDelete = { viewModel.onDeleteRequest(item) },
                        onShowTransactions = { onShowTransactions(item.tag.id) },
                    )
                }
            }
        }

        state.dialogState?.let { dialogState ->
            TagEditDialog(
                state = dialogState,
                onDismiss = viewModel::onDismissDialog,
                onSave = viewModel::onSaveTag,
            )
        }

        state.deleteState?.let { deleteState ->
            TagDeleteDialog(
                state = deleteState,
                onDismiss = viewModel::onDismissDelete,
                onConfirm = viewModel::onConfirmDelete,
            )
        }
    }
}

@Composable
private fun TagRow(
    item: TagWithUsage,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onShowTransactions: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = {
            val color = item.tag.color?.let { Color(it) } ?: MaterialTheme.colorScheme.outline
            Box(Modifier.size(16.dp).background(color, CircleShape))
        },
        headlineContent = { Text(item.tag.name) },
        supportingContent = {
            Text(pluralStringResource(R.plurals.tags_usage_count, item.usageCount, item.usageCount))
        },
        trailingContent = {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.action_more_options))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_edit)) },
                        onClick = { menuOpen = false; onEdit() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_show_transactions)) },
                        onClick = { menuOpen = false; onShowTransactions() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_delete)) },
                        onClick = { menuOpen = false; onDelete() },
                    )
                }
            }
        },
    )
}

@Composable
private fun TagEditDialog(
    state: TagDialogState,
    onDismiss: () -> Unit,
    onSave: (String, Long?) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(state.name) }
    var color by rememberSaveable { mutableStateOf(state.color) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (state.isEdit) R.string.tag_form_edit_title else R.string.tag_form_new_title))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.tag_form_name)) },
                    singleLine = true,
                    isError = state.error != null || state.isDuplicate,
                    supportingText = {
                        when {
                            state.isDuplicate -> Text(stringResource(R.string.tag_form_error_duplicate))
                            state.error == TagNameError.EMPTY -> Text(stringResource(R.string.tag_form_error_empty))
                            state.error == TagNameError.TOO_LONG -> Text(stringResource(R.string.tag_form_error_too_long))
                        }
                    },
                )
                Text(stringResource(R.string.picker_color), style = MaterialTheme.typography.labelMedium)
                ColorPicker(selected = color, onSelect = { color = it })
                if (color != null) {
                    TextButton(onClick = { color = null }) {
                        Text(stringResource(R.string.tag_form_no_color))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, color) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun TagDeleteDialog(
    state: TagDeleteDialogState,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tag_delete_title)) },
        text = {
            if (state.usageCount > 0) {
                Text(pluralStringResource(R.plurals.tag_delete_body, state.usageCount, state.usageCount))
            } else {
                Text(stringResource(R.string.tag_delete_body_unused))
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
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
