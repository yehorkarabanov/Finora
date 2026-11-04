package com.yehor.finora.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yehor.finora.R
import com.yehor.finora.domain.model.ThemeMode

@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SettingsContent(
        state = state,
        onBack = onBack,
        onThemeSelected = viewModel::onThemeSelected,
        onCurrencySelected = viewModel::onDisplayCurrencySelected,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsContent(
    state: SettingsUiState,
    onBack: () -> Unit,
    onThemeSelected: (ThemeMode) -> Unit,
    onCurrencySelected: (com.yehor.finora.domain.money.Currency) -> Unit,
) {
    var showCurrencyDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            Text(
                stringResource(R.string.settings_theme),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Column(Modifier.selectableGroup()) {
                ThemeOption(R.string.settings_theme_system, ThemeMode.SYSTEM, state.themeMode, onThemeSelected)
                ThemeOption(R.string.settings_theme_light, ThemeMode.LIGHT, state.themeMode, onThemeSelected)
                ThemeOption(R.string.settings_theme_dark, ThemeMode.DARK, state.themeMode, onThemeSelected)
            }
            ListItem(
                overlineContent = { Text(stringResource(R.string.settings_display_currency)) },
                headlineContent = {
                    val label = "${state.displayCurrency.code} (${state.displayCurrency.name})"
                    Text(
                        if (state.displayCurrencyIsAutomatic) {
                            stringResource(R.string.settings_display_currency_automatic, label)
                        } else {
                            label
                        },
                    )
                },
                modifier = Modifier.clickable { showCurrencyDialog = true },
            )
        }
    }

    if (showCurrencyDialog) {
        AlertDialog(
            onDismissRequest = { showCurrencyDialog = false },
            title = { Text(stringResource(R.string.settings_display_currency_choose)) },
            text = {
                LazyColumn {
                    items(state.availableCurrencies, key = { it.code }) { currency ->
                        ListItem(
                            headlineContent = { Text("${currency.code} (${currency.name})") },
                            modifier = Modifier.clickable {
                                onCurrencySelected(currency)
                                showCurrencyDialog = false
                            },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCurrencyDialog = false }) {
                    Text(stringResource(R.string.settings_close))
                }
            },
        )
    }
}

@Composable
private fun ThemeOption(
    label: Int,
    mode: ThemeMode,
    selected: ThemeMode,
    onSelected: (ThemeMode) -> Unit,
) {
    Row(
        Modifier
            .selectable(selected = mode == selected, onClick = { onSelected(mode) }, role = Role.RadioButton)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = mode == selected, onClick = null)
        Text(stringResource(label), modifier = Modifier.padding(start = 16.dp))
    }
}
