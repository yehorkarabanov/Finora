package com.yehor.finora.ui.analytics

import com.yehor.finora.designsystem.icon.AppIcons
import com.yehor.finora.designsystem.icon.IconSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yehor.finora.R
import com.yehor.finora.domain.account.Account
import com.yehor.finora.domain.analytics.Cell
import com.yehor.finora.domain.analytics.CurrencyScope
import com.yehor.finora.domain.analytics.Delta
import com.yehor.finora.domain.analytics.PeriodKind
import com.yehor.finora.domain.analytics.PeriodState
import com.yehor.finora.domain.analytics.ResolvedPeriod
import com.yehor.finora.domain.analytics.Share
import com.yehor.finora.domain.money.Estimate
import com.yehor.finora.ui.accounts.NotIncludedLine
import com.yehor.finora.ui.accounts.formatDate
import com.yehor.finora.ui.common.ColorPalette
import com.yehor.finora.ui.common.DateRangePickerDialog
import com.yehor.finora.ui.common.IconBadge
import com.yehor.finora.ui.common.compactRangeLabel
import com.yehor.finora.designsystem.theme.finance
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import com.yehor.finora.designsystem.theme.AmountStyle

// ---- Text helpers ----

/** "+12.00 zł", "−5.00 zł". The sign is explicit for any non-zero value. */
fun signedText(estimate: Estimate): String {
    val value = BigDecimal.valueOf(estimate.amountMinor, estimate.currency.scale)
    val sign = when {
        estimate.amountMinor > 0 -> "+"
        estimate.amountMinor < 0 -> "−"
        else -> ""
    }
    return "$sign${value.abs().toPlainString()} ${estimate.currency.symbol}"
}

/** "12.4%" from permille. */
fun formatPermille(share: Share): String = "${share.permille / 10}.${share.permille % 10}%"

/** The amount of a cell, "Unavailable" when it cannot be shown (R5, R13), "—" when there is nothing. */
@Composable
fun cellText(cell: Cell?): String {
    if (cell == null) return stringResource(R.string.analytics_none)
    val estimate = cell.estimate
    return when {
        cell.overflowed -> stringResource(R.string.valuation_unavailable)
        estimate == null ->
            stringResource(if (cell.partial) R.string.valuation_unavailable else R.string.analytics_none)
        else -> estimate.format()
    }
}

@Composable
fun deltaText(delta: Delta?): String {
    if (delta == null) return ""
    val percent = delta.percentTimesTen
    return when {
        percent != null -> "${signedText(delta.amount)}, ${formatPercentTimesTen(percent)}"
        delta.amount.amountMinor > 0 -> stringResource(R.string.analytics_delta_new)
        else -> signedText(delta.amount)
    }
}

@Composable
fun rangeLabel(period: ResolvedPeriod): String {
    val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    val from = period.fromDate.format(formatter)
    return if (period.fromDate == period.toDateInclusive) {
        from
    } else {
        stringResource(R.string.analytics_range, from, period.toDateInclusive.format(formatter))
    }
}

/** Short human label: "Today", "3 – 9 Nov", "November 2026", "2026", or a custom range. */
@Composable
fun periodTitle(state: PeriodState, period: ResolvedPeriod): String {
    val locale = LocalConfiguration.current.locales[0]
    val from = period.fromDate
    val to = period.toDateInclusive
    val dayMonth = DateTimeFormatter.ofPattern("d MMM", locale)
    val full = DateTimeFormatter.ofPattern("d MMM yyyy", locale)
    return when (state.kind) {
        PeriodKind.TODAY ->
            if (state.offset == 0) stringResource(R.string.period_today) else from.format(full)
        PeriodKind.WEEK -> "${from.format(dayMonth)} – ${to.format(if (to.year == LocalDate.now().year) dayMonth else full)}"
        PeriodKind.MONTH -> from.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
        PeriodKind.YEAR -> from.year.toString()
        PeriodKind.CUSTOM -> compactRangeLabel(from, to, locale)
    }
}

// ---- Amount ----

@Composable
fun AmountText(
    cell: Cell?,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = AmountStyle,
    color: Color = Color.Unspecified,
    signed: Boolean = false,
) {
    val estimate = cell?.estimate
    val text = if (signed && estimate != null && cell.isShowable()) signedText(estimate) else cellText(cell)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = style, color = color, fontWeight = FontWeight.SemiBold)
        if (cell != null && cell.partial) {
            Icon(
                AppIcons.Warning,
                contentDescription = stringResource(R.string.analytics_partial_warning),
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 4.dp).size(16.dp),
            )
        }
    }
}

