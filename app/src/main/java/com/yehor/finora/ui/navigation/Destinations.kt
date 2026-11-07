package com.yehor.finora.ui.navigation

import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.ui.graphics.vector.ImageVector
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
    const val TRANSFERS = "more/transfers"
    const val TRANSFERS_PRESET = "more/transfers?types={types}"
    const val SETTINGS = "more/settings"
    const val DATA = "more/settings/data"
    const val DATA_CSV = "more/settings/data/csv?filter={filter}"
    const val TRANSACTION_NEW = "transactions/new?kind={kind}&duplicateOf={duplicateOf}"
    const val TRANSACTION_EDIT = "transactions/{operationId}"
    const val TRANSFER_NEW = "transfers/new?duplicateOf={duplicateOf}&sourceAccountId={sourceAccountId}"
    const val TRANSFER_EDIT = "transfers/{operationId}"

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
    fun transactionNew(kind: String = "EXPENSE", duplicateOf: Long? = null) =
        "transactions/new?kind=$kind" + (duplicateOf?.let { "&duplicateOf=$it" } ?: "")
    fun transactionEdit(id: Long) = "transactions/$id"
    fun transferNew(duplicateOf: Long? = null, sourceAccountId: Long? = null): String {
        val params = listOfNotNull(
            duplicateOf?.let { "duplicateOf=$it" },
            sourceAccountId?.let { "sourceAccountId=$it" },
        )
        return "transfers/new" + if (params.isEmpty()) "" else params.joinToString("&", prefix = "?")
    }
    fun transferEdit(id: Long) = "transfers/$id"
    fun recurringDetail(id: Long) = "more/recurring/$id"
    fun recurringEdit(id: Long) = "more/recurring/$id/edit"
    fun dataCsv(filter: TransactionFilter = TransactionFilter()): String =
        "more/settings/data/csv?filter=" + Uri.encode(FilterCodec.encode(filter))
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
    route != null && (route == tabRoute || route.startsWith("$tabRoute/") || route.startsWith("$tabRoute?"))
