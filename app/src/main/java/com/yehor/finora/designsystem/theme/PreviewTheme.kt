package com.yehor.finora.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/** Light/dark theme for @Preview: exactly the app's brand schemes ([FinoraLightColorScheme] / [FinoraDarkColorScheme]) plus the finance colors. */
@Composable
fun PreviewTheme(dark: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalFinanceColors provides if (dark) DarkFinanceColors else LightFinanceColors) {
        MaterialTheme(
            colorScheme = if (dark) FinoraDarkColorScheme else FinoraLightColorScheme,
            typography = FinoraTypography,
            shapes = FinoraShapes,
            content = content,
        )
    }
}
