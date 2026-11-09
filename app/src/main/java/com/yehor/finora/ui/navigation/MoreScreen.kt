package com.yehor.finora.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.yehor.finora.R
import com.yehor.finora.ui.common.AppIcons
import com.yehor.finora.ui.common.IconSize
import com.yehor.finora.ui.theme.PreviewTheme

private data class MoreEntry(
    val icon: ImageVector,
    val title: Int,
    val subtitle: Int,
    val route: String,
)

private val OrganizeEntries = listOf(
    MoreEntry(AppIcons.Category, R.string.more_categories, R.string.more_categories_subtitle, Routes.CATEGORIES),
    MoreEntry(AppIcons.Tag, R.string.more_tags, R.string.more_tags_subtitle, Routes.TAGS),
    MoreEntry(AppIcons.Recurring, R.string.more_recurring, R.string.more_recurring_subtitle, Routes.RECURRING),
)

private val AppEntries = listOf(
    MoreEntry(AppIcons.Settings, R.string.more_settings, R.string.more_settings_subtitle, Routes.SETTINGS),
    MoreEntry(AppIcons.Data, R.string.more_data, R.string.more_data_subtitle, Routes.DATA),
)

private val GroupOuterRadius = 20.dp
private val GroupInnerRadius = 4.dp
private val GroupTopShape = RoundedCornerShape(
    topStart = GroupOuterRadius, topEnd = GroupOuterRadius,
    bottomStart = GroupInnerRadius, bottomEnd = GroupInnerRadius,
)
private val GroupMiddleShape = RoundedCornerShape(GroupInnerRadius)
private val GroupBottomShape = RoundedCornerShape(
    topStart = GroupInnerRadius, topEnd = GroupInnerRadius,
    bottomStart = GroupOuterRadius, bottomEnd = GroupOuterRadius,
)
private val GroupSingleShape = RoundedCornerShape(GroupOuterRadius)

private fun groupShape(index: Int, count: Int): Shape = when {
    count == 1 -> GroupSingleShape
    index == 0 -> GroupTopShape
    index == count - 1 -> GroupBottomShape
    else -> GroupMiddleShape
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(onNavigate: (String) -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_more)) }) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            MoreSection(R.string.more_section_organize, OrganizeEntries, onNavigate)
            MoreSection(R.string.more_section_app, AppEntries, onNavigate)
        }
    }
}

@Composable
private fun MoreSection(title: Int, entries: List<MoreEntry>, onNavigate: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 4.dp).semantics { heading() },
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            entries.forEachIndexed { index, entry ->
                MoreRow(entry, groupShape(index, entries.size)) { onNavigate(entry.route) }
            }
        }
    }
}

@Composable
private fun MoreRow(entry: MoreEntry, shape: Shape, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.heightIn(min = 64.dp).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    entry.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(IconSize.Default),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(entry.title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(entry.subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                AppIcons.Next,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(IconSize.Default),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MoreScreenLightPreview() {
    PreviewTheme(dark = false) { MoreScreen(onNavigate = {}) }
}

@Preview(showBackground = true)
@Composable
private fun MoreScreenDarkPreview() {
    PreviewTheme(dark = true) { MoreScreen(onNavigate = {}) }
}
