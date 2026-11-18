package com.yehor.finora.domain.settings

import com.yehor.finora.domain.model.AccountStatus
import com.yehor.finora.domain.money.Currency
import com.yehor.finora.domain.money.CurrencyRegistry

/** The minimum an account must expose for Display Currency resolution. */
data class AccountCurrencyInfo(
    val id: Long,
    val currencyCode: String,
    val status: AccountStatus,
)

/** The effective Display Currency and whether it came from an explicit user choice. */
data class ResolvedDisplayCurrency(val currency: Currency, val isAutomatic: Boolean)

object DisplayCurrencyResolver {

    private const val FALLBACK_CODE = "USD"

    /**
     * Resolution order, first that applies wins:
     * 1. [explicitCode], the user's `display_currency` setting.
     * 2. Currency of the active account with the lowest id.
     * 3. [localeCurrencyCode], if it exists in the registry.
     * 4. USD.
     */
    fun resolve(
        explicitCode: String?,
        accounts: List<AccountCurrencyInfo>,
        localeCurrencyCode: String?,
    ): ResolvedDisplayCurrency {
        explicitCode?.let { CurrencyRegistry.findOrNull(it) }?.let {
            return ResolvedDisplayCurrency(it, isAutomatic = false)
        }
        accounts
            .filter { it.status == AccountStatus.ACTIVE }
            .minByOrNull { it.id }
            ?.let { CurrencyRegistry.findOrNull(it.currencyCode) }
            ?.let { return ResolvedDisplayCurrency(it, isAutomatic = true) }
        localeCurrencyCode?.let { CurrencyRegistry.findOrNull(it) }?.let {
            return ResolvedDisplayCurrency(it, isAutomatic = true)
        }
        return ResolvedDisplayCurrency(CurrencyRegistry.get(FALLBACK_CODE), isAutomatic = true)
    }
}
