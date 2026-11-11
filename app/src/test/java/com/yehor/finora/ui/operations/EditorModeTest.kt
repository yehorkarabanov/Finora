package com.yehor.finora.ui.operations

import androidx.compose.ui.unit.dp
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

    @Test
    fun tallScreenGetsTheFullKeypad() {
        val layout = editorLayoutFor(700.dp)
        assertEquals(58.dp, layout.keyHeight)
        assertEquals(true, layout.showQuickAmounts)
    }

    @Test
    fun shortScreenShrinksKeysToTheTouchTargetAndDropsQuickAmounts() {
        // 360×640 with a 24dp status bar and 48dp 3-button navigation leaves 568dp.
        val layout = editorLayoutFor(568.dp)
        assertEquals(48.dp, layout.keyHeight)
        assertEquals(false, layout.showQuickAmounts)
    }
}
