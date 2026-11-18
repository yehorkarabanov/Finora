package com.yehor.finora.ui.settings

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yehor.finora.R
import com.yehor.finora.data.settings.LocaleResolution
import com.yehor.finora.designsystem.component.input.SegmentedPill
import com.yehor.finora.designsystem.component.list.GroupedCard
import com.yehor.finora.designsystem.component.list.ListRow
import com.yehor.finora.designsystem.component.list.RoundIcon
import com.yehor.finora.designsystem.component.list.SectionHeader
import com.yehor.finora.designsystem.component.sheet.FinoraSheet
import com.yehor.finora.designsystem.icon.AppIcons
import com.yehor.finora.designsystem.theme.PillShape
import com.yehor.finora.designsystem.theme.PreviewTheme
import com.yehor.finora.domain.model.ThemeMode
import com.yehor.finora.domain.money.Currency
import com.yehor.finora.domain.settings.AppLanguage
import com.yehor.finora.ui.common.OrganizeTopBar

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenData: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val version = remember(context) { settingsAppVersion(context) }
    SettingsContent(
        state = state,
        appVersion = version,
        onBack = onBack,
        onOpenData = onOpenData,
        onThemeSelected = viewModel::onThemeSelected,
        onCurrencySelected = viewModel::onDisplayCurrencySelected,
        onLanguageSelected = viewModel::onLanguageSelected,
    )
}

@Suppress("DEPRECATION")
private fun settingsAppVersion(context: Context): String =
    try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    } catch (_: Exception) {
        ""
    }

@Composable
internal fun SettingsContent(
    state: SettingsUiState,
    appVersion: String,
    onBack: () -> Unit,
    onOpenData: () -> Unit,
    onThemeSelected: (ThemeMode) -> Unit,
    onCurrencySelected: (Currency) -> Unit,
    onLanguageSelected: (AppLanguage) -> Unit,
) {
    var showLanguageSheet by rememberSaveable { mutableStateOf(false) }
    var showCurrencySheet by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { OrganizeTopBar(stringResource(R.string.settings_title), onBack) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SettingsSection(R.string.settings_section_appearance) {
                GroupedCard(Modifier.fillMaxWidth()) {
                    SettingsThemeBlock(state.themeMode, onThemeSelected)
                    SettingsDivider()
                    SettingsRow(
                        icon = AppIcons.Language,
                        title = stringResource(R.string.settings_language),
                        value = if (state.language == AppLanguage.SYSTEM) {
                            stringResource(R.string.settings_language_system) + " · " +
                                stringResource(remember { LocaleResolution.resolvedSystemLanguage() }.labelRes())
                        } else {
                            stringResource(state.language.labelRes())
                        },
                        onClick = { showLanguageSheet = true },
                    )
                }
            }

            SettingsSection(R.string.settings_section_currency) {
                GroupedCard(Modifier.fillMaxWidth()) {
                    val currency = state.displayCurrency
                    SettingsRow(
                        icon = AppIcons.Currency,
                        title = stringResource(R.string.settings_display_currency),
                        value = "${currency.code} · ${currency.name}",
                        badge = if (state.displayCurrencyIsAutomatic) stringResource(R.string.settings_automatic) else null,
                        onClick = { showCurrencySheet = true },
                    )
                }
                Text(
                    stringResource(R.string.settings_display_currency_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }

            SettingsSection(R.string.settings_section_data) {
                GroupedCard(Modifier.fillMaxWidth()) {
                    SettingsRow(
                        icon = AppIcons.Data,
                        title = stringResource(R.string.settings_data_title),
                        value = stringResource(R.string.settings_data_subtitle),
                        onClick = onOpenData,
                    )
                }
            }

            SettingsSection(R.string.settings_section_about) {
                GroupedCard(Modifier.fillMaxWidth()) {
                    SettingsRow(
                        icon = AppIcons.Info,
                        title = stringResource(R.string.settings_app_version),
                        value = appVersion.ifEmpty { null },
                    )
                }
            }
        }
    }

    if (showLanguageSheet) {
        SettingsLanguageSheet(
            selected = state.language,
            onSelect = {
                onLanguageSelected(it)
                showLanguageSheet = false
            },
            onDismiss = { showLanguageSheet = false },
        )
    }

    if (showCurrencySheet) {
        SettingsCurrencySheet(
            all = state.availableCurrencies,
            selected = state.displayCurrency,
            onSelect = {
                onCurrencySelected(it)
                showCurrencySheet = false
            },
            onDismiss = { showCurrencySheet = false },
        )
    }
}

@Composable
private fun SettingsSection(titleRes: Int, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionHeader(stringResource(titleRes), Modifier.semantics { heading() })
        content()
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

@Composable
private fun SettingsBadge(text: String, container: Color = MaterialTheme.colorScheme.surfaceContainerHighest) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(PillShape)
            .background(container)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    value: String? = null,
    badge: String? = null,
    onClick: (() -> Unit)? = null,
) {
    ListRow(
        icon = { RoundIcon(icon, MaterialTheme.colorScheme.primary) },
        title = title,
        subtitle = value,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (badge != null) SettingsBadge(badge)
                if (onClick != null) {
                    Icon(AppIcons.Next, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        modifier = Modifier.heightIn(min = 64.dp),
        onClick = onClick,
    )
}

@Composable
private fun SettingsThemeBlock(selected: ThemeMode, onSelected: (ThemeMode) -> Unit) {
    val modes = listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK)
    val labels = listOf(
        stringResource(R.string.settings_theme_system),
        stringResource(R.string.settings_theme_light),
        stringResource(R.string.settings_theme_dark),
    )
    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RoundIcon(AppIcons.Theme, MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.bodyLarge)
        }
        SegmentedPill(
            options = labels,
            selected = modes.indexOf(selected),
            onSelect = { onSelected(modes[it]) },
        )
    }
}

