package com.yehor.finora.data.repo

import androidx.room.withTransaction
import com.yehor.finora.data.db.CategoryEntity
import com.yehor.finora.data.db.FinoraDatabase
import com.yehor.finora.domain.category.Category
import com.yehor.finora.domain.category.CategoryDirectTotal
import com.yehor.finora.domain.category.CategoryRules
import com.yehor.finora.domain.category.CategoryRules.ParentCheck
import com.yehor.finora.domain.category.CategoryTree
import com.yehor.finora.domain.model.CategoryType
import com.yehor.finora.domain.model.OperationType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** What deleting a category will touch. [suggestedTarget] is the parent, or null for Uncategorized. */
data class DeletionImpact(
    val directOperationCount: Int,
    val childCount: Int,
    val suggestedTarget: Long?,
)

/** Where the direct operations of a deleted category go. */
sealed interface ReassignTarget {
    data class To(val categoryId: Long) : ReassignTarget
    data object Uncategorized : ReassignTarget
}

@Singleton
class CategoryRepository @Inject constructor(private val db: FinoraDatabase) {

    private fun CategoryEntity.toDomain() = Category(
        id = id, name = name, icon = icon, color = color, type = type, parentId = parentId, createdAt = createdAt,
    )

    fun observeAll(): Flow<List<Category>> =
        db.categoryDao().observeAll().map { list -> list.map { it.toDomain() } }

    private suspend fun loadTree(): CategoryTree =
        CategoryTree(db.categoryDao().getAll().map { it.toDomain() })

    suspend fun create(
        name: String,
        type: CategoryType,
        parentId: Long?,
        icon: String?,
        color: Long?,
        now: Long = System.currentTimeMillis(),
    ): Long = db.withTransaction {
        val check = CategoryRules.checkParent(loadTree(), null, type, parentId)
        if (check != ParentCheck.OK) throw InvalidCategoryParentException(check)
        db.categoryDao().insert(
            CategoryEntity(
                name = name, icon = icon, color = color, type = type, parentId = parentId, createdAt = now,
            ),
        )
    }

    suspend fun update(id: Long, name: String, icon: String?, color: Long?) {
        db.categoryDao().updateDetails(id, name, icon, color)
    }

    suspend fun move(id: Long, newParentId: Long?) = db.withTransaction {
        val entity = db.categoryDao().getById(id)
            ?: throw InvariantViolationException("Unknown category $id")
        val check = CategoryRules.checkParent(loadTree(), id, entity.type, newParentId)
        if (check != ParentCheck.OK) throw InvalidCategoryParentException(check)
        db.categoryDao().setParent(id, newParentId)
    }

    suspend fun deletionImpact(id: Long): DeletionImpact {
        val entity = db.categoryDao().getById(id)
            ?: throw InvariantViolationException("Unknown category $id")
        return DeletionImpact(
            directOperationCount = db.categoryDao().operationCount(id),
            childCount = db.categoryDao().childCount(id),
            suggestedTarget = entity.parentId,
        )
    }

    /**
     * Rules C6 to C10: reassign the direct operations, move immediate subcategories up to the deleted
     * category's parent, delete the row. One transaction; any failure rolls all of it back.
     */
    suspend fun delete(
        id: Long,
        target: ReassignTarget,
        now: Long = System.currentTimeMillis(),
    ) = db.withTransaction {
        val entity = db.categoryDao().getById(id)
            ?: throw InvariantViolationException("Unknown category $id")
        val targetId: Long? = when (target) {
            ReassignTarget.Uncategorized -> null
            is ReassignTarget.To -> {
                if (target.categoryId == id) {
                    throw InvariantViolationException("Cannot reassign operations to the category being deleted")
                }
                val targetEntity = db.categoryDao().getById(target.categoryId)
                    ?: throw InvariantViolationException("Unknown target category ${target.categoryId}")
                if (targetEntity.type != entity.type) {
                    throw InvariantViolationException("Target category has a different type")
                }
                target.categoryId
            }
        }
        db.categoryDao().reassignOperations(id, targetId, now)
        db.categoryDao().reparentChildren(id, entity.parentId)
        db.categoryDao().deleteById(id)
    }

    /** Direct (not rolled-up) totals; [type] must be INCOME or EXPENSE. */
    suspend fun directTotals(
        type: OperationType,
        from: Long = Long.MIN_VALUE,
        to: Long = Long.MAX_VALUE,
    ): List<CategoryDirectTotal> {
        require(type == OperationType.INCOME || type == OperationType.EXPENSE) {
            "directTotals supports INCOME and EXPENSE only"
        }
        return db.categoryDao().directTotals(type, from, to).map {
            CategoryDirectTotal(it.categoryId, it.currencyCode, it.totalMinor, it.operationCount)
        }
    }
}
