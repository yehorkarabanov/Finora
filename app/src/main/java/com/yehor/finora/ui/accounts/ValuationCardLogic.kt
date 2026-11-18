package com.yehor.finora.ui.accounts

import com.yehor.finora.domain.account.AccountWithBalance
import com.yehor.finora.domain.account.Balance
import com.yehor.finora.domain.money.Currency
import com.yehor.finora.domain.money.Estimate
import com.yehor.finora.domain.money.Money
import com.yehor.finora.domain.rate.RateConverter
import com.yehor.finora.domain.rate.RateTable

/** What the total valuation card on the Accounts tab shows. */
data class ValuationCardState(
    /** Null when nothing could be converted, or on overflow: the card says "Unavailable". */
    val total: Estimate?,
    /** Currencies left out of the total (no direct rate, or a balance out of range). */
    val excluded: List<Currency>,
    /** Oldest `fetched_at` among the rates used, shown when [outdated]. */
    val oldestFetchedAt: Long?,
) {
    val outdated: Boolean get() = total?.basis?.hasOutdatedRate == true
    val estimated: Boolean get() = total?.basis?.usesConversion == true
}

object ValuationCardLogic {

    /** Only active accounts count (PRD 11.4, 11.5). Null when there is no active account. */
    fun build(
        active: List<AccountWithBalance>,
        display: Currency,
        table: RateTable,
        now: Long,
    ): ValuationCardState? {
        if (active.isEmpty()) return null
        val amounts = mutableListOf<Money>()
        val overflowCurrencies = mutableSetOf<Currency>()
        active.forEach { item ->
            when (val balance = item.balance) {
                is Balance.Value -> amounts += balance.money
                Balance.Overflow -> overflowCurrencies += item.account.currency
            }
        }
        val valuation = RateConverter(table, now).value(amounts, display)
        return ValuationCardState(
            total = valuation.total,
            excluded = (valuation.excluded + overflowCurrencies).sortedBy { it.code },
            oldestFetchedAt = valuation.oldestFetchedAt,
        )
    }
}
