package com.yehor.finora.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.yehor.finora.R
import com.yehor.finora.designsystem.component.list.GroupedCard
import com.yehor.finora.designsystem.component.list.ListRow
import com.yehor.finora.designsystem.component.list.RoundIcon
import com.yehor.finora.designsystem.component.list.SectionHeader
import com.yehor.finora.designsystem.component.sheet.FinoraSheet
import com.yehor.finora.designsystem.icon.AppIcons
import com.yehor.finora.designsystem.theme.PreviewTheme

private data class MoreEntry(val icon: ImageVector, val title: Int, val subtitle: Int, val route: String)

private val OrganizeEntries = listOf(
    MoreEntry(AppIcons.Category, R.string.more_categories, R.string.more_categories_subtitle, Routes.CATEGORIES),
    MoreEntry(AppIcons.Tag, R.string.more_tags, R.string.more_tags_subtitle, Routes.TAGS),
    MoreEntry(AppIcons.Recurring, R.string.more_recurring, R.string.more_recurring_subtitle, Routes.RECURRING),
)

private val AppEntries = listOf(
    MoreEntry(AppIcons.Data, R.string.more_data, R.string.more_data_subtitle, Routes.DATA),
    MoreEntry(AppIcons.Settings, R.string.more_settings, R.string.more_settings_subtitle, Routes.SETTINGS),
)

/** Slide-up "More" panel: ORGANIZE tiles and APP rows. [onNavigate] receives the chosen route. */
@Composable
fun MoreSheet(onDismiss: () -> Unit, onNavigate: (String) -> Unit) {
    FinoraSheet(onDismiss = onDismiss) { MoreSheetContent(onNavigate) }
}

@Composable
private fun MoreSheetContent(onNavigate: (String) -> Unit) {
    SectionHeader(stringResource(R.string.more_section_organize))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OrganizeEntries.forEach { entry ->
            OrganizeTile(entry, Modifier.weight(1f)) { onNavigate(entry.route) }
        }
    }
    Spacer(Modifier.height(10.dp))
    SectionHeader(stringResource(R.string.more_section_app))
    GroupedCard(Modifier.fillMaxWidth()) {
        AppEntries.forEach { entry ->
            ListRow(
                icon = { RoundIcon(entry.icon, MaterialTheme.colorScheme.primary) },
                title = stringResource(entry.title),
                subtitle = stringResource(entry.subtitle),
                trailing = {
                    Icon(AppIcons.Next, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                },
                onClick = { onNavigate(entry.route) },
            )
        }
    }
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun OrganizeTile(entry: MoreEntry, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RoundIcon(entry.icon, MaterialTheme.colorScheme.primary)
        Text(
            stringResource(entry.title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MoreSheetLightPreview() {
    PreviewTheme(dark = false) { Column(Modifier.padding(20.dp)) { MoreSheetContent {} } }
}

@Preview(showBackground = true)
@Composable
private fun MoreSheetDarkPreview() {
    PreviewTheme(dark = true) { Column(Modifier.padding(20.dp)) { MoreSheetContent {} } }
}
