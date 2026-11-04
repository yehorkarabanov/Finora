package com.yehor.finora.data.repo

import androidx.room.withTransaction
import com.yehor.finora.data.db.FinoraDatabase
import com.yehor.finora.data.db.LedgerEntryEntity
import com.yehor.finora.data.db.OperationEntity
import com.yehor.finora.data.db.OperationTagEntity
import com.yehor.finora.domain.model.CategoryType
import com.yehor.finora.domain.model.OperationType
import com.yehor.finora.domain.money.MoneyOverflowException
import android.database.sqlite.SQLiteException
import javax.inject.Inject
import javax.inject.Singleton

/** One signed ledger line of an operation to be written. */
data class EntryDraft(val accountId: Long, val amountMinor: Long)

/** Raised when a write would violate an invariant that the schema cannot express. */
class InvariantViolationException(message: String) : IllegalArgumentException(message)

/**
 * The single write path for operations and their ledger entries. Invariants that Room cannot
 * express as constraints (section 6.4 of the design) are enforced here, inside one transaction.
 * CRUD screens and use cases are built on top of this in later sub-projects.
 */
@Singleton
class OperationRepository @Inject constructor(private val db: FinoraDatabase) {

    suspend fun insertOperation(
        type: OperationType,
        occurredAt: Long,
        entries: List<EntryDraft>,
        categoryId: Long? = null,
        description: String? = null,
        openingAccountId: Long? = null,
        now: Long = System.currentTimeMillis(),
    ): Long = db.withTransaction {
        validate(type, entries, categoryId, openingAccountId)
        val operationId = db.operationDao().insert(
            OperationEntity(
                type = type,
                occurredAt = occurredAt,
                categoryId = categoryId,
                description = description,
                openingAccountId = openingAccountId,
                createdAt = now,
                updatedAt = now,
            ),
        )
        entries.forEach { e ->
            val currency = db.accountDao().currencyCodeOf(e.accountId)
                ?: throw InvariantViolationException("Unknown account ${e.accountId}")
            db.ledgerEntryDao().insert(
                LedgerEntryEntity(
                    operationId = operationId,
                    accountId = e.accountId,
                    currencyCode = currency,
                    amountMinor = e.amountMinor,
                    occurredAt = occurredAt,
                ),
            )
        }
        operationId
    }

    /** Moves the operation and every one of its ledger entries to [occurredAt] together. */
    suspend fun changeOccurredAt(
        operationId: Long,
        occurredAt: Long,
        now: Long = System.currentTimeMillis(),
    ) = db.withTransaction {
        db.operationDao().getById(operationId)
            ?: throw InvariantViolationException("Unknown operation $operationId")
        db.operationDao().updateOccurredAt(operationId, occurredAt, now)
        db.ledgerEntryDao().updateOccurredAt(operationId, occurredAt)
    }

    suspend fun addTag(operationId: Long, tagId: Long) = db.withTransaction {
        val op = db.operationDao().getById(operationId)
            ?: throw InvariantViolationException("Unknown operation $operationId")
        if (op.type == OperationType.OPENING_BALANCE) {
            throw InvariantViolationException("Tags cannot be assigned to an opening balance")
        }
        db.operationTagDao().insert(OperationTagEntity(operationId, tagId))
    }

    /** Balance of an account. SQLite "integer overflow" is mapped to [MoneyOverflowException]. */
    suspend fun balanceMinor(accountId: Long): Long = try {
        db.ledgerEntryDao().balanceMinor(accountId)
    } catch (e: SQLiteException) {
        if (e.message?.contains("integer overflow", ignoreCase = true) == true) {
            throw MoneyOverflowException("Balance overflow for account $accountId", e)
        }
        throw e
    }

    private suspend fun validate(
        type: OperationType,
        entries: List<EntryDraft>,
        categoryId: Long?,
        openingAccountId: Long?,
    ) {
        if (type != OperationType.OPENING_BALANCE && openingAccountId != null) {
            throw InvariantViolationException("opening_account_id is only for OPENING_BALANCE")
        }
        when (type) {
            OperationType.INCOME -> {
                requireEntries(entries, 1, type)
                if (entries.single().amountMinor <= 0) {
                    throw InvariantViolationException("Income entry must be positive")
                }
            }
            OperationType.EXPENSE -> {
                requireEntries(entries, 1, type)
                if (entries.single().amountMinor >= 0) {
                    throw InvariantViolationException("Expense entry must be negative")
                }
            }
            OperationType.TRANSFER -> {
                requireEntries(entries, 2, type)
                if (entries[0].accountId == entries[1].accountId) {
                    throw InvariantViolationException("Transfer must use two different accounts")
                }
                if (entries.any { it.amountMinor == 0L } ||
                    (entries[0].amountMinor > 0) == (entries[1].amountMinor > 0)
                ) {
                    throw InvariantViolationException("Transfer needs one negative and one positive entry")
                }
            }
            OperationType.OPENING_BALANCE -> {
                requireEntries(entries, 1, type)
                val entry = entries.single()
                if (openingAccountId == null) {
                    throw InvariantViolationException("Opening balance requires opening_account_id")
                }
                if (entry.accountId != openingAccountId) {
                    throw InvariantViolationException("Opening balance entry must be on opening_account_id")
                }
                if (entry.amountMinor == 0L) {
                    throw InvariantViolationException("Opening balance must be non-zero")
                }
                if (categoryId != null) {
                    throw InvariantViolationException("Opening balance has no category")
                }
            }
        }
        if (categoryId != null) {
            val category = db.categoryDao().getById(categoryId)
                ?: throw InvariantViolationException("Unknown category $categoryId")
            val expected = when (type) {
                OperationType.INCOME -> CategoryType.INCOME
                OperationType.EXPENSE -> CategoryType.EXPENSE
                OperationType.TRANSFER -> CategoryType.TRANSFER
                OperationType.OPENING_BALANCE -> null
            }
            if (category.type != expected) {
                throw InvariantViolationException("Category type does not match operation type")
            }
        }
    }

    private fun requireEntries(entries: List<EntryDraft>, count: Int, type: OperationType) {
        if (entries.size != count) {
            throw InvariantViolationException("$type needs exactly $count ledger entries")
        }
    }
}
