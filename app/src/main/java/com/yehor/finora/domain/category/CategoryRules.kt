package com.yehor.finora.domain.category

import com.yehor.finora.domain.model.CategoryType

/** The single place where parent validity is decided. Repository, form and picker all call it. */
object CategoryRules {
    const val MAX_DEPTH = 3

    enum class ParentCheck { OK, SELF, CYCLE, TYPE_MISMATCH, TOO_DEEP }

    /**
     * [id] is null when creating. A null [newParentId] means top level, which is always valid.
     * A move is valid only if `depth(newParent) + heightOf(moved) <= MAX_DEPTH`; a new category has height 1.
     */
    fun checkParent(tree: CategoryTree, id: Long?, type: CategoryType, newParentId: Long?): ParentCheck {
        if (newParentId == null) return ParentCheck.OK
        if (id != null && newParentId == id) return ParentCheck.SELF
        val parent = tree.category(newParentId) ?: return ParentCheck.TYPE_MISMATCH
        if (parent.type != type) return ParentCheck.TYPE_MISMATCH
        if (id != null && newParentId in tree.descendantIds(id)) return ParentCheck.CYCLE
        val movedHeight = if (id == null) 1 else maxOf(tree.heightOf(id), 1)
        if (tree.depthOf(newParentId) + movedHeight > MAX_DEPTH) return ParentCheck.TOO_DEEP
        return ParentCheck.OK
    }

    /** Every category that may be offered as the parent of [id], or of a new category of [type] when [id] is null. */
    fun validParents(tree: CategoryTree, id: Long?, type: CategoryType): Set<Long> {
        val candidates = tree.rootsOf(type).flatMap { flatten(it) }
        return candidates
            .filter { checkParent(tree, id, type, it.id) == ParentCheck.OK }
            .map { it.id }
            .toSet()
    }

    private fun flatten(node: CategoryNode): List<Category> {
        val out = ArrayList<Category>()
        val stack = ArrayDeque<CategoryNode>().apply { add(node) }
        while (stack.isNotEmpty()) {
            val n = stack.removeLast()
            out += n.category
            stack.addAll(n.children)
        }
        return out
    }
}
