package com.yehor.finora.ui.common

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.yehor.finora.R
import com.yehor.finora.domain.operation.TransactionDateTime
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Short time formatter that follows the system 12/24-hour setting, not just the locale. */
@Composable
fun rememberTimeFormatter(): DateTimeFormatter {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val is24 = DateFormat.is24HourFormat(context)
    return remember(locale, is24) {
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, if (is24) "Hm" else "hm"), locale)
    }
}

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
        SingleDateDialog(
            initialUtcMillis = TransactionDateTime.pickerUtcMidnight(date ?: emptyPickerDate),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= maxUtcMidnight
            },
            onDismiss = { showDialog = false },
            onConfirm = {
                onDateChange(Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate())
                showDialog = false
            },
        )
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
    var open by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        OutlinedTextField(
            value = "${date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))}, " +
                time.format(rememberTimeFormatter()),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.transaction_form_date_time)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable { open = true },
        )
    }

    DateTimePickerFlow(
        open = open,
        date = date,
        time = time,
        maxDate = maxDate,
        onDismiss = { open = false },
        onPicked = { pickedDate, pickedTime ->
            onDateChange(pickedDate)
            onTimeChange(pickedTime)
            open = false
        },
    )
}

/** Full-screen date and time picker; [onPicked] receives the chosen date and time together. */
@Composable
fun DateTimePickerFlow(
    open: Boolean,
    date: LocalDate,
    time: LocalTime,
    maxDate: LocalDate,
    onDismiss: () -> Unit,
    onPicked: (LocalDate, LocalTime) -> Unit,
) {
    if (open) DateTimePickerDialog(date, time, maxDate, onDismiss, onConfirm = onPicked)
}
