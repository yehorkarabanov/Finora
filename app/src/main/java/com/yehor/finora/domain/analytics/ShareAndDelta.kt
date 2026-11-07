package com.yehor.finora.domain.analytics

import com.yehor.finora.domain.money.Estimate
import java.math.BigDecimal
import java.math.RoundingMode

/** A part of a total in permille (0..1000). */
data class Share(val permille: Int)

/** Null when [total] is zero or the currencies differ. One HALF_UP rounding, from BigDecimal. */
fun share(part: Estimate, total: Estimate): Share? {
    if (part.currency != total.currency || total.amountMinor == 0L) return null
    val permille = BigDecimal(part.amountMinor)
        .multiply(BigDecimal(1000))
        .divide(BigDecimal(total.amountMinor), 0, RoundingMode.HALF_UP)
        .toInt()
    return Share(permille.coerceIn(0, 1000))
}

/** [percentTimesTen] is null when the previous value is zero ("new"). */
data class Delta(val amount: Estimate, val percentTimesTen: Int?)

/** Null when the currencies differ. A missing side counts as zero. */
fun delta(current: Estimate?, previous: Estimate?): Delta? {
    val currency = (current ?: previous)?.currency ?: return null
    if (current != null && previous != null && current.currency != previous.currency) return null
    val cur = current ?: Estimate.zero(currency)
    val prev = previous ?: Estimate.zero(currency)
    val difference = try {
        Math.subtractExact(cur.amountMinor, prev.amountMinor)
    } catch (e: ArithmeticException) {
        return null
    }
    val basis = cur.basis + prev.basis
    val percent = if (prev.amountMinor == 0L) {
        null
    } else {
        BigDecimal(difference)
            .multiply(BigDecimal(1000))
            .divide(BigDecimal(prev.amountMinor).abs(), 0, RoundingMode.HALF_UP)
            .toInt()
    }
    return Delta(Estimate(difference, currency, basis), percent)
}
