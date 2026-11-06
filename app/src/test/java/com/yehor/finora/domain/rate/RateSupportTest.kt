package com.yehor.finora.domain.rate

import com.yehor.finora.domain.money.CurrencyRegistry
import com.yehor.finora.domain.money.CurrencyMismatchException
import com.yehor.finora.domain.money.Estimate
import com.yehor.finora.domain.money.EstimateBasis
import com.yehor.finora.domain.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate

class RateFreshnessTest {
    private fun quote(fetchedAt: Long) = RateQuote(BigDecimal.ONE, LocalDate.of(2026, 11, 6), fetchedAt)

    @Test
    fun dueAfterExactlyTwentyFourHours() {
        val now = 10L * RateFreshness.REFRESH_AFTER_MS
        assertTrue(RateFreshness.isDue(null, now))
        assertTrue(RateFreshness.isDue(quote(now - RateFreshness.REFRESH_AFTER_MS), now))
        assertFalse(RateFreshness.isDue(quote(now - RateFreshness.REFRESH_AFTER_MS + 1), now))
    }

    @Test
    fun outdatedAfterMoreThanSeventyTwoHours() {
        val now = 10L * RateFreshness.OUTDATED_AFTER_MS
        assertFalse(RateFreshness.isOutdated(quote(now - RateFreshness.OUTDATED_AFTER_MS), now))
        assertTrue(RateFreshness.isOutdated(quote(now - RateFreshness.OUTDATED_AFTER_MS - 1), now))
    }
}

class EstimateTest {
    private val pln = CurrencyRegistry.get("PLN")
    private val usd = CurrencyRegistry.get("USD")

    @Test
    fun plusMergesBasisFieldWise() {
        val a = Estimate(100, pln, EstimateBasis(usesConversion = true, hasOutdatedRate = false))
        val b = Estimate(50, pln, EstimateBasis(usesConversion = false, hasOutdatedRate = true))
        val sum = a + b
        assertEquals(150L, sum.amountMinor)
        assertEquals(EstimateBasis(usesConversion = true, hasOutdatedRate = true), sum.basis)
    }

    @Test
    fun plusRejectsDifferentCurrencies() {
        assertThrows(CurrencyMismatchException::class.java) {
            Estimate.zero(pln) + Estimate.zero(usd)
        }
    }

    @Test
    fun formatShowsApproximationOnlyWhenConverted() {
        assertTrue(Estimate(391_260, pln, EstimateBasis(true, false)).format().startsWith("≈ 3912.60"))
        assertEquals("3912.60 ${pln.symbol}", Estimate(391_260, pln, EstimateBasis.NATIVE).format())
    }

    @Test
    fun estimateIsNotMoneyAndHasNoWayBack() {
        assertFalse(Money::class.java.isAssignableFrom(Estimate::class.java))
        val dir = File("src/main/java/com/yehor/finora/domain")
        val sources = dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue(sources.isNotEmpty())
        sources.forEach { f ->
            val text = f.readText()
            assertFalse("${f.name} converts Estimate to Money", Regex("""fun\s+(Estimate\.)?toMoney""").containsMatchIn(text))
            assertFalse("${f.name} builds Money from an Estimate", Regex("""Money\(\s*\w*estimate\w*\.amountMinor""", RegexOption.IGNORE_CASE).containsMatchIn(text))
        }
    }
}

class RateStaticChecksTest {
    private val floating = Regex("""\b(Float|Double)\b|\d[fF]\b|toDouble|toFloat|\.double\b""")

    private fun kotlinFiles(path: String) =
        File(path).walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    @Test
    fun moneyAndRatePathHaveNoFloatingPoint() {
        listOf(
            "src/main/java/com/yehor/finora/domain/money",
            "src/main/java/com/yehor/finora/domain/rate",
            "src/main/java/com/yehor/finora/data/rates",
        ).forEach { dir ->
            val files = kotlinFiles(dir)
            assertTrue("no sources under $dir", files.isNotEmpty())
            files.forEach { file ->
                file.readLines().forEachIndexed { i, line ->
                    val code = line.substringBefore("//").trim()
                    if (code.startsWith("*") || code.startsWith("/*")) return@forEachIndexed
                    assertFalse("${file.name}:${i + 1} uses floating point: $line", floating.containsMatchIn(code))
                }
            }
        }
    }

    @Test
    fun domainKnowsNothingAboutFrankfurter() {
        kotlinFiles("src/main/java/com/yehor/finora/domain").forEach { file ->
            val text = file.readText()
            assertFalse("${file.name} imports data.rates", text.contains("data.rates"))
            assertFalse("${file.name} mentions frankfurter", text.contains("frankfurter", ignoreCase = true))
        }
    }
}
