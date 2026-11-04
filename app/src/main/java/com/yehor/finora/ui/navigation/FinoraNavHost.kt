package com.yehor.finora.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.clickable
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
import com.yehor.finora.ui.categories.CategoriesScreen
import com.yehor.finora.ui.categories.CategoryEditScreen
import com.yehor.finora.ui.operations.TransactionEditScreen
import com.yehor.finora.ui.operations.TransactionsScreen
import com.yehor.finora.ui.tags.TagsScreen
import com.yehor.finora.ui.settings.SettingsScreen

@Composable
fun FinoraShell() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        // Each screen has its own Scaffold and handles the top inset itself.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar {
                TopLevelDestination.entries.forEach { dest ->
                    NavigationBarItem(
                        selected = currentDestination?.hierarchy?.any { isRouteUnder(it.route, dest.route) } == true,
                        onClick = {
                            navController.navigate(dest.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(dest.icon, contentDescription = null) },
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
        composable(Routes.DASHBOARD) { PlaceholderScreen(R.string.nav_dashboard) }
        composable(
            Routes.TRANSACTIONS_PRESET,
            arguments = listOf(
                navArgument("accountId") { type = NavType.LongType; defaultValue = -1L },
                navArgument("tagId") { type = NavType.LongType; defaultValue = -1L },
                navArgument("categoryId") { type = NavType.LongType; defaultValue = -1L },
            ),
        ) {
            TransactionsScreen(
                onAdd = { navController.navigate(Routes.transactionNew()) },
                onOpenTransaction = { id -> navController.navigate(Routes.transactionEdit(id)) },
            )
        }
        composable(Routes.ACCOUNTS) {
            AccountsScreen(
                onOpen = { navController.navigate(Routes.accountDetail(it)) },
                onAdd = { navController.navigate(Routes.ACCOUNT_NEW) },
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
                onOpenTransaction = { navController.navigate(Routes.transactionEdit(it)) },
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
            ),
        ) {
            TransactionEditScreen(
                onBack = { navController.popBackStack() },
                onNavigateCreateCategory = { type -> navController.navigate(Routes.categoryNew(type)) },
            )
        }
        composable(
            Routes.TRANSACTION_EDIT,
            arguments = listOf(navArgument("operationId") { type = NavType.LongType }),
        ) {
            TransactionEditScreen(
                onBack = { navController.popBackStack() },
                onDuplicate = { id ->
                    navController.navigate(Routes.transactionNew(duplicateOf = id)) {
                        popUpTo(Routes.TRANSACTION_EDIT) { inclusive = true }
                    }
                },
                onNavigateCreateCategory = { type -> navController.navigate(Routes.categoryNew(type)) },
            )
        }
        composable(Routes.STATISTICS) { PlaceholderScreen(R.string.nav_statistics) }
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
            )
        }
        composable(Routes.RECURRING) {
            PlaceholderScreen(R.string.more_recurring, onBack = { navController.popBackStack() })
        }
        composable(Routes.TRANSFERS) {
            PlaceholderScreen(R.string.more_transfers, onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceholderScreen(@StringRes title: Int, onBack: (() -> Unit)? = null) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(title)) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.navigate_back),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.placeholder_coming_soon))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(onNavigate: (String) -> Unit) {
    val items = listOf(
        R.string.more_categories to Routes.CATEGORIES,
        R.string.more_tags to Routes.TAGS,
        R.string.more_recurring to Routes.RECURRING,
        R.string.more_transfers to Routes.TRANSFERS,
        R.string.more_settings to Routes.SETTINGS,
    )
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_more)) }) }) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            items(items.size) { index ->
                val (label, route) = items[index]
                ListItem(
                    headlineContent = { Text(stringResource(label)) },
                    modifier = Modifier.clickable { onNavigate(route) },
                )
            }
        }
    }
}
