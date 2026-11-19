package com.yehor.finora.ui.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.yehor.finora.designsystem.theme.PreviewTheme
import com.yehor.finora.domain.account.Account
import com.yehor.finora.domain.account.AccountType
import com.yehor.finora.domain.analytics.AccountPeriodRow
import com.yehor.finora.domain.analytics.BucketPlan
import com.yehor.finora.domain.analytics.Cell
import com.yehor.finora.domain.analytics.DrillRow
import com.yehor.finora.domain.analytics.Measure
import com.yehor.finora.domain.analytics.PeriodCalculator
import com.yehor.finora.domain.analytics.PeriodKind
import com.yehor.finora.domain.analytics.PeriodState
import com.yehor.finora.domain.analytics.Share
import com.yehor.finora.domain.analytics.TopSplit
import com.yehor.finora.domain.analytics.TrendPoint
import com.yehor.finora.domain.analytics.delta
import com.yehor.finora.domain.category.Category
import com.yehor.finora.domain.model.AccountStatus
import com.yehor.finora.domain.model.CategoryType
import com.yehor.finora.domain.money.CurrencyRegistry
import com.yehor.finora.domain.money.Estimate
import com.yehor.finora.domain.money.EstimateBasis
import com.yehor.finora.domain.money.Money
import com.yehor.finora.domain.operation.TransactionFilter
import com.yehor.finora.domain.tag.Tag
import com.yehor.finora.domain.tag.TagMatchMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset

// Previews of the Statistics content below the trend: categories, tags and accounts.

private object Sample {
    val pln = CurrencyRegistry.get("PLN")
    fun cell(minor: Long, count: Int = 3) = Cell(Estimate(minor, pln, EstimateBasis.NATIVE), count, emptySet(), false, null)

    private fun category(id: Long, name: String, icon: String?, color: Long?, parent: Long?) =
        Category(id, name, icon, color, CategoryType.EXPENSE, parent, 0L)

    val food = category(1, "Food & Dining", "restaurant", 0xFFFF9F6EL, null)
    val transport = category(2, "Transport", "bus", 0xFF78B4FFL, null)
    val subscriptions = category(3, "Subscriptions", "entertainment", 0xFFB18CFFL, null)
    val restaurants = category(10, "Restaurants", "restaurant", null, 1)
    val groceries = category(11, "Groceries", "shopping", null, 1)
    val delivery = category(20, "Delivery", "bus", null, 10)
    val dineIn = category(21, "Dine-in", "restaurant", null, 10)
    val taxi = category(30, "Taxi", "car", null, 2)
    val categories = listOf(food, transport, subscriptions, restaurants, groceries, delivery, dineIn, taxi)

    private fun line(category: Category?, minor: Long, total: Long, children: Boolean = false, direct: Boolean = false) =
        CategoryLine(
            DrillRow(category?.id, category, cell(minor, 4), isDirect = direct, hasChildren = children),
            Share((minor * 1000 / total).toInt()),
            previous = null,
            delta = null,
        )

    private fun report(breadcrumb: List<Category>, lines: List<CategoryLine>): CategoryReport = CategoryReport(
        total = cell(142_050),
        previousTotal = null,
        totalDelta = null,
        levelTotal = cell(lines.sumOf { it.row.cell.estimate?.amountMinor ?: 0L }),
        breadcrumb = breadcrumb,
        lines = lines,
        split = TopSplit(lines.map { it.row }, null, null),
    )

    val level1 = report(
        emptyList(),
        listOf(
            line(food, 81_200, 142_050, children = true),
            line(transport, 31_050, 142_050, children = true),
            line(subscriptions, 18_000, 142_050),
            line(null, 11_800, 142_050),
        ),
    )

    /** Food & Dining › Restaurants, with money booked on Restaurants itself. */
    val level3 = report(
        listOf(food, restaurants),
        listOf(
            line(delivery, 29_000, 47_000),
            line(dineIn, 16_000, 47_000),
            line(restaurants, 2_000, 47_000, direct = true),
        ),
    )

    private fun tag(id: Long, name: String) = Tag(id, name, null, 0L)
    val tags = listOf(tag(1, "trip"), tag(2, "work"), tag(3, "gifts"), tag(4, "kids"))
    val ranking = rankTags(
        tags,
        mapOf(1L to cell(161_000, 31), 2L to cell(70_000, 17), 3L to cell(24_000, 5), 4L to cell(9_600, 8)),
    )

    private fun account(id: Long, name: String, icon: String, color: Long, status: AccountStatus = AccountStatus.ACTIVE) =
        Account(id, name, pln, AccountType.BANK, icon, color, null, status, 0L)

