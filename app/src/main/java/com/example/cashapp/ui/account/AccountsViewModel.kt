package com.example.cashapp.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cashapp.data.local.model.AccountWithBalance
import com.example.cashapp.data.repository.AccountRepository
import com.example.cashapp.data.repository.CurrencyRepository
import com.example.cashapp.data.settings.SettingsRepository
import com.example.cashapp.domain.model.Currency
import com.example.cashapp.domain.money.MoneyFormatter
import com.example.cashapp.ui.home.HomeViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AccountsUiState(
    val isLoading: Boolean = true,
    val active: List<AccountItemUi> = emptyList(),
    val archived: List<AccountItemUi> = emptyList(),
)

data class AccountItemUi(
    val id: Long,
    val name: String,
    val icon: String,
    val color: Long,
    val currencyCode: String,
    val balance: String,
    val isNegative: Boolean,
)

/** Számla → megformázott listaelem (a főoldali csillagozást is figyelembe veszi). */
internal fun AccountWithBalance.toItemUi(currencies: Map<String, Currency>, hidden: Boolean): AccountItemUi {
    val currency = currencies[account.currencyCode]
    return AccountItemUi(
        id = account.id,
        name = account.name,
        icon = account.icon,
        color = account.color,
        currencyCode = account.currencyCode,
        balance = when {
            hidden -> HomeViewModel.MASK
            currency != null -> MoneyFormatter.format(balanceMinor, currency)
            else -> "$balanceMinor ${account.currencyCode}"
        },
        isNegative = !hidden && balanceMinor < 0,
    )
}

class AccountsViewModel(
    private val accountRepository: AccountRepository,
    currencyRepository: CurrencyRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val uiState: StateFlow<AccountsUiState> = combine(
        accountRepository.observeAllWithBalance(),
        currencyRepository.observeCurrencies(),
        settingsRepository.hideAmounts,
    ) { accounts, currencies, hidden ->
        val (archived, active) = accounts.partition { it.account.isArchived }
        AccountsUiState(
            isLoading = false,
            active = active.map { it.toItemUi(currencies, hidden) },
            archived = archived.map { it.toItemUi(currencies, hidden) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountsUiState())

    /** Aktív számla mozgatása egy hellyel feljebb (-1) / lejjebb (+1). */
    fun move(id: Long, delta: Int) {
        val ids = uiState.value.active.map { it.id }.toMutableList()
        val from = ids.indexOf(id)
        val to = from + delta
        if (from < 0 || to !in ids.indices) return
        ids.add(to, ids.removeAt(from))
        viewModelScope.launch { accountRepository.reorder(ids) }
    }

    fun restore(id: Long) {
        viewModelScope.launch { accountRepository.setArchived(id, false) }
    }
}
