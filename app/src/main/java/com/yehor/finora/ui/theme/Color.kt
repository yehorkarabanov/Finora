package com.yehor.finora.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/*
 * Fixed Finora brand scheme. Seed: indigo/violet #6B5CE7, M3 tonal logic.
 * Dark follows the Stitch palette (docs/stitch_finora_personal_finance_app/finora/DESIGN.md).
 * Every on* role keeps >= 4.5:1 against its pair, and onSurface/onSurfaceVariant against every surface step.
 */

// ---- Light ----
private val LightPrimary = Color(0xFF5847D0)
private val LightOnPrimary = Color(0xFFFFFFFF)
private val LightPrimaryContainer = Color(0xFFE3DEFF)
private val LightOnPrimaryContainer = Color(0xFF190064)
private val LightInversePrimary = Color(0xFFC8BFFF)
private val LightSecondary = Color(0xFF5D5791)
private val LightOnSecondary = Color(0xFFFFFFFF)
private val LightSecondaryContainer = Color(0xFFE6E0F7)
private val LightOnSecondaryContainer = Color(0xFF1A1640)
private val LightTertiary = Color(0xFF006C4F)
private val LightOnTertiary = Color(0xFFFFFFFF)
private val LightTertiaryContainer = Color(0xFF7BF9C6)
private val LightOnTertiaryContainer = Color(0xFF002115)
private val LightError = Color(0xFFBA1A1A)
private val LightOnError = Color(0xFFFFFFFF)
private val LightErrorContainer = Color(0xFFFFDAD6)
private val LightOnErrorContainer = Color(0xFF410002)
private val LightBackground = Color(0xFFFCF8FF)
private val LightOnBackground = Color(0xFF1B1B21)
private val LightSurface = Color(0xFFFCF8FF)
private val LightOnSurface = Color(0xFF1B1B21)
private val LightSurfaceVariant = Color(0xFFE4E1EC)
private val LightOnSurfaceVariant = Color(0xFF47454F)
private val LightOutline = Color(0xFF787580)
private val LightOutlineVariant = Color(0xFFC9C4D5)
private val LightInverseSurface = Color(0xFF303036)
private val LightInverseOnSurface = Color(0xFFF3EFF7)
private val LightSurfaceBright = Color(0xFFFCF8FF)
private val LightSurfaceDim = Color(0xFFDCD8E0)
private val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
private val LightSurfaceContainerLow = Color(0xFFF6F2FD)
private val LightSurfaceContainer = Color(0xFFF0ECF7)
private val LightSurfaceContainerHigh = Color(0xFFEAE6F1)
private val LightSurfaceContainerHighest = Color(0xFFE4E0EB)

// ---- Dark ----
private val DarkPrimary = Color(0xFFC8BFFF)
private val DarkOnPrimary = Color(0xFF2E138E)
private val DarkPrimaryContainer = Color(0xFF7565D7)
private val DarkOnPrimaryContainer = Color(0xFFFFFEFF)
private val DarkInversePrimary = Color(0xFF5D4CBD)
private val DarkSecondary = Color(0xFFC8BFFF)
private val DarkOnSecondary = Color(0xFF2F1D7D)
private val DarkSecondaryContainer = Color(0xFF463795)
private val DarkOnSecondaryContainer = Color(0xFFE5DEFF)
private val DarkTertiary = Color(0xFF5CDDAB)
private val DarkOnTertiary = Color(0xFF003826)
private val DarkTertiaryContainer = Color(0xFF008661)
private val DarkOnTertiaryContainer = Color(0xFFFCFFFA)
private val DarkError = Color(0xFFFFB4AB)
private val DarkOnError = Color(0xFF690005)
private val DarkErrorContainer = Color(0xFF93000A)
private val DarkOnErrorContainer = Color(0xFFFFDAD6)
private val DarkBackground = Color(0xFF0F1412)
private val DarkOnBackground = Color(0xFFDFE4DE)
private val DarkSurface = Color(0xFF0F1412)
private val DarkOnSurface = Color(0xFFDFE4DE)
private val DarkSurfaceVariant = Color(0xFF313632)
private val DarkOnSurfaceVariant = Color(0xFFC9C4D5)
private val DarkOutline = Color(0xFF928F9E)
private val DarkOutlineVariant = Color(0xFF484553)
private val DarkInverseSurface = Color(0xFFDFE4DE)
private val DarkInverseOnSurface = Color(0xFF2C322E)
private val DarkSurfaceBright = Color(0xFF353A37)
private val DarkSurfaceDim = Color(0xFF0F1412)
private val DarkSurfaceContainerLowest = Color(0xFF0A0F0C)
private val DarkSurfaceContainerLow = Color(0xFF181D1A)
private val DarkSurfaceContainer = Color(0xFF1C211E)
private val DarkSurfaceContainerHigh = Color(0xFF262B28)
private val DarkSurfaceContainerHighest = Color(0xFF313632)

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
