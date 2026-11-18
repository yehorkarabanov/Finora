package com.yehor.finora.ui.categories

import com.yehor.finora.domain.category.Category
import com.yehor.finora.domain.category.CategoryRules.ParentCheck
import com.yehor.finora.domain.model.CategoryType
import com.yehor.finora.ui.common.PickerRow
import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryFormCtaTest {
    private val ok = ParentCheck.OK

    @Test
    fun blockersComeBeforeTheActionLabel() {
        val none = CategoryFormLogic.NameError.EMPTY
        assertEquals(CategoryFormLogic.CtaState.NAME_MISSING, CategoryFormLogic.ctaState(false, none, ok))
        assertEquals(
            CategoryFormLogic.CtaState.NAME_TOO_LONG,
            CategoryFormLogic.ctaState(true, CategoryFormLogic.NameError.TOO_LONG, ok),
        )
        assertEquals(
            CategoryFormLogic.CtaState.PARENT_INVALID,
            CategoryFormLogic.ctaState(false, null, ParentCheck.CYCLE),
        )
        // The name is reported before a bad parent.
        assertEquals(CategoryFormLogic.CtaState.NAME_MISSING, CategoryFormLogic.ctaState(false, none, ParentCheck.SELF))
    }

    @Test
    fun validFormShowsCreateOrSave() {
        assertEquals(CategoryFormLogic.CtaState.CREATE, CategoryFormLogic.ctaState(false, null, ok))
        assertEquals(CategoryFormLogic.CtaState.SAVE, CategoryFormLogic.ctaState(true, null, ok))
    }

    private fun row(id: Long, level: Int) = PickerRow(
        category = Category(id, "c$id", null, null, CategoryType.EXPENSE, null, 0),
        level = level, hasChildren = false, expanded = false, selectable = true, path = "c$id",
    )

    @Test
    fun groupsSplitAtEveryTopLevelRow() {
        val groups = CategoryListLogic.groups(listOf(row(1, 0), row(2, 1), row(3, 2), row(4, 0), row(5, 0), row(6, 1)))
        assertEquals(listOf(listOf(1L, 2L, 3L), listOf(4L), listOf(5L, 6L)), groups.map { g -> g.map { it.category.id } })
        assertEquals(emptyList<List<PickerRow>>(), CategoryListLogic.groups(emptyList()))
    }
}
