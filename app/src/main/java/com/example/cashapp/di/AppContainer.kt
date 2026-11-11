package com.example.cashapp.di

import android.content.Context
import com.example.cashapp.data.DataResetter
import com.example.cashapp.data.backup.BackupManager
import com.example.cashapp.data.local.CashDatabase
import com.example.cashapp.data.local.seed.DatabaseSeeder
import com.example.cashapp.data.repository.AccountRepository
import com.example.cashapp.data.repository.CategoryRepository
import com.example.cashapp.data.repository.ExchangeRateRepository
import com.example.cashapp.data.repository.CurrencyRepository
import com.example.cashapp.data.repository.BankLoanRepository
import com.example.cashapp.data.repository.LoanRepository
import com.example.cashapp.data.repository.RecurringRepository
import com.example.cashapp.data.repository.ReportRepository
import com.example.cashapp.data.repository.TransactionRepository
import com.example.cashapp.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Kézi függőség-kezelés: egy helyen jön létre minden singleton.
 * Új repository / service felvétele: itt egy `val`, és a ViewModel factory-ban használható.
 */
class AppContainer(context: Context) {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val database: CashDatabase = CashDatabase.build(context)

    val settingsRepository = SettingsRepository(context)
    val currencyRepository = CurrencyRepository(database.currencyDao(), database.exchangeRateDao())
    val accountRepository = AccountRepository(database)
    val categoryRepository = CategoryRepository(database)
    val exchangeRateRepository = ExchangeRateRepository(database.exchangeRateDao(), database.currencyDao())
    val transactionRepository = TransactionRepository(database.transactionDao())
    val recurringRepository = RecurringRepository(database)
    val reportRepository = ReportRepository(database.transactionDao())
    val loanRepository = LoanRepository(database)
    val bankLoanRepository = BankLoanRepository(database)
    val dataResetter = DataResetter(database, settingsRepository, exchangeRateRepository)
    val backupManager = BackupManager(database, settingsRepository, recurringRepository, context.contentResolver)

    init {
        appScope.launch {
            DatabaseSeeder(database).seedIfEmpty()
            // Indításkor is pótoljuk az esedékes ismétlődő tételeket (a napi háttérfeladat mellett).
            recurringRepository.generateDue()
            exchangeRateRepository.refreshIfStale()
        }
    }
}
