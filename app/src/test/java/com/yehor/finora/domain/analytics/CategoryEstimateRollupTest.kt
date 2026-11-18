package com.yehor.finora.domain.analytics

import com.yehor.finora.domain.category.Category
import com.yehor.finora.domain.category.CategoryDirectTotal
import com.yehor.finora.domain.category.CategoryRollup
import com.yehor.finora.domain.category.CategoryTree
import com.yehor.finora.domain.model.CategoryType
import com.yehor.finora.domain.money.CurrencyRegistry
import com.yehor.finora.domain.money.Money
import com.yehor.finora.domain.rate.CurrencyPair
import com.yehor.finora.domain.rate.RateQuote
import com.yehor.finora.domain.rate.RateTable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class CategoryEstimateRollupTest {

    private val pln = CurrencyRegistry.get("PLN")
    private val usd = CurrencyRegistry.get("USD")
    private val eur = CurrencyRegistry.get("EUR")
    private val now = 1_000_000_000_000L

    private fun cat(id: Long, name: String, parent: Long? = null) =
        Category(id, name, null, null, CategoryType.EXPENSE, parent, 0)

    private fun table(vararg pairs: Pair<CurrencyPair, String>) =
        RateTable(pairs.associate { (pair, rate) -> pair to RateQuote(BigDecimal(rate), LocalDate.of(2026, 11, 6), now) })

    private fun converter(rates: RateTable = RateTable.EMPTY) =
        ScopeConverter(CurrencyScope.Display(pln), rates, now)

    private fun plnCell(minor: Long, count: Int = 1) =
        converter().cell(listOf(NativeAmount(Money(minor, pln), count)))

    // Food 1,250 = Groceries 700 + Restaurants 450 + direct 100 (PRD 6.5)
    private val tree = CategoryTree(
        listOf(cat(1, "Food"), cat(2, "Groceries", 1), cat(3, "Restaurants", 1), cat(4, "Fresh", 2), cat(5, "Transport")),
    )

    @Test
    fun prdExampleParentEqualsChildrenPlusDirect() {
        val direct = mapOf<Long?, Cell>(1L to plnCell(10_000), 2L to plnCell(30_000), 3L to plnCell(45_000), 4L to plnCell(40_000))
        val rolled = CategoryEstimateRollup.rollUp(tree, direct)
        assertEquals(125_000L, rolled.getValue(1).estimate!!.amountMinor)
        assertEquals(70_000L, rolled.getValue(2).estimate!!.amountMinor)
        assertEquals(45_000L, rolled.getValue(3).estimate!!.amountMinor)
        assertEquals(4, rolled.getValue(1).operationCount)
    }

    @Test
    fun threeLevelTreePropagatesToTheRoot() {
        val direct = mapOf<Long?, Cell>(4L to plnCell(500))
        val rolled = CategoryEstimateRollup.rollUp(tree, direct)
        assertEquals(500L, rolled.getValue(2).estimate!!.amountMinor)
        assertEquals(500L, rolled.getValue(1).estimate!!.amountMinor)
    }

    @Test
    fun uncategorizedStaysSeparate() {
        val direct = mapOf<Long?, Cell>(null to plnCell(300), 5L to plnCell(100))
        val rolled = CategoryEstimateRollup.rollUp(tree, direct)
        assertEquals(300L, rolled.getValue(null).estimate!!.amountMinor)
        assertEquals(100L, rolled.getValue(5).estimate!!.amountMinor)
    }

    @Test
    fun unknownCategoryKeepsItsOwnBucket() {
        val direct = mapOf<Long?, Cell>(99L to plnCell(50))
        val rolled = CategoryEstimateRollup.rollUp(tree, direct)
        assertEquals(50L, rolled.getValue(99).estimate!!.amountMinor)
    }

    @Test
    fun partialPropagatesToAncestors() {
        val partial = converter().cell(listOf(NativeAmount(Money(100, eur), 1), NativeAmount(Money(200, pln), 1)))
        val rolled = CategoryEstimateRollup.rollUp(tree, mapOf(4L to partial))
        assertEquals(setOf(eur), rolled.getValue(2).excluded)
        assertEquals(setOf(eur), rolled.getValue(1).excluded)
        assertTrue(rolled.getValue(1).partial)
        assertEquals(200L, rolled.getValue(1).estimate!!.amountMinor)
    }

    @Test
    fun overflowPropagatesToAncestors() {
        val big = converter().cell(listOf(NativeAmount(Money(Long.MAX_VALUE, pln), 1)))
        val one = plnCell(1)
        val rolled = CategoryEstimateRollup.rollUp(tree, mapOf(2L to big, 3L to one, 4L to one))
        assertTrue(rolled.getValue(2).overflowed)
        assertTrue(rolled.getValue(1).overflowed)
        assertNull(rolled.getValue(1).estimate)
    }

    @Test
    fun rollUpOfTwoCellsThatOverflowWhenAddedIsFlagged() {
        val a = plnCell(Long.MAX_VALUE)
        val b = plnCell(1)
        val sum = a + b
        assertTrue(sum.overflowed)
        assertNull(sum.estimate)
    }

    @Test
    fun additivityHoldsEvenWhenConvertingTheTotalWouldDifferByOneUnit() {
        // 1 minor USD at 0.4 PLN rounds to 0 in each group, but 3 minor USD would round to 1.
        val rates = table(CurrencyPair(usd, pln) to "0.4")
        val usdCell = converter(rates).cell(listOf(NativeAmount(Money(1, usd), 1)))
        val rolled = CategoryEstimateRollup.rollUp(tree, mapOf(1L to usdCell, 3L to usdCell, 4L to usdCell))
        val shownParts = listOf(1L, 3L, 4L).sumOf { converter(rates).cell(listOf(NativeAmount(Money(1, usd), 1))).estimate!!.amountMinor }
        assertEquals(shownParts, rolled.getValue(1).estimate!!.amountMinor)
        assertEquals(0L, rolled.getValue(1).estimate!!.amountMinor)
    }

    @Test
    fun withOneCurrencyAndNoConversionItAgreesWithTheNativeRollup() {
        val rows = listOf(
            CategoryDirectTotal(1, "PLN", 10_000, 1),
            CategoryDirectTotal(2, "PLN", 30_000, 2),
            CategoryDirectTotal(4, "PLN", 40_000, 3),
            CategoryDirectTotal(null, "PLN", 700, 1),
        )
        val native = CategoryRollup.rollUp(tree, rows)
        val direct = rows.associate { it.categoryId to plnCell(it.totalMinor, it.operationCount) }
        val rolled = CategoryEstimateRollup.rollUp(tree, direct)
        native.totals.forEach { (id, byCurrency) ->
            // A category with no data has an empty map natively and no estimate here: both mean zero.
            assertEquals(byCurrency["PLN"] ?: 0L, rolled.getValue(id).estimate?.amountMinor ?: 0L)
            assertEquals(native.counts.getValue(id), rolled.getValue(id).operationCount)
        }
        assertFalse(rolled.values.any { it.estimate?.basis?.usesConversion == true })
    }
}
