package com.yehor.finora.designsystem.component.navigation

import com.yehor.finora.designsystem.component.navigation.FabMode.COLLAPSED
import com.yehor.finora.designsystem.component.navigation.FabMode.EXTENDED
import com.yehor.finora.designsystem.component.navigation.FabMode.HIDDEN
import org.junit.Assert.assertEquals
import org.junit.Test

class FabScrollTest {
    @Test fun topIsExtended() = assertEquals(EXTENDED, FabScroll.next(COLLAPSED, true, 0, -2f))
    @Test fun flingDownHides() = assertEquals(HIDDEN, FabScroll.next(COLLAPSED, false, 400, 30f))
    @Test fun scrollUpShowsCollapsed() = assertEquals(COLLAPSED, FabScroll.next(HIDDEN, false, 400, -10f))
    @Test fun slowScrollAwayFromTopCollapses() = assertEquals(COLLAPSED, FabScroll.next(EXTENDED, false, 200, 3f))
    @Test fun hiddenStaysHiddenOnSlowScroll() = assertEquals(HIDDEN, FabScroll.next(HIDDEN, false, 400, 3f))
}
