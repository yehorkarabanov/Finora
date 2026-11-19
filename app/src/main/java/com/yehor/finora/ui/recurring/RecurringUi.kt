package com.yehor.finora.ui.recurring

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
import com.yehor.finora.domain.analytics.UpcomingItem
import com.yehor.finora.domain.money.Money
import com.yehor.finora.domain.operation.TransactionKind
import java.time.LocalDate
import com.yehor.finora.designsystem.icon.AppIcons
import com.yehor.finora.designsystem.theme.PillShape
import com.yehor.finora.designsystem.theme.finance
import com.yehor.finora.designsystem.theme.hairline

// Reusable building blocks of the recurring screens (design system 5.1 / 5.3 / 5.5).

@Composable
internal fun RecurringCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable () -> Unit,
) {
    Surface(modifier.fillMaxWidth(), shape = shape, color = color, border = hairline(MaterialTheme.finance.isDark), content = content)
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

/** Read-only badge ("Paused", "Ended"). */
@Composable
internal fun RecurringPill(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Surface(modifier, shape = PillShape, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
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

/** Out and in totals (minor units) of the [items] due within [today]..[today] + 30 days, inclusive. */
fun nextThirtyDays(items: List<UpcomingItem>, today: LocalDate): Pair<Long, Long> {
    val last = today.plusDays(30)
    var out = 0L
    var income = 0L
    items.forEach { item ->
        if (item.date < today || item.date > last) return@forEach
        if (item.schedule.kind == TransactionKind.INCOME) income += item.schedule.money.amountMinor
        else out += item.schedule.money.amountMinor
    }
    return out to income
}

/** Out / in of the next 30 days; one entry per currency because the schedule amounts are in their account currency. */
data class ThirtyDayTotals(val out: Money, val income: Money)

fun nextThirtyDaysByCurrency(items: List<UpcomingItem>, today: LocalDate): List<ThirtyDayTotals> =
    items.groupBy { it.schedule.money.currency }.map { (currency, group) ->
        val (out, income) = nextThirtyDays(group, today)
        ThirtyDayTotals(Money(out, currency), Money(income, currency))
    }.sortedBy { it.out.currency.code }
