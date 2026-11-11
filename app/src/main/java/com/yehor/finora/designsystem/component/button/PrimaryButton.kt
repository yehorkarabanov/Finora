package com.yehor.finora.designsystem.component.button

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import com.yehor.finora.designsystem.theme.PillShape
import com.yehor.finora.designsystem.theme.finance

/** Full-width mint pill CTA with the accent glow shadow. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = PillShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .shadow(if (enabled) 8.dp else 0.dp, PillShape, spotColor = MaterialTheme.finance.accentGlow),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}
