package com.yehor.finora.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yehor.finora.data.repo.SettingsRepository
import com.yehor.finora.domain.model.ThemeMode
import com.yehor.finora.domain.money.Currency
import com.yehor.finora.domain.money.CurrencyRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val displayCurrency: Currency = CurrencyRegistry.get("USD"),
    val displayCurrencyIsAutomatic: Boolean = true,
    val availableCurrencies: List<Currency> = CurrencyRegistry.all,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
) : ViewModel() {

    val state: StateFlow<SettingsUiState> = combine(
        repository.themeMode,
        repository.displayCurrency,
    ) { theme, display ->
        SettingsUiState(
            themeMode = theme,
            displayCurrency = display.currency,
            displayCurrencyIsAutomatic = display.isAutomatic,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun onThemeSelected(mode: ThemeMode) {
        viewModelScope.launch { repository.setThemeMode(mode) }
    }

    fun onDisplayCurrencySelected(currency: Currency) {
        viewModelScope.launch { repository.setDisplayCurrency(currency.code) }
    }
}
