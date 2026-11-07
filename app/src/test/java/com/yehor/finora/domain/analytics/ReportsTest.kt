package com.yehor.finora.domain.analytics

import com.yehor.finora.domain.category.Category
import com.yehor.finora.domain.category.CategoryTree
import com.yehor.finora.domain.model.CategoryType
import com.yehor.finora.domain.money.CurrencyRegistry
import com.yehor.finora.domain.money.Money
import com.yehor.finora.domain.rate.RateTable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class ReportsTest {

    private val pln = CurrencyRegistry.get("PLN")
    private val usd = CurrencyRegistry.get("USD")
    private val now = 1_000_000_000_000L
    private val converter = ScopeConverter(CurrencyScope.Display(pln), RateTable.EMPTY, now)

    private fun row(key: Long?, minor: Long, count: Int = 1, currency: com.yehor.finora.domain.money.Currency = pln) =
        NativeRow(key, Money(minor, currency), count)

    private fun cat(id: Long, name: String, parent: Long? = null) =
        Category(id, name, null, null, CategoryType.EXPENSE, parent, 0)

    private fun cell(minor: Long) = converter.cell(listOf(NativeAmount(Money(minor, pln), 1)))

    private fun drillRow(id: Long?, name: String?, minor: Long, direct: Boolean = false) = DrillRow(
        categoryId = id,
        category = name?.let { cat(id!!, it) },
        cell = cell(minor),
        isDirect = direct,
        hasChildren = false,
    )

    @Test
    fun cellsGroupsByKeyAndSumsPerCurrency() {
        val cells = Reports.cells(listOf(row(1, 100), row(1, 50, 2), row(2, 10), row(null, 5)), converter)
        assertEquals(150L, cells.getValue(1).estimate!!.amountMinor)
        assertEquals(3, cells.getValue(1).operationCount)
        assertEquals(10L, cells.getValue(2).estimate!!.amountMinor)
        assertEquals(5L, cells.getValue(null).estimate!!.amountMinor)
    }

    @Test
    fun totalEqualsTheSumOfTheGroups() {
        val cells = Reports.cells(listOf(row(1, 100), row(2, 40), row(null, 7)), converter)
        assertEquals(147L, Reports.total(cells).estimate!!.amountMinor)
    }

    @Test
    fun totalOfNothingHasNoEstimate() {
        assertNull(Reports.total(emptyMap()).estimate)
    }

    @Test
    fun nativeTotalsAreExactPerCurrencySortedByCode() {
        val totals = Reports.nativeTotals(listOf(row(1, 100, 1, usd), row(2, 50, 2, pln), row(3, 25, 1, usd)))
        assertEquals(listOf("PLN", "USD"), totals.map { it.currency.code })
        assertEquals(50L, totals[0].money!!.amountMinor)
        assertEquals(125L, totals[1].money!!.amountMinor)
        assertEquals(2, totals[1].operationCount)
    }

    @Test
    fun nativeTotalsReportAnOverflowAsNull() {
        val totals = Reports.nativeTotals(listOf(row(1, Long.MAX_VALUE), row(2, 1)))
        assertNull(totals.single().money)
    }

    @Test
    fun topSplitKeepsAFewRowsAndNoOther() {
        val rows = listOf(drillRow(1, "A", 30), drillRow(2, "B", 20))
        val split = Reports.topSplit(rows, limit = 5)
        assertEquals(2, split.top.size)
        assertNull(split.other)
        assertNull(split.uncategorized)
    }

    @Test
    fun topSplitFoldsTheTailIntoOtherAndKeepsUncategorizedApart() {
        val rows = listOf(
            drillRow(1, "A", 60), drillRow(2, "B", 50), drillRow(3, "C", 40),
            drillRow(4, "D", 30), drillRow(5, "E", 20), drillRow(6, "F", 10), drillRow(7, "G", 5),
            drillRow(null, null, 8),
        )
        val split = Reports.topSplit(rows, limit = 5)
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), split.top.map { it.categoryId })
        assertEquals(15L, split.other!!.estimate!!.amountMinor)
        assertEquals(8L, split.uncategorized!!.cell.estimate!!.amountMinor)
    }

    @Test
    fun compareMatchesRowsAndKeepsPreviousOnlyRows() {
        val current = listOf(drillRow(1, "Food", 100), drillRow(2, "Fun", 30))
        val previous = listOf(drillRow(1, "Food", 80), drillRow(3, "Gone", 50))
        val merged = Reports.compare(current, previous)
        assertEquals(3, merged.size)
        assertEquals(80L, merged[0].previous!!.estimate!!.amountMinor)
        assertNull(merged[1].previous)
        val goneRow = merged[2]
        assertEquals(3L, goneRow.row.categoryId)
        assertTrue(goneRow.row.cell.estimate == null)
        assertEquals(50L, goneRow.previous!!.estimate!!.amountMinor)
    }

    @Test
    fun compareDoesNotMixADirectRowWithItsCategory() {
        val current = listOf(drillRow(1, "Food", 100, direct = true))
        val previous = listOf(drillRow(1, "Food", 80, direct = false))
        val merged = Reports.compare(current, previous)
        assertNull(merged[0].previous)
        assertEquals(2, merged.size)
    }

    @Test
    fun trendHasOnePointPerBucketInOrder() {
        val buckets = BucketPlan.months(LocalDate.of(2026, 11, 15), 3, ZoneOffset.UTC)
        val points = Reports.trend(buckets, listOf(row(0, 10), row(2, 30)), converter)
        assertEquals(3, points.size)
        assertEquals(10L, points[0].cell.estimate!!.amountMinor)
        assertEquals(0L, points[1].cell.estimate!!.amountMinor)
        assertEquals(0, points[1].cell.operationCount)
        assertEquals(30L, points[2].cell.estimate!!.amountMinor)
    }

    @Test
    fun treeRowsStayConsistentWithTotal() {
        val tree = CategoryTree(listOf(cat(1, "Food"), cat(2, "Groceries", 1)))
        val direct = Reports.cells(listOf(row(1, 100), row(2, 300), row(null, 50)), converter)
        val rolled = CategoryEstimateRollup.rollUp(tree, direct)
        val top = CategoryDrill.rows(tree, direct, rolled, emptyList(), CategoryType.EXPENSE)
        assertEquals(450L, top.sumOf { it.cell.estimate!!.amountMinor })
        assertEquals(450L, Reports.total(direct).estimate!!.amountMinor)
    }
}
