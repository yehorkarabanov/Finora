package com.yehor.finora.domain.analytics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DomainAnalyticsCleanlinessTest {

    private val floating = Regex("""\b(Float|Double)\b|\d[fF]\b|toDouble|toFloat|\.double\b""")
    private val systemClock = Regex("""System\.(currentTimeMillis|nanoTime)|Instant\.now|LocalDate\.now|LocalDateTime\.now|Clock\.system""")

    /** A function or property whose return type is Money and whose parameters or receiver include an Estimate. */
    private val estimateToMoney = Regex("""fun\s+(\w+\.)?\w+\s*\([^)]*Estimate[^)]*\)\s*:\s*Money\b""")
    private val receiverEstimateToMoney = Regex("""fun\s+Estimate\.\w+\s*\([^)]*\)\s*:\s*Money\b""")
    private val moneyConstruction = Regex("""\bMoney\(\s*[\w.]*estimate[\w.]*""", RegexOption.IGNORE_CASE)

    private fun kotlinFiles(path: String) =
        File(path).walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    @Test
    fun `domain analytics has no floating point, android, room or system clock`() {
        val dir = "src/main/java/com/yehor/finora/domain/analytics"
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

    @Test
    fun `no function in domain analytics turns an Estimate into a Money`() {
        val dir = "src/main/java/com/yehor/finora/domain/analytics"
        kotlinFiles(dir).forEach { file ->
            file.readLines().forEachIndexed { i, line ->
                val code = line.substringBefore("//").trim()
                if (code.startsWith("*") || code.startsWith("/*")) return@forEachIndexed
                assertFalse("${file.name}:${i + 1} converts Estimate to Money: $line", estimateToMoney.containsMatchIn(code))
                assertFalse("${file.name}:${i + 1} converts Estimate to Money: $line", receiverEstimateToMoney.containsMatchIn(code))
                assertFalse("${file.name}:${i + 1} builds Money from an estimate: $line", moneyConstruction.containsMatchIn(code))
            }
        }
    }

    @Test
    fun `the analytics sources really are scanned`() {
        // Guards the two tests above against a wrong working directory: DayTicker must be found.
        assertTrue(File("src/main/java/com/yehor/finora/domain/analytics/DayTicker.kt").exists())
    }
}
