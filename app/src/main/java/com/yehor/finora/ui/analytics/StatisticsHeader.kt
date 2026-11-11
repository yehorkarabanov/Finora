package com.yehor.finora.ui.analytics

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yehor.finora.R
import com.yehor.finora.designsystem.component.button.RoundIconButton
import com.yehor.finora.designsystem.component.display.AmountText
import com.yehor.finora.designsystem.component.input.SegmentedPill
import com.yehor.finora.designsystem.component.list.GroupedCard
import com.yehor.finora.designsystem.component.list.ListRow
import com.yehor.finora.designsystem.component.list.RoundIcon
import com.yehor.finora.designsystem.component.list.SectionHeader
import com.yehor.finora.designsystem.component.sheet.FinoraSheet
import com.yehor.finora.designsystem.component.sheet.RangeWheelsSheet
import com.yehor.finora.designsystem.icon.AppIcons
import com.yehor.finora.designsystem.icon.IconSize
import com.yehor.finora.designsystem.motion.rememberReduceMotion
import com.yehor.finora.designsystem.theme.PillShape
import com.yehor.finora.designsystem.theme.finance
import com.yehor.finora.designsystem.theme.hairline
import com.yehor.finora.domain.account.Account
import com.yehor.finora.domain.analytics.Cell
import com.yehor.finora.domain.analytics.Delta
import com.yehor.finora.domain.analytics.Measure
import com.yehor.finora.domain.analytics.PeriodKind
import com.yehor.finora.domain.analytics.PeriodState
import com.yehor.finora.domain.analytics.ResolvedPeriod
import com.yehor.finora.ui.common.ColorPalette
import com.yehor.finora.ui.common.DateRangePickerDialog
import com.yehor.finora.ui.common.IconBadge
import java.time.LocalDate
import java.time.YearMonth

/** "Statistics" and the ☰ button that opens the account filter and Compare switch. */
@Composable
internal fun StatsTopBar(onOptions: (() -> Unit)?, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.nav_statistics),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (onOptions != null) RoundIconButton(AppIcons.Filter, stringResource(R.string.stats_options), onOptions)
    }
}

/** Text tabs; a mint underline slides to the selected one. */
@Composable
internal fun StatsTabs(selected: StatsTab, onSelect: (StatsTab) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val reduceMotion = rememberReduceMotion()
    val bounds = remember { mutableStateMapOf<StatsTab, Pair<Dp, Dp>>() }
    Box(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(1.dp)
                .background(scheme.outlineVariant.copy(alpha = 0.4f)),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            StatsTab.entries.forEach { tab ->
                val isSelected = tab == selected
                Box(
                    Modifier
                        .heightIn(min = 48.dp)
                        .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(tab) })
                        .onPlaced { coordinates ->
                            with(density) {
                                bounds[tab] = coordinates.positionInParent().x.toDp() to coordinates.size.width.toDp()
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(tab.titleRes()),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) scheme.onSurface else scheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
        val target = bounds[selected]
        if (target != null) {
            val spec = if (reduceMotion) snap<Dp>() else androidx.compose.animation.core.spring(stiffness = 500f)
            val x by animateDpAsState(target.first, spec, label = "tabX")
            val w by animateDpAsState(target.second, spec, label = "tabW")
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = x)
                    .width(w)
                    .height(3.dp)
                    .background(scheme.primary, RoundedCornerShape(3.dp)),
            )
        }
    }
}

private fun StatsTab.titleRes(): Int = when (this) {
    StatsTab.EXPENSES -> R.string.stats_tab_expenses
    StatsTab.INCOME -> R.string.stats_tab_income
    StatsTab.TAGS -> R.string.stats_tab_tags
    StatsTab.ACCOUNTS -> R.string.stats_tab_accounts
}

private val PeriodSegments = listOf(PeriodKind.WEEK, PeriodKind.MONTH, PeriodKind.YEAR)

/**
 * Week | Month | Year (nothing selected for Today and custom ranges) and the `‹ name ▾ ›` stepper.
 * The name opens the range wheels with "Today" and "Custom days" quick options.
 */
