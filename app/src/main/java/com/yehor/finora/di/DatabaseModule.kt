package com.yehor.finora.di

import android.content.Context
import com.yehor.finora.data.db.AccountDao
import com.yehor.finora.data.db.AppSettingDao
import com.yehor.finora.data.db.FinoraDatabase
import com.yehor.finora.data.db.FinoraDatabaseFactory
import com.yehor.finora.data.db.RateDao
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
        FinoraDatabaseFactory.build(context, FinoraDatabase.FILE_NAME)

    @Provides
    fun provideAppSettingDao(db: FinoraDatabase): AppSettingDao = db.appSettingDao()

    @Provides
    fun provideAccountDao(db: FinoraDatabase): AccountDao = db.accountDao()

    @Provides
    fun provideRateDao(db: FinoraDatabase): RateDao = db.rateDao()

    @Provides
    fun provideRecurringDao(db: FinoraDatabase): com.yehor.finora.data.db.RecurringDao = db.recurringDao()
}
