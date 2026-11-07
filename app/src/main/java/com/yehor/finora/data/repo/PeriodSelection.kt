package com.yehor.finora.data.repo

import com.yehor.finora.domain.analytics.PeriodKind
import com.yehor.finora.domain.analytics.PeriodState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The period shared by Dashboard, Statistics and Account statistics. In memory only: after process
 * death the app returns to This month.
 */
@Singleton
class PeriodSelection @Inject constructor() {

    private val _state = MutableStateFlow(PeriodState())
    val state: StateFlow<PeriodState> = _state.asStateFlow()

    /** Choosing a preset goes back to the current period of that kind. */
    fun select(kind: PeriodKind) = _state.update { PeriodState.preset(kind) }

    fun setCustom(start: LocalDate, end: LocalDate) = _state.update { PeriodState.custom(start, end) }

    fun shift(steps: Int, today: LocalDate) = _state.update { it.shifted(steps, today) }
}
