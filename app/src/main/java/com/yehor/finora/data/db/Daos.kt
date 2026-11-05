package com.yehor.finora.data.db

import androidx.paging.PagingSource
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.sqlite.db.SupportSQLiteQuery
import com.yehor.finora.domain.model.AccountStatus
import com.yehor.finora.domain.model.OperationType
import kotlinx.coroutines.flow.Flow

data class AccountWithBalanceRow(
    @Embedded val account: AccountEntity,
    @ColumnInfo(name = "balance_minor") val balanceMinor: Long
)

/** Raw row of [CategoryDao.directTotals]. `category_id` is null for Uncategorized. */
data class CategoryDirectTotalRow(
    @ColumnInfo(name = "category_id") val categoryId: Long?,
    @ColumnInfo(name = "currency_code") val currencyCode: String,
    @ColumnInfo(name = "total_minor") val totalMinor: Long,
    @ColumnInfo(name = "operation_count") val operationCount: Int,
)

@Dao
interface CurrencyDao {
    @Query("SELECT * FROM currency ORDER BY code")
    fun observeAll(): Flow<List<CurrencyEntity>>
}

@Dao
interface AccountDao {
    @Insert
    suspend fun insert(account: AccountEntity): Long

    @Query("SELECT * FROM account WHERE id = :id")
    suspend fun getById(id: Long): AccountEntity?

    @Query("SELECT * FROM account ORDER BY id")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT currency_code FROM account WHERE id = :id")
    suspend fun currencyCodeOf(id: Long): String?

    @Query("""
        SELECT a.*, COALESCE(SUM(e.amount_minor), 0) AS balance_minor
        FROM account a
        LEFT JOIN ledger_entry e ON e.account_id = a.id
        GROUP BY a.id
        ORDER BY a.id
    """)
    fun observeWithBalance(): Flow<List<AccountWithBalanceRow>>

    @Query("SELECT * FROM account WHERE status = 'ACTIVE' OR id = :originalAccountId ORDER BY id")
    fun observePickable(originalAccountId: Long): Flow<List<AccountEntity>>

    @Query("SELECT * FROM account WHERE id = :id")
    fun observeById(id: Long): Flow<AccountEntity?>

    @Query("UPDATE account SET name = :name, type = :type, icon = :icon, color = :color, description = :description WHERE id = :id")
    suspend fun updateDetails(id: Long, name: String, type: String, icon: String?, color: Long?, description: String?)

    @Query("UPDATE account SET status = :status WHERE id = :id")
    suspend fun setStatus(id: Long, status: AccountStatus)

