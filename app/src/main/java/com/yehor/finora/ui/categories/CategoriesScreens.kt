package com.yehor.finora.ui.categories

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yehor.finora.R
import com.yehor.finora.data.repo.ReassignTarget
import com.yehor.finora.domain.category.CategoryRules.ParentCheck
import com.yehor.finora.domain.model.CategoryType
import com.yehor.finora.ui.common.CategoryTreePicker
import com.yehor.finora.ui.common.ColorPalette
import com.yehor.finora.ui.common.ColorPicker
import com.yehor.finora.ui.common.IconBadge
import com.yehor.finora.ui.common.IconKeys
import com.yehor.finora.ui.common.IconPicker
import com.yehor.finora.ui.common.PickerMode
import com.yehor.finora.ui.common.PickerRow

private val TabOrder = listOf(CategoryType.EXPENSE, CategoryType.INCOME, CategoryType.TRANSFER)

@StringRes
private fun CategoryType.labelRes(): Int = when (this) {
    CategoryType.EXPENSE -> R.string.categories_tab_expense
    CategoryType.INCOME -> R.string.categories_tab_income
    CategoryType.TRANSFER -> R.string.categories_tab_transfer
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    onBack: () -> Unit,
    onAdd: (type: CategoryType, parentId: Long?) -> Unit,
    onEdit: (Long) -> Unit,
    onReviewTransactions: (Long) -> Unit,
    viewModel: CategoriesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pickOther by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.categories_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_back))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onAdd(state.type, null) }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.categories_add))
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(selectedTabIndex = TabOrder.indexOf(state.type)) {
                TabOrder.forEach { t ->
                    Tab(
                        selected = state.type == t,
                        onClick = { viewModel.onType(t) },
                        text = { Text(stringResource(t.labelRes())) },
                    )
                }
            }
            if (state.rows.isEmpty()) {
                Text(
                    stringResource(R.string.categories_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(state.rows, key = { it.category.id }) { row ->
                    CategoryRow(
                        row = row,
                        onToggle = { viewModel.toggle(row.category.id) },
                        onAddChild = { onAdd(row.category.type, row.category.id) },
                        onEdit = { onEdit(row.category.id) },
                        onDelete = { viewModel.requestDelete(row.category.id) },
                    )
                }
            }
        }
    }

    state.delete?.let { dialog ->
        DeleteCategoryDialog(
            state = dialog,
            onUncategorized = { viewModel.onTarget(ReassignTarget.Uncategorized) },
            onSuggested = { dialog.impact.suggestedTarget?.let { viewModel.onTarget(ReassignTarget.To(it)) } },
            onChooseOther = { pickOther = true },
            onReviewTransactions = {
                viewModel.dismissDelete()
                onReviewTransactions(dialog.categoryId)
            },
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::dismissDelete,
        )
        if (pickOther) {
            ModalBottomSheet(onDismissRequest = { pickOther = false }) {
                CategoryTreePicker(
                    type = dialog.type,
                    mode = PickerMode.Assign(excludedId = dialog.categoryId),
                    onSelect = { id ->
                        id?.let { viewModel.onTarget(ReassignTarget.To(it)) }
                        pickOther = false
                    },
                    modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun CategoryRow(
    row: PickerRow,
    onToggle: () -> Unit,
    onAddChild: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val c = row.category
    var menu by rememberSaveable { mutableStateOf(false) }
    ListItem(
        leadingContent = { IconBadge(c.icon, c.color, ColorPalette.defaultFor(c.type), size = 36) },
        headlineContent = { Text(c.name) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (row.hasChildren) {
                    IconButton(onClick = onToggle) {
                        Icon(
                            if (row.expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = stringResource(if (row.expanded) R.string.action_collapse else R.string.action_expand),
                        )
                    }
                }
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more_options))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (CategoryFormLogic.canAddSubcategory(row.level)) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.category_add_subcategory)) },
                            onClick = { menu = false; onAddChild() },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_edit)) },
                        onClick = { menu = false; onEdit() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_delete)) },
                        onClick = { menu = false; onDelete() },
                    )
                }
            }
        },
        modifier = Modifier.padding(start = (row.level * 24).dp),
    )
}

