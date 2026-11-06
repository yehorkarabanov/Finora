package com.yehor.finora

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.yehor.finora.data.db.FinoraDatabase
import com.yehor.finora.data.db.FinoraDatabaseCallback
import com.yehor.finora.data.db.InvariantChecker
import com.yehor.finora.data.repo.AccountRepository
import com.yehor.finora.data.repo.EntryDraft
import com.yehor.finora.data.repo.OperationRepository
import com.yehor.finora.data.repo.SettingsRepository
import com.yehor.finora.domain.account.Balance
import com.yehor.finora.domain.model.AccountStatus
import com.yehor.finora.domain.model.OperationType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AccountRepositoryTest {

    private lateinit var db: FinoraDatabase
    private lateinit var ops: OperationRepository
    private lateinit var repo: AccountRepository

    private val day = 24L * 60 * 60 * 1000
    private val now = 1_700_000_000_000L

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, FinoraDatabase::class.java)
            .addCallback(FinoraDatabaseCallback)
            .build()
        ops = OperationRepository(db)
        repo = AccountRepository(db, ops)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun create(
        currency: String = "USD",
        opening: Long? = null,
        name: String = "Acc",
    ): Long = repo.create(
        name = name, currencyCode = currency, type = "BANK", icon = null, color = null,
        description = null, openingMinor = opening, openingDate = null, now = now,
    )

    private fun violations() = InvariantChecker.check(db.openHelper.writableDatabase)

    private suspend fun balanceOf(id: Long): Balance =
        repo.observeAll().first().first { it.account.id == id }.balance

    private suspend fun minorOf(id: Long): Long = (balanceOf(id) as Balance.Value).money.amountMinor

    // ---- create ----

    @Test
    fun createWithoutOpeningBalanceCreatesNoOperation() = runBlocking {
        val id = create()
        assertNull(db.operationDao().openingBalanceOf(id))
        assertEquals(0L, minorOf(id))
        assertEquals(now, repo.observeAll().first().single().account.createdAt)
    }

    @Test
    fun createWithPositiveAndNegativeOpeningBalance() = runBlocking {
        val a = create(opening = 12_345)
        val b = create(opening = -500, name = "Debt")
        assertEquals(12_345L, minorOf(a))
        assertEquals(-500L, minorOf(b))
        assertTrue(violations().toString(), violations().isEmpty())
    }

    @Test
    fun createDatesOpeningBalanceAtStartOfCreationDay() = runBlocking {
        val id = create(opening = 100)
        val openingAt = db.operationDao().openingBalanceOf(id)!!.occurredAt
        assertTrue(openingAt <= now)
        assertTrue(now - openingAt < day + 3_600_000)
        assertEquals(openingAt, db.ledgerEntryDao().entriesOf(db.operationDao().openingBalanceOf(id)!!.id).single().occurredAt)
    }

    @Test
    fun createWithZeroOpeningBalanceCreatesNoOperation() = runBlocking {
        val id = create(opening = 0)
        assertNull(db.operationDao().openingBalanceOf(id))
    }

    // ---- setOpeningBalance ----

    @Test
    fun setOpeningBalanceInsertChangeMoveAndRemove() = runBlocking {
        val id = create()
        ops.setOpeningBalance(id, 1_000, now - 3 * day, now)
        assertEquals(1_000L, minorOf(id))

        ops.setOpeningBalance(id, 2_500, now - 3 * day, now)
        assertEquals(2_500L, minorOf(id))

        ops.setOpeningBalance(id, 2_500, now - 5 * day, now)
        val op = db.operationDao().openingBalanceOf(id)!!
        assertEquals(now - 5 * day, op.occurredAt)
        assertEquals(now - 5 * day, db.ledgerEntryDao().entriesOf(op.id).single().occurredAt)

        val count = db.openHelper.writableDatabase
            .query("SELECT COUNT(*) FROM operation WHERE type = 'OPENING_BALANCE'")
            .use { it.moveToFirst(); it.getInt(0) }
        assertEquals(1, count)

        ops.setOpeningBalance(id, 0, now, now)
        assertNull(db.operationDao().openingBalanceOf(id))
        assertEquals(0L, minorOf(id))
        assertTrue(violations().isEmpty())
    }

    // ---- update / archive ----

    @Test
    fun updateLeavesCurrencyStatusAndCreatedAtUnchanged() = runBlocking {
        val id = create(currency = "EUR")
        repo.update(id, "New name", "CASH", "wallet", 0xFF112233L, "desc")
        val e = db.accountDao().getById(id)!!
        assertEquals("New name", e.name)
        assertEquals("CASH", e.type)
        assertEquals("EUR", e.currencyCode)
        assertEquals(AccountStatus.ACTIVE, e.status)
        assertEquals(now, e.createdAt)
    }

    @Test
    fun archiveAndUnarchiveKeepBalanceAndEntries() = runBlocking {
        val id = create(opening = 700)
        repo.setArchived(id, true)
        assertEquals(AccountStatus.ARCHIVED, db.accountDao().getById(id)!!.status)
        assertEquals(700L, minorOf(id))
        repo.setArchived(id, false)
        assertEquals(AccountStatus.ACTIVE, db.accountDao().getById(id)!!.status)
        assertEquals(700L, minorOf(id))
    }

    // ---- pickable ----

    @Test
    fun pickableFollowsArchivedOriginalRules() = runBlocking {
        val active = create(name = "active")
        val archivedA = create(name = "a")
        val archivedB = create(name = "b")
        repo.setArchived(archivedA, true)
        repo.setArchived(archivedB, true)

        assertEquals(listOf(active), repo.observePickable(null).first().map { it.account.id })

        val withOriginal = repo.observePickable(archivedA).first()
        assertEquals(listOf(active, archivedA), withOriginal.map { it.account.id })
        assertTrue(withOriginal.single { it.account.id == archivedA }.isArchivedOriginal)
        assertFalse(withOriginal.single { it.account.id == active }.isArchivedOriginal)

        assertEquals(listOf(active), repo.observePickable(active).first().map { it.account.id })
    }

    // ---- balance query ----

    @Test
    fun observeAllReturnsEmptyAndArchivedAccountsInIdOrder() = runBlocking {
        val a = create(name = "empty")
        val b = create(name = "neg", opening = -250)
        val c = create(name = "arch", opening = 40)
        repo.setArchived(c, true)
        val list = repo.observeAll().first()
        assertEquals(listOf(a, b, c), list.map { it.account.id })
        assertEquals(0L, (list[0].balance as Balance.Value).money.amountMinor)
        assertEquals(-250L, (list[1].balance as Balance.Value).money.amountMinor)
        assertEquals(40L, (list[2].balance as Balance.Value).money.amountMinor)
    }

    @Test
    fun balanceMatchesSumOfEntriesIncludingTransfers() = runBlocking {
        val a = create(opening = 1_000)
        val b = create()
        ops.insertOperation(
            OperationType.TRANSFER, now,
            listOf(EntryDraft(a, -300), EntryDraft(b, 300)), now = now,
        )
        ops.insertOperation(OperationType.EXPENSE, now, listOf(EntryDraft(a, -50)), now = now)
        assertEquals(650L, minorOf(a))
        assertEquals(300L, minorOf(b))
    }

    @Test
    fun overflowMarksOnlyTheFailingAccount() = runBlocking {
        val bad = create(name = "bad")
        val good = create(name = "good", opening = 5)
        val badDay = now - day
        ops.insertOperation(OperationType.INCOME, badDay, listOf(EntryDraft(bad, Long.MAX_VALUE)), now = now)
        // insertOperation refuses to overflow, so the overflowing row is written directly.
        val raw = db.openHelper.writableDatabase
        raw.execSQL(
            "INSERT INTO operation (type, occurred_at, created_at, updated_at) VALUES ('INCOME', $badDay, $now, $now)",
        )
        raw.execSQL(
            "INSERT INTO ledger_entry (operation_id, account_id, currency_code, amount_minor, occurred_at) " +
                "VALUES (last_insert_rowid(), $bad, 'USD', 1, $badDay)",
        )
        val list = repo.observeAll().first()
        assertEquals(Balance.Overflow, list.single { it.account.id == bad }.balance)
        assertEquals(5L, ((list.single { it.account.id == good }.balance) as Balance.Value).money.amountMinor)
    }

    @Test
    fun balanceQueryUsesCoveringIndexOnLedgerEntry() {
        val plan = mutableListOf<String>()
        db.openHelper.writableDatabase.query(
            """
            EXPLAIN QUERY PLAN
            SELECT a.*, COALESCE(SUM(e.amount_minor), 0) AS balance_minor
            FROM account a
            LEFT JOIN ledger_entry e ON e.account_id = a.id
            GROUP BY a.id
            ORDER BY a.id
            """.trimIndent(),
        ).use { c ->
            while (c.moveToNext()) plan += c.getString(c.getColumnIndexOrThrow("detail"))
        }
        val ledger = plan.filter { it.contains(" e ") || it.endsWith(" e") || it.contains("ledger_entry") }
        assertTrue(plan.toString(), ledger.isNotEmpty())
        assertTrue(plan.toString(), ledger.all { it.contains("SEARCH") && it.contains("COVERING INDEX") })
    }

    // ---- delete ----

    @Test
    fun deleteRemovesAccountAndItsOpeningBalance() = runBlocking {
        val withOpening = create(opening = 900)
        val empty = create(name = "empty")
        repo.delete(withOpening)
        repo.delete(empty)
        assertNull(db.accountDao().getById(withOpening))
        assertNull(db.accountDao().getById(empty))
        val ledgerRows = db.openHelper.writableDatabase.query("SELECT COUNT(*) FROM ledger_entry")
            .use { it.moveToFirst(); it.getInt(0) }
        val opRows = db.openHelper.writableDatabase.query("SELECT COUNT(*) FROM operation")
            .use { it.moveToFirst(); it.getInt(0) }
        assertEquals(0, ledgerRows)
        assertEquals(0, opRows)
    }

    @Test
    fun deleteWithHistoryRemovesTransactionsIncludingTransfers() = runBlocking {
        val a = create(opening = 1_000)
        val b = create(opening = 500, name = "b")
        ops.insertOperation(OperationType.EXPENSE, now, listOf(EntryDraft(a, -10)), now = now)
        ops.insertOperation(
            OperationType.TRANSFER, now,
            listOf(EntryDraft(a, -100), EntryDraft(b, 100)), now = now,
        )

        repo.delete(a)

        assertNull(db.accountDao().getById(a))
        assertNotNull(db.accountDao().getById(b))
        // The transfer is gone as a whole, so b is back to its opening balance.
        assertEquals(500L, minorOf(b))
        val opRows = db.openHelper.writableDatabase.query("SELECT COUNT(*) FROM operation")
            .use { it.moveToFirst(); it.getInt(0) }
        assertEquals(1, opRows)
        assertTrue(violations().toString(), violations().isEmpty())
    }

    // ---- display currency ----

    @Test
    fun firstAccountSwitchesAutomaticDisplayCurrency() = runBlocking {
        val settings = SettingsRepository(db.appSettingDao(), db.accountDao())
        create(currency = "JPY")
        val resolved = settings.displayCurrency.first()
        assertEquals("JPY", resolved.currency.code)
        assertTrue(resolved.isAutomatic)
    }
}
