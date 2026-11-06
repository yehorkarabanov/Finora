package com.yehor.finora

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.yehor.finora.data.db.ALL_MIGRATIONS
import com.yehor.finora.data.db.FinoraDatabase
import com.yehor.finora.scratch.ScratchDatabase
import com.yehor.finora.scratch.ScratchMigration_1_2
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MigrationInfrastructureTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @get:Rule
    val scratchHelper = MigrationTestHelper(instrumentation, ScratchDatabase::class.java)

    @get:Rule
    val productionHelper = MigrationTestHelper(instrumentation, FinoraDatabase::class.java)

    private fun asset(path: String): ByteArray =
        instrumentation.context.assets.open(path).use { it.readBytes() }

    /** The scratch migration must start from the real production v1 schema, byte for byte. */
    @Test
    fun scratchV1SchemaIsByteIdenticalToProductionV1() {
        val production = asset("com.yehor.finora.data.db.FinoraDatabase/1.json")
        val scratch = asset("com.yehor.finora.scratch.ScratchDatabase/1.json")
        assertArrayEquals(production, scratch)
    }

    @Test
    fun scratchMigrationKeepsDataAndValidatesSchema() {
        val name = "scratch-migration-test"
        scratchHelper.createDatabase(name, 1).apply {
            execSQL("INSERT INTO app_setting (`key`, value, updated_at) VALUES ('theme_mode', 'dark', 7)")
            close()
        }
        val migrated = scratchHelper.runMigrationsAndValidate(name, 2, true, ScratchMigration_1_2)
        migrated.query("SELECT `key`, value, updated_at, note FROM app_setting").use {
            assertTrue(it.moveToFirst())
            assertEquals("theme_mode", it.getString(0))
            assertEquals("dark", it.getString(1))
            assertEquals(7L, it.getLong(2))
            assertTrue(it.isNull(3))
        }
    }

    @Test
    fun productionV1CanBeCreatedAndValidated() {
        productionHelper.createDatabase("production-v1", 1).close()
        productionHelper.runMigrationsAndValidate(
            "production-v1", FinoraDatabase.VERSION, true, *ALL_MIGRATIONS,
        ).close()
    }

    @Test
    fun productionChainContainsNoMigrationBeyondProductionVersion() {
        assertTrue(ALL_MIGRATIONS.none { it.endVersion > FinoraDatabase.VERSION })
        assertEquals(3, FinoraDatabase.VERSION)
    }
}
