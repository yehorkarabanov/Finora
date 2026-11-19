package com.yehor.finora.ui.operations

import com.yehor.finora.domain.category.Category
import com.yehor.finora.domain.category.CategoryTree
import com.yehor.finora.domain.model.CategoryType
import com.yehor.finora.domain.operation.TransactionFilter
import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryFilterSelectionTest {

    private fun cat(id: Long, parentId: Long? = null) =
        Category(id, "c$id", null, null, CategoryType.EXPENSE, parentId, 0L)

    // 1 -> 2 -> 3; 1 -> 4; 5 root
    private val tree = CategoryTree(listOf(cat(1), cat(2, 1), cat(3, 2), cat(4, 1), cat(5)))

    @Test
    fun selectingParentIncludesSubcategoriesAndDropsPickedDescendants() {
        val start = TransactionFilter(categoryIds = setOf(3, 5), exactCategoryIds = setOf(3))
        val result = start.toggleCategory(1, tree)
        assertEquals(setOf(1L, 5L), result.categoryIds)
        assertEquals(emptySet<Long>(), result.exactCategoryIds)
        assertEquals(setOf(2L, 3L, 4L), result.coveredCategoryIds(tree))
    }

    @Test
    fun selectingAgainRemovesIt() {
        val result = TransactionFilter(categoryIds = setOf(1), exactCategoryIds = setOf(1)).toggleCategory(1, tree)
        assertEquals(emptySet<Long>(), result.categoryIds)
        assertEquals(emptySet<Long>(), result.exactCategoryIds)
    }

    @Test
    fun directOnlyStopsCoveringDescendants() {
        val result = TransactionFilter(categoryIds = setOf(1)).toggleDirectOnly(1)
        assertEquals(setOf(1L), result.exactCategoryIds)
        assertEquals(emptySet<Long>(), result.coveredCategoryIds(tree))
        assertEquals(emptySet<Long>(), result.toggleDirectOnly(1).exactCategoryIds)
    }

    @Test
    fun directOnlyIgnoresUnselectedCategory() {
        val start = TransactionFilter(categoryIds = setOf(5))
        assertEquals(start, start.toggleDirectOnly(1))
    }

    @Test
    fun globalExactFlagBecomesPerCategoryBeforeEditing() {
        val start = TransactionFilter(categoryIds = setOf(1, 5), categoryExact = true)
        val result = start.toggleCategory(5, tree)
        assertEquals(false, result.categoryExact)
        assertEquals(setOf(1L), result.categoryIds)
        assertEquals(setOf(1L), result.exactCategoryIds)
    }

    @Test
    fun resetSectionsKeepsOtherDimensions() {
        val start = TransactionFilter(categoryIds = setOf(1), accountIds = setOf(7), from = 10, text = "milk")
        val result = start.resetSections(setOf(FilterSection.CATEGORY))
        assertEquals(emptySet<Long>(), result.categoryIds)
        assertEquals(setOf(7L), result.accountIds)
        assertEquals(10L, result.from)
        assertEquals("milk", result.text)
    }
}
