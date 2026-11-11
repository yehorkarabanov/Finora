package com.yehor.finora.ui.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yehor.finora.R
import com.yehor.finora.domain.money.Currency
import com.yehor.finora.designsystem.icon.AppIcons
import com.yehor.finora.designsystem.icon.IconSize
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Not tappable and navigates nowhere (design section 5.1). Accounts-tab hero. */
@Composable
fun ValuationCard(state: ValuationCardState, modifier: Modifier = Modifier, accountCount: Int? = null) {
    AccountsCard(modifier) {
        Text(
            stringResource(if (state.estimated) R.string.valuation_title_estimate else R.string.valuation_title_native),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        val total = state.total
        if (total != null) {
            Text(
                total.format(),
                style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        } else {
            Text(
                stringResource(R.string.valuation_unavailable),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.outdated) {
            Row(
                Modifier.padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    AppIcons.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(IconSize.Small),
                )
                Text(
                    stringResource(R.string.valuation_outdated, formatDate(state.oldestFetchedAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        NotIncludedLine(state.excluded)
        if (accountCount != null && accountCount > 0) {
            Text(
                pluralStringResource(R.plurals.account_list_active_count, accountCount, accountCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
fun NotIncludedLine(excluded: List<Currency>) {
    if (excluded.isEmpty()) return
    Text(
        stringResource(R.string.valuation_not_included, excluded.joinToString(", ") { it.code }),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

fun formatDate(epochMillis: Long?): String =
    if (epochMillis == null) {
        ""
    } else {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            .format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
    }
