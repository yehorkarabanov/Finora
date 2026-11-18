package com.yehor.finora.domain.category

import com.yehor.finora.domain.money.MoneyOverflowException

/** Sum of the stored (signed) amounts of one category's own operations in one currency. [categoryId] null = Uncategorized. */
data class CategoryDirectTotal(
    val categoryId: Long?,
    val currencyCode: String,
    val totalMinor: Long,
    val operationCount: Int,
)

/** Result of [CategoryRollup.rollUp]. */
data class CategoryRollupResult(
    /** Category id (null = Uncategorized) to currency code to rolled-up minor units. Currencies are never mixed. */
    val totals: Map<Long?, Map<String, Long>>,
    /** Category id (null = Uncategorized) to rolled-up operation count. */
    val counts: Map<Long?, Int>,
)

object CategoryRollup {

    /**
     * A category's total is its own direct total plus the totals of all its descendants, per currency.
     * Uncategorized stays a separate bucket under the `null` key. Throws [MoneyOverflowException] on overflow.
     */
    fun rollUp(tree: CategoryTree, directTotals: List<CategoryDirectTotal>): CategoryRollupResult {
        val directByCategory = directTotals.groupBy { it.categoryId }
        val totals = LinkedHashMap<Long?, Map<String, Long>>()
        val counts = LinkedHashMap<Long?, Int>()

        // Categories with data that the tree does not know are kept as their own bucket.
        directByCategory.keys.filter { it != null && tree.category(it) == null }.forEach { id ->
            addInto(totals, counts, id, directByCategory.getValue(id))
        }
        directByCategory[null]?.let { addInto(totals, counts, null, it) }

        for (root in tree.roots) fold(root, directByCategory, totals, counts)
        return CategoryRollupResult(totals, counts)
    }

    private fun addInto(
        totals: MutableMap<Long?, Map<String, Long>>,
        counts: MutableMap<Long?, Int>,
        id: Long?,
        rows: List<CategoryDirectTotal>,
    ) {
        val sums = LinkedHashMap<String, Long>()
        var count = 0
        for (row in rows) {
            sums[row.currencyCode] = add(sums[row.currencyCode] ?: 0L, row.totalMinor)
            count = addCount(count, row.operationCount)
        }
        totals[id] = sums
        counts[id] = count
    }

    /** Iterative post-order: children are folded into their parent. */
    private fun fold(
        root: CategoryNode,
        directByCategory: Map<Long?, List<CategoryDirectTotal>>,
        totals: MutableMap<Long?, Map<String, Long>>,
        counts: MutableMap<Long?, Int>,
    ) {
        val order = ArrayList<CategoryNode>()
        val stack = ArrayDeque<CategoryNode>().apply { add(root) }
        while (stack.isNotEmpty()) {
            val n = stack.removeLast()
            order += n
            stack.addAll(n.children)
        }
        for (node in order.asReversed()) {
            val id = node.category.id
            val sums = LinkedHashMap<String, Long>()
            var count = 0
            directByCategory[id].orEmpty().forEach { row ->
                sums[row.currencyCode] = add(sums[row.currencyCode] ?: 0L, row.totalMinor)
                count = addCount(count, row.operationCount)
            }
            node.children.forEach { child ->
                totals.getValue(child.category.id).forEach { (code, minor) ->
                    sums[code] = add(sums[code] ?: 0L, minor)
                }
                count = addCount(count, counts.getValue(child.category.id))
            }
            totals[id] = sums
            counts[id] = count
        }
    }

    private fun add(a: Long, b: Long): Long = try {
        Math.addExact(a, b)
    } catch (e: ArithmeticException) {
        throw MoneyOverflowException("Category total overflow", e)
    }

    private fun addCount(a: Int, b: Int): Int = try {
        Math.addExact(a, b)
    } catch (e: ArithmeticException) {
        throw MoneyOverflowException("Operation count overflow", e)
    }
}
