package com.yehor.finora.ui.common

import com.yehor.finora.domain.category.Category
import com.yehor.finora.domain.category.CategoryRules
import com.yehor.finora.domain.category.CategoryTree
import com.yehor.finora.domain.category.DefaultCategories
import com.yehor.finora.domain.model.CategoryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryPickerModelTest {

    private fun cat(id: Long, name: String, parent: Long? = null, type: CategoryType = CategoryType.EXPENSE) =
        Category(id, name, null, null, type, parent, 0)

    // Food(1) > Restaurants(2) > Coffee(3); Food > Groceries(4); Housing(5); income Salary(10)
    private val tree = CategoryTree(
        listOf(
            cat(1, "Food"), cat(2, "Restaurants", 1), cat(3, "Coffee", 2), cat(4, "Groceries", 1),
            cat(5, "Housing"), cat(10, "Salary", type = CategoryType.INCOME),
        ),
    )

    private fun rows(mode: PickerMode, expanded: Set<Long> = emptySet(), query: String = "") =
        CategoryPickerModel.rows(tree, CategoryType.EXPENSE, mode, expanded, query)

    @Test
    fun collapsedShowsOnlyRootsOfTheType() {
        val r = rows(PickerMode.Assign())
        assertEquals(listOf("Food", "Housing"), r.map { it.category.name })
        assertTrue(r[0].hasChildren)
        assertFalse(r[1].hasChildren)
    }

    @Test
    fun expandingShowsChildrenWithLevels() {
        val r = rows(PickerMode.Assign(), expanded = setOf(1L, 2L))
        assertEquals(listOf("Food", "Restaurants", "Coffee", "Groceries", "Housing"), r.map { it.category.name })
        assertEquals(listOf(0, 1, 2, 1, 0), r.map { it.level })
        assertTrue(r.first().expanded)
    }

    @Test
    fun assignModeAllowsParentsWithChildren() {
        val r = rows(PickerMode.Assign(), expanded = setOf(1L))
        assertTrue(r.first { it.category.name == "Food" }.selectable)
    }

    @Test
    fun assignModeExcludedIsNotSelectableButItsChildrenAre() {
        val r = rows(PickerMode.Assign(excludedId = 1), expanded = setOf(1L))
        assertFalse(r.first { it.category.name == "Food" }.selectable)
        assertTrue(r.first { it.category.name == "Groceries" }.selectable)
    }

    @Test
    fun searchIgnoresCaseAndIsFlatWithPath() {
        val r = rows(PickerMode.Assign(), query = " cOFFee ")
        assertEquals(1, r.size)
        assertEquals(0, r.single().level)
        assertEquals("Food › Restaurants › Coffee", r.single().path)
    }

    @Test
    fun searchWithNoMatchIsEmpty() {
        assertTrue(rows(PickerMode.Assign(), query = "zzz").isEmpty())
    }

    @Test
    fun parentModeHidesInvalidParentsForANewCategory() {
        // Coffee is level 3 and cannot have children, so it is not offered.
        val r = rows(PickerMode.Parent(movingId = null), expanded = setOf(1L, 2L))
        assertEquals(listOf("Food", "Restaurants", "Groceries", "Housing"), r.map { it.category.name })
        assertTrue(r.all { it.selectable })
        val restaurants = r.first { it.category.name == "Restaurants" }
        assertFalse("hidden Coffee must not count as a child", restaurants.hasChildren)
    }

    @Test
    fun parentModeExcludesMovedCategoryAndItsDescendants() {
        val r = rows(PickerMode.Parent(movingId = 2), expanded = setOf(1L))
        // Groceries is hidden too: Restaurants (height 2) under a level-2 category would reach level 4.
        assertEquals(listOf("Food", "Housing"), r.map { it.category.name })
    }

    @Test
    fun parentModeOffersOnlyWhatCheckParentAccepts() {
        val offered = rows(PickerMode.Parent(movingId = 5), expanded = setOf(1L, 2L)).map { it.category.id }.toSet()
        assertEquals(CategoryRules.validParents(tree, 5, CategoryType.EXPENSE), offered)
    }

    @Test
    fun otherTypesAreNeverListed() {
        val r = CategoryPickerModel.rows(tree, CategoryType.INCOME, PickerMode.Assign(), emptySet(), "")
        assertEquals(listOf("Salary"), r.map { it.category.name })
    }

    @Test
    fun everyDefaultIconKeyExistsInTheCatalog() {
        DefaultCategories.iconKeys.forEach { assertTrue(it, it in IconKeys.all) }
    }

    @Test
    fun catalogHasAVectorForEveryKey() {
        assertEquals(IconKeys.all, IconCatalog.keys)
        IconKeys.all.forEach { IconCatalog.get(it) }
        IconCatalog.get("nope")
        IconCatalog.get(null)
    }

    @Test
    fun unknownIconAndNullColorFallBackWithoutThrowing() {
        assertEquals(IconKeys.DEFAULT, IconKeys.resolve("no-such-icon"))
        assertEquals(IconKeys.DEFAULT, IconKeys.resolve(null))
        assertEquals("food", IconKeys.resolve("food"))
        assertEquals(ColorPalette.ACCOUNT_DEFAULT, ColorPalette.resolve(null, ColorPalette.ACCOUNT_DEFAULT))
        assertEquals(0xFF123456L, ColorPalette.resolve(0xFF123456L, ColorPalette.ACCOUNT_DEFAULT))
        assertEquals(16, ColorPalette.colors.size)
        assertEquals(16, ColorPalette.colors.toSet().size)
    }
}
