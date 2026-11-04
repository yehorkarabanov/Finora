package com.yehor.finora

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Ensures that the Room database schema (com.yehor.finora.data.db.FinoraDatabase/1.json)
 * remains exactly byte-identical to the committed v1 expected schema.
 *
 * In sub-project 3, the pinned baseline includes index_operation_occurred_at_id (S9).
 */
class SchemaUnchangedTest {

    @Test
    fun schemaFileIsByteIdentical() {
        val schemaPath = "schemas/com.yehor.finora.data.db.FinoraDatabase/1.json"
        val expectedPath = "src/test/resources/schema-v1.expected.json"

        // We run unit tests from the sub-project directory (app/) or project root.
        // Let's resolve files relative to the 'app' module root.
        var appDir = File(".")
        if (!File(appDir, "src").exists() && File(appDir, "app").exists()) {
            appDir = File(appDir, "app")
        }

        val schemaFile = File(appDir, schemaPath)
        val expectedFile = File(appDir, expectedPath)

        assertTrue("Active schema file not found at ${schemaFile.absolutePath}", schemaFile.exists())
        assertTrue("Expected schema file not found at ${expectedFile.absolutePath}", expectedFile.exists())

        val activeContent = schemaFile.readText().replace("\r\n", "\n").trim()
        val expectedContent = expectedFile.readText().replace("\r\n", "\n").trim()

        assertEquals(
            "Database schema has changed! If this was intentional, update the plan. Otherwise, revert the entity changes.",
            expectedContent,
            activeContent
        )
    }
}
