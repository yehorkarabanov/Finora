package com.yehor.finora.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yehor.finora.R
import com.yehor.finora.designsystem.component.display.AmountText
import com.yehor.finora.designsystem.component.list.RoundIcon
import com.yehor.finora.designsystem.icon.AppIcons
import com.yehor.finora.designsystem.icon.IconCatalog
import com.yehor.finora.designsystem.theme.AmountStyle
import com.yehor.finora.designsystem.theme.PillShape
import com.yehor.finora.designsystem.theme.finance
import com.yehor.finora.domain.model.OperationType
import com.yehor.finora.domain.operation.TransactionItem
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * One transaction: category-colored round icon, title, "Category · Account" with tag chips, amount and time.
 * Transfers read "A → B" with the sent / received pair. Place it inside a `GroupedCard`; it has no background.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TransactionRow(
    item: TransactionItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isTransfer = item.type == OperationType.TRANSFER
    val uncategorized = stringResource(R.string.picker_uncategorized)
    val transferLabel = stringResource(R.string.transaction_kind_transfer)
    val timeFormat = remember { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT) }
    val time = remember(item.occurredAt) {
        Instant.ofEpochMilli(item.occurredAt).atZone(ZoneId.systemDefault()).format(timeFormat)
    }

    // A transfer has two entries; the source is the negative one, whatever order they are stored in.
    val transferSides = if (isTransfer && item.entries.size == 2) {
        val (a, b) = item.entries
        if (a.money.amountMinor <= b.money.amountMinor) a to b else b to a
    } else {
        null
    }
    val pairDiffers = transferSides != null && !(
        transferSides.first.money.currency.code == transferSides.second.money.currency.code &&
            -transferSides.first.money.amountMinor == transferSides.second.money.amountMinor
        )

    val description = item.description?.takeIf { it.isNotBlank() }
    val title = when {
        transferSides != null -> "${transferSides.first.account.name} → ${transferSides.second.account.name}"
        else -> description ?: item.category?.name ?: if (isTransfer) transferLabel else uncategorized
    }
    val accountName = item.entries.firstOrNull()?.account?.name.orEmpty()
    val subtitle = when {
        transferSides != null -> listOfNotNull(
            description ?: transferLabel,
            if (pairDiffers) "${transferSides.first.money.format().removePrefix("-")} → ${transferSides.second.money.format()}" else null,
        ).joinToString(" · ")
        // When there is no description the title already is the category, so don't repeat it.
        description != null -> "${item.category?.name ?: uncategorized} · $accountName"
        else -> accountName
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val category = item.category
        when {
            isTransfer -> NeutralBadge(AppIcons.Transfer)
            category == null -> NeutralBadge(AppIcons.Help)
            else -> RoundIcon(
                icon = IconCatalog.get(category.icon),
                tint = MaterialTheme.finance.accent(ColorPalette.resolve(category.color, ColorPalette.defaultFor(category.type))),
                size = 40.dp,
            )
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (item.recurringScheduleId != null) {
                    Icon(
                        AppIcons.Recurring,
                        contentDescription = stringResource(R.string.recurring_marker),
                        modifier = Modifier.padding(start = 6.dp).size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (item.tags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    item.tags.take(2).forEach { tag -> TagChip("#${tag.name}") }
                    if (item.tags.size > 2) TagChip("+${item.tags.size - 2}")
                }
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            if (transferSides != null) {
                // A transfer is neither income nor expense: neutral, the received side.
                AmountText(
                    transferSides.second.money.format(),
                    AmountStyle,
                    MaterialTheme.colorScheme.onSurfaceVariant,
                    animate = false,
                )
            } else {
                item.entries.forEach { entry ->
                    val minor = entry.money.amountMinor
                    val amountText = if (minor > 0) "+${entry.money.format()}" else entry.money.format()
                    val color = when {
                        minor > 0 -> MaterialTheme.finance.income
                        minor < 0 -> MaterialTheme.finance.expense
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                    AmountText(amountText, AmountStyle, color, animate = false)
                }
            }
            Text(time, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun NeutralBadge(icon: ImageVector) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun TagChip(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, PillShape)
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}
