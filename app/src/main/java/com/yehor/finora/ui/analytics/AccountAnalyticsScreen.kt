package com.yehor.finora.ui.analytics

import com.yehor.finora.ui.common.AppIcons
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yehor.finora.R
import com.yehor.finora.domain.analytics.BucketUnit
import com.yehor.finora.domain.model.AccountStatus
import com.yehor.finora.domain.operation.TransactionFilter
import com.yehor.finora.domain.operation.TransactionItem
import com.yehor.finora.ui.accounts.BalanceText
import com.yehor.finora.ui.accounts.formatDate
import com.yehor.finora.ui.common.ColorPalette
import com.yehor.finora.ui.common.IconBadge
import com.yehor.finora.ui.common.TransactionRow
import com.yehor.finora.ui.common.charts.BalanceLine
import java.time.format.DateTimeFormatter

@Composable
fun AccountAnalyticsScreen(
    onBack: () -> Unit,
    onOpenTransaction: (TransactionItem) -> Unit,
    onShowAll: (TransactionFilter) -> Unit,
    viewModel: AccountAnalyticsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state) {
        if (state is AccountAnalyticsUiState.Missing) onBack()
    }
    AccountAnalyticsContent(state, viewModel, onBack, onOpenTransaction, onShowAll)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountAnalyticsContent(
    state: AccountAnalyticsUiState,
    vm: AccountAnalyticsViewModel,
    onBack: () -> Unit,
    onOpenTransaction: (TransactionItem) -> Unit,
    onShowAll: (TransactionFilter) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.stats_account_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(AppIcons.Back, contentDescription = stringResource(R.string.navigate_back))
                    }
                },
            )
        },
    ) { padding ->
        val loaded = state as? AccountAnalyticsUiState.Loaded
        if (loaded == null) {
            Box(Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }
        val env = loaded.env
        val account = loaded.detail.account
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (account.status == AccountStatus.ARCHIVED) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Text(
                        stringResource(R.string.stats_account_archived),
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(account.icon, account.color, ColorPalette.ACCOUNT_DEFAULT, size = 48)
                Column(Modifier.padding(start = 12.dp)) {
                    Text(account.name, style = MaterialTheme.typography.titleLarge)
                    Text(
                        account.currency.code,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column {
                Text(
                    stringResource(R.string.stats_account_current_balance),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                BalanceText(loaded.detail.balance, large = true)
            }
            val opening = loaded.detail.openingBalance
            LabeledValue(
                stringResource(R.string.stats_account_initial_balance),
                if (opening == null) {
                    stringResource(R.string.account_detail_no_initial_balance)
                } else {
                    "${opening.money.format()} · ${formatDate(opening.occurredAt)}"
                },
            )

            HorizontalDivider()
            PeriodSelector(
                state = env.periodState,
                period = env.period,
                onSelect = vm::selectPeriod,
                onCustom = vm::setCustomPeriod,
                modifier = Modifier.fillMaxWidth(),
                arrows = true,
                canShiftForward = env.canShiftForward,
                onShift = vm::shiftPeriod,
            )
            val figures = loaded.figures
            if (figures == null) {
                Text(stringResource(R.string.valuation_unavailable), style = MaterialTheme.typography.bodyLarge)
            } else {
                LabeledValue(stringResource(R.string.stats_account_balance_start), figures.balanceAtStart.format())
                LabeledValue(stringResource(R.string.stats_account_income), figures.income.format())
                LabeledValue(stringResource(R.string.stats_account_expense), figures.expense.format())
                LabeledValue(stringResource(R.string.stats_account_transfers_in), figures.transfersIn.format())
                LabeledValue(stringResource(R.string.stats_account_transfers_out), figures.transfersOut.format())
                if (figures.initialBalanceInPeriod.amountMinor != 0L) {
                    LabeledValue(
                        stringResource(R.string.stats_account_initial_balance),
                        figures.initialBalanceInPeriod.format(),
                    )
                }
                LabeledValue(stringResource(R.string.stats_account_balance_end), figures.balanceAtEnd.format())
                figures.netChange?.let { LabeledValue(stringResource(R.string.stats_account_net), signedMoney(it)) }
            }

            HorizontalDivider()
            Text(stringResource(R.string.stats_account_balance_over_time), style = MaterialTheme.typography.titleMedium)
            if (loaded.series.isEmpty()) {
                Text(stringResource(R.string.valuation_unavailable), style = MaterialTheme.typography.bodyMedium)
            } else {
                BalanceSeriesSection(loaded)
            }

            HorizontalDivider()
            Text(stringResource(R.string.dashboard_recent), style = MaterialTheme.typography.titleMedium)
            if (loaded.recent.isEmpty()) {
                Text(
                    stringResource(R.string.stats_no_data),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                loaded.recent.forEach { item -> TransactionRow(item, onClick = { onOpenTransaction(item) }) }
                TextButton(onClick = { onShowAll(loaded.filter) }) {
                    Text(stringResource(R.string.action_show_transactions))
                }
            }
        }
    }
}

@Composable
private fun BalanceSeriesSection(loaded: AccountAnalyticsUiState.Loaded) {
    val series = loaded.series
    val labels = series.map { pointLabel(it.bucket.fromDate, loaded.seriesUnit) }
    val values = series.map { it.balance.amountMinor }
    val description = series.indices.joinToString(". ") { "${labels[it]} ${series[it].balance.format()}" }
    BalanceLine(
        values = values,
        color = MaterialTheme.colorScheme.primary,
        description = description,
        modifier = Modifier.padding(vertical = 8.dp),
    )
    // The numbers behind the line, as text.
    series.forEachIndexed { i, point ->
        LabeledValue(labels[i], point.balance.format())
    }
}

private fun pointLabel(date: java.time.LocalDate, unit: BucketUnit): String = when (unit) {
    BucketUnit.DAY -> date.format(DateTimeFormatter.ofPattern("d MMM"))
    BucketUnit.MONTH -> date.format(DateTimeFormatter.ofPattern("MMM yy"))
    BucketUnit.YEAR -> date.year.toString()
}

@Composable
private fun LabeledValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
