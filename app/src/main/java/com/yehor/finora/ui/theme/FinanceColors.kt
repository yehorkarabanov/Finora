package com.yehor.finora.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * Money colors that follow the light/dark theme. Income shares the emerald primary (the design uses
 * mint for positive movement); expense is the red/coral family. Each text color keeps >= 4.5:1 on the
 * surface steps and on its own container; the 600-shade palette colors are lifted by [accent] on dark.
 */
@Immutable
data class FinanceColors(
    val income: Color,
    val incomeContainer: Color,
    val expense: Color,
    val expenseContainer: Color,
    val negative: Color,
    val accentGlow: Color,
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
    income = Color(0xFF006C46),
    incomeContainer = Color(0xFFC6F7DD),
    expense = Color(0xFFBA1A1A),
    expenseContainer = Color(0xFFFFDAD6),
    // Contrast: 5.13:1 on surface (#F8F8F9), 4.87:1 on surfaceContainerLow (#F2F2F3).
    negative = Color(0xFFC2362B),
    accentGlow = Color(0xFF00A36C).copy(alpha = 0.25f),
    isDark = false,
)

val DarkFinanceColors = FinanceColors(
    income = Color(0xFF00E297),
    incomeContainer = Color(0xFF0B3324),
    expense = Color(0xFFFFB4AB),
    expenseContainer = Color(0xFF3F1D1B),
    // Contrast: 7.31:1 on surface (#121314), 6.70:1 on surfaceContainerLow (#1B1C1D).
    negative = Color(0xFFFF7A6B),
    accentGlow = Color(0xFF00E599).copy(alpha = 0.35f),
    isDark = true,
)

val LocalFinanceColors = staticCompositionLocalOf { LightFinanceColors }

/** `MaterialTheme.finance.income` etc. */
@Suppress("UnusedReceiverParameter")
val MaterialTheme.finance: FinanceColors
    @Composable
    @ReadOnlyComposable
    get() = LocalFinanceColors.current
