package com.yehor.finora.ui.recurring

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yehor.finora.domain.operation.TransactionKind
import com.yehor.finora.ui.common.AppIcons
import com.yehor.finora.ui.common.IconSize
import com.yehor.finora.ui.theme.finance

// Reusable building blocks of the recurring screens (design system 5.1 / 5.3 / 5.5).

internal val RecurringGroupTopShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
internal val RecurringGroupMiddleShape = RoundedCornerShape(4.dp)
internal val RecurringGroupBottomShape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
internal val RecurringGroupSingleShape = RoundedCornerShape(20.dp)

/** Shape of item [index] in a connected group of [size] rows. */
internal fun recurringGroupShape(index: Int, size: Int): Shape = when {
    size <= 1 -> RecurringGroupSingleShape
    index == 0 -> RecurringGroupTopShape
    index == size - 1 -> RecurringGroupBottomShape
    else -> RecurringGroupMiddleShape
}

@Composable
internal fun RecurringCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable () -> Unit,
) {
    Surface(modifier.fillMaxWidth(), shape = shape, color = color, content = content)
}

@Composable
internal fun RecurringSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/** A row of a connected group: [shape] from [recurringGroupShape], optional click, optional error border. */
@Composable
internal fun RecurringRowCard(
    modifier: Modifier = Modifier,
    shape: Shape = RecurringGroupSingleShape,
    onClick: (() -> Unit)? = null,
    isError: Boolean = false,
    content: @Composable () -> Unit,
) {
    val border = if (isError) BorderStroke(1.dp, MaterialTheme.colorScheme.error) else null
    val rowModifier = modifier.fillMaxWidth()
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = rowModifier,
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = border,
        ) { Box(Modifier.padding(14.dp)) { content() } }
    } else {
        Surface(
            modifier = rowModifier,
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = border,
        ) { Box(Modifier.padding(14.dp)) { content() } }
    }
}

@Composable
internal fun RecurringLeadingIcon(icon: ImageVector, modifier: Modifier = Modifier) {
    Box(
        modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(IconSize.Default))
    }
}

/** Read-only badge ("Paused", "Ended"). */
@Composable
internal fun RecurringPill(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Surface(modifier, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
            }
            Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Circle in the kind container color with the Income / Expense arrow. */
@Composable
internal fun RecurringKindBadge(kind: TransactionKind, size: Dp, modifier: Modifier = Modifier) {
    val income = kind == TransactionKind.INCOME
    val finance = MaterialTheme.finance
    Box(
        modifier.size(size).clip(CircleShape).background(if (income) finance.incomeContainer else finance.expenseContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (income) AppIcons.Income else AppIcons.Expense,
            contentDescription = null,
            tint = if (income) finance.income else finance.expense,
            modifier = Modifier.size(size * 0.55f),
        )
    }
}

@Composable
internal fun recurringSignedAmountColor(kind: TransactionKind): Color =
    if (kind == TransactionKind.INCOME) MaterialTheme.finance.income else MaterialTheme.finance.expense
