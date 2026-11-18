package com.yehor.finora.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class RoutesTest {
    @Test
    fun patternsCarryKind() {
        assertEquals(
            "transactions/new?kind={kind}&duplicateOf={duplicateOf}&sourceAccountId={sourceAccountId}",
            Routes.TRANSACTION_NEW,
        )
        assertEquals("transactions/{operationId}?kind={kind}", Routes.TRANSACTION_EDIT)
    }

    @Test
    fun transactionNewDefaultsToExpense() {
        assertEquals("transactions/new?kind=EXPENSE", Routes.transactionNew())
        assertEquals("transactions/new?kind=INCOME", Routes.transactionNew(kind = "INCOME"))
        assertEquals("transactions/new?kind=EXPENSE&duplicateOf=7", Routes.transactionNew(duplicateOf = 7))
        assertEquals(
            "transactions/new?kind=TRANSFER&duplicateOf=7&sourceAccountId=3",
            Routes.transactionNew("TRANSFER", 7, 3),
        )
    }

    @Test
    fun transactionEditOnlyAddsKindWhenGiven() {
        assertEquals("transactions/12", Routes.transactionEdit(12))
        assertEquals("transactions/12?kind=TRANSFER", Routes.transactionEdit(12, kind = "TRANSFER"))
    }

    @Test
    fun transferHelpersReturnUnifiedRoutes() {
        assertEquals("transactions/new?kind=TRANSFER", Routes.transferNew())
        assertEquals("transactions/new?kind=TRANSFER&duplicateOf=5", Routes.transferNew(duplicateOf = 5))
        assertEquals("transactions/new?kind=TRANSFER&sourceAccountId=2", Routes.transferNew(sourceAccountId = 2))
        assertEquals("transactions/9?kind=TRANSFER", Routes.transferEdit(9))
    }
}
