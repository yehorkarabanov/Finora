package com.yehor.finora.ui.common

import com.yehor.finora.domain.category.DefaultCategories
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IconKeysTest {

    @Test
    fun catalogCoversExactlyTheKeys() {
        assertEquals(IconKeys.all, IconCatalog.keys)
    }

    @Test
    fun noDuplicatesWithinAList() {
        assertEquals(IconKeys.account.toSet().size, IconKeys.account.size)
        assertEquals(IconKeys.category.toSet().size, IconKeys.category.size)
    }

    @Test
    fun groupsAreNonEmptyAndHaveUniqueIds() {
        listOf(IconKeys.accountGroups, IconKeys.categoryGroups).forEach { groups ->
            assertTrue(groups.isNotEmpty())
            groups.forEach { assertTrue(it.id, it.keys.isNotEmpty()) }
            assertEquals(groups.size, groups.map { it.id }.toSet().size)
        }
    }

    @Test
    fun flattenedListsFollowTheGroups() {
        assertEquals(IconKeys.accountGroups.flatMap { it.keys }, IconKeys.account)
        assertEquals(IconKeys.categoryGroups.flatMap { it.keys }, IconKeys.category)
    }

    @Test
    fun defaultCategoryIconsAreOffered() {
        DefaultCategories.iconKeys.forEach { assertTrue(it, it in IconKeys.category) }
    }

    @Test
    fun unknownAndNullFallBackToOther() {
        assertEquals(IconKeys.DEFAULT, IconKeys.resolve("nope"))
        assertEquals(IconKeys.DEFAULT, IconKeys.resolve(null))
        assertEquals(IconCatalog.label("other"), IconCatalog.label("nope"))
    }

    @Test
    fun keysAreSnakeCase() {
        IconKeys.all.forEach { assertTrue(it, Regex("[a-z]+(_[a-z]+)*").matches(it)) }
    }
}
