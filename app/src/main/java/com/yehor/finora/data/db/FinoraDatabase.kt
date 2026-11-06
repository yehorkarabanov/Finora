package com.yehor.finora.data.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.yehor.finora.domain.money.CurrencyRegistry

@Database(
    entities = [
        CurrencyEntity::class,
        AccountEntity::class,
        CategoryEntity::class,
        TagEntity::class,
        OperationEntity::class,
        LedgerEntryEntity::class,
        OperationTagEntity::class,
        AppSettingEntity::class,
        ExchangeRateEntity::class,
    ],
    version = FinoraDatabase.VERSION,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class FinoraDatabase : RoomDatabase() {
    abstract fun currencyDao(): CurrencyDao
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun tagDao(): TagDao
    abstract fun operationDao(): OperationDao
    abstract fun ledgerEntryDao(): LedgerEntryDao
    abstract fun operationTagDao(): OperationTagDao
    abstract fun appSettingDao(): AppSettingDao
    abstract fun rateDao(): RateDao

    companion object {
        const val VERSION = 2
        const val FILE_NAME = "finora.db"
    }
}

/**
 * Seeds the `currency` table from [CurrencyRegistry] on creation and keeps `foreign_keys=ON`
 * on every open. Currencies added later arrive through migrations.
 */
object FinoraDatabaseCallback : RoomDatabase.Callback() {

    override fun onCreate(db: SupportSQLiteDatabase) {
        CurrencyRegistry.all.forEach { c ->
            db.execSQL(
                "INSERT INTO currency (code, scale, symbol, name) VALUES (?, ?, ?, ?)",
                arrayOf<Any>(c.code, c.scale, c.symbol, c.name),
            )
        }
    }

    override fun onOpen(db: SupportSQLiteDatabase) {
        db.execSQL("PRAGMA foreign_keys = ON")
    }
}
