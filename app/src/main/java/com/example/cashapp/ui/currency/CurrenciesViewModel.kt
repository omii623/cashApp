package com.example.cashapp.ui.currency

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cashapp.data.repository.CurrencyRepository
import com.example.cashapp.data.repository.CurrencyRepository.Companion.MAX_CURRENCIES
import com.example.cashapp.data.repository.ExchangeRateRepository
import com.example.cashapp.data.settings.SettingsRepository
import com.example.cashapp.domain.model.Currency
import com.example.cashapp.domain.money.AmountParser
import com.example.cashapp.domain.money.CurrencyConverter
import com.example.cashapp.domain.money.ExchangeMath
import com.example.cashapp.domain.money.MoneyFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Árfolyam az alap pénznemhez: „1 [leftCode] = [text] [rightCode]” (a drágább balra). */
data class RateLine(val code: String, val leftCode: String, val rightCode: String, val text: String?)

/** Az átváltó egy sora: az összeg ebben a pénznemben (null: nincs árfolyam). */
data class ConvertedLine(val currency: Currency, val text: String?)

data class CurrenciesUiState(
    val isLoading: Boolean = true,
    val baseCurrency: String = "",
    val currencies: List<Currency> = emptyList(),
    val canAdd: Boolean = false,
    val rates: List<RateLine> = emptyList(),
    val lastFetchedAt: Long? = null,
    val isFetching: Boolean = false,
    val fetchFailed: Boolean = false,
    // átváltó
    val amountText: String = "",
    val fromCode: String = "",
    val converted: List<ConvertedLine> = emptyList(),
    /** Egyszeri üzenet (snackbar). */
    val message: CurrencyMessage? = null,
)

enum class CurrencyMessage { ADDED, ADD_FAILED, REMOVED, IN_USE, PROTECTED }

/**
 * Pénznemek és árfolyamok: bővíthető pénznemlista (max. [MAX_CURRENCIES]),
 * aktuális (lekért) árfolyamok az alap pénznemhez, és átváltó.
 */
class CurrenciesViewModel(
    private val settingsRepository: SettingsRepository,
    private val currencyRepository: CurrencyRepository,
    private val exchangeRateRepository: ExchangeRateRepository,
) : ViewModel() {

    private data class Local(
        val amountText: String = "",
        val fromCode: String? = null,
        val isFetching: Boolean = false,
        val fetchFailed: Boolean = false,
        val message: CurrencyMessage? = null,
    )

    private val local = MutableStateFlow(Local())

    init {
        // Az oldal mindig az aktuális árfolyamot mutassa: megnyitáskor frissít.
        fetchRates()
    }

    val uiState: StateFlow<CurrenciesUiState> = combine(
        settingsRepository.baseCurrency,
        currencyRepository.observeCurrencies(),
        currencyRepository.observeConverter(),
        exchangeRateRepository.observeLastFetchedAt(),
        local,
    ) { base, currencyMap, converter, lastFetchedAt, l ->
        val currencies = currencyMap.values.toList()
        val from = currencyMap[l.fromCode] ?: currencyMap[base] ?: currencies.firstOrNull()
        CurrenciesUiState(
            isLoading = false,
            baseCurrency = base,
            currencies = currencies,
            canAdd = currencies.size < MAX_CURRENCIES,
            rates = rateLines(currencies, base, converter),
            lastFetchedAt = lastFetchedAt,
            isFetching = l.isFetching,
            fetchFailed = l.fetchFailed,
            amountText = l.amountText,
            fromCode = from?.code.orEmpty(),
            converted = from?.let { convertAll(l.amountText, it, currencies, converter) }.orEmpty(),
            message = l.message,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CurrenciesUiState())

    fun onAmountChange(text: String) = local.update { it.copy(amountText = text) }

    fun onFromCurrency(code: String) = local.update { it.copy(fromCode = code) }

    fun setBaseCurrency(code: String) {
        viewModelScope.launch { settingsRepository.setBaseCurrency(code) }
    }

    fun fetchRates() {
        if (local.value.isFetching) return
        local.update { it.copy(isFetching = true, fetchFailed = false) }
        viewModelScope.launch {
            val ok = runCatching { exchangeRateRepository.refresh() }.isSuccess
            local.update { it.copy(isFetching = false, fetchFailed = !ok) }
        }
    }

    fun addCurrency(code: String) {
        viewModelScope.launch {
            val ok = currencyRepository.addCurrency(code)
            local.update { it.copy(message = if (ok) CurrencyMessage.ADDED else CurrencyMessage.ADD_FAILED) }
            if (ok) fetchRates() // az új pénznem árfolyama is jöjjön le
        }
    }

    fun removeCurrency(code: String) {
        viewModelScope.launch {
            val result = currencyRepository.removeCurrency(code, settingsRepository.baseCurrency.first())
            local.update {
                it.copy(
                    message = when (result) {
                        CurrencyRepository.RemoveResult.REMOVED -> CurrencyMessage.REMOVED
                        CurrencyRepository.RemoveResult.IN_USE -> CurrencyMessage.IN_USE
                        CurrencyRepository.RemoveResult.PROTECTED -> CurrencyMessage.PROTECTED
                    },
                    fromCode = if (result == CurrencyRepository.RemoveResult.REMOVED && it.fromCode == code) null else it.fromCode,
                )
            }
        }
    }

    fun messageShown() = local.update { it.copy(message = null) }

    companion object {
        /** Minden nem alap pénznem árfolyama az alaphoz (pl. alap HUF: 1 EUR = 391 HUF). */
        internal fun rateLines(currencies: List<Currency>, base: String, converter: CurrencyConverter): List<RateLine> {
            val baseCur = currencies.firstOrNull { it.code == base } ?: return emptyList()
            return currencies.filter { it.code != base }.map { c ->
                val rate = converter.rate(c.code, base)
                val inverted = ExchangeMath.isDisplayInverted(rate, c.decimals, baseCur.decimals)
                RateLine(
                    code = c.code,
                    leftCode = if (inverted) base else c.code,
                    rightCode = if (inverted) c.code else base,
                    text = rate?.let { AmountParser.rateToEditText(ExchangeMath.toDisplay(it, inverted)) },
                )
            }
        }

        /** A beírt összeg minden pénznemben (a forrás pénznem is benne van). Üres / érvénytelen → üres lista. */
        internal fun convertAll(
            text: String,
            from: Currency,
            currencies: List<Currency>,
            converter: CurrencyConverter,
        ): List<ConvertedLine> {
            val amount = AmountParser.parseAmount(text, from.decimals) ?: return emptyList()
            return currencies.filter { it.code != from.code }.map { c ->
                ConvertedLine(c, converter.convert(amount, from.code, c.code)?.let { MoneyFormatter.format(it, c) })
            }
        }
    }
}
