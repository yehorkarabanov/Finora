package com.yehor.finora

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.yehor.finora.data.db.AccountEntity
import com.yehor.finora.data.db.CategoryEntity
import com.yehor.finora.data.db.FinoraDatabase
import com.yehor.finora.data.db.FinoraDatabaseCallback
import com.yehor.finora.data.db.InvariantChecker
import com.yehor.finora.data.db.LedgerEntryEntity
import com.yehor.finora.data.db.OperationEntity
import com.yehor.finora.data.repo.AccountRepository
import com.yehor.finora.data.repo.CategoryRepository
import com.yehor.finora.data.repo.CurrencyChangeNotConfirmedException
import com.yehor.finora.data.repo.FutureDateException
import com.yehor.finora.data.repo.InvariantViolationException
import com.yehor.finora.data.repo.OpeningBalanceOrderException
import com.yehor.finora.data.repo.OperationRepository
import com.yehor.finora.data.repo.TransactionRepository
import com.yehor.finora.data.repo.TransferRepository
import com.yehor.finora.domain.model.AccountStatus
import com.yehor.finora.domain.model.CategoryType
import com.yehor.finora.domain.model.OperationType
import com.yehor.finora.domain.operation.TransactionFilter
import com.yehor.finora.domain.operation.TransferDraft
import com.yehor.finora.domain.account.OpeningDatePolicy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.time.ZoneId

class TransferRepositoryTest {

