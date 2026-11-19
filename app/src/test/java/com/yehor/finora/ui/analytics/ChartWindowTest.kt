package com.yehor.finora.ui.analytics

import com.yehor.finora.domain.analytics.BucketUnit
import com.yehor.finora.domain.analytics.PeriodKind
import com.yehor.finora.domain.analytics.PeriodState
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartWindowTest {
    private val today = LocalDate.of(2026, 11, 10)
    private val nov = ChartWindow(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30))

    @Test fun panBackOneThird() = assertEquals(
        ChartWindow(LocalDate.of(2026, 8, 23), LocalDate.of(2026, 9, 21)),
        ChartWindow(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)).panned(-0.3f, today),
    )

    @Test fun panForwardStopsAtToday() = assertEquals(
        today,
        ChartWindow(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)).panned(1f, today).end,
    )

    @Test fun pinchOutClampsToMinDays() = assertEquals(7L, nov.zoomed(100f, 0.5f, today).days)

    @Test fun pinchInClampsToMaxDays() = assertEquals(1827L, nov.zoomed(0.001f, 0.5f, today).days)

    @Test fun zoomKeepsFocusDate() {
        val w = ChartWindow(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)).zoomed(2f, 0.5f, today)
        assertTrue(LocalDate.of(2026, 9, 15) in w.start..w.end)
    }

    @Test fun zoomNeverEndsAfterToday() {
        val w = nov.zoomed(0.5f, 0.5f, today)
        assertEquals(today, w.end)
        assertEquals(60L, w.days)
    }

    @Test fun unitHysteresis() {
        val w = ChartWindow(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1).plusDays(95))
        assertEquals(BucketUnit.DAY, w.unit(BucketUnit.DAY))
        assertEquals(BucketUnit.MONTH, w.unit(BucketUnit.MONTH))
        assertEquals(BucketUnit.DAY, w.unit(null))
    }

    @Test fun unitThresholds() {
        val s = LocalDate.of(2026, 1, 1)
        assertEquals(BucketUnit.DAY, ChartWindow(s, s.plusDays(91)).unit(BucketUnit.MONTH))
        assertEquals(BucketUnit.MONTH, ChartWindow(s, s.plusDays(99)).unit(BucketUnit.DAY))
    }

    @Test fun calendarMonthMapsToMonthPreset() = assertEquals(
        PeriodState(PeriodKind.MONTH, offset = -1),
        ChartWindow(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)).toPeriodState(DayOfWeek.MONDAY, today),
    )

    @Test fun oddRangeMapsToCustom() = assertEquals(
        PeriodKind.CUSTOM,
        ChartWindow(LocalDate.of(2026, 10, 3), LocalDate.of(2026, 11, 8)).toPeriodState(DayOfWeek.MONDAY, today).kind,
    )

    @Test fun sundayWeekRecognised() = assertEquals(
        PeriodKind.WEEK,
        ChartWindow(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 7)).toPeriodState(DayOfWeek.SUNDAY, today).kind,
    )

    @Test fun weekOffsetRelativeToCurrentWeek() = assertEquals(
        PeriodState(PeriodKind.WEEK, offset = -3),
        ChartWindow(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 25)).toPeriodState(DayOfWeek.MONDAY, today),
    )

    @Test fun calendarYearMapsToYearPreset() = assertEquals(
        PeriodState(PeriodKind.YEAR, offset = -1),
        ChartWindow(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31)).toPeriodState(DayOfWeek.MONDAY, today),
    )

    @Test fun customCarriesDates() {
        val s = ChartWindow(LocalDate.of(2026, 10, 3), LocalDate.of(2026, 11, 8))
            .toPeriodState(DayOfWeek.MONDAY, today)
        assertEquals(PeriodState.custom(LocalDate.of(2026, 10, 3), LocalDate.of(2026, 11, 8)), s)
    }
}
