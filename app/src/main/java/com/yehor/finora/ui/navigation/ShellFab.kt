package com.yehor.finora.ui.navigation

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.yehor.finora.designsystem.component.navigation.SpeedDialItem
import kotlinx.coroutines.DisposableHandle

/** Bottom space that content must leave free for the floating nav bar: 56dp bar + 14dp gap + 16dp breathing room. */
val ShellContentBottomPaddingDp = 56 + 14 + 16

/** A screen's FAB registration. */
@Stable
class ShellFabRegistration(
    val listState: LazyListState,
    val items: List<SpeedDialItem>?,
    val label: String,
    val onClick: () -> Unit,
)

/**
 * Shell-owned FAB slot. A screen registers its FAB with [bind]; the shell renders it, derives
 * its mode from [ShellFabRegistration.listState] and owns the expanded speed-dial state.
 * Tabs without a registration show no FAB.
 */
@Stable
class ShellFabController {
    var registration by mutableStateOf<ShellFabRegistration?>(null)
        private set

    /**
     * Registers a FAB. With [items] == null a tap calls [onClick] directly; otherwise it opens the speed dial.
     * Dispose the returned handle to clear the registration (a newer registration is left untouched).
     */
    fun bind(listState: LazyListState, items: List<SpeedDialItem>?, label: String, onClick: () -> Unit): DisposableHandle {
        val mine = ShellFabRegistration(listState, items, label, onClick)
        registration = mine
        return DisposableHandle { if (registration === mine) registration = null }
    }
}

val LocalShellFab = staticCompositionLocalOf { ShellFabController() }

/** Opens the More sheet. */
val LocalOpenMore = staticCompositionLocalOf<() -> Unit> { {} }

/**
 * Registers the screen's FAB with the shell for as long as it is in composition.
 * Pass a remembered [items] list; changing it re-registers.
 */
@Composable
fun BindShellFab(listState: LazyListState, items: List<SpeedDialItem>?, label: String, onClick: () -> Unit) {
    val controller = LocalShellFab.current
    val latestOnClick by rememberUpdatedState(onClick)
    DisposableEffect(controller, listState, items, label) {
        val handle = controller.bind(listState, items, label) { latestOnClick() }
        onDispose { handle.dispose() }
    }
}
