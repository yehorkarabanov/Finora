package com.yehor.finora

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.yehor.finora.data.db.AccountEntity
import com.yehor.finora.data.db.CategoryEntity
import com.yehor.finora.data.db.FinoraDatabase
import com.yehor.finora.data.db.FinoraDatabaseCallback
import com.yehor.finora.data.db.InvariantChecker
import com.yehor.finora.data.repo.EntryDraft
import com.yehor.finora.data.repo.InvariantViolationException
import com.yehor.finora.data.repo.OpeningBalanceOrderException
import com.yehor.finora.data.repo.OperationRepository
import com.yehor.finora.domain.account.OpeningDatePolicy
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
import java.time.ZoneId

class OperationRepositoryOpeningBalanceTest {

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
            AccountEntity(
                name = "Account $code", currencyCode = code, type = "CASH", icon = null, color = null,
                description = null, status = status, createdAt = 0,
            ),
        )

    private suspend fun category(type: CategoryType): Long =
        db.categoryDao().insert(
            CategoryEntity(
                name = "c", icon = null, color = null, type = type, parentId = null, createdAt = 0,
            ),
        )

    private fun violations() = InvariantChecker.check(db.openHelper.writableDatabase)

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
    fun equalTimestampAllowed() = runBlocking {
        val accId = account("USD")
        val catId = category(CategoryType.EXPENSE)

        // Set opening balance at t = 1000
        repo.setOpeningBalance(accId, 10000L, 1000L)

        // Non-opening entry at the same timestamp t = 1000 is allowed
        repo.insertOperation(
            type = OperationType.EXPENSE,
            occurredAt = 1000L,
            entries = listOf(EntryDraft(accId, -100L)),
            categoryId = catId
        )

        assertEquals(9900L, repo.balanceMinor(accId))
        assertTrue(violations().isEmpty())
    }

    @Test
    fun entryBeforeOpeningBalanceThrows() = runBlocking {
        val accId = account("USD")
        val catId = category(CategoryType.EXPENSE)

        repo.setOpeningBalance(accId, 10000L, 1000L)

        // Entry at t = 999 is before opening balance at t = 1000 -> should throw
        assertFails<OpeningBalanceOrderException> {
            runBlocking {
                repo.insertOperation(
                    type = OperationType.EXPENSE,
                    occurredAt = 999L,
                    entries = listOf(EntryDraft(accId, -100L)),
                    categoryId = catId
                )
            }
        }
    }

    @Test
    fun settingOpeningBalanceAfterEntryThrows() = runBlocking {
        val accId = account("USD")
        val catId = category(CategoryType.EXPENSE)

        // First insert a normal entry at t = 1000 (without opening balance, which is allowed)
        repo.insertOperation(
            type = OperationType.EXPENSE,
            occurredAt = 1000L,
            entries = listOf(EntryDraft(accId, -100L)),
            categoryId = catId
        )

        // Try to set opening balance at t = 1001 (which is after the entry at 1000) -> should throw
        assertFails<OpeningBalanceOrderException> {
            runBlocking {
                repo.setOpeningBalance(accId, 10000L, 1001L)
            }
        }

        // Setting it at t = 1000 is allowed
        repo.setOpeningBalance(accId, 10000L, 1000L)
        assertEquals(9900L, repo.balanceMinor(accId))
        assertTrue(violations().isEmpty())
    }

    @Test
    fun transferCheckedPerSide() = runBlocking {
        val acc1 = account("USD")
        val acc2 = account("USD")

        repo.setOpeningBalance(acc1, 10000L, 1000L)
        repo.setOpeningBalance(acc2, 5000L, 2000L)

        // Transfer from acc1 to acc2 at t = 1500 is allowed for acc1 (1500 >= 1000) but invalid for acc2 (1500 < 2000) -> should throw
        assertFails<OpeningBalanceOrderException> {
            runBlocking {
                repo.insertOperation(
                    type = OperationType.TRANSFER,
                    occurredAt = 1500L,
                    entries = listOf(EntryDraft(acc1, -500L), EntryDraft(acc2, 500L))
                )
            }
        }
    }

    @Test
    fun moveOpeningBalanceBackWorks() = runBlocking {
        val accId = account("USD")
        val catId = category(CategoryType.EXPENSE)

        repo.setOpeningBalance(accId, 10000L, 1000L)

        // Insert entry at t = 500, with moveOpeningBalanceBack = true
        repo.insertOperation(
            type = OperationType.EXPENSE,
            occurredAt = 500L,
            entries = listOf(EntryDraft(accId, -100L)),
            categoryId = catId,
            moveOpeningBalanceBack = true
        )

        // The opening balance should be moved to the start of day of 500
        val expectedDate = OpeningDatePolicy(ZoneId.systemDefault()).startOfDay(500L)
        val openingOp = db.operationDao().openingBalanceOf(accId)
        assertEquals(expectedDate, openingOp?.occurredAt)

        assertEquals(9900L, repo.balanceMinor(accId))
        assertTrue(violations().isEmpty())
    }

    @Test
    fun archivedInsertThrows() = runBlocking {
        val activeAcc = account("USD", AccountStatus.ACTIVE)
        val archivedAcc = account("USD", AccountStatus.ARCHIVED)
        val incomeCat = category(CategoryType.INCOME)
        val expenseCat = category(CategoryType.EXPENSE)

        // 1. Income insert on archived throws
        assertFails<InvariantViolationException> {
            runBlocking {
                repo.insertOperation(
                    type = OperationType.INCOME,
                    occurredAt = 1000L,
                    entries = listOf(EntryDraft(archivedAcc, 100L)),
                    categoryId = incomeCat
                )
            }
        }

        // 2. Expense insert on archived throws
        assertFails<InvariantViolationException> {
            runBlocking {
                repo.insertOperation(
                    type = OperationType.EXPENSE,
                    occurredAt = 1000L,
                    entries = listOf(EntryDraft(archivedAcc, -100L)),
                    categoryId = expenseCat
                )
            }
        }

        // 3. Transfer from active to archived throws
        assertFails<InvariantViolationException> {
            runBlocking {
                repo.insertOperation(
                    type = OperationType.TRANSFER,
                    occurredAt = 1000L,
                    entries = listOf(EntryDraft(activeAcc, -100L), EntryDraft(archivedAcc, 100L))
                )
            }
        }

        // 4. New opening balance on archived throws
        assertFails<InvariantViolationException> {
            runBlocking {
                repo.setOpeningBalance(archivedAcc, 10000L, 1000L)
            }
        }
    }

    @Test
    fun archivedEditSucceeds() = runBlocking {
        val tagId = db.tagDao().insert(
            com.yehor.finora.data.db.TagEntity(
                name = "tag", color = null, createdAt = 0
            )
        )

        // Set up an existing opening balance first (while ACTIVE, or by bypassing checking using a direct query or mock, but wait, the setup can just insert directly into DB using DAO!)
        // "Operations already on an archived account stay fully editable: changeOccurredAt, addTag, setOpeningBalance changing or removing an existing opening balance"
        // Let's bypass validation to insert a test transaction/opening balance on the archived account or make it ACTIVE, insert, and then archive it!
        // That is brilliant!
        val accId = account("EUR", AccountStatus.ACTIVE)
        repo.setOpeningBalance(accId, 5000L, 1000L)
        val opId = repo.insertOperation(
            type = OperationType.INCOME,
            occurredAt = 2000L,
            entries = listOf(EntryDraft(accId, 100L)),
            categoryId = category(CategoryType.INCOME)
        )

        // Archive the account now
        db.accountDao().setStatus(accId, AccountStatus.ARCHIVED)

        // 1. Edit existing opening balance succeeds!
        repo.setOpeningBalance(accId, 6000L, 1000L)
        assertEquals(6100L, repo.balanceMinor(accId))

        // 2. changeOccurredAt succeeds!
        repo.changeOccurredAt(opId, 2500L)

        // 3. addTag succeeds!
        repo.addTag(opId, tagId)

        assertTrue(violations().isEmpty())
    }
}
