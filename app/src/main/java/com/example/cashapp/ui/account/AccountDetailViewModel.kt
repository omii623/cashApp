package com.example.cashapp.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cashapp.data.repository.AccountRepository
import com.example.cashapp.data.repository.CurrencyRepository
import com.example.cashapp.data.repository.TransactionRepository
import com.example.cashapp.data.settings.SettingsRepository
import com.example.cashapp.domain.money.MoneyFormatter
import com.example.cashapp.ui.components.TransactionRowMapper
import com.example.cashapp.ui.components.TransactionRowUi
import com.example.cashapp.ui.home.HomeViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class AccountDetailUiState(
    val isLoading: Boolean = true,
    /** A számlát időközben törölték. */
    val notFound: Boolean = false,
    val account: AccountItemUi? = null,
    val isArchived: Boolean = false,
    val initialBalance: String = "",
    val transactions: List<TransactionRowUi> = emptyList(),
)

/** Egy számla: egyenleg + a saját tételei (a bejövő váltásokkal együtt). */
class AccountDetailViewModel(
    private val accountId: Long,
    accountRepository: AccountRepository,
    currencyRepository: CurrencyRepository,
    transactionRepository: TransactionRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val uiState: StateFlow<AccountDetailUiState> = combine(
        accountRepository.observeWithBalance(accountId),
        transactionRepository.observeForAccount(accountId),
        currencyRepository.observeCurrencies(),
        settingsRepository.hideAmounts,
    ) { account, items, currencies, hidden ->
        if (account == null) return@combine AccountDetailUiState(isLoading = false, notFound = true)
        val currency = currencies[account.account.currencyCode]
        AccountDetailUiState(
            isLoading = false,
            account = account.toItemUi(currencies, hidden),
            isArchived = account.account.isArchived,
            initialBalance = when {
                hidden -> HomeViewModel.MASK
                currency != null -> MoneyFormatter.format(account.account.initialBalanceMinor, currency)
                else -> ""
            },
            transactions = items.map {
                val row = TransactionRowMapper.map(it, currencies, perspectiveAccountId = accountId)
                if (hidden) row.copy(amountText = HomeViewModel.MASK) else row
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountDetailUiState())
}