@Composable
internal fun StatsPeriodBlock(
    state: PeriodState,
    period: ResolvedPeriod,
    today: LocalDate,
    canShiftForward: Boolean,
    onSelect: (PeriodKind) -> Unit,
    onShift: (Int) -> Unit,
    onMonths: (YearMonth, YearMonth) -> Unit,
    onCustom: (LocalDate, LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showRange by rememberSaveable { mutableStateOf(false) }
    var showPicker by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SegmentedPill(
            options = listOf(
                stringResource(R.string.period_week),
                stringResource(R.string.period_month),
                stringResource(R.string.period_year),
            ),
            selected = PeriodSegments.indexOf(state.kind),
            onSelect = { onSelect(PeriodSegments[it]) },
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            RoundIconButton(AppIcons.Previous, stringResource(R.string.analytics_period_previous), { onShift(-1) })
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Row(
                    Modifier
                        .heightIn(min = 48.dp)
                        .clip(PillShape)
                        .clickable(role = Role.Button, onClickLabel = stringResource(R.string.range_title)) { showRange = true }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stepperName(state, period, today),
                        style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Icon(
                        AppIcons.Dropdown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(IconSize.Medium),
                    )
                }
            }
            RoundIconButton(
                AppIcons.Next,
                stringResource(R.string.analytics_period_next),
                { onShift(1) },
                enabled = canShiftForward,
            )
        }
    }

    if (showRange) {
        val todayText = stringResource(R.string.period_today)
        val customText = stringResource(R.string.stats_custom_days)
        RangeWheelsSheet(
            initialFrom = YearMonth.from(period.fromDate),
            initialTo = YearMonth.from(minOf(period.toDateInclusive, today)),
            today = today,
            earliest = null,
            quickOptions = listOf(
                todayText to {
                    showRange = false
                    onSelect(PeriodKind.TODAY)
                },
                customText to {
                    showRange = false
                    showPicker = true
                },
            ),
            onConfirm = { from, to ->
                showRange = false
                onMonths(from, to)
            },
            onDismiss = { showRange = false },
        )
    }
    if (showPicker) {
        DateRangePickerDialog(
            initialStart = period.fromDate,
            initialEnd = period.toDateInclusive,
            onDismiss = { showPicker = false },
            onConfirm = { start, end ->
                showPicker = false
                onCustom(start, end)
            },
            maxDate = today,
        )
    }
}

