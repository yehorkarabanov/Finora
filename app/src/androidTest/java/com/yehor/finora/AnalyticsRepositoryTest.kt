package com.yehor.finora

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.yehor.finora.data.db.*
import com.yehor.finora.data.repo.*
import com.yehor.finora.domain.analytics.*
import com.yehor.finora.domain.model.*
import com.yehor.finora.domain.money.*
import com.yehor.finora.domain.operation.*
import com.yehor.finora.domain.tag.TagMatchMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

class AnalyticsRepositoryTest {

    private lateinit var db: FinoraDatabase
    private lateinit var opRepo: OperationRepository
    private lateinit var categoryRepo: CategoryRepository
    private lateinit var transactions: TransactionRepository
    private lateinit var transfers: TransferRepository
    private lateinit var analyticsRepo: AnalyticsRepository

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, FinoraDatabase::class.java)
            .addCallback(FinoraDatabaseCallback)
            .build()
        opRepo = OperationRepository(db)
        val accountsRepo = AccountRepository(db, opRepo)
        categoryRepo = CategoryRepository(db)
        transactions = TransactionRepository(db, opRepo, accountsRepo, categoryRepo)
        transfers = TransferRepository(db, opRepo, accountsRepo, categoryRepo)
        analyticsRepo = AnalyticsRepository(db, categoryRepo)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun account(code: String, status: AccountStatus = AccountStatus.ACTIVE): Long =
        db.accountDao().insert(
            AccountEntity(
                name = "Account $code", currencyCode = code, type = "CASH", icon = null, color = null,
                description = null, status = status, createdAt = 0L
            )
        )

    private suspend fun category(name: String, type: CategoryType, parentId: Long? = null): Long =
        db.categoryDao().insert(
            CategoryEntity(
                name = name, icon = null, color = null, type = type, parentId = parentId, createdAt = 0L
            )
        )

    private suspend fun tag(name: String): Long =
        db.tagDao().insert(
            TagEntity(name = name, color = null, createdAt = 0L)
        )

    private suspend fun transaction(
        kind: TransactionKind,
        accountId: Long,
        amountMinor: Long,
        categoryId: Long?,
        occurredAt: Long,
        tagIds: Set<Long> = emptySet()
    ): Long {
        return transactions.create(
            TransactionDraft(
                kind = kind,
                accountId = accountId,
                amountMinor = amountMinor,
                categoryId = categoryId,
                occurredAt = occurredAt,
                description = "tx",
                tagIds = tagIds,
                newTagNames = emptyList()
            )
        )
    }

    private suspend fun transfer(
        sourceAccountId: Long,
        destAccountId: Long,
        sentAmountMinor: Long,
        receivedAmountMinor: Long,
        occurredAt: Long,
        categoryId: Long? = null,
        tagIds: Set<Long> = emptySet()
    ): Long {
        return transfers.create(
            TransferDraft(
                sourceAccountId = sourceAccountId,
                destAccountId = destAccountId,
                sentAmountMinor = sentAmountMinor,
                receivedAmountMinor = receivedAmountMinor,
                categoryId = categoryId,
                occurredAt = occurredAt,
                description = "transfer",
                tagIds = tagIds,
                newTagNames = emptyList()
            )
        )
    }

    @Test
    fun testCoreCorrectness() = runBlocking {
        val pln = account("PLN")
        val usd = account("USD")
        val eur = account("EUR", AccountStatus.ARCHIVED)

        val food = category("Food", CategoryType.EXPENSE)
        val groceries = category("Groceries", CategoryType.EXPENSE, parentId = food)
        val salary = category("Salary", CategoryType.INCOME)

        // Seed some expenses, incomes, transfers, opening balance
        // 1. Opening balance on PLN at t = 0
        opRepo.setOpeningBalance(pln, 10_000_00L, 0L) // 10,000 PLN

        // 2. Income (Salary) on PLN: +5,000 PLN at t = 1000
        transaction(TransactionKind.INCOME, pln, 5_000_00L, salary, 1000L)

        // 3. Expense (Groceries) on PLN: -150 PLN at t = 2000
        transaction(TransactionKind.EXPENSE, pln, 150_00L, groceries, 2000L)

        // 4. Expense (Food direct) on PLN: -50 PLN at t = 2500
        transaction(TransactionKind.EXPENSE, pln, 50_00L, food, 2500L)

        // 5. Transfer PLN to USD: 200 PLN to 50 USD at t = 3000
        transfer(pln, usd, 200_00L, 50_00L, 3000L)

        // 6. Archived account (EUR) expense: -30 EUR at t = 4000
        transaction(TransactionKind.EXPENSE, eur, 30_00L, food, 4000L)

        // Let's verify Expense and Income rows never include transfers or opening balances
        val categoryExpenses = analyticsRepo.observeByCategory(TransactionFilter(), Measure.EXPENSE).first()
        // Should have Groceries and Food (both under Food tree)
        // Since we didn't filter, both PLN and EUR are present
        // Let's check that totals are correct and don't include transfers or opening balance
        // Groceries expense: 150.00 PLN
        // Food expense: 50.00 PLN + 30.00 EUR (since both are Food category, but distinct currencies!)
        
        val groceriesRow = categoryExpenses.find { it.key == groceries }
        assertNotNull(groceriesRow)
        assertEquals(150_00L, groceriesRow!!.money.amountMinor)
        assertEquals("PLN", groceriesRow.money.currency.code)
        assertEquals(1, groceriesRow.operationCount)

        val foodRowPLN = categoryExpenses.find { it.key == food && it.money.currency.code == "PLN" }
        assertNotNull(foodRowPLN)
        assertEquals(50_00L, foodRowPLN!!.money.amountMinor)

        val foodRowEUR = categoryExpenses.find { it.key == food && it.money.currency.code == "EUR" }
        assertNotNull(foodRowEUR)
        assertEquals(30_00L, foodRowEUR!!.money.amountMinor)

        // Net equals income minus expense for every period tested
        // Let's verify that net doesn't include transfers or opening balances
        // We will test AccountPeriodRow in accounts overview or account figures.
        // Let's check accountsOverview
        val period = ResolvedPeriod(
            Period(PeriodKind.CUSTOM, LocalDate.of(2026,1,1)),
            LocalDate.of(2026,1,1), LocalDate.of(2026,12,31),
            0L, 10000L
        )
        val overview = analyticsRepo.observeAccountsOverview(period).first()
        val plnOverview = overview.find { it.accountId == pln }
        assertNotNull(plnOverview)
        // PLN: income = 5,000 PLN (Salary), expense = 200 PLN (groceries 150 + food 50)
        // Note: transfer (200 PLN outgoing) and opening balance (10,000 PLN) are not income/expense!
        assertEquals(5_000_00L, plnOverview!!.income.amountMinor)
        assertEquals(200_00L, plnOverview.expense.amountMinor)
        // Net change for account period row counts everything, transfers and opening balance included!
        assertEquals(14_600_00L, plnOverview.netChange.amountMinor)
    }

    @Test
    fun testCategoryExact() = runBlocking {
        val pln = account("PLN")
        val food = category("Food", CategoryType.EXPENSE)
        val groceries = category("Groceries", CategoryType.EXPENSE, parentId = food)

        transaction(TransactionKind.EXPENSE, pln, 150_00L, groceries, 2000L)
        transaction(TransactionKind.EXPENSE, pln, 50_00L, food, 2500L)

        // categoryExact = true returns only direct operations of the category.
        // The default returns descendants as well.
        val filterExact = TransactionFilter(categoryIds = setOf(food), categoryExact = true)
        val rowsExact = analyticsRepo.observeByCategory(filterExact, Measure.EXPENSE).first()
        assertEquals(1, rowsExact.size)
        assertEquals(food, rowsExact.first().key)
        assertEquals(50_00L, rowsExact.first().money.amountMinor)

        val filterDefault = TransactionFilter(categoryIds = setOf(food), categoryExact = false)
        val rowsDefault = analyticsRepo.observeByCategory(filterDefault, Measure.EXPENSE).first()
        assertEquals(2, rowsDefault.size)
        val groceriesRow = rowsDefault.find { it.key == groceries }
        val foodRow = rowsDefault.find { it.key == food }
        assertNotNull(groceriesRow)
        assertEquals(150_00L, groceriesRow!!.money.amountMinor)
        assertNotNull(foodRow)
        assertEquals(50_00L, foodRow!!.money.amountMinor)
    }

    @Test
    fun testTags() = runBlocking {
        val pln = account("PLN")
        val cat = category("c", CategoryType.EXPENSE)
        val tagA = tag("A")
        val tagB = tag("B")

        // 1. Transaction with Tag A
        transaction(TransactionKind.EXPENSE, pln, 100_00L, cat, 1000L, tagIds = setOf(tagA))
        // 2. Transaction with Tag B
        transaction(TransactionKind.EXPENSE, pln, 200_00L, cat, 2000L, tagIds = setOf(tagB))
        // 3. Transaction with Tag A and B
        transaction(TransactionKind.EXPENSE, pln, 300_00L, cat, 3000L, tagIds = setOf(tagA, tagB))
        // 4. Untagged transaction
        transaction(TransactionKind.EXPENSE, pln, 400_00L, cat, 4000L, tagIds = emptySet())

        // Tags: ANY returns the union, ALL the intersection, a multi-tag transaction counts under each tag,
        // and the "No tag" row holds exactly the untagged ones.
        
        val tagRows = analyticsRepo.observeByTag(TransactionFilter(), Measure.EXPENSE).first()
        val rowA = tagRows.find { it.key == tagA }
        val rowB = tagRows.find { it.key == tagB }
        val rowNull = tagRows.find { it.key == null }

        assertNotNull(rowA)
        assertEquals(400_00L, rowA!!.money.amountMinor)
        assertEquals(2, rowA.operationCount)

        assertNotNull(rowB)
        assertEquals(500_00L, rowB!!.money.amountMinor)
        assertEquals(2, rowB.operationCount)

        assertNotNull(rowNull)
        assertEquals(400_00L, rowNull!!.money.amountMinor)
        assertEquals(1, rowNull.operationCount)

        // Let's test Filter with TagMode ALL: A and B
        val filterAll = TransactionFilter(tagIds = setOf(tagA, tagB), tagMode = TagMatchMode.ALL)
        val tagRowsAll = analyticsRepo.observeByTag(filterAll, Measure.EXPENSE).first()
        val rowAllA = tagRowsAll.find { it.key == tagA }
        val rowAllB = tagRowsAll.find { it.key == tagB }
        assertNotNull(rowAllA)
        assertEquals(300_00L, rowAllA!!.money.amountMinor)
        assertNotNull(rowAllB)
        assertEquals(300_00L, rowAllB!!.money.amountMinor)
    }

    @Test
    fun testTransferMeasure() = runBlocking {
        val pln = account("PLN")
        val usd = account("USD")

        // Same currency transfer: 100 to 95 PLN
        val pln2 = account("PLN")
        transfer(pln, pln2, 100_00L, 95_00L, 1000L)

        // Cross currency transfer: 200 PLN to 50 USD
        transfer(pln, usd, 200_00L, 50_00L, 2000L)

        val transferRows = analyticsRepo.observeByAccount(TransactionFilter(), Measure.TRANSFER).first()
        val plnRow = transferRows.find { it.key == pln }
        assertNotNull(plnRow)
        assertEquals(300_00L, plnRow!!.money.amountMinor)

        val filterPln2 = TransactionFilter(accountIds = setOf(pln2))
        val transferRowsPln2 = analyticsRepo.observeByAccount(filterPln2, Measure.TRANSFER).first()
        assertTrue(transferRowsPln2.isEmpty())
    }

    @Test
    fun testBuckets() = runBlocking {
        val pln = account("PLN")
        val cat = category("c", CategoryType.EXPENSE)

        transaction(TransactionKind.EXPENSE, pln, 100_00L, cat, 1000L)
        transaction(TransactionKind.EXPENSE, pln, 200_00L, cat, 2000L)

        val buckets = listOf(
            Bucket(0, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 1), 500L, 1500L),
            Bucket(1, LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 2), 1500L, 2500L),
        )

        val bucketRows = analyticsRepo.observeByBucket(TransactionFilter(), Measure.EXPENSE, buckets).first()
        val b0 = bucketRows.find { it.key == 0L }
        val b1 = bucketRows.find { it.key == 1L }

        assertNotNull(b0)
        assertEquals(100_00L, b0!!.money.amountMinor)
        assertNotNull(b1)
        assertEquals(200_00L, b1!!.money.amountMinor)
    }

    @Test
    fun testAccountFiguresAndReconciles() = runBlocking {
        val pln = account("PLN")
        val catEx = category("ex", CategoryType.EXPENSE)
        val catIn = category("in", CategoryType.INCOME)

        opRepo.setOpeningBalance(pln, 1000_00L, 0L)

        val period = ResolvedPeriod(
            Period(PeriodKind.CUSTOM, LocalDate.of(2026, 11, 1)),
            LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 5),
            500L, 2500L
        )

        transaction(TransactionKind.INCOME, pln, 500_00L, catIn, 1000L)
        transaction(TransactionKind.EXPENSE, pln, 200_00L, catEx, 1500L)
        transaction(TransactionKind.EXPENSE, pln, 100_00L, catEx, 3000L)

        val figures = analyticsRepo.observeAccountFigures(pln, period).first()
        assertNotNull(figures)
        assertEquals(500_00L, figures!!.income.amountMinor)
        assertEquals(200_00L, figures.expense.amountMinor)
        assertEquals(1000_00L, figures.balanceAtStart.amountMinor)
        assertEquals(1300_00L, figures.balanceAtEnd.amountMinor)
        assertTrue(figures.reconciles())
    }

    @Test
    fun testLiveUpdates() = runBlocking {
        val pln = account("PLN")
        val cat = category("c", CategoryType.EXPENSE)

        val flow = analyticsRepo.observeByCategory(TransactionFilter(), Measure.EXPENSE)
        assertTrue(flow.first().isEmpty())

        val txId = transaction(TransactionKind.EXPENSE, pln, 100_00L, cat, 1000L)
        val list1 = flow.first()
        assertEquals(1, list1.size)
        assertEquals(100_00L, list1.first().money.amountMinor)

        transactions.delete(txId)
        val list2 = flow.first()
        assertTrue(list2.isEmpty())
    }
}
