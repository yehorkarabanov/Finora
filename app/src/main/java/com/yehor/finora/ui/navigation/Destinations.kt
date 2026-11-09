package com.yehor.finora.ui.navigation

import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import com.yehor.finora.ui.common.AppIcons
import com.yehor.finora.R
import com.yehor.finora.domain.operation.FilterCodec
import com.yehor.finora.domain.operation.TransactionFilter

object Routes {
    const val DASHBOARD = "dashboard"
    const val TRANSACTIONS = "transactions"
    const val TRANSACTIONS_PRESET =
        "transactions?accountId={accountId}&tagId={tagId}&categoryId={categoryId}&types={types}&filter={filter}"
    const val ACCOUNTS = "accounts"
    const val ACCOUNT_NEW = "accounts/new"
    const val ACCOUNT_DETAIL = "accounts/{accountId}"
    const val ACCOUNT_EDIT = "accounts/{accountId}/edit"
    const val STATISTICS = "statistics"
    const val STATISTICS_PRESET = "statistics?tab={tab}&tagId={tagId}"
    const val ACCOUNT_ANALYTICS = "statistics/accounts/{accountId}"
    const val MORE = "more"
    const val CATEGORIES = "more/categories"
    const val CATEGORY_NEW = "more/categories/new?type={type}&parentId={parentId}"
    const val CATEGORY_EDIT = "more/categories/{categoryId}/edit"
    const val TAGS = "more/tags"
    const val RECURRING = "more/recurring"
    const val RECURRING_NEW = "more/recurring/new"
    const val RECURRING_DETAIL = "more/recurring/{scheduleId}"
    const val RECURRING_EDIT = "more/recurring/{scheduleId}/edit"
    const val SETTINGS = "more/settings"
    const val DATA = "more/settings/data"
    const val DATA_CSV = "more/settings/data/csv?filter={filter}"
    // One unified editor (Expense / Income / Transfer); `kind` picks the tab.
    const val TRANSACTION_NEW = "transactions/new?kind={kind}&duplicateOf={duplicateOf}&sourceAccountId={sourceAccountId}"
    const val TRANSACTION_EDIT = "transactions/{operationId}?kind={kind}"

    fun accountDetail(id: Long) = "accounts/$id"
    fun accountEdit(id: Long) = "accounts/$id/edit"
    fun categoryNew(type: String, parentId: Long? = null) =
        "more/categories/new?type=$type" + (parentId?.let { "&parentId=$it" } ?: "")
    fun categoryEdit(id: Long) = "more/categories/$id/edit"
    fun transactionsForTag(tagId: Long) = "transactions?tagId=$tagId"
    fun transactionsForAccount(accountId: Long) = "transactions?accountId=$accountId"
    fun transactionsForCategory(categoryId: Long) = "transactions?categoryId=$categoryId"

    /** History preset from an analytics row. An empty filter opens the plain list. */
    fun transactions(filter: TransactionFilter): String =
        "transactions?filter=" + Uri.encode(FilterCodec.encode(filter))
    fun statisticsTab(tab: String, tagId: Long? = null) =
        "statistics?tab=$tab" + (tagId?.let { "&tagId=$it" } ?: "")
    fun accountAnalytics(accountId: Long) = "statistics/accounts/$accountId"
    fun transactionNew(kind: String = "EXPENSE", duplicateOf: Long? = null, sourceAccountId: Long? = null) =
        "transactions/new?kind=$kind" +
            (duplicateOf?.let { "&duplicateOf=$it" } ?: "") +
            (sourceAccountId?.let { "&sourceAccountId=$it" } ?: "")
    fun transactionEdit(id: Long, kind: String? = null) =
        "transactions/$id" + (kind?.let { "?kind=$it" } ?: "")
    fun transferNew(duplicateOf: Long? = null, sourceAccountId: Long? = null) =
        transactionNew(kind = "TRANSFER", duplicateOf = duplicateOf, sourceAccountId = sourceAccountId)
    fun transferEdit(id: Long) = transactionEdit(id, kind = "TRANSFER")
    fun recurringDetail(id: Long) = "more/recurring/$id"
    fun recurringEdit(id: Long) = "more/recurring/$id/edit"
    fun dataCsv(filter: TransactionFilter = TransactionFilter()): String =
        "more/settings/data/csv?filter=" + Uri.encode(FilterCodec.encode(filter))
}

enum class TopLevelDestination(
    val route: String,
    @StringRes val label: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    DASHBOARD(Routes.DASHBOARD, R.string.nav_dashboard, AppIcons.NavDashboard, AppIcons.NavDashboardSelected),
    TRANSACTIONS(Routes.TRANSACTIONS, R.string.nav_transactions, AppIcons.NavTransactions, AppIcons.NavTransactionsSelected),
    ACCOUNTS(Routes.ACCOUNTS, R.string.nav_accounts, AppIcons.NavAccounts, AppIcons.NavAccountsSelected),
    STATISTICS(Routes.STATISTICS, R.string.nav_statistics, AppIcons.NavStatistics, AppIcons.NavStatisticsSelected),
    MORE(Routes.MORE, R.string.nav_more, AppIcons.NavMore, AppIcons.NavMoreSelected),
}

/**
 * A tab is selected on its own route and on any route nested under it, such as `accounts/{id}`.
 * `more/...` screens highlight the More tab through the same rule.
 */
fun isRouteUnder(route: String?, tabRoute: String): Boolean =
    route != null && (route == tabRoute || route.startsWith("$tabRoute/") || route.startsWith("$tabRoute?"))
