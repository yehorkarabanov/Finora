package com.yehor.finora.domain.analytics

import com.yehor.finora.domain.money.CurrencyRegistry
import com.yehor.finora.domain.money.Money
import com.yehor.finora.domain.money.MoneyOverflowException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

class BucketPlanTest {

    private val utc = ZoneId.of("UTC")

    private fun resolved(from: LocalDate, to: LocalDate) =
        PeriodCalculator.resolve(Period(PeriodKind.CUSTOM, from, to), utc, DayOfWeek.MONDAY)

    @Test
    fun sixtyTwoDaysIsDayBucketsAndSixtyThreeIsMonth() {
        val start = LocalDate.of(2026, 1, 1)
        val (unit62, buckets62) = BucketPlan.auto(resolved(start, start.plusDays(61)), utc)
        assertEquals(BucketUnit.DAY, unit62)
        assertEquals(62, buckets62.size)
        val (unit63, _) = BucketPlan.auto(resolved(start, start.plusDays(62)), utc)
        assertEquals(BucketUnit.MONTH, unit63)
    }

    @Test
    fun thirtySixMonthsIsMonthAndThirtySevenIsYear() {
        val (u36, b36) = BucketPlan.auto(resolved(LocalDate.of(2024, 1, 1), LocalDate.of(2026, 12, 31)), utc)
        assertEquals(BucketUnit.MONTH, u36)
        assertEquals(36, b36.size)
        val (u37, b37) = BucketPlan.auto(resolved(LocalDate.of(2024, 1, 1), LocalDate.of(2027, 1, 31)), utc)
        assertEquals(BucketUnit.YEAR, u37)
        assertEquals(4, b37.size)
    }

    @Test
    fun firstAndLastMonthBucketAreClipped() {
        val (unit, buckets) = BucketPlan.auto(resolved(LocalDate.of(2026, 3, 15), LocalDate.of(2026, 6, 10)), utc)
        assertEquals(BucketUnit.MONTH, unit)
        assertEquals(4, buckets.size)
        assertEquals(LocalDate.of(2026, 3, 15), buckets.first().fromDate)
        assertEquals(LocalDate.of(2026, 3, 31), buckets.first().toDateInclusive)
        assertEquals(LocalDate.of(2026, 6, 1), buckets.last().fromDate)
        assertEquals(LocalDate.of(2026, 6, 10), buckets.last().toDateInclusive)
    }

    @Test
    fun autoBucketsAreContiguousAndCoverThePeriodExactly() {
        val period = resolved(LocalDate.of(2026, 3, 15), LocalDate.of(2026, 6, 10))
        val (_, buckets) = BucketPlan.auto(period, utc)
        assertEquals(period.from, buckets.first().from)
        assertEquals(period.toExclusive, buckets.last().toExclusive)
        buckets.zipWithNext().forEach { (a, b) -> assertEquals(a.toExclusive, b.from) }
        assertEquals(buckets.indices.toList(), buckets.map { it.index })
    }

    @Test
    fun monthsEndAtTheBucketContainingTheEndDate() {
        val buckets = BucketPlan.months(LocalDate.of(2026, 11, 7), 12, utc)
        assertEquals(12, buckets.size)
        assertEquals(LocalDate.of(2025, 12, 1), buckets.first().fromDate)
        assertEquals(LocalDate.of(2026, 11, 1), buckets.last().fromDate)
        assertEquals(LocalDate.of(2026, 11, 30), buckets.last().toDateInclusive)
    }

    @Test
    fun yearsEndAtTheYearContainingTheEndDate() {
        val buckets = BucketPlan.years(LocalDate.of(2026, 3, 1), 5, utc)
        assertEquals(5, buckets.size)
        assertEquals(LocalDate.of(2022, 1, 1), buckets.first().fromDate)
        assertEquals(LocalDate.of(2026, 12, 31), buckets.last().toDateInclusive)
    }

    @Test
    fun noPlanExceedsOneHundredBuckets() {
        assertEquals(100, BucketPlan.months(LocalDate.of(2026, 1, 1), 500, utc).size)
        assertEquals(100, BucketPlan.years(LocalDate.of(2026, 1, 1), 500, utc).size)
        val (_, auto) = BucketPlan.auto(resolved(LocalDate.of(1900, 1, 1), LocalDate.of(2100, 12, 31)), utc)
        assertTrue(auto.size <= BucketPlan.MAX_BUCKETS)
    }

    @Test
    fun dstDayBucketsHaveTheirRealLength() {
        val zone = ZoneId.of("Europe/Warsaw")
        val period = PeriodCalculator.resolve(
            Period(PeriodKind.CUSTOM, LocalDate.of(2026, 10, 24), LocalDate.of(2026, 10, 26)),
            zone,
            DayOfWeek.MONDAY,
        )
        val (_, buckets) = BucketPlan.auto(period, zone)
        assertEquals(listOf(24L, 25L, 24L), buckets.map { (it.toExclusive - it.from) / 3_600_000L })
    }
}

class BalanceSeriesTest {

    private val pln = CurrencyRegistry.get("PLN")
    private val utc = ZoneId.of("UTC")
    private val buckets = BucketPlan.months(LocalDate.of(2026, 3, 10), 3, utc)

    @Test
    fun valuesAreCumulativeFromTheStartBalance() {
        val points = BalanceSeries.build(1000, pln, buckets, mapOf(0 to 500L, 2 to -200L))
        assertEquals(listOf(1500L, 1500L, 1300L), points.map { it.balance.amountMinor })
        assertEquals(Money(1300, pln), points.last().balance)
    }

    @Test
    fun emptyDeltasKeepTheStartBalance() {
        val points = BalanceSeries.build(42, pln, buckets, emptyMap())
        assertTrue(points.all { it.balance.amountMinor == 42L })
    }

    @Test
    fun overflowThrows() {
        var thrown = false
        try {
            BalanceSeries.build(Long.MAX_VALUE, pln, buckets, mapOf(1 to 1L))
        } catch (e: MoneyOverflowException) {
            thrown = true
        }
        assertTrue(thrown)
    }
}

class AccountFiguresTest {

    private val pln = CurrencyRegistry.get("PLN")
    private fun m(minor: Long) = Money(minor, pln)

    private fun figures(
        start: Long, end: Long, income: Long = 0, expense: Long = 0, inn: Long = 0, out: Long = 0, initial: Long = 0,
    ) = AccountFigures(pln, m(start), m(end), m(income), m(expense), m(inn), m(out), m(initial))

    @Test
    fun normalCaseReconciles() {
        assertTrue(figures(start = 1000, income = 500, expense = 200, end = 1300).reconciles())
    }

    @Test
    fun openingBalanceInsideThePeriodReconciles() {
        assertTrue(figures(start = 0, initial = 800, income = 100, end = 900).reconciles())
    }

    @Test
    fun transferInEachDirectionReconciles() {
        assertTrue(figures(start = 1000, inn = 300, end = 1300).reconciles())
        assertTrue(figures(start = 1000, out = 300, end = 700).reconciles())
    }

    @Test
    fun aWrongEndBalanceDoesNotReconcile() {
        assertFalse(figures(start = 1000, income = 500, end = 1400).reconciles())
    }

    @Test
    fun overflowIsNotReconciledAndDoesNotThrow() {
        assertFalse(figures(start = Long.MAX_VALUE, income = 1, end = 0).reconciles())
    }
}
