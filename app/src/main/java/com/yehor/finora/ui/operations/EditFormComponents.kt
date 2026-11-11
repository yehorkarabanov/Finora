package com.yehor.finora.ui.operations

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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yehor.finora.R
import com.yehor.finora.domain.category.CategoryTree
import com.yehor.finora.domain.model.CategoryType
import com.yehor.finora.domain.money.Money
import com.yehor.finora.domain.operation.TransactionDraftValidator
import com.yehor.finora.domain.tag.Tag
import com.yehor.finora.designsystem.icon.AppIcons
import com.yehor.finora.ui.common.ColorPalette
import com.yehor.finora.ui.common.IconBadge
import com.yehor.finora.designsystem.icon.IconSize
import com.yehor.finora.ui.common.TagChip
import com.yehor.finora.ui.common.TagPicker
import com.yehor.finora.ui.common.rememberTimeFormatter
import com.yehor.finora.designsystem.theme.finance
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import androidx.compose.material3.ripple
import com.yehor.finora.designsystem.theme.KeypadNumStyle

// Building blocks shared by the Expense/Income and the Transfer modes of the unified editor.

// Account → tags are one connected group: only its outer corners are rounded.
private val GroupRadius = 20.dp
private val GroupInnerRadius = 4.dp
internal val FormGroupTopShape = RoundedCornerShape(
    topStart = GroupRadius, topEnd = GroupRadius,
    bottomStart = GroupInnerRadius, bottomEnd = GroupInnerRadius,
)
internal val FormGroupMiddleShape = RoundedCornerShape(GroupInnerRadius)
internal val FormGroupBottomShape = RoundedCornerShape(
    topStart = GroupInnerRadius, topEnd = GroupInnerRadius,
    bottomStart = GroupRadius, bottomEnd = GroupRadius,
)
internal const val FORM_MINUS = "−"

// ---------------------------------------------------------------------------
// Mode tabs
// ---------------------------------------------------------------------------

@Composable
internal fun KindTabs(
    mode: EditorMode,
    enabled: Boolean,
    onModeChange: (EditorMode) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            KindTab(
                label = stringResource(R.string.transaction_kind_expense),
                icon = AppIcons.Income,
                color = MaterialTheme.finance.expense,
                selected = mode == EditorMode.EXPENSE,
                enabled = enabled,
                onClick = { onModeChange(EditorMode.EXPENSE) },
            )
            KindTab(
                label = stringResource(R.string.transaction_kind_income),
                icon = AppIcons.Expense,
                color = MaterialTheme.finance.income,
                selected = mode == EditorMode.INCOME,
                enabled = enabled,
                onClick = { onModeChange(EditorMode.INCOME) },
            )
            KindTab(
                label = stringResource(R.string.transaction_kind_transfer),
                icon = AppIcons.Transfer,
                color = MaterialTheme.colorScheme.primary,
                selected = mode == EditorMode.TRANSFER,
                enabled = enabled,
                onClick = { onModeChange(EditorMode.TRANSFER) },
            )
        }
    }
}

@Composable
private fun RowScope.KindTab(
    label: String,
    icon: ImageVector,
    color: Color,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val content = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        selected = selected,
        onClick = onClick,
        // In edit mode the kind is fixed: the selected tab stays readable, the others fade.
        enabled = enabled && !selected,
        shape = RoundedCornerShape(50),
        color = if (selected) color.copy(alpha = 0.16f) else Color.Transparent,
        contentColor = content,
        modifier = Modifier
            .weight(1f)
            .height(44.dp)
            .alpha(if (!enabled && !selected) 0.38f else 1f),
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(IconSize.Small))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)
        }
    }
}

// ---------------------------------------------------------------------------
// Amount display + keypad
// ---------------------------------------------------------------------------

