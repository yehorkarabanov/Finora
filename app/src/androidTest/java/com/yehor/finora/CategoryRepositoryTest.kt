package com.yehor.finora

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.yehor.finora.data.db.AccountEntity
import com.yehor.finora.data.db.FinoraDatabase
import com.yehor.finora.data.db.FinoraDatabaseCallback
import com.yehor.finora.data.db.InvariantChecker
import com.yehor.finora.data.repo.CategoryRepository
import com.yehor.finora.data.repo.EntryDraft
import com.yehor.finora.data.repo.InvalidCategoryParentException
import com.yehor.finora.data.repo.InvariantViolationException
import com.yehor.finora.data.repo.OperationRepository
import com.yehor.finora.data.repo.ReassignTarget
import com.yehor.finora.domain.category.CategoryRollup
import com.yehor.finora.domain.category.CategoryRules.ParentCheck
import com.yehor.finora.domain.category.CategoryTree
import com.yehor.finora.domain.model.AccountStatus
import com.yehor.finora.domain.model.CategoryType
import com.yehor.finora.domain.model.OperationType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class CategoryRepositoryTest {

    private lateinit var db: FinoraDatabase
    private lateinit var ops: OperationRepository
    private lateinit var repo: CategoryRepository

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, FinoraDatabase::class.java)
            .addCallback(FinoraDatabaseCallback)
            .build()
        ops = OperationRepository(db)
        repo = CategoryRepository(db)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun cat(
        name: String,
        parent: Long? = null,
        type: CategoryType = CategoryType.EXPENSE,
    ): Long = repo.create(name, type, parent, null, null, now = 1)

    private suspend fun account(code: String = "USD"): Long = db.accountDao().insert(
        AccountEntity(
            name = "a$code", currencyCode = code, type = "BANK", icon = null, color = null,
            description = null, status = AccountStatus.ACTIVE, createdAt = 0,
        ),
    )

    private suspend fun expense(accountId: Long, categoryId: Long?, minor: Long, at: Long = 1_000): Long =
        ops.insertOperation(
            OperationType.EXPENSE, at, listOf(EntryDraft(accountId, -minor)),
            categoryId = categoryId, now = at,
        )

    private suspend fun parentOf(id: Long): Long? = db.categoryDao().getById(id)!!.parentId

    private fun violations() = InvariantChecker.check(db.openHelper.writableDatabase)

    private suspend fun assertRejected(expected: ParentCheck, block: suspend () -> Unit) {
        try {
            block()
            fail("expected InvalidCategoryParentException($expected)")
        } catch (e: InvalidCategoryParentException) {
            assertEquals(expected, e.reason)
        }
    }

    // ---- create ----

    @Test
    fun createRootChildGrandchildAndRejectLevelFour() = runBlocking {
        val root = cat("root")
        val child = cat("child", root)
        val grandchild = cat("grandchild", child)
        assertEquals(CategoryType.EXPENSE, db.categoryDao().getById(grandchild)!!.type)
        val before = db.categoryDao().getAll().size
        assertRejected(ParentCheck.TOO_DEEP) { cat("level4", grandchild) }
        assertEquals(before, db.categoryDao().getAll().size)
        assertTrue(violations().isEmpty())
    }

    @Test
    fun createWithMismatchingTypeThrows() = runBlocking {
        val root = cat("root")
        assertRejected(ParentCheck.TYPE_MISMATCH) { cat("x", root, CategoryType.INCOME) }
        assertEquals(1, db.categoryDao().getAll().size)
    }

    // ---- move ----

    @Test
    fun moveToTopLevelAndToAnotherParent() = runBlocking {
        val a = cat("a")
        val b = cat("b")
        val c = cat("c", a)
        repo.move(c, b)
        assertEquals(b, parentOf(c))
        repo.move(c, null)
        assertNull(parentOf(c))
    }

    @Test
    fun moveRejectsSelfCycleAndOtherType() = runBlocking {
        val a = cat("a")
        val b = cat("b", a)
        val c = cat("c", b)
        val income = cat("inc", type = CategoryType.INCOME)
        assertRejected(ParentCheck.SELF) { repo.move(a, a) }
        assertRejected(ParentCheck.CYCLE) { repo.move(a, b) }
        assertRejected(ParentCheck.CYCLE) { repo.move(a, c) }
        assertRejected(ParentCheck.TYPE_MISMATCH) { repo.move(a, income) }
        assertNull(parentOf(a))
    }

    @Test
    fun moveEnforcesDepthForWholeSubtree() = runBlocking {
        val r1 = cat("r1")
        val r2 = cat("r2")
        val l2 = cat("l2", r2)
        val l3 = cat("l3", l2)
        val leaf = cat("leaf")

        repo.move(leaf, l2) // level 3: allowed
        assertEquals(l2, parentOf(leaf))
        assertRejected(ParentCheck.TOO_DEEP) { repo.move(r1, l3) } // leaf under level 3
        repo.move(leaf, null)

        val withChild = cat("withChild")
        cat("its child", withChild)
        repo.move(withChild, r1) // height 2 under root: allowed
        assertRejected(ParentCheck.TOO_DEEP) { repo.move(withChild, l2) } // would reach level 4
        assertEquals(r1, parentOf(withChild))

        val tall = cat("tall")
        val tallChild = cat("tallChild", tall)
        cat("tallGrand", tallChild)
        assertRejected(ParentCheck.TOO_DEEP) { repo.move(tall, r1) }
        repo.move(tall, null)
        assertTrue(violations().isEmpty())
    }

    @Test
    fun moveDoesNotChangeOperations() = runBlocking {
        val a = cat("a")
        val b = cat("b")
        val acc = account()
        val op = expense(acc, a, 500)
        repo.move(a, b)
        assertEquals(a, db.operationDao().getById(op)!!.categoryId)
        assertEquals(-500L, db.ledgerEntryDao().entriesOf(op).single().amountMinor)
    }

    // ---- delete ----

    @Test
    fun deleteReassignsToSiblingParentAndUncategorized() = runBlocking {
        val acc = account()
        val parent = cat("parent")
        val victim = cat("victim", parent)
        val sibling = cat("sibling", parent)
        val grand = cat("grand", victim)
        val op1 = expense(acc, victim, 100)
        val opGrand = expense(acc, grand, 40)

        repo.deletionImpact(victim).let {
            assertEquals(1, it.directOperationCount)
            assertEquals(1, it.childCount)
            assertEquals(parent, it.suggestedTarget)
        }

        repo.delete(victim, ReassignTarget.To(sibling), now = 99)
        assertNull(db.categoryDao().getById(victim))
        assertEquals(sibling, db.operationDao().getById(op1)!!.categoryId)
        assertEquals(99L, db.operationDao().getById(op1)!!.updatedAt)
        assertEquals(parent, parentOf(grand))
        assertEquals(grand, db.operationDao().getById(opGrand)!!.categoryId)

        repo.delete(sibling, ReassignTarget.To(parent), now = 100)
        assertEquals(parent, db.operationDao().getById(op1)!!.categoryId)

        repo.delete(parent, ReassignTarget.Uncategorized, now = 101)
        assertNull(db.operationDao().getById(op1)!!.categoryId)
        assertNull(parentOf(grand))
        assertEquals(2, db.operationDao().observeAll().first().size)
        assertTrue(violations().toString(), violations().isEmpty())
    }

    @Test
    fun deleteOfLevelTwoWithChildrenKeepsDepthWithinLimit() = runBlocking {
        val root = cat("root")
        val mid = cat("mid", root)
        val leaf = cat("leaf", mid)
        repo.delete(mid, ReassignTarget.Uncategorized)
        assertEquals(root, parentOf(leaf))
        val tree = CategoryTree(repo.observeAll().first())
        assertTrue(tree.heightOf(root) <= 3)
        assertTrue(violations().isEmpty())
    }

    @Test
    fun deleteRejectsBadTargetsAndChangesNothing() = runBlocking {
        val acc = account()
        val victim = cat("victim")
        val child = cat("child", victim)
        val income = cat("inc", type = CategoryType.INCOME)
        val op = expense(acc, victim, 10)

        for (target in listOf(
            ReassignTarget.To(victim),
            ReassignTarget.To(income),
            ReassignTarget.To(9_999),
        )) {
            try {
                repo.delete(victim, target)
                fail("expected InvariantViolationException for $target")
            } catch (_: InvariantViolationException) {
            }
            assertEquals(victim, db.operationDao().getById(op)!!.categoryId)
            assertEquals(victim, parentOf(child))
            assertTrue(db.categoryDao().getById(victim) != null)
        }
    }

    @Test
    fun deleteOfUnusedLeafWorks() = runBlocking {
        val leaf = cat("leaf")
        repo.delete(leaf, ReassignTarget.Uncategorized)
        assertNull(db.categoryDao().getById(leaf))
    }

    // ---- totals ----

    @Test
    fun directTotalsAndRollupOnRealRows() = runBlocking {
        val usd = account("USD")
        val pln = account("PLN")
        val food = cat("Food")
        val groceries = cat("Groceries", food)
        val restaurants = cat("Restaurants", food)
        expense(usd, food, 100, at = 1_000)
        expense(usd, groceries, 700, at = 2_000)
        expense(usd, restaurants, 450, at = 3_000)
        expense(pln, groceries, 30, at = 3_000)
        expense(usd, null, 5, at = 4_000)
        expense(usd, food, 999, at = 10_000) // outside the period (to is exclusive)
        ops.setOpeningBalance(usd, 50_000, 0, now = 0)

        val totals = repo.directTotals(OperationType.EXPENSE, from = 1_000, to = 10_000)
        val tree = CategoryTree(repo.observeAll().first())
        val rolled = CategoryRollup.rollUp(tree, totals)

        assertEquals(mapOf("USD" to -1_250L, "PLN" to -30L), rolled.totals.getValue(food))
        assertEquals(mapOf("USD" to -700L, "PLN" to -30L), rolled.totals.getValue(groceries))
        assertEquals(mapOf("USD" to -5L), rolled.totals.getValue(null))
        assertEquals(4, rolled.counts.getValue(food))
    }

    // ---- checker ----

    @Test
    fun checkerFlagsSelfParentCycleAndDeepChain() = runBlocking {
        val sql = db.openHelper.writableDatabase
        sql.execSQL("PRAGMA foreign_keys = OFF")
        sql.execSQL("INSERT INTO category (id, name, type, parent_id, created_at) VALUES (1, 'self', 'EXPENSE', 1, 0)")
        sql.execSQL("INSERT INTO category (id, name, type, parent_id, created_at) VALUES (2, 'c2a', 'EXPENSE', 3, 0)")
        sql.execSQL("INSERT INTO category (id, name, type, parent_id, created_at) VALUES (3, 'c2b', 'EXPENSE', 2, 0)")
        sql.execSQL("INSERT INTO category (id, name, type, parent_id, created_at) VALUES (10, 'l1', 'INCOME', NULL, 0)")
        sql.execSQL("INSERT INTO category (id, name, type, parent_id, created_at) VALUES (11, 'l2', 'INCOME', 10, 0)")
        sql.execSQL("INSERT INTO category (id, name, type, parent_id, created_at) VALUES (12, 'l3', 'INCOME', 11, 0)")
        sql.execSQL("INSERT INTO category (id, name, type, parent_id, created_at) VALUES (13, 'l4', 'INCOME', 12, 0)")
        sql.execSQL("PRAGMA foreign_keys = ON")

        val v = violations()
        assertTrue(v.toString(), v.any { it.startsWith("category cycle") && it.endsWith("category 1") })
        assertTrue(v.toString(), v.any { it.startsWith("category cycle") && it.endsWith("category 2") })
        assertTrue(v.toString(), v.any { it.startsWith("category cycle") && it.endsWith("category 3") })
        assertTrue(v.toString(), v.any { it.startsWith("category deeper than 3 levels") && it.endsWith("category 13") })
        assertTrue(v.toString(), v.none { it.endsWith("category 12") || it.endsWith("category 11") || it.endsWith("category 10") })
    }

    @Test
    fun checkerFlagsLateOpeningBalanceAndAcceptsValidChain() = runBlocking {
        val acc = account()
        val a = cat("a")
        val b = cat("b", a)
        cat("c", b)
        assertTrue(violations().isEmpty())

        ops.setOpeningBalance(acc, 100, 1_000, now = 0)
        expense(acc, null, 5, at = 2_000)
        assertTrue(violations().isEmpty())

        db.openHelper.writableDatabase.execSQL("UPDATE operation SET occurred_at = 5000 WHERE type = 'OPENING_BALANCE'")
        db.openHelper.writableDatabase.execSQL("UPDATE ledger_entry SET occurred_at = 5000 WHERE amount_minor = 100")
        val v = violations()
        assertTrue(v.toString(), v.any { it.startsWith("opening balance dated after an entry") })
    }
}
