package com.yehor.finora.domain.category

import com.yehor.finora.domain.model.CategoryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal fun cat(
    id: Long,
    parentId: Long? = null,
    type: CategoryType = CategoryType.EXPENSE,
    name: String = "c$id",
) = Category(id, name, null, null, type, parentId, 0L)

class CategoryTreeTest {

    // 1 -> 2 -> 3 -> (none); 1 -> 4; 5 root; income root 6
    private val tree = CategoryTree(
        listOf(
            cat(1), cat(2, 1), cat(3, 2), cat(4, 1), cat(5),
            cat(6, type = CategoryType.INCOME),
        ),
    )

    @Test
    fun rootsAreGroupedByType() {
        assertEquals(listOf(1L, 5L), tree.rootsOf(CategoryType.EXPENSE).map { it.category.id })
        assertEquals(listOf(6L), tree.rootsOf(CategoryType.INCOME).map { it.category.id })
        assertTrue(tree.rootsOf(CategoryType.TRANSFER).isEmpty())
        assertEquals(listOf(1L, 5L, 6L), tree.roots.map { it.category.id })
    }

    @Test
    fun childrenAreLinked() {
        assertEquals(listOf(2L, 4L), tree.node(1)!!.children.map { it.category.id })
        assertEquals(listOf(3L), tree.node(2)!!.children.map { it.category.id })
        assertTrue(tree.node(3)!!.children.isEmpty())
        assertNull(tree.node(99))
    }

    @Test
    fun descendantIdsExcludeSelf() {
        assertEquals(setOf(2L, 3L, 4L), tree.descendantIds(1))
        assertEquals(setOf(3L), tree.descendantIds(2))
        assertTrue(tree.descendantIds(3).isEmpty())
        assertTrue(tree.descendantIds(99).isEmpty())
    }

    @Test
    fun ancestorsAreOrderedRootFirst() {
        assertEquals(listOf(1L, 2L), tree.ancestorsOf(3).map { it.id })
        assertEquals(listOf(1L), tree.ancestorsOf(4).map { it.id })
        assertTrue(tree.ancestorsOf(1).isEmpty())
    }

    @Test
    fun depthAndHeight() {
        assertEquals(1, tree.depthOf(1))
        assertEquals(2, tree.depthOf(2))
        assertEquals(3, tree.depthOf(3))
        assertEquals(3, tree.heightOf(1))
        assertEquals(2, tree.heightOf(2))
        assertEquals(1, tree.heightOf(3))
        assertEquals(1, tree.heightOf(5))
        assertEquals(0, tree.depthOf(99))
        assertEquals(0, tree.heightOf(99))
    }

    @Test
    fun missingParentMakesCategoryARoot() {
        val t = CategoryTree(listOf(cat(1, parentId = 42), cat(2, 1)))
        assertEquals(listOf(1L), t.roots.map { it.category.id })
        assertEquals(1, t.depthOf(1))
        assertEquals(2, t.depthOf(2))
    }

    @Test
    fun selfParentIsTreatedAsRoot() {
        val t = CategoryTree(listOf(cat(1, parentId = 1)))
        assertEquals(listOf(1L), t.roots.map { it.category.id })
        assertEquals(1, t.depthOf(1))
        assertEquals(1, t.heightOf(1))
    }

    @Test
    fun cyclicInputTerminatesAndStaysConsistent() {
        val t = CategoryTree(listOf(cat(1, 2), cat(2, 1), cat(3, 2)))
        assertEquals(1, t.roots.size)
        // Every category is reachable exactly once from the roots.
        fun count(n: CategoryNode): Int = 1 + n.children.sumOf { count(it) }
        assertEquals(3, t.roots.sumOf { count(it) })
        listOf(1L, 2L, 3L).forEach {
            assertTrue(t.depthOf(it) in 1..3)
            assertTrue(t.heightOf(it) in 1..3)
            assertNotNull(t.node(it))
        }
    }

    @Test
    fun overDeepChainIsStillBuilt() {
        val chain = (1L..6L).map { cat(it, if (it == 1L) null else it - 1) }
        val t = CategoryTree(chain)
        assertEquals(6, t.depthOf(6))
        assertEquals(6, t.heightOf(1))
        assertEquals(setOf(2L, 3L, 4L, 5L, 6L), t.descendantIds(1))
    }

    @Test
    fun longChainDoesNotOverflowTheStack() {
        val chain = (1L..5000L).map { cat(it, if (it == 1L) null else it - 1) }
        val t = CategoryTree(chain)
        assertEquals(5000, t.depthOf(5000))
        assertEquals(1, t.roots.size)
    }
}