private fun Cell.isShowable(): Boolean = !overflowed && estimate != null

/** Net cash flow is green when positive and red when negative, and also carries its sign. */
@Composable
fun netColor(cell: Cell?): Color {
    val minor = cell?.estimate?.amountMinor ?: return Color.Unspecified
    return when {
        minor > 0 -> MaterialTheme.finance.income
        minor < 0 -> MaterialTheme.finance.expense
        else -> Color.Unspecified
    }
}

// ---- Period and scope ----

/**
 * A pill with the current period ("November 2026"). With [arrows] the pill also steps to the
 * previous/next period. Tapping the label opens a sheet with Today / Week / Month / Year / Custom.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeriodSelector(
    state: PeriodState,
    period: ResolvedPeriod,
    onSelect: (PeriodKind) -> Unit,
    onCustom: (LocalDate, LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    arrows: Boolean = false,
    canShiftForward: Boolean = false,
    onShift: (Int) -> Unit = {},
) {
    var showSheet by rememberSaveable { mutableStateOf(false) }
    var showPicker by rememberSaveable { mutableStateOf(false) }

    Surface(
        modifier = modifier.heightIn(min = 44.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (arrows) {
                IconButton(onClick = { onShift(-1) }, modifier = Modifier.size(44.dp)) {
                    Icon(AppIcons.Previous, contentDescription = stringResource(R.string.analytics_period_previous))
                }
            }
            Row(
                Modifier
                    .weight(1f, fill = arrows)
                    .heightIn(min = 44.dp)
                    .clickable(role = Role.Button, onClickLabel = stringResource(R.string.period_sheet_title)) { showSheet = true }
                    .padding(start = if (arrows) 0.dp else 16.dp, end = if (arrows) 0.dp else 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    periodTitle(state, period),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Icon(AppIcons.Dropdown, contentDescription = null, modifier = Modifier.size(IconSize.Medium))
            }
            if (arrows) {
                IconButton(onClick = { onShift(1) }, enabled = canShiftForward, modifier = Modifier.size(44.dp)) {
                    Icon(AppIcons.Next, contentDescription = stringResource(R.string.analytics_period_next))
                }
            }
        }
    }

    if (showSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { showSheet = false }, sheetState = sheetState) {
            Column(Modifier.navigationBarsPadding().padding(bottom = 8.dp)) {
                Text(
                    stringResource(R.string.period_sheet_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
                PeriodKind.entries.forEach { kind ->
                    val selected = state.kind == kind
                    ListItem(
                        modifier = Modifier.clickable(role = Role.RadioButton) {
                            showSheet = false
                            if (kind == PeriodKind.CUSTOM) showPicker = true else onSelect(kind)
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        leadingContent = { RadioButton(selected = selected, onClick = null) },
                        headlineContent = { Text(stringResource(kind.sheetLabelRes())) },
                        supportingContent = if (selected) {
                            { Text(rangeLabel(period), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }

    if (showPicker) {
        DateRangePickerDialog(
            initialStart = period.fromDate,
            initialEnd = period.toDateInclusive,
            // Cancelling goes back to the period list, like in the Transactions date sheet.
            onDismiss = {
                showPicker = false
                showSheet = true
            },
            onConfirm = { start, end ->
                showPicker = false
                onCustom(start, end)
            },
        )
    }
}

private fun PeriodKind.sheetLabelRes(): Int = when (this) {
    PeriodKind.TODAY -> R.string.period_today
    PeriodKind.WEEK -> R.string.period_this_week
    PeriodKind.MONTH -> R.string.period_this_month
    PeriodKind.YEAR -> R.string.period_this_year
    PeriodKind.CUSTOM -> R.string.period_custom_range
}

/** A compact pill with the selected accounts' icons; tapping it opens a multi-select sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSelector(
    accounts: List<Account>,
    selectedAccountIds: Set<Long>,
    onToggleAccount: (Long) -> Unit,
    onClearSelection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (accounts.isEmpty()) return
    var showSheet by rememberSaveable { mutableStateOf(false) }
    val selected = accounts.filter { it.id in selectedAccountIds }

    val label = when {
        selected.isEmpty() -> stringResource(R.string.filter_all_accounts)
        selected.size == 1 -> selected.single().name
        else -> pluralStringResource(R.plurals.analytics_accounts_selected, selected.size, selected.size)
    }

    Surface(
        onClick = { showSheet = true },
        modifier = modifier.heightIn(min = 44.dp),
        shape = CircleShape,
        color = if (selected.isEmpty()) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (selected.isEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            Modifier.heightIn(min = 44.dp).padding(start = 10.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selected.isEmpty()) {
                Icon(AppIcons.Account, contentDescription = null, modifier = Modifier.size(IconSize.Small))
            } else {
                StackedBadges(selected)
            }
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp).widthIn(max = 104.dp),
            )
            Icon(AppIcons.Dropdown, contentDescription = null, modifier = Modifier.size(IconSize.Medium))
        }
    }

    if (showSheet) {
        ModalBottomSheet(onDismissRequest = { showSheet = false }) {
            Column(Modifier.navigationBarsPadding().padding(bottom = 8.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.filter_accounts),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { showSheet = false }) { Text(stringResource(R.string.selector_done)) }
                }
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ListItem(
                        modifier = Modifier.clickable(role = Role.Checkbox, onClick = onClearSelection),
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        leadingContent = {
                            Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                                Icon(AppIcons.Account, contentDescription = null)
                            }
                        },
                        headlineContent = { Text(stringResource(R.string.filter_all_accounts_selected)) },
                        trailingContent = { Checkbox(checked = selectedAccountIds.isEmpty(), onCheckedChange = null) },
                    )
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    accounts.forEach { account ->
                        ListItem(
                            modifier = Modifier.clickable(role = Role.Checkbox) { onToggleAccount(account.id) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            leadingContent = { IconBadge(account.icon, account.color, ColorPalette.ACCOUNT_DEFAULT, size = 36) },
                            headlineContent = { Text(account.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = {
                                Text(account.currency.code, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            },
                            trailingContent = { Checkbox(checked = account.id in selectedAccountIds, onCheckedChange = null) },
                        )
                    }
                }
            }
        }
    }
}

/** Up to three overlapping account badges. */
@Composable
private fun StackedBadges(accounts: List<Account>) {
    val shown = accounts.take(3)
    Box(Modifier.size(width = (22 + (shown.size - 1) * 12).dp, height = 22.dp)) {
        shown.forEachIndexed { index, account ->
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.surfaceContainerHigh),
                modifier = Modifier.offset(x = (index * 12).dp),
            ) {
                IconBadge(account.icon, account.color, ColorPalette.ACCOUNT_DEFAULT, size = 22)
            }
        }
    }
}

