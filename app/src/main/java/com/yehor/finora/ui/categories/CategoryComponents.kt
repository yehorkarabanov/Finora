package com.yehor.finora.ui.categories

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.yehor.finora.ui.common.IconSize

// Rows of one group are connected: only the outer corners are fully rounded (design system 5.3).
private val GroupRadius = 20.dp
private val GroupInnerRadius = 4.dp
internal val CategoryGroupTopShape = RoundedCornerShape(
    topStart = GroupRadius, topEnd = GroupRadius,
    bottomStart = GroupInnerRadius, bottomEnd = GroupInnerRadius,
)
internal val CategoryGroupMiddleShape = RoundedCornerShape(GroupInnerRadius)
internal val CategoryGroupBottomShape = RoundedCornerShape(
    topStart = GroupInnerRadius, topEnd = GroupInnerRadius,
    bottomStart = GroupRadius, bottomEnd = GroupRadius,
)
internal val CategoryGroupSingleShape = RoundedCornerShape(GroupRadius)

/** Shape of item [index] in a connected group of [size] items. */
internal fun categoryGroupShape(index: Int, size: Int): Shape = when {
    size <= 1 -> CategoryGroupSingleShape
    index == 0 -> CategoryGroupTopShape
    index == size - 1 -> CategoryGroupBottomShape
    else -> CategoryGroupMiddleShape
}

@Composable
internal fun CategorySectionCard(
    shape: Shape,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    isError: Boolean = false,
    content: @Composable () -> Unit,
) {
    val clickModifier = if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = if (isError) BorderStroke(1.dp, MaterialTheme.colorScheme.error) else null,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(clickModifier.padding(14.dp)) { content() }
    }
}

@Composable
internal fun CategoryLeadingIcon(icon: ImageVector) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(IconSize.Default))
    }
}

/** Read-only badge: `labelSmall` text on `surfaceContainerHighest`, tinted by [color]. */
@Composable
internal fun CategoryPill(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
