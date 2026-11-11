package com.yehor.finora.ui.analytics

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.yehor.finora.designsystem.theme.PreviewTheme
import com.yehor.finora.domain.account.Account
import com.yehor.finora.domain.account.AccountType
import com.yehor.finora.domain.analytics.Cell
import com.yehor.finora.domain.model.AccountStatus
import com.yehor.finora.domain.money.CurrencyRegistry
import com.yehor.finora.domain.money.Estimate
import com.yehor.finora.domain.money.EstimateBasis
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDate
import java.time.ZoneOffset

private enum class HomeSample { POSITIVE, NEGATIVE, EMPTY, NO_ACCOUNTS }

@Preview(showBackground = true, heightDp = 1200)
@Composable
private fun HomePositivePreview() = HomePreview(dark = false, HomeSample.POSITIVE)

@Preview(showBackground = true, heightDp = 1200, backgroundColor = 0xFF121314)
@Composable
private fun HomePositiveDarkPreview() = HomePreview(dark = true, HomeSample.POSITIVE)

@Preview(showBackground = true, heightDp = 1200)
@Composable
private fun HomeNegativePreview() = HomePreview(dark = false, HomeSample.NEGATIVE)

@Preview(showBackground = true, heightDp = 1200, backgroundColor = 0xFF121314)
@Composable
private fun HomeNegativeDarkPreview() = HomePreview(dark = true, HomeSample.NEGATIVE)

@Preview(showBackground = true, heightDp = 1200)
@Composable
private fun HomeNoDataPreview() = HomePreview(dark = false, HomeSample.EMPTY)

@Preview(showBackground = true, heightDp = 1200, backgroundColor = 0xFF121314)
@Composable
private fun HomeNoDataDarkPreview() = HomePreview(dark = true, HomeSample.EMPTY)

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun HomeNoAccountsPreview() = HomePreview(dark = false, HomeSample.NO_ACCOUNTS)

@Preview(showBackground = true, heightDp = 800, backgroundColor = 0xFF121314)
@Composable
private fun HomeNoAccountsDarkPreview() = HomePreview(dark = true, HomeSample.NO_ACCOUNTS)

@Composable
private fun HomePreview(dark: Boolean, sample: HomeSample) {
    val pln = CurrencyRegistry.get("PLN")
    fun cell(minor: Long) = Cell(Estimate(minor, pln, EstimateBasis.NATIVE), 3, emptySet(), false, null)
    fun account(id: Long, name: String) = Account(id, name, pln, AccountType.BANK, "wallet", null, null, AccountStatus.ACTIVE, 0)
    val today = LocalDate.of(2026, 11, 10)
    val period = HomePeriod.current(HomeMode.DAYS, today)
    val (unit, buckets) = period.buckets(ZoneOffset.UTC, today)
    val (income, expense) = when (sample) {
        HomeSample.POSITIVE -> List(buckets.size) { if (it == 4) 520_000L else 0L } to List(buckets.size) { if (it % 2 == 0) 31_000L else 9_000L }
        HomeSample.NEGATIVE -> List(buckets.size) { 0L } to List(buckets.size) { 12_000L + it * 1_500L }
        HomeSample.EMPTY, HomeSample.NO_ACCOUNTS -> List(buckets.size) { 0L } to List(buckets.size) { 0L }
    }
    val last = period.lastElapsedIndex(today)
    val incomeTotal = income.take(last + 1).sum()
    val expenseTotal = expense.take(last + 1).sum()
    val chart = HomeChart(
        unit = unit,
        buckets = buckets,
        values = homeChartValues(period.mode, income.map(::cell), expense.map(::cell), last),
        net = cell(incomeTotal - expenseTotal),
        income = cell(incomeTotal),
        expense = cell(expenseTotal),
        currencyApprox = false,
    )
    PreviewTheme(dark) {
        DashboardContent(
            state = DashboardUiState(
                loading = false,
                hasAccounts = sample != HomeSample.NO_ACCOUNTS,
                today = today,
                display = pln,
                homePeriod = period,
                chart = chart,
                accounts = if (sample == HomeSample.NO_ACCOUNTS) emptyList() else listOf(account(1, "Santander"), account(2, "Cash")),
            ),
            actions = DashboardActions({}, {}, {}, {}, {}, {}, {}, {}),
            chartFor = { flowOf(chart) },
            onMode = {},
            onShow = {},
            onRange = { _, _ -> },
            onToggleAccount = {},
            onClearAccountSelection = {},
        )
    }
}
