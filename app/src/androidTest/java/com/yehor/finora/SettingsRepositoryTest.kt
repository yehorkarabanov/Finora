package com.yehor.finora

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.yehor.finora.data.db.FinoraDatabase
import com.yehor.finora.data.db.FinoraDatabaseCallback
import com.yehor.finora.data.repo.SettingsRepository
import com.yehor.finora.domain.model.AccountStatus
import com.yehor.finora.domain.model.ThemeMode
import com.yehor.finora.data.db.AccountEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SettingsRepositoryTest {

    private lateinit var db: FinoraDatabase
    private lateinit var repo: SettingsRepository

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, FinoraDatabase::class.java)
            .addCallback(FinoraDatabaseCallback)
            .build()
        repo = SettingsRepository(db.appSettingDao(), db.accountDao())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun themeDefaultsToSystemAndRoundTrips() = runBlocking {
        assertEquals(ThemeMode.SYSTEM, repo.themeMode.first())
        repo.setThemeMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, repo.themeMode.first())
        assertEquals("dark", db.appSettingDao().get("theme_mode"))
        repo.setThemeMode(ThemeMode.LIGHT)
        assertEquals(ThemeMode.LIGHT, repo.themeMode.first())
    }

    @Test
    fun resolvedDisplayCurrencyIsNeverStored() = runBlocking {
        val resolved = repo.displayCurrency.first()
        assertTrue(resolved.isAutomatic)
        assertNull(db.appSettingDao().get("display_currency"))

        db.accountDao().insert(
            AccountEntity(
                name = "a", currencyCode = "PLN", type = "CASH", icon = null, color = null,
                description = null, status = AccountStatus.ACTIVE, createdAt = 0,
            ),
        )
        val fromAccount = repo.displayCurrency.first()
        assertEquals("PLN", fromAccount.currency.code)
        assertTrue(fromAccount.isAutomatic)
        assertNull(db.appSettingDao().get("display_currency"))
    }

    @Test
    fun explicitDisplayCurrencyWinsAndIsStored() = runBlocking {
        repo.setDisplayCurrency("JPY")
        val resolved = repo.displayCurrency.first()
        assertEquals("JPY", resolved.currency.code)
        assertFalse(resolved.isAutomatic)
        assertEquals("JPY", db.appSettingDao().get("display_currency"))
    }
}
