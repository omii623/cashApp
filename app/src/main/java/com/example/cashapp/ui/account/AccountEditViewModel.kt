package com.example.cashapp.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cashapp.data.repository.AccountRepository
import com.example.cashapp.data.repository.AccountRepository.AccountDraft
import com.example.cashapp.data.repository.CurrencyRepository
import com.example.cashapp.data.settings.SettingsRepository
import com.example.cashapp.domain.model.CategoryIcon
import com.example.cashapp.domain.model.Currency
import com.example.cashapp.domain.money.AmountParser
import com.example.cashapp.ui.category.CategoryPalette
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AccountEditUiState(
    val isLoading: Boolean = true,
    val isNew: Boolean = true,
    val name: String = "",
    val nameError: Boolean = false,
    val currencyCode: String = "",
    val currencies: List<Currency> = emptyList(),
    /** Igaz, ha már van tétel a számlán – ilyenkor a pénznem nem váltható. */
    val currencyLocked: Boolean = false,
    val initialBalanceText: String = "",
    val initialBalanceError: Boolean = false,
    val color: Long = CategoryPalette.colors[5],
    val icon: String = "cash",
    val isSaving: Boolean = false,
    val isDone: Boolean = false,
)

class AccountEditViewModel(
    private val accountId: Long?,
    private val accountRepository: AccountRepository,
    private val currencyRepository: CurrencyRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AccountEditUiState(isNew = accountId == null))
    val uiState: StateFlow<AccountEditUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val currencies = currencyRepository.observeCurrencies().first()
        val account = accountId?.let { accountRepository.get(it) }
        if (account == null) {
            _state.update {
                it.copy(isLoading = false, currencies = currencies.values.toList(), currencyCode = settingsRepository.baseCurrency.first())
            }
            return
        }
        val decimals = currencies[account.currencyCode]?.decimals ?: 0
        _state.update {
            it.copy(
                isLoading = false,
                name = account.name,
                currencyCode = account.currencyCode,
                currencies = currencies.values.toList(),
                currencyLocked = accountRepository.isUsed(account.id),
                initialBalanceText = if (account.initialBalanceMinor == 0L) "" else
                    AmountParser.toEditText(account.initialBalanceMinor, decimals),
                color = account.color,
                icon = account.icon,
            )
        }
    }

    fun onNameChange(text: String) = _state.update { it.copy(name = text, nameError = false) }

    fun onCurrencySelect(code: String) = _state.update { if (it.currencyLocked) it else it.copy(currencyCode = code) }

    fun onInitialBalanceChange(text: String) = _state.update { it.copy(initialBalanceText = text, initialBalanceError = false) }

    fun onColorSelect(color: Long) = _state.update { it.copy(color = color) }

    fun onIconSelect(icon: CategoryIcon) = _state.update { it.copy(icon = icon.encode()) }

    fun save() {
        val s = _state.value
        if (s.isLoading || s.isSaving) return
        val name = s.name.trim()
        val decimals = s.currencies.firstOrNull { it.code == s.currencyCode }?.decimals ?: 0
        val initial = AmountParser.parseSignedAmount(s.initialBalanceText, decimals)
        if (name.isEmpty() || initial == null) {
            _state.update { it.copy(nameError = name.isEmpty(), initialBalanceError = initial == null) }
            return
        }
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            accountRepository.save(AccountDraft(accountId, name, s.currencyCode, initial, s.color, s.icon))
            _state.update { it.copy(isSaving = false, isDone = true) }
        }
    }

    fun delete() {
        val id = accountId ?: return
        viewModelScope.launch {
            accountRepository.delete(id)
            _state.update { it.copy(isDone = true) }
        }
    }
}
