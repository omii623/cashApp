package com.example.cashapp.ui.navigation

import com.example.cashapp.ui.recurring.RuleEditRoute
import com.example.cashapp.ui.recurring.RecurringRoute
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.example.cashapp.domain.model.TransactionType
import com.example.cashapp.ui.transaction.TransactionFormRoute
import com.example.cashapp.ui.category.CategoriesRoute
import com.example.cashapp.ui.category.CategoryEditRoute
import com.example.cashapp.ui.account.AccountDetailRoute
import com.example.cashapp.ui.account.AccountEditRoute
import com.example.cashapp.ui.account.AccountsRoute
import com.example.cashapp.ui.exchange.ExchangeRoute
import com.example.cashapp.ui.backup.BackupRoute
import com.example.cashapp.ui.currency.CurrenciesRoute
import com.example.cashapp.ui.more.MoreScreen
import com.example.cashapp.ui.reports.ReportsRoute
import com.example.cashapp.ui.settings.SettingsRoute
import com.example.cashapp.ui.transactions.TransactionsRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.cashapp.R
import com.example.cashapp.ui.components.PlaceholderScreen
import com.example.cashapp.ui.home.HomeActions
import com.example.cashapp.ui.home.HomeRoute

/** Az app váza: alsó sáv a fő oldalakon + a navigációs gráf. */
/**
 * @param openPendingRequest értesítésre koppintáskor nő – ilyenkor megnyílik az Ismétlődő tételek oldal.
 */
@Composable
fun CashApp(openPendingRequest: Int = 0) {
    val navController = rememberNavController()
    LaunchedEffect(openPendingRequest) {
        if (openPendingRequest > 0) navController.navigate(Routes.RECURRING) { launchSingleTop = true }
    }
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomBar = TopLevelDestination.entries.any { it.route == currentRoute }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    TopLevelDestination.entries.forEach { dest ->
                        NavigationBarItem(
                            selected = currentRoute == dest.route,
                            onClick = { navController.navigateTopLevel(dest.route) },
                            icon = { Icon(dest.icon, contentDescription = null) },
                            label = { Text(stringResource(dest.label)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(navController, startDestination = Routes.HOME, modifier = Modifier.padding(padding)) {
            composable(Routes.HOME) {
                HomeRoute(
                    HomeActions(
                        onIncome = { navController.navigate(Routes.transactionForm(TransactionType.INCOME)) },
                        onExpense = { navController.navigate(Routes.transactionForm(TransactionType.EXPENSE)) },
                        onExchange = { navController.navigate(Routes.EXCHANGE_NEW) },
                        onTransaction = { navController.navigate(Routes.editTransaction(it.type, it.id)) },
                        onPending = { navController.navigate(Routes.RECURRING) },
                    )
                )
            }
            composable(Routes.TRANSACTIONS) {
                TransactionsRoute(onEdit = { navController.navigate(Routes.editTransaction(it.type, it.id)) })
            }
            composable(Routes.REPORTS) { ReportsRoute() }
            composable(Routes.MORE) {
                MoreScreen(
                    onAccounts = { navController.navigate(Routes.ACCOUNTS) },
                    onCategories = { navController.navigate(Routes.CATEGORIES) },
                    onRecurring = { navController.navigate(Routes.RECURRING) },
                    onCurrencies = { navController.navigate(Routes.CURRENCIES) },
                    onSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }
            composable(Routes.CURRENCIES) {
                CurrenciesRoute(onBack = { navController.popBackStack() })
            }
            composable(Routes.SETTINGS) {
                SettingsRoute(
                    onBack = { navController.popBackStack() },
                    onAccounts = { navController.navigate(Routes.ACCOUNTS) },
                    onCategories = { navController.navigate(Routes.CATEGORIES) },
                    onCurrencies = { navController.navigate(Routes.CURRENCIES) },
                    onBackup = { navController.navigate(Routes.BACKUP) },
                )
            }
            composable(Routes.BACKUP) {
                BackupRoute(onBack = { navController.popBackStack() })
            }

            composable(Routes.ACCOUNTS) {
                AccountsRoute(
                    onBack = { navController.popBackStack() },
                    onOpen = { navController.navigate(Routes.accountDetail(it)) },
                    onNew = { navController.navigate(Routes.ACCOUNT_NEW) },
                )
            }
            composable(Routes.ACCOUNT_DETAIL, arguments = listOf(navArgument(Routes.ARG_ID) { type = NavType.LongType })) { entry ->
                val id = entry.arguments?.getLong(Routes.ARG_ID) ?: -1L
                AccountDetailRoute(
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate(Routes.accountEdit(id)) },
                    onTransaction = { navController.navigate(Routes.editTransaction(it.type, it.id)) },
                )
            }
            composable(
                Routes.ACCOUNT_EDIT,
                arguments = listOf(navArgument(Routes.ARG_ID) { type = NavType.LongType; defaultValue = -1L }),
            ) {
                AccountEditRoute(onDone = { navController.popBackStack() })
            }

            composable(Routes.CATEGORIES) {
                CategoriesRoute(
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate(Routes.categoryEdit(it)) },
                    onNew = { navController.navigate(Routes.categoryNew(it)) },
                )
            }
            composable(
                Routes.CATEGORY_EDIT,
                arguments = listOf(
                    navArgument(Routes.ARG_ID) { type = NavType.LongType; defaultValue = -1L },
                    navArgument(Routes.ARG_TYPE) { type = NavType.StringType; defaultValue = "EXPENSE" },
                ),
            ) {
                CategoryEditRoute(onDone = { navController.popBackStack() })
            }

            val back: () -> Unit = { navController.popBackStack() }
            composable(
                Routes.TRANSACTION_FORM,
                arguments = listOf(
                    navArgument(Routes.ARG_TYPE) { type = NavType.StringType },
                    navArgument(Routes.ARG_ID) { type = NavType.LongType; defaultValue = -1L },
                ),
            ) {
                TransactionFormRoute(onDone = back)
            }
            composable(
                Routes.EXCHANGE,
                arguments = listOf(navArgument(Routes.ARG_ID) { type = NavType.LongType; defaultValue = -1L }),
            ) { ExchangeRoute(onDone = back) }
            composable(Routes.RECURRING) {
                RecurringRoute(
                    onBack = back,
                    onEditRule = { navController.navigate(Routes.ruleEdit(it)) },
                    onNewRule = { navController.navigate(Routes.RULE_NEW) },
                )
            }
            composable(
                Routes.RULE_EDIT,
                arguments = listOf(navArgument(Routes.ARG_ID) { type = NavType.LongType; defaultValue = -1L }),
            ) { RuleEditRoute(onDone = back) }
        }
    }
}

/** Fő oldalak közti váltás: nem halmozza a back stacket, megőrzi az oldalak állapotát. */
private fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
