package com.yehor.finora.ui.recurring

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yehor.finora.data.repo.AccountRepository
import com.yehor.finora.data.repo.OpeningBalanceOrderException
import com.yehor.finora.data.repo.RecurringRepository
import com.yehor.finora.data.repo.ScheduleBlockedException
import com.yehor.finora.domain.money.MoneyOverflowException
import com.yehor.finora.domain.recurring.GenerationIssue
import com.yehor.finora.domain.recurring.RecurringSchedule
import com.yehor.finora.domain.recurring.ScheduleDisplayState
import com.yehor.finora.domain.recurring.displayState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

enum class DetailError { ACCOUNT_ARCHIVED, BALANCE_OVERFLOW }

/** What a confirmed "move the initial balance back" repeats. */
enum class MoveBackAction { RESUME_AND_CREATE, CREATE_BLOCKED }

sealed interface DetailDialog {
    data class SkipNext(val date: LocalDate) : DetailDialog
    data class Resume(val info: ResumeInfo) : DetailDialog
    data class Delete(val generatedCount: Int) : DetailDialog
    data class MoveBack(val then: MoveBackAction) : DetailDialog
    data class Error(val kind: DetailError) : DetailDialog
}

data class RecurringDetailUiState(
    val loading: Boolean = true,
    /** Null once loaded means the schedule does not exist (any more), for example after a delete. */
    val schedule: RecurringSchedule? = null,
    val upcoming: List<LocalDate> = emptyList(),
    val history: HistoryView = HistoryView(emptyList(), hasMore = false),
    val issue: GenerationIssue? = null,
    /** Date of the account's initial balance, shown with [GenerationIssue.BEFORE_OPENING_BALANCE]. */
    val openingDate: LocalDate? = null,
    val dialog: DetailDialog? = null,
) {
    val isMissing: Boolean get() = !loading && schedule == null
}

@HiltViewModel
class RecurringDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: RecurringRepository,
    private val accountRepository: AccountRepository,
) : ViewModel() {

    private val scheduleId: Long = savedStateHandle.get<Long>("scheduleId") ?: 0L
    private val dialog = MutableStateFlow<DetailDialog?>(null)

    val state: StateFlow<RecurringDetailUiState> = combine(
        repository.observeSchedule(scheduleId),
        repository.observeOccurrences(scheduleId, RecurringDetailLogic.HISTORY_LIMIT + 1),
        repository.issues,
        dialog,
    ) { schedule, occurrences, issues, currentDialog ->
        val issue = schedule?.takeIf { it.displayState() == ScheduleDisplayState.ACTIVE }?.let { issues[it.id] }
        RecurringDetailUiState(
            loading = false,
            schedule = schedule,
            upcoming = schedule?.let(RecurringDetailLogic::upcoming).orEmpty(),
            history = RecurringDetailLogic.history(occurrences),
            issue = issue,
            openingDate = if (schedule != null && issue == GenerationIssue.BEFORE_OPENING_BALANCE) {
                openingDateOf(schedule.account.id)
            } else {
                null
            },
            dialog = currentDialog,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecurringDetailUiState())

    private suspend fun openingDateOf(accountId: Long): LocalDate? =
        accountRepository.observe(accountId).first()?.openingBalance?.let {
            Instant.ofEpochMilli(it.occurredAt).atZone(ZoneId.systemDefault()).toLocalDate()
        }

    private fun current(): RecurringSchedule? = state.value.schedule

    fun onPause() = launchAction { repository.pause(scheduleId) }

    fun onSkipNextRequest() {
        val next = current()?.nextDate ?: return
        dialog.value = DetailDialog.SkipNext(next)
    }

    fun onConfirmSkipNext() {
        dialog.value = null
        launchAction { repository.skipNext(scheduleId) }
    }

    fun onResumeRequest() {
        val schedule = current() ?: return
        val info = RecurringDetailLogic.resumeInfo(schedule, LocalDate.now())
        if (info == null) {
            // Nothing came due while paused, so there is nothing to choose.
            launchAction { repository.resume(scheduleId, catchUp = true) }
        } else {
            dialog.value = DetailDialog.Resume(info)
        }
    }

    fun onResumeCreate(moveOpeningBalanceBack: Boolean = false) {
        dialog.value = null
        launchAction {
            try {
                repository.resume(scheduleId, catchUp = true, moveOpeningBalanceBack = moveOpeningBalanceBack)
            } catch (_: OpeningBalanceOrderException) {
                dialog.value = DetailDialog.MoveBack(MoveBackAction.RESUME_AND_CREATE)
            }
        }
    }

    fun onResumeSkip() {
        dialog.value = null
        launchAction { repository.resume(scheduleId, catchUp = false) }
    }

    fun onMoveBackRequest() {
        dialog.value = DetailDialog.MoveBack(MoveBackAction.CREATE_BLOCKED)
    }

    fun onConfirmMoveBack(action: MoveBackAction) {
        when (action) {
            MoveBackAction.RESUME_AND_CREATE -> onResumeCreate(moveOpeningBalanceBack = true)
            MoveBackAction.CREATE_BLOCKED -> {
                dialog.value = null
                launchAction { repository.generateBlocked(scheduleId) }
            }
        }
    }

    fun onSkipBlocked() = launchAction { repository.skipBlocked(scheduleId) }

    fun onDeleteRequest() {
        viewModelScope.launch {
            dialog.value = DetailDialog.Delete(repository.generatedCount(scheduleId))
        }
    }

    fun onConfirmDelete() {
        dialog.value = null
        launchAction { repository.delete(scheduleId) }
    }

    fun onDismissDialog() {
        dialog.value = null
    }

    private fun launchAction(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (_: MoneyOverflowException) {
                dialog.value = DetailDialog.Error(DetailError.BALANCE_OVERFLOW)
            } catch (_: ScheduleBlockedException) {
                dialog.value = DetailDialog.Error(DetailError.ACCOUNT_ARCHIVED)
            }
        }
    }
}
