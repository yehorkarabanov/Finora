package com.yehor.finora

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.yehor.finora.data.db.AccountEntity
import com.yehor.finora.data.db.CategoryEntity
import com.yehor.finora.data.db.FinoraDatabase
import com.yehor.finora.data.db.FinoraDatabaseCallback
import com.yehor.finora.data.db.InvariantChecker
import com.yehor.finora.data.repo.AccountRepository
import com.yehor.finora.data.repo.CategoryRepository
import com.yehor.finora.data.repo.OperationRepository
import com.yehor.finora.data.repo.TransactionRepository
import com.yehor.finora.domain.model.AccountStatus
import com.yehor.finora.domain.model.CategoryType
import com.yehor.finora.domain.operation.TransactionDraft
import com.yehor.finora.domain.operation.TransactionFilter
import com.yehor.finora.domain.operation.TransactionKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TransactionRepositoryTest {

    private lateinit var db: FinoraDatabase
    private lateinit var transRepo: TransactionRepository

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, FinoraDatabase::class.java)
            .addCallback(FinoraDatabaseCallback)
            .build()
        val opRepo = OperationRepository(db)
        transRepo = TransactionRepository(db, opRepo, AccountRepository(db, opRepo), CategoryRepository(db))
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun createAndObserveRecentAndSummarize() = runBlocking {
        val accId = db.accountDao().insert(
            AccountEntity(name = "Santander", currencyCode = "PLN", type = "BANK", icon = null, color = null, description = null, status = AccountStatus.ACTIVE, createdAt = 0L)
        )
        val catId = db.categoryDao().insert(
            CategoryEntity(name = "Groceries", icon = null, color = null, type = CategoryType.EXPENSE, parentId = null, createdAt = 0L)
        )

        val draft = TransactionDraft(
            kind = TransactionKind.EXPENSE,
            accountId = accId,
            amountMinor = 1250L,
            categoryId = catId,
            occurredAt = 1000L,
            description = "Supermarket",
            tagIds = emptySet(),
            newTagNames = listOf("Debt"),
        )
        val id = transRepo.create(draft)
        assertTrue(id > 0)

        val recent = transRepo.observeRecent(TransactionFilter(), 10).first()
        assertEquals(1, recent.size)
        val item = recent.first()
        assertEquals("Supermarket", item.description)
        assertEquals("Groceries", item.category?.name)
        assertEquals(1, item.tags.size)
        assertEquals("Debt", item.tags.first().name)

        val summaries = transRepo.summarize(TransactionFilter())
        assertEquals(1, summaries.size)
        val s = summaries.first()
        assertEquals("PLN", s.currency.code)
        assertEquals(1250L, s.expense.amountMinor)
        assertEquals(0L, s.income.amountMinor)
        assertEquals(1, s.operationCount)

        assertTrue(InvariantChecker.check(db.openHelper.writableDatabase).isEmpty())
    }
}
