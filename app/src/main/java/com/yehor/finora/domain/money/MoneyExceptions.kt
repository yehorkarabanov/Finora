package com.yehor.finora.domain.money

/** Thrown when a money operation would exceed the range of [Long]. Values never wrap. */
class MoneyOverflowException(message: String, cause: Throwable? = null) :
    ArithmeticException(message) {
    init {
        if (cause != null) initCause(cause)
    }
}

/** Thrown when text cannot be parsed as an amount of a given currency. */
class InvalidMoneyException(message: String) : IllegalArgumentException(message)

/** Thrown when two different currencies are combined without an explicit conversion. */
class CurrencyMismatchException(left: Currency, right: Currency) :
    IllegalArgumentException("Currency mismatch: ${left.code} vs ${right.code}")

/** Thrown when a code is not present in [CurrencyRegistry]. */
class UnknownCurrencyException(code: String) :
    IllegalArgumentException("Unknown currency code: $code")
