package com.yehor.finora.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yehor.finora.R
import com.yehor.finora.ui.theme.finance
import com.yehor.finora.domain.model.CategoryType
import com.yehor.finora.domain.model.OperationType
import com.yehor.finora.domain.operation.TransactionItem
import com.yehor.finora.ui.theme.AmountStyle

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TransactionRow(
    item: TransactionItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    colors: ListItemColors = ListItemDefaults.colors(),
) {
    val isTransfer = item.type == OperationType.TRANSFER
    val uncategorized = stringResource(R.string.picker_uncategorized)

    val title = item.description?.takeIf { it.isNotBlank() }
        ?: item.category?.name
        ?: if (isTransfer) stringResource(R.string.transaction_kind_transfer) else uncategorized

    // A transfer has two entries; the source is the negative one, whatever order they are stored in.
    val transferSides = if (isTransfer && item.entries.size == 2) {
        val (a, b) = item.entries
        if (a.money.amountMinor <= b.money.amountMinor) a to b else b to a
    } else {
        null
    }

    // When there is no description the title already is the category, so don't repeat it.
    val hasDescription = !item.description.isNullOrBlank()
    val accountName = item.entries.firstOrNull()?.account?.name.orEmpty()
    val subtitle = if (hasDescription) "${item.category?.name ?: uncategorized} · $accountName" else accountName

    ListItem(
        modifier = modifier.clickable(onClick = onClick),
        colors = colors,
        leadingContent = {
            if (isTransfer) {
                // Transfer uses primary / primaryContainer (design system §2).
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        AppIcons.Transfer,
                        contentDescription = null,
                        modifier = Modifier.size(IconSize.Default),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            } else {
                val category = item.category
                IconBadge(
                    icon = category?.icon,
                    color = category?.color,
                    fallbackColor = ColorPalette.defaultFor(category?.type ?: CategoryType.EXPENSE),
                    size = 40,
                )
            }
        },
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
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
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (transferSides != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            transferSides.first.account.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        // Not weighted: always measured, never hidden by long names.
                        Icon(
                            AppIcons.TransferDirection,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp).size(20.dp),
                        )
                        Text(
                            transferSides.second.account.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                } else if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (item.tags.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        item.tags.take(2).forEach { tag ->
                            Text(
                                "#${tag.name}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        if (item.tags.size > 2) {
                            Text(
                                "+${item.tags.size - 2}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                if (transferSides != null) {
                    // A transfer is neither income nor expense: neutral colors, source first.
                    val sent = transferSides.first.money
                    val received = transferSides.second.money
                    if (sent.currency.code == received.currency.code && -sent.amountMinor == received.amountMinor) {
                        Text(
                            received.format(),
                            style = AmountStyle,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    } else {
                        Text(
                            "−" + sent.format().removePrefix("-"),
                            style = AmountStyle,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "+" + received.format(),
                            style = AmountStyle,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    return@Column
                }
                item.entries.forEach { entry ->
                    val minor = entry.money.amountMinor
                    val amountText = if (minor > 0) "+${entry.money.format()}" else entry.money.format()
                    val color = when {
                        minor > 0 -> MaterialTheme.finance.income
                        minor < 0 -> MaterialTheme.finance.expense
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                    Text(
                        amountText,
                        style = AmountStyle,
                        fontWeight = FontWeight.SemiBold,
                        color = color,
                    )
                }
            }
        },
    )
}
