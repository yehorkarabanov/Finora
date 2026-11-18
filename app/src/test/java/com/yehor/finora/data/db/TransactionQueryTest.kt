package com.yehor.finora.data.db

import androidx.sqlite.db.SupportSQLiteProgram
import com.yehor.finora.domain.model.OperationType
import com.yehor.finora.domain.operation.AmountRange
import com.yehor.finora.domain.operation.TransactionFilter
import com.yehor.finora.domain.operation.TransactionSort
import com.yehor.finora.domain.tag.TagMatchMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionQueryTest {

    /** Records the arguments bound by a [androidx.sqlite.db.SimpleSQLiteQuery]. */
    private class RecordingProgram : SupportSQLiteProgram {
        val bound = mutableMapOf<Int, Any?>()
        override fun bindNull(index: Int) { bound[index] = null }
        override fun bindLong(index: Int, value: Long) { bound[index] = value }
        override fun bindDouble(index: Int, value: Double) { bound[index] = value }
        override fun bindString(index: Int, value: String) { bound[index] = value }
        override fun bindBlob(index: Int, value: ByteArray) { bound[index] = value }
        override fun clearBindings() { bound.clear() }
        override fun close() {}
    }

    private fun androidx.sqlite.db.SimpleSQLiteQuery.argAt(position: Int): Any? {
        val program = RecordingProgram()
        bindTo(program)
        return program.bound[position + 1]
    }

    @Test
    fun defaultFilterExcludesOpeningBalance() {
        val query = TransactionQuery.build(TransactionFilter(), TransactionSort.DATE_DESC, emptySet())
        assertTrue(query.sql.contains("o.type != 'OPENING_BALANCE'"))
        assertTrue(query.sql.contains("ORDER BY o.occurred_at DESC, o.id DESC"))
    }

    @Test
    fun tagFilterAnyVersusAll() {
        val anyFilter = TransactionFilter(tagIds = setOf(1, 2), tagMode = TagMatchMode.ANY)
        val anyQuery = TransactionQuery.build(anyFilter, TransactionSort.DATE_DESC, emptySet())
        assertTrue(anyQuery.sql.contains("EXISTS (SELECT 1 FROM operation_tag"))

        val allFilter = TransactionFilter(tagIds = setOf(1, 2), tagMode = TagMatchMode.ALL)
        val allQuery = TransactionQuery.build(allFilter, TransactionSort.DATE_DESC, emptySet())
        assertTrue(allQuery.sql.contains("COUNT(DISTINCT t.tag_id)"))
    }

    @Test
    fun textEscaping() {
        val filter = TransactionFilter(text = "100%_off")
        val query = TransactionQuery.build(filter, TransactionSort.DATE_DESC, emptySet())
        assertTrue(query.sql.contains("LIKE ? ESCAPE '\\'"))
        // The default type clause binds nothing, so the LIKE pattern is the first bound argument.
        val arg = query.argAt(0) as String
        assertEquals("%100\\%\\_off%", arg)
    }

    @Test
    fun amountSort() {
        val query = TransactionQuery.build(
            TransactionFilter(),
            TransactionSort.AMOUNT_DESC,
            emptySet(),
            scopeCurrency = "USD",
        )
        assertTrue(query.sql.contains("MAX(ABS(e.amount_minor))"))
        assertTrue(query.sql.contains("e.currency_code = ?"))
    }

    @Test
    fun amountSortWithoutScopeCurrency() {
        val descQuery = TransactionQuery.build(
            TransactionFilter(),
            TransactionSort.AMOUNT_DESC,
            emptySet(),
            scopeCurrency = null,
        )
        assertTrue(descQuery.sql.contains("ORDER BY (SELECT MAX(ABS(e.amount_minor)) FROM ledger_entry e WHERE e.operation_id = o.id) DESC"))

        val ascQuery = TransactionQuery.build(
            TransactionFilter(),
            TransactionSort.AMOUNT_ASC,
            emptySet(),
            scopeCurrency = null,
        )
        assertTrue(ascQuery.sql.contains("ORDER BY (SELECT MAX(ABS(e.amount_minor)) FROM ledger_entry e WHERE e.operation_id = o.id) ASC"))
    }

    @Test
    fun explicitTypesAreBound() {
        val query = TransactionQuery.build(
            TransactionFilter(types = setOf(OperationType.INCOME, OperationType.OPENING_BALANCE)),
            TransactionSort.DATE_ASC,
            emptySet(),
        )
        assertTrue(query.sql.contains("o.type IN (?)"))
        assertEquals("INCOME", query.argAt(0))
    }

    @Test
    fun amountFilterRequiresScopeCurrency() {
        val filter = TransactionFilter(
            amount = AmountRange(
                currency = com.yehor.finora.domain.money.CurrencyRegistry.get("USD"),
                minMinor = 100L,
            ),
        )
        val query = TransactionQuery.build(filter, TransactionSort.DATE_DESC, emptySet())
        assertTrue(query.sql.contains("ABS(e.amount_minor) >= ?"))
    }
}
