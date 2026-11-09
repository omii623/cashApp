package com.example.cashapp.ui.loan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cashapp.data.repository.CurrencyRepository
import com.example.cashapp.data.repository.LoanRepository
import com.example.cashapp.data.settings.SettingsRepository
import com.example.cashapp.domain.model.Currency
import com.example.cashapp.domain.model.TransactionType
import com.example.cashapp.domain.model.LoanDirection
import com.example.cashapp.domain.loan.LoanMath
import com.example.cashapp.domain.model.LoanOpeningPart
import com.example.cashapp.ui.components.TransactionRowMapper
import com.example.cashapp.ui.components.TransactionRowUi
import com.example.cashapp.ui.home.HomeViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * A rögzítés előtti (dátum nélküli) tétel a mozgások listájában: a kölcsön összege
 * ([increase] = true) vagy az abból már visszafizetett rész.
 */
data class OpeningRowUi(
    val increase: Boolean,
    val amountText: String,
    /** Ha könyvelve van (beleszámít a bevétel/kiadásba): a tétel dátuma és számlája; különben null. */
    val bookedDate: java.time.LocalDate? = null,
    val bookedAccount: String? = null,
)

data class LoanDetailUiState(
    val isLoading: Boolean = true,
    /** A kölcsönt közben törölték. */
    val notFound: Boolean = false,
    val loan: LoanItemUi? = null,
    val currency: Currency? = null,
    /** A rögzítés előtti kölcsön összege és abból a már visszafizetett rész (null: nincs). */
    val opening: String? = null,
    val openingRepaid: String? = null,
    val note: String = "",
    val movements: List<TransactionRowUi> = emptyList(),
    /** A lista alján: már visszafizetett rész, alatta a kölcsön összege (a legrégebbi legalul). */
    val openingRows: List<OpeningRowUi> = emptyList(),
)

class LoanDetailViewModel(
    private val loanId: Long,
    private val loanRepository: LoanRepository,
    currencyRepository: CurrencyRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val uiState: StateFlow<LoanDetailUiState> = combine(
        loanRepository.observe(loanId),
        loanRepository.observeMovements(loanId),
        loanRepository.observeOpenings(loanId),
        currencyRepository.observeCurrencies(),
        settingsRepository.hideAmounts,
    ) { loan, items, openings, currencies, hidden ->
        if (loan == null) return@combine LoanDetailUiState(isLoading = false, notFound = true)
        val l = loan.loan
        LoanDetailUiState(
            isLoading = false,
            loan = loan.toItemUi(currencies, hidden),
            currency = currencies[l.currencyCode],
            opening = l.openingAmountMinor.takeIf { it > 0 }?.let {
                if (hidden) HomeViewModel.MASK else format(it, l.currencyCode, currencies)
            },
            openingRepaid = l.openingRepaidMinor.takeIf { it > 0 }?.let {
                if (hidden) HomeViewModel.MASK else format(it, l.currencyCode, currencies)
            },
            note = l.note,
            movements = items.map {
                // Előjel a pénz iránya szerint, mint mindenhol máshol: bevétel „+”, kiadás „−”.
                val row = TransactionRowMapper.map(it, currencies)
                if (hidden) row.copy(amountText = HomeViewModel.MASK) else row
            },
            openingRows = listOfNotNull(
                l.openingRepaidMinor.takeIf { it > 0 }?.let {
                    val booked = openings.firstOrNull { o -> o.loanOpening == LoanOpeningPart.REPAID }
                    OpeningRowUi(false, if (hidden) HomeViewModel.MASK else signed(it, l.direction, increase = false, l.currencyCode, currencies), booked?.date, booked?.accountName)
                },
                l.openingAmountMinor.takeIf { it > 0 }?.let {
                    val booked = openings.firstOrNull { o -> o.loanOpening == LoanOpeningPart.AMOUNT }
                    OpeningRowUi(true, if (hidden) HomeViewModel.MASK else signed(it, l.direction, increase = true, l.currencyCode, currencies), booked?.date, booked?.accountName)
                },
            ),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LoanDetailUiState())

    /** Lezárás (akkor is, ha maradt hátralék) / újranyitás. */
    fun close() { viewModelScope.launch { loanRepository.close(loanId) } }

    fun reopen() { viewModelScope.launch { loanRepository.reopen(loanId) } }

    /** Rögzítés előtti sor összege a pénz iránya szerinti előjellel (bevétel „+”, kiadás „−”), mint a mozgásoknál. */
    private fun signed(minor: Long, direction: LoanDirection, increase: Boolean, code: String, currencies: Map<String, Currency>): String {
        val income = LoanMath.transactionType(direction, increase) == TransactionType.INCOME
        return format(if (income) minor else -minor, code, currencies, plus = income)
    }
}
