package com.example.cashapp.ui.bankloan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cashapp.data.repository.BankLoanOverview
import com.example.cashapp.data.repository.BankLoanRepository
import com.example.cashapp.data.repository.CurrencyRepository
import com.example.cashapp.data.settings.SettingsRepository
import com.example.cashapp.domain.model.Currency
import com.example.cashapp.ui.home.HomeViewModel
import com.example.cashapp.ui.loan.format
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class BankLoansUiState(
    val isLoading: Boolean = true,
    val open: List<BankLoanItemUi> = emptyList(),
    val done: List<BankLoanItemUi> = emptyList(),
    /** Pénznemenkénti összeg, pl. „4 812 340 Ft · 1 200,00 €” (üres: nincs futó hitel). */
    val totalOutstanding: String = "",
    /** A futó hitelek következő részleteinek összege pénznemenként. */
    val monthlyBurden: String = "",
)

class BankLoansViewModel(
    bankLoanRepository: BankLoanRepository,
    currencyRepository: CurrencyRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val uiState: StateFlow<BankLoansUiState> = combine(
        bankLoanRepository.observeAll(),
        currencyRepository.observeCurrencies(),
        settingsRepository.hideAmounts,
    ) { loans, currencies, hidden ->
        val items = loans.map { it.toItemUi(currencies, hidden) }
        val running = loans.filter { !it.isClosed && !it.isPaidOff }
        BankLoansUiState(
            isLoading = false,
            open = items.filterNot { it.isDone },
            done = items.filter { it.isDone },
            totalOutstanding = sum(running, currencies, hidden) { it.outstandingMinor },
            monthlyBurden = sum(running, currencies, hidden) { it.nextInstallment?.paymentMinor ?: 0 },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BankLoansUiState())

    private fun sum(
        loans: List<BankLoanOverview>,
        currencies: Map<String, Currency>,
        hidden: Boolean,
        value: (BankLoanOverview) -> Long,
    ): String {
        if (loans.isEmpty()) return ""
        if (hidden) return HomeViewModel.MASK
        return loans.groupBy { it.loan.currencyCode }
            .mapValues { (_, list) -> list.sumOf(value) }
            .entries.joinToString(" · ") { (code, minor) -> format(minor, code, currencies) }
    }
}
