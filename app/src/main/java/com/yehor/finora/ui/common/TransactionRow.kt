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
import androidx.compose.ui.unit.dp
import com.yehor.finora.R
import com.yehor.finora.ui.theme.finance
import com.yehor.finora.domain.model.CategoryType
import com.yehor.finora.domain.model.OperationType
import com.yehor.finora.domain.operation.TransactionItem

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

    val subtitle = if (isTransfer && item.entries.size == 2) {
        "${item.entries[0].account.name} → ${item.entries[1].account.name}"
    } else {
        "${item.category?.name ?: uncategorized} · ${item.entries.firstOrNull()?.account?.name.orEmpty()}"
    }

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
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = color,
                    )
                }
            }
        },
    )
}
