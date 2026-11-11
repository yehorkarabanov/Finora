package com.yehor.finora.designsystem.component.display

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import com.yehor.finora.designsystem.motion.rememberReduceMotion

/** Formatted amount with the decimals in the amount colour at 70% alpha and 60% size; a changed value slides in vertically. */
@Composable
fun AmountText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
) {
    // Text resolves an unspecified colour from the style, then LocalContentColor; the fraction tint needs the real one.
    val tint = color.takeOrElse { style.color.takeOrElse { LocalContentColor.current } }
    val reduce = rememberReduceMotion()
    if (!animate || reduce) {
        Text(styledAmount(text, style, tint), style = style, color = color, maxLines = 1, modifier = modifier)
        return
    }
    AnimatedContent(
        targetState = text,
        modifier = modifier,
        transitionSpec = {
            // A bigger value enters from below, a smaller one from above.
            val dir = if ((targetState.numericValue() ?: 0.0) >= (initialState.numericValue() ?: 0.0)) 1 else -1
            (slideInVertically(tween(250)) { it / 2 * dir } + fadeIn(tween(250))) togetherWith
                (slideOutVertically(tween(250)) { -it / 2 * dir } + fadeOut(tween(150))) using
                SizeTransform(clip = false)
        },
        label = "amount",
    ) { value ->
        Text(styledAmount(value, style, tint), style = style, color = color, maxLines = 1)
    }
}

private fun String.numericValue(): Double? {
    val cleaned = filter { it.isDigit() || it == '.' || it == ',' || it == '-' }
    val sep = cleaned.indexOfLast { it == '.' || it == ',' }
    val normalized = if (sep >= 0) {
        cleaned.substring(0, sep).filter { it.isDigit() || it == '-' } + "." + cleaned.substring(sep + 1)
    } else {
        cleaned
    }
    return normalized.toDoubleOrNull()
}

/** A formatted amount split for styling: [whole] and [suffix] at full size, [fraction] dimmed. */
internal data class AmountParts(val whole: String, val fraction: String, val suffix: String)

// Whole part, then a separator with one or two digits, then an optional non-digit currency suffix ("48.20 $").
private val AmountPattern = Regex("""^(.*?)([.,]\d{1,2})(\s*\D+)?$""")

/**
 * Splits [text] into whole part, fraction tail (separator + decimals) and an optional currency suffix. The
 * separator counts as decimal only when one or two digits follow it and nothing but a non-digit suffix comes
 * after, so grouped whole numbers such as "1,234" or "1.234.567" stay whole.
 */
internal fun splitAmount(text: String): AmountParts {
    val match = AmountPattern.matchEntire(text) ?: return AmountParts(text, "", "")
    val (whole, fraction, suffix) = match.destructured
    return AmountParts(whole, fraction, suffix)
}

/** The fraction tail (see [splitAmount]) keeps the amount colour at 70% alpha and 60% size; the suffix stays full. */
private fun styledAmount(text: String, style: TextStyle, color: Color): AnnotatedString = buildAnnotatedString {
    val (whole, fraction, suffix) = splitAmount(text)
    append(whole)
    if (fraction.isNotEmpty()) {
        val size = if (style.fontSize.isSpecified) style.fontSize * 0.6f else TextUnit.Unspecified
        withStyle(SpanStyle(color = color.copy(alpha = color.alpha * 0.7f), fontSize = size)) { append(fraction) }
    }
    append(suffix)
}
