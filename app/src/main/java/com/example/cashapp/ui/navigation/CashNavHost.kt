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
import com.example.cashapp.ui.bankloan.BankLoanDetailRoute
import com.example.cashapp.ui.bankloan.BankLoanEditRoute
import com.example.cashapp.ui.bankloan.BankLoanPrepaymentRoute
import com.example.cashapp.ui.bankloan.BankLoansRoute
import com.example.cashapp.ui.loan.LoanDetailRoute
import com.example.cashapp.ui.loan.LoanCalculatorRoute
import com.example.cashapp.ui.loan.LoanEditRoute
import com.example.cashapp.ui.loan.LoanHubScreen
import com.example.cashapp.ui.loan.LoanMovementRoute
import com.example.cashapp.ui.loan.LoansRoute
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
                        onTransaction = { navController.navigate(Routes.openRow(it)) },
                        onPending = { navController.navigate(Routes.RECURRING) },
                    )
                )
            }
            composable(Routes.TRANSACTIONS) {
                TransactionsRoute(onEdit = { navController.navigate(Routes.openRow(it)) })
            }
            composable(Routes.REPORTS) { ReportsRoute() }
            composable(Routes.MORE) {
                MoreScreen(
                    onAccounts = { navController.navigate(Routes.ACCOUNTS) },
                    onCategories = { navController.navigate(Routes.CATEGORIES) },
                    onRecurring = { navController.navigate(Routes.RECURRING) },
                    onLoans = { navController.navigate(Routes.LOANS_HUB) },
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
                    onTransaction = { navController.navigate(Routes.openRow(it)) },
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
                    onOpenBankLoan = { navController.navigate(Routes.bankLoanDetail(it)) },
                )
            }
            composable(
                Routes.RULE_EDIT,
                arguments = listOf(navArgument(Routes.ARG_ID) { type = NavType.LongType; defaultValue = -1L }),
            ) { RuleEditRoute(onDone = back) }

            composable(Routes.LOANS_HUB) {
                LoanHubScreen(
                    onBack = back,
                    onPrivateLoans = { navController.navigate(Routes.LOANS) },
                    onBankLoans = { navController.navigate(Routes.BANK_LOANS) },
                    onCalculator = { navController.navigate(Routes.LOAN_CALCULATOR) },
                )
            }
            composable(Routes.LOAN_CALCULATOR) {
                LoanCalculatorRoute(
                    onBack = back,
                    onSaveAsLoan = { principalText, thm, months ->
                        navController.navigate(Routes.bankLoanFromCalculator(principalText, thm, months))
                    },
                )
            }
            composable(Routes.BANK_LOANS) {
                BankLoansRoute(
                    onBack = back,
                    onOpen = { navController.navigate(Routes.bankLoanDetail(it)) },
                    onNew = { navController.navigate(Routes.BANK_LOAN_NEW) },
                )
            }
            composable(Routes.BANK_LOAN_DETAIL, arguments = listOf(navArgument(Routes.ARG_ID) { type = NavType.LongType })) { entry ->
                val id = entry.arguments?.getLong(Routes.ARG_ID) ?: -1L
                BankLoanDetailRoute(
                    onBack = back,
                    onEdit = { navController.navigate(Routes.bankLoanEdit(id)) },
                    onPrepay = { navController.navigate(Routes.bankLoanPrepay(id)) },
                )
            }
            composable(
                Routes.BANK_LOAN_EDIT,
                arguments = listOf(
                    navArgument(Routes.ARG_ID) { type = NavType.LongType; defaultValue = -1L },
                    navArgument(Routes.ARG_PRINCIPAL) { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument(Routes.ARG_THM) { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument(Routes.ARG_MONTHS) { type = NavType.IntType; defaultValue = -1 },
                ),
            ) { entry ->
                val isNew = (entry.arguments?.getLong(Routes.ARG_ID) ?: -1L) < 0
                BankLoanEditRoute(onDone = { savedId ->
                    // Új hitel mentése után a részletek oldal nyílik (a szerkesztő helyén); egyébként vissza.
                    if (savedId == null || !isNew) navController.popBackStack()
                    else navController.navigate(Routes.bankLoanDetail(savedId)) { popUpTo(Routes.BANK_LOAN_EDIT) { inclusive = true } }
                })
            }
            composable(
                Routes.BANK_LOAN_PREPAY,
                arguments = listOf(navArgument(Routes.ARG_ID) { type = NavType.LongType }),
            ) { BankLoanPrepaymentRoute(onDone = back) }
            composable(Routes.LOANS) {
                LoansRoute(
                    onBack = back,
                    onOpen = { navController.navigate(Routes.loanDetail(it)) },
                    onNew = { navController.navigate(Routes.LOAN_NEW) },
                )
            }
            composable(Routes.LOAN_DETAIL, arguments = listOf(navArgument(Routes.ARG_ID) { type = NavType.LongType })) { entry ->
                val id = entry.arguments?.getLong(Routes.ARG_ID) ?: -1L
                LoanDetailRoute(
                    onBack = back,
                    onEdit = { navController.navigate(Routes.loanEdit(id)) },
                    onNewMovement = { increase -> navController.navigate(Routes.loanMovementNew(id, increase)) },
                    onMovement = { txId -> navController.navigate(Routes.loanMovementEdit(id, txId)) },
                )
            }
            composable(
                Routes.LOAN_MOVEMENT,
                arguments = listOf(
                    navArgument(Routes.ARG_LOAN) { type = NavType.LongType },
                    navArgument(Routes.ARG_INCREASE) { type = NavType.BoolType; defaultValue = true },
                    navArgument(Routes.ARG_ID) { type = NavType.LongType; defaultValue = -1L },
                ),
            ) { LoanMovementRoute(onDone = back) }
            composable(
                Routes.LOAN_EDIT,
                arguments = listOf(navArgument(Routes.ARG_ID) { type = NavType.LongType; defaultValue = -1L }),
            ) {
                LoanEditRoute(onDone = { newId ->
                    if (newId == null) navController.popBackStack()
                    else navController.navigate(Routes.loanDetail(newId)) { popUpTo(Routes.LOAN_EDIT) { inclusive = true } }
                })
            }
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
