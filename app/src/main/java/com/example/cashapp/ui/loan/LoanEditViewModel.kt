package com.example.cashapp.ui.loan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cashapp.data.local.entity.AccountEntity
import com.example.cashapp.data.local.entity.LoanEntity
import com.example.cashapp.data.repository.AccountRepository
import com.example.cashapp.data.repository.CurrencyRepository
import com.example.cashapp.data.repository.LoanRepository
import com.example.cashapp.data.repository.LoanRepository.OpeningBooking
import com.example.cashapp.data.settings.SettingsRepository
import com.example.cashapp.domain.model.CategoryIcon
import com.example.cashapp.domain.model.Currency
import com.example.cashapp.domain.model.LoanDirection
import com.example.cashapp.domain.model.LoanOpeningPart
import com.example.cashapp.domain.money.AmountParser
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoanEditUiState(
    val isLoading: Boolean = true,
    val isNew: Boolean = true,
    val personName: String = "",
    val nameError: Boolean = false,
    val direction: LoanDirection = LoanDirection.LENT,
    val currencyCode: String = "",
    val currencies: List<Currency> = emptyList(),
    /** Már van mozgás → irány és pénznem nem módosítható. */
    val locked: Boolean = false,
    /** A rögzítés előtt létrejött kölcsön összege (üres → 0). */
    val openingText: String = "",
    val openingError: Boolean = false,
    /** Ebből már visszafizetve (üres → 0). */
    val repaidText: String = "",
    val repaidError: Boolean = false,
    /** Könyvelődjön-e a kölcsön összege / a már visszafizetett rész valódi tételként (bevétel/kiadás + számla). */
    val bookAmount: Boolean = false,
    val bookRepaid: Boolean = false,
    /** Az összes aktív számla; ebből a kölcsön pénznemével egyezők választhatók. */
    val allAccounts: List<AccountEntity> = emptyList(),
    val accountId: Long? = null,
    /** Könyvelés be van kapcsolva, de nincs választható számla. */
    val accountError: Boolean = false,
    val startDate: LocalDate = LocalDate.now(),
    val note: String = "",
    /** Jelölés, mint a kategóriáknál; null → alapértelmezett (az irány színe / a név első betűje). */
    val color: Long? = null,
    val icon: String? = null,
    val isSaving: Boolean = false,
    val saveFailed: Boolean = false,
    /** Mentés után az új / meglévő kölcsön azonosítója; törlés után -1. */
    val doneId: Long? = null,
) {
    val accounts: List<AccountEntity> get() = allAccounts.filter { it.currencyCode == currencyCode }
    val account: AccountEntity? get() = accounts.firstOrNull { it.id == accountId }
    val booksAnything: Boolean get() = bookAmount || bookRepaid
}

