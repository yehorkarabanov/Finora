package com.yehor.finora.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * Money colors that follow the light/dark theme. Dynamic color does not cover income/expense
 * semantics, and the light-theme greens and 600-shade palette colors are too dark on black.
 */
@Immutable
data class FinanceColors(
    val income: Color,
    val incomeContainer: Color,
    val expense: Color,
    val expenseContainer: Color,
    val isDark: Boolean,
) {
    /**
     * A stored category/account color as it should be drawn. In dark theme it is lifted toward
     * white so saturated or dark palette entries stay visible. The stored value is never changed.
     */
    fun accent(argb: Long): Color {
        val color = Color(argb)
        return if (isDark) lerp(color, Color.White, 0.25f) else color
    }
}

val LightFinanceColors = FinanceColors(
    income = Color(0xFF2E7D32),
    incomeContainer = Color(0xFFDCEFDD),
    expense = Color(0xFFC62828),
    expenseContainer = Color(0xFFFBE0E0),
    isDark = false,
)

val DarkFinanceColors = FinanceColors(
    income = Color(0xFF5CDDAB),
    incomeContainer = Color(0xFF123B2D),
    expense = Color(0xFFFFB4AB),
    expenseContainer = Color(0xFF4A1E1C),
    isDark = true,
)

val LocalFinanceColors = staticCompositionLocalOf { LightFinanceColors }

/** `MaterialTheme.finance.income` etc. */
@Suppress("UnusedReceiverParameter")
val MaterialTheme.finance: FinanceColors
    @Composable
    @ReadOnlyComposable
    get() = LocalFinanceColors.current
