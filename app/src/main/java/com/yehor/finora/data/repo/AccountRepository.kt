package com.yehor.finora.data.repo

import androidx.room.withTransaction
import com.yehor.finora.data.db.AccountEntity
import com.yehor.finora.data.db.FinoraDatabase
import com.yehor.finora.domain.account.Account
import com.yehor.finora.domain.account.AccountDetail
import com.yehor.finora.domain.account.AccountType
import com.yehor.finora.domain.account.AccountWithBalance
import com.yehor.finora.domain.account.Balance
import com.yehor.finora.domain.account.OpeningBalance
import com.yehor.finora.domain.account.OpeningDatePolicy
import com.yehor.finora.domain.account.PickableAccount
import com.yehor.finora.domain.model.AccountStatus
import com.yehor.finora.domain.money.CurrencyRegistry
import com.yehor.finora.domain.money.Money
import com.yehor.finora.domain.money.MoneyOverflowException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class AccountRepository @Inject constructor(
    private val db: FinoraDatabase,
    private val operationRepo: OperationRepository,
) {

    private fun AccountEntity.toDomain(): Account = Account(
        id = id,
        name = name,
        currency = CurrencyRegistry.get(currencyCode),
        type = AccountType.fromStorage(type),
        icon = icon,
        color = color,
        description = description,
        status = status,
        createdAt = createdAt,
    )

    fun observeAll(): Flow<List<AccountWithBalance>> {
        return db.accountDao().observeWithBalance()
            .map { rows ->
                rows.map { row ->
                    val currency = CurrencyRegistry.get(row.account.currencyCode)
                    val balance = try {
                        Balance.Value(Money(row.balanceMinor, currency))
                    } catch (e: MoneyOverflowException) {
                        Balance.Overflow
                    }
                    AccountWithBalance(row.account.toDomain(), balance)
                }
            }
            .catch { e ->
                if (e is android.database.sqlite.SQLiteException && e.message?.contains("integer overflow", ignoreCase = true) == true) {
                    emitAll(observeAllFallback())
                } else {
                    throw e
                }
            }
    }

    private fun observeAllFallback(): Flow<List<AccountWithBalance>> {
        return db.accountDao().observeAll().flatMapLatest { accounts ->
            flow {
                val list = accounts.map { entity ->
                    val currency = CurrencyRegistry.get(entity.currencyCode)
                    val balance = try {
                        Balance.Value(Money(operationRepo.balanceMinor(entity.id), currency))
                    } catch (e: MoneyOverflowException) {
                        Balance.Overflow
                    }
                    AccountWithBalance(entity.toDomain(), balance)
                }
                emit(list)
            }
        }
    }

    fun observePickable(originalAccountId: Long?): Flow<List<PickableAccount>> {
        val targetId = originalAccountId ?: -1L
        return db.accountDao().observePickable(targetId).map { list ->
            list.map { entity ->
                val isArchivedOriginal = entity.id == originalAccountId && entity.status == AccountStatus.ARCHIVED
                PickableAccount(entity.toDomain(), isArchivedOriginal)
            }
        }
    }

    fun observe(id: Long): Flow<AccountDetail?> {
        return combine(
            db.accountDao().observeById(id),
            db.operationDao().observeAll(),
        ) { entity, _ -> entity }.flatMapLatest { entity ->
            flow {
                if (entity == null) {
                    emit(null)
                } else {
                    val currency = CurrencyRegistry.get(entity.currencyCode)
                    val balance = try {
                        Balance.Value(Money(operationRepo.balanceMinor(entity.id), currency))
                    } catch (e: MoneyOverflowException) {
                        Balance.Overflow
                    }

                    val openingOp = db.operationDao().openingBalanceOf(entity.id)
                    val openingBalance = if (openingOp != null) {
                        val entries = db.ledgerEntryDao().entriesOf(openingOp.id)
                        val entry = entries.firstOrNull()
                        if (entry != null) {
                            OpeningBalance(
                                operationId = openingOp.id,
                                money = Money(entry.amountMinor, currency),
                                occurredAt = openingOp.occurredAt,
                            )
                        } else null
                    } else null

                    val earliestOther = db.ledgerEntryDao().earliestNonOpeningEntryAt(entity.id)
                    emit(
                        AccountDetail(
                            account = entity.toDomain(),
                            balance = balance,
                            openingBalance = openingBalance,
                            earliestOtherEntryAt = earliestOther,
                        ),
                    )
                }
            }
        }
    }

    suspend fun create(
        name: String,
        currencyCode: String,
        type: String,
        icon: String?,
        color: Long?,
        description: String?,
        openingMinor: Long?,
        openingDate: Long?,
        now: Long = System.currentTimeMillis(),
    ): Long = db.withTransaction {
        val accountId = db.accountDao().insert(
            AccountEntity(
                name = name,
                currencyCode = currencyCode,
                type = type,
                icon = icon,
                color = color,
                description = description,
                status = AccountStatus.ACTIVE,
                createdAt = now,
            ),
        )
        if (openingMinor != null && openingMinor != 0L) {
            val opDate = openingDate ?: OpeningDatePolicy(ZoneId.systemDefault()).startOfDay(now)
            operationRepo.setOpeningBalance(accountId, openingMinor, opDate, now)
        }
        accountId
    }

    suspend fun update(
        id: Long,
        name: String,
        type: String,
        icon: String?,
        color: Long?,
        description: String?,
    ) {
        db.accountDao().updateDetails(id, name, type, icon, color, description)
    }

    /**
     * Edit form save: details and the opening balance in one transaction. [openingMinor] null or 0
     * removes the opening balance. Throws [OpeningBalanceOrderException] for a date after the
     * earliest other entry, and rejects adding a new opening balance to an archived account.
     */
    suspend fun updateWithOpeningBalance(
        id: Long,
        name: String,
        type: String,
        icon: String?,
        color: Long?,
        description: String?,
        openingMinor: Long?,
        openingDate: Long,
        now: Long = System.currentTimeMillis(),
    ) = db.withTransaction {
        update(id, name, type, icon, color, description)
        operationRepo.setOpeningBalance(id, openingMinor, openingDate, now)
    }

    /** Number of ledger entries other than the account's own opening balance (rule A5). */
    suspend fun nonOpeningEntryCount(id: Long): Int = db.accountDao().nonOpeningEntryCount(id)

    suspend fun setArchived(id: Long, archived: Boolean) {
        val status = if (archived) AccountStatus.ARCHIVED else AccountStatus.ACTIVE
        db.accountDao().setStatus(id, status)
    }

    /**
     * Deletes the account together with all its transactions and its opening balance. A transfer
     * touching the account is deleted as a whole, so the other account loses that entry too.
     */
    suspend fun delete(id: Long) = db.withTransaction {
        db.operationDao().deleteAllTouchingAccount(id)
        db.accountDao().deleteById(id)
    }
}
