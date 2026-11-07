package com.yehor.finora.ui.analytics

import com.yehor.finora.domain.account.AccountWithBalance
import com.yehor.finora.domain.analytics.CurrencyScope
import com.yehor.finora.domain.analytics.Measure
import com.yehor.finora.domain.analytics.NativeRow
import com.yehor.finora.domain.analytics.PeriodKind
import com.yehor.finora.domain.analytics.PeriodState
import com.yehor.finora.domain.analytics.ScopeConverter
import com.yehor.finora.domain.category.Category
import com.yehor.finora.domain.model.CategoryType
import com.yehor.finora.domain.model.OperationType
import com.yehor.finora.domain.money.Currency
import com.yehor.finora.domain.money.CurrencyRegistry
import com.yehor.finora.domain.money.Money
import com.yehor.finora.domain.rate.CurrencyPair
import com.yehor.finora.domain.rate.RateQuote
import com.yehor.finora.domain.rate.RateTable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset

class ReportBuilderTest {

    private val pln = CurrencyRegistry.get("PLN")
    private val usd = CurrencyRegistry.get("USD")
    private val now = 1_000_000_000_000L
    private val today = LocalDate.of(2026, 11, 15)

    private val categories = listOf(
        Category(1, "Food", null, null, CategoryType.EXPENSE, null, 0),
        Category(2, "Groceries", null, null, CategoryType.EXPENSE, 1, 0),
        Category(3, "Fun", null, null, CategoryType.EXPENSE, null, 0),
    )

    private fun env(table: RateTable = RateTable.EMPTY): AnalyticsEnv {
        val state = PeriodState.preset(PeriodKind.MONTH)
        return AnalyticsEnv(
            periodState = state,
            period = state.resolve(today, ZoneOffset.UTC, DayOfWeek.MONDAY),
            today = today,
            zone = ZoneOffset.UTC,
            firstDayOfWeek = DayOfWeek.MONDAY,
            accounts = emptyList<AccountWithBalance>(),
            categories = categories,
            tags = emptyList(),
            table = table,
            display = pln,
            now = now,
        )
    }

    private fun row(key: Long?, minor: Long, currency: Currency = pln, count: Int = 1) =
        NativeRow(key, Money(minor, currency), count)

    private val display = CurrencyScope.Display(pln)

    private fun report(
        current: List<NativeRow>,
        previous: List<NativeRow>? = null,
        path: List<Long> = emptyList(),
        scope: CurrencyScope = display,
        table: RateTable = RateTable.EMPTY,
    ) = ReportBuilder.category(
        env(table), scope, Measure.EXPENSE, Rows.Ok(current), previous?.let { Rows.Ok(it) }, path,
    )

    // ---- category ----

    @Test
    fun theTotalIsTheSumOfEveryRowIncludingUncategorized() {
        val report = report(listOf(row(1, 100), row(2, 300), row(3, 50), row(null, 25)))
        assertEquals(475L, report.total.estimate!!.amountMinor)
        assertEquals(475L, report.levelTotal.estimate!!.amountMinor)
    }

    @Test
    fun rowsAtARootLevelAddUpToTheTotal() {
        val report = report(listOf(row(1, 100), row(2, 300), row(3, 50), row(null, 25)))
        val shown = report.lines.sumOf { it.row.cell.estimate!!.amountMinor }
        assertEquals(report.total.estimate!!.amountMinor, shown)
    }

    @Test
    fun aParentRowRollsUpItsChildren() {
        val report = report(listOf(row(1, 100), row(2, 300)))
        val food = report.lines.first { it.row.categoryId == 1L }
        assertEquals(400L, food.row.cell.estimate!!.amountMinor)
        assertTrue(food.row.hasChildren)
    }

    @Test
    fun enteringACategoryShowsItsChildrenAndADirectRowThatAddUpToItsRolledAmount() {
        val report = report(listOf(row(1, 100), row(2, 300)), path = listOf(1))
        val direct = report.lines.single { it.row.isDirect }
        assertEquals(100L, direct.row.cell.estimate!!.amountMinor)
        assertEquals(1L, direct.row.categoryId)
        assertEquals(400L, report.lines.sumOf { it.row.cell.estimate!!.amountMinor })
        assertEquals(listOf("Food"), report.breadcrumb.map { it.name })
    }

    @Test
    fun theLevelTotalDiffersFromThePeriodTotalWhenDrilledIn() {
        val report = report(listOf(row(1, 100), row(2, 300), row(3, 50)), path = listOf(1))
        assertEquals(450L, report.total.estimate!!.amountMinor)
        assertEquals(400L, report.levelTotal.estimate!!.amountMinor)
    }

