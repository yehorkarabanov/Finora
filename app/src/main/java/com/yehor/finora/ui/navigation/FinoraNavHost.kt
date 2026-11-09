package com.yehor.finora.ui.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.res.pluralStringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.yehor.finora.R
import com.yehor.finora.ui.accounts.AccountDetailScreen
import com.yehor.finora.ui.accounts.AccountEditScreen
import com.yehor.finora.ui.accounts.AccountsScreen
import com.yehor.finora.domain.operation.TransactionFilter
import com.yehor.finora.ui.analytics.AccountAnalyticsScreen
import com.yehor.finora.ui.analytics.DashboardActions
import com.yehor.finora.ui.analytics.DashboardScreen
import com.yehor.finora.ui.analytics.StatisticsActions
import com.yehor.finora.ui.analytics.StatisticsScreen
import com.yehor.finora.ui.categories.CategoriesScreen
import com.yehor.finora.ui.categories.CategoryEditScreen
import com.yehor.finora.domain.model.OperationType
import com.yehor.finora.domain.operation.TransactionItem
import com.yehor.finora.ui.operations.TransactionEditScreen
import com.yehor.finora.ui.operations.TransactionsScreen
import com.yehor.finora.ui.recurring.RecurringDetailScreen
import com.yehor.finora.ui.recurring.RecurringEditScreen
import com.yehor.finora.ui.recurring.RecurringScreen
import com.yehor.finora.ui.tags.TagsScreen
import com.yehor.finora.ui.settings.SettingsScreen
import com.yehor.finora.ui.data.CsvExportScreen
import com.yehor.finora.ui.data.DataScreen

// Full-screen forms with their own bottom CTA.
private val ROUTES_WITHOUT_BOTTOM_BAR = setOf(
    Routes.TRANSACTION_NEW,
    Routes.TRANSACTION_EDIT,
)

@Composable
fun FinoraShell(shellViewModel: ShellViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val snackbarHostState = remember { SnackbarHostState() }
    val showBottomBar = currentDestination?.route !in ROUTES_WITHOUT_BOTTOM_BAR

    val outcome = shellViewModel.restoreOutcome
    val accounts = outcome?.summary?.accounts ?: 0
    val transactions = outcome?.summary?.transactions ?: 0
    val outcomeMessage = when {
        outcome == null -> null
        outcome.success -> stringResource(
            R.string.data_restore_outcome_success,
            pluralStringResource(R.plurals.data_restore_outcome_accounts, accounts, accounts),
            pluralStringResource(R.plurals.data_restore_outcome_transactions, transactions, transactions),
        )
        else -> stringResource(R.string.data_restore_outcome_failed)
    }
    LaunchedEffect(outcomeMessage) {
        if (outcomeMessage != null) snackbarHostState.showSnackbar(outcomeMessage)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        // Each screen has its own Scaffold and handles the top inset itself.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) NavigationBar {
                TopLevelDestination.entries.forEach { dest ->
                    val selected = currentDestination?.hierarchy?.any { isRouteUnder(it.route, dest.route) } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            // Always open the tab's root screen. Sub-screens opened earlier in the tab
                            // are discarded, not saved and restored.
                            navController.navigate(dest.route) {
                                popUpTo(navController.graph.findStartDestination().id)
                                launchSingleTop = true
                            }
                        },
                        icon = { Icon(if (selected) dest.selectedIcon else dest.icon, contentDescription = null) },
                        label = { Text(stringResource(dest.label)) },
                    )
                }
            }
        },
    ) { padding ->
        FinoraNavHost(navController, padding)
    }
}

