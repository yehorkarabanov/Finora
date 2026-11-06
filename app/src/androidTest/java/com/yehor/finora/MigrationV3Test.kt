package com.yehor.finora

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.yehor.finora.data.db.FinoraDatabase
import com.yehor.finora.data.db.InvariantChecker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MigrationV3Test {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), FinoraDatabase::class.java)

    @Test
    fun migration2To3KeepsDataAndAddsEmptyRecurringTables() {
        val name = "migration-2-3"
        helper.createDatabase(name, 2).apply {
            execSQL("INSERT INTO currency (code, scale, symbol, name) VALUES ('USD', 2, '$', 'Dollar')")
            execSQL("INSERT INTO account (id, name, currency_code, type, icon, color, description, status, created_at) VALUES (1, 'Checking', 'USD', 'CHECKING', NULL, NULL, NULL, 'ACTIVE', 100)")
            execSQL("INSERT INTO category (id, name, icon, color, type, parent_id, created_at) VALUES (1, 'Food', NULL, NULL, 'EXPENSE', NULL, 100)")
            execSQL("INSERT INTO tag (id, name, color, created_at) VALUES (1, 'groceries', NULL, 100)")
            execSQL("INSERT INTO operation (id, type, occurred_at, category_id, description, opening_account_id, created_at, updated_at) VALUES (1, 'EXPENSE', 100, 1, 'Lunch', NULL, 100, 100)")
            execSQL("INSERT INTO ledger_entry (id, operation_id, account_id, currency_code, amount_minor, occurred_at) VALUES (1, 1, 1, 'USD', -1500, 100)")
            execSQL("INSERT INTO operation_tag (operation_id, tag_id) VALUES (1, 1)")
            execSQL("INSERT INTO app_setting (`key`, value, updated_at) VALUES ('theme_mode', 'dark', 100)")
            execSQL("INSERT INTO exchange_rate (base_currency, quote_currency, rate, source, quote_date, fetched_at) VALUES ('USD', 'USD', '1', 'seed', '2026-11-06', 100)")
            close()
        }

        val db = helper.runMigrationsAndValidate(name, 3, true)

        // Verify existing rows are equal
        db.query("SELECT name FROM account WHERE id = 1").use {
            assertTrue(it.moveToFirst())
            assertEquals("Checking", it.getString(0))
        }
        db.query("SELECT name FROM category WHERE id = 1").use {
            assertTrue(it.moveToFirst())
            assertEquals("Food", it.getString(0))
        }
        db.query("SELECT name FROM tag WHERE id = 1").use {
            assertTrue(it.moveToFirst())
            assertEquals("groceries", it.getString(0))
        }
        db.query("SELECT description FROM operation WHERE id = 1").use {
            assertTrue(it.moveToFirst())
            assertEquals("Lunch", it.getString(0))
        }
        db.query("SELECT amount_minor FROM ledger_entry WHERE id = 1").use {
            assertTrue(it.moveToFirst())
            assertEquals(-1500L, it.getLong(0))
        }
        db.query("SELECT value FROM app_setting WHERE `key` = 'theme_mode'").use {
            assertTrue(it.moveToFirst())
            assertEquals("dark", it.getString(0))
        }

        // Verify new tables exist and are empty
        db.query("SELECT COUNT(*) FROM recurring_schedule").use {
            assertTrue(it.moveToFirst())
            assertEquals(0, it.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM recurring_schedule_tag").use {
            assertTrue(it.moveToFirst())
            assertEquals(0, it.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM recurring_occurrence").use {
            assertTrue(it.moveToFirst())
            assertEquals(0, it.getInt(0))
        }

        db.close()
    }

    @Test
    fun fullChain1To3MigratesCleanly() {
        val name = "migration-chain-1-3"
        helper.createDatabase(name, 1).apply {
            execSQL("INSERT INTO currency (code, scale, symbol, name) VALUES ('USD', 2, '$', 'Dollar')")
            execSQL("INSERT INTO app_setting (`key`, value, updated_at) VALUES ('theme_mode', 'dark', 100)")
            close()
        }
        val db = helper.runMigrationsAndValidate(name, 3, true)
        db.query("SELECT value FROM app_setting WHERE `key` = 'theme_mode'").use {
            assertTrue(it.moveToFirst())
            assertEquals("dark", it.getString(0))
        }
        db.close()
    }

    @Test
    fun invariantCheckerFlagsEachRecurringViolation() {
        val name = "migration-recurring-checker"
        helper.createDatabase(name, 1).close()
        val db = helper.runMigrationsAndValidate(name, 3, true)

        db.execSQL("INSERT INTO currency (code, scale, symbol, name) VALUES ('USD', 2, '$', 'Dollar')")
        db.execSQL("INSERT INTO account (id, name, currency_code, type, icon, color, description, status, created_at) VALUES (1, 'Acc', 'USD', 'CHECKING', NULL, NULL, NULL, 'ACTIVE', 100)")
        db.execSQL("INSERT INTO category (id, name, icon, color, type, parent_id, created_at) VALUES (1, 'IncomeCat', NULL, NULL, 'INCOME', NULL, 100)")
        db.execSQL("INSERT INTO category (id, name, icon, color, type, parent_id, created_at) VALUES (2, 'ExpenseCat', NULL, NULL, 'EXPENSE', NULL, 100)")

        // Valid schedule and occurrence
        db.execSQL(
            "INSERT INTO recurring_schedule (id, name, kind, account_id, amount_minor, category_id, unit, interval_count, start_date, end_date, next_date, status, created_at, updated_at) " +
                "VALUES (1, 'Salary', 'INCOME', 1, 100000, 1, 'MONTH', 1, 20000, 21000, 20030, 'ACTIVE', 100, 100)",
        )
        db.execSQL(
            "INSERT INTO recurring_occurrence (id, schedule_id, occurrence_date, status, operation_id, handled_at) " +
                "VALUES (1, 1, 20000, 'GENERATED', NULL, 100)",
        )

        assertEquals(emptyList<String>(), InvariantChecker.check(db).filter { it.startsWith("recurring") })

        // Check each violation
        // 1. recurring amount not positive
        db.execSQL("UPDATE recurring_schedule SET amount_minor = 0 WHERE id = 1")
        assertTrue(InvariantChecker.check(db).any { it.startsWith("recurring amount not positive") })
        db.execSQL("UPDATE recurring_schedule SET amount_minor = 100000 WHERE id = 1")

        // 2. recurring bad interval
        db.execSQL("UPDATE recurring_schedule SET interval_count = 0 WHERE id = 1")
        assertTrue(InvariantChecker.check(db).any { it.startsWith("recurring bad interval") })
        db.execSQL("UPDATE recurring_schedule SET interval_count = 1 WHERE id = 1")

        // 3. recurring end before start
        db.execSQL("UPDATE recurring_schedule SET end_date = 19000 WHERE id = 1")
        assertTrue(InvariantChecker.check(db).any { it.startsWith("recurring end before start") })
        db.execSQL("UPDATE recurring_schedule SET end_date = 21000 WHERE id = 1")

        // 4. recurring next out of range
        db.execSQL("UPDATE recurring_schedule SET next_date = 19000 WHERE id = 1")
        assertTrue(InvariantChecker.check(db).any { it.startsWith("recurring next out of range") })
        db.execSQL("UPDATE recurring_schedule SET next_date = 20030 WHERE id = 1")

        // 5. recurring open-ended without next
        db.execSQL("UPDATE recurring_schedule SET next_date = NULL, end_date = NULL WHERE id = 1")
        assertTrue(InvariantChecker.check(db).any { it.startsWith("recurring open-ended without next") })
        db.execSQL("UPDATE recurring_schedule SET next_date = 20030, end_date = 21000 WHERE id = 1")

        // 6. recurring category type mismatch
        db.execSQL("UPDATE recurring_schedule SET category_id = 2 WHERE id = 1") // Expense category on Income schedule
        assertTrue(InvariantChecker.check(db).any { it.startsWith("recurring category type mismatch") })
        db.execSQL("UPDATE recurring_schedule SET category_id = 1 WHERE id = 1")

        // 7. recurring occurrence not before next
        db.execSQL("UPDATE recurring_occurrence SET occurrence_date = 20030 WHERE id = 1") // equal to next_date
        assertTrue(InvariantChecker.check(db).any { it.startsWith("recurring occurrence not before next") })
        db.execSQL("UPDATE recurring_occurrence SET occurrence_date = 20000 WHERE id = 1")

        // 8. recurring skipped with operation
        db.execSQL("INSERT INTO operation (id, type, occurred_at, category_id, description, opening_account_id, created_at, updated_at) VALUES (10, 'INCOME', 100, 1, 'Test', NULL, 100, 100)")
        db.execSQL("UPDATE recurring_occurrence SET status = 'SKIPPED', operation_id = 10 WHERE id = 1")
        assertTrue(InvariantChecker.check(db).any { it.startsWith("recurring skipped with operation") })
        db.execSQL("UPDATE recurring_occurrence SET status = 'GENERATED', operation_id = NULL WHERE id = 1")

        // 9. recurring generated operation mismatch
        db.execSQL("INSERT INTO operation (id, type, occurred_at, category_id, description, opening_account_id, created_at, updated_at) VALUES (11, 'EXPENSE', 100, 2, 'Exp', NULL, 100, 100)")
        db.execSQL("UPDATE recurring_occurrence SET status = 'GENERATED', operation_id = 11 WHERE id = 1")
        assertTrue(InvariantChecker.check(db).any { it.startsWith("recurring generated operation mismatch") })
        db.execSQL("UPDATE recurring_occurrence SET status = 'GENERATED', operation_id = NULL WHERE id = 1")

        db.close()
    }

    @Test
    fun dueSchedulesQueryUsesIndex() {
        val name = "query-plan-test"
        helper.createDatabase(name, 1).close()
        val db = helper.runMigrationsAndValidate(name, 3, true)
        db.query("EXPLAIN QUERY PLAN SELECT * FROM recurring_schedule WHERE status = 'ACTIVE' AND next_date IS NOT NULL AND next_date <= 20000 ORDER BY id").use {
            var usesIndex = false
            while (it.moveToNext()) {
                val detail = it.getString(3)
                if (detail.contains("index_recurring_schedule_status_next_date", ignoreCase = true)) {
                    usesIndex = true
                }
            }
            assertTrue("Expected query plan to use index_recurring_schedule_status_next_date", usesIndex)
        }
        db.close()
    }
}
