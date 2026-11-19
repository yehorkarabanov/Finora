package com.yehor.finora.ui.categories

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yehor.finora.data.repo.CategoryRepository
import com.yehor.finora.data.repo.InvalidCategoryParentException
import com.yehor.finora.data.repo.ReassignTarget
import com.yehor.finora.domain.category.CategoryRules
import com.yehor.finora.domain.category.CategoryRules.ParentCheck
import com.yehor.finora.domain.category.CategoryTree
import com.yehor.finora.domain.model.CategoryType
import com.yehor.finora.ui.common.CategoryPickerModel
import com.yehor.finora.ui.common.PickerMode
import com.yehor.finora.ui.common.PickerRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ---- Categories list ----

data class CategoriesUiState(
    val tree: CategoryTree = CategoryTree(emptyList()),
    val type: CategoryType = CategoryType.EXPENSE,
    val rows: List<PickerRow> = emptyList(),
    val delete: DeleteDialogState? = null,
)

@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val repository: CategoryRepository,
) : ViewModel() {
    private val type = MutableStateFlow(CategoryType.EXPENSE)
    private val expanded = MutableStateFlow(emptySet<Long>())
    private val delete = MutableStateFlow<DeleteDialogState?>(null)

    private val tree = repository.observeAll().map { CategoryTree(it) }

    val state: StateFlow<CategoriesUiState> = combine(tree, type, expanded, delete) { tree, type, expanded, delete ->
        CategoriesUiState(
            tree = tree,
            type = type,
            rows = CategoryPickerModel.rows(tree, type, PickerMode.Assign(), expanded, query = ""),
            delete = delete,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoriesUiState())

    fun onType(t: CategoryType) {
        type.value = t
    }

    fun toggle(id: Long) {
        expanded.update { if (id in it) it - id else it + id }
    }

    fun requestDelete(id: Long) {
        viewModelScope.launch {
            val tree = tree.first()
            val category = tree.category(id) ?: return@launch
            val impact = repository.deletionImpact(id)
            val target = DeleteDialogLogic.initialTarget(impact)
            delete.value = DeleteDialogState(
                categoryId = id,
                categoryName = category.name,
                type = category.type,
                icon = category.icon,
                color = category.color,
                impact = impact,
                target = target,
                suggestedName = impact.suggestedTarget?.let { tree.category(it)?.name },
                targetName = (target as? ReassignTarget.To)?.let { tree.category(it.categoryId)?.name },
            )
        }
    }

    fun onTarget(target: ReassignTarget) {
        viewModelScope.launch {
            val tree = tree.first()
            delete.update {
                it?.copy(
                    target = target,
                    targetName = (target as? ReassignTarget.To)?.let { t -> tree.category(t.categoryId)?.name },
                    failed = false,
                )
            }
        }
    }

    fun dismissDelete() {
        delete.value = null
    }

    fun confirmDelete() {
        val s = delete.value ?: return
        viewModelScope.launch {
            try {
                repository.delete(s.categoryId, s.target)
                delete.value = null
            } catch (e: Exception) {
                delete.update { it?.copy(failed = true) }
            }
        }
    }
}

// ---- Category form ----

data class CategoryFormState(
    /** Null when creating. */
    val categoryId: Long? = null,
    val loaded: Boolean = false,
    val name: String = "",
    val type: CategoryType = CategoryType.EXPENSE,
    val icon: String? = null,
    val color: Long? = null,
    val parentId: Long? = null,
    val tree: CategoryTree = CategoryTree(emptyList()),
    val saving: Boolean = false,
    val saved: Boolean = false,
    val createdCategoryId: Long? = null,
    val parentRejected: ParentCheck? = null,
    val saveFailed: Boolean = false,
) {
    val isEdit: Boolean get() = categoryId != null
    val nameError: CategoryFormLogic.NameError? get() = CategoryFormLogic.nameError(name)

    /** The chosen parent must still pass the shared rule. */
    val parentCheck: ParentCheck
        get() = CategoryRules.checkParent(tree, categoryId, type, parentId)

    val canSave: Boolean get() = loaded && !saving && nameError == null && parentCheck == ParentCheck.OK
}

@HiltViewModel
class CategoryEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: CategoryRepository,
) : ViewModel() {
    private val categoryId: Long? = savedStateHandle.get<Long>("categoryId")
    private val argType: CategoryType = CategoryFormLogic.typeFromArg(savedStateHandle.get<String>("type"))
    private val argParent: Long? = savedStateHandle.get<Long>("parentId")?.takeIf { it > 0 }

    private val _state = MutableStateFlow(CategoryFormState(categoryId = categoryId))
    val state: StateFlow<CategoryFormState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val tree = CategoryTree(repository.observeAll().first())
            if (categoryId == null) {
                val parent = argParent?.let { tree.category(it) }
                _state.value = CategoryFormState(
                    loaded = true,
                    type = parent?.type ?: argType,
                    parentId = parent?.id,
                    tree = tree,
                )
            } else {
                val c = tree.category(categoryId) ?: return@launch
                _state.value = CategoryFormState(
                    categoryId = categoryId, loaded = true, name = c.name, type = c.type, icon = c.icon,
                    color = c.color, parentId = tree.ancestorsOf(c.id).lastOrNull()?.id, tree = tree,
                )
            }
            // Keep the picker fresh if the tree changes while the form is open.
            repository.observeAll().collect { list ->
                _state.update { it.copy(tree = CategoryTree(list)) }
            }
        }
    }

    private fun edit(block: (CategoryFormState) -> CategoryFormState) {
        _state.update { block(it).copy(parentRejected = null, saveFailed = false) }
    }

    fun onName(v: String) = edit { it.copy(name = v) }
    fun onIcon(v: String) = edit { it.copy(icon = v) }
    fun onColor(v: Long) = edit { it.copy(color = v) }
    fun onParent(v: Long?) = edit { it.copy(parentId = v) }

    fun save() {
        val s = _state.value
        if (!s.canSave) return
        _state.update { it.copy(saving = true, parentRejected = null, saveFailed = false) }
        viewModelScope.launch {
            val name = s.name.trim()
            try {
                var createdId: Long? = null
                if (categoryId == null) {
                    createdId = repository.create(name, s.type, s.parentId, s.icon, s.color)
                } else {
                    val currentParent = s.tree.ancestorsOf(categoryId).lastOrNull()?.id
                    repository.update(categoryId, name, s.icon, s.color)
                    if (currentParent != s.parentId) repository.move(categoryId, s.parentId)
                }
                _state.update { it.copy(saving = false, saved = true, createdCategoryId = createdId) }
            } catch (e: InvalidCategoryParentException) {
                _state.update { it.copy(saving = false, parentRejected = e.reason) }
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, saveFailed = true) }
            }
        }
    }
}
