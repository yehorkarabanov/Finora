package com.yehor.finora.di

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.yehor.finora.data.db.ALL_MIGRATIONS
import com.yehor.finora.data.db.AccountDao
import com.yehor.finora.data.db.AppSettingDao
import com.yehor.finora.data.db.FinoraDatabase
import com.yehor.finora.data.db.RateDao
import com.yehor.finora.data.db.DefaultCategorySeedCallback
import com.yehor.finora.data.db.FinoraDatabaseCallback
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): FinoraDatabase =
        Room.databaseBuilder(context, FinoraDatabase::class.java, FinoraDatabase.FILE_NAME)
            // Creation goes through a SupportSQLiteOpenHelper.Factory so SQLCipher can be added
            // later without schema or DAO changes. See design section 8.3 and 9.1.
            .openHelperFactory(FrameworkSQLiteOpenHelperFactory())
            .setJournalMode(androidx.room.RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .addCallback(FinoraDatabaseCallback)
            .addCallback(DefaultCategorySeedCallback)
            .addMigrations(*ALL_MIGRATIONS)
            .build()

    @Provides
    fun provideAppSettingDao(db: FinoraDatabase): AppSettingDao = db.appSettingDao()

    @Provides
    fun provideAccountDao(db: FinoraDatabase): AccountDao = db.accountDao()

    @Provides
    fun provideRateDao(db: FinoraDatabase): RateDao = db.rateDao()

    @Provides
    fun provideRecurringDao(db: FinoraDatabase): com.yehor.finora.data.db.RecurringDao = db.recurringDao()
}