    @Query("DELETE FROM account WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("""
        SELECT COUNT(*) FROM ledger_entry e
        INNER JOIN operation o ON e.operation_id = o.id
        WHERE e.account_id = :id AND o.type != 'OPENING_BALANCE'
    """)
    suspend fun nonOpeningEntryCount(id: Long): Int
}

@Dao
interface CategoryDao {
    @Insert
    suspend fun insert(category: CategoryEntity): Long

    @Query("SELECT * FROM category WHERE id = :id")
    suspend fun getById(id: Long): CategoryEntity?

    @Query("SELECT * FROM category ORDER BY id")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM category ORDER BY id")
    suspend fun getAll(): List<CategoryEntity>

    @Query("UPDATE category SET name = :name, icon = :icon, color = :color WHERE id = :id")
    suspend fun updateDetails(id: Long, name: String, icon: String?, color: Long?)

    @Query("UPDATE category SET parent_id = :parentId WHERE id = :id")
    suspend fun setParent(id: Long, parentId: Long?)

    @Query("UPDATE category SET parent_id = :toParentId WHERE parent_id = :fromParentId")
    suspend fun reparentChildren(fromParentId: Long, toParentId: Long?)

    @Query("UPDATE operation SET category_id = :toId, updated_at = :now WHERE category_id = :fromId")
    suspend fun reassignOperations(fromId: Long, toId: Long?, now: Long)

    @Query("SELECT COUNT(*) FROM operation WHERE category_id = :id")
    suspend fun operationCount(id: Long): Int

    @Query("SELECT COUNT(*) FROM category WHERE parent_id = :id")
    suspend fun childCount(id: Long): Int

    @Query("DELETE FROM category WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("""
        SELECT o.category_id      AS category_id,
               e.currency_code    AS currency_code,
               SUM(e.amount_minor) AS total_minor,
               COUNT(DISTINCT o.id) AS operation_count
        FROM operation o
        JOIN ledger_entry e ON e.operation_id = o.id
        WHERE o.type = :type AND o.occurred_at >= :from AND o.occurred_at < :to
        GROUP BY o.category_id, e.currency_code
    """)
    suspend fun directTotals(
        type: com.yehor.finora.domain.model.OperationType,
        from: Long,
        to: Long
    ): List<CategoryDirectTotalRow>
}

@Dao
interface TagDao {
    @Insert
    suspend fun insert(tag: TagEntity): Long

    @Query("SELECT * FROM tag ORDER BY name")
    fun observeAll(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tag ORDER BY name")
    suspend fun getAll(): List<TagEntity>

    @Query("SELECT * FROM tag WHERE id = :id")
    suspend fun getById(id: Long): TagEntity?

    @Query("""
        SELECT t.*, COUNT(ot.operation_id) AS usageCount
        FROM tag t
        LEFT JOIN operation_tag ot ON ot.tag_id = t.id
        GROUP BY t.id
        ORDER BY t.name
    """)
    fun observeAllWithUsage(): Flow<List<TagWithUsageRow>>

    @Query("UPDATE tag SET name = :name, color = :color WHERE id = :id")
    suspend fun updateDetails(id: Long, name: String, color: Long?)

    @Query("UPDATE tag SET color = :color WHERE id = :id")
    suspend fun setColor(id: Long, color: Long?)

    @Query("DELETE FROM tag WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM operation_tag WHERE tag_id = :id")
    suspend fun usageCount(id: Long): Int
}

@Dao
interface OperationDao {
    @Insert
    suspend fun insert(operation: OperationEntity): Long

    @Query("SELECT * FROM operation WHERE id = :id")
    suspend fun getById(id: Long): OperationEntity?

    @Query("UPDATE operation SET occurred_at = :occurredAt, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateOccurredAt(id: Long, occurredAt: Long, updatedAt: Long)

    @Query("SELECT * FROM operation ORDER BY occurred_at DESC, id DESC")
    fun observeAll(): Flow<List<OperationEntity>>

    @Query("SELECT * FROM operation WHERE opening_account_id = :accountId LIMIT 1")
    suspend fun openingBalanceOf(accountId: Long): OperationEntity?

    @Query("DELETE FROM operation WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Transaction
    @RawQuery(observedEntities = [OperationEntity::class, LedgerEntryEntity::class, OperationTagEntity::class, TagEntity::class])
    fun page(query: SupportSQLiteQuery): PagingSource<Int, OperationWithDetails>

    @Transaction
    @RawQuery(observedEntities = [OperationEntity::class, LedgerEntryEntity::class, OperationTagEntity::class, TagEntity::class])
    fun listWithDetails(query: SupportSQLiteQuery): Flow<List<OperationWithDetails>>

    @RawQuery
    suspend fun summarize(query: SupportSQLiteQuery): List<SummaryRow>

    @Transaction
    @Query("SELECT * FROM operation WHERE id = :id")
    fun observeWithDetails(id: Long): Flow<OperationWithDetails?>

    @Query("UPDATE operation SET occurred_at = :occurredAt, category_id = :categoryId, description = :description, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateFields(id: Long, occurredAt: Long, categoryId: Long?, description: String?, updatedAt: Long)

    @Query("""
        SELECT DISTINCT category_id FROM operation
        WHERE type = :type AND category_id IS NOT NULL
        ORDER BY occurred_at DESC, id DESC LIMIT :limit
    """)
    suspend fun recentCategoryIds(type: OperationType, limit: Int): List<Long>

    @Query("""
        SELECT e.account_id FROM operation o
        JOIN ledger_entry e ON e.operation_id = o.id
        WHERE o.type IN ('INCOME', 'EXPENSE')
        ORDER BY o.occurred_at DESC, o.id DESC LIMIT 1
    """)
    suspend fun latestAccountId(): Long?
}

@Dao
interface LedgerEntryDao {
    @Insert
    suspend fun insert(entry: LedgerEntryEntity): Long

    @Query("SELECT * FROM ledger_entry WHERE operation_id = :operationId ORDER BY id")
    suspend fun entriesOf(operationId: Long): List<LedgerEntryEntity>

    @Query("UPDATE ledger_entry SET occurred_at = :occurredAt WHERE operation_id = :operationId")
    suspend fun updateOccurredAt(operationId: Long, occurredAt: Long)

    /** Throws `SQLiteException` ("integer overflow") when the sum exceeds the range of Long. */
    @Query("SELECT COALESCE(SUM(amount_minor), 0) FROM ledger_entry WHERE account_id = :accountId")
    suspend fun balanceMinor(accountId: Long): Long

    @Query("UPDATE ledger_entry SET amount_minor = :amountMinor WHERE operation_id = :operationId")
    suspend fun updateAmount(operationId: Long, amountMinor: Long)

    @Query("UPDATE ledger_entry SET account_id = :accountId, currency_code = :currencyCode, amount_minor = :amountMinor, occurred_at = :occurredAt WHERE operation_id = :operationId")
    suspend fun updateEntry(operationId: Long, accountId: Long, currencyCode: String, amountMinor: Long, occurredAt: Long)

    @Query("UPDATE ledger_entry SET account_id = :accountId, currency_code = :currencyCode, amount_minor = :amountMinor, occurred_at = :occurredAt WHERE id = :entryId")
    suspend fun updateEntryById(entryId: Long, accountId: Long, currencyCode: String, amountMinor: Long, occurredAt: Long)

    @Query("""
        SELECT MIN(e.occurred_at) FROM ledger_entry e
        INNER JOIN operation o ON e.operation_id = o.id
        WHERE e.account_id = :accountId AND o.type != 'OPENING_BALANCE'
    """)
    suspend fun earliestNonOpeningEntryAt(accountId: Long): Long?

    @Query("""
        SELECT occurred_at FROM operation
        WHERE opening_account_id = :accountId LIMIT 1
    """)
    suspend fun openingDateOf(accountId: Long): Long?
}

@Dao
interface OperationTagDao {
    @Insert
    suspend fun insert(link: OperationTagEntity)

    @Query("SELECT tag_id FROM operation_tag WHERE operation_id = :operationId ORDER BY tag_id")
    suspend fun tagIdsOf(operationId: Long): List<Long>

    @Query("DELETE FROM operation_tag WHERE operation_id = :operationId")
    suspend fun deleteAllFor(operationId: Long)
}

@Dao
interface AppSettingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(setting: AppSettingEntity)

    @Query("DELETE FROM app_setting WHERE `key` = :key")
    suspend fun delete(key: String)

    @Query("SELECT value FROM app_setting WHERE `key` = :key")
    fun observe(key: String): Flow<String?>

    @Query("SELECT value FROM app_setting WHERE `key` = :key")
    suspend fun get(key: String): String?
}
