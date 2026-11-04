package com.yehor.finora.domain.category

import com.yehor.finora.domain.category.CategoryRules.ParentCheck
import com.yehor.finora.domain.model.CategoryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryRulesTest {
    private val expense = CategoryType.EXPENSE

    // Level 1: 1, 10, 20(leaf root), 30 (root with child 31 and grandchild 32)
    // Level 2: 2 (under 1), 11 (under 10), 31 (under 30)
    // Level 3: 3 (under 2), 32 (under 31)
    // Other types: 100 income root, 101 income child
    private val tree = CategoryTree(
        listOf(
            cat(1), cat(2, 1), cat(3, 2),
            cat(10), cat(11, 10),
            cat(20),
            cat(30), cat(31, 30), cat(32, 31),
            cat(100, type = CategoryType.INCOME), cat(101, 100, CategoryType.INCOME),
        ),
    )

    private fun check(id: Long?, parent: Long?, type: CategoryType = expense) =
        CategoryRules.checkParent(tree, id, type, parent)

    @Test
    fun topLevelIsAlwaysOk() {
        assertEquals(ParentCheck.OK, check(null, null))
        assertEquals(ParentCheck.OK, check(3, null))
        assertEquals(ParentCheck.OK, check(30, null))
    }

    @Test
    fun createRootChildAndGrandchildAreOk() {
        assertEquals(ParentCheck.OK, check(null, null))
        assertEquals(ParentCheck.OK, check(null, 20)) // child, level 2
        assertEquals(ParentCheck.OK, check(null, 11)) // grandchild, level 3
    }

    @Test
    fun createUnderLevelThreeIsTooDeep() {
        assertEquals(ParentCheck.TOO_DEEP, check(null, 3))
        assertEquals(ParentCheck.TOO_DEEP, check(null, 32))
    }

    @Test
    fun selfIsRejected() {
        assertEquals(ParentCheck.SELF, check(2, 2))
    }

    @Test
    fun cycleIsRejectedForOwnChildAndGrandchild() {
        assertEquals(ParentCheck.CYCLE, check(1, 2))
        assertEquals(ParentCheck.CYCLE, check(1, 3))
    }

    @Test
    fun typeMismatchIsRejected() {
        assertEquals(ParentCheck.TYPE_MISMATCH, check(20, 100))
        assertEquals(ParentCheck.TYPE_MISMATCH, check(null, 100))
        assertEquals(ParentCheck.TYPE_MISMATCH, CategoryRules.checkParent(tree, null, CategoryType.INCOME, 1))
    }

    @Test
    fun unknownParentIsRejected() {
        assertEquals(ParentCheck.TYPE_MISMATCH, check(20, 999))
    }

    @Test
    fun leafMoveDepth() {
        assertEquals(ParentCheck.OK, check(20, 11)) // under level 2 -> level 3
        assertEquals(ParentCheck.TOO_DEEP, check(20, 3)) // under level 3 -> level 4
    }

    @Test
    fun heightTwoSubtreeDepth() {
        // Root 10 with child 11 (height 2).
        assertEquals(ParentCheck.OK, check(10, 20)) // under a root: child ends at level 3
        assertEquals(ParentCheck.TOO_DEEP, check(10, 2)) // under a level-2 category: child at level 4
    }

    @Test
    fun heightThreeSubtreeCanOnlyGoToTopLevel() {
        assertEquals(ParentCheck.TOO_DEEP, check(30, 20))
        assertEquals(ParentCheck.TOO_DEEP, check(30, 11))
        assertEquals(ParentCheck.TOO_DEEP, check(30, 1))
        assertEquals(ParentCheck.OK, check(30, null))
    }

    @Test
    fun movingToCurrentParentIsOk() {
        assertEquals(ParentCheck.OK, check(3, 2))
        assertEquals(ParentCheck.OK, check(31, 30))
    }

    @Test
    fun validParentsForNewCategory() {
        val valid = CategoryRules.validParents(tree, null, expense)
        assertEquals(setOf(1L, 2L, 10L, 11L, 20L, 30L, 31L), valid)
    }

    @Test
    fun validParentsForMovedLeaf() {
        val valid = CategoryRules.validParents(tree, 20, expense)
        assertEquals(setOf(1L, 2L, 10L, 11L, 30L, 31L), valid)
    }

    @Test
    fun validParentsForHeightTwoSubtree() {
        // Root 10 (height 2): only roots other than itself; not its own child.
        val valid = CategoryRules.validParents(tree, 10, expense)
        assertEquals(setOf(1L, 20L, 30L), valid)
    }

    @Test
    fun validParentsForHeightThreeSubtreeIsEmpty() {
        assertTrue(CategoryRules.validParents(tree, 30, expense).isEmpty())
    }

    @Test
    fun validParentsNeverContainForbiddenCategories() {
        val ids = listOf<Long?>(null, 1, 2, 3, 10, 11, 20, 30, 31, 32)
        for (id in ids) {
            val valid = CategoryRules.validParents(tree, id, expense)
            val level3 = listOf(3L, 32L)
            assertTrue("level 3 for $id", valid.none { it in level3 })
            assertTrue("other type for $id", valid.none { it in setOf(100L, 101L) })
            if (id != null) {
                assertFalse("self for $id", id in valid)
                assertTrue("descendants for $id", valid.none { it in tree.descendantIds(id) })
            }
            valid.forEach { assertEquals(ParentCheck.OK, check(id, it)) }
        }
    }

    @Test
    fun validParentsRespectType() {
        val valid = CategoryRules.validParents(tree, null, CategoryType.INCOME)
        assertEquals(setOf(100L, 101L), valid)
        assertTrue(CategoryRules.validParents(tree, null, CategoryType.TRANSFER).isEmpty())
    }
}
