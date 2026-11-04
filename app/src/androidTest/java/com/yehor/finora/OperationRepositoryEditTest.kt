package com.yehor.finora

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.yehor.finora.data.db.AccountEntity
import com.yehor.finora.data.db.CategoryEntity
import com.yehor.finora.data.db.FinoraDatabase
import com.yehor.finora.data.db.FinoraDatabaseCallback
import com.yehor.finora.data.db.InvariantChecker
import com.yehor.finora.data.db.TagEntity
import com.yehor.finora.data.repo.CurrencyChangeNotConfirmedException
import com.yehor.finora.data.repo.EntryDraft
import com.yehor.finora.data.repo.FutureDateException
import com.yehor.finora.data.repo.OperationRepository
import com.yehor.finora.domain.model.AccountStatus
import com.yehor.finora.domain.model.CategoryType
import com.yehor.finora.domain.model.OperationType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class OperationRepositoryEditTest {

    private lateinit var db: FinoraDatabase
    private lateinit var repo: OperationRepository

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, FinoraDatabase::class.java)
            .addCallback(FinoraDatabaseCallback)
            .build()
        repo = OperationRepository(db)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun account(code: String, status: AccountStatus = AccountStatus.ACTIVE): Long =
        db.accountDao().insert(
            AccountEntity(name = "Account $code", currencyCode = code, type = "CASH", icon = null, color = null, description = null, status = status, createdAt = 0)
        )

    private suspend fun category(type: CategoryType): Long =
        db.categoryDao().insert(CategoryEntity(name = "cat", icon = null, color = null, type = type, parentId = null, createdAt = 0))

    private inline fun <reified T : Throwable> assertFails(block: () -> Unit) {
        try {
            block()
        } catch (e: Throwable) {
            if (e is T) return
            throw e
        }
        fail("expected ${T::class.simpleName}")
    }

    @Test
    fun createWithTagsAndInlineTags() = runBlocking {
        val accId = account("USD")
        val catId = category(CategoryType.EXPENSE)
        val tagId = db.tagDao().insert(TagEntity(name = "Existing", color = null, createdAt = 0L))

        val opId = repo.insertOperation(
            type = OperationType.EXPENSE,
            occurredAt = 1000L,
            entries = listOf(EntryDraft(accId, -500L)),
            categoryId = catId,
            tagIds = setOf(tagId),
            newTagNames = listOf("New Tag"),
            now = 2000L,
        )

        val tags = db.operationTagDao().tagIdsOf(opId)
        assertEquals(2, tags.size)
        assertTrue(InvariantChecker.check(db.openHelper.writableDatabase).isEmpty())
    }

    @Test
    fun futureDateRejected() = runBlocking {
        val accId = account("USD")
        val catId = category(CategoryType.INCOME)
        val now = 2000L

        assertFails<FutureDateException> {
            runBlocking {
                repo.insertOperation(
                    type = OperationType.INCOME,
                    occurredAt = 2001L,
                    entries = listOf(EntryDraft(accId, 500L)),
                    categoryId = catId,
                    now = now,
                )
            }
        }
    }

    @Test
    fun crossCurrencyReassignmentT12() = runBlocking {
        val plnAcc = account("PLN")
        val usdAcc = account("USD")
        val catId = category(CategoryType.EXPENSE)

        val opId = repo.insertOperation(
            type = OperationType.EXPENSE,
            occurredAt = 1000L,
            entries = listOf(EntryDraft(plnAcc, -10000L)), // -100.00 PLN
            categoryId = catId,
            now = 1000L,
        )
        assertEquals(-10000L, repo.balanceMinor(plnAcc))

        // Moving without confirmation throws
        assertFails<CurrencyChangeNotConfirmedException> {
            runBlocking {
                repo.updateOperation(
                    id = opId,
                    accountId = usdAcc,
                    amountMinor = 10000L,
                    categoryId = catId,
                    occurredAt = 1000L,
                    description = null,
                    confirmCurrencyChange = false,
                    now = 2000L,
                )
            }
        }

        // Moving with confirmation succeeds and retains nominal magnitude 10000
        repo.updateOperation(
            id = opId,
            accountId = usdAcc,
            amountMinor = 10000L,
            categoryId = catId,
            occurredAt = 1000L,
            description = null,
            confirmCurrencyChange = true,
            now = 2000L,
        )

        assertEquals(0L, repo.balanceMinor(plnAcc))
        assertEquals(-10000L, repo.balanceMinor(usdAcc))

        val entry = db.ledgerEntryDao().entriesOf(opId).single()
        assertEquals("USD", entry.currencyCode)
        assertEquals(-10000L, entry.amountMinor)
        assertEquals(usdAcc, entry.accountId)

        assertTrue(InvariantChecker.check(db.openHelper.writableDatabase).isEmpty())
    }

    @Test
    fun deleteOperationCascades() = runBlocking {
        val accId = account("USD")
        val catId = category(CategoryType.INCOME)
        val opId = repo.insertOperation(
            type = OperationType.INCOME,
            occurredAt = 1000L,
            entries = listOf(EntryDraft(accId, 1000L)),
            categoryId = catId,
            now = 1000L,
        )
        assertEquals(1000L, repo.balanceMinor(accId))

        repo.deleteOperation(opId)
        assertEquals(0L, repo.balanceMinor(accId))
        assertTrue(db.operationDao().getById(opId) == null)
        assertTrue(InvariantChecker.check(db.openHelper.writableDatabase).isEmpty())
    }
}
