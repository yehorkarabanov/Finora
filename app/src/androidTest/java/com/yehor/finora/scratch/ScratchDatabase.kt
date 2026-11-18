package com.yehor.finora.scratch

import androidx.room.ColumnInfo
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.yehor.finora.data.db.AccountEntity
import com.yehor.finora.data.db.CategoryEntity
import com.yehor.finora.data.db.CurrencyEntity
import com.yehor.finora.data.db.LedgerEntryEntity
import com.yehor.finora.data.db.OperationEntity
import com.yehor.finora.data.db.OperationTagEntity
import com.yehor.finora.data.db.TagEntity

/**
 * TEST-ONLY. Version 2 of the production schema plus one extra column on `app_setting`.
 * Exists only to prove the migration test infrastructure works. It is not referenced from main,
 * from `Migrations.kt`, from `app/schemas`, or from the production migration chain.
 */
@Entity(tableName = "app_setting")
data class ScratchAppSettingEntity(
    @PrimaryKey val key: String,
    val value: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    val note: String?,
)

@Database(
    entities = [
        CurrencyEntity::class,
        AccountEntity::class,
        CategoryEntity::class,
        TagEntity::class,
        OperationEntity::class,
        LedgerEntryEntity::class,
        OperationTagEntity::class,
        ScratchAppSettingEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class ScratchDatabase : RoomDatabase()

/** Applied only by the scratch migration test through `MigrationTestHelper`. */
object ScratchMigration_1_2 : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE app_setting ADD COLUMN note TEXT")
    }
}
