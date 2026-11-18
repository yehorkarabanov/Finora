package com.yehor.finora.designsystem.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/*
 * Obsidian Wealth geometry: controls are full pills (M3 buttons/chips already default to
 * CircleShape-like full rounding); cards, inputs and sheets use the 24dp "md" radius.
 * Token scale: sm 8, DEFAULT 16, md 24, lg 32.
 */
val FinoraShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

/** Pill for buttons, chips and selectors. */
val PillShape = RoundedCornerShape(50)

/** Layer-1 container outline: 1px white at 8% (dark). Light theme uses a black hairline instead. */
fun hairline(isDark: Boolean) = BorderStroke(1.dp, if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f))
