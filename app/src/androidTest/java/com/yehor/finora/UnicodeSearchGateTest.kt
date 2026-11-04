package com.yehor.finora

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.yehor.finora.data.db.AccountEntity
import com.yehor.finora.data.db.FinoraDatabase
import com.yehor.finora.data.db.FinoraDatabaseCallback
import com.yehor.finora.data.db.LedgerEntryEntity
import com.yehor.finora.data.db.OperationEntity
import com.yehor.finora.data.db.TransactionQuery
import com.yehor.finora.domain.model.AccountStatus
import com.yehor.finora.domain.model.OperationType
import com.yehor.finora.domain.operation.TransactionFilter
import com.yehor.finora.domain.operation.TransactionSort
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class UnicodeSearchGateTest {
    private lateinit var db: FinoraDatabase

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, FinoraDatabase::class.java)
            .addCallback(FinoraDatabaseCallback)
            .build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun unicodeSearchCaseInsensitive() = runBlocking {
        val accId = db.accountDao().insert(
            AccountEntity(name = "A", currencyCode = "USD", type = "CASH", icon = null, color = null, description = null, status = AccountStatus.ACTIVE, createdAt = 0L)
        )
        val op1 = db.operationDao().insert(OperationEntity(type = OperationType.EXPENSE, occurredAt = 1000L, categoryId = null, description = "Кофе в зернах", openingAccountId = null, createdAt = 0L, updatedAt = 0L))
        db.ledgerEntryDao().insert(LedgerEntryEntity(operationId = op1, accountId = accId, currencyCode = "USD", amountMinor = -100L, occurredAt = 1000L))

        val op2 = db.operationDao().insert(OperationEntity(type = OperationType.EXPENSE, occurredAt = 2000L, categoryId = null, description = "Żabka zakupy", openingAccountId = null, createdAt = 0L, updatedAt = 0L))
        db.ledgerEntryDao().insert(LedgerEntryEntity(operationId = op2, accountId = accId, currencyCode = "USD", amountMinor = -200L, occurredAt = 2000L))

        // Query with lowercase 'кофе'
        val query1 = TransactionQuery.build(TransactionFilter(text = "кофе"), TransactionSort.DATE_DESC, emptySet())
        val res1 = db.operationDao().listWithDetails(query1).first()
        assertEquals(1, res1.size)
        assertEquals(op1, res1.first().operation.id)

        // Query with uppercase 'ZABKA' or lowercase 'żabka'
        val query2 = TransactionQuery.build(TransactionFilter(text = "żabka"), TransactionSort.DATE_DESC, emptySet())
        val res2 = db.operationDao().listWithDetails(query2).first()
        assertEquals(1, res2.size)
        assertEquals(op2, res2.first().operation.id)
    }
}
