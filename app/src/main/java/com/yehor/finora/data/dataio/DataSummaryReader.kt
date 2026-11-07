package com.yehor.finora.data.dataio

import androidx.sqlite.db.SupportSQLiteDatabase
import com.yehor.finora.domain.dataio.DataSummary

object DataSummaryReader {

    fun read(db: SupportSQLiteDatabase, databaseBytes: Long, schemaVersion: Int): DataSummary {
        val accounts = queryLong(db, "SELECT COUNT(*) FROM account").toInt()
        val activeAccounts = queryLong(db, "SELECT COUNT(*) FROM account WHERE status = 'ACTIVE'").toInt()
        val categories = queryLong(db, "SELECT COUNT(*) FROM category").toInt()
        val tags = queryLong(db, "SELECT COUNT(*) FROM tag").toInt()
        val transactions = queryLong(db, "SELECT COUNT(*) FROM operation WHERE type IN ('INCOME', 'EXPENSE')").toInt()
        val transfers = queryLong(db, "SELECT COUNT(*) FROM operation WHERE type = 'TRANSFER'").toInt()
        val schedules = queryLong(db, "SELECT COUNT(*) FROM recurring_schedule").toInt()
        val firstOp = queryLongOrNull(db, "SELECT MIN(occurred_at) FROM operation WHERE type <> 'OPENING_BALANCE'")
        val lastOp = queryLongOrNull(db, "SELECT MAX(occurred_at) FROM operation WHERE type <> 'OPENING_BALANCE'")

        return DataSummary(
            accounts = accounts,
            activeAccounts = activeAccounts,
            categories = categories,
            tags = tags,
            transactions = transactions,
            transfers = transfers,
            schedules = schedules,
            firstOperationAt = firstOp,
            lastOperationAt = lastOp,
            databaseBytes = databaseBytes,
            schemaVersion = schemaVersion,
        )
    }

    private fun queryLong(db: SupportSQLiteDatabase, sql: String): Long {
        return db.query(sql).use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else 0L
        }
    }

    private fun queryLongOrNull(db: SupportSQLiteDatabase, sql: String): Long? {
        return db.query(sql).use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null
        }
    }
}
