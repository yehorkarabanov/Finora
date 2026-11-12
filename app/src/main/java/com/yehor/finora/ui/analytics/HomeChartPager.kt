package com.yehor.finora.ui.analytics

import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yehor.finora.R
import com.yehor.finora.designsystem.chart.WaveChart
import com.yehor.finora.designsystem.motion.rememberReduceMotion
import com.yehor.finora.designsystem.theme.PillShape
import com.yehor.finora.designsystem.theme.finance
import com.yehor.finora.designsystem.theme.hairline
import com.yehor.finora.domain.analytics.Bucket
import com.yehor.finora.domain.analytics.BucketUnit
import com.yehor.finora.domain.money.Currency
import com.yehor.finora.domain.money.Estimate
import com.yehor.finora.domain.money.EstimateBasis
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Far enough back for any real history; page 0 is the latest period, higher pages go back in time. */
private const val HOME_PAGE_COUNT = 1200
private const val DOT_COUNT = 5
private val CardShape = RoundedCornerShape(24.dp)

/**
 * The swipeable net wave. Page 0 is the latest period of [period]'s chain (never after today); swiping
 * right goes back by the period's length. Each page observes its own chart via [chartFor]. When a page
 * settles, [onShow] makes that page's period Home's period, so the hero, label and chip follow.
 *
 * The card is not clipped and every page keeps 16dp around the chart: the wave draws a 16dp bleed.
 */
@Composable
internal fun HomeChartPager(
    period: HomePeriod,
    today: LocalDate,
    display: Currency?,
    chartFor: (HomePeriod) -> Flow<HomeChart>,
    onShow: (HomePeriod) -> Unit,
    modifier: Modifier = Modifier,
) {
    val page = period.stepsToLatest(today)
    val anchor = period.shifted(page)
    val currentPage by rememberUpdatedState(page)
    val currentOnShow by rememberUpdatedState(onShow)
    val haptics = LocalHapticFeedback.current
    val reduceMotion = rememberReduceMotion()

    Column(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, CardShape)
            .border(hairline(MaterialTheme.finance.isDark), CardShape)
            .padding(bottom = 12.dp),
    ) {
        // Re-created only when the chain changes (mode, length or alignment), not on every settle.
        key(period.mode, anchor) {
            val pagerState = rememberPagerState(initialPage = page) { HOME_PAGE_COUNT }
            LaunchedEffect(pagerState) {
                snapshotFlow { pagerState.settledPage }.collect { settled ->
                    if (settled != currentPage) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        // Absolute target: a lagging currentPage can never cause a double shift.
                        currentOnShow(anchor.shifted(-settled))
                    }
                }
            }
            // A period chosen elsewhere (range sheet) on the same chain: move the pager to it.
            LaunchedEffect(page) {
                if (pagerState.settledPage != page) {
                    if (reduceMotion) pagerState.scrollToPage(page) else pagerState.animateScrollToPage(page)
                }
            }
            HorizontalPager(
                state = pagerState,
                reverseLayout = true,
                beyondViewportPageCount = 1,
                key = { it },
                modifier = Modifier.fillMaxWidth(),
            ) { index ->
                // Only the settled page breathes; the pre-composed neighbours stay idle.
                HomeChartPage(anchor.shifted(-index), today, display, chartFor, live = pagerState.settledPage == index)
            }
            PageDots(pagerState.currentPage, Modifier.fillMaxWidth().padding(top = 4.dp))
        }
    }
}

