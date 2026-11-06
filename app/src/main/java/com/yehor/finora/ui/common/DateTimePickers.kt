package com.yehor.finora.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.yehor.finora.R
import com.yehor.finora.domain.operation.TransactionDateTime
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    date: LocalDate?,
    onDateChange: (LocalDate) -> Unit,
    maxDate: LocalDate,
    modifier: Modifier = Modifier,
    label: String = stringResource(R.string.transaction_form_date),
    /** The month the picker opens on while [date] is empty. */
    emptyPickerDate: LocalDate = maxDate,
) {
    var showDialog by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        OutlinedTextField(
            value = date?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)).orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable { showDialog = true },
        )
    }

    if (showDialog) {
        val maxUtcMidnight = TransactionDateTime.pickerUtcMidnight(maxDate)
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = TransactionDateTime.pickerUtcMidnight(date ?: emptyPickerDate),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= maxUtcMidnight
            },
        )
        DatePickerDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let {
                            onDateChange(Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate())
                        }
                        showDialog = false
                    },
                ) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

/** One field showing date and time together; tapping it picks the date, then the time. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimeField(
    date: LocalDate,
    time: LocalTime,
    onDateChange: (LocalDate) -> Unit,
    onTimeChange: (LocalTime) -> Unit,
    maxDate: LocalDate,
    modifier: Modifier = Modifier,
) {
    // 0 = closed, 1 = picking the date, 2 = picking the time.
    var step by remember { mutableIntStateOf(0) }
    var pendingDate by remember { mutableStateOf(date) }

    Box(modifier = modifier) {
        OutlinedTextField(
            value = "${date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))}, " +
                time.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.transaction_form_date_time)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable {
                    pendingDate = date
                    step = 1
                },
        )
    }

    if (step == 1) {
        val maxUtcMidnight = TransactionDateTime.pickerUtcMidnight(maxDate)
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = TransactionDateTime.pickerUtcMidnight(date),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= maxUtcMidnight
            },
        )
        DatePickerDialog(
            onDismissRequest = { step = 0 },
            confirmButton = {
                TextButton(
                    onClick = {
                        val picked = datePickerState.selectedDateMillis
                        if (picked != null) {
                            pendingDate = Instant.ofEpochMilli(picked).atZone(ZoneId.of("UTC")).toLocalDate()
                            step = 2
                        } else {
                            step = 0
                        }
                    },
                ) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { step = 0 }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (step == 2) {
        val timePickerState = rememberTimePickerState(
            initialHour = time.hour,
            initialMinute = time.minute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { step = 0 },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDateChange(pendingDate)
                        onTimeChange(LocalTime.of(timePickerState.hour, timePickerState.minute))
                        step = 0
                    },
                ) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { step = 0 }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
            text = { TimePicker(state = timePickerState) },
        )
    }
}