    @Test
    fun anUnknownPathEntryIsIgnored() {
        val report = report(listOf(row(1, 100)), path = listOf(999))
        assertTrue(report.breadcrumb.isEmpty())
        assertEquals(1, report.lines.size)
    }

    @Test
    fun sharesAreRelativeToTheLevel() {
        val report = report(listOf(row(3, 750), row(1, 250)))
        assertEquals(750, report.lines.first { it.row.categoryId == 3L }.share!!.permille)
        assertEquals(250, report.lines.first { it.row.categoryId == 1L }.share!!.permille)
    }

    @Test
    fun withoutCompareThereIsNoPreviousAndNoDelta() {
        val report = report(listOf(row(1, 100)))
        assertNull(report.previousTotal)
        assertNull(report.totalDelta)
        assertTrue(report.lines.all { it.previous == null && it.delta == null })
    }

    @Test
    fun compareShowsTheChangeOfEveryRow() {
        val report = report(current = listOf(row(3, 150)), previous = listOf(row(3, 100)))
        val line = report.lines.single()
        assertEquals(50L, line.delta!!.amount.amountMinor)
        assertEquals(500, line.delta.percentTimesTen)
        assertEquals(50L, report.totalDelta!!.amount.amountMinor)
    }

    @Test
    fun aCategoryOnlyInTheCurrentPeriodIsNew() {
        val report = report(current = listOf(row(3, 150)), previous = emptyList())
        val line = report.lines.single()
        assertNull(line.delta!!.percentTimesTen)
        assertEquals(150L, line.delta!!.amount.amountMinor)
        assertEquals(0L, report.previousTotal!!.estimate!!.amountMinor)
    }

    @Test
    fun aCategoryOnlyInThePreviousPeriodStaysAsAnEmptyRowWithANegativeDelta() {
        val report = report(current = listOf(row(1, 100)), previous = listOf(row(1, 100), row(3, 80)))
        val gone = report.lines.first { it.row.categoryId == 3L }
        assertNull(gone.row.cell.estimate)
        assertEquals(80L, gone.previous!!.estimate!!.amountMinor)
        assertEquals(-80L, gone.delta!!.amount.amountMinor)
        assertEquals(-1000, gone.delta!!.percentTimesTen)
    }

    @Test
    fun anEmptyPeriodHasAnExactZeroTotalNotAMissingOne() {
        val report = report(emptyList())
        assertEquals(0L, report.total.estimate!!.amountMinor)
        assertTrue(report.lines.isEmpty())
    }

    @Test
    fun anOverflowingSumIsUnavailableNotWrapped() {
        val report = ReportBuilder.category(
            env(), display, Measure.EXPENSE, Rows.Overflow, null, emptyList(),
        )
        assertTrue(report.total.overflowed)
        assertNull(report.total.estimate)
        assertTrue(report.lines.isEmpty())
    }

    @Test
    fun aNativeScopeNeverConverts() {
        val report = report(listOf(row(3, 100, usd)), scope = CurrencyScope.Native(usd))
        val estimate = report.total.estimate!!
        assertEquals(usd, estimate.currency)
        assertFalse(estimate.basis.usesConversion)
    }

    @Test
    fun aMissingRateKeepsTheCurrencyOutOfTheTotalAndMarksItPartial() {
        val report = report(listOf(row(3, 100, pln), row(3, 500, usd)))
        assertEquals(100L, report.total.estimate!!.amountMinor)
        assertEquals(setOf(usd), report.total.excluded)
        assertTrue(report.total.partial)
    }

    @Test
    fun aDirectRateConvertsAndIsMarkedAsAnEstimate() {
        val table = RateTable(
            mapOf(CurrencyPair(usd, pln) to RateQuote(BigDecimal("4.00"), LocalDate.of(2026, 11, 14), now)),
        )
        val report = report(listOf(row(3, 100, pln), row(3, 100, usd)), table = table)
        assertEquals(500L, report.total.estimate!!.amountMinor)
        assertTrue(report.total.estimate.basis.usesConversion)
    }

    // ---- breakdown ----

    @Test
    fun breakdownIsSortedLargestFirstWithSharesOfTheTotal() {
        val converter = ScopeConverter(display, RateTable.EMPTY, now)
        val total = report(listOf(row(3, 400))).total
        val lines = ReportBuilder.breakdown(
            Rows.Ok(listOf(row(10, 100), row(11, 300))), converter, total,
        )
        assertEquals(listOf(11L, 10L), lines.map { it.id })
        assertEquals(750, lines[0].share!!.permille)
    }

