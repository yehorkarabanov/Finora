package com.yehor.finora.domain.category

import com.yehor.finora.ui.common.IconKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultCategoriesTest {

    private fun DefaultCategory.walk(): List<DefaultCategory> = listOf(this) + children.flatMap { it.walk() }

    private fun depth(c: DefaultCategory): Int = 1 + (c.children.maxOfOrNull { depth(it) } ?: 0)

    @Test
    fun allThreeTypesArePresent() {
        assertTrue(DefaultCategories.expense.isNotEmpty())
        assertTrue(DefaultCategories.income.isNotEmpty())
        assertTrue(DefaultCategories.transfer.isNotEmpty())
        assertEquals(setOf("EXPENSE", "INCOME", "TRANSFER"), DefaultCategories.all.map { it.first.name }.toSet())
    }

    @Test
    fun matchesTheSpecTable() {
        assertEquals(
            listOf(
                "Food", "Housing", "Transport", "Shopping", "Entertainment",
                "Health", "Subscriptions", "Fees & Charges", "Other",
            ),
            DefaultCategories.expense.map { it.name },
        )
        assertEquals(
            listOf("Groceries", "Restaurants & Cafes"),
            DefaultCategories.expense.first().children.map { it.name },
        )
        assertEquals(
            listOf("Salary", "Freelance", "Investment Income", "Gifts", "Refunds", "Other"),
            DefaultCategories.income.map { it.name },
        )
        assertEquals(
            listOf("Own Account Transfer", "Currency Exchange", "Savings Transfer", "Investment Deposit", "Other"),
            DefaultCategories.transfer.map { it.name },
        )
    }

    @Test
    fun depthIsAtMostTwo() {
        DefaultCategories.all.forEach { (_, c) -> assertTrue(c.name, depth(c) <= 2) }
        assertTrue(2 <= CategoryRules.MAX_DEPTH)
    }

    @Test
    fun noBlankOrOverlongNames() {
        DefaultCategories.all.flatMap { it.second.walk() }.forEach {
            assertTrue(it.name, it.name.isNotBlank())
            assertEquals(it.name, it.name.trim())
            assertTrue(it.name, it.name.length <= 50)
            assertTrue(it.icon, it.icon.isNotBlank())
        }
    }

    @Test
    fun siblingNamesAreUniqueWithinAType() {
        listOf(DefaultCategories.expense, DefaultCategories.income, DefaultCategories.transfer).forEach { roots ->
            assertEquals(roots.map { it.name }.toSet().size, roots.size)
            roots.forEach { r -> assertEquals(r.children.map { it.name }.toSet().size, r.children.size) }
        }
    }

    @Test
    fun totalCountMatchesTheTree() {
        assertEquals(
            DefaultCategories.all.sumOf { it.second.walk().size },
            DefaultCategories.totalCount,
        )
    }

    @Test
    fun everyDefaultIconKeyExistsInTheCatalog() {
        DefaultCategories.all.flatMap { it.second.walk() }.forEach {
            assertTrue("${it.name}: ${it.icon}", it.icon in IconKeys.category)
        }
    }

    @Test
    fun feesAndChargesExistForTransferFees() {
        assertTrue(DefaultCategories.expense.any { it.name == "Fees & Charges" })
    }
}
