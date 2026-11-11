package com.yehor.finora.designsystem.component.button

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.yehor.finora.designsystem.theme.finance
import com.yehor.finora.designsystem.theme.hairline

/** 40dp circular container-colored icon button (top bars, filters). */
@Composable
fun RoundIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 48dp hit area around the 40dp visual circle.
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(MaterialTheme.colorScheme.surfaceContainer, CircleShape)
                .border(hairline(MaterialTheme.finance.isDark), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
        }
    }
}
