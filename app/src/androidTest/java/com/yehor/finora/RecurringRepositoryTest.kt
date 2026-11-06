package com.yehor.finora

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.yehor.finora.data.db.AccountEntity
import com.yehor.finora.data.db.CategoryEntity
import com.yehor.finora.data.db.FinoraDatabase
import com.yehor.finora.data.db.FinoraDatabaseCallback
import com.yehor.finora.data.db.InvariantChecker
import com.yehor.finora.data.db.TagEntity
import com.yehor.finora.data.repo.AccountRepository
import com.yehor.finora.data.repo.CategoryRepository
import com.yehor.finora.data.repo.CurrencyChangeNotConfirmedException
import com.yehor.finora.data.repo.InvariantViolationException
import com.yehor.finora.data.repo.OpeningBalanceOrderException
import com.yehor.finora.data.repo.OperationRepository
import com.yehor.finora.data.repo.ReassignTarget
import com.yehor.finora.data.repo.RecurringRepository
import com.yehor.finora.data.repo.TagRepository
import com.yehor.finora.data.repo.TransactionRepository
import com.yehor.finora.domain.model.AccountStatus
import com.yehor.finora.domain.model.CategoryType
import com.yehor.finora.domain.model.OperationType
import com.yehor.finora.domain.money.MoneyOverflowException
import com.yehor.finora.domain.operation.TransactionFilter
import com.yehor.finora.domain.operation.TransactionKind
import com.yehor.finora.domain.recurring.GenerationIssue
import com.yehor.finora.domain.recurring.OccurrenceStatus
import com.yehor.finora.domain.recurring.RecurrenceCalculator
import com.yehor.finora.domain.recurring.RecurrenceRule
import com.yehor.finora.domain.recurring.RecurrenceUnit
import com.yehor.finora.domain.recurring.ScheduleDraft
import com.yehor.finora.domain.recurring.ScheduleStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
import java.time.LocalDate
import java.time.ZoneId

class RecurringRepositoryTest {

    private lateinit var db: FinoraDatabase
    private lateinit var opRepo: OperationRepository
    private lateinit var accountRepo: AccountRepository
    private lateinit var categoryRepo: CategoryRepository
    private lateinit var tagRepo: TagRepository
    private lateinit var recurringRepo: RecurringRepository
    private lateinit var transactionRepo: TransactionRepository

    private var currentTime = 1_700_000_000_000L // arbitrary fixed epoch ms
    private val zone = ZoneId.of("UTC")

