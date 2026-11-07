package com.yehor.finora.ui.data

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yehor.finora.data.dataio.CsvExportResult
import com.yehor.finora.data.repo.AccountRepository
import com.yehor.finora.data.repo.BackupRepository
import com.yehor.finora.data.repo.CategoryRepository
import com.yehor.finora.data.repo.TagRepository
import com.yehor.finora.data.repo.TransactionRepository
import com.yehor.finora.domain.account.Account
import com.yehor.finora.domain.category.Category
import com.yehor.finora.domain.operation.FilterCodec
import com.yehor.finora.domain.operation.TransactionFilter
import com.yehor.finora.domain.tag.Tag
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId
import javax.inject.Inject

data class CsvExportUiState(
    val filter: TransactionFilter = TransactionFilter(),
    val matchingCount: Int = 0,
    val isBusy: Boolean = false,
    val exportResult: CsvExportResult? = null,
    val errorMessage: String? = null,
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val tags: List<Tag> = emptyList(),
)

@HiltViewModel
class CsvExportViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val transactionRepository: TransactionRepository,
    private val backupRepository: BackupRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val tagRepository: TagRepository,
) : ViewModel() {

    private val initialFilter: TransactionFilter = run {
        val encoded = savedStateHandle.get<String>("filter")
        if (!encoded.isNullOrBlank()) {
            FilterCodec.decode(encoded)
        } else {
            TransactionFilter()
        }
    }

    private val _state = MutableStateFlow(CsvExportUiState(filter = initialFilter))
    val state: StateFlow<CsvExportUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                accountRepository.observeAll(),
                categoryRepository.observeAll(),
                tagRepository.observeAll(),
            ) { accs, cats, tags ->
                Triple(accs.map { it.account }, cats, tags)
            }.collect { (accs, cats, tags) ->
                _state.update {
                    it.copy(
                        accounts = accs,
                        categories = cats,
                        tags = tags,
                    )
                }
            }
        }
        recalculateCount(initialFilter)
    }

    fun onFilterChange(newFilter: TransactionFilter) {
        _state.update { it.copy(filter = newFilter, exportResult = null, errorMessage = null) }
        recalculateCount(newFilter)
    }

    private fun recalculateCount(filter: TransactionFilter) {
        viewModelScope.launch {
            val count = try {
                transactionRepository.count(filter)
            } catch (_: Exception) {
                0
            }
            _state.update { it.copy(matchingCount = count) }
        }
    }

    fun exportCsv(destination: Uri, fileName: String, zone: ZoneId = ZoneId.systemDefault()) {
        if (_state.value.isBusy) return
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, errorMessage = null, exportResult = null) }
            val res = backupRepository.exportCsv(_state.value.filter, destination, fileName, zone)
            _state.update {
                it.copy(
                    isBusy = false,
                    exportResult = res.getOrNull(),
                    errorMessage = res.exceptionOrNull()?.message,
                )
            }
        }
    }
}
