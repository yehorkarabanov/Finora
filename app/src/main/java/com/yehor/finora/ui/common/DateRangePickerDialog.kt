package com.yehor.finora.ui.common


import com.yehor.finora.designsystem.icon.AppIcons
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DateRangePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** The Material date picker works in UTC-midnight millis; these are the only converters the app should use for it. */
internal fun utcMillis(date: LocalDate): Long = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

internal fun utcDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

/**
 * Compact label of a custom range: "9 Nov 2026" for one day, "3 Nov – 9 Nov 2026" within a year,
 * "3 Nov 2025 – 9 Nov 2026" across years. Used for period pills so every screen reads the same.
 */
internal fun compactRangeLabel(from: LocalDate, to: LocalDate, locale: Locale = Locale.getDefault()): String {
    val dayMonth = DateTimeFormatter.ofPattern("d MMM", locale)
    val full = DateTimeFormatter.ofPattern("d MMM yyyy", locale)
    return if (from == to) from.format(full)
    else "${from.format(if (from.year == to.year) dayMonth else full)} – ${to.format(full)}"
}

/** Inclusive number of days between two dates. */
internal fun inclusiveDays(from: LocalDate, to: LocalDate): Int = (ChronoUnit.DAYS.between(from, to) + 1).toInt()

internal const val MIN_YEAR = 2000

/**
 * Full-screen date range picker (M3 guidance for ranges: a month list needs the whole screen).
 *
 * Transactions cannot be dated in the future, so dates after [maxDate] are not selectable. If only a
 * start date is picked, Apply uses a single-day range and the bar says so. [onConfirm] gets an
 * inclusive end date. Dismissing (X, back) never applies anything.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangePickerDialog(
    initialStart: LocalDate?,
    initialEnd: LocalDate?,
    onDismiss: () -> Unit,
    onConfirm: (start: LocalDate, endInclusive: LocalDate) -> Unit,
    maxDate: LocalDate = LocalDate.now(),
) {
    val minDate = LocalDate.of(MIN_YEAR, 1, 1)
    // The state throws for values outside the year range, and old presets may end in the future.
    val start = initialStart?.coerceIn(minDate, maxDate)
    val end = initialEnd?.coerceIn(minDate, maxDate)?.takeIf { start != null }
    val pickerState = rememberDateRangePickerState(
        initialSelectedStartDateMillis = start?.let(::utcMillis),
        initialSelectedEndDateMillis = end?.let(::utcMillis),
        yearRange = MIN_YEAR..maxDate.year,
        selectableDates = remember(maxDate) { UpToDate(maxDate) },
    )
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        DateRangePickerContent(pickerState, onDismiss, onConfirm)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
internal class UpToDate(private val maxDate: LocalDate) : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long): Boolean = !utcDate(utcTimeMillis).isAfter(maxDate)
    override fun isSelectableYear(year: Int): Boolean = year <= maxDate.year
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRangePickerContent(
    state: DateRangePickerState,
    onDismiss: () -> Unit,
    onConfirm: (start: LocalDate, endInclusive: LocalDate) -> Unit,
) {
    val start = state.selectedStartDateMillis?.let(::utcDate)
    // A lone start date means one day. DateRangePicker itself never lets the end precede the start.
    val end = state.selectedEndDateMillis?.let(::utcDate) ?: start
    val locale = LocalConfiguration.current.locales[0]
    val thisYear = LocalDate.now().year

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
                    stringResource(R.string.date_range_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).padding(start = 8.dp).semantics { heading() },
                )
                TextButton(
                    enabled = start != null,
                    onClick = { state.setSelection(null, null) },
                    shape = PillShape,
                ) { Text(stringResource(R.string.filter_clear)) }
            }

            DateRangePicker(
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
                Text(
                    text = when {
                        start == null || end == null -> stringResource(R.string.date_range_hint_start)
                        state.selectedEndDateMillis == null -> stringResource(R.string.date_range_hint_end)
                        else -> {
                            val days = inclusiveDays(start, end)
                            val full = DateTimeFormatter.ofPattern("d MMM yyyy", locale)
                            val range = if (start == end) start.format(full)
                            else stringResource(R.string.analytics_range, start.format(full), end.format(full))
                            stringResource(
                                R.string.date_range_summary,
                                range,
                                pluralStringResource(R.plurals.date_range_days, days, days),
                            )
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
                PrimaryButton(
                    text = if (start != null && end != null) {
                        val dayMonth = DateTimeFormatter.ofPattern("d MMM", locale)
                        val full = DateTimeFormatter.ofPattern("d MMM yyyy", locale)
                        val label = when {
                            start == end -> start.format(if (start.year == thisYear) dayMonth else full)
                            start.year == thisYear && end.year == thisYear ->
                                stringResource(R.string.analytics_range, start.format(dayMonth), end.format(dayMonth))
                            else -> stringResource(R.string.analytics_range, start.format(full), end.format(full))
                        }
                        stringResource(R.string.date_range_apply, label)
                    } else {
                        stringResource(R.string.date_range_apply_empty)
                    },
                    onClick = { if (start != null && end != null) onConfirm(start, end) },
                    enabled = start != null && end != null,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PreviewContent(dark: Boolean) {
    PreviewTheme(dark) {
        val today = LocalDate.of(2026, 11, 9)
        val state = rememberDateRangePickerState(
            initialSelectedStartDateMillis = utcMillis(today.minusDays(6)),
            initialSelectedEndDateMillis = utcMillis(today),
            yearRange = MIN_YEAR..today.year,
            selectableDates = UpToDate(today),
        )
        DateRangePickerContent(state, onDismiss = {}, onConfirm = { _, _ -> })
    }
}

@Preview(showBackground = true, heightDp = 780)
@Composable
private fun DateRangePickerLightPreview() = PreviewContent(dark = false)

@Preview(showBackground = true, backgroundColor = 0xFF0F1412, heightDp = 780)
@Composable
private fun DateRangePickerDarkPreview() = PreviewContent(dark = true)
