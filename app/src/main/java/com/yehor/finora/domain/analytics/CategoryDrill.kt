package com.yehor.finora.domain.analytics

import com.yehor.finora.domain.category.Category
import com.yehor.finora.domain.category.CategoryTree
import com.yehor.finora.domain.model.CategoryType

/**
 * One row of the category drill-down. [categoryId] is null for Uncategorized. A [isDirect] row is
 * the amount booked on [categoryId] itself, excluding its subcategories.
 */
data class DrillRow(
    val categoryId: Long?,
    val category: Category?,
    val cell: Cell,
    val isDirect: Boolean,
    val hasChildren: Boolean,
)

object CategoryDrill {

    /**
     * Rows for the current level. [path] holds the entered categories from the root down; empty
     * means the top level (roots of [type] plus Uncategorized). Inside a category, its children are
     * listed and, when its own direct amount is not zero, one direct row.
     * Rows without an amount are hidden unless they carry a warning. Largest amount first.
     */
    fun rows(
        tree: CategoryTree,
        direct: Map<Long?, Cell>,
        rolled: Map<Long?, Cell>,
        path: List<Long>,
        type: CategoryType,
    ): List<DrillRow> {
        val rows = ArrayList<DrillRow>()
        val current = path.lastOrNull()?.let { tree.node(it) }
        if (current == null) {
            for (node in tree.rootsOf(type)) rows += childRow(node.category, node.children.isNotEmpty(), rolled)
            rolled[null]?.let { rows += DrillRow(null, null, it, isDirect = false, hasChildren = false) }
        } else {
            for (child in current.children) rows += childRow(child.category, child.children.isNotEmpty(), rolled)
            val own = direct[current.category.id]
            if (own != null) {
                rows += DrillRow(current.category.id, current.category, own, isDirect = true, hasChildren = false)
            }
        }
        return rows
            .filter { it.cell.hasAmount || it.cell.partial || it.cell.overflowed }
            .sortedWith(
                compareByDescending<DrillRow> { it.cell.estimate?.amountMinor ?: Long.MIN_VALUE }
                    .thenBy { it.category?.name ?: "" },
            )
    }

    private fun childRow(category: Category, hasChildren: Boolean, rolled: Map<Long?, Cell>) = DrillRow(
        categoryId = category.id,
        category = category,
        cell = rolled[category.id] ?: Cell.NONE,
        isDirect = false,
        hasChildren = hasChildren,
    )
}
