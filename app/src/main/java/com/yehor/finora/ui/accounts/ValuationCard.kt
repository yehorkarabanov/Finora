package com.yehor.finora.ui.accounts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yehor.finora.R
import com.yehor.finora.domain.money.Currency
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Not tappable and navigates nowhere (design section 5.1). */
@Composable
fun ValuationCard(state: ValuationCardState, modifier: Modifier = Modifier) {
    val warning = state.outdated
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (warning) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(if (state.estimated) R.string.valuation_title_estimate else R.string.valuation_title_native),
                style = MaterialTheme.typography.labelLarge,
            )
            val total = state.total
            if (total != null) {
                Text(total.format(), style = MaterialTheme.typography.headlineMedium)
            } else {
                Text(stringResource(R.string.valuation_unavailable), style = MaterialTheme.typography.headlineSmall)
            }
            if (warning) {
                Text(
                    stringResource(R.string.valuation_outdated, formatDate(state.oldestFetchedAt)),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            NotIncludedLine(state.excluded)
        }
    }
}

@Composable
fun NotIncludedLine(excluded: List<Currency>) {
    if (excluded.isEmpty()) return
    Text(
        stringResource(R.string.valuation_not_included, excluded.joinToString(", ") { it.code }),
        style = MaterialTheme.typography.bodySmall,
    )
}

fun formatDate(epochMillis: Long?): String =
    if (epochMillis == null) {
        ""
    } else {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            .format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
    }
