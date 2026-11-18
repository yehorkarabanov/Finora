package com.yehor.finora.domain.category

import com.yehor.finora.domain.model.CategoryType

/**
 * Read-only forest built from a flat list. Pure Kotlin and tolerant of corrupt input: a missing
 * parent makes the category a root, and a parent cycle is cut at one link, so construction and
 * every query terminate on any data. The invariant checker is what reports such data.
 */
class CategoryTree(categories: List<Category>) {

    private val byId: Map<Long, Category> = categories.associateBy { it.id }

    /** Parent id after repairing missing parents and cycles. */
    private val effectiveParent: Map<Long, Long?> = repairParents(categories, byId)

    private val childrenIds: Map<Long, List<Long>> = categories
        .filter { effectiveParent[it.id] != null }
        .groupBy({ effectiveParent.getValue(it.id)!! }, { it.id })

    private val nodes: Map<Long, CategoryNode> = buildNodes(categories)

    val roots: List<CategoryNode> = categories
        .filter { effectiveParent[it.id] == null }
        .map { nodes.getValue(it.id) }

    fun rootsOf(type: CategoryType): List<CategoryNode> = roots.filter { it.category.type == type }

    fun node(id: Long): CategoryNode? = nodes[id]

    fun category(id: Long): Category? = byId[id]

    /** Every descendant of [id], excluding [id] itself. Empty for an unknown id. */
    fun descendantIds(id: Long): Set<Long> {
        val result = LinkedHashSet<Long>()
        val stack = ArrayDeque(childrenIds[id].orEmpty())
        while (stack.isNotEmpty()) {
            val next = stack.removeLast()
            if (result.add(next)) stack.addAll(childrenIds[next].orEmpty())
        }
        return result
    }

    /** Ancestors of [id] ordered from the root down to the direct parent, excluding [id]. */
    fun ancestorsOf(id: Long): List<Category> {
        val result = ArrayDeque<Category>()
        var current = effectiveParent[id]
        while (current != null) {
            result.addFirst(byId.getValue(current))
            current = effectiveParent[current]
        }
        return result.toList()
    }

    /** Root = 1. Unknown id = 0. */
    fun depthOf(id: Long): Int = if (id in byId) ancestorsOf(id).size + 1 else 0

    /** Leaf = 1, counting the node's own level. Unknown id = 0. */
    fun heightOf(id: Long): Int {
        if (id !in byId) return 0
        return 1 + (childrenIds[id].orEmpty().maxOfOrNull { heightOf(it) } ?: 0)
    }

    private fun buildNodes(categories: List<Category>): Map<Long, CategoryNode> {
        val built = HashMap<Long, CategoryNode>()
        // Iterative post-order so deep corrupt chains cannot overflow the stack.
        val order = ArrayList<Long>()
        val stack = ArrayDeque(categories.filter { effectiveParent[it.id] == null }.map { it.id })
        while (stack.isNotEmpty()) {
            val id = stack.removeLast()
            order += id
            stack.addAll(childrenIds[id].orEmpty())
        }
        for (id in order.asReversed()) {
            built[id] = CategoryNode(
                byId.getValue(id),
                childrenIds[id].orEmpty().map { built.getValue(it) },
            )
        }
        return built
    }

    private companion object {
        fun repairParents(categories: List<Category>, byId: Map<Long, Category>): Map<Long, Long?> {
            val parent = HashMap<Long, Long?>()
            categories.forEach { c ->
                parent[c.id] = c.parentId?.takeIf { it in byId && it != c.id }
            }
            // Walk up from every node; a revisit inside one walk is a cycle, cut at the link just followed.
            val settled = HashSet<Long>()
            categories.forEach { start ->
                val path = HashSet<Long>()
                var current: Long? = start.id
                while (current != null && current !in settled) {
                    if (!path.add(current)) {
                        // [current] closes the cycle: detach it from its parent.
                        parent[current] = null
                        break
                    }
                    current = parent[current]
                }
                settled += path
            }
            return parent
        }
    }
}
