package com.yehor.finora.ui.navigation

import androidx.lifecycle.ViewModel
import com.yehor.finora.data.repo.BackupRepository
import com.yehor.finora.domain.dataio.ImportOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Reads the result of a restore once, on the first screen after the restart (design 6.5, Outcome). */
@HiltViewModel
class ShellViewModel @Inject constructor(
    backupRepository: BackupRepository,
) : ViewModel() {
    val restoreOutcome: ImportOutcome? = backupRepository.consumeOutcome()
}
