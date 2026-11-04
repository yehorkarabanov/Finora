package com.yehor.finora.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.yehor.finora.R

/** A round colored badge with an icon, used for accounts and categories. */
@Composable
fun IconBadge(icon: String?, color: Long?, fallbackColor: Long, modifier: Modifier = Modifier, size: Int = 40) {
    val tint = Color(ColorPalette.resolve(color, fallbackColor))
    Box(
        modifier.size(size.dp).clip(CircleShape).background(tint.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(IconCatalog.get(icon), contentDescription = null, tint = tint, modifier = Modifier.size((size * 0.55f).dp))
    }
}

/** Grid of icon keys. [selected] may be null or unknown; nothing is highlighted then. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun IconPicker(
    keys: List<String>,
    selected: String?,
    color: Long?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = Color(color ?: ColorPalette.ACCOUNT_DEFAULT)
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        keys.forEach { key ->
            val isSelected = key == selected
            val description = stringResource(R.string.picker_icon_item, key)
            Surface(
                shape = CircleShape,
                color = if (isSelected) tint.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                border = if (isSelected) BorderStroke(2.dp, tint) else null,
                modifier = Modifier
                    .size(48.dp)
                    .semantics { contentDescription = description; this.selected = isSelected }
                    .clickable(role = Role.RadioButton) { onSelect(key) },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(IconCatalog.get(key), contentDescription = null, tint = if (isSelected) tint else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Grid of the 16 palette colors. A [selected] color outside the palette selects none. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ColorPicker(
    selected: Long?,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ColorPalette.colors.forEachIndexed { index, argb ->
            val isSelected = argb == selected
            val description = stringResource(R.string.picker_color_item, index + 1)
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(argb))
                    .semantics { contentDescription = description; this.selected = isSelected }
                    .clickable(role = Role.RadioButton) { onSelect(argb) },
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.padding(4.dp))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PickersPreview() {
    MaterialTheme {
        androidx.compose.foundation.layout.Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            IconBadge("food", 0xFFFB8C00L, ColorPalette.ACCOUNT_DEFAULT)
            IconPicker(IconKeys.category, "food", 0xFFFB8C00L, {})
            ColorPicker(0xFFFB8C00L, {})
        }
    }
}
