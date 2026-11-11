package com.example.cashapp.ui

import com.example.cashapp.ui.recurring.RuleEditViewModel
import com.example.cashapp.ui.recurring.RecurringViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.cashapp.CashApplication
import com.example.cashapp.di.AppContainer
import com.example.cashapp.domain.model.CategoryType
import com.example.cashapp.domain.model.TransactionType
import com.example.cashapp.ui.account.AccountDetailViewModel
import com.example.cashapp.ui.account.AccountEditViewModel
import com.example.cashapp.ui.account.AccountsViewModel
import com.example.cashapp.ui.category.CategoriesViewModel
import com.example.cashapp.ui.category.CategoryEditViewModel
import com.example.cashapp.ui.backup.BackupViewModel
import com.example.cashapp.ui.bankloan.BankLoanDetailViewModel
import com.example.cashapp.ui.bankloan.BankLoanEditViewModel
import com.example.cashapp.ui.bankloan.BankLoanPrefill
import com.example.cashapp.ui.bankloan.BankLoanPrepaymentViewModel
import com.example.cashapp.ui.bankloan.BankLoansViewModel
import com.example.cashapp.ui.currency.CurrenciesViewModel
import com.example.cashapp.ui.exchange.ExchangeViewModel
import com.example.cashapp.ui.home.HomeViewModel
import com.example.cashapp.ui.loan.LoanCalculatorViewModel
import com.example.cashapp.ui.loan.LoanDetailViewModel
import com.example.cashapp.ui.loan.LoanEditViewModel
import com.example.cashapp.ui.loan.LoanMovementViewModel
import com.example.cashapp.ui.loan.LoansViewModel
import com.example.cashapp.ui.navigation.Routes
import com.example.cashapp.ui.reports.ReportsViewModel
import com.example.cashapp.ui.settings.SettingsViewModel
import com.example.cashapp.ui.transaction.TransactionFormViewModel
import com.example.cashapp.ui.transactions.TransactionsViewModel

