package com.yehor.finora.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.filled.AccountBalanceWallet as FilledWallet
import androidx.compose.material.icons.filled.BarChart as FilledBarChart
import androidx.compose.material.icons.filled.Dashboard as FilledDashboard
import androidx.compose.material.icons.filled.MoreHoriz as FilledMoreHoriz
import androidx.compose.material.icons.filled.Receipt as FilledReceipt
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.CurrencyExchange
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The only source of UI-chrome icons. Call sites never import `Icons.*` directly.
 *
 * Rules: Outlined family; directional icons are AutoMirrored; one icon per concept.
 * User-content icons (categories, accounts) live in [IconCatalog] and use the Rounded family.
 */
object AppIcons {
    // Navigation and actions
    val Back: ImageVector get() = Icons.AutoMirrored.Outlined.ArrowBack
    val Close: ImageVector get() = Icons.Outlined.Close
    val Add: ImageVector get() = Icons.Outlined.Add
    val Remove: ImageVector get() = Icons.Outlined.Remove
    val AddCircle: ImageVector get() = Icons.Outlined.AddCircleOutline
    val Edit: ImageVector get() = Icons.Outlined.Edit
    val Delete: ImageVector get() = Icons.Outlined.Delete
    val More: ImageVector get() = Icons.Outlined.MoreVert
    val Export: ImageVector get() = Icons.Outlined.FileDownload
    val Backspace: ImageVector get() = Icons.AutoMirrored.Outlined.Backspace

    // Search, filter, sort
    val Search: ImageVector get() = Icons.Outlined.Search
    val SearchEmpty: ImageVector get() = Icons.Outlined.SearchOff
    val Sort: ImageVector get() = Icons.AutoMirrored.Outlined.Sort
    val SwapVertical: ImageVector get() = Icons.Outlined.SwapVert

    // Concepts
    val Calendar: ImageVector get() = Icons.Outlined.CalendarMonth
    val Time: ImageVector get() = Icons.Outlined.Schedule
    val Account: ImageVector get() = Icons.Outlined.AccountBalanceWallet
    val Category: ImageVector get() = Icons.Outlined.Category
    val Tag: ImageVector get() = Icons.Outlined.Sell
    val Merchant: ImageVector get() = Icons.Outlined.Storefront
    val Receipt: ImageVector get() = Icons.AutoMirrored.Outlined.ReceiptLong

    // Transaction kinds: Income = money in (down), Expense = money out (up)
    val Income: ImageVector get() = Icons.Outlined.ArrowDownward
    val Expense: ImageVector get() = Icons.Outlined.ArrowUpward
    val Transfer: ImageVector get() = Icons.Outlined.SwapHoriz
    val Recurring: ImageVector get() = Icons.Outlined.Repeat
    val Pause: ImageVector get() = Icons.Outlined.Pause
    val Resume: ImageVector get() = Icons.Outlined.PlayArrow
    val SkipNext: ImageVector get() = Icons.Outlined.SkipNext

    // Disclosure
    val Expand: ImageVector get() = Icons.Outlined.ExpandMore
    val Collapse: ImageVector get() = Icons.Outlined.ExpandLess
    val Dropdown: ImageVector get() = Icons.Outlined.ArrowDropDown
    val Next: ImageVector get() = Icons.AutoMirrored.Outlined.KeyboardArrowRight
    val Previous: ImageVector get() = Icons.AutoMirrored.Outlined.KeyboardArrowLeft

    // Status
    val Check: ImageVector get() = Icons.Outlined.Check
    val Success: ImageVector get() = Icons.Outlined.CheckCircle
    val Warning: ImageVector get() = Icons.Outlined.Warning
    val Error: ImageVector get() = Icons.Outlined.ErrorOutline
    val Help: ImageVector get() = Icons.AutoMirrored.Outlined.HelpOutline
    val Lock: ImageVector get() = Icons.Outlined.Lock
    val Unlock: ImageVector get() = Icons.Outlined.LockOpen

    // Settings
    val Theme: ImageVector get() = Icons.Outlined.Palette
    val ThemeSystem: ImageVector get() = Icons.Outlined.BrightnessAuto
    val ThemeLight: ImageVector get() = Icons.Outlined.LightMode
    val ThemeDark: ImageVector get() = Icons.Outlined.DarkMode
    val Currency: ImageVector get() = Icons.Outlined.CurrencyExchange
    val Settings: ImageVector get() = Icons.Outlined.Settings
    val Data: ImageVector get() = Icons.Outlined.Storage
    val Info: ImageVector get() = Icons.Outlined.Info

    // Bottom navigation: unselected Outlined, selected Filled
    val NavDashboard: ImageVector get() = Icons.Outlined.Dashboard
    val NavDashboardSelected: ImageVector get() = Icons.Filled.FilledDashboard
    val NavTransactions: ImageVector get() = Icons.Outlined.Receipt
    val NavTransactionsSelected: ImageVector get() = Icons.Filled.FilledReceipt
    val NavAccounts: ImageVector get() = Icons.Outlined.AccountBalanceWallet
    val NavAccountsSelected: ImageVector get() = Icons.Filled.FilledWallet
    val NavStatistics: ImageVector get() = Icons.Outlined.BarChart
    val NavStatisticsSelected: ImageVector get() = Icons.Filled.FilledBarChart
    val NavMore: ImageVector get() = Icons.Outlined.MoreHoriz
    val NavMoreSelected: ImageVector get() = Icons.Filled.FilledMoreHoriz
}

/** Icon size scale. */
object IconSize {
    /** Inside pills, chips, small buttons. */
    val Small: Dp = 18.dp

    /** Trailing dropdown carets in selector pills. */
    val Medium: Dp = 20.dp

    /** IconButtons and list leading icons. */
    val Default: Dp = 24.dp
}
