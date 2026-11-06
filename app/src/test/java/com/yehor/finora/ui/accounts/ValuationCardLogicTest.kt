package com.yehor.finora.ui.accounts

import com.yehor.finora.domain.account.Account
import com.yehor.finora.domain.account.AccountType
import com.yehor.finora.domain.account.AccountWithBalance
import com.yehor.finora.domain.account.Balance
import com.yehor.finora.domain.model.AccountStatus
import com.yehor.finora.domain.money.CurrencyRegistry
import com.yehor.finora.domain.money.Money
import com.yehor.finora.domain.operation.CurrencySummary
import com.yehor.finora.domain.rate.CurrencyPair
import com.yehor.finora.domain.rate.RateFreshness
import com.yehor.finora.domain.rate.RateQuote
import com.yehor.finora.domain.rate.RateTable
import com.yehor.finora.ui.operations.SummaryConversion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class ValuationCardLogicTest {
    private val pln = CurrencyRegistry.get("PLN")
    private val usd = CurrencyRegistry.get("USD")
    private val thb = CurrencyRegistry.get("THB")
    private val now = 1_000_000_000_000L

    private fun acc(id: Long, balance: Balance, code: String) = AccountWithBalance(
        Account(id, "a$id", CurrencyRegistry.get(code), AccountType.CASH, null, null, null, AccountStatus.ACTIVE, 0),
        balance,
    )

    private fun value(minor: Long, code: String) = Balance.Value(Money(minor, CurrencyRegistry.get(code)))

    private fun table(fetchedAt: Long = now) = RateTable(
        mapOf(CurrencyPair(usd, pln) to RateQuote(BigDecimal("4"), LocalDate.of(2026, 11, 6), fetchedAt)),
    )

    @Test
    fun noActiveAccountsMeansNoCard() {
        assertNull(ValuationCardLogic.build(emptyList(), pln, RateTable.EMPTY, now))
    }

    @Test
    fun nativeOnly() {
        val card = ValuationCardLogic.build(listOf(acc(1, value(1000, "PLN"), "PLN")), pln, RateTable.EMPTY, now)!!
        assertFalse(card.estimated)
        assertEquals(1000L, card.total!!.amountMinor)
        assertTrue(card.excluded.isEmpty())
    }

    @Test
    fun converted() {
        val card = ValuationCardLogic.build(
            listOf(acc(1, value(1000, "PLN"), "PLN"), acc(2, value(100, "USD"), "USD")), pln, table(), now,
        )!!
        assertTrue(card.estimated)
        assertFalse(card.outdated)
        assertEquals(1400L, card.total!!.amountMinor)
    }

    @Test
    fun outdated() {
        val card = ValuationCardLogic.build(
            listOf(acc(1, value(100, "USD"), "USD")), pln, table(now - RateFreshness.OUTDATED_AFTER_MS - 1), now,
        )!!
        assertTrue(card.outdated)
        assertEquals(now - RateFreshness.OUTDATED_AFTER_MS - 1, card.oldestFetchedAt)
    }

    @Test
    fun partialAndUnavailable() {
        val partial = ValuationCardLogic.build(
            listOf(acc(1, value(100, "USD"), "USD"), acc(2, value(5, "THB"), "THB")), pln, table(), now,
        )!!
        assertEquals(400L, partial.total!!.amountMinor)
        assertEquals(listOf(thb), partial.excluded)

        val none = ValuationCardLogic.build(listOf(acc(1, value(100, "USD"), "USD")), pln, RateTable.EMPTY, now)!!
        assertNull(none.total)
        assertEquals(listOf(usd), none.excluded)
    }

    @Test
    fun overflowingBalanceIsListedAsNotIncluded() {
        val card = ValuationCardLogic.build(
            listOf(acc(1, value(1000, "PLN"), "PLN"), acc(2, Balance.Overflow, "USD")), pln, table(), now,
        )!!
        assertEquals(1000L, card.total!!.amountMinor)
        assertEquals(listOf(usd), card.excluded)
    }

    @Test
    fun summaryConversionOnlyForMultipleCurrencies() {
        fun summary(code: String, income: Long, expense: Long) = CurrencySummary(
            CurrencyRegistry.get(code), Money(income, CurrencyRegistry.get(code)), Money(expense, CurrencyRegistry.get(code)), 1,
        )
        assertNull(SummaryConversion.build(listOf(summary("USD", 100, 50)), pln, table(), now))
        val converted = SummaryConversion.build(listOf(summary("PLN", 1000, 200), summary("USD", 100, 50)), pln, table(), now)!!
        assertEquals(1400L, converted.income!!.amountMinor)
        assertEquals(400L, converted.expense!!.amountMinor)
        assertEquals(1000L, converted.net!!.amountMinor)
        assertTrue(converted.income!!.format().startsWith("≈"))
    }
}
