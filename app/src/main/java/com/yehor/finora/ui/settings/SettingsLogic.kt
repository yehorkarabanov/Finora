package com.yehor.finora.ui.settings

import com.yehor.finora.domain.money.Currency

object SettingsLogic {
    /**
     * Currencies whose code or name contains [query] (trimmed, case-insensitive). A blank query keeps
     * everything. Exact code matches come first, then code prefix matches, then the rest in the
     * original order.
     */
    fun filterCurrencies(all: List<Currency>, query: String): List<Currency> {
        val q = query.trim()
        if (q.isEmpty()) return all
        val matches = all.filter { it.code.contains(q, ignoreCase = true) || it.name.contains(q, ignoreCase = true) }
        return matches.sortedBy {
            when {
                it.code.equals(q, ignoreCase = true) -> 0
                it.code.startsWith(q, ignoreCase = true) -> 1
                else -> 2
            }
        }
    }
}