/** R5, R6, R9 and the native-scope note. */
@Composable
fun ConversionNote(info: ConversionInfo, scope: CurrencyScope, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        when (scope) {
            is CurrencyScope.Native ->
                Text(
                    stringResource(R.string.analytics_note_native, scope.currency.code),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            is CurrencyScope.Display -> {
                if (info.converted) {
                    Text(
                        stringResource(R.string.analytics_note_converted, formatDate(info.oldestFetchedAt)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (info.outdated) {
                    Text(
                        stringResource(R.string.valuation_outdated, formatDate(info.oldestFetchedAt)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                NotIncludedLine(info.excluded)
            }
        }
        if (info.overflowed) {
            Text(
                stringResource(R.string.analytics_note_overflow),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

// ---- Rows ----

@Composable
fun AnalyticsRow(
    label: String,
    cell: Cell,
    modifier: Modifier = Modifier,
    icon: String? = null,
    color: Long? = null,
    fallbackColor: Long = ColorPalette.ACCOUNT_DEFAULT,
    share: Share? = null,
    delta: Delta? = null,
    previousOnly: Boolean = false,
    muted: Boolean = false,
    chevron: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val details = buildList {
        if (share != null) add(formatPermille(share))
        val text = deltaText(delta)
        if (text.isNotEmpty()) add(text)
        if (cell.operationCount > 0) {
            add(pluralStringResource(R.plurals.analytics_operations, cell.operationCount, cell.operationCount))
        }
    }.joinToString(" · ")

    ListItem(
        modifier = modifier
            .alpha(if (muted) 0.6f else 1f)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        leadingContent = { IconBadge(icon, color, fallbackColor, size = 36) },
        headlineContent = { Text(label, maxLines = 1) },
        supportingContent = if (details.isNotEmpty()) {
            { Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            null
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AmountText(if (previousOnly) null else cell, style = MaterialTheme.typography.bodyLarge)
                if (chevron) Icon(AppIcons.Next, contentDescription = null)
            }
        },
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = modifier.padding(top = 8.dp))
}
