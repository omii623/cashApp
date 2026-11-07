package com.example.cashapp.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cashapp.data.DataResetter
import com.example.cashapp.data.repository.CurrencyRepository
import com.example.cashapp.data.settings.AppLanguage
import com.example.cashapp.data.settings.LanguageManager
import com.example.cashapp.data.settings.SettingsRepository
import com.example.cashapp.domain.model.Currency
import com.example.cashapp.domain.model.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val isLoading: Boolean = true,
    val language: AppLanguage = AppLanguage.SYSTEM,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val baseCurrency: String = "",
    val currencies: List<Currency> = emptyList(),
    val hideAmounts: Boolean = false,
    val recurringNotifications: Boolean = true,
    val isResetting: Boolean = false,
    /** Egyszeri jelzés: a törlés lefutott (snackbar). */
    val resetDone: Boolean = false,
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    currencyRepository: CurrencyRepository,
    private val dataResetter: DataResetter,
) : ViewModel() {

    /** A nyelvet az Android tárolja, nem Flow – ezért helyben tartjuk az aktuálisat. */
    private val language = MutableStateFlow(LanguageManager.current())
    private val reset = MutableStateFlow(ResetState())

    private data class ResetState(val running: Boolean = false, val done: Boolean = false)

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.themeMode,
        settingsRepository.baseCurrency,
        settingsRepository.hideAmounts,
        currencyRepository.observeCurrencies(),
        combine(language, reset, settingsRepository.recurringNotifications) { l, r, n -> Triple(l, r, n) },
    ) { theme, base, hide, currencies, (lang, resetState, notify) ->
        SettingsUiState(
            isLoading = false,
            language = lang,
            themeMode = theme,
            baseCurrency = base,
            currencies = currencies.values.toList(),
            hideAmounts = hide,
            recurringNotifications = notify,
            isResetting = resetState.running,
            resetDone = resetState.done,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    /** Nyelvváltás: az Activity újraindul, a szövegek az új nyelven jelennek meg. */
    fun setLanguage(lang: AppLanguage) {
        language.value = lang
        LanguageManager.set(lang)
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setBaseCurrency(code: String) {
        viewModelScope.launch { settingsRepository.setBaseCurrency(code) }
    }

    fun setHideAmounts(hide: Boolean) {
        viewModelScope.launch { settingsRepository.setHideAmounts(hide) }
    }

    fun setRecurringNotifications(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setRecurringNotifications(enabled) }
    }

    fun resetAllData() {
        if (reset.value.running) return
        reset.value = ResetState(running = true)
        viewModelScope.launch {
            dataResetter.resetAll()
            reset.value = ResetState(done = true)
        }
    }

    fun resetMessageShown() = reset.update { it.copy(done = false) }
}
