package com.yehor.finora.ui.operations

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.yehor.finora.R
import com.yehor.finora.designsystem.icon.AppIcons
import com.yehor.finora.designsystem.motion.FinoraMotion
import com.yehor.finora.designsystem.motion.rememberReduceMotion
import com.yehor.finora.designsystem.theme.KeypadNumStyle
import java.math.BigDecimal

/**
 * Pure editing rules for the on-screen amount keypad. The amount is kept as plain text with
 * '.' as the decimal separator; [AmountParser][com.yehor.finora.domain.money.AmountParser]
 * stays the single source of validation.
 */
object AmountKeypad {
    const val BACKSPACE = "⌫"
    const val DECIMAL = "."

    /** Integer digits accepted from the keypad; larger amounts are out of range anyway. */
    const val MAX_INTEGER_DIGITS = 12

    /**
     * Applies one key press. Keys that would make the text invalid for a currency with [scale]
     * fraction digits are ignored instead of producing an error state.
     */
    fun applyKey(current: String, key: String, scale: Int): String {
        val dot = current.indexOf('.')
        return when (key) {
            BACKSPACE -> current.dropLast(1)
            DECIMAL -> when {
                scale <= 0 || dot >= 0 -> current
                current.isEmpty() -> "0."
                else -> "$current."
            }
            else -> {
                if (key.length != 1 || !key[0].isDigit()) return current
                when {
                    dot >= 0 && current.length - dot - 1 >= scale -> current
                    current == "0" -> key
                    dot < 0 && current.length >= MAX_INTEGER_DIGITS -> current
                    else -> current + key
                }
            }
        }
    }

    /** Adds a whole [increment] to the current amount, keeping whatever fraction was typed. */
    fun add(current: String, increment: Int): String {
        val base = current.trimEnd('.').toBigDecimalOrNull() ?: BigDecimal.ZERO
        val sum = base + increment.toBigDecimal()
        return if (sum.signum() == 0) "0" else sum.stripTrailingZeros().toPlainString()
    }

    /**
     * Formats typed text for display with locale grouping, keeping a trailing separator and
     * trailing fraction zeros exactly as typed (so "12." and "12.50" render as typed).
     */
    fun format(text: String, groupingSeparator: Char, decimalSeparator: Char): String {
        val source = text.ifEmpty { "0" }
        val dot = source.indexOf('.')
        val integer = if (dot >= 0) source.substring(0, dot) else source
        val fraction = if (dot >= 0) source.substring(dot + 1) else null
        val grouped = integer.ifEmpty { "0" }
            .reversed()
            .chunked(3)
            .joinToString(groupingSeparator.toString())
            .reversed()
        return if (fraction == null) grouped else "$grouped$decimalSeparator$fraction"
    }
}

private val KeySize = 58.dp

/**
 * The borderless 3×4 amount keypad: digits, the decimal key bottom-left and backspace bottom-right.
 * A press shows a circular wash and a slight springy shrink; long-pressing backspace clears the amount.
 */
@Composable
internal fun NumberPad(
    decimalLabel: String,
    decimalEnabled: Boolean,
    onKey: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", AmountKeypad.DECIMAL, "0", AmountKeypad.BACKSPACE)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        keys.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { key ->
                    KeypadKey(
                        key = key,
                        decimalLabel = decimalLabel,
                        enabled = key != AmountKeypad.DECIMAL || decimalEnabled,
                        onKey = onKey,
                        onClear = onClear,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun KeypadKey(
    key: String,
    decimalLabel: String,
    enabled: Boolean,
    onKey: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val backspaceLabel = stringResource(R.string.transaction_form_backspace)
    val isBackspace = key == AmountKeypad.BACKSPACE
    val isDecimal = key == AmountKeypad.DECIMAL
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val reduceMotion = rememberReduceMotion()
    // The wash appears at once on press and fades out after release, so even a quick tap shows it.
    val wash by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = if (pressed || reduceMotion) snap() else tween(220),
        label = "keyWash",
    )
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduceMotion) 0.96f else 1f,
        animationSpec = if (reduceMotion) snap() else FinoraMotion.press,
        label = "keyScale",
    )
    val washColor = MaterialTheme.colorScheme.surfaceContainerHigh
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(KeySize)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClickLabel = if (isBackspace) backspaceLabel else null,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.KeyboardTap)
                    onKey(key)
                },
                onLongClick = if (isBackspace) {
                    {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onClear()
                    }
                } else {
                    null
                },
            )
            .semantics { if (isBackspace) contentDescription = backspaceLabel },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(KeySize)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .drawBehind { if (wash > 0f) drawCircle(washColor.copy(alpha = washColor.alpha * wash)) },
        ) {
            when {
                isBackspace -> Icon(
                    AppIcons.Backspace,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp),
                )
                isDecimal && !enabled -> Unit
                else -> Text(
                    if (isDecimal) decimalLabel else key,
                    style = KeypadNumStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