@Composable
private fun SettingsPickerRow(
    selected: Boolean,
    onClick: () -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .semantics { this.selected = selected }
            .heightIn(min = 56.dp)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        content(Modifier.weight(1f))
        if (selected) {
            Icon(AppIcons.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun SettingsCurrencySheet(
    all: List<Currency>,
    selected: Currency,
    onSelect: (Currency) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val matches = remember(query, all) { SettingsLogic.filterCurrencies(all, query) }
    FinoraSheet(onDismiss = onDismiss) {
        Text(
            stringResource(R.string.settings_display_currency_choose),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        TextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            placeholder = { Text(stringResource(R.string.account_form_currency_search)) },
            leadingIcon = { Icon(AppIcons.Search, contentDescription = null) },
            trailingIcon = if (query.isNotEmpty()) {
                {
                    IconButton(onClick = { query = "" }) {
                        Icon(AppIcons.Close, contentDescription = stringResource(R.string.settings_close))
                    }
                }
            } else {
                null
            },
            singleLine = true,
            shape = CircleShape,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
        )
        if (matches.isEmpty()) {
            Text(
                stringResource(R.string.settings_currency_no_results),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        } else {
            LazyColumn(
                Modifier.selectableGroup(),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                items(matches, key = { it.code }) { currency ->
                    SettingsPickerRow(
                        selected = currency.code == selected.code,
                        onClick = { onSelect(currency) },
                    ) { weight ->
                        Row(
                            weight,
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            SettingsBadge(currency.code, MaterialTheme.colorScheme.surfaceContainerHigh)
                            Text(currency.name, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }
}

private fun AppLanguage.labelRes(): Int = when (this) {
    AppLanguage.SYSTEM -> R.string.settings_language_system
    AppLanguage.ENGLISH -> R.string.settings_language_english
    AppLanguage.RUSSIAN -> R.string.settings_language_russian
}

@Composable
private fun SettingsLanguageSheet(
    selected: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
    onDismiss: () -> Unit,
) {
    val resolvedSystem = remember { LocaleResolution.resolvedSystemLanguage() }
    FinoraSheet(onDismiss = onDismiss) {
        Text(
            stringResource(R.string.settings_language_choose),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        Column(Modifier.padding(vertical = 12.dp).selectableGroup()) {
            AppLanguage.entries.forEach { lang ->
                SettingsPickerRow(selected = lang == selected, onClick = { onSelect(lang) }) { weight ->
                    Column(weight) {
                        Text(stringResource(lang.labelRes()), style = MaterialTheme.typography.bodyLarge)
                        if (lang == AppLanguage.SYSTEM) {
                            Text(
                                stringResource(resolvedSystem.labelRes()),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsPreviewBody(dark: Boolean, mode: ThemeMode) {
    PreviewTheme(dark = dark) {
        Surface(color = MaterialTheme.colorScheme.background) {
            val inspection = LocalInspectionMode.current
            SettingsContent(
                state = SettingsUiState(themeMode = mode, language = if (dark) AppLanguage.RUSSIAN else AppLanguage.SYSTEM),
                appVersion = if (inspection) "1.0" else "",
                onBack = {},
                onOpenData = {},
                onThemeSelected = {},
                onCurrencySelected = {},
                onLanguageSelected = {},
            )
        }
    }
}

@Preview(name = "Settings light", showBackground = true)
@Composable
private fun SettingsContentLightPreview() = SettingsPreviewBody(dark = false, mode = ThemeMode.SYSTEM)

@Preview(name = "Settings dark", showBackground = true, backgroundColor = 0xFF0F1412)
@Composable
private fun SettingsContentDarkPreview() = SettingsPreviewBody(dark = true, mode = ThemeMode.DARK)
