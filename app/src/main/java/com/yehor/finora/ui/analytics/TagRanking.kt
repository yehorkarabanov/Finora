package com.yehor.finora.ui.analytics

import com.yehor.finora.domain.analytics.Cell
import com.yehor.finora.domain.tag.Tag

/** One row of the Tags tab ranking. [fraction] is the bar length, 0..1 of the largest amount. */
data class TagRankRow(val tag: Tag, val cell: Cell, val fraction: Float)

/**
 * The tags of the period, largest amount first, then by name. Tags with nothing in [cells] are
 * dropped; tags whose amount cannot be shown (no rate, overflow) keep their row but come last.
 */
fun rankTags(tags: List<Tag>, cells: Map<Long?, Cell>): List<TagRankRow> {
    val present = tags.mapNotNull { tag ->
        cells[tag.id]?.takeIf { it.hasAmount || it.partial || it.overflowed }?.let { tag to it }
    }
    val largest = present.mapNotNull { it.second.amount() }.maxOrNull()
    return present
        .sortedWith(
            compareBy<Pair<Tag, Cell>> { it.second.amount() == null }
                .thenByDescending { it.second.amount() ?: 0L }
                .thenBy { it.first.name.lowercase() },
        )
        .map { (tag, cell) -> TagRankRow(tag, cell, fraction(cell.amount(), largest)) }
}

private fun Cell.amount(): Long? = if (overflowed) null else estimate?.amountMinor

private fun fraction(amount: Long?, largest: Long?): Float {
    if (amount == null || largest == null || largest <= 0L) return 0f
    return (amount.toDouble() / largest).toFloat().coerceIn(0f, 1f)
}
