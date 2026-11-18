package com.yehor.finora.domain.rate

import com.yehor.finora.domain.money.Currency
import com.yehor.finora.domain.money.Estimate
import com.yehor.finora.domain.money.EstimateBasis
import com.yehor.finora.domain.money.Money
import com.yehor.finora.domain.money.MoneyOverflowException
import java.math.BigDecimal
import java.math.RoundingMode

sealed interface ConversionResult {
    data class Converted(val estimate: Estimate) : ConversionResult
    data class Unavailable(val from: Currency, val to: Currency) : ConversionResult
}

data class Valuation(
    /** Null when nothing converted, or on overflow. */
    val total: Estimate?,
    /** Source currencies with no direct rate. */
    val excluded: Set<Currency>,
    val overflowed: Boolean,
    /** Oldest `fetchedAt` over the rates actually used. */
    val oldestFetchedAt: Long?,
)

/**
 * The only way to turn [Money] into another currency. Uses a direct cached pair only: no inverse
 * rate, no pivot currency, no routing.
 */
class RateConverter(private val table: RateTable, private val now: Long) {

    /** Throws [MoneyOverflowException] when the converted amount does not fit in a Long. */
    fun convert(money: Money, to: Currency): ConversionResult = convertWithQuote(money, to).first

    private fun convertWithQuote(money: Money, to: Currency): Pair<ConversionResult, RateQuote?> {
        if (money.currency == to) {
            return ConversionResult.Converted(Estimate(money.amountMinor, to, EstimateBasis.NATIVE)) to null
        }
        val quote = table.quotes[CurrencyPair(money.currency, to)]
            ?: return ConversionResult.Unavailable(money.currency, to) to null
        val minor = try {
            BigDecimal(money.amountMinor).multiply(quote.rate)
                .movePointRight(to.scale - money.currency.scale)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact()
        } catch (e: ArithmeticException) {
            throw MoneyOverflowException("Converted amount out of range", e)
        }
        val basis = EstimateBasis(
            usesConversion = true,
            hasOutdatedRate = RateFreshness.isOutdated(quote, now),
        )
        return ConversionResult.Converted(Estimate(minor, to, basis)) to quote
    }

    fun value(amounts: List<Money>, to: Currency): Valuation {
        var total = Estimate.zero(to)
        val excluded = linkedSetOf<Currency>()
        var oldest: Long? = null
        try {
            for (money in amounts) {
                val (result, quote) = convertWithQuote(money, to)
                when (result) {
                    is ConversionResult.Converted -> {
                        total += result.estimate
                        if (quote != null) {
                            oldest = minOf(oldest ?: quote.fetchedAt, quote.fetchedAt)
                        }
                    }
                    is ConversionResult.Unavailable -> excluded += result.from
                }
            }
        } catch (e: MoneyOverflowException) {
            return Valuation(total = null, excluded = excluded, overflowed = true, oldestFetchedAt = oldest)
        }
        val anyConverted = amounts.any { it.currency !in excluded }
        return Valuation(
            total = if (anyConverted || amounts.isEmpty()) total else null,
            excluded = excluded,
            overflowed = false,
            oldestFetchedAt = oldest,
        )
    }
}
