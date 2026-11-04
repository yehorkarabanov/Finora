package com.yehor.finora.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.ui.graphics.vector.ImageVector
import com.yehor.finora.R

object Routes {
    const val DASHBOARD = "dashboard"
    const val TRANSACTIONS = "transactions"
    const val ACCOUNTS = "accounts"
    const val STATISTICS = "statistics"
    const val MORE = "more"
    const val CATEGORIES = "more/categories"
    const val TAGS = "more/tags"
    const val RECURRING = "more/recurring"
    const val TRANSFERS = "more/transfers"
    const val SETTINGS = "more/settings"
}

enum class TopLevelDestination(
    val route: String,
    @StringRes val label: Int,
    val icon: ImageVector,
) {
    DASHBOARD(Routes.DASHBOARD, R.string.nav_dashboard, Icons.Outlined.Dashboard),
    TRANSACTIONS(Routes.TRANSACTIONS, R.string.nav_transactions, Icons.Outlined.Receipt),
    ACCOUNTS(Routes.ACCOUNTS, R.string.nav_accounts, Icons.Outlined.AccountBalanceWallet),
    STATISTICS(Routes.STATISTICS, R.string.nav_statistics, Icons.Outlined.BarChart),
    MORE(Routes.MORE, R.string.nav_more, Icons.Outlined.MoreHoriz),
}
