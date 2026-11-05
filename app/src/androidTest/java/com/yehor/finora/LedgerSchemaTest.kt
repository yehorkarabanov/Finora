package com.yehor.finora

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.yehor.finora.data.db.AccountEntity
import com.yehor.finora.data.db.CategoryEntity
import com.yehor.finora.data.db.FinoraDatabase
import com.yehor.finora.data.db.FinoraDatabaseCallback
import com.yehor.finora.data.db.InvariantChecker
import com.yehor.finora.data.db.LedgerEntryEntity
import com.yehor.finora.data.db.OperationEntity
import com.yehor.finora.data.db.TagEntity
import com.yehor.finora.data.repo.EntryDraft
import com.yehor.finora.data.repo.InvariantViolationException
import com.yehor.finora.data.repo.OperationRepository
import com.yehor.finora.domain.model.AccountStatus
import com.yehor.finora.domain.model.CategoryType
import com.yehor.finora.domain.model.OperationType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class LedgerSchemaTest {

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

    private suspend fun account(code: String, name: String = code): Long =
        db.accountDao().insert(
            AccountEntity(
                name = name, currencyCode = code, type = "CASH", icon = null, color = null,
                description = null, status = AccountStatus.ACTIVE, createdAt = 0,
            ),
        )

    private suspend fun category(type: CategoryType, parentId: Long? = null): Long =
        db.categoryDao().insert(
            CategoryEntity(
                name = "c", icon = null, color = null, type = type, parentId = parentId, createdAt = 0,
            ),
        )

    private fun violations() = InvariantChecker.check(db.openHelper.writableDatabase)

    private fun exec(sql: String) = db.openHelper.writableDatabase.execSQL(sql)

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
    fun currencyTableIsSeededFromRegistry() = runBlocking {
        db.openHelper.writableDatabase.query("SELECT COUNT(*) FROM currency").use {
            it.moveToFirst()
            assertEquals(com.yehor.finora.domain.money.CurrencyRegistry.all.size, it.getInt(0))
        }
        db.openHelper.writableDatabase.query("SELECT scale FROM currency WHERE code = 'KWD'").use {
            it.moveToFirst()
            assertEquals(3, it.getInt(0))
        }
    }

    @Test
    fun foreignKeysAreEnforced() = runBlocking {
        db.openHelper.writableDatabase.query("PRAGMA foreign_keys").use {
            it.moveToFirst()
            assertEquals(1, it.getInt(0))
        }
        assertFails<SQLiteConstraintException> {
            runBlocking {
                db.operationDao().insert(
                    OperationEntity(
                        type = OperationType.EXPENSE, occurredAt = 1, categoryId = 999,
                        description = null, openingAccountId = null, createdAt = 1, updatedAt = 1,
                    ),
                )
            }
        }
    }

    @Test
    fun currencyMismatchInsertFails() = runBlocking {
        val usd = account("USD")
        val op = repo.insertOperation(
            OperationType.INCOME, 1, listOf(EntryDraft(usd, 100)),
            categoryId = category(CategoryType.INCOME),
        )
        assertFails<SQLiteConstraintException> {
            runBlocking {
                db.ledgerEntryDao().insert(
                    LedgerEntryEntity(
                        operationId = op, accountId = usd, currencyCode = "EUR",
                        amountMinor = 5, occurredAt = 1,
                    ),
                )
            }
        }
    }

    @Test
    fun accountCurrencyCannotChangeWhileEntriesExist() = runBlocking {
        val withEntries = account("USD")
        val empty = account("USD", "empty")
        repo.insertOperation(
            OperationType.INCOME, 1, listOf(EntryDraft(withEntries, 100)),
            categoryId = category(CategoryType.INCOME),
        )
        assertFails<SQLiteConstraintException> {
            exec("UPDATE account SET currency_code = 'EUR' WHERE id = $withEntries")
        }
        // An account with no entries can still change currency.
        exec("UPDATE account SET currency_code = 'EUR' WHERE id = $empty")
        assertEquals("EUR", db.accountDao().currencyCodeOf(empty))
    }

    @Test
    fun balanceIsSignedSumIncludingNegative() = runBlocking {
        val acc = account("USD")
        val income = category(CategoryType.INCOME)
        val expense = category(CategoryType.EXPENSE)
        repo.insertOperation(OperationType.INCOME, 1, listOf(EntryDraft(acc, 1000)), categoryId = income)
        repo.insertOperation(OperationType.EXPENSE, 2, listOf(EntryDraft(acc, -2500)), categoryId = expense)
        assertEquals(-1500L, repo.balanceMinor(acc))
        assertEquals(0L, repo.balanceMinor(account("EUR")))
        assertTrue(violations().isEmpty())
    }

    @Test
    fun secondOpeningBalanceForAccountFails() = runBlocking {
        val acc = account("USD")
        repo.insertOperation(
            OperationType.OPENING_BALANCE, 1, listOf(EntryDraft(acc, 500)), openingAccountId = acc,
        )
        assertFails<SQLiteConstraintException> {
            runBlocking {
                repo.insertOperation(
                    OperationType.OPENING_BALANCE, 2, listOf(EntryDraft(acc, 700)), openingAccountId = acc,
                )
            }
        }
        assertEquals(500L, repo.balanceMinor(acc))
    }

    @Test
    fun openingBalanceMayBeNegative() = runBlocking {
        val acc = account("USD")
        repo.insertOperation(
            OperationType.OPENING_BALANCE, 1, listOf(EntryDraft(acc, -500)), openingAccountId = acc,
        )
        assertEquals(-500L, repo.balanceMinor(acc))
        assertTrue(violations().isEmpty())
    }

    @Test
    fun openingBalanceEntryOnOtherAccountIsRejectedAndFlagged() = runBlocking {
        val a = account("USD", "a")
        val b = account("USD", "b")
        assertFails<InvariantViolationException> {
            runBlocking {
                repo.insertOperation(
                    OperationType.OPENING_BALANCE, 1, listOf(EntryDraft(b, 500)), openingAccountId = a,
                )
            }
        }
        // Nothing may have been written by the rejected call.
        assertEquals(0L, repo.balanceMinor(b))

        // Raw SQL bypasses the repository; the checker must flag it.
        exec(
            "INSERT INTO operation (id, type, occurred_at, opening_account_id, created_at, updated_at) " +
                "VALUES (50, 'OPENING_BALANCE', 1, $a, 1, 1)",
        )
        exec(
            "INSERT INTO ledger_entry (operation_id, account_id, currency_code, amount_minor, occurred_at) " +
                "VALUES (50, $b, 'USD', 500, 1)",
        )
        assertTrue(violations().any { it.startsWith("opening balance fields") })
    }

    @Test
    fun otherOpeningBalanceRulesAreRejected() = runBlocking {
        val acc = account("USD")
        assertFails<InvariantViolationException> {
            runBlocking {
                repo.insertOperation(OperationType.OPENING_BALANCE, 1, listOf(EntryDraft(acc, 0)), openingAccountId = acc)
            }
        }
        assertFails<InvariantViolationException> {
            runBlocking {
                repo.insertOperation(OperationType.OPENING_BALANCE, 1, listOf(EntryDraft(acc, 5)))
            }
        }
        assertFails<InvariantViolationException> {
            runBlocking {
                repo.insertOperation(
                    OperationType.OPENING_BALANCE, 1, listOf(EntryDraft(acc, 5)),
                    categoryId = category(CategoryType.INCOME), openingAccountId = acc,
                )
            }
        }
        assertFails<InvariantViolationException> {
            runBlocking {
                repo.insertOperation(
                    OperationType.INCOME, 1, listOf(EntryDraft(acc, 5)),
                    categoryId = category(CategoryType.INCOME), openingAccountId = acc,
                )
            }
        }
    }

    @Test
    fun tagOnOpeningBalanceIsRejectedAndFlagged() = runBlocking {
        val acc = account("USD")
        val tag = db.tagDao().insert(TagEntity(name = "t", color = null, createdAt = 0))
        val opening = repo.insertOperation(
            OperationType.OPENING_BALANCE, 1, listOf(EntryDraft(acc, 500)), openingAccountId = acc,
        )
        assertFails<InvariantViolationException> { runBlocking { repo.addTag(opening, tag) } }
        assertTrue(db.operationTagDao().tagIdsOf(opening).isEmpty())

        exec("INSERT INTO operation_tag (operation_id, tag_id) VALUES ($opening, $tag)")
        assertTrue(violations().any { it.startsWith("tag on opening balance") })
    }

    @Test
    fun tagOnRegularOperationIsAllowed() = runBlocking {
        val acc = account("USD")
        val tag = db.tagDao().insert(TagEntity(name = "t", color = null, createdAt = 0))
        val op = repo.insertOperation(
            OperationType.INCOME, 1, listOf(EntryDraft(acc, 5)), categoryId = category(CategoryType.INCOME),
        )
        repo.addTag(op, tag)
        assertEquals(listOf(tag), db.operationTagDao().tagIdsOf(op))
        assertTrue(violations().isEmpty())
    }

    @Test
    fun changingOperationDateMovesEveryEntryIncludingBothTransferSides() = runBlocking {
        val a = account("USD", "a")
        val b = account("USD", "b")
        val transfer = repo.insertOperation(
            OperationType.TRANSFER, 1_000,
            listOf(EntryDraft(a, -300), EntryDraft(b, 300)),
            categoryId = category(CategoryType.TRANSFER),
        )
        repo.changeOccurredAt(transfer, 9_999)
        assertEquals(9_999L, db.operationDao().getById(transfer)!!.occurredAt)
        val entries = db.ledgerEntryDao().entriesOf(transfer)
        assertEquals(2, entries.size)
        assertTrue(entries.all { it.occurredAt == 9_999L })
        assertTrue(violations().isEmpty())
    }

    @Test
    fun checkerFlagsRawSqlOccurredAtMismatch() = runBlocking {
        val acc = account("USD")
        val op = repo.insertOperation(
            OperationType.INCOME, 1_000, listOf(EntryDraft(acc, 5)),
            categoryId = category(CategoryType.INCOME),
        )
        assertFalse(violations().any { it.startsWith("ledger occurred_at mismatch") })
        exec("UPDATE ledger_entry SET occurred_at = 5 WHERE operation_id = $op")
        assertTrue(violations().any { it.startsWith("ledger occurred_at mismatch") })
    }

    @Test
    fun transferAndSignRulesAreRejected() = runBlocking {
        val a = account("USD", "a")
        val b = account("USD", "b")
        val transferCat = category(CategoryType.TRANSFER)
        assertFails<InvariantViolationException> {
            runBlocking {
                repo.insertOperation(
                    OperationType.TRANSFER, 1, listOf(EntryDraft(a, -5), EntryDraft(a, 5)),
                    categoryId = transferCat,
                )
            }
        }
        assertFails<InvariantViolationException> {
            runBlocking {
                repo.insertOperation(
                    OperationType.TRANSFER, 1, listOf(EntryDraft(a, -5)), categoryId = transferCat,
                )
            }
        }
        assertFails<InvariantViolationException> {
            runBlocking {
                repo.insertOperation(
                    OperationType.INCOME, 1, listOf(EntryDraft(a, -5)),
                    categoryId = category(CategoryType.INCOME),
                )
            }
        }
        assertFails<InvariantViolationException> {
            runBlocking {
                repo.insertOperation(
                    OperationType.EXPENSE, 1, listOf(EntryDraft(b, 5)),
                    categoryId = category(CategoryType.EXPENSE),
                )
            }
        }
        // Category type must match the operation type.
        assertFails<InvariantViolationException> {
            runBlocking {
                repo.insertOperation(
                    OperationType.EXPENSE, 1, listOf(EntryDraft(b, -5)),
                    categoryId = category(CategoryType.INCOME),
                )
            }
        }
    }

    @Test
    fun checkerFlagsRawSqlTransferWithoutOppositeSigns() = runBlocking {
        val a = account("USD", "a")
        val b = account("USD", "b")
        val transfer = repo.insertOperation(
            OperationType.TRANSFER, 1,
            listOf(EntryDraft(a, -5), EntryDraft(b, 5)),
            categoryId = category(CategoryType.TRANSFER),
        )
        assertFalse(violations().any { it.startsWith("transfer shape") })

        // Both sides positive.
        exec("UPDATE ledger_entry SET amount_minor = 5 WHERE operation_id = $transfer")
        assertTrue(violations().any { it.startsWith("transfer shape") })

        // Both sides negative.
        exec("UPDATE ledger_entry SET amount_minor = -5 WHERE operation_id = $transfer")
        assertTrue(violations().any { it.startsWith("transfer shape") })

        // One side zero.
        exec("UPDATE ledger_entry SET amount_minor = 0 WHERE operation_id = $transfer AND account_id = $a")
        exec("UPDATE ledger_entry SET amount_minor = 5 WHERE operation_id = $transfer AND account_id = $b")
        assertTrue(violations().any { it.startsWith("transfer shape") })
    }

    @Test
    fun balanceQueryUsesCoveringIndexWithoutTableScan() {
        db.openHelper.writableDatabase.query(
            "EXPLAIN QUERY PLAN SELECT COALESCE(SUM(amount_minor), 0) FROM ledger_entry WHERE account_id = 1",
        ).use { c ->
            val details = buildList {
                while (c.moveToNext()) add(c.getString(c.getColumnIndexOrThrow("detail")))
            }
            assertTrue(details.toString(), details.any { it.contains("COVERING INDEX") })
            assertTrue(details.toString(), details.none { it.startsWith("SCAN ledger_entry") })
        }
    }
}
