package com.yehor.finora.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/*
 * Finora "Obsidian Wealth" scheme. Dark is the design-token set verbatim: obsidian surfaces,
 * electric-mint primary (#6DFFBA / container #00E599), neutral graphite secondary, cool-grey tertiary.
 * Light is derived from the same tokens (inverse-primary #006C46 as primary, fixed tones as containers).
 */

// ---- Light ----
private val LightPrimary = Color(0xFF006C46)
private val LightOnPrimary = Color(0xFFFFFFFF)
private val LightPrimaryContainer = Color(0xFF4DFFB2)
private val LightOnPrimaryContainer = Color(0xFF002112)
private val LightInversePrimary = Color(0xFF00E297)
private val LightSecondary = Color(0xFF5D5E63)
private val LightOnSecondary = Color(0xFFFFFFFF)
private val LightSecondaryContainer = Color(0xFFE2E2E7)
private val LightOnSecondaryContainer = Color(0xFF1A1C20)
private val LightTertiary = Color(0xFF585E6B)
private val LightOnTertiary = Color(0xFFFFFFFF)
private val LightTertiaryContainer = Color(0xFFDDE2F1)
private val LightOnTertiaryContainer = Color(0xFF161C26)
private val LightError = Color(0xFFBA1A1A)
private val LightOnError = Color(0xFFFFFFFF)
private val LightErrorContainer = Color(0xFFFFDAD6)
private val LightOnErrorContainer = Color(0xFF410002)
private val LightBackground = Color(0xFFF8F8F9)
private val LightOnBackground = Color(0xFF1B1C1D)
private val LightSurface = Color(0xFFF8F8F9)
private val LightOnSurface = Color(0xFF1B1C1D)
private val LightSurfaceVariant = Color(0xFFDCE5DD)
private val LightOnSurfaceVariant = Color(0xFF3B4A41)
private val LightOutline = Color(0xFF6B7B70)
private val LightOutlineVariant = Color(0xFFBACBBE)
private val LightInverseSurface = Color(0xFF303031)
private val LightInverseOnSurface = Color(0xFFF1F0F1)
private val LightSurfaceBright = Color(0xFFF8F8F9)
private val LightSurfaceDim = Color(0xFFD9D9DA)
private val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
private val LightSurfaceContainerLow = Color(0xFFF2F2F3)
private val LightSurfaceContainer = Color(0xFFECECED)
private val LightSurfaceContainerHigh = Color(0xFFE6E6E7)
private val LightSurfaceContainerHighest = Color(0xFFE0E0E1)

// ---- Dark ----
private val DarkPrimary = Color(0xFF6DFFBA)
private val DarkOnPrimary = Color(0xFF003822)
private val DarkPrimaryContainer = Color(0xFF00E599)
private val DarkOnPrimaryContainer = Color(0xFF00613E)
private val DarkInversePrimary = Color(0xFF006C46)
private val DarkSecondary = Color(0xFFC6C6CB)
private val DarkOnSecondary = Color(0xFF2E3035)
private val DarkSecondaryContainer = Color(0xFF47494D)
private val DarkOnSecondaryContainer = Color(0xFFB7B8BD)
private val DarkTertiary = Color(0xFFDFE5F4)
private val DarkOnTertiary = Color(0xFF2B313C)
private val DarkTertiaryContainer = Color(0xFFC3C9D8)
private val DarkOnTertiaryContainer = Color(0xFF4E5461)
private val DarkError = Color(0xFFFFB4AB)
private val DarkOnError = Color(0xFF690005)
private val DarkErrorContainer = Color(0xFF93000A)
private val DarkOnErrorContainer = Color(0xFFFFDAD6)
private val DarkBackground = Color(0xFF121314)
private val DarkOnBackground = Color(0xFFE3E2E3)
private val DarkSurface = Color(0xFF121314)
private val DarkOnSurface = Color(0xFFE3E2E3)
private val DarkSurfaceVariant = Color(0xFF343536)
private val DarkOnSurfaceVariant = Color(0xFFBACBBE)
private val DarkOutline = Color(0xFF849589)
private val DarkOutlineVariant = Color(0xFF3B4A41)
private val DarkInverseSurface = Color(0xFFE3E2E3)
private val DarkInverseOnSurface = Color(0xFF303031)
private val DarkSurfaceBright = Color(0xFF39393A)
private val DarkSurfaceDim = Color(0xFF121314)
private val DarkSurfaceContainerLowest = Color(0xFF0D0E0F)
private val DarkSurfaceContainerLow = Color(0xFF1B1C1D)
private val DarkSurfaceContainer = Color(0xFF1F2021)
private val DarkSurfaceContainerHigh = Color(0xFF292A2B)
private val DarkSurfaceContainerHighest = Color(0xFF343536)

val FinoraLightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    inversePrimary = LightInversePrimary,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightTertiary,
    onTertiary = LightOnTertiary,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = LightOnTertiaryContainer,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceTint = LightPrimary,
    inverseSurface = LightInverseSurface,
    inverseOnSurface = LightInverseOnSurface,
    error = LightError,
    onError = LightOnError,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    scrim = Color(0xFF000000),
    surfaceBright = LightSurfaceBright,
    surfaceDim = LightSurfaceDim,
    surfaceContainerLowest = LightSurfaceContainerLowest,
    surfaceContainerLow = LightSurfaceContainerLow,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerHighest = LightSurfaceContainerHighest,
)

val FinoraDarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    inversePrimary = DarkInversePrimary,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceTint = DarkPrimary,
    inverseSurface = DarkInverseSurface,
    inverseOnSurface = DarkInverseOnSurface,
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    scrim = Color(0xFF000000),
    surfaceBright = DarkSurfaceBright,
    surfaceDim = DarkSurfaceDim,
    surfaceContainerLowest = DarkSurfaceContainerLowest,
    surfaceContainerLow = DarkSurfaceContainerLow,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = DarkSurfaceContainerHighest,
)
