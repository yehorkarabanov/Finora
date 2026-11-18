package com.yehor.finora

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.yehor.finora.data.db.FinoraDatabase
import com.yehor.finora.data.db.InvariantChecker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MigrationV2Test {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), FinoraDatabase::class.java)

    @Test
    fun migration1To2KeepsDataAndAddsEmptyRateTable() {
        val name = "migration-1-2"
        helper.createDatabase(name, 1).apply {
            execSQL("INSERT INTO currency (code, scale, symbol, name) VALUES ('PLN', 2, 'zł', 'Zloty')")
            execSQL("INSERT INTO app_setting (`key`, value, updated_at) VALUES ('theme_mode', 'dark', 7)")
            close()
        }
        val db = helper.runMigrationsAndValidate(name, 2, true)
        db.query("SELECT value FROM app_setting WHERE `key` = 'theme_mode'").use {
            assertTrue(it.moveToFirst())
            assertEquals("dark", it.getString(0))
        }
        db.query("SELECT COUNT(*) FROM exchange_rate").use {
            it.moveToFirst()
            assertEquals(0, it.getInt(0))
        }
        db.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name LIKE '%rate%'").use {
            val names = buildList { while (it.moveToNext()) add(it.getString(0)) }
            assertEquals(listOf("exchange_rate"), names)
        }
        db.close()
    }

    @Test
    fun cacheWorksAndInvariantCheckerFlagsEachViolation() {
        val name = "migration-rates"
        helper.createDatabase(name, 1).close()
        // InvariantChecker covers the current schema, so migrate the whole chain.
        val db = helper.runMigrationsAndValidate(name, 3, true)
        db.execSQL("INSERT INTO currency (code, scale, symbol, name) VALUES ('PLN', 2, 'zł', 'Zloty')")
        db.execSQL("INSERT INTO currency (code, scale, symbol, name) VALUES ('USD', 2, '$', 'Dollar')")
        db.execSQL(
            "INSERT INTO exchange_rate (base_currency, quote_currency, rate, source, quote_date, fetched_at) " +
                "VALUES ('USD', 'PLN', '3.9126', 'frankfurter', '2026-11-06', 1)",
        )
        assertEquals(emptyList<String>(), InvariantChecker.check(db).filter { it.startsWith("exchange_rate") })

        listOf(
            "exchange_rate same currency" to "('PLN', 'PLN', '1', 'frankfurter', '2026-11-06', 1)",
            "exchange_rate invalid rate" to "('PLN', 'USD', '0', 'frankfurter', '2026-11-06', 1)",
            "exchange_rate invalid quote date" to "('PLN', 'USD', '2', 'frankfurter', 'bad', 1)",
            "exchange_rate blank source" to "('PLN', 'USD', '2', ' ', '2026-11-06', 1)",
        ).forEach { (check, values) ->
            db.execSQL("DELETE FROM exchange_rate WHERE base_currency = 'PLN'")
            db.execSQL(
                "INSERT INTO exchange_rate (base_currency, quote_currency, rate, source, quote_date, fetched_at) VALUES $values",
            )
            assertTrue(check, InvariantChecker.check(db).any { it.startsWith(check) })
        }
        db.close()
    }
}
