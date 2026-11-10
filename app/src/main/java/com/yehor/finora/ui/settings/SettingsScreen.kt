package com.yehor.finora.ui.settings

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yehor.finora.R
import com.yehor.finora.domain.model.ThemeMode
import com.yehor.finora.domain.money.Currency
import com.yehor.finora.data.settings.LocaleResolution
import com.yehor.finora.domain.settings.AppLanguage
import com.yehor.finora.ui.common.AppIcons
import com.yehor.finora.ui.common.IconSize
import com.yehor.finora.ui.theme.PreviewTheme

private val SettingsTopShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
private val SettingsMiddleShape = RoundedCornerShape(4.dp)
private val SettingsBottomShape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
private val SettingsSingleShape = RoundedCornerShape(20.dp)

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

@OptIn(ExperimentalMaterial3Api::class)
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
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(AppIcons.Back, contentDescription = stringResource(R.string.navigate_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsSection(R.string.settings_section_appearance) {
                SettingsThemeCard(state.themeMode, onThemeSelected)
                SettingsRow(
                    icon = AppIcons.Language,
                    title = stringResource(R.string.settings_language),
                    value = if (state.language == AppLanguage.SYSTEM) {
                        stringResource(R.string.settings_language_system) + " · " +
                            stringResource(remember { LocaleResolution.resolvedSystemLanguage() }.labelRes())
                    } else {
                        stringResource(state.language.labelRes())
                    },
                    shape = SettingsSingleShape,
                    onClick = { showLanguageSheet = true },
                    trailing = { SettingsChevron() },
                )
            }

            SettingsSection(R.string.settings_section_currency) {
                val currency = state.displayCurrency
                SettingsRow(
                    icon = AppIcons.Currency,
                    title = stringResource(R.string.settings_display_currency),
                    value = "${currency.code} · ${currency.name}",
                    badge = if (state.displayCurrencyIsAutomatic) stringResource(R.string.settings_automatic) else null,
                    shape = SettingsSingleShape,
                    onClick = { showCurrencySheet = true },
                    trailing = { SettingsChevron() },
                )
                Text(
                    stringResource(R.string.settings_display_currency_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }

            SettingsSection(R.string.settings_section_data) {
                SettingsRow(
                    icon = AppIcons.Data,
                    title = stringResource(R.string.settings_data_title),
                    value = stringResource(R.string.settings_data_subtitle),
                    shape = SettingsSingleShape,
                    onClick = onOpenData,
                    trailing = { SettingsChevron() },
                )
            }

            SettingsSection(R.string.settings_section_about) {
                SettingsRow(
                    icon = AppIcons.Info,
                    title = stringResource(R.string.settings_app_version),
                    value = appVersion.ifEmpty { null },
                    shape = SettingsSingleShape,
                )
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingsSectionHeader(stringResource(titleRes))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) { content() }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .semantics { heading() },
    )
}

@Composable
private fun SettingsChevron() {
    Icon(
        AppIcons.Next,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SettingsLeadingIcon(icon: ImageVector) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(IconSize.Default))
    }
}

@Composable
private fun SettingsBadge(text: String, container: Color = MaterialTheme.colorScheme.surfaceContainerHighest) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(container)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    shape: Shape,
    value: String? = null,
    badge: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = shape,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
                .heightIn(min = 64.dp)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SettingsLeadingIcon(icon)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (value != null) {
                    Text(
                        value,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (badge != null) {
                    Box(Modifier.padding(top = 2.dp)) { SettingsBadge(badge) }
                }
            }
            trailing?.invoke()
        }
    }
}

@Composable
private fun SettingsThemeCard(selected: ThemeMode, onSelected: (ThemeMode) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = SettingsSingleShape,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                SettingsLeadingIcon(AppIcons.Theme)
                Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.bodyLarge)
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(4.dp)
                    .selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                SettingsThemeTab(AppIcons.ThemeSystem, R.string.settings_theme_system, selected == ThemeMode.SYSTEM) { onSelected(ThemeMode.SYSTEM) }
                SettingsThemeTab(AppIcons.ThemeLight, R.string.settings_theme_light, selected == ThemeMode.LIGHT) { onSelected(ThemeMode.LIGHT) }
                SettingsThemeTab(AppIcons.ThemeDark, R.string.settings_theme_dark, selected == ThemeMode.DARK) { onSelected(ThemeMode.DARK) }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.SettingsThemeTab(
    icon: ImageVector,
    labelRes: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    Row(
        Modifier
            .weight(1f)
            .heightIn(min = 44.dp)
            .clip(CircleShape)
            .background(if (selected) primary.copy(alpha = 0.16f) else Color.Transparent)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val tint = if (selected) primary else MaterialTheme.colorScheme.onSurfaceVariant
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(IconSize.Small))
        Text(
            stringResource(labelRes),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = tint,
            maxLines = 1,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsCurrencySheet(
    all: List<Currency>,
    selected: Currency,
    onSelect: (Currency) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val matches = remember(query, all) { SettingsLogic.filterCurrencies(all, query) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.navigationBarsPadding()) {
            Text(
                stringResource(R.string.settings_display_currency_choose),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .semantics { heading() },
            )
            TextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
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
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
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
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(matches, key = { it.code }) { currency ->
                        val isSelected = currency.code == selected.code
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.surfaceContainerHighest
                                    else MaterialTheme.colorScheme.surfaceContainer,
                                )
                                .selectable(selected = isSelected, onClick = { onSelect(currency) }, role = Role.RadioButton)
                                .semantics { this.selected = isSelected }
                                .heightIn(min = 56.dp)
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            SettingsBadge(currency.code, MaterialTheme.colorScheme.surfaceContainerHigh)
                            Text(
                                currency.name,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            if (isSelected) {
                                Icon(AppIcons.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsLanguageSheet(
    selected: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
    onDismiss: () -> Unit,
) {
    val resolvedSystem = remember { LocaleResolution.resolvedSystemLanguage() }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.navigationBarsPadding()) {
            Text(
                stringResource(R.string.settings_language_choose),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .semantics { heading() },
            )
            Column(
                Modifier
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                AppLanguage.entries.forEach { lang ->
                    val isSelected = lang == selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.surfaceContainerHighest
                                else MaterialTheme.colorScheme.surfaceContainer,
                            )
                            .selectable(selected = isSelected, onClick = { onSelect(lang) }, role = Role.RadioButton)
                            .heightIn(min = 56.dp)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(lang.labelRes()),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            if (lang == AppLanguage.SYSTEM) {
                                Text(
                                    stringResource(resolvedSystem.labelRes()),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (isSelected) {
                            Icon(AppIcons.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
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
