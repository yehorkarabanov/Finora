package com.yehor.finora.ui.operations

import com.yehor.finora.domain.account.Account
import com.yehor.finora.domain.account.AccountType
import com.yehor.finora.domain.model.AccountStatus
import com.yehor.finora.domain.money.CurrencyRegistry
import org.junit.Assert.assertEquals
import org.junit.Test

class PresetAccountTest {
    private fun acc(id: Long, status: AccountStatus) =
        Account(id, "A$id", CurrencyRegistry.get("USD"), AccountType.BANK, null, null, null, status, 0)

    private val accounts = listOf(acc(1, AccountStatus.ACTIVE), acc(2, AccountStatus.ARCHIVED))

    @Test fun activePresetIsUsed() = assertEquals(1L, presetAccountId(1, accounts, 9L))
    @Test fun archivedPresetFallsBack() = assertEquals(9L, presetAccountId(2, accounts, 9L))
    @Test fun unknownPresetFallsBack() = assertEquals(9L, presetAccountId(5, accounts, 9L))
    @Test fun absentArgFallsBack() = assertEquals(9L, presetAccountId(-1, accounts, 9L))
    @Test fun noDefaultIsNull() = assertEquals(null, presetAccountId(-1, accounts, null))
}
