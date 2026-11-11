package com.yehor.finora.ui.analytics

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.yehor.finora.R
import com.yehor.finora.designsystem.chart.WaveChart
import com.yehor.finora.designsystem.chart.WaveGestures
import com.yehor.finora.designsystem.component.input.FilterPill
import com.yehor.finora.designsystem.component.list.GroupedCard
import com.yehor.finora.designsystem.motion.rememberReduceMotion
import com.yehor.finora.designsystem.theme.SpaceGrotesk
import com.yehor.finora.designsystem.theme.finance
import com.yehor.finora.domain.analytics.Bucket
import com.yehor.finora.domain.analytics.BucketUnit
import com.yehor.finora.domain.analytics.Measure
import com.yehor.finora.domain.money.Estimate
import com.yehor.finora.domain.money.EstimateBasis
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.util.Locale

/** After the last pan/pinch ends, the visible window becomes the selected period this much later. */
private const val COMMIT_DELAY_MS = 300L

/** A bucket-unit switch fades the chart in from this alpha over [UNIT_FADE_MS]. */
private const val UNIT_FADE_FROM = 0.15f
private const val UNIT_FADE_MS = 250

/**
 * The trend card: a wave of the selected period that the user drags through time and pinches to show
 * fewer or more days. [committed] is the selected period's window; while the fingers move the card
 * keeps its own window, asks for its data through [onPreview], and [onCommit]s it once the gesture has
 * settled. Tapping a point shows its value; the pill under the chart opens that bucket's transactions.
 *
 * Between data updates the wave slides with the fingers (a horizontal offset of the drawn data from the
 * card's window), so a drag moves every frame even while the next window's data is still loading. There
 * is one chart for the card's lifetime: a bucket-unit switch fades it instead of replacing it, so the
 * gesture in progress survives the switch.
 */
