package com.example.cashapp.ui.loan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cashapp.data.local.model.LoanWithBalance
import com.example.cashapp.data.repository.CurrencyRepository
import com.example.cashapp.data.repository.LoanRepository
import com.example.cashapp.data.settings.SettingsRepository
import com.example.cashapp.domain.model.Currency
import com.example.cashapp.domain.model.LoanDirection
import com.example.cashapp.domain.money.MoneyFormatter
import com.example.cashapp.ui.home.HomeViewModel
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Egy kölcsön a listában / a részletek fejlécében, megformázva. */
data class LoanItemUi(
    val id: Long,
    val personName: String,
    val direction: LoanDirection,
    val outstanding: String,
    /** Volt már mozgás (vagy nyitó összeg), és a hátralék 0. Új, üres kölcsön nem „rendezett”. */
    val isSettled: Boolean,
    /** Van mit törleszteni (hátralék > 0, és nincs lezárva). */
    val canRepay: Boolean,
    val lastMovementDate: LocalDate?,
    /** Lezárás dátuma (null: nincs lezárva). Lezárt kölcsönre nem rögzíthető mozgás. */
    val closedDate: LocalDate? = null,
    /** Választott jelölés (null: az irány színe / a név első betűje). */
    val color: Long? = null,
    val icon: String? = null,
) {
    val isClosed: Boolean get() = closedDate != null
    /** A „Rendezett” szekcióba kerül: visszafizetve vagy lezárva. */
    val isDone: Boolean get() = isSettled || isClosed
}

data class LoansUiState(
    val isLoading: Boolean = true,
    val open: List<LoanItemUi> = emptyList(),
    val settled: List<LoanItemUi> = emptyList(),
    /** Pénznemenkénti összeg, pl. „50 000 Ft · 20,00 €” (üres: nincs ilyen). */
    val totalBorrowed: String = "",
    val totalLent: String = "",
)

class LoansViewModel(
    loanRepository: LoanRepository,
    currencyRepository: CurrencyRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val uiState: StateFlow<LoansUiState> = combine(
        loanRepository.observeAll(),
        currencyRepository.observeCurrencies(),
        settingsRepository.hideAmounts,
    ) { loans, currencies, hidden ->
        val items = loans.map { it.toItemUi(currencies, hidden) }
        LoansUiState(
            isLoading = false,
            open = items.filterNot { it.isDone },
            settled = items.filter { it.isDone },
            totalBorrowed = totals(loans, LoanDirection.BORROWED, currencies, hidden),
            totalLent = totals(loans, LoanDirection.LENT, currencies, hidden),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LoansUiState())

    private fun totals(loans: List<LoanWithBalance>, direction: LoanDirection, currencies: Map<String, Currency>, hidden: Boolean): String {
        // A lezárt kölcsön maradéka leírva → nem számít bele.
        val sums = loans.filter { it.loan.direction == direction && it.outstandingMinor != 0L && it.loan.closedDate == null }
            .groupBy { it.loan.currencyCode }
            .mapValues { (_, list) -> list.sumOf { it.outstandingMinor } }
        if (sums.isEmpty()) return ""
        if (hidden) return HomeViewModel.MASK
        return sums.entries.joinToString(" · ") { (code, minor) -> format(minor, code, currencies) }
    }
}

internal fun format(minor: Long, code: String, currencies: Map<String, Currency>, plus: Boolean = false): String =
    currencies[code]?.let { MoneyFormatter.format(minor, it, plus) } ?: "$minor $code"

internal fun LoanWithBalance.toItemUi(currencies: Map<String, Currency>, hidden: Boolean) = LoanItemUi(
    id = loan.id,
    personName = loan.personName,
    direction = loan.direction,
    outstanding = if (hidden) HomeViewModel.MASK else format(outstandingMinor, loan.currencyCode, currencies),
    isSettled = outstandingMinor == 0L && lastMovementDate != null,
    canRepay = outstandingMinor > 0L && loan.closedDate == null,
    lastMovementDate = lastMovementDate,
    closedDate = loan.closedDate,
    color = loan.color,
    icon = loan.icon,
)
