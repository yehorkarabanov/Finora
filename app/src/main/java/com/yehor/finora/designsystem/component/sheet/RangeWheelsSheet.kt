package com.yehor.finora.designsystem.component.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yehor.finora.R
import com.yehor.finora.designsystem.component.button.PrimaryButton
import com.yehor.finora.designsystem.component.input.FilterPill
import com.yehor.finora.designsystem.theme.PillShape
import com.yehor.finora.designsystem.theme.finance
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.launch

private val ItemHeight = 44.dp
private val WheelHeight = ItemHeight * 5

/** Month-range picker in a bottom sheet: FROM and TO wheel pairs, month-count chip, confirm CTA. */
@Composable
fun RangeWheelsSheet(
    initialFrom: YearMonth,
    initialTo: YearMonth,
    today: LocalDate,
    earliest: LocalDate?,
    quickOptions: List<Pair<String, () -> Unit>>,
    onConfirm: (YearMonth, YearMonth) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FinoraSheet(onDismiss = onDismiss, modifier = modifier) {
        RangeWheelsContent(initialFrom, initialTo, today, earliest, quickOptions, onConfirm)
    }
}

@Composable
internal fun RangeWheelsContent(
    initialFrom: YearMonth,
    initialTo: YearMonth,
    today: LocalDate,
    earliest: LocalDate?,
    quickOptions: List<Pair<String, () -> Unit>>,
    onConfirm: (YearMonth, YearMonth) -> Unit,
    modifier: Modifier = Modifier,
) {
    val years = remember(today, earliest) { RangeWheelsLogic.years(today, earliest) }
    val locale = Locale.getDefault()
    val monthNames = remember(locale) { Month.entries.map { it.getDisplayName(TextStyle.SHORT, locale) } }
    val current = YearMonth.from(today)

    var fromMonth by remember { mutableIntStateOf(initialFrom.monthValue - 1) }
    var fromYear by remember { mutableIntStateOf(years.indexOf(initialFrom.year).coerceAtLeast(0)) }
    var toMonth by remember { mutableIntStateOf(initialTo.monthValue - 1) }
    var toYear by remember { mutableIntStateOf(years.indexOf(initialTo.year).coerceAtLeast(0)) }

    val from = YearMonth.of(years[fromYear], fromMonth + 1)
    val to = YearMonth.of(years[toYear], toMonth + 1)
    val validity = RangeWheelsLogic.validate(from, to, today)
    fun label(ym: YearMonth) = "${monthNames[ym.monthValue - 1]} ${ym.year}"

    Column(modifier.padding(bottom = 22.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.range_title), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            CountChip(validity)
        }
        if (quickOptions.isNotEmpty()) {
            Row(
                Modifier.padding(top = 12.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                quickOptions.forEach { (text, action) -> FilterPill(label = text, active = false, onClick = action) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 18.dp)) {
            WheelHeading(stringResource(R.string.range_from), Modifier.weight(1f))
            Box(Modifier.width(24.dp))
            WheelHeading(stringResource(R.string.range_to), Modifier.weight(1f))
        }
        Box(Modifier.fillMaxWidth().padding(top = 8.dp).height(WheelHeight), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(ItemHeight)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(16.dp))
                    .border(1.dp, Color.White.copy(alpha = if (MaterialTheme.finance.isDark) 0.06f else 0f), RoundedCornerShape(16.dp)),
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Wheel(
                    items = monthNames,
                    selected = fromMonth,
                    onSelect = { fromMonth = it },
                    selectedColor = MaterialTheme.colorScheme.onSurface,
                    isFuture = { YearMonth.of(years[fromYear], it + 1).isAfter(current) },
                    modifier = Modifier.weight(1f),
                )
                Wheel(
                    items = years.map { it.toString() },
                    selected = fromYear,
                    onSelect = { fromYear = it },
                    selectedColor = MaterialTheme.colorScheme.onSurface,
                    isFuture = { YearMonth.of(years[it], fromMonth + 1).isAfter(current) },
                    modifier = Modifier.weight(1f),
                )
                Text("→", color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center, fontSize = 16.sp, modifier = Modifier.width(24.dp))
                Wheel(
                    items = monthNames,
                    selected = toMonth,
                    onSelect = { toMonth = it },
                    selectedColor = MaterialTheme.colorScheme.primary,
                    isFuture = { YearMonth.of(years[toYear], it + 1).isAfter(current) },
                    modifier = Modifier.weight(1f),
                )
                Wheel(
                    items = years.map { it.toString() },
                    selected = toYear,
                    onSelect = { toYear = it },
                    selectedColor = MaterialTheme.colorScheme.primary,
                    isFuture = { YearMonth.of(years[it], toMonth + 1).isAfter(current) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        PrimaryButton(
            text = when (validity) {
                is RangeValidity.Valid -> stringResource(R.string.range_show, label(from), label(to))
                RangeValidity.EndBeforeStart -> stringResource(R.string.range_invalid_order)
                RangeValidity.InFuture -> stringResource(R.string.range_in_future)
            },
            onClick = { onConfirm(from, to) },
            enabled = validity is RangeValidity.Valid,
            modifier = Modifier.padding(top = 22.dp),
        )
    }
}

@Composable
private fun WheelHeading(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}

@Composable
private fun CountChip(validity: RangeValidity) {
    val color = if (validity is RangeValidity.Valid) MaterialTheme.colorScheme.primary else MaterialTheme.finance.negative
    val text = when (validity) {
        is RangeValidity.Valid -> pluralStringResource(R.plurals.range_months_count, validity.months, validity.months)
        RangeValidity.EndBeforeStart -> stringResource(R.string.range_invalid_order)
        RangeValidity.InFuture -> stringResource(R.string.range_in_future)
    }
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = Modifier.background(color.copy(alpha = 0.12f), PillShape).padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

/** One snapping wheel: 5 rows visible, the row centered in the band is selected; edges fade out. */
@Composable
private fun Wheel(
    items: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    selectedColor: Color,
    isFuture: (Int) -> Boolean,
    modifier: Modifier = Modifier,
) {
    val state = rememberLazyListState(initialFirstVisibleItemIndex = selected.coerceIn(0, items.lastIndex))
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val centered by remember(state) {
        derivedStateOf {
            val half = state.layoutInfo.visibleItemsInfo.firstOrNull()?.size?.div(2) ?: 0
            (state.firstVisibleItemIndex + if (state.firstVisibleItemScrollOffset > half) 1 else 0)
        }
    }
    LaunchedEffect(state) {
        var last = selected
        snapshotFlow { centered.coerceIn(0, items.lastIndex) }.collect { index ->
            if (index != last) {
                last = index
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onSelect(index)
            }
        }
    }
    LazyColumn(
        state = state,
        flingBehavior = rememberSnapFlingBehavior(state),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = ItemHeight * 2),
        modifier = modifier
            .height(WheelHeight)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(
                    Brush.verticalGradient(0f to Color.Transparent, 0.35f to Color.Black, 0.65f to Color.Black, 1f to Color.Transparent),
                    blendMode = BlendMode.DstIn,
                )
            },
    ) {
        itemsIndexed(items) { index, text ->
            val isSelected = index == centered
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(ItemHeight)
                    .alpha(if (isFuture(index)) 0.25f else 1f)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        scope.launch { state.animateScrollToItem(index) }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text,
                    fontSize = if (isSelected) 20.sp else 17.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) selectedColor else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
