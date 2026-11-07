package com.yehor.finora.domain.dataio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DomainDataIoCleanlinessTest {

    private val floating = Regex("""\b(Float|Double)\b|\d[fF]\b|toDouble|toFloat|\.double\b""")
    private val systemClock = Regex("""System\.(currentTimeMillis|nanoTime)|Instant\.now|LocalDate\.now|LocalDateTime\.now|Clock\.system""")

    private fun appDir(): File {
        var appDir = File(".")
        if (!File(appDir, "src").exists() && File(appDir, "app").exists()) {
            appDir = File(appDir, "app")
        }
        return appDir
    }

    private fun kotlinFiles(path: String) =
        File(appDir(), path).walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    @Test
    fun `domain dataio has no floating point, android, room or system clock`() {
        val dir = "src/main/java/com/yehor/finora/domain/dataio"
        val files = kotlinFiles(dir)
        assertTrue("no sources under $dir", files.isNotEmpty())

        files.forEach { file ->
            val text = file.readText()
            assertFalse("${file.name} imports android", text.contains("import android."))
            assertFalse("${file.name} imports room", text.contains("androidx.room"))

            file.readLines().forEachIndexed { i, line ->
                val code = line.substringBefore("//").trim()
                if (code.startsWith("*") || code.startsWith("/*")) return@forEachIndexed
                assertFalse("${file.name}:${i + 1} uses floating point: $line", floating.containsMatchIn(code))
                assertFalse("${file.name}:${i + 1} reads the system clock: $line", systemClock.containsMatchIn(code))
            }
        }
    }
}
