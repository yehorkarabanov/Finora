package com.yehor.finora.domain.category

import com.yehor.finora.domain.money.MoneyOverflowException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertNull
import org.junit.Test

class CategoryRollupTest {

    private fun direct(id: Long?, code: String, total: Long, count: Int = 1) =
        CategoryDirectTotal(id, code, total, count)

    @Test
    fun prdExampleFoodEqualsChildrenPlusDirect() {
        // Food(1) 100 direct, Groceries(2) 700, Restaurants(3) 450.
        val tree = CategoryTree(listOf(cat(1), cat(2, 1), cat(3, 1)))
        val result = CategoryRollup.rollUp(
            tree,
            listOf(direct(1, "PLN", 100), direct(2, "PLN", 700), direct(3, "PLN", 450)),
        )
        assertEquals(1250L, result.totals.getValue(1)["PLN"])
        assertEquals(700L, result.totals.getValue(2)["PLN"])
        assertEquals(450L, result.totals.getValue(3)["PLN"])
        assertEquals(3, result.counts[1])
    }

    @Test
    fun threeLevelTreeRollsUpToEveryAncestor() {
        val tree = CategoryTree(listOf(cat(1), cat(2, 1), cat(3, 2)))
        val result = CategoryRollup.rollUp(
            tree,
            listOf(direct(1, "USD", 10, 1), direct(2, "USD", 20, 2), direct(3, "USD", 30, 3)),
        )
        assertEquals(60L, result.totals.getValue(1)["USD"])
        assertEquals(50L, result.totals.getValue(2)["USD"])
        assertEquals(30L, result.totals.getValue(3)["USD"])
        assertEquals(6, result.counts[1])
        assertEquals(5, result.counts[2])
        assertEquals(3, result.counts[3])
    }

    @Test
    fun currenciesStayApart() {
        val tree = CategoryTree(listOf(cat(1), cat(2, 1)))
        val result = CategoryRollup.rollUp(
            tree,
            listOf(direct(1, "PLN", 100), direct(2, "PLN", 50), direct(2, "USD", 7), direct(1, "USD", 3)),
        )
        assertEquals(mapOf("PLN" to 150L, "USD" to 10L), result.totals.getValue(1))
        assertEquals(mapOf("PLN" to 50L, "USD" to 7L), result.totals.getValue(2))
    }

    @Test
    fun uncategorizedIsItsOwnBucket() {
        val tree = CategoryTree(listOf(cat(1)))
        val result = CategoryRollup.rollUp(
            tree,
            listOf(direct(null, "PLN", -40, 2), direct(1, "PLN", -10)),
        )
        assertEquals(-40L, result.totals.getValue(null)["PLN"])
        assertEquals(2, result.counts[null])
        assertEquals(-10L, result.totals.getValue(1)["PLN"])
    }

    @Test
    fun categoryWithoutDataHasEmptyTotalsAndZeroCount() {
        val tree = CategoryTree(listOf(cat(1), cat(2, 1)))
        val result = CategoryRollup.rollUp(tree, emptyList())
        assertEquals(emptyMap<String, Long>(), result.totals.getValue(1))
        assertEquals(0, result.counts[1])
        assertNull(result.totals[null])
    }

    @Test
    fun signsAreKept() {
        val tree = CategoryTree(listOf(cat(1), cat(2, 1)))
        val result = CategoryRollup.rollUp(tree, listOf(direct(1, "PLN", -100), direct(2, "PLN", -50)))
        assertEquals(-150L, result.totals.getValue(1)["PLN"])
    }

    @Test
    fun overflowThrows() {
        val tree = CategoryTree(listOf(cat(1), cat(2, 1)))
        assertThrows(MoneyOverflowException::class.java) {
            CategoryRollup.rollUp(
                tree,
                listOf(direct(1, "PLN", Long.MAX_VALUE), direct(2, "PLN", 1)),
            )
        }
        assertThrows(MoneyOverflowException::class.java) {
            CategoryRollup.rollUp(
                tree,
                listOf(direct(2, "PLN", Long.MIN_VALUE), direct(1, "PLN", -1)),
            )
        }
    }

    @Test
    fun separateRootsDoNotMix() {
        val tree = CategoryTree(listOf(cat(1), cat(2)))
        val result = CategoryRollup.rollUp(tree, listOf(direct(1, "PLN", 5), direct(2, "PLN", 7)))
        assertEquals(5L, result.totals.getValue(1)["PLN"])
        assertEquals(7L, result.totals.getValue(2)["PLN"])
    }
}
