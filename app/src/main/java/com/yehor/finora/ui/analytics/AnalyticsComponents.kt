package com.yehor.finora.ui.analytics

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
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
import com.yehor.finora.ui.common.IconBadge
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val IncomeGreen = Color(0xFF2E7D32)

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

// ---- Amount ----

@Composable
fun AmountText(
    cell: Cell?,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleMedium,
    color: Color = Color.Unspecified,
    signed: Boolean = false,
) {
    val estimate = cell?.estimate
    val text = if (signed && estimate != null && cell.isShowable()) signedText(estimate) else cellText(cell)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = style, color = color, fontWeight = FontWeight.SemiBold)
        if (cell != null && cell.partial) {
            Icon(
                Icons.Filled.Warning,
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
        minor > 0 -> IncomeGreen
        minor < 0 -> MaterialTheme.colorScheme.error
        else -> Color.Unspecified
    }
}

// ---- Period and scope ----

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    var showPicker by rememberSaveable { mutableStateOf(false) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PeriodKind.entries.forEach { kind ->
                FilterChip(
                    selected = state.kind == kind,
                    onClick = { if (kind == PeriodKind.CUSTOM) showPicker = true else onSelect(kind) },
                    label = { Text(stringResource(kind.labelRes())) },
                )
            }
        }
        if (arrows) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onShift(-1) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.analytics_period_previous))
                }
                Text(
                    rangeLabel(period),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                IconButton(onClick = { onShift(1) }, enabled = canShiftForward) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.analytics_period_next))
                }
            }
        } else {
            Text(rangeLabel(period), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (showPicker) {
        val pickerState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = utcMillis(period.fromDate),
            initialSelectedEndDateMillis = utcMillis(period.toDateInclusive),
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    enabled = pickerState.selectedStartDateMillis != null,
                    onClick = {
                        val start = pickerState.selectedStartDateMillis?.let(::utcDate)
                        val end = pickerState.selectedEndDateMillis?.let(::utcDate) ?: start
                        if (start != null && end != null) onCustom(start, end)
                        showPicker = false
                    },
                ) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        ) {
            DateRangePicker(state = pickerState, modifier = Modifier.height(480.dp))
        }
    }
}

private fun PeriodKind.labelRes(): Int = when (this) {
    PeriodKind.TODAY -> R.string.period_today
    PeriodKind.WEEK -> R.string.period_week
    PeriodKind.MONTH -> R.string.period_month
    PeriodKind.YEAR -> R.string.period_year
    PeriodKind.CUSTOM -> R.string.period_custom
}

private fun utcMillis(date: LocalDate): Long = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun utcDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

@Composable
fun AccountSelector(
    accounts: List<Account>,
    selectedAccountIds: Set<Long>,
    onToggleAccount: (Long) -> Unit,
    onClearSelection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (accounts.isEmpty()) return
    var expanded by rememberSaveable { mutableStateOf(false) }

    val labelText = when {
        selectedAccountIds.isEmpty() -> stringResource(R.string.filter_all_accounts_selected)
        selectedAccountIds.size == 1 -> accounts.firstOrNull { it.id == selectedAccountIds.single() }?.name
            ?: stringResource(R.string.filter_all_accounts_selected)
        else -> pluralStringResource(R.plurals.analytics_accounts_selected, selectedAccountIds.size, selectedAccountIds.size)
    }

    Box(modifier = modifier) {
        OutlinedCard(
            onClick = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    if (selectedAccountIds.size == 1) {
                        val singleAccount = accounts.firstOrNull { it.id == selectedAccountIds.single() }
                        if (singleAccount != null) {
                            IconBadge(
                                icon = singleAccount.icon,
                                color = singleAccount.color,
                                fallbackColor = ColorPalette.ACCOUNT_DEFAULT,
                                size = 28,
                            )
                        }
                    }
                    Column {
                        Text(
                            text = stringResource(R.string.filter_accounts),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = labelText,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(0.9f),
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.filter_all_accounts_selected)) },
                onClick = { onClearSelection() },
                leadingIcon = {
                    Checkbox(
                        checked = selectedAccountIds.isEmpty(),
                        onCheckedChange = null,
                    )
                },
            )
            HorizontalDivider()
            accounts.forEach { account ->
                val isSelected = account.id in selectedAccountIds
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(account.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                account.currency.code,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    onClick = { onToggleAccount(account.id) },
                    leadingIcon = {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = null,
                        )
                    },
                    trailingIcon = {
                        IconBadge(
                            icon = account.icon,
                            color = account.color,
                            fallbackColor = ColorPalette.ACCOUNT_DEFAULT,
                            size = 24,
                        )
                    },
                )
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
                if (chevron) Icon(Icons.Filled.ChevronRight, contentDescription = null)
            }
        },
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = modifier.padding(top = 8.dp))
}
