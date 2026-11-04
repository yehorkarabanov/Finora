package com.yehor.finora.data.repo

import com.yehor.finora.data.db.AccountDao
import com.yehor.finora.data.db.AppSettingDao
import com.yehor.finora.data.db.AppSettingEntity
import com.yehor.finora.domain.model.ThemeMode
import com.yehor.finora.domain.money.CurrencyRegistry
import com.yehor.finora.domain.settings.AccountCurrencyInfo
import com.yehor.finora.domain.settings.DisplayCurrencyResolver
import com.yehor.finora.domain.settings.ResolvedDisplayCurrency
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Typed access to the `app_setting` table. DataStore is not used. */
@Singleton
class SettingsRepository @Inject constructor(
    private val settings: AppSettingDao,
    accounts: AccountDao,
) {
    val themeMode: Flow<ThemeMode> =
        settings.observe(KEY_THEME_MODE).map { ThemeMode.fromStorage(it) }.distinctUntilChanged()

    /** The explicit user choice only. The resolved fallback is never stored. */
    val explicitDisplayCurrencyCode: Flow<String?> =
        settings.observe(KEY_DISPLAY_CURRENCY).distinctUntilChanged()

    /** The effective Display Currency, following the user's accounts until they pick one. */
    val displayCurrency: Flow<ResolvedDisplayCurrency> = combine(
        explicitDisplayCurrencyCode,
        accounts.observeAll(),
    ) { explicit, list ->
        DisplayCurrencyResolver.resolve(
            explicitCode = explicit,
            accounts = list.map { AccountCurrencyInfo(it.id, it.currencyCode, it.status) },
            localeCurrencyCode = localeCurrencyCode(),
        )
    }.distinctUntilChanged()

    suspend fun setThemeMode(mode: ThemeMode) {
        put(KEY_THEME_MODE, mode.storageValue)
    }

    suspend fun setDisplayCurrency(code: String) {
        CurrencyRegistry.get(code)
        put(KEY_DISPLAY_CURRENCY, code)
    }

    private suspend fun put(key: String, value: String) {
        settings.upsert(AppSettingEntity(key, value, System.currentTimeMillis()))
    }

    private fun localeCurrencyCode(): String? = try {
        java.util.Currency.getInstance(Locale.getDefault())?.currencyCode
    } catch (e: IllegalArgumentException) {
        null
    }

    companion object {
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_DISPLAY_CURRENCY = "display_currency"
    }
}
