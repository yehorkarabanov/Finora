package com.yehor.finora.ui.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.yehor.finora.designsystem.theme.PreviewTheme
import com.yehor.finora.domain.analytics.Cell
import com.yehor.finora.domain.analytics.Measure
import com.yehor.finora.domain.analytics.PeriodCalculator
import com.yehor.finora.domain.analytics.PeriodKind
import com.yehor.finora.domain.analytics.PeriodState
import com.yehor.finora.domain.analytics.TrendPoint
import com.yehor.finora.domain.analytics.delta
import com.yehor.finora.domain.money.CurrencyRegistry
import com.yehor.finora.domain.money.Estimate
import com.yehor.finora.domain.money.EstimateBasis
import com.yehor.finora.domain.operation.TransactionFilter
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun StatisticsMonthPreview() = StatisticsPreview(dark = false, PeriodState.preset(PeriodKind.MONTH))

@Preview(showBackground = true, heightDp = 900, backgroundColor = 0xFF121314)
@Composable
private fun StatisticsMonthDarkPreview() = StatisticsPreview(dark = true, PeriodState.preset(PeriodKind.MONTH))

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun StatisticsCustomPreview() =
    StatisticsPreview(dark = false, PeriodState.custom(LocalDate.of(2026, 10, 3), LocalDate.of(2026, 11, 10)))

@Preview(showBackground = true, heightDp = 900, backgroundColor = 0xFF121314)
@Composable
private fun StatisticsCustomDarkPreview() =
    StatisticsPreview(dark = true, PeriodState.custom(LocalDate.of(2026, 10, 3), LocalDate.of(2026, 11, 10)))

/** The restyled top of Expenses: top bar, tabs, period block, hero and trend card. */
@Composable
private fun StatisticsPreview(dark: Boolean, state: PeriodState) {
    val pln = CurrencyRegistry.get("PLN")
    val zone = ZoneOffset.UTC
    val today = LocalDate.of(2026, 11, 10)
    val period = state.resolve(today, zone, DayOfWeek.MONDAY)
    val previous = PeriodCalculator.resolve(PeriodCalculator.previous(period.period), zone, DayOfWeek.MONDAY)
    fun cell(minor: Long) = Cell(Estimate(minor, pln, EstimateBasis.NATIVE), 3, emptySet(), false, null)
    val window = displayWindow(period, today)
    val visible = PeriodCalculator.resolve(
        com.yehor.finora.domain.analytics.Period(PeriodKind.CUSTOM, window.start, window.end), zone, DayOfWeek.MONDAY,
    )
    val (unit, buckets) = trendBuckets(visible, zone, null)
    val points = buckets.mapIndexed { i, b -> TrendPoint(b, cell(4_000L + (i * 7_919L) % 21_000L)) }
    val compare = buckets.indices.map { i -> 6_000L + (i * 5_303L) % 15_000L }
    val trend = StatsTrend(
        window = window,
        measure = Measure.EXPENSE,
        unit = unit,
        buckets = buckets,
        values = trendValues(points, today),
        compare = compare,
        average = trendAverage(points, today, pln),
        currency = pln,
        base = TransactionFilter(),
    )
    val total = cell(142_050L)
    PreviewTheme(dark) {
        Column(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatsTopBar(onOptions = {})
            StatsTabs(StatsTab.EXPENSES, {})
            StatsPeriodBlock(
                state = state,
                period = period,
                today = today,
                canShiftForward = false,
                onSelect = {},
                onShift = {},
                onMonths = { _, _ -> },
                onCustom = { _, _ -> },
            )
            StatsHero(
                measure = Measure.EXPENSE,
                state = state,
                period = period,
                previous = previous,
                today = today,
                total = total,
                delta = delta(total.estimate, cell(154_400L).estimate),
            )
            StatsTrendCard(
                trend = trend,
                committed = window,
                today = today,
                currentName = periodShortName(period, today),
                previousName = periodShortName(previous, today),
                onPreview = {},
                onCommit = {},
                onOpenBucket = { _, _ -> },
            )
        }
    }
}
