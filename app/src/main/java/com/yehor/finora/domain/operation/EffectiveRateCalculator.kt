package com.yehor.finora.domain.operation

import com.yehor.finora.domain.money.Money
import java.math.BigDecimal
import java.math.RoundingMode

/** Both directions of the rate implied by a cross-currency transfer, for example `1 EUR = 1.0850 USD`. */
data class EffectiveRate(val forward: String, val inverse: String)

/** Informational only. The rate is never stored and never replaces either entered amount. */
object EffectiveRateCalculator {
    private const val RATE_SCALE = 4

    /** Null for a same-currency transfer or when either amount is zero. */
    fun calculate(sent: Money, received: Money): EffectiveRate? {
        if (sent.currency.code == received.currency.code) return null
        if (sent.amountMinor == 0L || received.amountMinor == 0L) return null
        val sentMajor = BigDecimal.valueOf(sent.amountMinor, sent.currency.scale)
        val receivedMajor = BigDecimal.valueOf(received.amountMinor, received.currency.scale)
        val forward = receivedMajor.divide(sentMajor, RATE_SCALE, RoundingMode.HALF_UP)
        val inverse = sentMajor.divide(receivedMajor, RATE_SCALE, RoundingMode.HALF_UP)
        return EffectiveRate(
            forward = "1 ${sent.currency.code} = ${forward.toPlainString()} ${received.currency.code}",
            inverse = "1 ${received.currency.code} = ${inverse.toPlainString()} ${sent.currency.code}",
        )
    }
}
