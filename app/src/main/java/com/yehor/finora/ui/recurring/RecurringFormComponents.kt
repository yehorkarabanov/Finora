package com.yehor.finora.ui.recurring

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yehor.finora.R
import com.yehor.finora.domain.operation.TransactionDateTime
import com.yehor.finora.domain.operation.TransactionKind
import com.yehor.finora.ui.common.AppIcons
import com.yehor.finora.ui.common.IconSize
import com.yehor.finora.ui.operations.AmountKeypad
import com.yehor.finora.ui.theme.finance
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import androidx.compose.material3.ripple
import com.yehor.finora.ui.theme.KeypadNumStyle

// Building blocks of the schedule form, in the style of TransactionEditScreen (docs/ui-design-system.md 5.3).

private val FormGroupRadius = 20.dp
private val FormGroupInnerRadius = 4.dp
internal val FormGroupTopShape = RoundedCornerShape(
    topStart = FormGroupRadius, topEnd = FormGroupRadius,
    bottomStart = FormGroupInnerRadius, bottomEnd = FormGroupInnerRadius,
)
internal val FormGroupMiddleShape = RoundedCornerShape(FormGroupInnerRadius)
internal val FormGroupBottomShape = RoundedCornerShape(
    topStart = FormGroupInnerRadius, topEnd = FormGroupInnerRadius,
    bottomStart = FormGroupRadius, bottomEnd = FormGroupRadius,
)
internal const val FORM_MINUS = "−"

// ---------------------------------------------------------------------------
// Kind tabs
// ---------------------------------------------------------------------------

/** Expense / Income only: schedules do not support transfers. */
@Composable
internal fun FormKindTabs(kind: TransactionKind, locked: Boolean, onKindChange: (TransactionKind) -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            FormKindTab(
                label = stringResource(R.string.transaction_kind_expense),
                icon = AppIcons.Income,
                color = MaterialTheme.finance.expense,
                selected = kind == TransactionKind.EXPENSE,
                locked = locked,
                onClick = { onKindChange(TransactionKind.EXPENSE) },
            )
            FormKindTab(
                label = stringResource(R.string.transaction_kind_income),
                icon = AppIcons.Expense,
                color = MaterialTheme.finance.income,
                selected = kind == TransactionKind.INCOME,
                locked = locked,
                onClick = { onKindChange(TransactionKind.INCOME) },
            )
        }
    }
}