/** The big amount with a blinking caret. An empty [sign] shows the amount alone (transfers are not signed). */
@Composable
internal fun AmountDisplay(sign: String, signColor: Color, amountDisplay: String, isError: Boolean, isEmpty: Boolean) {
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
        if (sign.isNotEmpty()) {
            withStyle(SpanStyle(color = signColor)) { append(sign) }
            append(" ")
        }
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

/** The +10/+25/+50 quick-add pills and the clear pill. */
@Composable
internal fun QuickAmountRow(amountText: String, currencySymbol: String, onAmountChange: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(10, 25, 50).forEach { inc ->
            SmallPill(text = "+$currencySymbol$inc", onClick = { onAmountChange(AmountKeypad.add(amountText, inc)) })
        }
        if (amountText.isNotEmpty()) {
            val clear = stringResource(R.string.transaction_form_clear_amount)
            SmallPill(onClick = { onAmountChange("") }, modifier = Modifier.semantics { contentDescription = clear }) {
                Icon(AppIcons.Close, contentDescription = null, modifier = Modifier.size(IconSize.Small))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun NumberPad(
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
                                // Long-press on backspace clears the whole amount.
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
// Grouped field cards shared by both modes
// ---------------------------------------------------------------------------

@Composable
internal fun CategoryCard(
    shape: Shape,
    tree: CategoryTree,
    categoryType: CategoryType,
    selectedCategoryId: Long?,
    /** Editing an operation that had no category: show "Uncategorized" instead of the empty prompt. */
    showUncategorized: Boolean,
    showError: Boolean,
    recentCategoryIds: List<Long>,
    onClick: () -> Unit,
    onSelectRecent: (Long) -> Unit,
    onCreateCategory: (() -> Unit)?,
) {
    val hasCategoriesOfKind = remember(tree, categoryType) { tree.rootsOf(categoryType).isNotEmpty() }
    val category = selectedCategoryId?.let { tree.category(it) }
    // Resolve composable values unconditionally: a composable call nested in `?: if (...)` is skipped once a
    // category is picked, which shifted the slot table and crashed the next SectionCard on recomposition.
    val uncategorizedLabel = stringResource(R.string.picker_uncategorized)
    val categoryName = category?.name ?: if (showUncategorized) uncategorizedLabel else null
    val parentPath = remember(category, tree) {
        generateSequence(category?.parentId?.let { tree.category(it) }) { c -> c.parentId?.let { tree.category(it) } }
            .toList()
            .asReversed()
            .joinToString(" › ") { it.name }
            .ifEmpty { null }
    }
    val recents = recentCategoryIds.mapNotNull { tree.category(it) }.take(6)
    SectionCard(shape = shape, onClick = onClick, isError = showError) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconBadge(category?.icon, category?.color, ColorPalette.defaultFor(categoryType), size = 44)
            Column(Modifier.weight(1f)) {
                Text(
                    categoryName ?: stringResource(R.string.transaction_form_category),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (categoryName == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                when {
                    showError -> Text(
                        stringResource(R.string.transaction_form_error_category_missing),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    parentPath != null -> Text(
                        stringResource(R.string.transaction_form_in_parent, parentPath),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(AppIcons.Next, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (recents.isNotEmpty() || (!hasCategoriesOfKind && onCreateCategory != null)) {
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (recents.isNotEmpty()) {
                    Text(
                        stringResource(R.string.transaction_form_recent),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                recents.forEach { recent ->
                    FilterChip(
                        selected = recent.id == selectedCategoryId,
                        onClick = { onSelectRecent(recent.id) },
                        label = { Text(recent.name) },
                    )
                }
                if (!hasCategoriesOfKind && onCreateCategory != null) {
                    FilterChip(
                        selected = false,
                        onClick = onCreateCategory,
                        label = { Text(stringResource(R.string.transaction_form_create_category)) },
                        leadingIcon = { Icon(AppIcons.Add, contentDescription = null, modifier = Modifier.size(IconSize.Small)) },
                    )
                }
            }
        }
    }
}

@Composable
internal fun DateTimeCard(
    shape: Shape,
    date: LocalDate,
    time: LocalTime,
    isFuture: Boolean,
    onOpenPicker: () -> Unit,
    onDateChange: (LocalDate) -> Unit,
    onTimeChange: (LocalTime) -> Unit,
) {
    val today = LocalDate.now()
    SectionCard(shape = shape, onClick = onOpenPicker, isError = isFuture) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LeadingIcon(AppIcons.Calendar)
            Column(Modifier.weight(1f)) {
                Text(dateLabel(date, today), style = MaterialTheme.typography.titleMedium)
                Text(
                    if (isFuture) {
                        stringResource(R.string.transaction_form_error_future_date)
                    } else {
                        stringResource(R.string.transaction_form_time_hint, time.format(rememberTimeFormatter()))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isFuture) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(AppIcons.Next, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                today to R.string.transactions_day_today,
                today.minusDays(1) to R.string.transactions_day_yesterday,
            ).forEach { (day, label) ->
                FilterChip(
                    selected = date == day,
                    onClick = {
                        onDateChange(day)
                        // Picking "today" after a later time was chosen for yesterday must not land in the future.
                        if (day == today && time.isAfter(LocalTime.now())) {
                            onTimeChange(LocalTime.now().withSecond(0).withNano(0))
                        }
                    },
                    label = { Text(stringResource(label)) },
                )
            }
        }
    }
}

@Composable
internal fun DescriptionCard(
    shape: Shape,
    label: String,
    hint: String,
    description: String,
    isTooLong: Boolean,
    onChange: (String) -> Unit,
) {
    SectionCard(shape = shape, isError = isTooLong) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LeadingIcon(AppIcons.Merchant)
            Column(Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                BasicTextField(
                    value = description,
                    onValueChange = onChange,
                    textStyle = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done,
                    ),
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        Box {
                            if (description.isEmpty()) {
                                Text(
                                    hint,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                )
                            }
                            inner()
                        }
                    },
                )
                // The counter only appears when the limit is getting close.
                if (isTooLong) {
                    Text(
                        stringResource(R.string.transaction_form_error_description_long),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else if (description.length > TransactionDraftValidator.DESCRIPTION_MAX - 100) {
                    Text(
                        "${description.length}/${TransactionDraftValidator.DESCRIPTION_MAX}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun TagsCard(
    allTags: List<Tag>,
    selectedTagIds: Set<Long>,
    pendingTagNames: List<String>,
    onToggleTag: (Long) -> Unit,
    onAddPendingTag: (String) -> Unit,
    onRemovePendingTag: (String) -> Unit,
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    SectionCard(shape = FormGroupBottomShape) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                AppIcons.Tag,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.transaction_form_labels_tags),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            if (editing) {
                TextButton(onClick = { editing = false }) { Text(stringResource(R.string.transaction_form_tags_done)) }
            } else {
                Text(
                    stringResource(R.string.transaction_form_tags_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        if (editing) {
            TagPicker(
                allTags = allTags,
                selectedTagIds = selectedTagIds,
                pendingTagNames = pendingTagNames,
                onToggleTag = onToggleTag,
                onAddPendingTag = onAddPendingTag,
                onRemovePendingTag = onRemovePendingTag,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                allTags.filter { it.id in selectedTagIds }.forEach { tag ->
                    TagChip(tag = tag, onRemove = { onToggleTag(tag.id) })
                }
                pendingTagNames.forEach { name ->
                    InputChip(
                        selected = true,
                        onClick = {},
                        label = { Text(stringResource(R.string.tag_picker_new_suffix, name)) },
                        trailingIcon = {
                            IconButton(onClick = { onRemovePendingTag(name) }, modifier = Modifier.size(16.dp)) {
                                Icon(AppIcons.Close, contentDescription = stringResource(R.string.action_remove))
                            }
                        },
                    )
                }
                FilterChip(
                    selected = false,
                    onClick = { editing = true },
                    label = { Text(stringResource(R.string.transaction_form_add_tag)) },
                    leadingIcon = { Icon(AppIcons.Add, contentDescription = null, modifier = Modifier.size(IconSize.Small)) },
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Reusable pieces
// ---------------------------------------------------------------------------

@Composable
internal fun SectionCard(
    shape: Shape,
    onClick: (() -> Unit)? = null,
    isError: Boolean = false,
    content: @Composable () -> Unit,
) {
    // One Surface with a stable structure: the click and error outline are plain modifiers/params,
    // so toggling them never changes the composition shape.
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
internal fun LeadingIcon(icon: ImageVector) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(IconSize.Default))
    }
}

@Composable
internal fun Pill(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
internal fun SmallPill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String? = null,
    icon: (@Composable () -> Unit)? = null,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.height(32.dp),
    ) {
        Box(Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
            if (text != null) Text(text, style = MaterialTheme.typography.labelLarge)
            icon?.invoke()
        }
    }
}

@Composable
internal fun dateLabel(date: LocalDate, today: LocalDate): String {
    val formatted = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    return when (date) {
        today -> "${stringResource(R.string.transactions_day_today)}, $formatted"
        today.minusDays(1) -> "${stringResource(R.string.transactions_day_yesterday)}, $formatted"
        else -> formatted
    }
}

/** The bottom CTA bar: a full-width 56dp round button whose label says what is missing while it is disabled. */
@Composable
internal fun FormCtaBar(
    label: String,
    enabled: Boolean,
    showCheck: Boolean,
    onClick: () -> Unit,
    bottomPadding: androidx.compose.ui.unit.Dp,
    secondary: (@Composable () -> Unit)? = null,
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = bottomPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Button(
                onClick = onClick,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(28.dp),
            ) {
                if (showCheck) {
                    Icon(AppIcons.Check, contentDescription = null, modifier = Modifier.size(IconSize.Small))
                    Spacer(Modifier.width(8.dp))
                }
                Text(label, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            secondary?.invoke()
        }
    }
}

/** Money with the symbol in front and locale digit grouping, matching the amount hero. */
internal fun formatMoney(money: Money, symbols: DecimalFormatSymbols): String {
    val plain = money.toPlainString()
    val negative = plain.startsWith("-")
    val body = AmountKeypad.format(plain.removePrefix("-"), symbols.groupingSeparator, symbols.decimalSeparator)
    return (if (negative) FORM_MINUS else "") + money.currency.symbol + body
}
