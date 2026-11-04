package com.yehor.finora.ui.categories

import com.yehor.finora.data.repo.DeletionImpact
import com.yehor.finora.data.repo.ReassignTarget
import com.yehor.finora.domain.category.CategoryRules
import com.yehor.finora.domain.model.CategoryType

object CategoryFormLogic {
    const val NAME_MAX = 50

    enum class NameError { EMPTY, TOO_LONG }

    fun nameError(name: String): NameError? {
        val n = name.trim()
        return when {
            n.isEmpty() -> NameError.EMPTY
            n.length > NAME_MAX -> NameError.TOO_LONG
            else -> null
        }
    }

    /** [level] is 0 for a top-level row. "Add subcategory" is not offered on level 3 rows (C11). */
    fun canAddSubcategory(level: Int): Boolean = level + 1 < CategoryRules.MAX_DEPTH

    /** Category type from a navigation argument; unknown or missing means EXPENSE. */
    fun typeFromArg(arg: String?): CategoryType =
        CategoryType.entries.firstOrNull { it.name == arg } ?: CategoryType.EXPENSE
}

/** State of the delete dialog (design 5.6). Names are resolved by the ViewModel from the current tree. */
data class DeleteDialogState(
    val categoryId: Long,
    val categoryName: String,
    val type: CategoryType,
    val impact: DeletionImpact,
    val target: ReassignTarget,
    val suggestedName: String?,
    val targetName: String?,
    val failed: Boolean = false,
)

enum class DeleteTargetOption { SUGGESTED, UNCATEGORIZED, OTHER }

object DeleteDialogLogic {

    /** The suggested target (the parent, or Uncategorized) is preselected. */
    fun initialTarget(impact: DeletionImpact): ReassignTarget =
        impact.suggestedTarget?.let { ReassignTarget.To(it) } ?: ReassignTarget.Uncategorized

    /** Only direct operations need a destination; subcategories move up on their own (C8). */
    fun needsTarget(impact: DeletionImpact): Boolean = impact.directOperationCount > 0

    fun nothingToMove(impact: DeletionImpact): Boolean =
        impact.directOperationCount == 0 && impact.childCount == 0

    fun selectedOption(state: DeleteDialogState): DeleteTargetOption = when (val t = state.target) {
        ReassignTarget.Uncategorized -> DeleteTargetOption.UNCATEGORIZED
        is ReassignTarget.To ->
            if (t.categoryId == state.impact.suggestedTarget) DeleteTargetOption.SUGGESTED else DeleteTargetOption.OTHER
    }
}

