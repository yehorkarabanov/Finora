package com.yehor.finora.domain.money

import java.math.BigDecimal

/** Field-wise OR when two estimates are added. */
data class EstimateBasis(val usesConversion: Boolean, val hasOutdatedRate: Boolean) {
    operator fun plus(other: EstimateBasis): EstimateBasis = EstimateBasis(
        usesConversion = usesConversion || other.usesConversion,
        hasOutdatedRate = hasOutdatedRate || other.hasOutdatedRate,
    )

    companion object {
        val NATIVE = EstimateBasis(usesConversion = false, hasOutdatedRate = false)
    }
}

/**
 * A converted value. It is deliberately not a [Money]: it cannot be passed where Money is
 * required, and there is no way back to Money.
 */
data class Estimate(
    val amountMinor: Long,
    val currency: Currency,
    val basis: EstimateBasis,
) {
    operator fun plus(other: Estimate): Estimate {
        if (currency != other.currency) throw CurrencyMismatchException(currency, other.currency)
        val sum = try {
            Math.addExact(amountMinor, other.amountMinor)
        } catch (e: ArithmeticException) {
            throw MoneyOverflowException("Estimate arithmetic overflow", e)
        }
        return Estimate(sum, currency, basis + other.basis)
    }

    fun toPlainString(): String = BigDecimal.valueOf(amountMinor, currency.scale).toPlainString()

    /** "3912.60 zł": the plain amount with its symbol. */
    fun format(): String = "${toPlainString()} ${currency.symbol}"

    companion object {
        fun zero(currency: Currency): Estimate = Estimate(0L, currency, EstimateBasis.NATIVE)
    }
}