@Composable
private fun FinoraNavHost(navController: NavHostController, padding: PaddingValues) {
    NavHost(
        navController = navController,
        startDestination = Routes.DASHBOARD,
        modifier = Modifier.padding(padding).consumeWindowInsets(padding),
    ) {
        composable(Routes.DASHBOARD) {
            DashboardScreen(
                DashboardActions(
                    onAddTransaction = { navController.navigate(Routes.transactionNew()) },
                    onAddIncome = { navController.navigate(Routes.transactionNew(kind = "INCOME")) },
                    onAddTransfer = { navController.navigate(Routes.transferNew()) },
                    onAddAccount = { navController.navigate(Routes.ACCOUNT_NEW) },
                    onOpenAccounts = { navController.navigate(Routes.ACCOUNTS) },
                    onOpenAccountHistory = { navController.navigate(Routes.accountDetail(it)) },
                    onOpenTransactions = { navController.openTransactions(it) },
                    onOpenStatistics = { navController.navigate(Routes.statisticsTab("EXPENSES")) },
                    onOpenTransaction = { navController.navigate(openRoute(it)) },
                    onOpenSchedule = { navController.navigate(Routes.recurringDetail(it)) },
                    onOpenRecurring = { navController.navigate(Routes.RECURRING) },
                ),
            )
        }
        composable(
            Routes.TRANSACTIONS_PRESET,
            arguments = listOf(
                navArgument("accountId") { type = NavType.LongType; defaultValue = -1L },
                navArgument("tagId") { type = NavType.LongType; defaultValue = -1L },
                navArgument("categoryId") { type = NavType.LongType; defaultValue = -1L },
                navArgument("types") { type = NavType.StringType; defaultValue = "" },
                navArgument("filter") { type = NavType.StringType; defaultValue = "" },
            ),
        ) {
            TransactionsScreen(
                onAdd = { navController.navigate(Routes.transactionNew()) },
                onOpenTransaction = { navController.navigate(openRoute(it)) },
                onExportCsv = { navController.navigate(Routes.dataCsv(it)) },
            )
        }
        composable(Routes.ACCOUNTS) {
            AccountsScreen(
                onOpen = { navController.navigate(Routes.accountDetail(it)) },
                onAdd = { navController.navigate(Routes.ACCOUNT_NEW) },
                onAddTransfer = { navController.navigate(Routes.transferNew()) },
            )
        }
        composable(Routes.ACCOUNT_NEW) {
            AccountEditScreen(onBack = { navController.popBackStack() })
        }
        composable(
            Routes.ACCOUNT_DETAIL,
            arguments = listOf(navArgument("accountId") { type = NavType.LongType }),
        ) {
            AccountDetailScreen(
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate(Routes.accountEdit(it)) },
                onTransfer = { navController.navigate(Routes.transferNew(sourceAccountId = it)) },
                onOpenTransaction = { navController.navigate(openRoute(it)) },
                onOpenStatistics = { navController.navigate(Routes.accountAnalytics(it)) },
                onShowAllTransactions = { accountId ->
                    navController.navigate(Routes.transactionsForAccount(accountId)) {
                        launchSingleTop = true
                        restoreState = false
                    }
                },
            )
        }
        composable(
            Routes.ACCOUNT_EDIT,
            arguments = listOf(navArgument("accountId") { type = NavType.LongType }),
        ) {
            AccountEditScreen(onBack = { navController.popBackStack() })
        }
        composable(
            Routes.TRANSACTION_NEW,
            arguments = listOf(
                navArgument("kind") { type = NavType.StringType; defaultValue = "EXPENSE" },
                navArgument("duplicateOf") { type = NavType.LongType; defaultValue = -1L },
                navArgument("sourceAccountId") { type = NavType.LongType; defaultValue = -1L },
            ),
        ) {
            TransactionEditScreen(
                onBack = { navController.popBackStack() },
                onNavigateCreateCategory = { type -> navController.navigate(Routes.categoryNew(type)) },
            )
        }
        composable(
            Routes.TRANSACTION_EDIT,
            arguments = listOf(
                navArgument("operationId") { type = NavType.LongType },
                navArgument("kind") { type = NavType.StringType; defaultValue = "" },
            ),
        ) {
            TransactionEditScreen(
                onBack = { navController.popBackStack() },
                onDuplicate = { id, mode ->
                    navController.navigate(Routes.transactionNew(kind = mode.arg, duplicateOf = id)) {
                        popUpTo(Routes.TRANSACTION_EDIT) { inclusive = true }
                    }
                },
                onNavigateCreateCategory = { type -> navController.navigate(Routes.categoryNew(type)) },
                onOpenSchedule = { navController.navigate(Routes.recurringDetail(it)) },
            )
        }
        composable(
            Routes.STATISTICS_PRESET,
            arguments = listOf(
                navArgument("tab") { type = NavType.StringType; defaultValue = "EXPENSES" },
                navArgument("tagId") { type = NavType.LongType; defaultValue = -1L },
            ),
        ) {
            StatisticsScreen(
                StatisticsActions(
                    onOpenTransactions = { navController.openTransactions(it) },
                    onOpenAccountAnalytics = { navController.navigate(Routes.accountAnalytics(it)) },
                    onOpenTransaction = { navController.navigate(openRoute(it)) },
                ),
            )
        }
        composable(
            Routes.ACCOUNT_ANALYTICS,
            arguments = listOf(navArgument("accountId") { type = NavType.LongType }),
        ) {
            AccountAnalyticsScreen(
                onBack = { navController.popBackStack() },
                onOpenTransaction = { navController.navigate(openRoute(it)) },
                onShowAll = { navController.openTransactions(it) },
            )
        }
        composable(Routes.MORE) {
            MoreScreen(onNavigate = { navController.navigate(it) })
        }
        composable(Routes.CATEGORIES) {
            CategoriesScreen(
                onBack = { navController.popBackStack() },
                onAdd = { type, parentId -> navController.navigate(Routes.categoryNew(type.name, parentId)) },
                onEdit = { navController.navigate(Routes.categoryEdit(it)) },
                onReviewTransactions = { categoryId ->
                    navController.navigate(Routes.transactionsForCategory(categoryId)) {
                        launchSingleTop = true
                        restoreState = false
                    }
                },
            )
        }
        composable(
            Routes.CATEGORY_NEW,
            arguments = listOf(
                navArgument("type") { type = NavType.StringType; defaultValue = "EXPENSE" },
                navArgument("parentId") { type = NavType.LongType; defaultValue = -1L },
            ),
        ) {
            CategoryEditScreen(onBack = { navController.popBackStack() })
        }
        composable(
            Routes.CATEGORY_EDIT,
            arguments = listOf(navArgument("categoryId") { type = NavType.LongType }),
        ) {
            CategoryEditScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.TAGS) {
            TagsScreen(
                onBack = { navController.popBackStack() },
                onShowTransactions = { tagId ->
                    navController.navigate(Routes.transactionsForTag(tagId)) {
                        launchSingleTop = true
                        restoreState = false
                    }
                },
                onAnalyze = { tagId ->
                    navController.navigate(Routes.statisticsTab("TAGS", tagId)) {
                        launchSingleTop = true
                        restoreState = false
                    }
                },
            )
        }
        composable(Routes.RECURRING) {
            RecurringScreen(
                onBack = { navController.popBackStack() },
                onAdd = { navController.navigate(Routes.RECURRING_NEW) },
                onOpen = { navController.navigate(Routes.recurringDetail(it)) },
            )
        }
        // `new` is a literal segment and wins over {scheduleId}.
        composable(Routes.RECURRING_NEW) {
            RecurringEditScreen(
                onBack = { navController.popBackStack() },
                onNavigateCreateCategory = { type -> navController.navigate(Routes.categoryNew(type)) },
            )
        }
        composable(
            Routes.RECURRING_DETAIL,
            arguments = listOf(navArgument("scheduleId") { type = NavType.LongType }),
        ) {
            RecurringDetailScreen(
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate(Routes.recurringEdit(it)) },
                onOpenTransaction = { navController.navigate(Routes.transactionEdit(it)) },
            )
        }
        composable(
            Routes.RECURRING_EDIT,
            arguments = listOf(navArgument("scheduleId") { type = NavType.LongType }),
        ) {
            RecurringEditScreen(
                onBack = { navController.popBackStack() },
                onNavigateCreateCategory = { type -> navController.navigate(Routes.categoryNew(type)) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenData = { navController.navigate(Routes.DATA) },
            )
        }
        composable(Routes.DATA) {
            DataScreen(
                onBack = { navController.popBackStack() },
                onOpenCsvExport = { navController.navigate(Routes.dataCsv()) },
            )
        }
        composable(
            Routes.DATA_CSV,
            arguments = listOf(navArgument("filter") { type = NavType.StringType; defaultValue = "" }),
        ) {
            CsvExportScreen(onBack = { navController.popBackStack() })
        }
    }
}

private fun NavHostController.openTransactions(filter: TransactionFilter) {
    navigate(Routes.transactions(filter)) {
        launchSingleTop = true
        restoreState = false
    }
}

private fun openRoute(item: TransactionItem): String =
    if (item.type == OperationType.TRANSFER) Routes.transactionEdit(item.id, kind = "TRANSFER") else Routes.transactionEdit(item.id)
