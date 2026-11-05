package com.yehor.finora.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yehor.finora.R
import com.yehor.finora.domain.model.CategoryType
import com.yehor.finora.domain.model.OperationType
import com.yehor.finora.domain.operation.TransactionItem

private val IncomeGreen = Color(0xFF2E7D32)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TransactionRow(
    item: TransactionItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
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
        leadingContent = {
            if (isTransfer) {
                Icon(
                    Icons.Default.SwapHoriz,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
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
                        minor > 0 -> IncomeGreen
                        minor < 0 -> MaterialTheme.colorScheme.error
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
