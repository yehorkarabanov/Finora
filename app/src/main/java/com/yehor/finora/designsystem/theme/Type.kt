package com.yehor.finora.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.yehor.finora.R

/*
 * Obsidian Wealth type system: Space Grotesk for numbers and headings, Manrope for everything else.
 * Both are bundled variable fonts (res/font); each weight is one instance of the wght axis.
 */

@OptIn(ExperimentalTextApi::class)
private fun variableFamily(resId: Int, vararg weights: Int) = FontFamily(
    weights.map { w ->
        Font(resId, weight = FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
    },
)

val SpaceGrotesk = variableFamily(R.font.space_grotesk, 300, 400, 500, 600, 700)
val Manrope = variableFamily(R.font.manrope, 300, 400, 500, 600, 700, 800)

/** Tabular numerals so balances and amounts do not jitter while they change. */
private const val TabularNums = "tnum"

private fun grotesk(size: Int, line: Int, weight: FontWeight, tracking: Float = 0f) = TextStyle(
    fontFamily = SpaceGrotesk,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.sp,
    fontFeatureSettings = TabularNums,
)

private fun manrope(size: Int, line: Int, weight: FontWeight, tracking: Float = 0f) = TextStyle(
    fontFamily = Manrope,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.sp,
)

/**
 * M3 roles mapped onto the design tokens:
 * display = display-hero (56 / 44 mobile), headline = headline-lg/md/sm, title = Space Grotesk
 * headline-sm for the large step and Manrope label weights below it, body/label = Manrope.
 */
val FinoraTypography = Typography(
    displayLarge = grotesk(56, 64, FontWeight.Bold, -0.5f),
    displayMedium = grotesk(44, 52, FontWeight.Bold, -0.5f),
    displaySmall = grotesk(36, 44, FontWeight.Bold, -0.25f),
    headlineLarge = grotesk(32, 40, FontWeight.SemiBold),
    headlineMedium = grotesk(24, 32, FontWeight.SemiBold),
    headlineSmall = grotesk(20, 28, FontWeight.SemiBold),
    titleLarge = grotesk(18, 26, FontWeight.SemiBold),
    titleMedium = manrope(16, 22, FontWeight.SemiBold),
    titleSmall = manrope(14, 18, FontWeight.SemiBold),
    bodyLarge = manrope(16, 24, FontWeight.Normal),
    bodyMedium = manrope(14, 20, FontWeight.Normal),
    bodySmall = manrope(12, 16, FontWeight.Normal),
    labelLarge = manrope(14, 18, FontWeight.SemiBold),
    labelMedium = manrope(12, 16, FontWeight.SemiBold),
    labelSmall = manrope(11, 14, FontWeight.SemiBold, 0.2f),
)

/** keypad-num: number keys on the amount keypad. */
val KeypadNumStyle = grotesk(26, 32, FontWeight.Medium)

/** Money in list rows and tiles: Space Grotesk with tabular figures, sized like titleMedium. */
val AmountStyle = grotesk(16, 22, FontWeight.SemiBold)
