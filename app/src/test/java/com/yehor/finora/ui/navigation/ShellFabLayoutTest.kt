package com.yehor.finora.ui.navigation

import androidx.compose.ui.unit.dp
import com.yehor.finora.designsystem.component.navigation.AddFabBottomOffsetDp
import com.yehor.finora.designsystem.component.navigation.AddFabHeightDp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellFabLayoutTest {
    private val fabTop = (AddFabBottomOffsetDp + AddFabHeightDp).dp

    @Test
    fun lastListRowClearsTheFabWithAGap() {
        val contentEnd = ShellContentBottomPaddingDp.dp + ShellFabClearance
        assertTrue("content must end above the FAB top", contentEnd >= fabTop + 16.dp)
        assertEquals(72.dp, ShellFabClearance)
    }

    @Test
    fun shellSnackbarSitsAboveTheFabOnlyWhenOneIsBound() {
        assertTrue(shellSnackbarBottom(fabBound = true) >= fabTop + 8.dp)
        assertEquals(76.dp, shellSnackbarBottom(fabBound = false))
    }
}
