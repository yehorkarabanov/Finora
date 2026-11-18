package com.yehor.finora.domain.operation

import com.yehor.finora.domain.model.OperationType
import com.yehor.finora.domain.tag.TagMatchMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterCodecTest {

    private val full = TransactionFilter(
        types = setOf(OperationType.EXPENSE, OperationType.TRANSFER),
        accountIds = setOf(1, 2),
        categoryIds = setOf(7, 8),
        exactCategoryIds = setOf(7),
        categoryExact = true,
        includeUncategorized = true,
        tagIds = setOf(4, 5),
        tagMode = TagMatchMode.ALL,
        from = 1_730_419_200_000,
        toExclusive = 1_733_011_200_000,
    )

    @Test
    fun roundTripOfEveryField() {
        assertEquals(full, FilterCodec.decode(FilterCodec.encode(full)))
    }

    @Test
    fun defaultFilterEncodesToEmptyAndBack() {
        assertEquals("", FilterCodec.encode(TransactionFilter()))
        assertEquals(TransactionFilter(), FilterCodec.decode(""))
    }

    @Test
    fun documentedFormat() {
        val filter = TransactionFilter(
            types = setOf(OperationType.EXPENSE),
            accountIds = setOf(1, 2),
            categoryIds = setOf(7),
            categoryExact = true,
            tagIds = setOf(4, 5),
            tagMode = TagMatchMode.ALL,
            from = 1_730_419_200_000,
            toExclusive = 1_733_011_200_000,
        )
        assertEquals("t=EXPENSE;a=1,2;c=7;x=1;g=4,5;m=ALL;f=1730419200000;e=1733011200000", FilterCodec.encode(filter))
    }

    @Test
    fun exactCategoryIdsFormat() {
        val filter = TransactionFilter(
            categoryIds = setOf(1, 2),
            exactCategoryIds = setOf(1),
        )
        val encoded = FilterCodec.encode(filter)
        assertEquals("c=1,2;xe=1", encoded)
        val decoded = FilterCodec.decode(encoded)
        assertEquals(filter, decoded)
    }

    @Test
    fun unknownKeysAreIgnored() {
        val decoded = FilterCodec.decode("zz=1;a=3;foo=bar")
        assertEquals(TransactionFilter(accountIds = setOf(3)), decoded)
    }

    @Test
    fun badValuesAreIgnored() {
        val decoded = FilterCodec.decode("t=NOPE,EXPENSE;a=x,2,;f=abc;e=;c=9999999999999999999999")
        assertEquals(setOf(OperationType.EXPENSE), decoded.types)
        assertEquals(setOf(2L), decoded.accountIds)
        assertEquals(null, decoded.from)
        assertEquals(null, decoded.toExclusive)
        assertTrue(decoded.categoryIds.isEmpty())
    }

    @Test
    fun openingBalanceCannotBeRequested() {
        assertTrue(FilterCodec.decode("t=OPENING_BALANCE").types.isEmpty())
    }

    @Test
    fun garbageNeverThrows() {
        listOf(null, "", ";", "=", ";;;", "a", "a=", "=1", "a=1=2", "\u0000", "t=;a=;c=;g=", "a=1,,,2;;x", "%3B%3D")
            .forEach { FilterCodec.decode(it) }
        val random = java.util.Random(42)
        repeat(500) {
            val text = String(CharArray(random.nextInt(40)) { "ta;=,c1x9g-m ALL".random(random) })
            FilterCodec.decode(text)
        }
    }

    @Test
    fun separatorsInValuesCannotInjectKeys() {
        // A value can only hold digits and enum names; anything else is dropped, so no key can be smuggled in.
        val decoded = FilterCodec.decode("a=1;c=2,x=1")
        assertEquals(setOf(1L), decoded.accountIds)
        assertEquals(setOf(2L), decoded.categoryIds) // "x=1" is not a number and is dropped
        assertEquals(false, decoded.categoryExact)
    }

    @Test
    fun textAndAmountAreNotEncoded() {
        val encoded = FilterCodec.encode(TransactionFilter(text = "a;b=c", types = setOf(OperationType.INCOME)))
        assertEquals("t=INCOME", encoded)
    }

    private fun String.random(random: java.util.Random): Char = this[random.nextInt(length)]
}
