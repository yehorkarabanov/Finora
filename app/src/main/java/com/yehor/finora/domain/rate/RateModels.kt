package com.yehor.finora.domain.rate

import com.yehor.finora.domain.money.Currency
import java.math.BigDecimal
import java.time.LocalDate

/** A directed currency pair. [base] and [quote] are always different. */
data class CurrencyPair(val base: Currency, val quote: Currency) {
    init {
        require(base != quote) { "A pair needs two different currencies" }
    }
}

data class RateQuote(
    /** 1 unit of base = [rate] units of quote, always > 0. */
    val rate: BigDecimal,
    /** As published; informational. */
    val quoteDate: LocalDate,
    /** Epoch ms, when this device got the rate. */
    val fetchedAt: Long,
)

data class RateTable(val quotes: Map<CurrencyPair, RateQuote>) {
    companion object {
        val EMPTY = RateTable(emptyMap())
    }
}

object RateFreshness {
    const val REFRESH_AFTER_MS = 24L * 60 * 60 * 1000
    const val OUTDATED_AFTER_MS = 72L * 60 * 60 * 1000

    /** True for a missing quote, or one that is 24 hours old or more. */
    fun isDue(quote: RateQuote?, now: Long): Boolean =
        quote == null || now - quote.fetchedAt >= REFRESH_AFTER_MS

    /** True when the quote is more than 72 hours old. */
    fun isOutdated(quote: RateQuote, now: Long): Boolean = now - quote.fetchedAt > OUTDATED_AFTER_MS
}