@Composable
private fun DeleteCategoryDialog(
    state: DeleteDialogState,
    onUncategorized: () -> Unit,
    onSuggested: () -> Unit,
    onChooseOther: () -> Unit,
    onReviewTransactions: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val impact = state.impact
    val selected = DeleteDialogLogic.selectedOption(state)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.category_delete_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(state.categoryName, style = MaterialTheme.typography.titleSmall)
                if (DeleteDialogLogic.nothingToMove(impact)) {
                    Text(stringResource(R.string.category_delete_nothing))
                }
                if (impact.childCount > 0) {
                    Text(pluralStringResource(R.plurals.category_delete_children, impact.childCount, impact.childCount))
                }
                if (impact.scheduleCount > 0) {
                    Text(pluralStringResource(R.plurals.category_delete_schedules, impact.scheduleCount, impact.scheduleCount))
                }
                if (DeleteDialogLogic.needsTarget(impact)) {
                    if (impact.directOperationCount > 0) {
                        Text(
                            pluralStringResource(
                                R.plurals.category_delete_operations, impact.directOperationCount, impact.directOperationCount,
                            ),
                        )
                        Text(
                            stringResource(R.string.category_delete_direct_only_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = onReviewTransactions) {
                            Text(stringResource(R.string.category_delete_review_transactions))
                        }
                    }
                    Text(stringResource(R.string.category_delete_target), style = MaterialTheme.typography.labelMedium)
                    if (impact.suggestedTarget != null && state.suggestedName != null) {
                        TargetOption(state.suggestedName, selected == DeleteTargetOption.SUGGESTED, onSuggested)
                    }
                    TargetOption(
                        stringResource(R.string.picker_uncategorized),
                        selected == DeleteTargetOption.UNCATEGORIZED,
                        onUncategorized,
                    )
                    TargetOption(
                        if (selected == DeleteTargetOption.OTHER && state.targetName != null) {
                            stringResource(R.string.category_delete_target_chosen, state.targetName)
                        } else {
                            stringResource(R.string.category_delete_choose_other)
                        },
                        selected == DeleteTargetOption.OTHER,
                        onChooseOther,
                    )
                }
                if (state.failed) {
                    Text(
                        stringResource(R.string.category_form_error_generic),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_delete)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun TargetOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, modifier = Modifier.padding(start = 12.dp))
    }
}

// ---- Form ----

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryEditScreen(
    onBack: () -> Unit,
    viewModel: CategoryEditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved) onBack() }
    var pickParent by rememberSaveable { mutableStateOf(false) }

    val rejection = state.parentRejected ?: state.parentCheck.takeIf { it != ParentCheck.OK }
    val parentName = state.parentId?.let { state.tree.category(it)?.name }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (state.isEdit) R.string.category_form_edit_title else R.string.category_form_new_title))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_back))
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::save, enabled = state.canSave) {
                        Text(stringResource(R.string.action_save))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onName,
                label = { Text(stringResource(R.string.category_form_name)) },
                singleLine = true,
                isError = state.nameError == CategoryFormLogic.NameError.TOO_LONG ||
                    (state.nameError == CategoryFormLogic.NameError.EMPTY && state.name.isNotEmpty()),
                supportingText = {
                    when (state.nameError) {
                        CategoryFormLogic.NameError.TOO_LONG -> Text(stringResource(R.string.category_form_error_name_long))
                        CategoryFormLogic.NameError.EMPTY ->
                            if (state.name.isNotEmpty()) Text(stringResource(R.string.category_form_error_name_empty))
                        null -> Unit
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                stringResource(R.string.category_form_type, stringResource(state.type.labelRes())),
                style = MaterialTheme.typography.bodyMedium,
            )

            OutlinedTextField(
                value = parentName ?: stringResource(R.string.picker_no_parent),
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.category_form_parent)) },
                isError = rejection != null,
                supportingText = {
                    rejection?.let {
                        Text(
                            stringResource(
                                when (it) {
                                    ParentCheck.SELF -> R.string.category_form_error_self
                                    ParentCheck.CYCLE -> R.string.category_form_error_cycle
                                    ParentCheck.TYPE_MISMATCH -> R.string.category_form_error_type
                                    ParentCheck.TOO_DEEP -> R.string.category_form_error_deep
                                    ParentCheck.OK -> R.string.category_form_error_generic
                                },
                            ),
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().clickable { pickParent = true },
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.picker_icon), style = MaterialTheme.typography.labelMedium)
                IconPicker(IconKeys.category, state.icon, state.color, viewModel::onIcon)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.picker_color), style = MaterialTheme.typography.labelMedium)
                ColorPicker(state.color, viewModel::onColor)
            }
            if (state.saveFailed) {
                Text(
                    stringResource(R.string.category_form_error_generic),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }

    if (pickParent) {
        ModalBottomSheet(onDismissRequest = { pickParent = false }) {
            CategoryTreePicker(
                type = state.type,
                mode = PickerMode.Parent(movingId = state.categoryId),
                selectedId = state.parentId,
                onSelect = { viewModel.onParent(it); pickParent = false },
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp),
            )
        }
    }
}
