package com.yehor.finora.domain.analytics

import com.yehor.finora.domain.category.CategoryNode
import com.yehor.finora.domain.category.CategoryTree

/** Additive roll-up: a node is its direct cell plus all child nodes, added with exact arithmetic. */
object CategoryEstimateRollup {

    /** The `null` key is Uncategorized. A category the tree does not know keeps its own bucket. */
    fun rollUp(tree: CategoryTree, direct: Map<Long?, Cell>): Map<Long?, Cell> {
        val result = LinkedHashMap<Long?, Cell>()

        direct.keys.filter { it != null && tree.category(it) == null }.forEach { id ->
            result[id] = direct.getValue(id)
        }
        direct[null]?.let { result[null] = it }

        for (root in tree.roots) fold(root, direct, result)
        return result
    }

    /** Iterative post-order, so a deep corrupt chain cannot overflow the stack. */
    private fun fold(root: CategoryNode, direct: Map<Long?, Cell>, out: MutableMap<Long?, Cell>) {
        val order = ArrayList<CategoryNode>()
        val stack = ArrayDeque<CategoryNode>().apply { add(root) }
        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            order += node
            stack.addAll(node.children)
        }
        for (node in order.asReversed()) {
            var cell = direct[node.category.id] ?: Cell.NONE
            for (child in node.children) cell += out.getValue(child.category.id)
            out[node.category.id] = cell
        }
    }
}
