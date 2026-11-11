package com.yehor.finora.ui.operations

import com.yehor.finora.domain.account.Account
import com.yehor.finora.domain.model.AccountStatus
import com.yehor.finora.domain.account.AccountType
import com.yehor.finora.domain.model.OperationType
import com.yehor.finora.domain.money.CurrencyRegistry
import com.yehor.finora.domain.money.Money
import com.yehor.finora.domain.operation.DayGroup
import com.yehor.finora.domain.operation.TransactionEntryView
import com.yehor.finora.domain.operation.TransactionItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TransactionsSectionsTest {
    private val usd = CurrencyRegistry.get("USD")
    private val eur = CurrencyRegistry.get("EUR")
    private val usdAccount = Account(1, "A", usd, AccountType.BANK, null, null, null, AccountStatus.ACTIVE, 0)
    private val eurAccount = Account(2, "B", eur, AccountType.BANK, null, null, null, AccountStatus.ACTIVE, 0)

    private fun item(id: Long, type: OperationType, vararg entries: Pair<Account, Money>) = TransactionItem(
        id, type, 0, null, null, emptyList(), emptyList(),
        entries.map { TransactionEntryView(it.first, it.second) }, 0, 0,
    )

    private fun expense(id: Long, minor: Long) = item(id, OperationType.EXPENSE, usdAccount to Money(minor, usd))

    private fun source(rows: List<TransactionListRow>, endReached: Boolean = true) =
        RowSource(rows.size, { rows.getOrNull(it) }, { rows.getOrNull(it) }, endReached)

    @Test
    fun netSumsOneCurrency() {
        val net = dayNet(listOf(expense(1, -6420), expense(2, -1000), item(3, OperationType.INCOME, usdAccount to Money(500, usd))))
        assertEquals(Money(-6920, usd), net)
    }

    @Test
    fun netIsOmittedForMixedCurrencies() {
        val mixed = listOf(expense(1, -100), item(2, OperationType.EXPENSE, eurAccount to Money(-100, eur)))
        assertNull(dayNet(mixed))
    }

    @Test
    fun netIgnoresTransfersAndIsNullWithoutOperations() {
        val transfer = item(1, OperationType.TRANSFER, usdAccount to Money(-500, usd), eurAccount to Money(450, eur))
        assertNull(dayNet(listOf(transfer)))
        assertEquals(Money(-100, usd), dayNet(listOf(transfer, expense(2, -100))))
    }

    @Test
    fun daysBecomeSectionsWithTheirNet() {
        val rows = listOf(
            TransactionListRow.Header(DayGroup.Today),
            TransactionListRow.Entry(expense(1, -100)),
            TransactionListRow.Entry(expense(2, -200)),
            TransactionListRow.Header(DayGroup.Yesterday),
            TransactionListRow.Entry(expense(3, -50)),
        )
        val sections = buildSections(source(rows))
        assertEquals(2, sections.size)
        assertEquals(1 until 3, sections[0].start until sections[0].endExclusive)
        assertEquals(Money(-300, usd), sections[0].net)
        assertEquals(Money(-50, usd), sections[1].net)
    }

    @Test
    fun lastDayHasNoNetWhileMorePagesAreComing() {
        val rows = listOf(
            TransactionListRow.Header(DayGroup.Today),
            TransactionListRow.Entry(expense(1, -100)),
            TransactionListRow.Header(DayGroup.Yesterday),
            TransactionListRow.Entry(expense(2, -50)),
        )
        val sections = buildSections(source(rows, endReached = false))
        assertEquals(Money(-100, usd), sections[0].net)
        assertNull(sections[1].net)
    }

    @Test
    fun listWithoutHeadersIsChunked() {
        val rows = (1L..5L).map { TransactionListRow.Entry(expense(it, -10)) }
        val sections = buildSections(source(rows), chunk = 2)
        assertEquals(listOf(0 to 2, 2 to 4, 4 to 5), sections.map { it.start to it.endExclusive })
        assertEquals(setOf(null), sections.map { it.group }.toSet())
    }
}
