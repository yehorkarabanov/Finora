package com.yehor.finora.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import com.yehor.finora.domain.model.ThemeMode

/**
 * App theme. Uses the fixed brand schemes from `Color.kt`; [dynamicColor] opts into wallpaper
 * (Material You) colors instead. Finance colors are always the static ones.
 */
@Composable
fun FinoraTheme(themeMode: ThemeMode, dynamicColor: Boolean = false, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = when {
        // minSdk is 33, so dynamic color is always available.
        dynamicColor -> {
            val context = LocalContext.current
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        dark -> FinoraDarkColorScheme
        else -> FinoraLightColorScheme
    }
    CompositionLocalProvider(LocalFinanceColors provides if (dark) DarkFinanceColors else LightFinanceColors) {
        MaterialTheme(colorScheme = colors, typography = FinoraTypography, shapes = FinoraShapes, content = content)
    }
}
