package com.yehor.finora

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Review rule as a test: destructive migration is forbidden (design section 7). */
class NoDestructiveMigrationTest {

    @Test
    fun mainSourcesNeverUseDestructiveMigration() {
        val root = File("src/main")
        val sources = root.walkTopDown().filter { it.isFile && it.extension in setOf("kt", "java") }.toList()
        assertTrue("no sources found under ${root.absolutePath}", sources.isNotEmpty())
        sources.forEach { f ->
            f.readLines().forEachIndexed { i, line ->
                val code = line.substringBefore("//").trim()
                if (code.startsWith("*") || code.startsWith("/*")) return@forEachIndexed
                assertFalse(
                    "${f.name}:${i + 1} uses destructive migration",
                    code.contains("fallbackToDestructiveMigration"),
                )
            }
        }
    }

    @Test
    fun databaseExportsSchema() {
        val text = File("src/main/java/com/yehor/finora/data/db/FinoraDatabase.kt").readText()
        assertTrue(text.contains("exportSchema = true"))
    }
}
