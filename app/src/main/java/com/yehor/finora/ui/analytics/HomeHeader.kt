package com.yehor.finora.ui.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yehor.finora.R
import com.yehor.finora.designsystem.component.button.RoundIconButton
import com.yehor.finora.designsystem.component.display.AmountText
import com.yehor.finora.designsystem.component.input.SegmentedPill
import com.yehor.finora.designsystem.component.sheet.RangeWheelsSheet
import com.yehor.finora.designsystem.icon.AppIcons
import com.yehor.finora.designsystem.icon.IconSize
import com.yehor.finora.designsystem.theme.PillShape
import com.yehor.finora.designsystem.theme.SpaceGrotesk
import com.yehor.finora.designsystem.theme.finance
import com.yehor.finora.designsystem.theme.hairline
import com.yehor.finora.domain.account.Account
import com.yehor.finora.domain.money.Estimate
import com.yehor.finora.ui.navigation.LocalOpenMore
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** "FINORA" wordmark and the round ⋯ that opens the More sheet. No search on Home. */
@Composable
internal fun HomeTopBar(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.app_name).uppercase(),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        RoundIconButton(AppIcons.NavMore, stringResource(R.string.nav_more), LocalOpenMore.current)
    }
}

/** Days | Months, the period chip (opens the range wheels) and, with several accounts, the account filter. */
@Composable
internal fun HomePeriodControls(
    period: HomePeriod,
    today: LocalDate,
    accounts: List<Account>,
    selectedAccountIds: Set<Long>,
    onMode: (HomeMode) -> Unit,
    onRange: (YearMonth, YearMonth) -> Unit,
    onToggleAccount: (Long) -> Unit,
    onClearAccounts: () -> Unit,
) {
    var showRange by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SegmentedPill(
            options = listOf(stringResource(R.string.home_days), stringResource(R.string.home_months)),
            selected = period.mode.ordinal,
            onSelect = { onMode(HomeMode.entries[it]) },
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HomeChip(homeChipLabel(period), onClick = { showRange = true }, clickLabel = stringResource(R.string.range_title))
            if (accounts.size > 1) {
                AccountSelector(
                    accounts = accounts,
                    selectedAccountIds = selectedAccountIds,
                    onToggleAccount = onToggleAccount,
                    onClearSelection = onClearAccounts,
                )
            }
        }
    }

    if (showRange) {
        val current = HomePeriod.current(period.mode, today)
        val quick = stringResource(if (period.mode == HomeMode.DAYS) R.string.period_this_month else R.string.period_this_year)
        RangeWheelsSheet(
            initialFrom = period.from,
            initialTo = minOf(period.to, YearMonth.from(today)),
            today = today,
            earliest = null,
            quickOptions = listOf(
                quick to {
                    showRange = false
                    onRange(current.from, current.to)
                },
            ),
            onConfirm = { from, to ->
                showRange = false
                onRange(from, to)
            },
            onDismiss = { showRange = false },
        )
    }
}

/** A compact pill with a dropdown caret; the whole 48dp-high box is the touch target. */
@Composable
private fun HomeChip(label: String, onClick: () -> Unit, clickLabel: String) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier.heightIn(min = 48.dp).clip(PillShape).clickable(role = Role.Button, onClickLabel = clickLabel, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .background(scheme.surfaceContainerHigh, PillShape)
                .border(hairline(MaterialTheme.finance.isDark), PillShape)
                .padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = scheme.onSurface, maxLines = 1)
            Icon(AppIcons.Dropdown, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(IconSize.Medium))
        }
    }
}

