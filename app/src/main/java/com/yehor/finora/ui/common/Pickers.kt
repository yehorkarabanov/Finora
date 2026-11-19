package com.yehor.finora.ui.common


import com.yehor.finora.designsystem.icon.AppIcons
import com.yehor.finora.designsystem.icon.IconCatalog
import com.yehor.finora.designsystem.icon.IconGroup
import com.yehor.finora.designsystem.icon.IconKeys
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.yehor.finora.R
import com.yehor.finora.ui.operations.FilterSearchField
import com.yehor.finora.designsystem.component.list.GroupedCard
import com.yehor.finora.designsystem.component.list.SectionHeader
import com.yehor.finora.designsystem.component.sheet.FinoraSheet
import com.yehor.finora.designsystem.theme.PreviewTheme
import com.yehor.finora.designsystem.theme.hairline
import com.yehor.finora.designsystem.theme.finance

/** A round colored badge with an icon, used for accounts and categories. */
@Composable
fun IconBadge(icon: String?, color: Long?, fallbackColor: Long, modifier: Modifier = Modifier, size: Int = 40) {
    val tint = MaterialTheme.finance.accent(ColorPalette.resolve(color, fallbackColor))
    Box(
        modifier.size(size.dp).clip(CircleShape).background(tint.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(IconCatalog.get(icon), contentDescription = null, tint = tint, modifier = Modifier.size((size * 0.55f).dp))
    }
}

@StringRes
private fun groupLabel(id: String): Int = when (id) {
    "food" -> R.string.icon_group_food
    "transport" -> R.string.icon_group_transport
    "home" -> R.string.icon_group_home
    "shopping" -> R.string.icon_group_shopping
    "health" -> R.string.icon_group_health
    "education" -> R.string.icon_group_education
    "leisure" -> R.string.icon_group_leisure
    "personal" -> R.string.icon_group_personal
    "finance" -> R.string.icon_group_finance
    "income" -> R.string.icon_group_income
    "everyday" -> R.string.icon_group_everyday
    "banks" -> R.string.icon_group_banks
    "investments" -> R.string.icon_group_investments
    else -> R.string.icon_group_other
}

/**
 * Icon field for the account and category forms: a compact row showing the current icon that opens
 * a searchable, grouped sheet. [selected] may be null or unknown; the row shows the default icon
 * and nothing is highlighted in the sheet. Place it inside a [GroupedCard].
 */
@Composable
fun IconPicker(
    groups: List<IconGroup>,
    selected: String?,
    color: Long?,
    fallbackColor: Long,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    IconPickerRow(selected, color, fallbackColor, onClick = { open = true }, modifier = modifier)
    if (open) {
        FinoraSheet(onDismiss = { open = false }) {
            IconPickerSheetContent(
                groups = groups,
                selected = selected,
                color = color,
                fallbackColor = fallbackColor,
                onSelect = { onSelect(it); open = false },
                modifier = Modifier.fillMaxHeight(0.9f),
            )
        }
    }
}

/** The form row: badge in the selected color, the "Icon" label with the icon's name, a chevron. */
@Composable
internal fun IconPickerRow(
    selected: String?,
    color: Long?,
    fallbackColor: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clickLabel = stringResource(R.string.picker_icon_choose)
    Row(
        modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = clickLabel, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconBadge(selected, color, fallbackColor, size = 40)
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.picker_icon),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(stringResource(IconCatalog.label(selected)), style = MaterialTheme.typography.bodyLarge)
        }
        Icon(AppIcons.Next, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Search field plus a grouped grid of 48dp circles. Selecting an icon calls [onSelect]. */
@Composable
internal fun IconPickerSheetContent(
    groups: List<IconGroup>,
    selected: String?,
    color: Long?,
    fallbackColor: Long,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = MaterialTheme.finance.accent(ColorPalette.resolve(color, fallbackColor))
    val resources = LocalResources.current
    val labels = remember(groups, resources) {
        groups.flatMap { it.keys }.associateWith { resources.getString(IconCatalog.label(it)) }
    }
    var query by rememberSaveable { mutableStateOf("") }
    val needle = query.trim()
    val visible = remember(groups, labels, needle) {
        if (needle.isEmpty()) {
            groups
        } else {
            groups.mapNotNull { group ->
                val keys = group.keys.filter { key ->
                    key.replace('_', ' ').contains(needle, ignoreCase = true) ||
                        labels.getValue(key).contains(needle, ignoreCase = true)
                }
                if (keys.isEmpty()) null else group.copy(keys = keys)
            }
        }
    }
    Column(modifier.fillMaxWidth()) {
        FilterSearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = stringResource(R.string.picker_icon_search),
            modifier = Modifier.padding(bottom = 8.dp),
        )
        if (visible.isEmpty()) {
            Text(
                stringResource(R.string.picker_no_results),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(56.dp),
            modifier = Modifier.weight(1f, fill = false),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            visible.forEach { group ->
                item(key = "header-${group.id}", span = { GridItemSpan(maxLineSpan) }) {
                    SectionHeader(
                        stringResource(groupLabel(group.id)),
                        modifier = Modifier.padding(top = 8.dp).semantics { heading() },
                    )
                }
                items(group.keys, key = { "${group.id}-$it" }) { key ->
                    IconOption(key, labels.getValue(key), key == selected, tint, onSelect)
                }
            }
        }
    }
}

@Composable
private fun IconOption(key: String, label: String, isSelected: Boolean, tint: Color, onSelect: (String) -> Unit) {
    val description = stringResource(R.string.picker_icon_item, label)
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Surface(
            shape = CircleShape,
            color = if (isSelected) tint.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceContainerHigh,
            border = if (isSelected) BorderStroke(2.dp, tint) else hairline(MaterialTheme.finance.isDark),
            modifier = Modifier
                .size(48.dp)
                .semantics { contentDescription = description; this.selected = isSelected }
                .clickable(role = Role.RadioButton) { onSelect(key) },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    IconCatalog.get(key),
                    contentDescription = null,
                    tint = if (isSelected) tint else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Grid of the palette colors. A [selected] color outside the palette selects none. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPicker(
    selected: Long?,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(0.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        ColorPalette.colors.forEachIndexed { index, argb ->
            val isSelected = argb == selected
            val description = stringResource(R.string.picker_color_item, index + 1)
            Box(
                // 48dp hit area around the 40dp swatch.
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .semantics { contentDescription = description; this.selected = isSelected }
                    .clickable(role = Role.RadioButton) { onSelect(argb) }
                    .padding(4.dp)
                    .clip(CircleShape)
                    .background(Color(argb))
                    .then(if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) Icon(AppIcons.Check, contentDescription = null, tint = Color.White, modifier = Modifier.padding(4.dp))
            }
        }
    }
}

@Preview(showBackground = true, name = "Icon row light")
@Composable
private fun IconRowPreviewLight() = IconRowPreview(dark = false)

@Preview(showBackground = true, backgroundColor = 0xFF0F1412, name = "Icon row dark")
@Composable
private fun IconRowPreviewDark() = IconRowPreview(dark = true)

@Composable
private fun IconRowPreview(dark: Boolean) {
    PreviewTheme(dark) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                GroupedCard {
                    IconPickerRow("restaurant", 0xFFEA6A0CL, ColorPalette.ACCOUNT_DEFAULT, {})
                    IconPickerRow("legacy-garbage", null, ColorPalette.ACCOUNT_DEFAULT, {})
                    ColorPicker(0xFFEA6A0CL, {}, Modifier.padding(14.dp))
                }
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 640, name = "Icon sheet light")
@Composable
private fun IconSheetPreviewLight() = IconSheetPreview(dark = false)

@Preview(showBackground = true, backgroundColor = 0xFF0F1412, heightDp = 640, name = "Icon sheet dark")
@Composable
private fun IconSheetPreviewDark() = IconSheetPreview(dark = true)

@Composable
private fun IconSheetPreview(dark: Boolean) {
    PreviewTheme(dark) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            IconPickerSheetContent(
                groups = IconKeys.categoryGroups,
                selected = "coffee",
                color = 0xFFEA6A0CL,
                fallbackColor = ColorPalette.ACCOUNT_DEFAULT,
                onSelect = {},
                modifier = Modifier.fillMaxHeight().padding(horizontal = 20.dp),
            )
        }
    }
}
