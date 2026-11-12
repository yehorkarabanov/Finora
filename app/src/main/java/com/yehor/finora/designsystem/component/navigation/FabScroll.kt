package com.yehor.finora.designsystem.component.navigation

enum class FabMode { EXTENDED, COLLAPSED, HIDDEN }

/** Pure scroll-to-FAB-mode rules shared by every list screen. */
object FabScroll {
    const val FLING_PX = 12f
    private const val TOP_OFFSET_PX = 20
    private const val SCROLL_UP_PX = -4f

    fun next(mode: FabMode, firstItemVisible: Boolean, scrollOffsetPx: Int, deltaPx: Float): FabMode = when {
        firstItemVisible && scrollOffsetPx < TOP_OFFSET_PX -> FabMode.EXTENDED
        deltaPx > FLING_PX -> FabMode.HIDDEN
        deltaPx < SCROLL_UP_PX -> FabMode.COLLAPSED
        mode == FabMode.EXTENDED -> FabMode.COLLAPSED
        else -> mode
    }
}