    private lateinit var db: FinoraDatabase
    private lateinit var opRepo: OperationRepository
    private lateinit var transfers: TransferRepository
    private lateinit var transactions: TransactionRepository

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, FinoraDatabase::class.java)
            .addCallback(FinoraDatabaseCallback)
            .build()
        opRepo = OperationRepository(db)
        val accounts = AccountRepository(db, opRepo)
        val categories = CategoryRepository(db)
        transfers = TransferRepository(db, opRepo, accounts, categories)
        transactions = TransactionRepository(db, opRepo, accounts, categories)
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

    private fun draft(
        source: Long,
        dest: Long,
        category: Long?,
        sent: Long = 10_000L,
        received: Long = 10_000L,
        occurredAt: Long = 1_000L,
        tagIds: Set<Long> = emptySet(),
        newTagNames: List<String> = emptyList(),
    ) = TransferDraft(
        sourceAccountId = source,
        destAccountId = dest,
        sentAmountMinor = sent,
        receivedAmountMinor = received,
        categoryId = category,
        occurredAt = occurredAt,
        description = null,
        tagIds = tagIds,
        newTagNames = newTagNames,
    )

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
    fun sameCurrencyEqualAmounts() = runBlocking {
        val a = account("PLN")
        val b = account("PLN")
        val cat = category(CategoryType.TRANSFER)

        val id = transfers.create(draft(a, b, cat, sent = 10_000L, received = 10_000L))

        assertEquals(-10_000L, opRepo.balanceMinor(a))
        assertEquals(10_000L, opRepo.balanceMinor(b))
        val item = transfers.observeTransfer(id).first()
        assertNotNull(item)
        assertEquals(a, item!!.sourceAccount.id)
        assertEquals(b, item.destAccount.id)
        assertEquals(10_000L, item.sentMoney.amountMinor)
        assertEquals(10_000L, item.receivedMoney.amountMinor)
        assertTrue(violations().isEmpty())
    }

    @Test
    fun sameCurrencyDifferingAmountsAreStoredIndependently() = runBlocking {
        val a = account("PLN")
        val b = account("PLN")
        val cat = category(CategoryType.TRANSFER)

        val id = transfers.create(draft(a, b, cat, sent = 10_000L, received = 9_500L))

        assertEquals(-10_000L, opRepo.balanceMinor(a))
        assertEquals(9_500L, opRepo.balanceMinor(b))
        val item = transfers.observeTransfer(id).first()!!
        assertEquals(10_000L, item.sentMoney.amountMinor)
        assertEquals(9_500L, item.receivedMoney.amountMinor)
        assertTrue(violations().isEmpty())
    }

    @Test
    fun crossCurrencyTransfer() = runBlocking {
        val pln = account("PLN")
        val usd = account("USD")
        val cat = category(CategoryType.TRANSFER)

        val id = transfers.create(draft(pln, usd, cat, sent = 100_000L, received = 25_000L))

        assertEquals(-100_000L, opRepo.balanceMinor(pln))
        assertEquals(25_000L, opRepo.balanceMinor(usd))
        val item = transfers.observeTransfer(id).first()!!
        assertEquals("PLN", item.sentMoney.currency.code)
        assertEquals("USD", item.receivedMoney.currency.code)
        assertTrue(violations().isEmpty())
    }

    @Test
    fun sameAccountIsRejected() = runBlocking {
        val a = account("PLN")
        val cat = category(CategoryType.TRANSFER)

        assertFails<InvariantViolationException> {
            runBlocking { transfers.create(draft(a, a, cat)) }
        }
        assertEquals(0L, opRepo.balanceMinor(a))
    }

    @Test
    fun wrongCategoryTypeIsRejected() = runBlocking {
        val a = account("PLN")
        val b = account("PLN")
        val expenseCat = category(CategoryType.EXPENSE)

        assertFails<InvariantViolationException> {
            runBlocking { transfers.create(draft(a, b, expenseCat)) }
        }
        assertEquals(0L, opRepo.balanceMinor(a))
        assertEquals(0L, opRepo.balanceMinor(b))
    }

    @Test
    fun missingCategoryIsRejected() = runBlocking {
        val a = account("PLN")
        val b = account("PLN")

        assertFails<InvariantViolationException> {
            runBlocking { transfers.create(draft(a, b, null)) }
        }
    }

    @Test
    fun futureDateIsRefused() = runBlocking {
        val a = account("PLN")
        val b = account("PLN")
        val cat = category(CategoryType.TRANSFER)
        val future = System.currentTimeMillis() + 24L * 60 * 60 * 1000

        assertFails<FutureDateException> {
            runBlocking { transfers.create(draft(a, b, cat, occurredAt = future)) }
        }
        assertEquals(0L, opRepo.balanceMinor(a))
        assertEquals(0L, opRepo.balanceMinor(b))
    }

    @Test
    fun updateChangesAmountsDateCategoryAndTags() = runBlocking {
        val a = account("PLN")
        val b = account("PLN")
        val cat1 = category(CategoryType.TRANSFER)
        val cat2 = category(CategoryType.TRANSFER)
        val id = transfers.create(draft(a, b, cat1, newTagNames = listOf("old")))

        transfers.update(
            id,
            draft(a, b, cat2, sent = 20_000L, received = 19_000L, occurredAt = 2_000L, newTagNames = listOf("new")),
        )

        assertEquals(-20_000L, opRepo.balanceMinor(a))
        assertEquals(19_000L, opRepo.balanceMinor(b))
        val item = transfers.observeTransfer(id).first()!!
        assertEquals(2_000L, item.occurredAt)
        assertEquals(cat2, item.category?.id)
        assertEquals(listOf("new"), item.tags.map { it.name })
        assertTrue(violations().isEmpty())
    }

    @Test
    fun updateCanSwapDirection() = runBlocking {
        val a = account("PLN")
        val b = account("PLN")
        val cat = category(CategoryType.TRANSFER)
        val id = transfers.create(draft(a, b, cat, sent = 10_000L, received = 9_000L))

        transfers.update(id, draft(b, a, cat, sent = 9_000L, received = 10_000L))

        assertEquals(10_000L, opRepo.balanceMinor(a))
        assertEquals(-9_000L, opRepo.balanceMinor(b))
        assertTrue(violations().isEmpty())
    }

    @Test
    fun updateCurrencyChangeNeedsConfirmation() = runBlocking {
        val pln = account("PLN")
        val usd = account("USD")
        val eur = account("EUR")
        val cat = category(CategoryType.TRANSFER)
        val id = transfers.create(draft(pln, usd, cat, sent = 100_000L, received = 25_000L))

        assertFails<CurrencyChangeNotConfirmedException> {
            runBlocking { transfers.update(id, draft(pln, eur, cat, sent = 100_000L, received = 23_000L)) }
        }
        // Nothing changed after the refusal.
        assertEquals(25_000L, opRepo.balanceMinor(usd))
        assertEquals(0L, opRepo.balanceMinor(eur))

        transfers.update(
            id,
            draft(pln, eur, cat, sent = 100_000L, received = 23_000L),
            confirmCurrencyChange = true,
        )
        assertEquals(0L, opRepo.balanceMinor(usd))
        assertEquals(23_000L, opRepo.balanceMinor(eur))
        assertTrue(violations().isEmpty())
    }

    @Test
    fun deleteRevertsBothBalances() = runBlocking {
        val a = account("PLN")
        val b = account("USD")
        val cat = category(CategoryType.TRANSFER)
        val id = transfers.create(draft(a, b, cat, newTagNames = listOf("t")))

        transfers.delete(id)

        assertEquals(0L, opRepo.balanceMinor(a))
        assertEquals(0L, opRepo.balanceMinor(b))
        assertNull(transfers.observeTransfer(id).first())
        assertTrue(violations().isEmpty())
    }

    @Test
    fun deleteRejectsNonTransfer() = runBlocking {
        val a = account("PLN")
        val incomeCat = category(CategoryType.INCOME)
        val incomeId = opRepo.insertOperation(
            type = OperationType.INCOME,
            occurredAt = 1_000L,
            entries = listOf(com.yehor.finora.data.repo.EntryDraft(a, 100L)),
            categoryId = incomeCat,
        )

        assertFails<InvariantViolationException> {
            runBlocking { transfers.delete(incomeId) }
        }
        assertEquals(100L, opRepo.balanceMinor(a))
    }

    @Test
    fun openingBalanceOrderIsCheckedOnBothAccounts() = runBlocking {
        val a = account("PLN")
        val b = account("PLN")
        val cat = category(CategoryType.TRANSFER)
        opRepo.setOpeningBalance(a, 50_000L, 1_000L)
        opRepo.setOpeningBalance(b, 50_000L, 2_000L)

        // 1500 is fine for a (opening at 1000) but before b's opening balance (2000).
        assertFails<OpeningBalanceOrderException> {
            runBlocking { transfers.create(draft(a, b, cat, occurredAt = 1_500L)) }
        }
        assertEquals(50_000L, opRepo.balanceMinor(a))
        assertEquals(50_000L, opRepo.balanceMinor(b))
    }

    @Test
    fun moveOpeningBalanceBackAppliesToBothAccounts() = runBlocking {
        val a = account("PLN")
        val b = account("PLN")
        val cat = category(CategoryType.TRANSFER)
        opRepo.setOpeningBalance(a, 50_000L, 1_000L)
        opRepo.setOpeningBalance(b, 50_000L, 2_000L)

        transfers.create(draft(a, b, cat, occurredAt = 500L), moveOpeningBalanceBack = true)

        val expected = OpeningDatePolicy(ZoneId.systemDefault()).startOfDay(500L)
        assertEquals(expected, db.operationDao().openingBalanceOf(b)?.occurredAt)
        assertEquals(40_000L, opRepo.balanceMinor(a))
        assertEquals(60_000L, opRepo.balanceMinor(b))
        assertTrue(violations().isEmpty())
    }

    @Test
    fun archivedAccountRefusedForNewTransfer() = runBlocking {
        val active = account("PLN")
        val archived = account("PLN", AccountStatus.ARCHIVED)
        val cat = category(CategoryType.TRANSFER)

        assertFails<InvariantViolationException> {
            runBlocking { transfers.create(draft(active, archived, cat)) }
        }
        assertFails<InvariantViolationException> {
            runBlocking { transfers.create(draft(archived, active, cat)) }
        }
    }

    @Test
    fun editKeepsOwnArchivedAccountButCannotMoveToAnotherArchived() = runBlocking {
        val a = account("PLN")
        val b = account("PLN")
        val otherArchived = account("PLN")
        val cat = category(CategoryType.TRANSFER)
        val id = transfers.create(draft(a, b, cat))
        db.accountDao().setStatus(b, AccountStatus.ARCHIVED)
        db.accountDao().setStatus(otherArchived, AccountStatus.ARCHIVED)

        // Destination stays on its own archived account: allowed.
        transfers.update(id, draft(a, b, cat, sent = 12_000L, received = 12_000L))
        assertEquals(12_000L, opRepo.balanceMinor(b))

        // Destination moves to a different archived account: refused.
        assertFails<InvariantViolationException> {
            runBlocking { transfers.update(id, draft(a, otherArchived, cat)) }
        }
        assertEquals(12_000L, opRepo.balanceMinor(b))
        assertEquals(0L, opRepo.balanceMinor(otherArchived))
        assertTrue(violations().isEmpty())
    }

    @Test
    fun historyShowsTransferAndSummaryExcludesIt() = runBlocking {
        val a = account("PLN")
        val b = account("PLN")
        val transferCat = category(CategoryType.TRANSFER)
        val expenseCat = category(CategoryType.EXPENSE)
        transfers.create(draft(a, b, transferCat, sent = 10_000L, received = 9_500L))
        opRepo.insertOperation(
            type = OperationType.EXPENSE,
            occurredAt = 1_000L,
            entries = listOf(com.yehor.finora.data.repo.EntryDraft(a, -300L)),
            categoryId = expenseCat,
        )

        val all = transactions.observeRecent(TransactionFilter(), 10).first()
        assertEquals(2, all.size)
        val transferOnly = transactions
            .observeRecent(TransactionFilter(types = setOf(OperationType.TRANSFER)), 10)
            .first()
        assertEquals(1, transferOnly.size)
        assertEquals(2, transferOnly.single().entries.size)

        val summaries = transactions.summarize(TransactionFilter())
        assertEquals(1, summaries.size)
        assertEquals(0L, summaries.single().income.amountMinor)
        assertEquals(300L, summaries.single().expense.amountMinor)
        assertEquals(1, summaries.single().operationCount)
    }

    @Test
    fun invariantCheckerFlagsTransferOnSameAccount() = runBlocking {
        val a = account("PLN")
        val cat = category(CategoryType.TRANSFER)
        rawTransfer(cat, listOf(a to -100L, a to 100L))

        assertTrue(violations().any { it.startsWith("transfer shape") })
    }

    @Test
    fun invariantCheckerFlagsTransferWithWrongEntryCount() = runBlocking {
        val a = account("PLN")
        val cat = category(CategoryType.TRANSFER)
        rawTransfer(cat, listOf(a to -100L))

        assertTrue(violations().any { it.startsWith("transfer shape") })
    }

    @Test
    fun invariantCheckerFlagsTransferWithBothEntriesPositive() = runBlocking {
        val a = account("PLN")
        val b = account("PLN")
        val cat = category(CategoryType.TRANSFER)
        rawTransfer(cat, listOf(a to 100L, b to 100L))

        assertTrue(violations().any { it.startsWith("transfer entries have invalid signs") })
    }

    @Test
    fun invariantCheckerFlagsTransferWithWrongCategoryType() = runBlocking {
        val a = account("PLN")
        val b = account("PLN")
        val expenseCat = category(CategoryType.EXPENSE)
        rawTransfer(expenseCat, listOf(a to -100L, b to 100L))

        assertTrue(violations().any { it.startsWith("category type mismatch") })
    }

    private suspend fun rawTransfer(categoryId: Long, entries: List<Pair<Long, Long>>) {
        val opId = db.operationDao().insert(
            OperationEntity(
                type = OperationType.TRANSFER, occurredAt = 1_000L, categoryId = categoryId,
                description = null, openingAccountId = null, createdAt = 1, updatedAt = 1,
            ),
        )
        entries.forEach { (accountId, amount) ->
            db.ledgerEntryDao().insert(
                LedgerEntryEntity(
                    operationId = opId, accountId = accountId, currencyCode = "PLN",
                    amountMinor = amount, occurredAt = 1_000L,
                ),
            )
        }
    }
}
