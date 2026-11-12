package com.yehor.finora.ui.operations

import com.yehor.finora.domain.money.Currency
import com.yehor.finora.domain.money.Money
import com.yehor.finora.domain.operation.EffectiveRate
import com.yehor.finora.domain.operation.EffectiveRateCalculator
import com.yehor.finora.domain.operation.TransferAmountParser
import com.yehor.finora.domain.operation.TransferDraftError

/** The part of the transfer form that the swap action rewrites. */
data class TransferSides(
    val sourceAccountId: Long?,
    val destAccountId: Long?,
    val sentText: String,
    val receivedText: String,
)

/** What a disabled Transfer CTA tells the user is missing, in priority order. */
enum class TransferBlocker { SENT_EMPTY, SOURCE_MISSING, DEST_MISSING, SAME_ACCOUNTS, RECEIVED_EMPTY, CATEGORY_MISSING }

object TransferFormLogic {

    /** The first missing piece of the draft, or null when it is valid or only has errors that are shown inline. */
    fun blocker(errors: Set<TransferDraftError>): TransferBlocker? = when {
        errors.isEmpty() -> null
        TransferDraftError.SENT_AMOUNT_EMPTY in errors -> TransferBlocker.SENT_EMPTY
        TransferDraftError.SOURCE_ACCOUNT_MISSING in errors -> TransferBlocker.SOURCE_MISSING
        TransferDraftError.DEST_ACCOUNT_MISSING in errors -> TransferBlocker.DEST_MISSING
        TransferDraftError.SAME_ACCOUNTS in errors -> TransferBlocker.SAME_ACCOUNTS
        TransferDraftError.RECEIVED_AMOUNT_EMPTY in errors -> TransferBlocker.RECEIVED_EMPTY
        TransferDraftError.CATEGORY_MISSING in errors -> TransferBlocker.CATEGORY_MISSING
        else -> null
    }

    /**
     * Swaps source and destination. Amounts follow their account's currency, so the texts swap too,
     * except when both currencies are known and equal, where each field keeps what the user typed.
     */
    fun swap(sides: TransferSides, sourceCurrency: Currency?, destCurrency: Currency?): TransferSides {
        val sameCurrency = sourceCurrency != null && destCurrency != null && sourceCurrency.code == destCurrency.code
        return TransferSides(
            sourceAccountId = sides.destAccountId,
            destAccountId = sides.sourceAccountId,
            sentText = if (sameCurrency) sides.sentText else sides.receivedText,
            receivedText = if (sameCurrency) sides.receivedText else sides.sentText,
        )
    }

    /**
     * Swap for the unified editor. While the received amount is [linked] to the sent amount the sent text
     * stays where it is (the user's typed number does not jump to the other field) and the received text is re-synced.
     */
    fun swap(sides: TransferSides, sourceCurrency: Currency?, destCurrency: Currency?, linked: Boolean): TransferSides {
        if (!linked) return swap(sides, sourceCurrency, destCurrency)
        return TransferSides(
            sourceAccountId = sides.destAccountId,
            destAccountId = sides.sourceAccountId,
            sentText = sides.sentText,
            receivedText = syncReceived(sides.sentText, sides.receivedText, true, destCurrency, sourceCurrency),
        )
    }

    /**
     * The received text to use after the sent amount, the accounts or a swap changed.
     * Linked and same (or still unknown) currency: it mirrors the sent text. Linked across currencies: the user has
     * to enter it, so it is empty. Not linked: the user's own text is kept.
     */
    fun syncReceived(
        sentText: String,
        receivedText: String,
        linked: Boolean,
        sourceCurrency: Currency?,
        destCurrency: Currency?,
    ): String {
        if (!linked) return receivedText
        val sameOrUnknown = sourceCurrency == null || destCurrency == null || sourceCurrency.code == destCurrency.code
        return if (sameOrUnknown) sentText else ""
    }

    /**
     * Whether "They receive" is its own editable row: across currencies, or when a same-currency (or still unknown)
     * received amount was unlinked by hand.
     */
    fun showReceivedRow(sourceCurrency: Currency?, destCurrency: Currency?, linked: Boolean): Boolean {
        val knownEqual = sourceCurrency == null || destCurrency == null || sourceCurrency.code == destCurrency.code
        return !knownEqual || !linked
    }

    /**
     * Whether to offer "Different amount" (e.g. a fee): both currencies known and equal and the received amount
     * still mirrors the sent one. Choosing it lets the keypad edit the received amount, which unlinks it.
     */
    fun showDifferentAmountAction(sourceCurrency: Currency?, destCurrency: Currency?, linked: Boolean): Boolean =
        linked && sourceCurrency != null && destCurrency != null && sourceCurrency.code == destCurrency.code

    /** True when a stored transfer is a plain same-amount, same-currency one, so the received amount starts linked. */
    fun isLinked(sent: Money, received: Money): Boolean =
        sent.currency.code == received.currency.code && sent.amountMinor == received.amountMinor

    /** The effective rate for the typed amounts, or null when it cannot or should not be shown. */
    fun effectiveRate(
        sentText: String,
        receivedText: String,
        sourceCurrency: Currency?,
        destCurrency: Currency?,
    ): EffectiveRate? {
        if (sourceCurrency == null || destCurrency == null) return null
        val sent = TransferAmountParser.parse(sentText, sourceCurrency) as? TransferAmountParser.Result.Value ?: return null
        val received = TransferAmountParser.parse(receivedText, destCurrency) as? TransferAmountParser.Result.Value ?: return null
        return EffectiveRateCalculator.calculate(
            Money(sent.minor, sourceCurrency),
            Money(received.minor, destCurrency),
        )
    }
}
