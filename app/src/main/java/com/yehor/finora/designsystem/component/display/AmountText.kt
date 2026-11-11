package com.yehor.finora.designsystem.component.display

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.yehor.finora.designsystem.motion.rememberReduceMotion

/** Formatted amount with the decimals dimmed and smaller; a changed value slides in vertically. */
@Composable
fun AmountText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
) {
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    val reduce = rememberReduceMotion()
    if (!animate || reduce) {
        Text(styledAmount(text, style, dim), style = style, color = color, maxLines = 1, modifier = modifier)
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
        Text(styledAmount(value, style, dim), style = style, color = color, maxLines = 1)
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

/**
 * Splits [text] into the whole part and the fraction tail (separator + decimals). The last '.' or ',' is a
 * decimal separator only when exactly one or two digits follow it at the end, so grouped whole numbers such as
 * "1,234" or "1.234.567" stay whole.
 */
internal fun splitAmount(text: String): Pair<String, String> {
    val split = text.indexOfLast { it == '.' || it == ',' }
    if (split < 0) return text to ""
    val tail = text.substring(split + 1)
    return if (tail.length in 1..2 && tail.all { it.isDigit() }) text.substring(0, split) to text.substring(split) else text to ""
}

/** The fraction tail (see [splitAmount]) is dimmed at 60% size. */
private fun styledAmount(text: String, style: TextStyle, dim: Color): AnnotatedString = buildAnnotatedString {
    val (whole, fraction) = splitAmount(text)
    append(whole)
    if (fraction.isNotEmpty()) {
        withStyle(SpanStyle(color = dim, fontSize = style.fontSize * 0.6f)) { append(fraction) }
    }
}
