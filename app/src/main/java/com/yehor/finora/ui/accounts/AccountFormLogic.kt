package com.yehor.finora.ui.accounts

import com.yehor.finora.domain.account.AccountDraftError
import com.yehor.finora.domain.account.AccountDraftValidator
import com.yehor.finora.domain.account.AccountType
import com.yehor.finora.domain.account.AmountParse
import com.yehor.finora.domain.account.Balance
import com.yehor.finora.domain.account.OpeningDatePolicy
import com.yehor.finora.domain.money.Currency
import com.yehor.finora.domain.money.CurrencyRegistry
import com.yehor.finora.domain.money.Money

/** Everything the account form shows and edits. Immutable; the ViewModel replaces it on every change. */
data class AccountFormState(
    val isEdit: Boolean = false,
    val loaded: Boolean = false,
    val name: String = "",
    val type: AccountType = AccountType.BANK,
    val currency: Currency = CurrencyRegistry.get("USD"),
    val icon: String? = null,
    val color: Long? = null,
    val description: String = "",
    /** Signed amount text; a leading `-` makes the opening balance negative. */
    val amountText: String = "",
    /** Start of the local day of the opening balance. */
    val dateMillis: Long = 0L,
    val archived: Boolean = false,
    /** Minor units of the opening balance at load time, null when the account has none. */
    val originalOpeningMinor: Long? = null,
    val earliestOtherEntryAt: Long? = null,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val dateOrderRejected: Boolean = false,
    val saveFailed: Boolean = false,
)

object AccountFormLogic {

    /** Archived accounts can change or remove an existing opening balance, but not add one (design 4.7). */
    fun openingSectionEnabled(s: AccountFormState): Boolean =
        !(s.isEdit && s.archived && s.originalOpeningMinor == null)

    /** Signed minor units entered in the form, null when the amount is empty or zero or unparsable. */
    fun openingMinor(s: AccountFormState): Long? {
        val parsed = AccountDraftValidator.parseAmount(s.amountText, s.currency) as? AmountParse.Value ?: return null
        return parsed.minor.takeIf { it != 0L }
    }

    fun errors(s: AccountFormState): Set<AccountDraftError> =
        AccountDraftValidator.validate(
            name = s.name,
            description = s.description,
            amountText = if (openingSectionEnabled(s)) s.amountText else "",
            currency = s.currency,
        )

    /** True when the chosen date is later than [OpeningDatePolicy.latestAllowed]. Only matters with an amount. */
    fun dateTooLate(s: AccountFormState, policy: OpeningDatePolicy, now: Long): Boolean =
        openingMinor(s) != null && s.dateMillis > policy.latestAllowed(now, s.earliestOtherEntryAt)

    fun canSave(s: AccountFormState, policy: OpeningDatePolicy, now: Long): Boolean =
        s.loaded && !s.saving && errors(s).isEmpty() && !dateTooLate(s, policy, now)

    /**
     * Non-blocking warning on edit: the amount of change to the current balance. Null when the account has
     * no other entries (then the opening balance IS the balance and the change is obvious), or nothing changed.
     */
    fun balanceDelta(s: AccountFormState): Money? {
        if (!s.isEdit || s.earliestOtherEntryAt == null || !openingSectionEnabled(s)) return null
        if (AccountDraftValidator.parseAmount(s.amountText, s.currency) is AmountParse.Invalid) return null
        val delta = (openingMinor(s) ?: 0L) - (s.originalOpeningMinor ?: 0L)
        return if (delta == 0L) null else Money(delta, s.currency)
    }

    /** Text to prefill the amount field from a signed opening balance. */
    fun amountTextOf(minor: Long?, currency: Currency): String =
        if (minor == null) "" else Money(minor, currency).toPlainString()
}

/** Whether archiving needs a confirmation: any balance other than a known zero. */
fun archiveNeedsConfirmation(balance: Balance): Boolean = when (balance) {
    is Balance.Value -> balance.money.amountMinor != 0L
    Balance.Overflow -> true
}
