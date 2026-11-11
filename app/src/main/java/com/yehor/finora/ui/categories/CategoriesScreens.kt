package com.yehor.finora.ui.categories

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yehor.finora.R
import com.yehor.finora.data.repo.DeletionImpact
import com.yehor.finora.data.repo.ReassignTarget
import com.yehor.finora.domain.category.Category
import com.yehor.finora.domain.category.CategoryTree
import com.yehor.finora.domain.model.CategoryType
import com.yehor.finora.designsystem.icon.AppIcons
import com.yehor.finora.ui.common.CategoryPickerModel
import com.yehor.finora.ui.common.CategoryTreePicker
import com.yehor.finora.ui.common.ColorPalette
import com.yehor.finora.ui.common.IconBadge
import com.yehor.finora.designsystem.icon.IconSize
import com.yehor.finora.ui.common.PickerMode
import com.yehor.finora.ui.common.PickerRow
import com.yehor.finora.designsystem.theme.PreviewTheme
import com.yehor.finora.designsystem.theme.finance

private val TabOrder = listOf(CategoryType.EXPENSE, CategoryType.INCOME, CategoryType.TRANSFER)

@StringRes
internal fun CategoryType.labelRes(): Int = when (this) {
    CategoryType.EXPENSE -> R.string.categories_tab_expense
    CategoryType.INCOME -> R.string.categories_tab_income
    CategoryType.TRANSFER -> R.string.categories_tab_transfer
}

@Composable
internal fun CategoryType.kindColor(): Color = when (this) {
    CategoryType.EXPENSE -> MaterialTheme.finance.expense
    CategoryType.INCOME -> MaterialTheme.finance.income
    CategoryType.TRANSFER -> MaterialTheme.colorScheme.primary
}

private fun CategoryType.icon(): ImageVector = when (this) {
    CategoryType.EXPENSE -> AppIcons.Expense
    CategoryType.INCOME -> AppIcons.Income
    CategoryType.TRANSFER -> AppIcons.Transfer
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

    CategoriesContent(
        state = state,
        onBack = onBack,
        onType = viewModel::onType,
        onToggle = viewModel::toggle,
        onAdd = onAdd,
        onEdit = onEdit,
        onDelete = viewModel::requestDelete,
    )

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CategoriesContent(
    state: CategoriesUiState,
    onBack: () -> Unit,
    onType: (CategoryType) -> Unit,
    onToggle: (Long) -> Unit,
    onAdd: (CategoryType, Long?) -> Unit,
    onEdit: (Long) -> Unit,
    onDelete: (Long) -> Unit,
) {
    val groups = remember(state.rows) { CategoryListLogic.groups(state.rows) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.categories_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(AppIcons.Back, contentDescription = stringResource(R.string.navigate_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onAdd(state.type, null) }) {
                Icon(AppIcons.Add, contentDescription = stringResource(R.string.categories_add))
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "tabs") {
                CategoryTypeTabs(selected = state.type, onSelect = onType)
            }
            if (groups.isEmpty()) {
                item(key = "empty") {
                    EmptyCategories(onAdd = { onAdd(state.type, null) })
                }
            }
            items(groups, key = { it.first().category.id }) { group ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    group.forEachIndexed { index, row ->
                        CategoryListRow(
                            row = row,
                            subcategories = state.tree.node(row.category.id)?.children?.size ?: 0,
                            shape = categoryGroupShape(index, group.size),
                            onToggle = { onToggle(row.category.id) },
                            onAddChild = { onAdd(row.category.type, row.category.id) },
                            onEdit = { onEdit(row.category.id) },
                            onDelete = { onDelete(row.category.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryTypeTabs(selected: CategoryType, onSelect: (CategoryType) -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TabOrder.forEach { type ->
                TypeTab(type, selected = type == selected, onClick = { onSelect(type) })
            }
        }
    }
}

@Composable
private fun RowScope.TypeTab(type: CategoryType, selected: Boolean, onClick: () -> Unit) {
    val color = type.kindColor()
    Surface(
        selected = selected,
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) color.copy(alpha = 0.16f) else Color.Transparent,
        contentColor = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.weight(1f).height(44.dp),
    ) {
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(type.icon(), contentDescription = null, modifier = Modifier.size(IconSize.Small))
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(type.labelRes()),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun EmptyCategories(onAdd: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            AppIcons.Category,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
        Text(
            stringResource(R.string.categories_empty),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(R.string.category_empty_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onAdd, modifier = Modifier.heightIn(min = 44.dp)) {
            Icon(AppIcons.Add, contentDescription = null, modifier = Modifier.size(IconSize.Small))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.categories_add))
        }
    }
}

@Composable
private fun CategoryListRow(
    row: PickerRow,
    subcategories: Int,
    shape: androidx.compose.ui.graphics.Shape,
    onToggle: () -> Unit,
    onAddChild: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val c = row.category
    var menu by rememberSaveable { mutableStateOf(false) }
    val badge = when (row.level) {
        0 -> 44
        1 -> 36
        else -> 32
    }
    Surface(
        onClick = onEdit,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .heightIn(min = 64.dp)
                .padding(start = 14.dp + (row.level * 20).dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconBadge(c.icon, c.color, ColorPalette.defaultFor(c.type), size = badge)
            Column(Modifier.weight(1f)) {
                Text(c.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subcategories > 0) {
                    Text(
                        pluralStringResource(R.plurals.filter_subcategories_count, subcategories, subcategories),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (row.hasChildren) {
                IconButton(onClick = onToggle, modifier = Modifier.size(44.dp)) {
                    Icon(
                        if (row.expanded) AppIcons.Collapse else AppIcons.Expand,
                        contentDescription = stringResource(if (row.expanded) R.string.action_collapse else R.string.action_expand),
                    )
                }
            }
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(44.dp)) {
                    Icon(AppIcons.More, contentDescription = stringResource(R.string.action_more_options))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (CategoryFormLogic.canAddSubcategory(row.level)) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.category_add_subcategory)) },
                            leadingIcon = { Icon(AppIcons.Add, contentDescription = null) },
                            onClick = { menu = false; onAddChild() },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_edit)) },
                        leadingIcon = { Icon(AppIcons.Edit, contentDescription = null) },
                        onClick = { menu = false; onEdit() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_delete)) },
                        leadingIcon = { Icon(AppIcons.Delete, contentDescription = null) },
                        onClick = { menu = false; onDelete() },
                    )
                }
            }
        }
    }
}