/** The ☰ sheet: the account filter, where it applies the Compare switch and, on Tags, the filter sheet. */
@Composable
internal fun StatsOptionsSheet(
    accounts: List<Account>,
    selectedAccountIds: Set<Long>,
    onToggleAccount: (Long) -> Unit,
    onClearAccounts: () -> Unit,
    compare: Boolean,
    previousLabel: String?,
    onCompare: ((Boolean) -> Unit)?,
    onDismiss: () -> Unit,
    onFilter: (() -> Unit)? = null,
) {
    FinoraSheet(onDismiss = onDismiss) {
        Column(Modifier.padding(horizontal = 18.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (onFilter != null) {
                GroupedCard {
                    ListRow(
                        icon = { RoundIcon(AppIcons.Filter, MaterialTheme.colorScheme.primary, size = 36.dp) },
                        title = stringResource(R.string.stats_filter),
                        subtitle = stringResource(R.string.stats_tags_filter_hint),
                        trailing = { Icon(AppIcons.Next, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        onClick = onFilter,
                    )
                }
            }
            if (onCompare != null) {
                GroupedCard {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .clickable(role = Role.Switch) { onCompare(!compare) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.stats_compare), style = MaterialTheme.typography.bodyLarge)
                            if (previousLabel != null) {
                                Text(
                                    stringResource(R.string.stats_compare_previous, previousLabel),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Switch(checked = compare, onCheckedChange = null)
                    }
                }
            }
            if (accounts.isNotEmpty()) {
                SectionHeader(stringResource(R.string.stats_tab_accounts))
                GroupedCard {
                    ListRow(
                        icon = { Box(Modifier.size(36.dp)) },
                        title = stringResource(R.string.filter_all_accounts_selected),
                        subtitle = null,
                        trailing = { Checkbox(checked = selectedAccountIds.isEmpty(), onCheckedChange = null) },
                        onClick = onClearAccounts,
                    )
                    accounts.forEach { account ->
                        ListRow(
                            icon = { IconBadge(account.icon, account.color, ColorPalette.ACCOUNT_DEFAULT, size = 36) },
                            title = account.name,
                            subtitle = null,
                            trailing = { Checkbox(checked = account.id in selectedAccountIds, onCheckedChange = null) },
                            onClick = { onToggleAccount(account.id) },
                        )
                    }
                }
            }
        }
    }
}

/** "SPENT IN NOVEMBER", the period total and, with Compare on, "↘ 8% less than October". */
@Composable
internal fun StatsHero(
    measure: Measure,
    state: PeriodState,
    period: ResolvedPeriod,
    previous: ResolvedPeriod?,
    today: LocalDate,
    total: Cell,
    delta: Delta?,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val finance = MaterialTheme.finance
    val locale = LocalConfiguration.current.locales[0]
    Column(modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            heroLabel(measure, state, period, today).uppercase(locale),
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = 1.4.sp,
            fontWeight = FontWeight.Bold,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.semantics { heading() },
        )
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            val converted = total.estimate?.basis?.usesConversion == true && !total.overflowed
            AmountText(
                (if (converted) "≈ " else "") + cellText(total),
                style = MaterialTheme.typography.displaySmall.copy(fontSize = 40.sp, lineHeight = 48.sp),
                color = scheme.onSurface,
            )
            if (total.partial) {
                Icon(
                    AppIcons.Warning,
                    contentDescription = stringResource(R.string.analytics_partial_warning),
                    tint = scheme.error,
                    modifier = Modifier.padding(start = 6.dp).size(20.dp),
                )
            }
        }
        if (delta != null && previous != null) {
            val (text, tint) = comparison(measure, delta, previous, today)
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = tint,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .background(tint.copy(alpha = 0.10f), PillShape)
                    .border(hairline(finance.isDark), PillShape)
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            )
        }
    }
}

/** "↘ 8% less than October" and its tint: mint for the good direction, red for the other, grey for no change. */
@Composable
internal fun comparison(measure: Measure, delta: Delta, previous: ResolvedPeriod, today: LocalDate): Pair<String, Color> {
    val scheme = MaterialTheme.colorScheme
    val finance = MaterialTheme.finance
    val percent = delta.percentTimesTen
    val previousName = periodShortName(previous, today)
    val down = delta.amount.amountMinor < 0
    val text = when {
        delta.amount.amountMinor == 0L -> stringResource(R.string.stats_same_as, previousName)
        percent == null -> deltaText(delta)
        down -> "↘ " + stringResource(R.string.stats_less_than, wholePercent(percent), previousName)
        else -> "↗ " + stringResource(R.string.stats_more_than, wholePercent(percent), previousName)
    }
    // Spending less, or earning more, is the good direction.
    val good = if (measure == Measure.INCOME) !down else down
    val tint = when {
        delta.amount.amountMinor == 0L -> scheme.onSurfaceVariant
        good -> finance.income
        else -> finance.negative
    }
    return text to tint
}

/** "8%" from tenths of a percent, rounded half up, without a sign. */
internal fun wholePercent(percentTimesTen: Int): String = "${(Math.abs(percentTimesTen.toLong()) + 5) / 10}%"

/** "Spent in November", "Earned in 2026", "Spent · Oct 3 – Nov 18". */
@Composable
private fun heroLabel(measure: Measure, state: PeriodState, period: ResolvedPeriod, today: LocalDate): String {
    val income = measure == Measure.INCOME
    return when (state.kind) {
        PeriodKind.MONTH, PeriodKind.YEAR ->
            stringResource(if (income) R.string.stats_earned_in else R.string.stats_spent_in, periodShortName(period, today))
        else ->
            stringResource(if (income) R.string.stats_earned_range else R.string.stats_spent_range, stepperName(state, period, today))
    }
}
