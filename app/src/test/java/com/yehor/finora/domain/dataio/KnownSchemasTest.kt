package com.yehor.finora.domain.dataio

import com.yehor.finora.data.db.FinoraDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class KnownSchemasTest {

    private fun appDir(): File {
        var appDir = File(".")
        if (!File(appDir, "src").exists() && File(appDir, "app").exists()) {
            appDir = File(appDir, "app")
        }
        return appDir
    }

    @Test
    fun `keys are exactly 1 to FinoraDatabase VERSION`() {
        val expectedKeys = (1..FinoraDatabase.VERSION).toSet()
        assertEquals(expectedKeys, KnownSchemas.identityHashes.keys)
    }

    @Test
    fun `each identityHash matches the committed schema JSON file`() {
        val identityRegex = Regex("\"identityHash\"\\s*:\\s*\"([^\"]+)\"")
        for (version in 1..FinoraDatabase.VERSION) {
            val schemaFile = File(appDir(), "schemas/com.yehor.finora.data.db.FinoraDatabase/$version.json")
            assertTrue("Schema file not found at ${schemaFile.absolutePath}", schemaFile.exists())
            val text = schemaFile.readText()
            val match = identityRegex.find(text)
            requireNotNull(match) { "identityHash not found in schema v$version" }
            val hashInJson = match.groupValues[1]
            assertEquals(
                "KnownSchemas hash mismatch for schema version $version",
                hashInJson,
                KnownSchemas.identityHashes[version],
            )
        }
    }
}
