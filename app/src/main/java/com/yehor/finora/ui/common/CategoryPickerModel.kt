package com.yehor.finora.ui.common

import com.yehor.finora.domain.category.Category
import com.yehor.finora.domain.category.CategoryNode
import com.yehor.finora.domain.category.CategoryRules
import com.yehor.finora.domain.category.CategoryTree
import com.yehor.finora.domain.model.CategoryType

sealed interface PickerMode {
    /**
     * Any category of the type can be selected, including parents with children. [excludedId] stays
     * visible, to keep the hierarchy readable, but cannot be selected; its children can.
     */
    data class Assign(val excludedId: Long? = null) : PickerMode

    /** Only [CategoryRules.validParents]; [movingId] is null when creating. Other categories are hidden. */
    data class Parent(val movingId: Long? = null) : PickerMode
}

data class PickerRow(
    val category: Category,
    /** 0 = top level. Always 0 while searching. */
    val level: Int,
    val hasChildren: Boolean,
    val expanded: Boolean,
    val selectable: Boolean,
    /** "Food › Groceries", used to tell equal names apart in search results. */
    val path: String,
)

/** Pure flatten, expand, search and filter logic of the category tree picker. */
object CategoryPickerModel {

    fun rows(
        tree: CategoryTree,
        type: CategoryType,
        mode: PickerMode,
        expanded: Set<Long>,
        query: String,
    ): List<PickerRow> {
        val visible = visibleIds(tree, type, mode)
        val selectable = selectableIds(mode, visible)
        val needle = query.trim()
        val out = ArrayList<PickerRow>()

        if (needle.isNotEmpty()) {
            fun search(node: CategoryNode) {
                val c = node.category
                if (c.id in visible) {
                    if (c.name.contains(needle, ignoreCase = true)) {
                        out += row(tree, c, 0, node.children, expanded = false, c.id in selectable, visible)
                    }
                    node.children.forEach(::search)
                }
            }
            tree.rootsOf(type).forEach(::search)
            return out
        }

        fun walk(node: CategoryNode, level: Int) {
            val c = node.category
            if (c.id !in visible) return
            val isExpanded = c.id in expanded
            out += row(tree, c, level, node.children, isExpanded, c.id in selectable, visible)
            if (isExpanded) node.children.forEach { walk(it, level + 1) }
        }
        tree.rootsOf(type).forEach { walk(it, 0) }
        return out
    }

    private fun row(
        tree: CategoryTree,
        c: Category,
        level: Int,
        children: List<CategoryNode>,
        expanded: Boolean,
        selectable: Boolean,
        visible: Set<Long>,
    ) = PickerRow(
        category = c,
        level = level,
        hasChildren = children.any { it.category.id in visible },
        expanded = expanded,
        selectable = selectable,
        path = (tree.ancestorsOf(c.id).map { it.name } + c.name).joinToString(" › "),
    )

    private fun allIds(tree: CategoryTree, type: CategoryType): Set<Long> {
        val ids = LinkedHashSet<Long>()
        fun collect(n: CategoryNode) {
            ids += n.category.id
            n.children.forEach(::collect)
        }
        tree.rootsOf(type).forEach(::collect)
        return ids
    }

    private fun visibleIds(tree: CategoryTree, type: CategoryType, mode: PickerMode): Set<Long> = when (mode) {
        is PickerMode.Assign -> allIds(tree, type)
        is PickerMode.Parent -> CategoryRules.validParents(tree, mode.movingId, type)
    }

    private fun selectableIds(mode: PickerMode, visible: Set<Long>): Set<Long> = when (mode) {
        is PickerMode.Assign -> visible - setOfNotNull(mode.excludedId)
        is PickerMode.Parent -> visible
    }
}
