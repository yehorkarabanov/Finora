package com.yehor.finora.ui.recurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yehor.finora.data.repo.RecurringRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class RecurringListUiState(
    val loading: Boolean = true,
    val sections: RecurringSections = RecurringSections(),
)

@HiltViewModel
class RecurringListViewModel @Inject constructor(
    repository: RecurringRepository,
) : ViewModel() {
    val state: StateFlow<RecurringListUiState> = combine(
        repository.observeSchedules(),
        repository.issues,
    ) { schedules, issues ->
        RecurringListUiState(loading = false, sections = RecurringListLogic.sections(schedules, issues))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecurringListUiState())
}
