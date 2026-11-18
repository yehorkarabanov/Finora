package com.yehor.finora.ui.tags

import com.yehor.finora.domain.tag.TagNameError
import com.yehor.finora.domain.tag.TagNameRules
import com.yehor.finora.domain.tag.TagWithUsage

enum class TagSortOrder { MOST_USED, NAME }

object TagsListLogic {
    /** The search field is shown only for longer lists. */
    const val SEARCH_THRESHOLD = 8

    fun showSearch(totalCount: Int): Boolean = totalCount > SEARCH_THRESHOLD

    fun filter(tags: List<TagWithUsage>, query: String): List<TagWithUsage> {
        val key = TagNameRules.key(query)
        if (key.isEmpty()) return tags
        return tags.filter { TagNameRules.key(it.tag.name).contains(key) }
    }

    fun sort(tags: List<TagWithUsage>, order: TagSortOrder): List<TagWithUsage> = when (order) {
        TagSortOrder.MOST_USED -> tags.sortedWith(
            compareByDescending<TagWithUsage> { it.usageCount }.thenBy { TagNameRules.key(it.tag.name) },
        )
        TagSortOrder.NAME -> tags.sortedBy { TagNameRules.key(it.tag.name) }
    }

    fun visible(tags: List<TagWithUsage>, query: String, order: TagSortOrder): List<TagWithUsage> =
        sort(filter(tags, query), order)
}

/** What the primary button of the tag sheet should do and say. */
enum class TagFormAction { CREATE, SAVE, ENTER_NAME, NAME_TOO_LONG }

object TagFormLogic {
    fun action(name: String, isEdit: Boolean): TagFormAction = when (TagNameRules.validate(name)) {
        TagNameError.EMPTY -> TagFormAction.ENTER_NAME
        TagNameError.TOO_LONG -> TagFormAction.NAME_TOO_LONG
        null -> if (isEdit) TagFormAction.SAVE else TagFormAction.CREATE
    }

    fun isEnabled(action: TagFormAction): Boolean =
        action == TagFormAction.CREATE || action == TagFormAction.SAVE
}
