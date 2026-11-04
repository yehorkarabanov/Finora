package com.yehor.finora.domain.account

import com.yehor.finora.domain.model.AccountStatus
import com.yehor.finora.domain.money.Currency
import com.yehor.finora.domain.money.Money

data class Account(
    val id: Long,
    val name: String,
    val currency: Currency,
    val type: AccountType,
    val icon: String?,
    val color: Long?,
    val description: String?,
    val status: AccountStatus,
    val createdAt: Long,
)

sealed interface Balance {
    data class Value(val money: Money) : Balance
    data object Overflow : Balance
}

data class AccountWithBalance(val account: Account, val balance: Balance)

data class OpeningBalance(val operationId: Long, val money: Money, val occurredAt: Long)

/** Account, balance and the data the form needs to bound the opening date. */
data class AccountDetail(
    val account: Account,
    val balance: Balance,
    val openingBalance: OpeningBalance?,
    val earliestOtherEntryAt: Long?,
)

/** An account offered by a picker. [isArchivedOriginal] is true only for the edited operation's own archived account. */
data class PickableAccount(val account: Account, val isArchivedOriginal: Boolean)
