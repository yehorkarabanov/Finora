package com.yehor.finora.ui.operations

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yehor.finora.R
import com.yehor.finora.domain.category.Category
import com.yehor.finora.domain.category.CategoryNode
import com.yehor.finora.domain.category.CategoryTree
import com.yehor.finora.domain.model.CategoryType
import com.yehor.finora.domain.model.OperationType
import com.yehor.finora.domain.operation.TransactionFilter
import com.yehor.finora.ui.common.ColorPalette
import com.yehor.finora.ui.common.IconBadge
import com.yehor.finora.ui.theme.finance


// ---- Selection rules (pure, so they can be unit tested) ----

/** Turns the global "all exact" flag into per-category flags, so single categories can be edited. */
internal fun TransactionFilter.withPerCategoryExact(): TransactionFilter =
    if (!categoryExact) this else copy(exactCategoryIds = categoryIds, categoryExact = false)

/**
 * Selecting a category includes its subcategories, and drops descendants that were picked one by one
 * (they are now covered). Selecting it again removes it.
 */
internal fun TransactionFilter.toggleCategory(id: Long, tree: CategoryTree): TransactionFilter {
    val f = withPerCategoryExact()
    return if (id in f.categoryIds) {
        f.copy(categoryIds = f.categoryIds - id, exactCategoryIds = f.exactCategoryIds - id)
    } else {
        val descendants = tree.descendantIds(id)
        f.copy(categoryIds = f.categoryIds - descendants + id, exactCategoryIds = f.exactCategoryIds - descendants - id)
    }
}

/** Switches a selected parent between "with subcategories" and "direct only". */
internal fun TransactionFilter.toggleDirectOnly(id: Long): TransactionFilter {
    val f = withPerCategoryExact()
    if (id !in f.categoryIds) return f
    return f.copy(exactCategoryIds = if (id in f.exactCategoryIds) f.exactCategoryIds - id else f.exactCategoryIds + id)
}

/** Categories that are implicitly included because an ancestor is selected with its subcategories. */
internal fun TransactionFilter.coveredCategoryIds(tree: CategoryTree): Set<Long> =
    categoryIds
        .filter { !categoryExact && it !in exactCategoryIds }
        .flatMapTo(HashSet()) { tree.descendantIds(it) }

// ---- Sheet ----

/**
 * Category filter in the design-system style: Expense / Income tabs, one connected group of rows with
 * category badges, expandable parents, and changes applied immediately (like the account sheet).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryFilterSheet(
    filter: TransactionFilter,
    categories: List<Category>,
    onFilterChange: (TransactionFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    val tree = remember(categories) { CategoryTree(categories) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val hasIncome = tree.rootsOf(CategoryType.INCOME).isNotEmpty()
    val hasExpense = tree.rootsOf(CategoryType.EXPENSE).isNotEmpty()
    var tab by rememberSaveable {
        mutableStateOf(
            if (!hasExpense || (filter.types == setOf(OperationType.INCOME) && hasIncome)) CategoryType.INCOME else CategoryType.EXPENSE,
        )
    }
    var query by rememberSaveable { mutableStateOf("") }
    // Parents open when something under them is selected.
    val expanded = remember {
        mutableStateListOf<Long>().apply {
            filter.categoryIds.forEach { id -> tree.ancestorsOf(id).forEach { if (it.id !in this) add(it.id) } }
        }
    }
    val covered = filter.coveredCategoryIds(tree)
    val selectedCount = filter.categoryIds.size + if (filter.includeUncategorized) 1 else 0

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(Modifier.navigationBarsPadding()) {
            FilterSheetHeader(
                title = stringResource(R.string.filter_categories),
                showClear = selectedCount > 0,
                onClear = {
                    onFilterChange(
                        filter.copy(
                            categoryIds = emptySet(),
                            exactCategoryIds = emptySet(),
                            categoryExact = false,
                            includeUncategorized = false,
                        ),
                    )
                },
                onDone = onDismiss,
            )

            if (hasExpense && hasIncome) {
                KindTabs(
                    selected = tab,
                    expenseCount = filter.categoryIds.count { tree.category(it)?.type == CategoryType.EXPENSE },
                    incomeCount = filter.categoryIds.count { tree.category(it)?.type == CategoryType.INCOME },
                    onSelect = { tab = it },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            if (categories.size > FILTER_SEARCH_THRESHOLD) {
                FilterSearchField(
                    query,
                    { query = it },
                    stringResource(R.string.filter_categories_search),
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            val rows = remember(tree, tab, query, expanded.toList()) { visibleRows(tree, tab, query, expanded) }
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (query.isBlank()) {
                    item(key = "uncategorized") {
                        UncategorizedRow(
                            checked = filter.includeUncategorized,
                            onToggle = { onFilterChange(filter.copy(includeUncategorized = !filter.includeUncategorized)) },
                        )
                    }
                    item(key = "gap") { Box(Modifier.height(10.dp)) }
                }
                rows.forEachIndexed { index, row ->
                    item(key = row.node.category.id) {
                        val id = row.node.category.id
                        val isSelected = id in filter.categoryIds
                        CategoryRow(
                            row = row,
                            shape = filterGroupShape(index, rows.size),
                            selected = isSelected,
                            covered = id in covered,
                            directOnly = isSelected && (filter.categoryExact || id in filter.exactCategoryIds),
                            expanded = id in expanded,
                            onToggle = { onFilterChange(filter.toggleCategory(id, tree)) },
                            onToggleDirectOnly = { onFilterChange(filter.toggleDirectOnly(id)) },
                            onToggleExpanded = { if (id in expanded) expanded.remove(id) else expanded.add(id) },
                        )
                    }
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
        }
    }
}

private data class VisibleRow(val node: CategoryNode, val depth: Int, val parentName: String?)

/** The tree flattened to what is on screen. A search shows matches flat, with the parent name. */
private fun visibleRows(tree: CategoryTree, type: CategoryType, query: String, expanded: List<Long>): List<VisibleRow> {
    val result = mutableListOf<VisibleRow>()
    val needle = query.trim()
    fun walk(node: CategoryNode, depth: Int, parent: String?) {
        if (needle.isEmpty()) {
            result += VisibleRow(node, depth, null)
            if (node.category.id in expanded) node.children.forEach { walk(it, depth + 1, node.category.name) }
        } else {
            if (node.category.name.contains(needle, ignoreCase = true)) result += VisibleRow(node, 0, parent)
            node.children.forEach { walk(it, depth + 1, node.category.name) }
        }
    }
    tree.rootsOf(type).forEach { walk(it, 0, null) }
    return result
}

