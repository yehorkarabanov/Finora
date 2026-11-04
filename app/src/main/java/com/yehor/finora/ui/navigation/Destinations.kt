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
    const val ACCOUNT_NEW = "accounts/new"
    const val ACCOUNT_DETAIL = "accounts/{accountId}"
    const val ACCOUNT_EDIT = "accounts/{accountId}/edit"
    const val STATISTICS = "statistics"
    const val MORE = "more"
    const val CATEGORIES = "more/categories"
    const val CATEGORY_NEW = "more/categories/new?type={type}&parentId={parentId}"
    const val CATEGORY_EDIT = "more/categories/{categoryId}/edit"
    const val TAGS = "more/tags"
    const val RECURRING = "more/recurring"
    const val TRANSFERS = "more/transfers"
    const val SETTINGS = "more/settings"
    const val TRANSACTION_NEW = "transactions/new?kind={kind}&duplicateOf={duplicateOf}"
    const val TRANSACTION_EDIT = "transactions/{operationId}"

    fun accountDetail(id: Long) = "accounts/$id"
    fun accountEdit(id: Long) = "accounts/$id/edit"
    fun categoryNew(type: String, parentId: Long? = null) =
        "more/categories/new?type=$type" + (parentId?.let { "&parentId=$it" } ?: "")
    fun categoryEdit(id: Long) = "more/categories/$id/edit"
    fun transactionsForTag(tagId: Long) = "transactions?tagId=$tagId"
    fun transactionNew(kind: String = "EXPENSE", duplicateOf: Long? = null) =
        "transactions/new?kind=$kind" + (duplicateOf?.let { "&duplicateOf=$it" } ?: "")
    fun transactionEdit(id: Long) = "transactions/$id"
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

/**
 * A tab is selected on its own route and on any route nested under it, such as `accounts/{id}`.
 * `more/...` screens highlight the More tab through the same rule.
 */
fun isRouteUnder(route: String?, tabRoute: String): Boolean =
    route != null && (route == tabRoute || route.startsWith("$tabRoute/"))