/** Minden ViewModel itt kapja meg a függőségeit. Új képernyő → új `initializer`. */
object AppViewModelProvider {
    val Factory = viewModelFactory {
        initializer {
            with(container()) {
                HomeViewModel(
                    settingsRepository, currencyRepository, accountRepository,
                    transactionRepository, recurringRepository,
                )
            }
        }
        initializer {
            val type = TransactionType.valueOf(checkNotNull(createSavedStateHandle().get<String>(Routes.ARG_TYPE)))
            val editId = createSavedStateHandle().get<Long>(Routes.ARG_ID)?.takeIf { it >= 0 }
            with(container()) {
                TransactionFormViewModel(
                    type, editId, accountRepository, categoryRepository, currencyRepository, transactionRepository,
                )
            }
        }
        initializer {
            with(container()) { CategoriesViewModel(categoryRepository, currencyRepository, transactionRepository) }
        }
        initializer {
            val args = createSavedStateHandle()
            val id = args.get<Long>(Routes.ARG_ID)?.takeIf { it >= 0 }
            val type = CategoryType.valueOf(args.get<String>(Routes.ARG_TYPE) ?: CategoryType.EXPENSE.name)
            with(container()) {
                CategoryEditViewModel(id, type, categoryRepository, currencyRepository, settingsRepository)
            }
        }
        initializer {
            val editId = createSavedStateHandle().get<Long>(Routes.ARG_ID)?.takeIf { it >= 0 }
            with(container()) {
                ExchangeViewModel(
                    editId, accountRepository, currencyRepository, exchangeRateRepository, transactionRepository,
                )
            }
        }
        initializer {
            with(container()) { AccountsViewModel(accountRepository, currencyRepository, settingsRepository) }
        }
        initializer {
            val id = checkNotNull(createSavedStateHandle().get<Long>(Routes.ARG_ID))
            with(container()) {
                AccountDetailViewModel(id, accountRepository, currencyRepository, transactionRepository, settingsRepository)
            }
        }
        initializer {
            val id = createSavedStateHandle().get<Long>(Routes.ARG_ID)?.takeIf { it >= 0 }
            with(container()) { AccountEditViewModel(id, accountRepository, currencyRepository, settingsRepository) }
        }
        initializer {
            with(container()) { CurrenciesViewModel(settingsRepository, currencyRepository, exchangeRateRepository) }
        }
        initializer {
            with(container()) { BackupViewModel(backupManager) }
        }
        initializer {
            with(container()) { SettingsViewModel(settingsRepository, currencyRepository, dataResetter) }
        }
        initializer {
            with(container()) {
                TransactionsViewModel(
                    transactionRepository, accountRepository, categoryRepository, currencyRepository, settingsRepository,
                )
            }
        }
        initializer {
            with(container()) { RecurringViewModel(recurringRepository, currencyRepository, settingsRepository) }
        }
        initializer {
            val id = createSavedStateHandle().get<Long>(Routes.ARG_ID)?.takeIf { it >= 0 }
            with(container()) {
                RuleEditViewModel(id, recurringRepository, accountRepository, categoryRepository, currencyRepository)
            }
        }
        initializer {
            with(container()) { LoanCalculatorViewModel(settingsRepository, currencyRepository) }
        }
        initializer {
            with(container()) { LoansViewModel(loanRepository, currencyRepository, settingsRepository) }
        }
        initializer {
            val id = createSavedStateHandle().get<Long>(Routes.ARG_ID)?.takeIf { it >= 0 }
            with(container()) { LoanEditViewModel(id, loanRepository, accountRepository, currencyRepository, settingsRepository) }
        }
        initializer {
            val id = checkNotNull(createSavedStateHandle().get<Long>(Routes.ARG_ID))
            with(container()) {
                LoanDetailViewModel(id, loanRepository, currencyRepository, settingsRepository)
            }
        }
        initializer {
            val args = createSavedStateHandle()
            val loanId = checkNotNull(args.get<Long>(Routes.ARG_LOAN))
            val increase = args.get<Boolean>(Routes.ARG_INCREASE) ?: true
            val editId = args.get<Long>(Routes.ARG_ID)?.takeIf { it >= 0 }
            with(container()) {
                LoanMovementViewModel(loanId, increase, editId, loanRepository, accountRepository, currencyRepository)
            }
        }
        initializer {
            with(container()) { BankLoansViewModel(bankLoanRepository, currencyRepository, settingsRepository) }
        }
        initializer {
            val args = createSavedStateHandle()
            val id = args.get<Long>(Routes.ARG_ID)?.takeIf { it >= 0 }
            val prefill = BankLoanPrefill(
                principalText = args.get<String>(Routes.ARG_PRINCIPAL)?.takeIf { it.isNotBlank() },
                thmPercent = args.get<String>(Routes.ARG_THM)?.toDoubleOrNull(),
                months = args.get<Int>(Routes.ARG_MONTHS)?.takeIf { it > 0 },
            ).takeIf { it.principalText != null || it.thmPercent != null || it.months != null }
            with(container()) {
                BankLoanEditViewModel(id, prefill, bankLoanRepository, accountRepository, categoryRepository, currencyRepository)
            }
        }
        initializer {
            val id = checkNotNull(createSavedStateHandle().get<Long>(Routes.ARG_ID))
            with(container()) { BankLoanDetailViewModel(id, bankLoanRepository, currencyRepository, settingsRepository) }
        }
        initializer {
            val id = checkNotNull(createSavedStateHandle().get<Long>(Routes.ARG_ID))
            with(container()) { BankLoanPrepaymentViewModel(id, bankLoanRepository, currencyRepository) }
        }
        initializer {
            with(container()) { ReportsViewModel(reportRepository, categoryRepository, currencyRepository, settingsRepository) }
        }
    }

    private fun CreationExtras.container(): AppContainer =
        (this[APPLICATION_KEY] as CashApplication).container
}
