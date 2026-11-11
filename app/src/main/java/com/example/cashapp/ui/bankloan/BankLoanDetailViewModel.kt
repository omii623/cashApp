package com.example.cashapp.ui.bankloan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cashapp.data.repository.BankLoanRepository
import com.example.cashapp.data.repository.CurrencyRepository
import com.example.cashapp.data.settings.SettingsRepository
import com.example.cashapp.domain.model.BankLoanEventType
import com.example.cashapp.domain.model.Currency
import com.example.cashapp.domain.money.AmountParser
import com.example.cashapp.ui.home.HomeViewModel
import com.example.cashapp.ui.loan.format
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A törlesztési terv egy sora, megformázva. */
data class ScheduleRowUi(
    val index: Int,
    val dueDate: LocalDate,
    val payment: String,
    val principal: String,
    val interest: String,
    val balanceAfter: String,
    val paid: Boolean,
    /** A részlet előtti előtörlesztés (null: nem volt). */
    val prepaidBefore: String?,
)

/** Előtörlesztés / kamatváltozás a listában. */
data class EventUi(
    val id: Long,
    val type: BankLoanEventType,
    val date: LocalDate,
    val afterInstallment: Int,
    /** Előtörlesztésnél az összeg, kamatváltozásnál az új THM (pl. „8,5 %”). */
    val value: String,
    val penalty: String?,
)

/** A „Részlet befizetve” ablak adatai: a következő részlet. */
data class NextInstallmentUi(val index: Int, val dueDate: LocalDate, val amountText: String)

data class BankLoanDetailUiState(
    val isLoading: Boolean = true,
    val notFound: Boolean = false,
    val item: BankLoanItemUi? = null,
    val currency: Currency? = null,
    val originalPrincipal: String = "",
    /** Visszafizetett rész az eredeti hitelösszegből, 0–100. */
    val paidPercent: Int = 0,
    val monthly: String = "–",
    val thmPercent: Double = 0.0,
    val endDate: LocalDate? = null,
    val interestPaid: String = "",
    val rows: List<ScheduleRowUi> = emptyList(),
    val paidCount: Int = 0,
    val events: List<EventUi> = emptyList(),
    val next: NextInstallmentUi? = null,
    val note: String = "",
    val closedDate: LocalDate? = null,
    val actionFailed: Boolean = false,
)

class BankLoanDetailViewModel(
    private val loanId: Long,
    private val bankLoanRepository: BankLoanRepository,
    currencyRepository: CurrencyRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val actionFailed = MutableStateFlow(false)

    val uiState: StateFlow<BankLoanDetailUiState> = combine(
        bankLoanRepository.observe(loanId),
        currencyRepository.observeCurrencies(),
        settingsRepository.hideAmounts,
        actionFailed,
    ) { overview, currencies, hidden, failed ->
        if (overview == null) return@combine BankLoanDetailUiState(isLoading = false, notFound = true)
        val loan = overview.loan
        val currency = currencies[loan.currencyCode]
        fun money(minor: Long) = if (hidden) HomeViewModel.MASK else format(minor, loan.currencyCode, currencies)
        val schedule = overview.schedule
        val paid = overview.paidInstallments
        val next = overview.nextInstallment
        val original = overview.originalPrincipalMinor
        BankLoanDetailUiState(
            isLoading = false,
            item = overview.toItemUi(currencies, hidden),
            currency = currency,
            originalPrincipal = money(original),
            paidPercent = if (original <= 0) 0 else (((original - overview.outstandingMinor) * 100) / original).toInt().coerceIn(0, 100),
            monthly = next?.let { money(it.paymentMinor) } ?: "–",
            thmPercent = next?.thmPercent ?: schedule.installments.lastOrNull()?.thmPercent ?: loan.thmPercent,
            endDate = schedule.lastDueDate,
            interestPaid = money(schedule.interestPaidAfter(paid)),
            rows = schedule.installments.map {
                ScheduleRowUi(
                    index = it.index,
                    dueDate = it.dueDate,
                    payment = money(it.paymentMinor),
                    principal = money(it.principalMinor),
                    interest = money(it.interestMinor),
                    balanceAfter = money(it.balanceAfterMinor),
                    paid = it.index <= paid,
                    prepaidBefore = it.prepaidBeforeMinor.takeIf { p -> p > 0 }?.let(::money),
                )
            },
            paidCount = paid,
            events = overview.events.map { e ->
                EventUi(
                    id = e.id,
                    type = e.type,
                    date = e.date,
                    afterInstallment = e.afterInstallment,
                    value = when (e.type) {
                        BankLoanEventType.PREPAYMENT -> money(e.amountMinor ?: 0)
                        BankLoanEventType.RATE_CHANGE -> com.example.cashapp.ui.loan.formatPercent(e.newThmPercent ?: 0.0)
                    },
                    penalty = e.penaltyMinor?.takeIf { it > 0 }?.let(::money),
                )
            },
            next = next?.takeIf { !overview.isClosed }?.let {
                NextInstallmentUi(it.index, it.dueDate, AmountParser.toEditText(it.paymentMinor, currency?.decimals ?: 0))
            },
            note = loan.note,
            closedDate = loan.closedDate,
            actionFailed = failed,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BankLoanDetailUiState())

    /** @return false, ha az összeg érvénytelen (az ablak nyitva marad). */
    fun payInstallment(date: LocalDate, amountText: String): Boolean {
        val decimals = uiState.value.currency?.decimals ?: 0
        val amount = AmountParser.parseAmount(amountText, decimals) ?: return false
        launchAction { bankLoanRepository.payNextInstallment(loanId, date, amount) }
        return true
    }

    fun changeRate(thmPercent: Double) = launchAction { bankLoanRepository.changeRate(loanId, LocalDate.now(), thmPercent) }
    fun deleteEvent(id: Long) = launchAction { bankLoanRepository.deleteEvent(id) }
    fun close() = launchAction { bankLoanRepository.close(loanId) }
    fun reopen() = launchAction { bankLoanRepository.reopen(loanId) }
    fun dismissError() = actionFailed.update { false }

    private fun launchAction(action: suspend () -> Unit) {
        viewModelScope.launch {
            if (runCatching { action() }.isFailure) actionFailed.value = true
        }
    }
}