    @Test
    fun breakdownKeepsTheNoTagRowAndDropsEmptyOnes() {
        val converter = ScopeConverter(display, RateTable.EMPTY, now)
        val total = report(listOf(row(3, 100))).total
        val lines = ReportBuilder.breakdown(
            Rows.Ok(listOf(row(null, 100), row(5, 0))), converter, total,
        )
        assertEquals(listOf<Long?>(null), lines.map { it.id })
    }

    @Test
    fun breakdownOfAnOverflowIsEmpty() {
        val converter = ScopeConverter(display, RateTable.EMPTY, now)
        assertTrue(ReportBuilder.breakdown(Rows.Overflow, converter, ReportBuilder.OVERFLOW).isEmpty())
    }

    // ---- figures ----

    @Test
    fun figuresForAllTypesHaveNetAsIncomeMinusExpense() {
        val figures = ReportBuilder.figures(
            env(), display, emptySet(),
            income = Rows.Ok(listOf(row(1, 1000))),
            expense = Rows.Ok(listOf(row(1, 300))),
            transfer = Rows.Ok(listOf(row(null, 50))),
        )
        assertEquals(1000L, figures.income!!.estimate!!.amountMinor)
        assertEquals(300L, figures.expense!!.estimate!!.amountMinor)
        assertEquals(700L, figures.net!!.estimate!!.amountMinor)
        assertEquals(50L, figures.transferred!!.estimate!!.amountMinor)
    }

    @Test
    fun aTypeTheFilterExcludesHasNoFigureAndNoNet() {
        val figures = ReportBuilder.figures(
            env(), display, setOf(OperationType.EXPENSE),
            income = Rows.Ok(listOf(row(1, 1000))),
            expense = Rows.Ok(listOf(row(1, 300))),
            transfer = Rows.Ok(emptyList()),
        )
        assertNull(figures.income)
        assertNull(figures.net)
        assertNull(figures.transferred)
        assertEquals(300L, figures.expense!!.estimate!!.amountMinor)
    }

    @Test
    fun aQuietPeriodReadsZeroInTheScopeCurrency() {
        val figures = ReportBuilder.figures(
            env(), display, emptySet(), Rows.Ok(emptyList()), Rows.Ok(emptyList()), Rows.Ok(emptyList()),
        )
        assertEquals(0L, figures.income!!.estimate!!.amountMinor)
        assertEquals(pln, figures.net!!.estimate!!.currency)
    }

    @Test
    fun anOverflowOnOneSideMakesTheNetUnavailable() {
        val figures = ReportBuilder.figures(
            env(), display, emptySet(), Rows.Overflow, Rows.Ok(listOf(row(1, 5))), Rows.Ok(emptyList()),
        )
        assertTrue(figures.income!!.overflowed)
        assertTrue(figures.net!!.overflowed)
        assertNull(figures.net.estimate)
        assertNotNull(figures.expense!!.estimate)
    }

    @Test
    fun figuresCountTheirOperations() {
        val figures = ReportBuilder.figures(
            env(), display, emptySet(),
            income = Rows.Ok(listOf(row(1, 10, count = 2))),
            expense = Rows.Ok(listOf(row(1, 10, count = 3))),
            transfer = Rows.Ok(listOf(row(null, 10, count = 4))),
        )
        assertEquals(9, figures.operationCount)
    }

    // ---- flow ----

    @Test
    fun accountAndTagSharesAreAgainstThePeriodTotalEvenWhenDrilledIn() {
        val buckets = com.yehor.finora.domain.analytics.BucketPlan.months(today, 2, ZoneOffset.UTC)
        val report = ReportBuilder.flow(
            env = env(),
            scope = display,
            measure = Measure.EXPENSE,
            categoryRows = Rows.Ok(listOf(row(1, 100), row(3, 300))),
            previousRows = null,
            accountRows = Rows.Ok(listOf(row(7, 100))),
            tagRows = Rows.Ok(listOf(row(null, 400))),
            trendRows = Rows.Ok(emptyList()),
            trendBuckets = buckets,
            trendYearly = false,
            path = listOf(1),
        )
        assertEquals(250, report.byAccount.single().share!!.permille)
        assertEquals(1000, report.byTag.single().share!!.permille)
        assertEquals(2, report.trend.size)
        assertFalse(report.trendYearly)
    }

    @Test
    fun thePeriodOfTheEnvironmentIsAMonthContainingToday() {
        val e = env()
        assertEquals(LocalDate.of(2026, 11, 1), e.period.fromDate)
        assertEquals(LocalDate.of(2026, 11, 30), e.period.toDateInclusive)
    }
}