@Composable
internal fun StatsTrendCard(
    trend: StatsTrend?,
    committed: ChartWindow,
    today: LocalDate,
    currentName: String,
    previousName: String?,
    onPreview: (ChartWindow) -> Unit,
    onCommit: (ChartWindow) -> Unit,
    onOpenBucket: (StatsTrend, Bucket) -> Unit,
    modifier: Modifier = Modifier,
) {
    var window by remember { mutableStateOf(committed) }
    var active by remember { mutableStateOf(false) }
    var gestureEnds by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<Int?>(null) }
    val gesture = remember { WindowGesture() }
    // Sub-day pan not yet turned into a window change, for the frame-by-frame slide.
    var pendingDays by remember { mutableFloatStateOf(0f) }
    val currentToday by rememberUpdatedState(today)

    // A period picked elsewhere (segments, arrows, wheels) or a commit landing moves the card's window.
    LaunchedEffect(committed) {
        if (!active) {
            window = committed
            gesture.reset()
            pendingDays = 0f
        }
    }
    LaunchedEffect(gestureEnds) {
        if (gestureEnds == 0) return@LaunchedEffect
        delay(COMMIT_DELAY_MS)
        if (!active) onCommit(window)
    }
    // The last data that matches the card's window; while a preview is loading the old data stays.
    var shown by remember { mutableStateOf(trend) }
    LaunchedEffect(trend, window) {
        if (trend != null && (trend.window == window || shown == null)) shown = trend
    }

    fun moveTo(next: ChartWindow) {
        if (next == window) return
        window = next
        selected = null
        onPreview(next)
    }
    val gestures = WaveGestures(
        onPan = { fraction ->
            active = true
            moveTo(gesture.pan(window, fraction, currentToday))
            pendingDays = gesture.pendingDays.toFloat()
        },
        onZoom = { scale, focus ->
            active = true
            moveTo(gesture.zoom(window, scale, focus, currentToday))
        },
        onGestureEnd = {
            active = false
            gesture.reset()
            pendingDays = 0f
            gestureEnds++
        },
    )

    val data = shown
    GroupedCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(top = 14.dp, bottom = 12.dp)) {
            TrendHeader(data, Modifier.padding(horizontal = 16.dp))
            val reduceMotion = rememberReduceMotion()
            // A bucket-unit switch fades the one chart back in (no second instance, no lost gesture).
            val unitAlpha = remember { Animatable(1f) }
            var drawnUnit by remember { mutableStateOf(data?.unit) }
            LaunchedEffect(data?.unit) {
                val unit = data?.unit
                if (unit != null && drawnUnit != null && unit != drawnUnit && !reduceMotion) {
                    unitAlpha.snapTo(UNIT_FADE_FROM)
                    unitAlpha.animateTo(1f, tween(UNIT_FADE_MS))
                }
                drawnUnit = unit
            }
            // Days between the drawn data and the card's window (same length only: a pinch just waits).
            val slideDays: () -> Float = {
                val t = data
                if (t == null || t.window.days != window.days) {
                    0f
                } else {
                    val days = java.time.temporal.ChronoUnit.DAYS.between(t.window.start, window.start) + pendingDays
                    days.coerceIn(-window.days.toFloat(), window.days.toFloat())
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    // Clip only sideways (the slide), keeping the chart's vertical glow; the 16dp padding
                    // leaves room for its sideways bleed at rest.
                    .drawWithContent {
                        clipRect(0f, -size.height, size.width, size.height * 2) { this@drawWithContent.drawContent() }
                    }
                    .padding(horizontal = 16.dp)
                    .padding(top = 6.dp)
                    .height(150.dp)
                    .graphicsLayer { alpha = unitAlpha.value },
            ) {
                val t = data
                if (t != null && t.values.isNotEmpty()) {
                    val locale = LocalConfiguration.current.locales[0]
                    val none = stringResource(R.string.analytics_none)
                    WaveChart(
                        values = t.values,
                        modifier = Modifier.fillMaxSize(),
                        compare = t.compare,
                        selectedIndex = selected?.takeIf { it in t.values.indices },
                        onSelect = { selected = it },
                        label = { i -> bucketBubble(t, i, locale, today, none) },
                        gestures = gestures,
                        slide = { -slideDays() / (window.days - 1).coerceAtLeast(1) },
                    )
                }
            }
            if (data != null && data.buckets.isNotEmpty()) {
                TrendAxis(
                    data,
                    today,
                    Modifier.padding(horizontal = 16.dp, vertical = 4.dp).graphicsLayer { alpha = unitAlpha.value },
                )
            }
            val pick = selected?.let { i -> data?.buckets?.getOrNull(i) }
            if (data != null && pick != null) {
                val locale = LocalConfiguration.current.locales[0]
                FilterPill(
                    label = stringResource(R.string.stats_open_bucket, bucketName(pick, data.unit, today, locale)),
                    active = false,
                    onClick = { onOpenBucket(data, pick) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            if (data?.compare != null && previousName != null) {
                TrendLegend(currentName, previousName, Modifier.padding(horizontal = 16.dp).padding(top = 6.dp))
            }
        }
    }
}

/** "Daily spending" and "Avg 47.00 zł / day". */
@Composable
private fun TrendHeader(trend: StatsTrend?, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val monthly = trend?.unit == BucketUnit.MONTH
    val income = trend?.measure == Measure.INCOME
    val title = stringResource(
        when {
            income && monthly -> R.string.stats_monthly_income
            income -> R.string.stats_daily_income
            monthly -> R.string.stats_monthly_spending
            else -> R.string.stats_daily_spending
        },
    )
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        val average = trend?.average
        if (average != null) {
            val amount = (if (average.basis.usesConversion) "≈ " else "") + average.format()
            val line = stringResource(if (monthly) R.string.stats_avg_month else R.string.stats_avg_day, amount)
            val at = line.indexOf(amount)
            Text(
                buildAnnotatedString {
                    if (at < 0) {
                        append(line)
                    } else {
                        append(line.substring(0, at))
                        withStyle(SpanStyle(color = scheme.onSurface, fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold)) {
                            append(amount)
                        }
                        append(line.substring(at + amount.length))
                    }
                },
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                color = scheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/** First, middle and last bucket dates under the wave. */
@Composable
private fun TrendAxis(trend: StatsTrend, today: LocalDate, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    val buckets = trend.buckets
    val picks = listOf(0, buckets.lastIndex / 2, buckets.lastIndex).distinct()
    Row(modifier.fillMaxWidth(), horizontalArrangement = if (picks.size == 1) Arrangement.Center else Arrangement.SpaceBetween) {
        picks.forEach { i ->
            Text(
                axisName(buckets[i], trend.unit, buckets, today, locale),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Solid mint "This period" next to dashed grey "previous". */
@Composable
private fun TrendLegend(current: String, previous: String, modifier: Modifier = Modifier) {
    val variant = MaterialTheme.colorScheme.onSurfaceVariant
    val line = MaterialTheme.finance.income
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        LegendSwatch(line, dashed = false)
        Text(current, style = MaterialTheme.typography.labelSmall, color = variant, modifier = Modifier.padding(start = 6.dp))
        Spacer(Modifier.width(14.dp))
        LegendSwatch(variant, dashed = true)
        Text(previous, style = MaterialTheme.typography.labelSmall, color = variant, modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
private fun LegendSwatch(color: Color, dashed: Boolean) {
    Canvas(Modifier.size(width = 14.dp, height = 4.dp)) {
        drawLine(
            color,
            Offset(0f, size.height / 2),
            Offset(size.width, size.height / 2),
            strokeWidth = 2.dp.toPx(),
            pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx())) else null,
        )
    }
}

/** "Nov 18" for a day, "November 2026" for a month (clipped months show their date range). */
private fun bucketName(bucket: Bucket, unit: BucketUnit, today: LocalDate, locale: Locale): String = when (unit) {
    BucketUnit.DAY -> shortDate(bucket.fromDate, today, locale)
    else -> {
        val wholeMonth = bucket.fromDate.dayOfMonth == 1 && bucket.toDateInclusive == bucket.fromDate.withDayOfMonth(bucket.fromDate.lengthOfMonth())
        if (wholeMonth) {
            monthYearName(bucket.fromDate, locale)
        } else {
            "${shortDate(bucket.fromDate, today, locale)} – ${shortDate(bucket.toDateInclusive, today, locale)}"
        }
    }
}

private fun axisName(bucket: Bucket, unit: BucketUnit, all: List<Bucket>, today: LocalDate, locale: Locale): String = when (unit) {
    BucketUnit.DAY -> shortDate(bucket.fromDate, today, locale)
    else -> {
        val spansYears = all.first().fromDate.year != all.last().fromDate.year || all.first().fromDate.year != today.year
        bucket.fromDate.format(bestPattern(locale, if (spansYears) "MMMyy" else "LLL"))
    }
}

/** "Nov 18 · 210.00 zł". */
private fun bucketBubble(trend: StatsTrend, index: Int, locale: Locale, today: LocalDate, none: String): String {
    val bucket = trend.buckets.getOrNull(index) ?: return none
    val minor = trend.values.getOrNull(index)
    val amount = if (minor == null) none else Estimate(minor, trend.currency, EstimateBasis.NATIVE).format()
    return "${bucketName(bucket, trend.unit, today, locale)} · $amount"
}
