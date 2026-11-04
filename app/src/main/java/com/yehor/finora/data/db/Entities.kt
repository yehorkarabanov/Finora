package com.yehor.finora.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.yehor.finora.domain.model.AccountStatus
import com.yehor.finora.domain.model.CategoryType
import com.yehor.finora.domain.model.OperationType

@Entity(tableName = "currency")
data class CurrencyEntity(
    @PrimaryKey val code: String,
    val scale: Int,
    val symbol: String,
    val name: String,
)

/**
 * The unique index on `(id, currency_code)` is the parent key of the composite foreign key on
 * `ledger_entry`, which makes entry currency always equal account currency.
 */
@Entity(
    tableName = "account",
    foreignKeys = [
        ForeignKey(
            entity = CurrencyEntity::class,
            parentColumns = ["code"],
            childColumns = ["currency_code"],
        ),
    ],
    indices = [
        Index(value = ["id", "currency_code"], unique = true),
        Index(value = ["currency_code"]),
    ],
)
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "currency_code") val currencyCode: String,
    val type: String,
    val icon: String?,
    val color: Long?,
    val description: String?,
    val status: AccountStatus,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(
    tableName = "category",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["parent_id"],
        ),
    ],
    indices = [Index(value = ["parent_id"])],
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String?,
    val color: Long?,
    val type: CategoryType,
    @ColumnInfo(name = "parent_id") val parentId: Long?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(tableName = "tag", indices = [Index(value = ["name"], unique = true)])
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: Long?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/**
 * `opening_account_id` is set if and only if [type] is [OperationType.OPENING_BALANCE]. Its unique
 * index allows at most one opening balance per account (SQLite permits many NULLs).
 */
@Entity(
    tableName = "operation",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["opening_account_id"],
        ),
    ],
    indices = [
        Index(value = ["type", "occurred_at"]),
        Index(value = ["category_id", "occurred_at"]),
        Index(value = ["opening_account_id"], unique = true),
    ],
)
data class OperationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: OperationType,
    @ColumnInfo(name = "occurred_at") val occurredAt: Long,
    @ColumnInfo(name = "category_id") val categoryId: Long?,
    val description: String?,
    @ColumnInfo(name = "opening_account_id") val openingAccountId: Long?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/**
 * `occurred_at` is a denormalized copy of the parent operation's `occurred_at`, kept so the
 * covering index can answer balance-over-time queries without a join. It must always match.
 */
@Entity(
    tableName = "ledger_entry",
    foreignKeys = [
        ForeignKey(
            entity = OperationEntity::class,
            parentColumns = ["id"],
            childColumns = ["operation_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id", "currency_code"],
            childColumns = ["account_id", "currency_code"],
        ),
    ],
    indices = [
        Index(value = ["account_id", "occurred_at", "amount_minor"]),
        Index(value = ["operation_id"]),
    ],
)
data class LedgerEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "operation_id") val operationId: Long,
    @ColumnInfo(name = "account_id") val accountId: Long,
    @ColumnInfo(name = "currency_code") val currencyCode: String,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    @ColumnInfo(name = "occurred_at") val occurredAt: Long,
)

@Entity(
    tableName = "operation_tag",
    primaryKeys = ["operation_id", "tag_id"],
    foreignKeys = [
        ForeignKey(
            entity = OperationEntity::class,
            parentColumns = ["id"],
            childColumns = ["operation_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tag_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["tag_id", "operation_id"])],
)
data class OperationTagEntity(
    @ColumnInfo(name = "operation_id") val operationId: Long,
    @ColumnInfo(name = "tag_id") val tagId: Long,
)

@Entity(tableName = "app_setting")
data class AppSettingEntity(
    @PrimaryKey val key: String,
    val value: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
