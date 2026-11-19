package com.yehor.finora.ui.analytics

import com.yehor.finora.domain.analytics.Cell
import com.yehor.finora.domain.money.CurrencyRegistry
import com.yehor.finora.domain.money.Estimate
import com.yehor.finora.domain.money.EstimateBasis
import com.yehor.finora.domain.tag.Tag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TagRankingTest {

    private val pln = CurrencyRegistry.get("PLN")

    private fun tag(name: String, id: Long = 1L) = Tag(id = id, name = name, color = null, createdAt = 0L)

    private fun cell(minor: Long) = Cell(Estimate(minor, pln, EstimateBasis.NATIVE), 1, emptySet(), false, null)

    /** A group whose only currency has no rate: no estimate, but a warning. */
    private fun unconvertible() = Cell(null, 1, setOf(CurrencyRegistry.get("USD")), false, null)

    @Test fun sortsByAmountDesc() = assertEquals(
        listOf("trip", "work"),
        rankTags(listOf(tag("work", 1), tag("trip", 2)), mapOf(1L to cell(700), 2L to cell(1610))).map { it.tag.name },
    )

    @Test fun largestIsFullBar() = assertEquals(1f, rankTags(listOf(tag("a")), mapOf(1L to cell(5))).single().fraction)

    @Test fun emptyTagsDropped() = assertTrue(rankTags(listOf(tag("a")), mapOf(1L to Cell.NONE)).isEmpty())

    @Test fun unconvertibleLast() = assertEquals(
        "b",
        rankTags(listOf(tag("a", 1), tag("b", 2)), mapOf(1L to unconvertible(), 2L to cell(1))).first().tag.name,
    )

    @Test fun noTagsNoRows() = assertTrue(rankTags(emptyList(), emptyMap()).isEmpty())

    @Test fun fractionIsShareOfLargest() = assertEquals(
        listOf(1f, 0.5f),
        rankTags(listOf(tag("a", 1), tag("b", 2)), mapOf(1L to cell(400), 2L to cell(800))).map { it.fraction },
    )

    @Test fun equalAmountsByName() = assertEquals(
        listOf("alpha", "beta"),
        rankTags(listOf(tag("beta", 1), tag("alpha", 2)), mapOf(1L to cell(10), 2L to cell(10))).map { it.tag.name },
    )
}