@Composable
private fun KindTabs(
    selected: CategoryType,
    expenseCount: Int,
    incomeCount: Int,
    onSelect: (CategoryType) -> Unit,
    modifier: Modifier = Modifier,
) {
    @Composable
    fun option(type: CategoryType, label: Int, count: Int, color: Color): SegmentOption {
        val text = stringResource(label)
        return SegmentOption(if (count > 0) "$text · $count" else text, type == selected, color) { onSelect(type) }
    }
    SegmentedTrack(
        listOf(
            option(CategoryType.EXPENSE, R.string.filter_type_expenses, expenseCount, MaterialTheme.finance.expense),
            option(CategoryType.INCOME, R.string.filter_type_income, incomeCount, MaterialTheme.finance.income),
        ),
        modifier,
    )
}

@Composable
private fun UncategorizedRow(checked: Boolean, onToggle: () -> Unit) {
    Surface(shape = RoundedCornerShape(FilterRowRadius), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .clickable(role = Role.Checkbox, onClick = onToggle)
                .heightIn(min = 60.dp)
                .padding(start = 12.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(36.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.HelpOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                stringResource(R.string.picker_uncategorized),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f).padding(start = 12.dp),
            )
            Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(end = 4.dp))
        }
    }
}

@Composable
private fun CategoryRow(
    row: VisibleRow,
    shape: Shape,
    selected: Boolean,
    covered: Boolean,
    directOnly: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    onToggleDirectOnly: () -> Unit,
    onToggleExpanded: () -> Unit,
) {
    val category = row.node.category
    val childCount = row.node.children.size
    val hasChildren = childCount > 0
    val isChild = row.depth > 0
    Surface(
        shape = shape,
        // Subcategories sit one tone up, so the hierarchy reads without lines.
        color = if (isChild) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .clickable(enabled = !covered, role = Role.Checkbox, onClick = onToggle)
                .heightIn(min = if (isChild) 52.dp else 60.dp)
                .padding(start = (12 + row.depth * 24).dp, end = 4.dp, top = 6.dp, bottom = 6.dp)
                .alpha(if (covered) 0.6f else 1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(
                icon = category.icon,
                color = category.color,
                fallbackColor = ColorPalette.defaultFor(category.type),
                size = if (isChild) 28 else 36,
            )
            Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    category.name,
                    style = if (isChild) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                when {
                    row.parentName != null -> SecondaryLine(row.parentName)
                    hasChildren && selected -> ScopePill(directOnly = directOnly, onClick = onToggleDirectOnly)
                    hasChildren -> SecondaryLine(pluralStringResource(R.plurals.filter_subcategories_count, childCount, childCount))
                }
            }
            Checkbox(checked = selected || covered, enabled = !covered, onCheckedChange = null)
            if (hasChildren && row.parentName == null) {
                IconButton(onClick = onToggleExpanded, modifier = Modifier.size(44.dp)) {
                    Icon(
                        if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = stringResource(
                            if (expanded) R.string.filter_categories_collapse else R.string.filter_categories_expand,
                        ),
                    )
                }
            } else {
                Box(Modifier.size(width = 8.dp, height = 44.dp))
            }
        }
    }
}

@Composable
private fun SecondaryLine(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** "With subcategories ▾" / "Direct only ▾": one tap flips how a selected parent matches. */
@Composable
private fun ScopePill(directOnly: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.height(28.dp),
    ) {
        Row(Modifier.padding(start = 10.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(if (directOnly) R.string.filter_category_stage_direct else R.string.filter_category_stage_recursive),
                style = MaterialTheme.typography.labelMedium,
            )
            Icon(Icons.Filled.ExpandMore, contentDescription = null, modifier = Modifier.size(16.dp))
        }
    }
}
