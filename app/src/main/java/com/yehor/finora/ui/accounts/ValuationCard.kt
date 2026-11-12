package com.yehor.finora.ui.accounts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yehor.finora.R
import com.yehor.finora.domain.money.Currency
import com.yehor.finora.designsystem.icon.AppIcons
import com.yehor.finora.designsystem.icon.IconSize
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** The small warning lines under the Accounts total: outdated rates and currencies left out of the total. */
@Composable
fun ValuationNotes(state: ValuationCardState, modifier: Modifier = Modifier) {
    Column(modifier) {
        if (state.outdated) {
            Row(
                Modifier.padding(top = 6.dp),
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
