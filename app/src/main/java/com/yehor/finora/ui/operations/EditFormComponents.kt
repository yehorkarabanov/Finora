package com.yehor.finora.ui.operations

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yehor.finora.R
import com.yehor.finora.designsystem.component.button.RoundIconButton
import com.yehor.finora.designsystem.component.display.AmountText
import com.yehor.finora.designsystem.component.input.FilterPill
import com.yehor.finora.designsystem.component.input.SegmentedPill
import com.yehor.finora.designsystem.icon.AppIcons
import com.yehor.finora.designsystem.icon.IconSize
import com.yehor.finora.designsystem.theme.AmountStyle
import com.yehor.finora.designsystem.theme.PillShape
import com.yehor.finora.designsystem.theme.finance
import com.yehor.finora.designsystem.theme.hairline
import com.yehor.finora.domain.account.Account
import com.yehor.finora.domain.category.CategoryTree
import com.yehor.finora.domain.model.CategoryType
import com.yehor.finora.domain.money.Money
import com.yehor.finora.domain.operation.TransactionDraftValidator
import com.yehor.finora.domain.tag.Tag
import com.yehor.finora.ui.common.ColorPalette
import com.yehor.finora.ui.common.IconBadge
import com.yehor.finora.ui.common.TagChip
import com.yehor.finora.ui.common.PendingTagChip
import com.yehor.finora.ui.common.TagPicker
import com.yehor.finora.ui.common.rememberTimeFormatter
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

// Building blocks shared by the Expense/Income and the Transfer modes of the unified editor.

internal const val FORM_MINUS = "−"

// ---------------------------------------------------------------------------
// Stage
// ---------------------------------------------------------------------------

/** The editor's two stages: typing the amount on the keypad, or the details shelf raised over it. */
enum class EditorStage { AMOUNT, DETAILS }

/** New operations start on the keypad; existing ones open with the details shelf up. */
fun initialStage(isEdit: Boolean): EditorStage = if (isEdit) EditorStage.DETAILS else EditorStage.AMOUNT

// ---------------------------------------------------------------------------
// Top bar
// ---------------------------------------------------------------------------

/**
 * Round ✕, the Expense | Income | Transfer pill and, when editing, the ⋯ menu (Duplicate, Delete).
 * In edit mode the kind is fixed, so taps on the other segments are ignored.
 */
