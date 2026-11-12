package com.yehor.finora.ui.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

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
