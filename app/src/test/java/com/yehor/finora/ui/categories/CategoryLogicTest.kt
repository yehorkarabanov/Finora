package com.yehor.finora.ui.categories

import com.yehor.finora.data.repo.DeletionImpact
import com.yehor.finora.data.repo.ReassignTarget
import com.yehor.finora.domain.model.CategoryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryLogicTest {

    private fun state(impact: DeletionImpact, target: ReassignTarget = DeleteDialogLogic.initialTarget(impact)) =
        DeleteDialogState(1, "Food", CategoryType.EXPENSE, impact, target, "Parent", null)

    @Test
    fun nameValidationTrimsAndBounds() {
        assertEquals(CategoryFormLogic.NameError.EMPTY, CategoryFormLogic.nameError("   "))
        assertEquals(CategoryFormLogic.NameError.EMPTY, CategoryFormLogic.nameError(""))
        assertNull(CategoryFormLogic.nameError(" a "))
        assertNull(CategoryFormLogic.nameError("x".repeat(50)))
        assertEquals(CategoryFormLogic.NameError.TOO_LONG, CategoryFormLogic.nameError("x".repeat(51)))
        assertNull(CategoryFormLogic.nameError(" " + "x".repeat(50) + " "))
    }

    @Test
    fun addSubcategoryIsHiddenOnLevelThree() {
        assertTrue(CategoryFormLogic.canAddSubcategory(0))
        assertTrue(CategoryFormLogic.canAddSubcategory(1))
        assertFalse(CategoryFormLogic.canAddSubcategory(2))
    }

    @Test
    fun typeArgumentFallsBackToExpense() {
        assertEquals(CategoryType.INCOME, CategoryFormLogic.typeFromArg("INCOME"))
        assertEquals(CategoryType.EXPENSE, CategoryFormLogic.typeFromArg(null))
        assertEquals(CategoryType.EXPENSE, CategoryFormLogic.typeFromArg("nonsense"))
    }

    @Test
    fun suggestedTargetIsParentOrUncategorized() {
        assertEquals(ReassignTarget.To(7), DeleteDialogLogic.initialTarget(DeletionImpact(3, 1, 7)))
        assertEquals(ReassignTarget.Uncategorized, DeleteDialogLogic.initialTarget(DeletionImpact(3, 1, null)))
    }

    @Test
    fun targetIsNeededOnlyForDirectOperations() {
        assertTrue(DeleteDialogLogic.needsTarget(DeletionImpact(1, 0, null)))
        assertFalse(DeleteDialogLogic.needsTarget(DeletionImpact(0, 2, 5)))
        assertTrue(DeleteDialogLogic.nothingToMove(DeletionImpact(0, 0, null)))
        assertFalse(DeleteDialogLogic.nothingToMove(DeletionImpact(0, 1, null)))
        // A category used only by schedules still needs a destination for them.
        assertTrue(DeleteDialogLogic.needsTarget(DeletionImpact(0, 0, null, scheduleCount = 2)))
        assertFalse(DeleteDialogLogic.nothingToMove(DeletionImpact(0, 0, null, scheduleCount = 2)))
    }

    @Test
    fun selectedOptionFollowsTheTarget() {
        val impact = DeletionImpact(2, 0, 7)
        assertEquals(DeleteTargetOption.SUGGESTED, DeleteDialogLogic.selectedOption(state(impact)))
        assertEquals(
            DeleteTargetOption.UNCATEGORIZED,
            DeleteDialogLogic.selectedOption(state(impact, ReassignTarget.Uncategorized)),
        )
        assertEquals(
            DeleteTargetOption.OTHER,
            DeleteDialogLogic.selectedOption(state(impact, ReassignTarget.To(99))),
        )
        // Without a parent, Uncategorized is the suggestion and any category is "other".
        assertEquals(
            DeleteTargetOption.OTHER,
            DeleteDialogLogic.selectedOption(state(DeletionImpact(2, 0, null), ReassignTarget.To(5))),
        )
    }
}
