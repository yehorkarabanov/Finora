package com.yehor.finora.ui.operations

import org.junit.Assert.assertEquals
import org.junit.Test

class EditorModeTest {
    @Test
    fun fromArgMapsKnownKinds() {
        assertEquals(EditorMode.INCOME, EditorMode.fromArg("INCOME"))
        assertEquals(EditorMode.TRANSFER, EditorMode.fromArg("TRANSFER"))
        assertEquals(EditorMode.EXPENSE, EditorMode.fromArg("EXPENSE"))
    }

    @Test
    fun fromArgDefaultsToExpense() {
        assertEquals(EditorMode.EXPENSE, EditorMode.fromArg(null))
        assertEquals(EditorMode.EXPENSE, EditorMode.fromArg(""))
        assertEquals(EditorMode.EXPENSE, EditorMode.fromArg("income"))
        assertEquals(EditorMode.EXPENSE, EditorMode.fromArg("garbage"))
    }

    @Test
    fun argRoundTrips() {
        EditorMode.entries.forEach { assertEquals(it, EditorMode.fromArg(it.arg)) }
    }

    @Test
    fun newOpensOnAmount() = assertEquals(EditorStage.AMOUNT, initialStage(isEdit = false))

    @Test
    fun editOpensWithShelf() = assertEquals(EditorStage.DETAILS, initialStage(isEdit = true))
}
