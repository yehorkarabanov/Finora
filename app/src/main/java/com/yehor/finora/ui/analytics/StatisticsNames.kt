package com.yehor.finora.ui.analytics

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.yehor.finora.R
import com.yehor.finora.domain.analytics.PeriodKind
import com.yehor.finora.domain.analytics.PeriodState
import com.yehor.finora.domain.analytics.ResolvedPeriod
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// Period and date names of the Statistics screen.

/** The locale's own order and punctuation for a skeleton such as "MMMd" ("Nov 9" in English, "9 нояб." in Russian). */
internal fun bestPattern(locale: Locale, skeleton: String): DateTimeFormatter =
    DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)

/** "November 2026", capitalised (Russian stand-alone month names are lower case). */
internal fun monthYearName(date: LocalDate, locale: Locale): String =
    date.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }

/** "Nov 9", or "Nov 9, 2025" outside the current year. */
internal fun shortDate(date: LocalDate, today: LocalDate, locale: Locale): String =
    date.format(bestPattern(locale, if (date.year == today.year) "MMMd" else "yMMMd"))

/** "Today", "Week of Nov 9", "November 2026", "2026" or "Oct 3 – Nov 18". */
@Composable
internal fun stepperName(state: PeriodState, period: ResolvedPeriod, today: LocalDate): String {
    val locale = LocalConfiguration.current.locales[0]
    val from = period.fromDate
    return when (state.kind) {
        PeriodKind.TODAY -> if (from == today) stringResource(R.string.period_today) else shortDate(from, today, locale)
        PeriodKind.WEEK -> stringResource(R.string.stats_week_of, shortDate(from, today, locale))
        PeriodKind.MONTH -> monthYearName(from, locale)
        PeriodKind.YEAR -> from.year.toString()
        PeriodKind.CUSTOM -> rangeName(period, today, locale)
    }
}

/** "Oct 3 – Nov 18"; years are written out when the range is not within the current year. */
internal fun rangeName(period: ResolvedPeriod, today: LocalDate, locale: Locale): String {
    val from = period.fromDate
    val to = period.toDateInclusive
    if (from == to) return shortDate(from, today, locale)
    val sameYear = from.year == to.year && to.year == today.year
    val skeleton = if (sameYear) "MMMd" else "yMMMd"
    return "${from.format(bestPattern(locale, skeleton))} – ${to.format(bestPattern(locale, skeleton))}"
}

/** "October", "October 2025", "2025", or the stepper name for other periods: the "than …" of the comparison. */
@Composable
internal fun periodShortName(period: ResolvedPeriod, today: LocalDate): String {
    val locale = LocalConfiguration.current.locales[0]
    val from = period.fromDate
    return when (period.period.kind) {
        PeriodKind.MONTH -> from.format(DateTimeFormatter.ofPattern(if (from.year == today.year) "LLLL" else "LLLL yyyy", locale))
        PeriodKind.YEAR -> from.year.toString()
        PeriodKind.TODAY -> shortDate(from, today, locale)
        PeriodKind.WEEK -> stringResource(R.string.stats_week_of, shortDate(from, today, locale))
        PeriodKind.CUSTOM -> rangeName(period, today, locale)
    }
}
