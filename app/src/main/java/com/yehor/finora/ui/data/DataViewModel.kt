package com.yehor.finora.ui.data

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yehor.finora.data.dataio.BackupException
import com.yehor.finora.data.dataio.BackupExportResult
import com.yehor.finora.data.repo.BackupRepository
import com.yehor.finora.domain.dataio.DataSummary
import com.yehor.finora.domain.dataio.ImportFailure
import com.yehor.finora.domain.dataio.ImportProblem
import com.yehor.finora.domain.dataio.StagingResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ExportState {
    data object Idle : ExportState
    data object Exporting : ExportState
    data class Done(val fileName: String, val sizeBytes: Long) : ExportState
    data class Failed(val message: String) : ExportState
}

sealed interface RestoreState {
    data object Idle : RestoreState
    data class Validating(val step: String) : RestoreState
    data class Rejected(
        val failure: ImportFailure,
        val problems: List<ImportProblem>,
        val totalProblems: Int,
    ) : RestoreState
    data class Preview(
        val ready: StagingResult.Ready,
        val currentSummary: DataSummary?,
        val backedUpFirst: Boolean = false,
    ) : RestoreState
    data object Restarting : RestoreState
}

data class DataUiState(
    val summary: DataSummary? = null,
    val appVersion: String = "1.0",
    val isBusy: Boolean = false,
    val exportState: ExportState = ExportState.Idle,
    val restoreState: RestoreState = RestoreState.Idle,
)

@HiltViewModel
class DataViewModel @Inject constructor(
    private val backupRepository: BackupRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DataUiState())
    val state: StateFlow<DataUiState> = _state.asStateFlow()

    private var activeJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val summary = try {
                backupRepository.getLiveSummary()
            } catch (_: Exception) {
                null
            }
            _state.update { it.copy(summary = summary) }
        }
    }

    fun exportBackup(destination: Uri, fileName: String) {
        if (_state.value.isBusy) return
        activeJob = viewModelScope.launch {
            _state.update { it.copy(isBusy = true, exportState = ExportState.Exporting) }
            val result = backupRepository.exportBackup(destination, fileName)
            _state.update {
                it.copy(
                    isBusy = false,
                    exportState = result.fold(
                        onSuccess = { res -> ExportState.Done(res.fileName, res.sizeBytes) },
                        onFailure = { err -> ExportState.Failed(err.message ?: "Unknown error") },
                    ),
                )
            }
        }
    }

    fun dismissExportResult() {
        _state.update { it.copy(exportState = ExportState.Idle) }
    }

    fun stageRestore(source: Uri) {
        if (_state.value.isBusy) return
        activeJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    isBusy = true,
                    restoreState = RestoreState.Validating("Copying"),
                )
            }

            val stagingResult = backupRepository.stageBackup(
                source = source,
                onProgress = { step ->
                    _state.update { it.copy(restoreState = RestoreState.Validating(step)) }
                },
            )

            when (stagingResult) {
                is StagingResult.Ready -> {
                    val current = try {
                        backupRepository.getLiveSummary()
                    } catch (_: Exception) {
                        null
                    }
                    _state.update {
                        it.copy(
                            isBusy = false,
                            restoreState = RestoreState.Preview(
                                ready = stagingResult,
                                currentSummary = current,
                            ),
                        )
                    }
                }
                is StagingResult.Rejected -> {
                    _state.update {
                        it.copy(
                            isBusy = false,
                            restoreState = RestoreState.Rejected(
                                failure = stagingResult.failure,
                                problems = stagingResult.problems,
                                totalProblems = stagingResult.totalProblems,
                            ),
                        )
                    }
                }
            }
        }
    }

    fun cancelRestore() {
        activeJob?.cancel()
        _state.update { it.copy(isBusy = false, restoreState = RestoreState.Idle) }
    }

    fun backUpCurrentFirst(destination: Uri, fileName: String) {
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true) }
            val res = backupRepository.exportBackup(destination, fileName)
            _state.update { current ->
                val updatedRestoreState = (current.restoreState as? RestoreState.Preview)?.copy(backedUpFirst = res.isSuccess)
                    ?: current.restoreState
                current.copy(
                    isBusy = false,
                    restoreState = updatedRestoreState,
                    exportState = if (res.isSuccess) ExportState.Done(fileName, 0L) else ExportState.Failed(res.exceptionOrNull()?.message ?: "Backup failed"),
                )
            }
        }
    }

    fun confirmRestore() {
        val ready = (_state.value.restoreState as? RestoreState.Preview)?.ready ?: return
        _state.update { it.copy(isBusy = true, restoreState = RestoreState.Restarting) }
        viewModelScope.launch {
            backupRepository.commitBackup(ready).onFailure { err ->
                val failure = (err as? BackupException)?.failure ?: ImportFailure.UNREADABLE
                _state.update {
                    it.copy(
                        isBusy = false,
                        restoreState = RestoreState.Rejected(failure, emptyList(), 0),
                    )
                }
            }
        }
    }
}
