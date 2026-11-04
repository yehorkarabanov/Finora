package com.yehor.finora.ui.tags

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yehor.finora.data.repo.DuplicateTagNameException
import com.yehor.finora.data.repo.InvalidTagNameException
import com.yehor.finora.data.repo.TagRepository
import com.yehor.finora.domain.tag.Tag
import com.yehor.finora.domain.tag.TagNameError
import com.yehor.finora.domain.tag.TagWithUsage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TagDialogState(
    val isEdit: Boolean = false,
    val tagId: Long = 0,
    val name: String = "",
    val color: Long? = null,
    val error: TagNameError? = null,
    val isDuplicate: Boolean = false,
)

data class TagDeleteDialogState(
    val tag: Tag,
    val usageCount: Int,
)

data class TagsUiState(
    val tags: List<TagWithUsage> = emptyList(),
    val dialogState: TagDialogState? = null,
    val deleteState: TagDeleteDialogState? = null,
)

@HiltViewModel
class TagsViewModel @Inject constructor(
    private val tagRepository: TagRepository,
) : ViewModel() {

    private val _dialogState = MutableStateFlow<TagDialogState?>(null)
    private val _deleteState = MutableStateFlow<TagDeleteDialogState?>(null)

    val state: StateFlow<TagsUiState> = combine(
        tagRepository.observeAllWithUsage(),
        _dialogState,
        _deleteState,
    ) { tags, dialog, delete ->
        TagsUiState(tags = tags, dialogState = dialog, deleteState = delete)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TagsUiState())

    fun onAdd() {
        _dialogState.value = TagDialogState()
    }

    fun onEdit(tag: Tag) {
        _dialogState.value = TagDialogState(
            isEdit = true,
            tagId = tag.id,
            name = tag.name,
            color = tag.color,
        )
    }

    fun onDeleteRequest(tagWithUsage: TagWithUsage) {
        _deleteState.value = TagDeleteDialogState(
            tag = tagWithUsage.tag,
            usageCount = tagWithUsage.usageCount,
        )
    }

    fun onDismissDialog() {
        _dialogState.value = null
    }

    fun onDismissDelete() {
        _deleteState.value = null
    }

    fun onSaveTag(name: String, color: Long?) {
        val current = _dialogState.value ?: return
        viewModelScope.launch {
            try {
                if (current.isEdit) {
                    tagRepository.rename(current.tagId, name)
                    tagRepository.setColor(current.tagId, color)
                } else {
                    tagRepository.create(name, color)
                }
                _dialogState.value = null
            } catch (e: InvalidTagNameException) {
                _dialogState.value = current.copy(name = name, color = color, error = e.error, isDuplicate = false)
            } catch (_: DuplicateTagNameException) {
                _dialogState.value = current.copy(name = name, color = color, error = null, isDuplicate = true)
            }
        }
    }

    fun onConfirmDelete() {
        val current = _deleteState.value ?: return
        viewModelScope.launch {
            tagRepository.delete(current.tag.id)
            _deleteState.value = null
        }
    }
}