@Composable
private fun HomeChartPage(
    period: HomePeriod,
    today: LocalDate,
    display: Currency?,
    chartFor: (HomePeriod) -> Flow<HomeChart>,
    live: Boolean,
) {
    val flow = remember(period) { chartFor(period) }
    val chart by flow.collectAsStateWithLifecycle(initialValue = null)
    var selected by remember(period) { mutableStateOf<Int?>(null) }
    val locale = LocalConfiguration.current.locales[0]
    val none = stringResource(R.string.analytics_none)

    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
        Box(Modifier.fillMaxWidth().height(150.dp)) {
            val c = chart
            if (c != null) {
                val currency = c.net.estimate?.currency ?: c.income.estimate?.currency ?: display
                WaveChart(
                    values = c.values,
                    modifier = Modifier.fillMaxSize(),
                    zeroLine = true,
                    selectedIndex = selected,
                    onSelect = { selected = it },
                    label = { i -> bubbleLabel(c, i, currency, locale, none) },
                    live = live,
                )
            }
        }
        val c = chart
        val todayIndex = period.lastElapsedIndex(today)
        val n = c?.buckets?.size ?: 0
        val showsToday = c != null && todayIndex in 0 until n && !today.isAfter(period.to.atEndOfMonth())
        Box(Modifier.fillMaxWidth().height(18.dp).padding(top = 2.dp)) {
            if (showsToday) {
                val fraction = if (n <= 1) 0.5f else todayIndex.toFloat() / (n - 1)
                Text(
                    stringResource(R.string.home_today),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.layout { measurable, constraints ->
                        val p = measurable.measure(constraints.copy(minWidth = 0))
                        val width = constraints.maxWidth
                        layout(width, p.height) {
                            val x = (fraction * width - p.width / 2f).roundToInt().coerceIn(0, (width - p.width).coerceAtLeast(0))
                            p.place(x, 0)
                        }
                    },
                )
            }
        }
        if (c != null && c.buckets.isNotEmpty()) {
            AxisLabels(c.unit, c.buckets, locale)
        }
    }
}

@Composable
private fun AxisLabels(unit: BucketUnit, buckets: List<Bucket>, locale: Locale) {
    val spansYears = buckets.first().fromDate.year != buckets.last().fromDate.year
    val format = when (unit) {
        BucketUnit.DAY -> DateTimeFormatter.ofPattern("d MMM", locale)
        else -> DateTimeFormatter.ofPattern(if (spansYears) "LLL yy" else "LLL", locale)
    }
    val picks = listOf(0, buckets.lastIndex / 2, buckets.lastIndex).distinct()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        picks.forEach { i ->
            Text(
                buckets[i].fromDate.format(format),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** "12 Nov · +1240.00 zł" for a day, "Apr · −640.00 zł" for a month; "≈" when converted. */
private fun bubbleLabel(chart: HomeChart, index: Int, currency: Currency?, locale: Locale, none: String): String {
    val bucket = chart.buckets.getOrNull(index) ?: return none
    val date = when (chart.unit) {
        BucketUnit.DAY -> bucket.fromDate.format(DateTimeFormatter.ofPattern("d MMM", locale))
        else -> monthYearName(bucket.fromDate, locale)
    }
    val minor = chart.values.getOrNull(index)
    val amount = if (minor == null || currency == null) {
        none
    } else {
        (if (chart.currencyApprox) "≈ " else "") + signedText(Estimate(minor, currency, EstimateBasis.NATIVE))
    }
    return "$date · $amount"
}

/** Five dots, newest at the right; the active one stretches. Pages past the fifth keep the leftmost lit. */
@Composable
private fun PageDots(page: Int, modifier: Modifier = Modifier) {
    val active = page.coerceIn(0, DOT_COUNT - 1)
    val reduce = rememberReduceMotion()
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(5.dp, androidx.compose.ui.Alignment.CenterHorizontally)) {
        for (i in DOT_COUNT - 1 downTo 0) {
            val on = i == active
            val width = animateDpAsState(
                if (on) 18.dp else 6.dp,
                animationSpec = if (reduce) snap() else spring(),
                label = "dot",
            )
            Box(
                Modifier
                    // Width read in layout only: the spring re-measures the dot, never recomposes it.
                    .layout { measurable, _ ->
                        val px = width.value.roundToPx()
                        val p = measurable.measure(Constraints.fixed(px, 6.dp.roundToPx()))
                        layout(p.width, p.height) { p.place(0, 0) }
                    }
                    .background(
                        if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                        PillShape,
                    ),
            )
        }
    }
}
