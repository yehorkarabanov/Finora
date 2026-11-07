package com.yehor.finora.data.db

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory

object FinoraDatabaseFactory {

    fun build(context: Context, name: String): FinoraDatabase =
        Room.databaseBuilder(context, FinoraDatabase::class.java, name)
            // Creation goes through a SupportSQLiteOpenHelper.Factory so SQLCipher can be added
            // later without schema or DAO changes. See design section 8.3 and 9.1.
            .openHelperFactory(FrameworkSQLiteOpenHelperFactory())
            .setJournalMode(androidx.room.RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .addCallback(FinoraDatabaseCallback)
            .addCallback(DefaultCategorySeedCallback)
            .addMigrations(*ALL_MIGRATIONS)
            .build()
}
