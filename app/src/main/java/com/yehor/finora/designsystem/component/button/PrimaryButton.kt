package com.yehor.finora.designsystem.component.button

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.yehor.finora.designsystem.motion.pressScale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import com.yehor.finora.designsystem.theme.PillShape
import com.yehor.finora.designsystem.theme.finance

/**
 * Mint pill CTA with the accent glow shadow; full-width unless [fillWidth] is false (e.g. inside a dialog slot).
 * [destructive] switches to the error colors for irreversible actions.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
    fillWidth: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        shape = PillShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (destructive) scheme.error else scheme.primary,
            contentColor = if (destructive) scheme.onError else scheme.onPrimary,
        ),
        modifier = modifier
            .pressScale(interaction)
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
            .height(52.dp)
            .shadow(
                if (enabled) 8.dp else 0.dp,
                PillShape,
                spotColor = if (destructive) scheme.error else MaterialTheme.finance.accentGlow,
            ),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}
