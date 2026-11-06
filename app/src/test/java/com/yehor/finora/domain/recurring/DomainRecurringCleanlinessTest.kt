package com.yehor.finora.domain.recurring

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DomainRecurringCleanlinessTest {

    private val floating = Regex("""\b(Float|Double)\b|\d[fF]\b|toDouble|toFloat|\.double\b""")

    private fun kotlinFiles(path: String) =
        File(path).walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    @Test
    fun `domain recurring has no floating point, android, room or system clock`() {
        val dir = "src/main/java/com/yehor/finora/domain/recurring"
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
            }
        }
    }
}
