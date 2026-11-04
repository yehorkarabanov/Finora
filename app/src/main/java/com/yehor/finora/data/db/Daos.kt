package com.yehor.finora.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

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
}

@Dao
interface CategoryDao {
    @Insert
    suspend fun insert(category: CategoryEntity): Long

    @Query("SELECT * FROM category WHERE id = :id")
    suspend fun getById(id: Long): CategoryEntity?

    @Query("SELECT * FROM category ORDER BY id")
    fun observeAll(): Flow<List<CategoryEntity>>
}

@Dao
interface TagDao {
    @Insert
    suspend fun insert(tag: TagEntity): Long

    @Query("SELECT * FROM tag ORDER BY name")
    fun observeAll(): Flow<List<TagEntity>>
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
}

@Dao
interface OperationTagDao {
    @Insert
    suspend fun insert(link: OperationTagEntity)

    @Query("SELECT tag_id FROM operation_tag WHERE operation_id = :operationId ORDER BY tag_id")
    suspend fun tagIdsOf(operationId: Long): List<Long>
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