/** "NET IN NOVEMBER", big net (mint ≥ 0, red < 0, "≈" when converted) and the "In · Out" pill. */
@Composable
internal fun HomeHero(period: HomePeriod, today: LocalDate, chart: HomeChart?, onOpenTransactions: () -> Unit) {
    val finance = MaterialTheme.finance
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            homeHeroLabel(period, today),
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = 1.5.sp,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.semantics { heading() },
        )
        val net = chart?.net
        val estimate = net?.estimate?.takeIf { !net.overflowed }
        if (estimate == null) {
            Text(
                cellText(net),
                style = HeroStyle,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        } else {
            val color = if (estimate.amountMinor >= 0) finance.income else finance.negative
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.Bottom) {
                AmountText(heroNumber(estimate, chart.currencyApprox), style = HeroStyle, color = color)
                Text(
                    " ${estimate.currency.symbol}",
                    style = HeroStyle.copy(fontSize = HeroStyle.fontSize * 0.6f),
                    color = color,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
        }
        if (chart != null) {
            val inText = cellText(chart.income)
            val outText = cellText(chart.expense)
            val line = stringResource(R.string.home_in_out, inText, outText)
            val styled = buildAnnotatedString {
                append(line)
                val inAt = line.indexOf(inText)
                if (inAt >= 0) addStyle(SpanStyle(color = finance.income), inAt, inAt + inText.length)
                val outAt = line.lastIndexOf(outText)
                if (outAt >= 0) addStyle(SpanStyle(color = finance.negative), outAt, outAt + outText.length)
            }
            Box(
                Modifier.padding(top = 2.dp).heightIn(min = 48.dp).clip(PillShape)
                    .clickable(role = Role.Button, onClick = onOpenTransactions),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    styled,
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = SpaceGrotesk, fontFeatureSettings = "tnum"),
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .background(scheme.primary.copy(alpha = 0.10f), PillShape)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

private val HeroStyle
    @Composable get() = MaterialTheme.typography.displaySmall.copy(fontSize = 40.sp, lineHeight = 48.sp)

/** "+1240.00" / "−310.50", prefixed "≈ " when converted. The currency symbol is drawn separately. */
internal fun heroNumber(estimate: Estimate, approx: Boolean): String {
    val value = BigDecimal.valueOf(estimate.amountMinor, estimate.currency.scale).abs().toPlainString()
    val sign = when {
        estimate.amountMinor > 0 -> "+"
        estimate.amountMinor < 0 -> "−"
        else -> ""
    }
    return (if (approx) "≈ " else "") + sign + value
}

private enum class HomeSpan { MONTH, YEAR, RANGE }

private fun HomePeriod.span(): HomeSpan = when {
    monthCount == 1 -> HomeSpan.MONTH
    from.year == to.year && from.monthValue == 1 && to.monthValue == 12 -> HomeSpan.YEAR
    else -> HomeSpan.RANGE
}

private fun String.capitalized(locale: Locale) = replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }

private fun rangeText(period: HomePeriod, locale: Locale): String {
    val short = DateTimeFormatter.ofPattern("LLL yyyy", locale)
    return "${period.from.format(short).capitalized(locale)} – ${period.to.format(short).capitalized(locale)}"
}

/** Chip text: "November 2026", "2026" or "Nov 2025 – Feb 2026". */
@Composable
internal fun homeChipLabel(period: HomePeriod): String {
    val locale = LocalConfiguration.current.locales[0]
    return when (period.span()) {
        HomeSpan.MONTH -> period.from.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale)).capitalized(locale)
        HomeSpan.YEAR -> period.from.year.toString()
        HomeSpan.RANGE -> rangeText(period, locale)
    }
}

/** Hero label: "NET IN NOVEMBER" (year added when not the current one), "NET IN 2026", "NET · NOV 2025 – FEB 2026". */
@Composable
internal fun homeHeroLabel(period: HomePeriod, today: LocalDate): String {
    val locale = LocalConfiguration.current.locales[0]
    val text = when (period.span()) {
        HomeSpan.MONTH -> {
            val pattern = if (period.from.year == today.year) "LLLL" else "LLLL yyyy"
            stringResource(R.string.home_net_in, period.from.format(DateTimeFormatter.ofPattern(pattern, locale)))
        }
        HomeSpan.YEAR -> stringResource(R.string.home_net_in, period.from.year.toString())
        HomeSpan.RANGE -> stringResource(R.string.home_net_range, rangeText(period, locale))
    }
    return text.uppercase(locale)
}