@Composable
internal fun EditorTopBar(
    mode: EditorMode,
    isEdit: Boolean,
    onClose: () -> Unit,
    onModeChange: (EditorMode) -> Unit,
    onDuplicate: (() -> Unit)?,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        RoundIconButton(AppIcons.Close, stringResource(R.string.navigate_back), onClose)
        SegmentedPill(
            options = listOf(
                stringResource(R.string.transaction_kind_expense),
                stringResource(R.string.transaction_kind_income),
                stringResource(R.string.transaction_kind_transfer),
            ),
            selected = mode.ordinal,
            onSelect = { if (!isEdit) onModeChange(EditorMode.entries[it]) },
            enabled = !isEdit,
            modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
        )
        if (isEdit) {
            Box {
                RoundIconButton(AppIcons.More, stringResource(R.string.action_more_options), { menuOpen = true })
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    if (onDuplicate != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.transaction_form_duplicate)) },
                            onClick = {
                                menuOpen = false
                                onDuplicate()
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_delete)) },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        },
                    )
                }
            }
        } else {
            Spacer(Modifier.size(48.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// Amount block
// ---------------------------------------------------------------------------

/** Big-amount size that keeps long numbers on one line, capped at [max]. */
private fun amountFontSize(length: Int, max: TextUnit): TextUnit {
    val step = when {
        length <= 8 -> 56.sp
        length <= 11 -> 46.sp
        length <= 14 -> 38.sp
        else -> 30.sp
    }
    return if (step.value > max.value) max else step
}

/**
 * The huge amount: a muted sign, the symbol and the typed number with its decimals dimmed.
 * [label] is an optional caption above it (the transfer's "They receive").
 */
@Composable
internal fun EditorAmount(
    sign: String,
    amountText: String,
    currencySymbol: String,
    symbols: DecimalFormatSymbols,
    isError: Boolean,
    label: String? = null,
    maxFontSize: TextUnit = 56.sp,
) {
    val formatted = currencySymbol + AmountKeypad.format(amountText, symbols.groupingSeparator, symbols.decimalSeparator)
    val size = amountFontSize(sign.length + formatted.length, maxFontSize)
    val style = MaterialTheme.typography.displayLarge.copy(fontSize = size, lineHeight = size * 1.1f)
    val color = when {
        isError -> MaterialTheme.colorScheme.error
        amountText.isEmpty() -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (label != null) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        ) {
            if (sign.isNotEmpty()) Text(sign, style = style, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AmountText(formatted, style = style, color = color, animate = false)
        }
    }
}

/** The account pill under the amount: icon, name, balance and ⇅; tapping it opens the account picker. */
@Composable
internal fun AccountPill(account: Account?, balance: Money?, symbols: DecimalFormatSymbols, onClick: () -> Unit) {
    val name = account?.name ?: stringResource(R.string.transaction_form_account)
    val changeAccount = stringResource(R.string.transaction_form_change_account)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(PillShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow, PillShape)
            .border(hairline(MaterialTheme.finance.isDark), PillShape)
            .clickable(role = Role.Button, onClickLabel = changeAccount, onClick = onClick)
            .padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
    ) {
        IconBadge(account?.icon, account?.color, ColorPalette.ACCOUNT_DEFAULT, size = 32)
        Text(
            name,
            style = MaterialTheme.typography.labelLarge,
            color = if (account == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 180.dp),
        )
        if (balance != null) {
            Text(formatMoney(balance, symbols), style = AmountStyle.copy(fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Icon(AppIcons.SwapVertical, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
    }
}

/** The +10/+25/+50 quick-add pills and, while an amount is typed, the clear pill. */
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

/** A compact pill button; the 48dp touch target surrounds a 32dp visual pill. */
@Composable
internal fun SmallPill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String? = null,
    icon: (@Composable () -> Unit)? = null,
) {
    Box(
        modifier = modifier.heightIn(min = 48.dp).clip(PillShape).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .heightIn(min = 32.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerLow, PillShape)
                .border(hairline(MaterialTheme.finance.isDark), PillShape)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (text != null) {
                Text(text, style = AmountStyle.copy(fontSize = 14.sp), color = MaterialTheme.colorScheme.onSurface)
            }
            icon?.invoke()
        }
    }
}

// ---------------------------------------------------------------------------
// Shelf rows (one GroupedCard)
// ---------------------------------------------------------------------------

/**
 * One row of the details card: round icon, muted [label], [trailing] value at the end; [supporting] is a
 * second line (red when [supportingIsError]); [below] holds extra controls under the row.
 */
@Composable
internal fun ShelfRow(
    icon: @Composable () -> Unit,
    label: String,
    onClick: (() -> Unit)?,
    supporting: String? = null,
    supportingIsError: Boolean = false,
    trailing: @Composable RowScope.() -> Unit = {},
    below: (@Composable () -> Unit)? = null,
) {
    val clickModifier = if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier
    Column(
        Modifier
            .fillMaxWidth()
            .then(clickModifier)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 36.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            icon()
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Row(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
                content = trailing,
            )
        }
        if (supporting != null) {
            Text(
                supporting,
                style = MaterialTheme.typography.bodySmall,
                color = if (supportingIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 48.dp, top = 2.dp),
            )
        }
        below?.invoke()
    }
}

/** The bold value at the end of a [ShelfRow]. */
@Composable
internal fun RowScope.ShelfValue(text: String, muted: Boolean = false, color: Color? = null) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (muted) FontWeight.Medium else FontWeight.SemiBold,
        color = color ?: if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.End,
        modifier = Modifier.weight(1f, fill = false),
    )
}

@Composable
internal fun ShelfDivider() {
    val color = (hairline(MaterialTheme.finance.isDark).brush as SolidColor).value
    HorizontalDivider(Modifier.padding(start = 62.dp, end = 14.dp), thickness = 1.dp, color = color)
}

/** Neutral 34dp round icon for the field rows. */
@Composable
internal fun ShelfIcon(icon: ImageVector) {
    Box(
        Modifier.size(34.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(IconSize.Small))
    }
}

/** An account row: name and balance at the end; [supporting] carries the "after saving" line or an error. */
@Composable
internal fun AccountShelfRow(
    label: String,
    account: Account?,
    balance: Money?,
    symbols: DecimalFormatSymbols,
    supporting: String?,
    supportingIsError: Boolean,
    onClick: () -> Unit,
    extraTrailing: (@Composable () -> Unit)? = null,
    below: (@Composable () -> Unit)? = null,
) {
    ShelfRow(
        icon = { IconBadge(account?.icon, account?.color, ColorPalette.ACCOUNT_DEFAULT, size = 34) },
        label = label,
        onClick = onClick,
        supporting = supporting,
        supportingIsError = supportingIsError,
        trailing = {
            if (account == null) {
                Icon(AppIcons.Next, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                ShelfValue(account.name)
                if (balance != null) {
                    Text(
                        "· " + formatMoney(balance, symbols),
                        style = AmountStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            extraTrailing?.invoke()
        },
        below = below,
    )
}

/** "Balance → after saving" or the archived note for an account row, with the red flag for a negative result. */
@Composable
internal fun accountSupporting(archived: Boolean, balance: Money?, balanceAfter: Money?, symbols: DecimalFormatSymbols): Pair<String?, Boolean> = when {
    archived -> stringResource(R.string.account_detail_archived_banner) to false
    balance != null && balanceAfter != null -> stringResource(
        R.string.transaction_form_balance_after,
        formatMoney(balance, symbols),
        formatMoney(balanceAfter, symbols),
    ) to (balanceAfter.amountMinor < 0)
    else -> null to false
}

@Composable
internal fun CategoryShelfRow(
    tree: CategoryTree,
    categoryType: CategoryType,
    selectedCategoryId: Long?,
    /** Editing an operation that had no category: show "Uncategorized" instead of the empty prompt. */
    showUncategorized: Boolean,
    showError: Boolean,
    onClick: () -> Unit,
) {
    val category = selectedCategoryId?.let { tree.category(it) }
    // Resolve composable values unconditionally so the composition shape does not depend on the selection.
    val uncategorizedLabel = stringResource(R.string.picker_uncategorized)
    val categoryName = category?.name ?: if (showUncategorized) uncategorizedLabel else null
    val parentPath = remember(category, tree) {
        generateSequence(category?.parentId?.let { tree.category(it) }) { c -> c.parentId?.let { tree.category(it) } }
            .toList()
            .asReversed()
            .joinToString(" › ") { it.name }
            .ifEmpty { null }
    }
    val errorText = stringResource(R.string.transaction_form_error_category_missing)
    val parentText = parentPath?.let { stringResource(R.string.transaction_form_in_parent, it) }
    ShelfRow(
        icon = { IconBadge(category?.icon, category?.color, ColorPalette.defaultFor(categoryType), size = 34) },
        label = stringResource(R.string.transaction_form_category),
        onClick = onClick,
        supporting = if (showError) errorText else parentText,
        supportingIsError = showError,
        trailing = {
            if (categoryName != null) {
                ShelfValue(categoryName)
            } else {
                Icon(AppIcons.Next, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    )
}

/**
 * Recent-category shortcuts as round icons under the card, plus "Create category" when the kind has none yet.
 * Renders nothing when there is nothing to offer.
 */
@Composable
internal fun RecentCategoriesRow(
    tree: CategoryTree,
    categoryType: CategoryType,
    selectedCategoryId: Long?,
    recentCategoryIds: List<Long>,
    onSelectRecent: (Long) -> Unit,
    onCreateCategory: (() -> Unit)?,
) {
    val hasCategoriesOfKind = remember(tree, categoryType) { tree.rootsOf(categoryType).isNotEmpty() }
    val recents = recentCategoryIds.mapNotNull { tree.category(it) }.take(6)
    val offerCreate = !hasCategoriesOfKind && onCreateCategory != null
    if (recents.isEmpty() && !offerCreate) return
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        if (recents.isNotEmpty()) {
            Text(
                stringResource(R.string.transaction_form_recent),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
            )
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            recents.forEach { recent ->
                val selected = recent.id == selectedCategoryId
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(68.dp)
                        .clip(MaterialTheme.shapes.small)
                        .clickable(role = Role.Button) { onSelectRecent(recent.id) }
                        .padding(vertical = 6.dp)
                        .semantics(mergeDescendants = true) { this.selected = selected },
                ) {
                    val ring = if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier
                    Box(ring.padding(3.dp)) {
                        IconBadge(recent.icon, recent.color, ColorPalette.defaultFor(categoryType), size = 40)
                    }
                    Text(
                        recent.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            if (offerCreate) {
                FilterPill(
                    label = stringResource(R.string.transaction_form_create_category),
                    active = false,
                    onClick = onCreateCategory,
                )
            }
        }
    }
}

@Composable
internal fun DateShelfRow(
    date: LocalDate,
    time: LocalTime,
    isFuture: Boolean,
    onOpenPicker: () -> Unit,
    onDateChange: (LocalDate) -> Unit,
    onTimeChange: (LocalTime) -> Unit,
) {
    val today = LocalDate.now()
    val value = shortDateLabel(date, today) + ", " + time.format(rememberTimeFormatter())
    ShelfRow(
        icon = { ShelfIcon(AppIcons.Calendar) },
        label = stringResource(R.string.transaction_form_date),
        onClick = onOpenPicker,
        supporting = if (isFuture) stringResource(R.string.transaction_form_error_future_date) else null,
        supportingIsError = isFuture,
        trailing = { ShelfValue(value, color = if (isFuture) MaterialTheme.colorScheme.error else null) },
        below = {
            Row(Modifier.padding(start = 48.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    today to R.string.transactions_day_today,
                    today.minusDays(1) to R.string.transactions_day_yesterday,
                ).forEach { (day, label) ->
                    FilterPill(
                        label = stringResource(label),
                        active = date == day,
                        onClick = {
                            onDateChange(day)
                            // Picking "today" after a later time was chosen for yesterday must not land in the future.
                            if (day == today && time.isAfter(LocalTime.now())) {
                                onTimeChange(LocalTime.now().withSecond(0).withNano(0))
                            }
                        },
                    )
                }
            }
        },
    )
}

/** The note/description row: an inline text field with its validation lines. */
@Composable
internal fun NoteShelfRow(
    label: String,
    hint: String,
    description: String,
    isTooLong: Boolean,
    onChange: (String) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.padding(top = 1.dp)) { ShelfIcon(AppIcons.Edit) }
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            BasicTextField(
                value = description,
                onValueChange = onChange,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                maxLines = 3,
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                decorationBox = { inner ->
                    Box {
                        if (description.isEmpty()) {
                            Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TagsShelfRow(
    allTags: List<Tag>,
    selectedTagIds: Set<Long>,
    pendingTagNames: List<String>,
    onToggleTag: (Long) -> Unit,
    onAddPendingTag: (String) -> Unit,
    onRemovePendingTag: (String) -> Unit,
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    val selected = allTags.filter { it.id in selectedTagIds }
    ShelfRow(
        icon = { ShelfIcon(AppIcons.Tag) },
        label = stringResource(R.string.transaction_form_tags),
        onClick = if (editing) null else ({ editing = true }),
        trailing = {
            if (editing) {
                TextButton(onClick = { editing = false }) { Text(stringResource(R.string.transaction_form_tags_done)) }
            } else if (selected.isEmpty() && pendingTagNames.isEmpty()) {
                ShelfValue(stringResource(R.string.transaction_form_add_tag), color = MaterialTheme.colorScheme.primary)
            } else {
                Icon(AppIcons.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(IconSize.Small))
            }
        },
        below = {
            if (editing) {
                TagPicker(
                    allTags = allTags,
                    selectedTagIds = selectedTagIds,
                    pendingTagNames = pendingTagNames,
                    onToggleTag = onToggleTag,
                    onAddPendingTag = onAddPendingTag,
                    onRemovePendingTag = onRemovePendingTag,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            } else if (selected.isNotEmpty() || pendingTagNames.isNotEmpty()) {
                FlowRow(
                    Modifier.padding(start = 48.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    selected.forEach { tag -> TagChip(tag = tag, onRemove = { onToggleTag(tag.id) }) }
                    pendingTagNames.forEach { name ->
                        PendingTagChip(name) { onRemovePendingTag(name) }
                    }
                }
            }
        },
    )
}

/** The "Add another after saving" switch under the details card. */
@Composable
internal fun AddAnotherRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(MaterialTheme.shapes.small)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // The row handles the toggle so the whole line is the touch target.
        Switch(checked = checked, onCheckedChange = null)
        Text(stringResource(R.string.editor_add_another), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

@Composable
internal fun shortDateLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> stringResource(R.string.transactions_day_today)
    today.minusDays(1) -> stringResource(R.string.transactions_day_yesterday)
    else -> date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
}

/** Money with the symbol in front and locale digit grouping, matching the amount hero. */
internal fun formatMoney(money: Money, symbols: DecimalFormatSymbols): String {
    val plain = money.toPlainString()
    val negative = plain.startsWith("-")
    val body = AmountKeypad.format(plain.removePrefix("-"), symbols.groupingSeparator, symbols.decimalSeparator)
    return (if (negative) FORM_MINUS else "") + money.currency.symbol + body
}
