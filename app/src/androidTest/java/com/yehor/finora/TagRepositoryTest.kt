package com.yehor.finora

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.yehor.finora.data.db.AccountEntity
import com.yehor.finora.data.db.FinoraDatabase
import com.yehor.finora.data.db.FinoraDatabaseCallback
import com.yehor.finora.data.db.InvariantChecker
import com.yehor.finora.data.repo.DuplicateTagNameException
import com.yehor.finora.data.repo.EntryDraft
import com.yehor.finora.data.repo.InvalidTagNameException
import com.yehor.finora.data.repo.OperationRepository
import com.yehor.finora.data.repo.TagRepository
import com.yehor.finora.domain.model.AccountStatus
import com.yehor.finora.domain.model.OperationType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class TagRepositoryTest {

    private lateinit var db: FinoraDatabase
    private lateinit var tagRepo: TagRepository
    private lateinit var opRepo: OperationRepository

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, FinoraDatabase::class.java)
            .addCallback(FinoraDatabaseCallback)
            .build()
        tagRepo = TagRepository(db)
        opRepo = OperationRepository(db)
    }

    @After
    fun tearDown() = db.close()

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
    fun createAndDuplicateCaseInsensitive() = runBlocking {
        val id1 = tagRepo.create("Debt", 0xFF0000L)
        assertTrue(id1 > 0)

        assertFails<DuplicateTagNameException> {
            runBlocking { tagRepo.create("debt", null) }
        }
        assertFails<DuplicateTagNameException> {
            runBlocking { tagRepo.create("  DEBT  ", null) }
        }
        assertFails<InvalidTagNameException> {
            runBlocking { tagRepo.create("", null) }
        }
    }

    @Test
    fun renameAndRecolorDoesNotTouchOperationUpdatedAt() = runBlocking {
        val accId = db.accountDao().insert(
            AccountEntity(name = "A", currencyCode = "USD", type = "CASH", icon = null, color = null, description = null, status = AccountStatus.ACTIVE, createdAt = 0L)
        )
        val tagId = tagRepo.create("Food", null)
        val opId = opRepo.insertOperation(
            type = OperationType.EXPENSE,
            occurredAt = 1000L,
            entries = listOf(EntryDraft(accId, -100L)),
            tagIds = setOf(tagId),
            now = 5000L,
        )

        val beforeOp = db.operationDao().getById(opId)!!
        assertEquals(5000L, beforeOp.updatedAt)

        // Rename tag
        tagRepo.rename(tagId, "Food & Drinks")
        tagRepo.setColor(tagId, 0x00FF00L)

        val afterOp = db.operationDao().getById(opId)!!
        assertEquals(5000L, afterOp.updatedAt) // untouched!

        val list = tagRepo.observeAllWithUsage().first()
        assertEquals(1, list.size)
        assertEquals("Food & Drinks", list.first().tag.name)
        assertEquals(1, list.first().usageCount)
    }

    @Test
    fun deleteTagRemovesLinksKeepsOperation() = runBlocking {
        val accId = db.accountDao().insert(
            AccountEntity(name = "A", currencyCode = "USD", type = "CASH", icon = null, color = null, description = null, status = AccountStatus.ACTIVE, createdAt = 0L)
        )
        val tagId = tagRepo.create("Travel", null)
        val opId = opRepo.insertOperation(
            type = OperationType.EXPENSE,
            occurredAt = 1000L,
            entries = listOf(EntryDraft(accId, -100L)),
            tagIds = setOf(tagId),
        )

        tagRepo.delete(tagId)

        val op = db.operationDao().getById(opId)
        assertTrue(op != null)
        val tags = db.operationTagDao().tagIdsOf(opId)
        assertTrue(tags.isEmpty())
        assertTrue(InvariantChecker.check(db.openHelper.writableDatabase).isEmpty())
    }
}
