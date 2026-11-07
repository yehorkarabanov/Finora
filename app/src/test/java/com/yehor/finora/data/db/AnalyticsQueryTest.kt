package com.yehor.finora.data.db

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteProgram
import com.yehor.finora.domain.analytics.Bucket
import com.yehor.finora.domain.analytics.Measure
import com.yehor.finora.domain.model.OperationType
import com.yehor.finora.domain.operation.TransactionFilter
import com.yehor.finora.domain.tag.TagMatchMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AnalyticsQueryTest {

    private class RecordingProgram : SupportSQLiteProgram {
        val bound = sortedMapOf<Int, Any?>()
        override fun bindNull(index: Int) { bound[index] = null }
        override fun bindLong(index: Int, value: Long) { bound[index] = value }
        override fun bindDouble(index: Int, value: Double) { bound[index] = value }
        override fun bindString(index: Int, value: String) { bound[index] = value }
        override fun bindBlob(index: Int, value: ByteArray) { bound[index] = value }
        override fun clearBindings() { bound.clear() }
        override fun close() {}
    }

    private fun SimpleSQLiteQuery.args(): List<Any?> {
        val program = RecordingProgram()
        bindTo(program)
        return program.bound.values.toList()
    }

    private fun SimpleSQLiteQuery.placeholders() = sql.count { it == '?' }

    private fun buckets(n: Int) = (0 until n).map {
        Bucket(it, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), 1000L * it, 1000L * it + 500)
    }

    private val filter = TransactionFilter(
        accountIds = setOf(3, 4),
        categoryIds = setOf(7),
        tagIds = setOf(1, 2),
        tagMode = TagMatchMode.ALL,
        from = 100,
        toExclusive = 200,
    )

    private fun allBuilders(f: TransactionFilter, m: Measure): List<SimpleSQLiteQuery> = listOf(
        AnalyticsQuery.byCategory(f, m, setOf(7, 8)),
        AnalyticsQuery.byAccount(f, m, setOf(7, 8)),
        AnalyticsQuery.byTag(f, m, setOf(7, 8)),
        AnalyticsQuery.byBucket(f, m, setOf(7, 8), buckets(3)),
    )

    @Test
    fun measurePredicates() {
        val expense = AnalyticsQuery.byCategory(TransactionFilter(), Measure.EXPENSE, emptySet()).sql
        assertTrue(expense.contains("o.type = 'EXPENSE'"))
        val income = AnalyticsQuery.byCategory(TransactionFilter(), Measure.INCOME, emptySet()).sql
        assertTrue(income.contains("o.type = 'INCOME'"))
        val transfer = AnalyticsQuery.byCategory(TransactionFilter(), Measure.TRANSFER, emptySet()).sql
        assertTrue(transfer.contains("o.type = 'TRANSFER' AND e.amount_minor < 0"))
    }

    @Test
    fun everyBuilderAggregatesTheAbsoluteAmount() {
        allBuilders(TransactionFilter(), Measure.EXPENSE).forEach {
            assertTrue(it.sql, it.sql.contains("SUM(ABS(e.amount_minor))"))
        }
    }

    @Test
    fun openingBalanceIsExcludedFromFlowMeasures() {
        val sql = AnalyticsQuery.byCategory(TransactionFilter(), Measure.INCOME, emptySet()).sql
        assertTrue(sql.contains("o.type != 'OPENING_BALANCE'"))
        assertTrue(sql.contains("o.type = 'INCOME'"))
    }

    @Test
    fun filterTypesStillApplyWhenTheyContradictTheMeasure() {
        val sql = AnalyticsQuery.byCategory(TransactionFilter(types = setOf(OperationType.EXPENSE)), Measure.INCOME, emptySet()).sql
        assertTrue(sql.contains("o.type IN (?)"))
        assertTrue(sql.contains("o.type = 'INCOME'"))
    }

    @Test
    fun accountFilterRestrictsTheAggregatedEntryToo() {
        val sql = AnalyticsQuery.byCategory(filter, Measure.TRANSFER, emptySet()).sql
        assertTrue(sql.contains("e.account_id IN (?,?)"))
        val none = AnalyticsQuery.byCategory(TransactionFilter(), Measure.TRANSFER, emptySet()).sql
        assertFalse(none.contains("e.account_id IN"))
    }

    @Test
    fun groupingKeys() {
        val category = AnalyticsQuery.byCategory(TransactionFilter(), Measure.EXPENSE, emptySet()).sql
        assertTrue(category.contains("GROUP BY o.category_id, e.currency_code"))
        val account = AnalyticsQuery.byAccount(TransactionFilter(), Measure.EXPENSE, emptySet()).sql
        assertTrue(account.contains("GROUP BY e.account_id, e.currency_code"))
        val tag = AnalyticsQuery.byTag(TransactionFilter(), Measure.EXPENSE, emptySet()).sql
        assertTrue(tag.contains("LEFT JOIN operation_tag ot ON ot.operation_id = o.id"))
        assertTrue(tag.contains("GROUP BY ot.tag_id, e.currency_code"))
    }

    @Test
    fun argumentCountEqualsPlaceholderCountForEveryBuilder() {
        for (measure in Measure.entries) {
            allBuilders(filter, measure).forEach {
                assertEquals(it.sql, it.placeholders(), it.args().size)
            }
            allBuilders(TransactionFilter(), measure).forEach {
                assertEquals(it.sql, it.placeholders(), it.args().size)
            }
        }
        val others = listOf(
            AnalyticsQuery.accountFigures(1, 10, 20),
            AnalyticsQuery.accountsOverview(10, 20),
            AnalyticsQuery.balanceBefore(1, 10),
            AnalyticsQuery.balanceDeltas(1, buckets(4)),
        )
        others.forEach { assertEquals(it.sql, it.placeholders(), it.args().size) }
    }

    @Test
    fun noUserValueAppearsInTheSqlText() {
        val hostile = TransactionFilter(
            text = "x' OR 1=1 --",
            accountIds = setOf(987654321),
            categoryIds = setOf(555555),
            tagIds = setOf(424242),
            from = 1122334455,
            toExclusive = 9988776655,
        )
        for (measure in Measure.entries) {
            allBuilders(hostile, measure).forEach { q ->
                listOf("987654321", "555555", "424242", "1122334455", "9988776655", "OR 1=1").forEach {
                    assertFalse("${q.sql} leaks $it", q.sql.contains(it))
                }
            }
        }
    }

    @Test
    fun bucketQueryReplacesTheFilterDateRange() {
        val q = AnalyticsQuery.byBucket(filter, Measure.EXPENSE, emptySet(), buckets(2))
        assertTrue(q.sql.startsWith("WITH b(idx, lo, hi) AS (VALUES (?,?,?),(?,?,?))"))
        assertTrue(q.sql.contains("JOIN operation o ON o.occurred_at >= b.lo AND o.occurred_at < b.hi"))
        assertFalse(q.sql.contains("o.occurred_at >= ?"))
        // The first six bound values are the buckets: index, lo, hi.
        assertEquals(listOf<Any?>(0L, 0L, 500L, 1L, 1000L, 1500L), q.args().take(6))
        assertFalse(q.args().contains(100L))
        assertFalse(q.args().contains(200L))
    }

    @Test
    fun bucketCountIsBounded() {
        AnalyticsQuery.byBucket(TransactionFilter(), Measure.EXPENSE, emptySet(), buckets(100))
        var thrown = false
        try {
            AnalyticsQuery.byBucket(TransactionFilter(), Measure.EXPENSE, emptySet(), buckets(101))
        } catch (e: IllegalArgumentException) {
            thrown = true
        }
        assertTrue(thrown)
        thrown = false
        try {
            AnalyticsQuery.byBucket(TransactionFilter(), Measure.EXPENSE, emptySet(), emptyList())
        } catch (e: IllegalArgumentException) {
            thrown = true
        }
        assertTrue(thrown)
    }

    @Test
    fun accountFiguresShape() {
        val q = AnalyticsQuery.accountFigures(5, 100, 200)
        assertTrue(q.sql.contains("WHERE e.account_id = ? AND e.occurred_at >= ? AND e.occurred_at < ?"))
        assertTrue(q.sql.contains("GROUP BY o.type, 2"))
        assertEquals(listOf<Any?>(5L, 100L, 200L), q.args())
    }

    @Test
    fun balanceQueriesCountEveryEntryType() {
        val before = AnalyticsQuery.balanceBefore(5, 100)
        assertTrue(before.sql.contains("COALESCE(SUM(amount_minor), 0)"))
        assertFalse(before.sql.contains("operation"))
        assertEquals(listOf<Any?>(5L, 100L), before.args())
        val deltas = AnalyticsQuery.balanceDeltas(5, buckets(2))
        assertFalse(deltas.sql.contains("o.type"))
        assertEquals(5L, deltas.args().last())
    }

    @Test
    fun categoryClosureIsBoundNotInlined() {
        val q = AnalyticsQuery.byCategory(TransactionFilter(categoryIds = setOf(7)), Measure.EXPENSE, setOf(7, 8, 9))
        assertTrue(q.sql.contains("o.category_id IN (?,?,?)"))
        assertTrue(q.args().containsAll(listOf(7L, 8L, 9L)))
    }
}