class LoanEditViewModel(
    private val loanId: Long?,
    private val loanRepository: LoanRepository,
    private val accountRepository: AccountRepository,
    private val currencyRepository: CurrencyRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LoanEditUiState(isNew = loanId == null))
    val uiState: StateFlow<LoanEditUiState> = _state.asStateFlow()

    private var createdAt: Long = System.currentTimeMillis()

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val currencies = currencyRepository.observeCurrencies().first()
        val allAccounts = accountRepository.observeActive().first()
        val loan = loanId?.let { loanRepository.get(it) }
        if (loan == null) {
            val base = settingsRepository.baseCurrency.first()
            _state.update {
                it.copy(
                    isLoading = false,
                    currencies = currencies.values.toList(),
                    currencyCode = base,
                    allAccounts = allAccounts,
                    accountId = allAccounts.firstOrNull { a -> a.currencyCode == base }?.id,
                )
            }
            return
        }
        val openings = loanRepository.getOpenings(loan.id)
        createdAt = loan.createdAt
        val decimals = currencies[loan.currencyCode]?.decimals ?: 0
        _state.update {
            it.copy(
                isLoading = false,
                personName = loan.personName,
                direction = loan.direction,
                currencyCode = loan.currencyCode,
                currencies = currencies.values.toList(),
                locked = loanRepository.hasMovements(loan.id),
                openingText = if (loan.openingAmountMinor == 0L) "" else AmountParser.toEditText(loan.openingAmountMinor, decimals),
                repaidText = if (loan.openingRepaidMinor == 0L) "" else AmountParser.toEditText(loan.openingRepaidMinor, decimals),
                startDate = loan.startDate,
                note = loan.note,
                color = loan.color,
                icon = loan.icon,
                bookAmount = openings.any { o -> o.loanOpening == LoanOpeningPart.AMOUNT },
                bookRepaid = openings.any { o -> o.loanOpening == LoanOpeningPart.REPAID },
                allAccounts = allAccounts,
                accountId = openings.firstOrNull()?.accountId
                    ?: allAccounts.firstOrNull { a -> a.currencyCode == loan.currencyCode }?.id,
            )
        }
    }

    fun onName(text: String) = _state.update { it.copy(personName = text, nameError = false) }
    fun onDirection(direction: LoanDirection) = _state.update { if (it.locked) it else it.copy(direction = direction) }
    fun onCurrency(code: String) = _state.update {
        if (it.locked) it
        else it.copy(currencyCode = code, accountId = it.allAccounts.firstOrNull { a -> a.currencyCode == code }?.id, accountError = false)
    }
    fun onBookAmount(on: Boolean) = _state.update { it.copy(bookAmount = on, accountError = false) }
    fun onBookRepaid(on: Boolean) = _state.update { it.copy(bookRepaid = on, accountError = false) }
    fun onAccount(id: Long) = _state.update { it.copy(accountId = id, accountError = false) }
    fun onOpening(text: String) = _state.update { it.copy(openingText = text, openingError = false, repaidError = false) }
    fun onRepaid(text: String) = _state.update { it.copy(repaidText = text, repaidError = false) }
    fun onStartDate(date: LocalDate) = _state.update { it.copy(startDate = date) }
    fun onNote(text: String) = _state.update { it.copy(note = text) }
    fun onColor(color: Long) = _state.update { it.copy(color = color) }
    fun onIcon(icon: CategoryIcon) = _state.update { it.copy(icon = icon.encode()) }
    fun dismissError() = _state.update { it.copy(saveFailed = false) }

    fun save() {
        val s = _state.value
        if (s.isLoading || s.isSaving) return
        val name = s.personName.trim()
        val decimals = s.currencies.firstOrNull { it.code == s.currencyCode }?.decimals ?: 0
        // Üres → 0; különben nem negatív összeg. A visszafizetett rész nem lehet több a kölcsönnél.
        fun parse(text: String) = if (text.isBlank()) 0L else AmountParser.parseSignedAmount(text, decimals)?.takeIf { it >= 0 }
        val opening = parse(s.openingText)
        val repaid = parse(s.repaidText)?.takeIf { opening == null || it <= opening }
        // Könyveléshez kell egy, a kölcsön pénznemével egyező számla.
        val needsAccount = (s.bookAmount && (opening ?: 0) > 0) || (s.bookRepaid && (repaid ?: 0) > 0)
        val accountMissing = needsAccount && s.account == null
        if (name.isEmpty() || opening == null || repaid == null || accountMissing) {
            _state.update {
                it.copy(nameError = name.isEmpty(), openingError = opening == null, repaidError = repaid == null, accountError = accountMissing)
            }
            return
        }
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val result = runCatching {
                loanRepository.save(
                    LoanEntity(
                        id = loanId ?: 0,
                        personName = name,
                        direction = s.direction,
                        currencyCode = s.currencyCode,
                        openingAmountMinor = opening,
                        openingRepaidMinor = repaid,
                        startDate = s.startDate,
                        note = s.note.trim(),
                        createdAt = createdAt,
                        color = s.color,
                        icon = s.icon,
                    ),
                    OpeningBooking(bookAmount = s.bookAmount, bookRepaid = s.bookRepaid, accountId = s.account?.id),
                )
            }
            _state.update { st ->
                result.fold(
                    onSuccess = { st.copy(isSaving = false, doneId = it) },
                    onFailure = { st.copy(isSaving = false, saveFailed = true) },
                )
            }
        }
    }

    fun delete() {
        val id = loanId ?: return
        viewModelScope.launch {
            loanRepository.delete(id)
            _state.update { it.copy(doneId = -1) }
        }
    }
}
