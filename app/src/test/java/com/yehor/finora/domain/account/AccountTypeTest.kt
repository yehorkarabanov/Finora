package com.yehor.finora.domain.account

import org.junit.Assert.assertEquals
import org.junit.Test

class AccountTypeTest {

    @Test
    fun knownValuesMapToThemselves() {
        AccountType.entries.forEach { assertEquals(it, AccountType.fromStorage(it.name)) }
    }

    @Test
    fun nullMapsToOther() {
        assertEquals(AccountType.OTHER, AccountType.fromStorage(null))
    }

    @Test
    fun unknownMapsToOther() {
        assertEquals(AccountType.OTHER, AccountType.fromStorage("CRYPTO"))
        assertEquals(AccountType.OTHER, AccountType.fromStorage("bank"))
        assertEquals(AccountType.OTHER, AccountType.fromStorage(""))
    }
}
