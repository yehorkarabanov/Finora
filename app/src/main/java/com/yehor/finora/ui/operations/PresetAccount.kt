package com.yehor.finora.ui.operations

import com.yehor.finora.domain.account.Account
import com.yehor.finora.domain.model.AccountStatus

/** The account a new transaction starts on: the route's [arg] when it is a known active account, else [default]. */
fun presetAccountId(arg: Long, accounts: List<Account>, default: Long?): Long? =
    accounts.firstOrNull { it.id == arg && it.status == AccountStatus.ACTIVE }?.id ?: default
