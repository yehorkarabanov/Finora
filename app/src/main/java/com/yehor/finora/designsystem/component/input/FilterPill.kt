package com.yehor.finora.designsystem.component.input

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.yehor.finora.designsystem.icon.AppIcons
import com.yehor.finora.designsystem.theme.PillShape
import com.yehor.finora.designsystem.theme.finance
import com.yehor.finora.designsystem.theme.hairline

/** Filter chip; active shows primary tint and, when [onClear] is given, a clear glyph. */
@Composable
fun FilterPill(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onClear: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val content = if (active) scheme.primary else scheme.onSurfaceVariant
    val border = if (active) BorderStroke(1.dp, scheme.primary.copy(alpha = 0.4f)) else hairline(MaterialTheme.finance.isDark)
    Row(
        modifier = modifier
            .clip(PillShape)
            .background(if (active) scheme.primary.copy(alpha = 0.12f) else scheme.surfaceContainer, PillShape)
            .border(border, PillShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = content)
        if (active && onClear != null) {
            Icon(
                AppIcons.Close,
                contentDescription = null,
                tint = content,
                modifier = Modifier.padding(start = 6.dp).size(14.dp).clickable(onClick = onClear),
            )
        }
    }
}