@Composable
private fun RowScope.FormKindTab(
    label: String,
    icon: ImageVector,
    color: Color,
    selected: Boolean,
    locked: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        selected = selected,
        onClick = onClick,
        enabled = !locked && !selected,
        shape = RoundedCornerShape(50),
        color = if (selected) color.copy(alpha = 0.16f) else Color.Transparent,
        contentColor = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .weight(1f)
            .height(44.dp)
            .alpha(if (locked && !selected) 0.38f else 1f),
    ) {
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(IconSize.Small))
            Spacer(Modifier.width(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Amount hero + keypad
// ---------------------------------------------------------------------------

@Composable
internal fun FormAmountCard(
    amountText: String,
    amountDisplay: String,
    sign: String,
    signColor: Color,
    currencyScale: Int,
    currencySymbol: String,
    accountLabel: String?,
    archivedNote: String?,
    errorText: String?,
    decimalSeparator: Char,
    onAccountClick: () -> Unit,
    onAmountChange: (String) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val changeAccount = stringResource(R.string.transaction_form_change_account)
            Surface(
                onClick = onAccountClick,
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .height(44.dp)
                    .semantics { contentDescription = "${accountLabel.orEmpty()}. $changeAccount" },
            ) {
                Row(
                    Modifier.padding(start = 14.dp, end = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        accountLabel ?: stringResource(R.string.transaction_form_account),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Icon(
                        AppIcons.Dropdown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(IconSize.Small),
                    )
                }
            }
            if (archivedNote != null) {
                Text(
                    archivedNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            FormAmountDisplay(
                sign = sign,
                signColor = signColor,
                amountDisplay = amountDisplay,
                isError = errorText != null,
                isEmpty = amountText.isEmpty(),
            )
            if (errorText != null) {
                Text(errorText, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf(10, 25, 50).forEach { inc ->
                    FormSmallPill(text = "+$currencySymbol$inc", onClick = { onAmountChange(AmountKeypad.add(amountText, inc)) })
                }
                if (amountText.isNotEmpty()) {
                    val clear = stringResource(R.string.transaction_form_clear_amount)
                    FormSmallPill(onClick = { onAmountChange("") }, modifier = Modifier.semantics { contentDescription = clear }) {
                        Icon(AppIcons.Close, contentDescription = null, modifier = Modifier.size(IconSize.Small))
                    }
                }
            }

            FormNumberPad(
                decimalLabel = decimalSeparator.toString(),
                decimalEnabled = currencyScale > 0,
                onKey = { onAmountChange(AmountKeypad.applyKey(amountText, it, currencyScale)) },
                onClear = { onAmountChange("") },
            )
        }
    }
}

@Composable
private fun FormAmountDisplay(sign: String, signColor: Color, amountDisplay: String, isError: Boolean, isEmpty: Boolean) {
    val amountColor = when {
        isError -> MaterialTheme.colorScheme.error
        isEmpty -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        else -> MaterialTheme.colorScheme.onSurface
    }
    val caretAlpha by rememberInfiniteTransition(label = "caret").animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(530), RepeatMode.Reverse),
        label = "caretAlpha",
    )
    val text = buildAnnotatedString {
        withStyle(SpanStyle(color = signColor)) { append(sign) }
        append(" ")
        withStyle(SpanStyle(color = amountColor)) { append(amountDisplay) }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        BasicText(
            text = text,
            maxLines = 1,
            style = MaterialTheme.typography.displayMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontFeatureSettings = "tnum",
                letterSpacing = (-1).sp,
            ),
            autoSize = TextAutoSize.StepBased(minFontSize = 24.sp, maxFontSize = 56.sp),
            modifier = Modifier.weight(1f, fill = false),
        )
        Box(
            Modifier
                .padding(start = 4.dp)
                .width(2.dp)
                .height(40.dp)
                .alpha(caretAlpha)
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FormNumberPad(
    decimalLabel: String,
    decimalEnabled: Boolean,
    onKey: (String) -> Unit,
    onClear: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val backspaceLabel = stringResource(R.string.transaction_form_backspace)
    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", AmountKeypad.DECIMAL, "0", AmountKeypad.BACKSPACE)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        keys.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { key ->
                    val isDecimal = key == AmountKeypad.DECIMAL
                    val isBackspace = key == AmountKeypad.BACKSPACE
                    val keyEnabled = !isDecimal || decimalEnabled
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .combinedClickable(
                                interactionSource = null,
                                // Borderless keys: a circular wash on press instead of a filled tile.
                                indication = ripple(bounded = false, radius = 32.dp),
                                enabled = keyEnabled,
                                role = Role.Button,
                                onClickLabel = if (isBackspace) backspaceLabel else null,
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                                    onKey(key)
                                },
                                onLongClick = if (isBackspace) {
                                    {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onClear()
                                    }
                                } else {
                                    null
                                },
                            )
                            .semantics { if (isBackspace) contentDescription = backspaceLabel },
                    ) {
                        when {
                            isBackspace -> Icon(AppIcons.Backspace, contentDescription = null)
                            isDecimal && !decimalEnabled -> Unit
                            else -> Text(
                                if (isDecimal) decimalLabel else key,
                                style = KeypadNumStyle,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Cards, icons, pills
// ---------------------------------------------------------------------------

@Composable
internal fun FormSectionCard(
    shape: Shape,
    onClick: (() -> Unit)? = null,
    isError: Boolean = false,
    content: @Composable () -> Unit,
) {
    val errorColor = MaterialTheme.colorScheme.error
    val clickModifier = if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = if (isError) BorderStroke(1.dp, errorColor) else null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(clickModifier.padding(14.dp)) { content() }
    }
}

@Composable
internal fun FormLeadingIcon(icon: ImageVector) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(IconSize.Default),
        )
    }
}

/** 32dp pill. [selected] switches to the active-filter colors. */
@Composable
internal fun FormSmallPill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String? = null,
    selected: Boolean = false,
    icon: (@Composable () -> Unit)? = null,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
        modifier = modifier.height(32.dp),
    ) {
        Box(Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
            if (text != null) {
                Text(
                    text,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
            icon?.invoke()
        }
    }
}

/** Round 44dp stepper button. */
@Composable
internal fun FormStepButton(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.size(44.dp).alpha(if (enabled) 1f else 0.38f),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description, modifier = Modifier.size(IconSize.Default))
        }
    }
}

// ---------------------------------------------------------------------------
// Bottom CTA
// ---------------------------------------------------------------------------

@Composable
internal fun FormCtaBar(cta: RecurringCta, amountSuffix: String?, onSave: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        val ready = cta is RecurringCta.Ready
        val label = when (cta) {
            is RecurringCta.Missing -> stringResource(
                when (cta.reason) {
                    RecurringCtaMissing.LOADING -> R.string.recurring_form_cta_loading
                    RecurringCtaMissing.ENTER_AMOUNT -> R.string.recurring_form_cta_enter_amount
                    RecurringCtaMissing.CHOOSE_ACCOUNT -> R.string.recurring_form_cta_choose_account
                    RecurringCtaMissing.ENTER_NAME -> R.string.recurring_form_cta_enter_name
                    RecurringCtaMissing.CHOOSE_CATEGORY -> R.string.recurring_form_cta_choose_category
                    RecurringCtaMissing.FIX_ERRORS -> R.string.recurring_form_cta_fix_errors
                },
            )
            is RecurringCta.Ready -> stringResource(
                when {
                    cta.isEdit -> R.string.recurring_form_cta_save
                    cta.kind == TransactionKind.INCOME -> R.string.recurring_form_cta_add_income
                    else -> R.string.recurring_form_cta_add_expense
                },
            ) + (amountSuffix?.let { " · $it" } ?: "")
        }
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Button(
                onClick = onSave,
                enabled = ready,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(28.dp),
            ) {
                if (ready) {
                    Icon(AppIcons.Check, contentDescription = null, modifier = Modifier.size(IconSize.Small))
                    Spacer(Modifier.width(8.dp))
                }
                Text(label, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Date picker (date only)
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FormDatePickerDialog(
    initial: LocalDate,
    maxDate: LocalDate,
    onDismiss: () -> Unit,
    onPicked: (LocalDate) -> Unit,
) {
    val maxUtcMidnight = TransactionDateTime.pickerUtcMidnight(maxDate)
    val state = rememberDatePickerState(
        initialSelectedDateMillis = TransactionDateTime.pickerUtcMidnight(initial),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= maxUtcMidnight
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let {
                        onPicked(Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate())
                    }
                    onDismiss()
                },
            ) { Text(stringResource(R.string.action_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    ) {
        DatePicker(state = state)
    }
}