    val accounts = listOf(
        AccountPeriodView(
            account(1, "Revolut", "bank", 0xFFC4A0FFL),
            AccountPeriodRow(1, Money(420_000, pln), Money(161_000, pln), Money(259_000, pln)),
        ),
        AccountPeriodView(
            account(2, "Wallet", "wallet", 0xFFFFA05AL),
            AccountPeriodRow(2, Money(0, pln), Money(70_000, pln), Money(-70_000, pln)),
        ),
    )
    val archived = listOf(
        AccountPeriodView(
            account(3, "Old savings", "savings", 0xFF00E599L, AccountStatus.ARCHIVED),
            AccountPeriodRow(3, Money(0, pln), Money(0, pln), Money(0, pln)),
        ),
    )
}

@Composable
private fun ContentPreview(dark: Boolean, content: @Composable () -> Unit) = PreviewTheme(dark) {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}

@Composable
private fun CategoriesPreview(dark: Boolean, report: CategoryReport) = ContentPreview(dark) {
    CategoryCard(
        report = report,
        measure = Measure.EXPENSE,
        base = TransactionFilter(),
        compare = false,
        categories = Sample.categories,
        onEnter = {},
        onBack = {},
        onOpen = {},
    )
}

@Preview(showBackground = true, heightDp = 520)
@Composable
private fun CategoriesLevel1Preview() = CategoriesPreview(dark = false, Sample.level1)

@Preview(showBackground = true, heightDp = 520, backgroundColor = 0xFF121314)
@Composable
private fun CategoriesLevel1DarkPreview() = CategoriesPreview(dark = true, Sample.level1)

@Preview(showBackground = true, heightDp = 520)
@Composable
private fun CategoriesLevel3Preview() = CategoriesPreview(dark = false, Sample.level3)

@Preview(showBackground = true, heightDp = 520, backgroundColor = 0xFF121314)
@Composable
private fun CategoriesLevel3DarkPreview() = CategoriesPreview(dark = true, Sample.level3)

@Composable
private fun TagRankingPreview(dark: Boolean) = ContentPreview(dark) {
    TagRankingSection(
        rows = Sample.ranking,
        measure = Measure.EXPENSE,
        measureEnabled = true,
        title = "Tags in November",
        onMeasure = {},
        onOpen = {},
        onCombine = {},
    )
}

@Preview(showBackground = true, heightDp = 520)
@Composable
private fun TagRankingLightPreview() = TagRankingPreview(dark = false)

@Preview(showBackground = true, heightDp = 520, backgroundColor = 0xFF121314)
@Composable
private fun TagRankingDarkPreview() = TagRankingPreview(dark = true)

@Composable
private fun TagDetailPreview(dark: Boolean) {
    val zone = ZoneOffset.UTC
    val today = LocalDate.of(2026, 11, 10)
    val period = PeriodState.preset(PeriodKind.YEAR).resolve(today, zone, DayOfWeek.MONDAY)
    val previous = PeriodCalculator.resolve(PeriodCalculator.previous(period.period), zone, DayOfWeek.MONDAY)
    val (unit, buckets) = BucketPlan.auto(period, zone)
    val points = buckets.mapIndexed { i, b -> TrendPoint(b, Sample.cell(10_000L + (i * 37_919L) % 90_000L)) }
    val figures = Figures(
        income = Sample.cell(90_000),
        expense = Sample.cell(231_000, 48),
        net = Sample.cell(-141_000),
        transferred = Sample.cell(0, 0),
    )
    ContentPreview(dark) {
        TagChips(Sample.tags, setOf(1L, 2L), TagMatchMode.ANY, onToggle = {}, onMode = {})
        TagFigures(
            figures = figures,
            measure = Measure.EXPENSE,
            delta = delta(Sample.cell(231_000).estimate, Sample.cell(206_000).estimate),
            previous = previous,
            today = today,
            onOpen = {},
        )
        OverTimeCard(
            points = points,
            unit = unit,
            measure = Measure.EXPENSE,
            measureEnabled = true,
            today = today,
            onMeasure = {},
            onOpenBucket = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 640)
@Composable
private fun TagDetailLightPreview() = TagDetailPreview(dark = false)

@Preview(showBackground = true, heightDp = 640, backgroundColor = 0xFF121314)
@Composable
private fun TagDetailDarkPreview() = TagDetailPreview(dark = true)

@Composable
private fun AccountsPreview(dark: Boolean) = ContentPreview(dark) {
    AccountPeriodCard(Sample.accounts, onOpen = {})
    AccountPeriodCard(Sample.archived, onOpen = {}, Modifier.alpha(0.6f))
}

@Preview(showBackground = true, heightDp = 400)
@Composable
private fun AccountsLightPreview() = AccountsPreview(dark = false)

@Preview(showBackground = true, heightDp = 400, backgroundColor = 0xFF121314)
@Composable
private fun AccountsDarkPreview() = AccountsPreview(dark = true)
