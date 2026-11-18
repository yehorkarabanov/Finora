package com.yehor.finora

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Ensures each committed Room schema file stays byte-identical to its pinned copy.
 * `1.json` is the frozen baseline and never changes again; `2.json` adds `exchange_rate`.
 */
class SchemaUnchangedTest {

    private fun appDir(): File {
        // Unit tests run from the app/ directory or the project root.
        var appDir = File(".")
        if (!File(appDir, "src").exists() && File(appDir, "app").exists()) {
            appDir = File(appDir, "app")
        }
        return appDir
    }

    private fun assertIdentical(version: Int) {
        val schemaFile = File(appDir(), "schemas/com.yehor.finora.data.db.FinoraDatabase/$version.json")
        val expectedFile = File(appDir(), "src/test/resources/schema-v$version.expected.json")

        assertTrue("Active schema file not found at ${schemaFile.absolutePath}", schemaFile.exists())
        assertTrue("Expected schema file not found at ${expectedFile.absolutePath}", expectedFile.exists())

        val activeContent = schemaFile.readText().replace("\r\n", "\n").trim()
        val expectedContent = expectedFile.readText().replace("\r\n", "\n").trim()

        assertEquals(
            "Database schema v$version has changed! If this was intentional, update the plan. Otherwise, revert the entity changes.",
            expectedContent,
            activeContent,
        )
    }

    @Test
    fun schemaV1IsByteIdentical() = assertIdentical(1)

    @Test
    fun schemaV2IsByteIdentical() = assertIdentical(2)

    @Test
    fun schemaV3IsByteIdentical() = assertIdentical(3)

    @Test
    fun exchangeRateIsTheOnlyRateTable() {
        val text = File(appDir(), "schemas/com.yehor.finora.data.db.FinoraDatabase/2.json").readText()
        val tableNames = Regex("\"tableName\"\\s*:\\s*\"([^\"]+)\"").findAll(text).map { it.groupValues[1] }.toList()
        assertEquals(listOf("exchange_rate"), tableNames.filter { it.contains("rate", ignoreCase = true) })
        assertTrue(tableNames.none { it.contains("manual", ignoreCase = true) })
    }
}
