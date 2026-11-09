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
    /** Null on a new account until the user picks one. */
    val currency: Currency? = null,
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

/** Why the form's primary button is disabled. */
enum class AccountFormBlocker {
    LOADING, SAVING, NAME_EMPTY, NAME_TOO_LONG, CURRENCY_MISSING, AMOUNT_INVALID, DESCRIPTION_TOO_LONG, DATE_TOO_LATE,
}

object AccountFormLogic {

    /** Scale used to parse the opening balance text while no currency is chosen yet. */
    private val PARSE_FALLBACK = CurrencyRegistry.get("USD")

    private fun parseCurrency(s: AccountFormState): Currency = s.currency ?: PARSE_FALLBACK

    /** Archived accounts can change or remove an existing opening balance, but not add one (design 4.7). */
    fun openingSectionEnabled(s: AccountFormState): Boolean =
        !(s.isEdit && s.archived && s.originalOpeningMinor == null)

    /** Signed minor units entered in the form, null when the amount is empty or zero or unparsable. */
    fun openingMinor(s: AccountFormState): Long? {
        val parsed = AccountDraftValidator.parseAmount(s.amountText, parseCurrency(s)) as? AmountParse.Value ?: return null
        return parsed.minor.takeIf { it != 0L }
    }

    fun errors(s: AccountFormState): Set<AccountDraftError> =
        AccountDraftValidator.validate(
            name = s.name,
            description = s.description,
            amountText = if (openingSectionEnabled(s)) s.amountText else "",
            currency = parseCurrency(s),
        )

    /** True when the chosen date is later than [OpeningDatePolicy.latestAllowed]. Only matters with an amount. */
    fun dateTooLate(s: AccountFormState, policy: OpeningDatePolicy, now: Long): Boolean =
        openingMinor(s) != null && s.dateMillis > policy.latestAllowed(now, s.earliestOtherEntryAt)

    private val AMOUNT_ERRORS: Set<AccountDraftError> = setOf(
        AccountDraftError.AMOUNT_INVALID,
        AccountDraftError.AMOUNT_TOO_PRECISE,
        AccountDraftError.AMOUNT_OUT_OF_RANGE,
    )

    /** The first reason the form cannot be saved yet, null when it can. */
    fun blocker(s: AccountFormState, policy: OpeningDatePolicy, now: Long): AccountFormBlocker? {
        if (!s.loaded) return AccountFormBlocker.LOADING
        if (s.saving) return AccountFormBlocker.SAVING
        val errors = errors(s)
        return when {
            AccountDraftError.NAME_EMPTY in errors -> AccountFormBlocker.NAME_EMPTY
            AccountDraftError.NAME_TOO_LONG in errors -> AccountFormBlocker.NAME_TOO_LONG
            s.currency == null -> AccountFormBlocker.CURRENCY_MISSING
            errors.any { it in AMOUNT_ERRORS } -> AccountFormBlocker.AMOUNT_INVALID
            AccountDraftError.DESCRIPTION_TOO_LONG in errors -> AccountFormBlocker.DESCRIPTION_TOO_LONG
            dateTooLate(s, policy, now) -> AccountFormBlocker.DATE_TOO_LATE
            else -> null
        }
    }

    fun canSave(s: AccountFormState, policy: OpeningDatePolicy, now: Long): Boolean =
        blocker(s, policy, now) == null

    /** Flips the sign of the amount text: "" -> "-", "12" <-> "-12". Surrounding whitespace is dropped. */
    fun toggleSign(text: String): String {
        val t = text.trim()
        return if (t.startsWith("-")) t.removePrefix("-").trimStart() else "-$t"
    }

    /**
     * Non-blocking warning on edit: the amount of change to the current balance. Null when the account has
     * no other entries (then the opening balance IS the balance and the change is obvious), or nothing changed.
     */
    fun balanceDelta(s: AccountFormState): Money? {
        val currency = s.currency ?: return null
        if (!s.isEdit || s.earliestOtherEntryAt == null || !openingSectionEnabled(s)) return null
        if (AccountDraftValidator.parseAmount(s.amountText, currency) is AmountParse.Invalid) return null
        val delta = (openingMinor(s) ?: 0L) - (s.originalOpeningMinor ?: 0L)
        return if (delta == 0L) null else Money(delta, currency)
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
