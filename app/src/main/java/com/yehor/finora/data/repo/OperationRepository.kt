package com.yehor.finora.data.repo

import android.database.sqlite.SQLiteException
import androidx.room.withTransaction
import com.yehor.finora.data.db.FinoraDatabase
import com.yehor.finora.data.db.LedgerEntryEntity
import com.yehor.finora.data.db.OperationEntity
import com.yehor.finora.data.db.OperationTagEntity
import com.yehor.finora.data.db.TagEntity
import com.yehor.finora.domain.account.OpeningDatePolicy
import com.yehor.finora.domain.model.AccountStatus
import com.yehor.finora.domain.model.CategoryType
import com.yehor.finora.domain.model.OperationType
import com.yehor.finora.domain.money.MoneyOverflowException
import com.yehor.finora.domain.tag.TagNameRules
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** One signed ledger line of an operation to be written. */
data class EntryDraft(val accountId: Long, val amountMinor: Long)

/** Raised when a write would violate an invariant that the schema cannot express. */
open class InvariantViolationException(message: String) : IllegalArgumentException(message)

/**
 * The single write path for operations and their ledger entries. Invariants that Room cannot
 * express as constraints (section 6.4 of the design) are enforced here, inside one transaction.
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
        tagIds: Set<Long> = emptySet(),
        newTagNames: List<String> = emptyList(),
        moveOpeningBalanceBack: Boolean = false,
        now: Long = System.currentTimeMillis(),
    ): Long = db.withTransaction {
        if (type == OperationType.INCOME || type == OperationType.EXPENSE) {
            if (occurredAt > now) throw FutureDateException(occurredAt, now)
        }

        // Reject if any of the entry accounts are archived (S4.7)
        entries.forEach { e ->
            val account = db.accountDao().getById(e.accountId)
                ?: throw InvariantViolationException("Unknown account ${e.accountId}")
            if (account.status == AccountStatus.ARCHIVED) {
                throw InvariantViolationException("Account ${e.accountId} is archived")
            }
        }

        // Reject a non-opening entry dated before its account's opening date (S4.6)
        if (type != OperationType.OPENING_BALANCE) {
            entries.forEach { e ->
                enforceOpeningOrder(e.accountId, occurredAt, moveOpeningBalanceBack, now)
            }
        }

        // Pre-check balance overflow for affected accounts
        entries.forEach { e ->
            val currentBalance = balanceMinor(e.accountId)
            try {
                Math.addExact(currentBalance, e.amountMinor)
            } catch (_: ArithmeticException) {
                throw MoneyOverflowException("Balance overflow for account ${e.accountId}")
            }
        }

        validate(type, entries, categoryId, openingAccountId)

        val operationId = db.operationDao().insert(
            OperationEntity(
                type = type,
                occurredAt = occurredAt,
                categoryId = categoryId,
                description = description?.trim()?.takeIf { it.isNotEmpty() },
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

        if (tagIds.isNotEmpty() || newTagNames.isNotEmpty()) {
            if (type == OperationType.OPENING_BALANCE) {
                throw InvariantViolationException("Tags cannot be assigned to an opening balance")
            }
            resolveAndLinkTags(operationId, tagIds, newTagNames, now)
        }

        operationId
    }

    /**
     * Rewrites an INCOME or EXPENSE operation. [amountMinor] is the positive magnitude in the
     * currency of the target account. A currency change needs [confirmCurrencyChange] (T12).
     */
    suspend fun updateOperation(
        id: Long,
        accountId: Long,
        amountMinor: Long,
        categoryId: Long?,
        occurredAt: Long,
        description: String?,
        tagIds: Set<Long> = emptySet(),
        newTagNames: List<String> = emptyList(),
        moveOpeningBalanceBack: Boolean = false,
        confirmCurrencyChange: Boolean = false,
        now: Long = System.currentTimeMillis(),
    ) = db.withTransaction {
        val op = db.operationDao().getById(id)
            ?: throw InvariantViolationException("Unknown operation $id")
        if (op.type != OperationType.INCOME && op.type != OperationType.EXPENSE) {
            throw InvariantViolationException("Only INCOME and EXPENSE operations can be updated here")
        }
        if (occurredAt > now) throw FutureDateException(occurredAt, now)
        if (amountMinor <= 0) throw InvariantViolationException("Amount must be positive")

        val oldEntry = db.ledgerEntryDao().entriesOf(id).single()
        val targetAccount = db.accountDao().getById(accountId)
            ?: throw InvariantViolationException("Unknown account $accountId")

        // The target may be archived only when it is the operation's own original account.
        if (targetAccount.status == AccountStatus.ARCHIVED && targetAccount.id != oldEntry.accountId) {
            throw InvariantViolationException("Cannot move operation to a different archived account")
        }

        // T12 cross-currency check
        val targetCurrency = targetAccount.currencyCode
        if (targetCurrency != oldEntry.currencyCode && !confirmCurrencyChange) {
            throw CurrencyChangeNotConfirmedException(oldEntry.currencyCode, targetCurrency)
        }

        enforceOpeningOrder(accountId, occurredAt, moveOpeningBalanceBack, now)

        val signedAmount = if (op.type == OperationType.INCOME) amountMinor else -amountMinor

        // Pre-check balance overflow
        try {
            if (accountId == oldEntry.accountId) {
                Math.addExact(balanceMinor(accountId), signedAmount - oldEntry.amountMinor)
            } else {
                Math.addExact(balanceMinor(oldEntry.accountId), -oldEntry.amountMinor)
                Math.addExact(balanceMinor(accountId), signedAmount)
            }
        } catch (_: ArithmeticException) {
            throw MoneyOverflowException("Balance overflow for account $accountId")
        }

        validateCategoryType(op.type, categoryId)

        db.operationDao().updateFields(
            id = id,
            occurredAt = occurredAt,
            categoryId = categoryId,
            description = description?.trim()?.takeIf { it.isNotEmpty() },
            updatedAt = now,
        )
        db.ledgerEntryDao().updateEntry(
            operationId = id,
            accountId = accountId,
            currencyCode = targetCurrency,
            amountMinor = signedAmount,
            occurredAt = occurredAt,
        )

        db.operationTagDao().deleteAllFor(id)
        if (tagIds.isNotEmpty() || newTagNames.isNotEmpty()) {
            resolveAndLinkTags(id, tagIds, newTagNames, now)
        }
    }

    suspend fun deleteOperation(id: Long) = db.withTransaction {
        val op = db.operationDao().getById(id)
            ?: throw InvariantViolationException("Unknown operation $id")
        if (op.type != OperationType.INCOME && op.type != OperationType.EXPENSE) {
            throw InvariantViolationException("Only INCOME and EXPENSE operations can be deleted")
        }
        db.operationDao().deleteById(id)
    }

    suspend fun setTags(
        operationId: Long,
        tagIds: Set<Long>,
        newTagNames: List<String> = emptyList(),
        now: Long = System.currentTimeMillis(),
    ) = db.withTransaction {
        val op = db.operationDao().getById(operationId)
            ?: throw InvariantViolationException("Unknown operation $operationId")
        if (op.type == OperationType.OPENING_BALANCE) {
            throw InvariantViolationException("Tags cannot be assigned to an opening balance")
        }
        db.operationTagDao().deleteAllFor(operationId)
        resolveAndLinkTags(operationId, tagIds, newTagNames, now)
    }

    /** Moves the operation and every one of its ledger entries to [occurredAt] together. */
    suspend fun changeOccurredAt(
        operationId: Long,
        occurredAt: Long,
        now: Long = System.currentTimeMillis(),
    ) = db.withTransaction {
        val op = db.operationDao().getById(operationId)
            ?: throw InvariantViolationException("Unknown operation $operationId")
        if (op.type == OperationType.INCOME || op.type == OperationType.EXPENSE) {
            if (occurredAt > now) throw FutureDateException(occurredAt, now)
        }

        if (op.type == OperationType.OPENING_BALANCE) {
            val accountId = op.openingAccountId
                ?: throw InvariantViolationException("Opening balance operation has no openingAccountId")
            val earliestOther = db.ledgerEntryDao().earliestNonOpeningEntryAt(accountId)
            if (earliestOther != null && occurredAt > earliestOther) {
                throw OpeningBalanceOrderException(accountId, occurredAt, earliestOther)
            }
        } else {
            val entries = db.ledgerEntryDao().entriesOf(operationId)
            entries.forEach { e ->
                val openingDate = db.ledgerEntryDao().openingDateOf(e.accountId)
                if (openingDate != null && occurredAt < openingDate) {
                    throw OpeningBalanceOrderException(e.accountId, openingDate, occurredAt)
                }
            }
        }

        db.operationDao().updateOccurredAt(operationId, occurredAt, now)
        db.ledgerEntryDao().updateOccurredAt(operationId, occurredAt)
    }

    suspend fun setOpeningBalance(
        accountId: Long,
        amountMinor: Long?,
        occurredAt: Long,
        now: Long = System.currentTimeMillis(),
    ): Unit = db.withTransaction {
        val openingOp = db.operationDao().openingBalanceOf(accountId)
        if (amountMinor == null || amountMinor == 0L) {
            if (openingOp != null) {
                db.operationDao().deleteById(openingOp.id)
            }
            return@withTransaction
        }

        val earliestOther = db.ledgerEntryDao().earliestNonOpeningEntryAt(accountId)
        if (earliestOther != null && occurredAt > earliestOther) {
            throw OpeningBalanceOrderException(accountId, occurredAt, earliestOther)
        }

        if (openingOp == null) {
            insertOperation(
                type = OperationType.OPENING_BALANCE,
                occurredAt = occurredAt,
                entries = listOf(EntryDraft(accountId, amountMinor)),
                openingAccountId = accountId,
                now = now,
            )
        } else {
            db.ledgerEntryDao().updateAmount(openingOp.id, amountMinor)
            changeOccurredAt(openingOp.id, occurredAt, now)
        }
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

    private suspend fun resolveAndLinkTags(
        operationId: Long,
        tagIds: Set<Long>,
        newTagNames: List<String>,
        now: Long,
    ) {
        val existingIds = db.tagDao().getAll().map { it.id }.toSet()
        tagIds.forEach { id ->
            if (id !in existingIds) throw InvariantViolationException("Unknown tag id $id")
        }

        val resolvedIds = tagIds.toMutableSet()
        newTagNames.map { TagNameRules.normalize(it) }.distinct().forEach { name ->
            val error = TagNameRules.validate(name)
            if (error != null) throw InvalidTagNameException(error)
            val key = TagNameRules.key(name)
            val matched = db.tagDao().getAll().find { TagNameRules.key(it.name) == key }
            val id = matched?.id
                ?: db.tagDao().insert(TagEntity(name = name, color = null, createdAt = now))
            resolvedIds.add(id)
        }

        resolvedIds.forEach { tagId ->
            db.operationTagDao().insert(OperationTagEntity(operationId, tagId))
        }
    }

    private suspend fun enforceOpeningOrder(
        accountId: Long,
        occurredAt: Long,
        moveOpeningBalanceBack: Boolean,
        now: Long,
    ) {
        val openingDate = db.ledgerEntryDao().openingDateOf(accountId)
        if (openingDate != null && occurredAt < openingDate) {
            if (moveOpeningBalanceBack) {
                val startOfLocalDay = OpeningDatePolicy(ZoneId.systemDefault()).startOfDay(occurredAt)
                val openingOp = db.operationDao().openingBalanceOf(accountId)
                if (openingOp != null) {
                    db.operationDao().updateOccurredAt(openingOp.id, startOfLocalDay, now)
                    db.ledgerEntryDao().updateOccurredAt(openingOp.id, startOfLocalDay)
                }
            } else {
                throw OpeningBalanceOrderException(accountId, openingDate, occurredAt)
            }
        }
    }

    private suspend fun validateCategoryType(type: OperationType, categoryId: Long?) {
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
        validateCategoryType(type, categoryId)
    }

    private fun requireEntries(entries: List<EntryDraft>, count: Int, type: OperationType) {
        if (entries.size != count) {
            throw InvariantViolationException("$type needs exactly $count ledger entries")
        }
    }
}
