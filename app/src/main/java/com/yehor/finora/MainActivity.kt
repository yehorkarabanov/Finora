package com.yehor.finora

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yehor.finora.ui.navigation.FinoraShell
import com.yehor.finora.ui.settings.SettingsViewModel
import com.yehor.finora.ui.theme.FinoraTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onStart() {
        super.onStart()
        // Re-check staleness when the app returns to the foreground.
        (application as FinoraApp).rates.requestRefresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val state by settingsViewModel.state.collectAsStateWithLifecycle()
            FinoraTheme(themeMode = state.themeMode) {
                FinoraShell()
            }
        }
    }
}