    private fun today(): LocalDate =
        java.time.Instant.ofEpochMilli(currentTime).atZone(zone).toLocalDate()

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, FinoraDatabase::class.java)
            .addCallback(FinoraDatabaseCallback)
            .build()
        opRepo = OperationRepository(db)
        accountRepo = AccountRepository(db, opRepo)
        categoryRepo = CategoryRepository(db)
        tagRepo = TagRepository(db)
        recurringRepo = RecurringRepository(
            db = db,
            operationRepo = opRepo,
            accountRepo = accountRepo,
            categoryRepo = categoryRepo,
            tagRepo = tagRepo,
            clock = { currentTime },
            zoneProvider = { zone },
        )
        transactionRepo = TransactionRepository(db, opRepo, accountRepo, categoryRepo)

        // Seed basic entities
        runBlocking {
            db.accountDao().insert(
                AccountEntity(
                    id = 1L,
                    name = "Checking",
                    currencyCode = "USD",
                    type = "CHECKING",
                    icon = null,
                    color = null,
                    description = null,
                    status = AccountStatus.ACTIVE,
                    createdAt = 0L,
                ),
            )
            db.accountDao().insert(
                AccountEntity(
                    id = 2L,
                    name = "EUR Acc",
                    currencyCode = "EUR",
                    type = "SAVINGS",
                    icon = null,
                    color = null,
                    description = null,
                    status = AccountStatus.ACTIVE,
                    createdAt = 0L,
                ),
            )
            db.categoryDao().insert(
                CategoryEntity(
                    id = 1L,
                    name = "Salary",
                    icon = null,
                    color = null,
                    type = CategoryType.INCOME,
                    parentId = null,
                    createdAt = 0L,
                ),
            )
            db.categoryDao().insert(
                CategoryEntity(
                    id = 2L,
                    name = "Groceries",
                    icon = null,
                    color = null,
                    type = CategoryType.EXPENSE,
                    parentId = null,
                    createdAt = 0L,
                ),
            )
            db.tagDao().insert(TagEntity(id = 1L, name = "work", color = null, createdAt = 0L))
        }
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun dailyScheduleStartingTodayGeneratesOneTransaction() = runBlocking {
        val draft = ScheduleDraft(
            name = "Coffee",
            kind = TransactionKind.EXPENSE,
            accountId = 1L,
            amountMinor = 450L,
            categoryId = 2L,
            tagIds = setOf(1L),
            newTagNames = emptyList(),
            rule = RecurrenceRule(RecurrenceUnit.DAY, 1),
            firstDate = today(),
            endDate = null,
        )

        val id = recurringRepo.create(draft, now = currentTime)
        val schedule = recurringRepo.observeSchedule(id).first()
        assertNotNull(schedule)
        assertEquals(ScheduleStatus.ACTIVE, schedule!!.status)
        assertEquals(today().plusDays(1), schedule.nextDate)

        // Check generated operation
        val ops = db.operationDao().observeAll().first()
        assertEquals(1, ops.size)
        val op = ops.first()
        assertEquals(OperationType.EXPENSE, op.type)
        assertEquals("Coffee", op.description)
        assertEquals(2L, op.categoryId)
        assertEquals(RecurrenceCalculator.startOfDay(today(), zone), op.occurredAt)
        assertEquals(currentTime, op.createdAt)
        assertEquals(currentTime, op.updatedAt)

        // Check balance
        assertEquals(-450L, opRepo.balanceMinor(1L))

        // Check occurrence row
        val occurrences = recurringRepo.observeOccurrences(id).first()
        assertEquals(1, occurrences.size)
        val occ = occurrences.first()
        assertEquals(today(), occ.date)
        assertEquals(OccurrenceStatus.GENERATED, occ.status)
        assertEquals(op.id, occ.operationId)

        // Check transaction item mapping
        val tx = transactionRepo.observeTransaction(op.id).first()
        assertNotNull(tx)
        assertEquals(id, tx!!.recurringScheduleId)

        assertEquals(emptyList<String>(), InvariantChecker.check(db.openHelper.readableDatabase))
    }

    @Test
    fun nothingIsCreatedForFutureDatesUntilMidnight() = runBlocking {
        val tomorrow = today().plusDays(1)
        val draft = ScheduleDraft(
            name = "Future Bill",
            kind = TransactionKind.EXPENSE,
            accountId = 1L,
            amountMinor = 1000L,
            categoryId = 2L,
            tagIds = emptySet(),
            newTagNames = emptyList(),
            rule = RecurrenceRule(RecurrenceUnit.DAY, 1),
            firstDate = tomorrow,
            endDate = null,
        )

        val id = recurringRepo.create(draft, now = currentTime)
        assertEquals(0, db.operationDao().observeAll().first().size)
        val schedule = recurringRepo.observeSchedule(id).first()
        assertEquals(tomorrow, schedule!!.nextDate)

        // Advance clock by 1 day
        currentTime += 24 * 60 * 60 * 1000L
        recurringRepo.generateAllDue(now = currentTime)

        val ops = db.operationDao().observeAll().first()
        assertEquals(1, ops.size)
        assertEquals(tomorrow.plusDays(1), recurringRepo.observeSchedule(id).first()!!.nextDate)
    }

    @Test
    fun catchUpGeneratesMissedOccurrencesInDateOrder() = runBlocking {
        val fiveDaysAgo = today().minusDays(5)
        val draft = ScheduleDraft(
            name = "Daily Sub",
            kind = TransactionKind.EXPENSE,
            accountId = 1L,
            amountMinor = 100L,
            categoryId = 2L,
            tagIds = emptySet(),
            newTagNames = emptyList(),
            rule = RecurrenceRule(RecurrenceUnit.DAY, 1),
            firstDate = fiveDaysAgo,
            endDate = null,
        )

        // 6 occurrences due: from fiveDaysAgo to today
        val id = recurringRepo.create(draft, now = currentTime)
        val ops = db.operationDao().observeAll().first().sortedBy { it.occurredAt }
        assertEquals(6, ops.size)

        for (i in 0..5) {
            val expectedDate = fiveDaysAgo.plusDays(i.toLong())
            assertEquals(RecurrenceCalculator.startOfDay(expectedDate, zone), ops[i].occurredAt)
        }

        assertEquals(today().plusDays(1), recurringRepo.observeSchedule(id).first()!!.nextDate)
        assertEquals(emptyList<String>(), InvariantChecker.check(db.openHelper.readableDatabase))
    }

    @Test
    fun idempotencyRunsDoNotDuplicateOccurrences() = runBlocking {
        val draft = ScheduleDraft(
            name = "Gym",
            kind = TransactionKind.EXPENSE,
            accountId = 1L,
            amountMinor = 3000L,
            categoryId = 2L,
            tagIds = emptySet(),
            newTagNames = emptyList(),
            rule = RecurrenceRule(RecurrenceUnit.MONTH, 1),
            firstDate = today(),
            endDate = null,
        )

        val id = recurringRepo.create(draft, now = currentTime)
        assertEquals(1, db.operationDao().observeAll().first().size)

        // Run generateAllDue again
        recurringRepo.generateAllDue(now = currentTime)
        assertEquals(1, db.operationDao().observeAll().first().size)

        // Multiple rapid calls
        repeat(5) {
            recurringRepo.generateAllDue(now = currentTime)
        }
        assertEquals(1, db.operationDao().observeAll().first().size)
    }

    @Test
    fun deletingGeneratedTransactionDoesNotRecreateIt() = runBlocking {
        val draft = ScheduleDraft(
            name = "Utility",
            kind = TransactionKind.EXPENSE,
            accountId = 1L,
            amountMinor = 5000L,
            categoryId = 2L,
            tagIds = emptySet(),
            newTagNames = emptyList(),
            rule = RecurrenceRule(RecurrenceUnit.DAY, 1),
            firstDate = today(),
            endDate = null,
        )

        val id = recurringRepo.create(draft, now = currentTime)
        val op = db.operationDao().observeAll().first().first()

        // Delete generated operation
        opRepo.deleteOperation(op.id)
        assertEquals(0, db.operationDao().observeAll().first().size)

        // Occurrence row still exists with operationId = null
        val occurrences = recurringRepo.observeOccurrences(id).first()
        assertEquals(1, occurrences.size)
        assertNull(occurrences.first().operationId)

        // Run generate again - must NOT recreate deleted occurrence
        recurringRepo.generateAllDue(now = currentTime)
        assertEquals(0, db.operationDao().observeAll().first().size)
    }

    @Test
    fun pauseAndResumeWithCatchUpOrSkip() = runBlocking {
        val draft = ScheduleDraft(
            name = "Weekly Magazine",
            kind = TransactionKind.EXPENSE,
            accountId = 1L,
            amountMinor = 500L,
            categoryId = 2L,
            tagIds = emptySet(),
            newTagNames = emptyList(),
            rule = RecurrenceRule(RecurrenceUnit.WEEK, 1),
            firstDate = today(),
            endDate = null,
        )

        val id = recurringRepo.create(draft, now = currentTime)
        assertEquals(1, db.operationDao().observeAll().first().size)

        // Pause
        recurringRepo.pause(id, now = currentTime)
        assertEquals(ScheduleStatus.PAUSED, recurringRepo.observeSchedule(id).first()!!.status)

        // Advance 3 weeks
        currentTime += 21 * 24 * 60 * 60 * 1000L
        recurringRepo.generateAllDue(now = currentTime)
        // Nothing created while paused
        assertEquals(1, db.operationDao().observeAll().first().size)

        // Resume with catchUp = false
        recurringRepo.resume(id, catchUp = false, now = currentTime)
        // No new operations created
        assertEquals(1, db.operationDao().observeAll().first().size)
        // Next date is now in the future
        val schedule = recurringRepo.observeSchedule(id).first()!!
        assertEquals(ScheduleStatus.ACTIVE, schedule.status)
        assertTrue(schedule.nextDate!! > today())

        // Check occurrences has skipped rows
        val occs = recurringRepo.observeOccurrences(id).first()
        assertTrue(occs.any { it.status == OccurrenceStatus.SKIPPED })
    }

    @Test
    fun skipNextCreatesNoTransactionAndAdvances() = runBlocking {
        val draft = ScheduleDraft(
            name = "Weekly Class",
            kind = TransactionKind.EXPENSE,
            accountId = 1L,
            amountMinor = 1500L,
            categoryId = 2L,
            tagIds = emptySet(),
            newTagNames = emptyList(),
            rule = RecurrenceRule(RecurrenceUnit.WEEK, 1),
            firstDate = today(),
            endDate = null,
        )

        val id = recurringRepo.create(draft, now = currentTime)
        assertEquals(1, db.operationDao().observeAll().first().size)

        val nextDateBefore = recurringRepo.observeSchedule(id).first()!!.nextDate!!
        recurringRepo.skipNext(id, now = currentTime)

        val nextDateAfter = recurringRepo.observeSchedule(id).first()!!.nextDate!!
        assertEquals(nextDateBefore.plusWeeks(1), nextDateAfter)

        val occs = recurringRepo.observeOccurrences(id).first()
        assertEquals(2, occs.size)
        assertEquals(OccurrenceStatus.SKIPPED, occs.first().status)
    }

    @Test
    fun endDateEndsScheduleAndExtendingRevivesIt() = runBlocking {
        val endDate = today().plusDays(2)
        val draft = ScheduleDraft(
            name = "Short Project",
            kind = TransactionKind.INCOME,
            accountId = 1L,
            amountMinor = 2000L,
            categoryId = 1L,
            tagIds = emptySet(),
            newTagNames = emptyList(),
            rule = RecurrenceRule(RecurrenceUnit.DAY, 1),
            firstDate = today(),
            endDate = endDate,
        )

        val id = recurringRepo.create(draft, now = currentTime)
        assertEquals(1, db.operationDao().observeAll().first().size)

        // Advance past end date
        currentTime += 5 * 24 * 60 * 60 * 1000L
        recurringRepo.generateAllDue(now = currentTime)

        // 3 operations generated (today, today+1, today+2)
        assertEquals(3, db.operationDao().observeAll().first().size)
        val schedule = recurringRepo.observeSchedule(id).first()!!
        assertNull(schedule.nextDate) // Ended

        // Extend end date
        val extendedDraft = draft.copy(
            firstDate = endDate.plusDays(1),
            endDate = today().plusDays(10),
        )
        recurringRepo.update(id, extendedDraft, now = currentTime)

        val revived = recurringRepo.observeSchedule(id).first()!!
        assertNotNull(revived.nextDate)
    }

    @Test
    fun archivedAccountBlocksGenerationAndReportsIssue() = runBlocking {
        accountRepo.setArchived(1L, true)

        val draft = ScheduleDraft(
            name = "Archived Bill",
            kind = TransactionKind.EXPENSE,
            accountId = 1L,
            amountMinor = 1000L,
            categoryId = 2L,
            tagIds = emptySet(),
            newTagNames = emptyList(),
            rule = RecurrenceRule(RecurrenceUnit.DAY, 1),
            firstDate = today(),
            endDate = null,
        )

        // Create on archived account is blocked during generation
        try {
            recurringRepo.create(draft, now = currentTime)
            fail("Expected exception for archived account")
        } catch (_: Exception) {
        }

        // Unarchive account
        accountRepo.setArchived(1L, false)
        val id = recurringRepo.create(draft, now = currentTime)
        assertEquals(1, db.operationDao().observeAll().first().size)

        // Now archive again and advance time
        accountRepo.setArchived(1L, true)
        currentTime += 24 * 60 * 60 * 1000L
        recurringRepo.generateAllDue(now = currentTime)

        // Issue reported
        assertEquals(GenerationIssue.ACCOUNT_ARCHIVED, recurringRepo.issues.value[id])
        assertEquals(1, db.operationDao().observeAll().first().size)

        // Unarchive account again and generate
        accountRepo.setArchived(1L, false)
        recurringRepo.generateAllDue(now = currentTime)
        assertEquals(2, db.operationDao().observeAll().first().size)
        assertNull(recurringRepo.issues.value[id])
    }

    @Test
    fun crossCurrencyAccountChangeRequiresConfirmation() = runBlocking {
        val draft = ScheduleDraft(
            name = "Currency Test",
            kind = TransactionKind.EXPENSE,
            accountId = 1L, // USD
            amountMinor = 1000L,
            categoryId = 2L,
            tagIds = emptySet(),
            newTagNames = emptyList(),
            rule = RecurrenceRule(RecurrenceUnit.MONTH, 1),
            firstDate = today(),
            endDate = null,
        )

        val id = recurringRepo.create(draft, now = currentTime)

        val changedDraft = draft.copy(
            firstDate = today().plusMonths(1),
            accountId = 2L, // EUR
        )

        try {
            recurringRepo.update(id, changedDraft, confirmCurrencyChange = false, now = currentTime)
            fail("Expected CurrencyChangeNotConfirmedException")
        } catch (_: CurrencyChangeNotConfirmedException) {
        }

        recurringRepo.update(id, changedDraft, confirmCurrencyChange = true, now = currentTime)
        val schedule = recurringRepo.observeSchedule(id).first()!!
        assertEquals(2L, schedule.account.id)
        assertEquals("EUR", schedule.account.currency.code)
    }

    @Test
    fun deleteScheduleRemovesScheduleAndOccurrencesKeepsOperations() = runBlocking {
        val draft = ScheduleDraft(
            name = "To Delete",
            kind = TransactionKind.EXPENSE,
            accountId = 1L,
            amountMinor = 1000L,
            categoryId = 2L,
            tagIds = setOf(1L),
            newTagNames = emptyList(),
            rule = RecurrenceRule(RecurrenceUnit.MONTH, 1),
            firstDate = today(),
            endDate = null,
        )

        val id = recurringRepo.create(draft, now = currentTime)
        val op = db.operationDao().observeAll().first().first()

        // Transaction has recurring link
        val txBefore = transactionRepo.observeTransaction(op.id).first()!!
        assertEquals(id, txBefore.recurringScheduleId)

        // Delete schedule
        recurringRepo.delete(id)
        assertNull(recurringRepo.observeSchedule(id).first())
        assertEquals(0, recurringRepo.observeOccurrences(id).first().size)

        // Operation and balance still exist
        assertEquals(1, db.operationDao().observeAll().first().size)
        assertEquals(-1000L, opRepo.balanceMinor(1L))

        // Recurring marker is removed
        val txAfter = transactionRepo.observeTransaction(op.id).first()!!
        assertNull(txAfter.recurringScheduleId)

        assertEquals(emptyList<String>(), InvariantChecker.check(db.openHelper.readableDatabase))
    }

    @Test
    fun deleteCategoryReassignsSchedules() = runBlocking {
        val draft = ScheduleDraft(
            name = "Cat Schedule",
            kind = TransactionKind.EXPENSE,
            accountId = 1L,
            amountMinor = 1000L,
            categoryId = 2L,
            tagIds = emptySet(),
            newTagNames = emptyList(),
            rule = RecurrenceRule(RecurrenceUnit.MONTH, 1),
            firstDate = today(),
            endDate = null,
        )

        val id = recurringRepo.create(draft, now = currentTime)

        // Check deletion impact
        val impact = categoryRepo.deletionImpact(2L)
        assertEquals(1, impact.scheduleCount)

        // Delete category to Uncategorized
        categoryRepo.delete(2L, ReassignTarget.Uncategorized)

        val schedule = recurringRepo.observeSchedule(id).first()!!
        assertNull(schedule.category)
    }

    // ---- additional coverage from design section 11 ----

    private val dayMs = 24L * 60 * 60 * 1000

    private fun draft(
        name: String = "Sched",
        kind: TransactionKind = TransactionKind.EXPENSE,
        accountId: Long = 1L,
        amountMinor: Long = 100L,
        categoryId: Long? = if (kind == TransactionKind.EXPENSE) 2L else 1L,
        rule: RecurrenceRule = RecurrenceRule(RecurrenceUnit.DAY, 1),
        firstDate: LocalDate = today(),
        endDate: LocalDate? = null,
    ) = ScheduleDraft(
        name = name,
        kind = kind,
        accountId = accountId,
        amountMinor = amountMinor,
        categoryId = categoryId,
        tagIds = emptySet(),
        newTagNames = emptyList(),
        rule = rule,
        firstDate = firstDate,
        endDate = endDate,
    )

    private fun count(sql: String): Int =
        db.openHelper.readableDatabase.query(sql).use {
            it.moveToFirst()
            it.getInt(0)
        }

    private fun snapshot(): List<String> =
        db.openHelper.readableDatabase.query(
            "SELECT 'op:' || id || '|' || type || '|' || occurred_at || '|' || IFNULL(category_id, '') || '|' || " +
                "IFNULL(description, '') || '|' || created_at || '|' || updated_at FROM operation " +
                "UNION ALL SELECT 'le:' || id || '|' || operation_id || '|' || account_id || '|' || currency_code || " +
                "'|' || amount_minor || '|' || occurred_at FROM ledger_entry ORDER BY 1",
        ).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }

    @Test
    fun twoHundredFiftyMissedDaysAreCreatedInBatchesWithoutDuplicates() = runBlocking {
        val start = today()
        val id = recurringRepo.create(draft(firstDate = start.plusDays(1)), now = currentTime)
        currentTime += 250 * dayMs
        recurringRepo.generateAllDue(now = currentTime)
        assertEquals(250, db.operationDao().observeAll().first().size)
        assertEquals(start.plusDays(251), recurringRepo.observeSchedule(id).first()!!.nextDate)

        recurringRepo.generateAllDue(now = currentTime)
        assertEquals(250, db.operationDao().observeAll().first().size)
        assertEquals(emptyList<String>(), InvariantChecker.check(db.openHelper.readableDatabase))
    }

    @Test
    fun concurrentGenerationCreatesEachOccurrenceOnce() = runBlocking {
        recurringRepo.create(draft(firstDate = today().plusDays(1)), now = currentTime)
        currentTime += 4 * dayMs
        val now = currentTime
        (1..20).map { async(Dispatchers.Default) { recurringRepo.generateAllDue(now = now) } }.awaitAll()
        assertEquals(4, db.operationDao().observeAll().first().size)
        assertEquals(4, count("SELECT COUNT(*) FROM recurring_occurrence"))
        assertEquals(emptyList<String>(), InvariantChecker.check(db.openHelper.readableDatabase))
    }

    @Test
    fun duplicateOccurrenceDateIsRejectedByUniqueIndex() = runBlocking {
        val id = recurringRepo.create(draft(), now = currentTime)
        try {
            db.openHelper.writableDatabase.execSQL(
                "INSERT INTO recurring_occurrence (schedule_id, occurrence_date, status, operation_id, handled_at) " +
                    "VALUES ($id, ${today().toEpochDay()}, 'SKIPPED', NULL, 1)",
            )
            fail("Expected unique index violation")
        } catch (_: SQLiteConstraintException) {
        }
    }

    @Test
    fun weeklyMonthlyAndCustomIntervalsAnchorWithoutDrift() = runBlocking {
        val jan31 = LocalDate.of(2026, 1, 31)
        currentTime = RecurrenceCalculator.startOfDay(LocalDate.of(2026, 5, 1), zone)
        val monthly = recurringRepo.create(
            draft(rule = RecurrenceRule(RecurrenceUnit.MONTH, 1), firstDate = jan31),
            now = currentTime,
        )
        // Jan 31, Feb 28, Mar 31, Apr 30 are due on May 1.
        assertEquals(4, count("SELECT COUNT(*) FROM recurring_occurrence WHERE schedule_id = $monthly"))
        assertEquals(LocalDate.of(2026, 5, 31), recurringRepo.observeSchedule(monthly).first()!!.nextDate)

        val custom = recurringRepo.create(
            draft(rule = RecurrenceRule(RecurrenceUnit.WEEK, 2), firstDate = LocalDate.of(2026, 4, 1)),
            now = currentTime,
        )
        // Apr 1, Apr 15, Apr 29 are due, the next one is May 13.
        assertEquals(3, count("SELECT COUNT(*) FROM recurring_occurrence WHERE schedule_id = $custom"))
        assertEquals(LocalDate.of(2026, 5, 13), recurringRepo.observeSchedule(custom).first()!!.nextDate)
    }

    @Test
    fun skipNextOnEndedScheduleAndPauseOnEndedScheduleAreRefused() = runBlocking {
        val id = recurringRepo.create(draft(endDate = today()), now = currentTime)
        assertNull(recurringRepo.observeSchedule(id).first()!!.nextDate)
        try {
            recurringRepo.skipNext(id, now = currentTime)
            fail("Expected refusal")
        } catch (_: InvariantViolationException) {
        }
        try {
            recurringRepo.pause(id, now = currentTime)
            fail("Expected refusal")
        } catch (_: InvariantViolationException) {
        }
    }

    @Test
    fun editingScheduleLeavesGeneratedHistoryUntouched() = runBlocking {
        val id = recurringRepo.create(draft(name = "Old", amountMinor = 100L), now = currentTime)
        val before = snapshot()

        recurringRepo.update(
            id,
            draft(name = "New", amountMinor = 999L, firstDate = today().plusDays(1)),
            now = currentTime,
        )
        assertEquals(before, snapshot())

        currentTime += dayMs
        recurringRepo.generateAllDue(now = currentTime)
        val ops = db.operationDao().observeAll().first().sortedBy { it.occurredAt }
        assertEquals(listOf("Old", "New"), ops.map { it.description })
        assertEquals(-999L, db.ledgerEntryDao().entriesOf(ops.last().id).single().amountMinor)
        assertEquals(emptyList<String>(), InvariantChecker.check(db.openHelper.readableDatabase))
    }

    @Test
    fun editingToDateAlreadyHandledIsRefused() = runBlocking {
        val id = recurringRepo.create(draft(), now = currentTime)
        try {
            recurringRepo.update(id, draft(firstDate = today()), now = currentTime)
            fail("Expected refusal")
        } catch (_: InvariantViolationException) {
        }
        assertEquals(1, count("SELECT COUNT(*) FROM operation"))
    }

    @Test
    fun createBeforeOpeningBalanceWritesNothingUntilConfirmed() = runBlocking {
        opRepo.setOpeningBalance(1L, 5000L, RecurrenceCalculator.startOfDay(today(), zone), now = currentTime)
        val opsBefore = count("SELECT COUNT(*) FROM operation")

        try {
            recurringRepo.create(draft(firstDate = today().minusDays(3)), now = currentTime)
            fail("Expected OpeningBalanceOrderException")
        } catch (_: OpeningBalanceOrderException) {
        }
        assertEquals(0, count("SELECT COUNT(*) FROM recurring_schedule"))
        assertEquals(opsBefore, count("SELECT COUNT(*) FROM operation"))

        recurringRepo.create(draft(firstDate = today().minusDays(3)), moveOpeningBalanceBack = true, now = currentTime)
        assertEquals(opsBefore + 4, count("SELECT COUNT(*) FROM operation"))
        val openingDate = db.ledgerEntryDao().openingDateOf(1L)!!
        assertTrue(openingDate <= RecurrenceCalculator.startOfDay(today().minusDays(3), zone))
        assertEquals(emptyList<String>(), InvariantChecker.check(db.openHelper.readableDatabase))
    }

    @Test
    fun backgroundRunNeverMovesOpeningBalanceAndSkipOrMoveBackClearsIssue() = runBlocking {
        val start = today()
        val id = recurringRepo.create(draft(firstDate = start.plusDays(1)), now = currentTime)
        val openingAt = RecurrenceCalculator.startOfDay(start.plusDays(5), zone)
        opRepo.setOpeningBalance(1L, 5000L, openingAt, now = currentTime)

        currentTime += dayMs
        recurringRepo.generateAllDue(now = currentTime)
        assertEquals(GenerationIssue.BEFORE_OPENING_BALANCE, recurringRepo.issues.value[id])
        assertEquals(start.plusDays(1), recurringRepo.observeSchedule(id).first()!!.nextDate)
        assertEquals(openingAt, db.ledgerEntryDao().openingDateOf(1L))
        assertEquals(1, count("SELECT COUNT(*) FROM operation"))

        recurringRepo.skipBlocked(id, now = currentTime)
        assertNull(recurringRepo.issues.value[id])
        assertEquals(1, count("SELECT COUNT(*) FROM operation"))
        assertEquals(start.plusDays(2), recurringRepo.observeSchedule(id).first()!!.nextDate)

        // A second blocked occurrence, resolved by moving the opening balance back.
        currentTime += dayMs
        recurringRepo.generateAllDue(now = currentTime)
        assertEquals(GenerationIssue.BEFORE_OPENING_BALANCE, recurringRepo.issues.value[id])
        recurringRepo.generateBlocked(id, now = currentTime)
        assertNull(recurringRepo.issues.value[id])
        assertEquals(2, count("SELECT COUNT(*) FROM operation"))
        assertEquals(emptyList<String>(), InvariantChecker.check(db.openHelper.readableDatabase))
    }

    @Test
    fun balanceOverflowWritesNothingAndIsReported() = runBlocking {
        val nearMax = Long.MAX_VALUE - 100
        fun seedNearMax() {
            val raw = db.openHelper.writableDatabase
            raw.execSQL("INSERT INTO operation (type, occurred_at, created_at, updated_at) VALUES ('INCOME', 1, 1, 1)")
            raw.execSQL(
                "INSERT INTO ledger_entry (operation_id, account_id, currency_code, amount_minor, occurred_at) " +
                    "VALUES (last_insert_rowid(), 1, 'USD', $nearMax, 1)",
            )
        }
        seedNearMax()
        val opsBefore = count("SELECT COUNT(*) FROM operation")

        try {
            recurringRepo.create(draft(kind = TransactionKind.INCOME, amountMinor = 450L), now = currentTime)
            fail("Expected MoneyOverflowException")
        } catch (_: MoneyOverflowException) {
        }
        assertEquals(0, count("SELECT COUNT(*) FROM recurring_schedule"))
        assertEquals(opsBefore, count("SELECT COUNT(*) FROM operation"))
        assertEquals(0, count("SELECT COUNT(*) FROM recurring_occurrence"))

        // Background: schedule created for tomorrow, then the balance grows to the limit.
        db.openHelper.writableDatabase.execSQL("DELETE FROM operation")
        val start = today()
        val id = recurringRepo.create(
            draft(kind = TransactionKind.INCOME, amountMinor = 450L, firstDate = start.plusDays(1)),
            now = currentTime,
        )
        seedNearMax()
        currentTime += dayMs
        recurringRepo.generateAllDue(now = currentTime)
        assertEquals(GenerationIssue.BALANCE_OVERFLOW, recurringRepo.issues.value[id])
        assertEquals(start.plusDays(1), recurringRepo.observeSchedule(id).first()!!.nextDate)
        assertEquals(0, count("SELECT COUNT(*) FROM recurring_occurrence"))
    }

    @Test
    fun createAllowsThousandDueOccurrencesButNotMore() = runBlocking {
        try {
            recurringRepo.create(draft(firstDate = today().minusDays(1000)), now = currentTime)
            fail("Expected refusal of 1001 occurrences")
        } catch (_: InvariantViolationException) {
        }
        assertEquals(0, count("SELECT COUNT(*) FROM recurring_schedule"))
        assertEquals(0, count("SELECT COUNT(*) FROM operation"))

        recurringRepo.create(draft(firstDate = today().minusDays(999)), now = currentTime)
        assertEquals(1000, count("SELECT COUNT(*) FROM operation"))
        assertEquals(emptyList<String>(), InvariantChecker.check(db.openHelper.readableDatabase))
    }

    @Test
    fun movingToDifferentArchivedAccountIsRefusedButKeepingArchivedOriginalIsAllowed() = runBlocking {
        val id = recurringRepo.create(draft(firstDate = today().plusDays(1)), now = currentTime)

        accountRepo.setArchived(2L, true)
        try {
            recurringRepo.update(
                id,
                draft(accountId = 2L, firstDate = today().plusDays(1)),
                confirmCurrencyChange = true,
                now = currentTime,
            )
            fail("Expected refusal")
        } catch (_: InvariantViolationException) {
        }

        accountRepo.setArchived(1L, true)
        recurringRepo.update(id, draft(name = "Renamed", firstDate = today().plusDays(1)), now = currentTime)
        assertEquals("Renamed", recurringRepo.observeSchedule(id).first()!!.name)
    }

    @Test
    fun deletingAccountRemovesItsSchedulesAndOccurrences() = runBlocking {
        recurringRepo.create(draft(accountId = 1L), now = currentTime)
        val other = recurringRepo.create(draft(accountId = 2L, firstDate = today().plusDays(1)), now = currentTime)
        assertEquals(1, accountRepo.scheduleCount(1L))

        accountRepo.delete(1L)
        assertEquals(1, count("SELECT COUNT(*) FROM recurring_schedule"))
        assertEquals(0, count("SELECT COUNT(*) FROM recurring_occurrence"))
        assertNotNull(recurringRepo.observeSchedule(other).first())
        assertEquals(emptyList<String>(), InvariantChecker.check(db.openHelper.readableDatabase))
    }

    @Test
    fun dueSchedulesQueryUsesStatusNextDateIndex() {
        val plan = mutableListOf<String>()
        db.openHelper.readableDatabase.query(
            "EXPLAIN QUERY PLAN SELECT * FROM recurring_schedule " +
                "WHERE status = 'ACTIVE' AND next_date IS NOT NULL AND next_date <= 1 ORDER BY id",
        ).use { c -> while (c.moveToNext()) plan += c.getString(c.getColumnIndexOrThrow("detail")) }
        assertTrue(plan.toString(), plan.any { it.contains("index_recurring_schedule_status_next_date") })
    }

    @Test
    fun invariantCheckerFlagsRecurringViolations() = runBlocking {
        val id = recurringRepo.create(draft(firstDate = today().plusDays(1)), now = currentTime)
        val raw = db.openHelper.writableDatabase
        fun flagged(check: String) = InvariantChecker.check(raw).any { it.startsWith(check) }

        raw.execSQL("UPDATE recurring_schedule SET amount_minor = 0 WHERE id = $id")
        assertTrue(flagged("recurring amount not positive"))
        raw.execSQL("UPDATE recurring_schedule SET amount_minor = 100, interval_count = 400 WHERE id = $id")
        assertTrue(flagged("recurring bad interval"))
        raw.execSQL("UPDATE recurring_schedule SET interval_count = 1, end_date = start_date - 1 WHERE id = $id")
        assertTrue(flagged("recurring end before start"))
        raw.execSQL("UPDATE recurring_schedule SET end_date = NULL, next_date = NULL WHERE id = $id")
        assertTrue(flagged("recurring open-ended without next"))
        raw.execSQL("UPDATE recurring_schedule SET next_date = start_date - 1 WHERE id = $id")
        assertTrue(flagged("recurring next out of range"))
        raw.execSQL("UPDATE recurring_schedule SET next_date = start_date, category_id = 1 WHERE id = $id")
        assertTrue(flagged("recurring category type mismatch"))
        raw.execSQL("UPDATE recurring_schedule SET category_id = 2 WHERE id = $id")
        raw.execSQL(
            "INSERT INTO recurring_occurrence (schedule_id, occurrence_date, status, operation_id, handled_at) " +
                "VALUES ($id, ${today().plusDays(1).toEpochDay()}, 'SKIPPED', NULL, 1)",
        )
        assertTrue(flagged("recurring occurrence not before next"))
        raw.execSQL("UPDATE recurring_occurrence SET occurrence_date = occurrence_date - 5")
        raw.execSQL("INSERT INTO operation (type, occurred_at, created_at, updated_at) VALUES ('INCOME', 1, 1, 1)")
        raw.execSQL("UPDATE recurring_occurrence SET operation_id = last_insert_rowid()")
        assertTrue(flagged("recurring skipped with operation"))
        raw.execSQL("UPDATE recurring_occurrence SET status = 'GENERATED'")
        assertTrue(flagged("recurring generated operation mismatch"))
    }
}
