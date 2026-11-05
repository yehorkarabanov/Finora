package com.yehor.finora.ui.operations

import com.yehor.finora.domain.money.Currency
import com.yehor.finora.domain.money.Money
import com.yehor.finora.domain.operation.EffectiveRate
import com.yehor.finora.domain.operation.EffectiveRateCalculator
import com.yehor.finora.domain.operation.TransferAmountParser

/** The part of the transfer form that the swap action rewrites. */
data class TransferSides(
    val sourceAccountId: Long?,
    val destAccountId: Long?,
    val sentText: String,
    val receivedText: String,
)

object TransferFormLogic {

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
