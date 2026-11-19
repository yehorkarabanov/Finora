package com.yehor.finora.ui.accounts

import com.yehor.finora.domain.analytics.Bucket
import com.yehor.finora.domain.analytics.BalancePoint
import com.yehor.finora.domain.money.CurrencyRegistry
import com.yehor.finora.domain.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AccountsListLogicTest {
    @Test fun sharesSumToOne() =
        assertEquals(1f, accountShares(listOf(1L to 300L, 2L to 100L)).sumOf { it.second.toDouble() }.toFloat(), 0.001f)

    @Test fun negativeIgnored() =
        assertEquals(listOf(1L to 1f), accountShares(listOf(1L to 50L, 2L to -20L)).filter { it.second > 0f })

    @Test fun allZeroEmpty() = assertTrue(accountShares(listOf(1L to 0L, 2L to null)).isEmpty())

    private val usd = CurrencyRegistry.get("USD")
    private val start = LocalDate.of(2026, 10, 20)

    private fun points(vararg balances: Long) = balances.mapIndexed { i, b ->
        val d = start.plusDays(i.toLong())
        BalancePoint(Bucket(i, d, d, 0L, 0L), Money(b, usd))
    }

    @Test fun trendKeepsLastThirtyPointsAndMonthChange() {
        // Window: Oct 20 .. Nov 11 (23 days); month starts Nov 1 = index 12, the day before is index 11.
        val series = points(*LongArray(23) { 100L + it })
        val trend = accountTrend(series, LocalDate.of(2026, 11, 1))
        assertEquals(23, trend.sparkline.size)
        assertEquals(122L - 111L, trend.monthChange)
    }

    @Test fun trendTrimsToThirty() {
        val series = points(*LongArray(40) { it.toLong() })
        assertEquals(30, accountTrend(series, LocalDate.of(2026, 11, 20)).sparkline.size)
    }

    @Test fun monthChangeNullWithoutBaseline() {
        // First point is already inside the month: no balance before the month is known.
        val trend = accountTrend(points(5L, 6L), LocalDate.of(2026, 10, 20))
        assertNull(trend.monthChange)
    }

    @Test fun emptySeries() {
        val trend = accountTrend(emptyList(), LocalDate.of(2026, 11, 1))
        assertTrue(trend.sparkline.isEmpty())
        assertNull(trend.monthChange)
    }
}
