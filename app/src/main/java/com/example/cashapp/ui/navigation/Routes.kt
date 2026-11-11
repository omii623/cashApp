package com.example.cashapp.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.cashapp.R
import com.example.cashapp.domain.model.CategoryType
import com.example.cashapp.domain.model.TransactionType

/** Minden képernyő útvonala egy helyen. */
object Routes {
    const val HOME = "home"
    const val TRANSACTIONS = "transactions"
    const val REPORTS = "reports"
    const val MORE = "more"

    const val ARG_TYPE = "type"
    const val ARG_ID = "id"

    /** Bevétel/kiadás űrlap; [ARG_TYPE] a [TransactionType] neve, [ARG_ID] = -1 → új tétel. */
    const val TRANSACTION_FORM = "transaction/{$ARG_TYPE}?$ARG_ID={$ARG_ID}"
    fun transactionForm(type: TransactionType) = "transaction/${type.name}"
    fun transactionEdit(type: TransactionType, id: Long) = "transaction/${type.name}?$ARG_ID=$id"

    const val CATEGORIES = "categories"

    /** Kategória szerkesztő; [ARG_ID] = -1 → új kategória ([ARG_TYPE] típussal). */
    const val CATEGORY_EDIT = "categories/edit?$ARG_ID={$ARG_ID}&$ARG_TYPE={$ARG_TYPE}"
    fun categoryEdit(id: Long) = "categories/edit?$ARG_ID=$id&$ARG_TYPE=${CategoryType.EXPENSE.name}"
    fun categoryNew(type: CategoryType) = "categories/edit?$ARG_ID=-1&$ARG_TYPE=${type.name}"

    const val ACCOUNTS = "accounts"
    const val ACCOUNT_DETAIL = "accounts/{$ARG_ID}"
    fun accountDetail(id: Long) = "accounts/$id"
    /** Számla szerkesztő; [ARG_ID] = -1 → új számla. */
    const val ACCOUNT_EDIT = "account-edit?$ARG_ID={$ARG_ID}"
    fun accountEdit(id: Long) = "account-edit?$ARG_ID=$id"
    const val ACCOUNT_NEW = "account-edit?$ARG_ID=-1"

    const val SETTINGS = "settings"
    const val CURRENCIES = "currencies"
    const val BACKUP = "backup"

    /** Váltás; [ARG_ID] = -1 (vagy hiányzik) → új váltás. */
    const val EXCHANGE = "exchange?$ARG_ID={$ARG_ID}"
    const val EXCHANGE_NEW = "exchange"
    fun exchangeEdit(id: Long) = "exchange?$ARG_ID=$id"

    /** Meglévő tétel szerkesztése a típusának megfelelő képernyőn. */
    fun editTransaction(type: TransactionType, id: Long) =
        if (type == TransactionType.EXCHANGE) exchangeEdit(id) else transactionEdit(type, id)

    /** Lista sorra koppintás: kölcsön mozgásánál a kölcsön oldala, különben a tétel szerkesztője. */
    fun openRow(row: com.example.cashapp.ui.components.TransactionRowUi) =
        row.loanId?.let { loanDetail(it) } ?: editTransaction(row.type, row.id)

    /** Továbbiak → Kölcsönök (magán / banki választó). */
    const val LOANS_HUB = "loans-hub"
    const val LOAN_CALCULATOR = "loan-calculator"
    const val LOANS = "loans"
    const val LOAN_DETAIL = "loans/{$ARG_ID}"
    fun loanDetail(id: Long) = "loans/$id"
    /** Kölcsön szerkesztő; [ARG_ID] = -1 → új kölcsön. */
    const val LOAN_EDIT = "loan-edit?$ARG_ID={$ARG_ID}"
    fun loanEdit(id: Long) = "loan-edit?$ARG_ID=$id"
    const val LOAN_NEW = "loan-edit?$ARG_ID=-1"

    const val ARG_LOAN = "loanId"
    const val ARG_INCREASE = "increase"
    /** Kölcsön mozgás űrlap; [ARG_INCREASE]: új kölcsön (true) vagy törlesztés; [ARG_ID] = -1 → új mozgás. */
    const val LOAN_MOVEMENT = "loan-movement/{$ARG_LOAN}?$ARG_INCREASE={$ARG_INCREASE}&$ARG_ID={$ARG_ID}"
    fun loanMovementNew(loanId: Long, increase: Boolean) = "loan-movement/$loanId?$ARG_INCREASE=$increase"
    fun loanMovementEdit(loanId: Long, id: Long) = "loan-movement/$loanId?$ARG_ID=$id"

    const val RECURRING = "recurring"
    /** Ismétlődő szabály szerkesztő; [ARG_ID] = -1 → új szabály. */
    const val RULE_EDIT = "recurring-rule?$ARG_ID={$ARG_ID}"
    fun ruleEdit(id: Long) = "recurring-rule?$ARG_ID=$id"
    const val RULE_NEW = "recurring-rule?$ARG_ID=-1"
}

/** Az alsó navigációs sáv elemei. */
enum class TopLevelDestination(
    val route: String,
    @StringRes val label: Int,
    val icon: ImageVector,
) {
    HOME(Routes.HOME, R.string.nav_home, Icons.Filled.Home),
    TRANSACTIONS(Routes.TRANSACTIONS, R.string.nav_transactions, Icons.AutoMirrored.Filled.ReceiptLong),
    REPORTS(Routes.REPORTS, R.string.nav_reports, Icons.Filled.BarChart),
    MORE(Routes.MORE, R.string.nav_more, Icons.Filled.MoreHoriz),
}
