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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yehor.finora.designsystem.component.navigation.AddFabBottomOffsetDp
import com.yehor.finora.designsystem.component.navigation.AddFabHeightDp
import com.yehor.finora.designsystem.component.navigation.SpeedDialItem
import kotlinx.coroutines.DisposableHandle

/** Bottom space that content must leave free for the floating nav bar: 56dp bar + 14dp gap + 16dp breathing room. */
val ShellContentBottomPaddingDp = 56 + 14 + 16

/** Gap between the last list row and the top of the shell FAB. */
private const val FAB_GAP_DP = 22

/**
 * Extra bottom content padding for a screen that binds the shell FAB, on top of [ShellContentBottomPaddingDp]:
 * the FAB (collapsed or extended, same height) spans [AddFabBottomOffsetDp]..+[AddFabHeightDp] above the nav
 * inset, so the last row needs 84 + 52 + 22 - 86 = 72dp more to scroll fully clear of it.
 */
val ShellFabClearance: Dp = (AddFabBottomOffsetDp + AddFabHeightDp + FAB_GAP_DP - ShellContentBottomPaddingDp).dp

/** Snackbar bottom offset above the nav inset: just over the nav bar, or over the FAB when a screen binds one. */
fun shellSnackbarBottom(fabBound: Boolean): Dp =
    if (fabBound) (AddFabBottomOffsetDp + AddFabHeightDp + 8).dp else 76.dp

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
