package com.yehor.finora.ui.common

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerColors
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yehor.finora.R
import com.yehor.finora.designsystem.component.button.PrimaryButton
import com.yehor.finora.designsystem.theme.PillShape
import com.yehor.finora.designsystem.theme.finance

/** Obsidian colors shared by every date picker: raised container, mint selection and "today" ring. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun obsidianDatePickerColors(container: Color = MaterialTheme.colorScheme.surfaceContainer): DatePickerColors {
    val scheme = MaterialTheme.colorScheme
    return DatePickerDefaults.colors(
        containerColor = container,
        selectedDayContainerColor = scheme.primaryContainer,
        selectedDayContentColor = MaterialTheme.finance.onMint,
        todayContentColor = scheme.primary,
        todayDateBorderColor = scheme.primary,
        selectedYearContainerColor = scheme.primaryContainer,
        selectedYearContentColor = MaterialTheme.finance.onMint,
        dayInSelectionRangeContainerColor = scheme.primary.copy(alpha = 0.16f),
        dayInSelectionRangeContentColor = scheme.onSurface,
    )
}

/**
 * Single-date dialog on a 28dp raised container with pill buttons. The picker works in UTC midnights;
 * [selectableDates] carries the min/max rules and [onConfirm] gets the chosen UTC midnight.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SingleDateDialog(
    initialUtcMillis: Long?,
    selectableDates: SelectableDates,
    onDismiss: () -> Unit,
    onConfirm: (utcMillis: Long) -> Unit,
) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initialUtcMillis, selectableDates = selectableDates)
    val colors = obsidianDatePickerColors()
    DatePickerDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        colors = colors,
        confirmButton = {
            PrimaryButton(
                text = stringResource(R.string.action_confirm),
                onClick = { state.selectedDateMillis?.let(onConfirm) },
                enabled = state.selectedDateMillis != null,
                fillWidth = false,
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shape = PillShape) { Text(stringResource(R.string.action_cancel)) }
        },
    ) {
        DatePicker(state = state, colors = colors)
    }
}
