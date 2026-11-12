package com.yehor.finora.ui.tags

import com.yehor.finora.domain.tag.Tag
import com.yehor.finora.domain.tag.TagWithUsage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TagsLogicTest {
    private fun t(id: Long, name: String, usage: Int) =
        TagWithUsage(Tag(id, name, null, 0L), usage)

    private val tags = listOf(t(1, "Work", 2), t(2, "apple", 5), t(3, "Zoo", 5), t(4, "Home trip", 0))

    @Test fun blankQueryKeepsAll() {
        assertEquals(tags, TagsListLogic.filter(tags, "   "))
    }

    @Test fun filterIsCaseInsensitiveSubstring() {
        assertEquals(listOf(1L, 3L, 4L), TagsListLogic.filter(tags, " O ").map { it.tag.id })
        assertEquals(listOf(4L), TagsListLogic.filter(tags, "HOME  trip").map { it.tag.id })
        assertTrue(TagsListLogic.filter(tags, "xyz").isEmpty())
    }

    @Test fun sortMostUsedTiesByName() {
        assertEquals(listOf(2L, 3L, 1L, 4L), TagsListLogic.sort(tags, TagSortOrder.MOST_USED).map { it.tag.id })
    }

    @Test fun sortByNameIgnoresCase() {
        assertEquals(listOf(2L, 4L, 1L, 3L), TagsListLogic.sort(tags, TagSortOrder.NAME).map { it.tag.id })
    }

    @Test fun visibleCombines() {
        assertEquals(listOf(4L, 1L, 3L), TagsListLogic.visible(tags, "o", TagSortOrder.NAME).map { it.tag.id })
        assertEquals(listOf(1L), TagsListLogic.visible(tags, "wor", TagSortOrder.MOST_USED).map { it.tag.id })
    }

    @Test fun searchThreshold() {
        assertFalse(TagsListLogic.showSearch(8))
        assertTrue(TagsListLogic.showSearch(9))
    }

    @Test fun formActions() {
        assertEquals(TagFormAction.ENTER_NAME, TagFormLogic.action("  ", false))
        assertEquals(TagFormAction.NAME_TOO_LONG, TagFormLogic.action("a".repeat(31), true))
        assertEquals(TagFormAction.CREATE, TagFormLogic.action("Food", false))
        assertEquals(TagFormAction.SAVE, TagFormLogic.action("Food", true))
        assertFalse(TagFormLogic.isEnabled(TagFormAction.ENTER_NAME))
        assertFalse(TagFormLogic.isEnabled(TagFormAction.NAME_TOO_LONG))
        assertTrue(TagFormLogic.isEnabled(TagFormAction.SAVE))
    }
}
