package com.yehor.finora.domain.money

/**
 * An ISO 4217 currency. Instances are created only by [CurrencyRegistry], which is the single
 * source of truth for code, minor-unit scale, symbol and name.
 */
@ConsistentCopyVisibility
data class Currency internal constructor(
    val code: String,
    val scale: Int,
    val symbol: String,
    val name: String,
) {
    override fun toString(): String = code
}
