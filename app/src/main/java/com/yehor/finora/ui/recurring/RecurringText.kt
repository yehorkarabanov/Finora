package com.yehor.finora.ui.recurring

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.yehor.finora.R
import com.yehor.finora.domain.recurring.GenerationIssue
import com.yehor.finora.domain.recurring.RecurrenceRule
import com.yehor.finora.domain.recurring.RecurrenceUnit
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val MEDIUM_DATE: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

fun formatScheduleDate(date: LocalDate): String = date.format(MEDIUM_DATE)

/** "Every month", "Every 2 weeks". */
@Composable
fun ruleText(rule: RecurrenceRule): String {
    if (rule.interval == 1) {
        return stringResource(
            when (rule.unit) {
                RecurrenceUnit.DAY -> R.string.recurring_every_day
                RecurrenceUnit.WEEK -> R.string.recurring_every_week
                RecurrenceUnit.MONTH -> R.string.recurring_every_month
                RecurrenceUnit.YEAR -> R.string.recurring_every_year
            },
        )
    }
    val plural = when (rule.unit) {
        RecurrenceUnit.DAY -> R.plurals.recurring_every_n_days
        RecurrenceUnit.WEEK -> R.plurals.recurring_every_n_weeks
        RecurrenceUnit.MONTH -> R.plurals.recurring_every_n_months
        RecurrenceUnit.YEAR -> R.plurals.recurring_every_n_years
    }
    return pluralStringResource(plural, rule.interval, rule.interval)
}

@Composable
fun unitLabel(unit: RecurrenceUnit): String = stringResource(
    when (unit) {
        RecurrenceUnit.DAY -> R.string.recurring_unit_days
        RecurrenceUnit.WEEK -> R.string.recurring_unit_weeks
        RecurrenceUnit.MONTH -> R.string.recurring_unit_months
        RecurrenceUnit.YEAR -> R.string.recurring_unit_years
    },
)

@Composable
fun presetLabel(preset: FrequencyPreset): String = stringResource(
    when (preset) {
        FrequencyPreset.DAILY -> R.string.recurring_freq_daily
        FrequencyPreset.WEEKLY -> R.string.recurring_freq_weekly
        FrequencyPreset.MONTHLY -> R.string.recurring_freq_monthly
        FrequencyPreset.YEARLY -> R.string.recurring_freq_yearly
        FrequencyPreset.CUSTOM -> R.string.recurring_freq_custom
    },
)

@Composable
fun statusLineText(line: StatusLine): String = when (line) {
    is StatusLine.Next -> stringResource(R.string.recurring_status_next, formatScheduleDate(line.date))
    StatusLine.Paused -> stringResource(R.string.recurring_status_paused)
    is StatusLine.Ended -> stringResource(R.string.recurring_status_ended, formatScheduleDate(line.date))
    is StatusLine.Blocked -> stringResource(
        when (line.issue) {
            GenerationIssue.ACCOUNT_ARCHIVED -> R.string.recurring_issue_archived_short
            GenerationIssue.BEFORE_OPENING_BALANCE -> R.string.recurring_issue_opening_short
            GenerationIssue.BALANCE_OVERFLOW -> R.string.recurring_issue_overflow_short
        },
    )
}

/** "1 Dec 2026" for one date, "1 Dec 2026 to 5 Dec 2026" for a range. */
@Composable
fun rangeText(first: LocalDate, last: LocalDate): String =
    if (first == last) {
        stringResource(R.string.recurring_range_single, formatScheduleDate(first))
    } else {
        stringResource(R.string.recurring_range_multi, formatScheduleDate(first), formatScheduleDate(last))
    }