// ---- Delete confirmation ----

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
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = { Text(stringResource(R.string.category_delete_title)) },
        text = {
            DeleteDialogBody(
                state = state,
                onUncategorized = onUncategorized,
                onSuggested = onSuggested,
                onChooseOther = onChooseOther,
                onReviewTransactions = onReviewTransactions,
                modifier = Modifier.verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) { Text(stringResource(R.string.action_delete)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
internal fun DeleteDialogBody(
    state: DeleteDialogState,
    onUncategorized: () -> Unit,
    onSuggested: () -> Unit,
    onChooseOther: () -> Unit,
    onReviewTransactions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val impact = state.impact
    val selected = DeleteDialogLogic.selectedOption(state)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconBadge(state.icon, state.color, ColorPalette.defaultFor(state.type), size = 44)
            Text(state.categoryName, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (DeleteDialogLogic.nothingToMove(impact)) {
                ImpactLine(AppIcons.Success, stringResource(R.string.category_delete_nothing))
            }
            if (impact.directOperationCount > 0) {
                ImpactLine(
                    AppIcons.Transfer,
                    pluralStringResource(R.plurals.category_delete_operations, impact.directOperationCount, impact.directOperationCount),
                )
            }
            if (impact.childCount > 0) {
                ImpactLine(
                    AppIcons.Category,
                    pluralStringResource(R.plurals.category_delete_children, impact.childCount, impact.childCount),
                )
            }
            if (impact.scheduleCount > 0) {
                ImpactLine(
                    AppIcons.Recurring,
                    pluralStringResource(R.plurals.category_delete_schedules, impact.scheduleCount, impact.scheduleCount),
                )
            }
        }
        if (DeleteDialogLogic.needsTarget(impact)) {
            if (impact.directOperationCount > 0) {
                Text(
                    stringResource(R.string.category_delete_direct_only_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onReviewTransactions, modifier = Modifier.heightIn(min = 44.dp)) {
                    Text(stringResource(R.string.category_delete_review_transactions))
                }
            }
            Text(
                stringResource(R.string.category_delete_target),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.semantics { heading() },
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val options = buildList<@Composable (Int, Int) -> Unit> {
                    if (impact.suggestedTarget != null && state.suggestedName != null) {
                        add { i, n ->
                            TargetOption(
                                state.suggestedName, selected == DeleteTargetOption.SUGGESTED, onSuggested,
                                categoryGroupShape(i, n),
                            )
                        }
                    }
                    add { i, n ->
                        TargetOption(
                            stringResource(R.string.picker_uncategorized),
                            selected == DeleteTargetOption.UNCATEGORIZED, onUncategorized, categoryGroupShape(i, n),
                        )
                    }
                    add { i, n ->
                        TargetOption(
                            if (selected == DeleteTargetOption.OTHER && state.targetName != null) {
                                stringResource(R.string.category_delete_target_chosen, state.targetName)
                            } else {
                                stringResource(R.string.category_delete_choose_other)
                            },
                            selected == DeleteTargetOption.OTHER, onChooseOther, categoryGroupShape(i, n),
                        )
                    }
                }
                options.forEachIndexed { index, option -> option(index, options.size) }
            }
        }
        Text(
            stringResource(R.string.category_delete_confirm_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.failed) {
            Text(
                stringResource(R.string.category_form_error_generic),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ImpactLine(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp).size(IconSize.Small),
        )
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun TargetOption(label: String, selected: Boolean, onClick: () -> Unit, shape: androidx.compose.ui.graphics.Shape) {
    Surface(
        shape = shape,
        color = if (selected) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    ) {
        Row(
            Modifier.heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = null)
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp))
        }
    }
}

// ---- Previews ----

private fun previewCategory(id: Long, name: String, icon: String, parent: Long? = null, type: CategoryType = CategoryType.EXPENSE) =
    Category(id, name, icon, null, type, parent, 0)

private fun previewState(empty: Boolean = false): CategoriesUiState {
    val tree = CategoryTree(
        if (empty) {
            emptyList()
        } else {
            listOf(
                previewCategory(1, "Food", "restaurant"),
                previewCategory(2, "Groceries", "shopping_cart", 1),
                previewCategory(3, "Cafes", "coffee", 1),
                previewCategory(4, "Transport", "directions_bus"),
                previewCategory(5, "Home", "home"),
            )
        },
    )
    return CategoriesUiState(
        tree = tree,
        type = CategoryType.EXPENSE,
        rows = CategoryPickerModel.rows(tree, CategoryType.EXPENSE, PickerMode.Assign(), setOf(1L), query = ""),
    )
}

@Composable
private fun CategoriesPreview(dark: Boolean, empty: Boolean) {
    PreviewTheme(dark) {
        CategoriesContent(previewState(empty), {}, {}, {}, { _, _ -> }, {}, {})
    }
}

@Preview(showBackground = true, heightDp = 640, name = "Categories light")
@Composable
private fun CategoriesPreviewLight() = CategoriesPreview(dark = false, empty = false)

@Preview(showBackground = true, backgroundColor = 0xFF0F1412, heightDp = 640, name = "Categories dark")
@Composable
private fun CategoriesPreviewDark() = CategoriesPreview(dark = true, empty = false)

@Preview(showBackground = true, heightDp = 480, name = "Categories empty light")
@Composable
private fun CategoriesEmptyPreviewLight() = CategoriesPreview(dark = false, empty = true)

@Preview(showBackground = true, backgroundColor = 0xFF0F1412, heightDp = 480, name = "Categories empty dark")
@Composable
private fun CategoriesEmptyPreviewDark() = CategoriesPreview(dark = true, empty = true)

@Composable
private fun DeletePreview(dark: Boolean) {
    PreviewTheme(dark) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(28.dp)) {
            DeleteDialogBody(
                state = DeleteDialogState(
                    categoryId = 1, categoryName = "Food", type = CategoryType.EXPENSE,
                    impact = DeletionImpact(directOperationCount = 12, childCount = 2, suggestedTarget = 9, scheduleCount = 1),
                    target = ReassignTarget.To(9), suggestedName = "Living", targetName = "Living",
                    icon = "restaurant", color = null,
                ),
                onUncategorized = {}, onSuggested = {}, onChooseOther = {}, onReviewTransactions = {},
                modifier = Modifier.padding(24.dp),
            )
        }
    }
}

@Preview(showBackground = true, name = "Delete light")
@Composable
private fun DeletePreviewLight() = DeletePreview(dark = false)

@Preview(showBackground = true, backgroundColor = 0xFF0F1412, name = "Delete dark")
@Composable
private fun DeletePreviewDark() = DeletePreview(dark = true)
