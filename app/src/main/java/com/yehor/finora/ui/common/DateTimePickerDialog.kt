package com.yehor.finora.ui.common


import com.yehor.finora.designsystem.icon.AppIcons
import com.yehor.finora.designsystem.icon.IconSize
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yehor.finora.R
import com.yehor.finora.designsystem.component.button.PrimaryButton
import com.yehor.finora.designsystem.component.button.RoundIconButton
import com.yehor.finora.designsystem.theme.PillShape
import com.yehor.finora.designsystem.theme.PreviewTheme
import com.yehor.finora.designsystem.theme.finance
import com.yehor.finora.designsystem.theme.hairline
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Full-screen date and time picker for transactions. Dates after [maxDate] are not selectable and a
 * time later than now is refused on [maxDate] itself. [onConfirm] gets both values together;
 * dismissing (X, back) never reports anything.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimePickerDialog(
    date: LocalDate,
    time: LocalTime,
    maxDate: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, LocalTime) -> Unit,
) {
    val minDate = LocalDate.of(MIN_YEAR, 1, 1)
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = utcMillis(date.coerceIn(minDate, maxDate)),
        yearRange = MIN_YEAR..maxDate.year,
        selectableDates = remember(maxDate) { UpToDate(maxDate) },
    )
    var pendingTime by remember { mutableStateOf(time) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        DateTimePickerContent(
            state = pickerState,
            time = pendingTime,
            maxDate = maxDate,
            onTimeChange = { pendingTime = it },
            onDismiss = onDismiss,
            onConfirm = onConfirm,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateTimePickerContent(
    state: DatePickerState,
    time: LocalTime,
    maxDate: LocalDate,
    onTimeChange: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, LocalTime) -> Unit,
) {
    val date = state.selectedDateMillis?.let(::utcDate)
    val locale = LocalConfiguration.current.locales[0]
    val timeFormat = rememberTimeFormatter()
    val timeText = time.format(timeFormat)
    var showTimeDialog by remember { mutableStateOf(false) }

    val inFuture = date != null && date == maxDate && time.isAfter(LocalTime.now())
    val canConfirm = date != null && !inFuture

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(12.dp),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 8.dp, end = 12.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundIconButton(AppIcons.Close, stringResource(R.string.date_range_close), onDismiss)
                Text(
                    stringResource(R.string.transaction_form_date_time),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).padding(start = 8.dp).semantics { heading() },
                )
            }

            DatePicker(
                state = state,
                modifier = Modifier.weight(1f),
                title = null,
                headline = null,
                showModeToggle = false,
                colors = obsidianDatePickerColors(),
            )

            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val timeLabel = stringResource(R.string.date_time_time_label, timeText)
                    PickerPill(
                        onClick = { showTimeDialog = true },
                        modifier = Modifier.semantics {
                            role = Role.Button
                            contentDescription = timeLabel
                        },
                    ) {
                        Icon(AppIcons.Time, contentDescription = null, modifier = Modifier.size(IconSize.Small))
                        Text(
                            timeText,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 6.dp),
                        )
                        Icon(AppIcons.Dropdown, contentDescription = null, modifier = Modifier.size(20.dp))
                    }
                    PickerPill(
                        onClick = {
                            val now = LocalDate.now()
                            val nowMillis = utcMillis(now.coerceAtMost(maxDate))
                            state.selectedDateMillis = nowMillis
                            state.displayedMonthMillis = nowMillis
                            onTimeChange(LocalTime.now().withSecond(0).withNano(0))
                        },
                    ) {
                        Text(
                            stringResource(R.string.date_time_now),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                }
                PrimaryButton(
                    text = when {
                        date == null -> stringResource(R.string.date_time_choose_date)
                        inFuture -> stringResource(R.string.date_time_future)
                        else -> {
                            val pattern = if (date.year == LocalDate.now().year) "d MMM" else "d MMM yyyy"
                            val dateText = date.format(DateTimeFormatter.ofPattern(pattern, locale))
                            stringResource(R.string.date_time_set, "$dateText, $timeText")
                        }
                    },
                    onClick = { if (date != null && canConfirm) onConfirm(date, time) },
                    enabled = canConfirm,
                )
            }
        }
    }

    if (showTimeDialog) {
        TimeSelectDialog(
            initial = time,
            onDismiss = { showTimeDialog = false },
            onConfirm = {
                onTimeChange(it)
                showTimeDialog = false
            },
        )
    }
}

@Composable
private fun PickerPill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = PillShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = hairline(MaterialTheme.finance.isDark),
        modifier = modifier.defaultMinSize(minHeight = 48.dp),
    ) {
        Row(
            Modifier.defaultMinSize(minHeight = 48.dp).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) { content() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeSelectDialog(
    initial: LocalTime,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime) -> Unit,
) {
    val timeState = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = DateFormat.is24HourFormat(LocalContext.current),
    )
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(28.dp),
        ) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Text(
                    stringResource(R.string.date_time_select_time),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() },
                )
                TimePicker(
                    state = timeState,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    colors = TimePickerDefaults.colors(
                        selectorColor = MaterialTheme.colorScheme.primaryContainer,
                        clockDialSelectedContentColor = MaterialTheme.finance.onMint,
                        timeSelectorSelectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                        timeSelectorSelectedContentColor = MaterialTheme.colorScheme.primary,
                        periodSelectorSelectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                        periodSelectorSelectedContentColor = MaterialTheme.colorScheme.primary,
                    ),
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss, shape = PillShape) { Text(stringResource(R.string.action_cancel)) }
                    PrimaryButton(
                        text = stringResource(R.string.date_time_set_short),
                        onClick = { onConfirm(LocalTime.of(timeState.hour, timeState.minute)) },
                        fillWidth = false,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PreviewContent(dark: Boolean) {
    PreviewTheme(dark) {
        val today = LocalDate.of(2026, 11, 9)
        val state = rememberDatePickerState(
            initialSelectedDateMillis = utcMillis(today),
            yearRange = MIN_YEAR..today.year,
            selectableDates = UpToDate(today),
        )
        DateTimePickerContent(
            state = state,
            time = LocalTime.of(0, 30),
            maxDate = today,
            onTimeChange = {},
            onDismiss = {},
            onConfirm = { _, _ -> },
        )
    }
}

@Preview(showBackground = true, heightDp = 780)
@Composable
private fun DateTimePickerLightPreview() = PreviewContent(dark = false)

@Preview(showBackground = true, backgroundColor = 0xFF0F1412, heightDp = 780)
@Composable
private fun DateTimePickerDarkPreview() = PreviewContent(dark = true)
