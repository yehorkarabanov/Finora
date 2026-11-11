package com.yehor.finora.designsystem.component.input

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.yehor.finora.R
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
    val showClear = active && onClear != null
    val content = if (active) scheme.primary else scheme.onSurfaceVariant
    val border = if (active) BorderStroke(1.dp, scheme.primary.copy(alpha = 0.4f)) else hairline(MaterialTheme.finance.isDark)
    // The whole 48dp-high outer box is the pill's hit area; the visual pill inside stays compact.
    // The clear glyph is a separate 48dp target layered on top.
    Box(
        modifier.heightIn(min = 48.dp).clip(PillShape).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier
                .clip(PillShape)
                .background(if (active) scheme.primary.copy(alpha = 0.12f) else scheme.surfaceContainer, PillShape)
                .border(border, PillShape)
                .padding(start = 12.dp, top = 7.dp, bottom = 7.dp, end = if (showClear) 0.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = content)
            if (showClear) Spacer(Modifier.width(48.dp))
        }
        if (onClear != null && showClear) {
            Box(
                Modifier.align(Alignment.CenterEnd).size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClear),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.Close, contentDescription = stringResource(R.string.filter_clear), tint = content, modifier = Modifier.size(14.dp))
            }
        }
    }
}
