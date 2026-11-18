package com.yehor.finora.domain.money

import java.math.BigDecimal

/** An amount in the minor units of [currency]. No floating point is used anywhere. */
data class Money(val amountMinor: Long, val currency: Currency) {

    operator fun plus(other: Money): Money {
        requireSameCurrency(other)
        return Money(exact { Math.addExact(amountMinor, other.amountMinor) }, currency)
    }

    operator fun minus(other: Money): Money {
        requireSameCurrency(other)
        return Money(exact { Math.subtractExact(amountMinor, other.amountMinor) }, currency)
    }

    operator fun unaryMinus(): Money = Money(exact { Math.negateExact(amountMinor) }, currency)

    operator fun times(factor: Long): Money =
        Money(exact { Math.multiplyExact(amountMinor, factor) }, currency)

    /** Plain decimal text without symbol, for example `-12.50`, `1000` (scale 0) or `0.005`. */
    fun toPlainString(): String = BigDecimal.valueOf(amountMinor, currency.scale).toPlainString()

    /** Decimal text followed by the currency symbol, for example `12.50 $`. */
    fun format(): String = "${toPlainString()} ${currency.symbol}"

    private fun requireSameCurrency(other: Money) {
        if (currency != other.currency) throw CurrencyMismatchException(currency, other.currency)
    }

    private inline fun exact(block: () -> Long): Long = try {
        block()
    } catch (e: ArithmeticException) {
        throw MoneyOverflowException("Money arithmetic overflow", e)
    }

    companion object {
        fun zero(currency: Currency): Money = Money(0L, currency)

        /**
         * Parses decimal [text] such as `12`, `-12.5` or `+0.005`.
         * Input with more fractional digits than the currency scale is rejected, never rounded.
         */
        fun parse(text: String, currency: Currency): Money {
            val decimal = try {
                BigDecimal(text.trim())
            } catch (e: NumberFormatException) {
                throw InvalidMoneyException("Not a valid amount: '$text'")
            }
            if (decimal.stripTrailingZeros().scale() > currency.scale) {
                throw InvalidMoneyException(
                    "'$text' has more than ${currency.scale} fractional digits for ${currency.code}",
                )
            }
            val minor = try {
                decimal.setScale(currency.scale).unscaledValue().longValueExact()
            } catch (e: ArithmeticException) {
                throw MoneyOverflowException("Amount out of range: '$text'", e)
            }
            return Money(minor, currency)
        }
    }
}
