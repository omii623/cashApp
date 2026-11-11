package com.example.cashapp.ui.bankloan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cashapp.data.local.entity.BankLoanEventEntity
import com.example.cashapp.data.repository.BankLoanOverview
import com.example.cashapp.data.repository.BankLoanRepository
import com.example.cashapp.data.repository.CurrencyRepository
import com.example.cashapp.data.repository.schedule
import com.example.cashapp.domain.model.BankLoanEventType
import com.example.cashapp.domain.model.Currency
import com.example.cashapp.domain.model.PrepaymentMode
import com.example.cashapp.domain.money.AmountParser
import com.example.cashapp.domain.money.MoneyFormatter
import com.example.cashapp.ui.loan.PrepaymentUi
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BankLoanPrepaymentUiState(
    val isLoading: Boolean = true,
    val loanName: String = "",
    val currency: Currency? = null,
    val balance: String = "",
    val amountText: String = "",
    val amountError: Boolean = false,
    val penaltyText: String = "",
    val penaltyError: Boolean = false,
    val thresholdText: String = "",
    val thresholdError: Boolean = false,
    val date: LocalDate = LocalDate.now(),
    val mode: PrepaymentMode = PrepaymentMode.SHORTER_TERM,
    /** A két változat a hitel saját tervéből (a korábbi eseményekkel együtt); null, ha nincs érvényes összeg. */
    val result: PrepaymentUi? = null,
    val isSaving: Boolean = false,
    val saveFailed: Boolean = false,
    val done: Boolean = false,
)

/**
 * Előtörlesztés egy banki hitelre: a kalkulátor mezői (összeg, büntető %, küszöb), de a változatok a hitel
 * tényleges törlesztési tervéből számolnak, az eddig befizetett részletek után.
 */
class BankLoanPrepaymentViewModel(
    private val loanId: Long,
    private val bankLoanRepository: BankLoanRepository,
    currencyRepository: CurrencyRepository,
) : ViewModel() {

    private data class Input(
        val amountText: String = "",
        val penaltyText: String = "",
        val thresholdText: String = "",
        val date: LocalDate = LocalDate.now(),
        val mode: PrepaymentMode = PrepaymentMode.SHORTER_TERM,
        val isSaving: Boolean = false,
        val saveFailed: Boolean = false,
        val done: Boolean = false,
    )

    private val input = MutableStateFlow(Input())

    /** A mentéshez szükséges, utoljára kiszámolt értékek. */
    private var computed: Pair<Long, Long>? = null

    val uiState: StateFlow<BankLoanPrepaymentUiState> = combine(
        input,
        bankLoanRepository.observe(loanId),
        currencyRepository.observeCurrencies(),
    ) { i, overview, currencies ->
        if (overview == null) return@combine BankLoanPrepaymentUiState(isLoading = false, done = true)
        val currency = currencies[overview.loan.currencyCode]
        val decimals = currency?.decimals ?: 0
        val amount = AmountParser.parseAmount(i.amountText, decimals)
        val penaltyPercent = if (i.penaltyText.isBlank()) 0.0
            else i.penaltyText.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 && it.isFinite() }
        val threshold = if (i.thresholdText.isBlank()) 0L else AmountParser.parseSignedAmount(i.thresholdText, decimals)?.takeIf { it >= 0 }
        val result = if (currency != null && amount != null && penaltyPercent != null && threshold != null) {
            runCatching { compute(overview, amount, penaltyPercent, threshold, currency) }.getOrNull()
        } else null
        computed = result?.second
        BankLoanPrepaymentUiState(
            isLoading = false,
            loanName = overview.loan.name,
            currency = currency,
            balance = currency?.let { MoneyFormatter.format(overview.outstandingMinor, it) } ?: "",
            amountText = i.amountText,
            amountError = i.amountText.isNotBlank() && amount == null,
            penaltyText = i.penaltyText,
            penaltyError = penaltyPercent == null,
            thresholdText = i.thresholdText,
            thresholdError = threshold == null,
            date = i.date,
            mode = i.mode,
            result = result?.first,
            isSaving = i.isSaving,
            saveFailed = i.saveFailed,
            done = i.done,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BankLoanPrepaymentUiState())

    /**
     * A két változat: a hitel terve egy újabb előtörlesztés-eseménnyel (az eddig befizetett részletek után).
     * Megtakarítás = (eredeti részletek + korábbi előtörlesztések) − (új részletek + összes előtörlesztés + büntető).
     * @return a megjelenítendő eredmény és a mentéshez (ténylegesen előtörlesztett összeg, büntető)
     */
    private fun compute(
        overview: BankLoanOverview,
        amount: Long,
        penaltyPercent: Double,
        threshold: Long,
        currency: Currency,
    ): Pair<PrepaymentUi, Pair<Long, Long>>? {
        val outstanding = overview.outstandingMinor
        if (outstanding <= 0) return null
        val paid = overview.paidInstallments
        val prepaid = minOf(amount, outstanding)
        val penalty = BigDecimal.valueOf((prepaid - threshold).coerceAtLeast(0))
            .multiply(BigDecimal.valueOf(penaltyPercent)).divide(BigDecimal.valueOf(100))
            .setScale(0, RoundingMode.HALF_UP).longValueExact()
        val base = overview.schedule
        val baseTotal = base.totalPaymentMinor + base.prepaidMinor

        fun scenario(mode: PrepaymentMode) = overview.loan.schedule(
            overview.events + BankLoanEventEntity(
                bankLoanId = overview.loan.id,
                date = LocalDate.now(),
                afterInstallment = paid,
                type = BankLoanEventType.PREPAYMENT,
                amountMinor = prepaid,
                mode = mode,
            )
        )
        val lower = scenario(PrepaymentMode.LOWER_PAYMENT)
        val shorter = scenario(PrepaymentMode.SHORTER_TERM)
        fun saving(s: com.example.cashapp.domain.loan.AmortizationSchedule.Schedule) =
            baseTotal - (s.totalPaymentMinor + s.prepaidMinor + penalty)
        fun m(v: Long) = MoneyFormatter.format(v, currency)

        val ui = PrepaymentUi(
            month = paid,
            balanceBefore = m(outstanding),
            prepaid = m(prepaid),
            penalty = m(penalty),
            paidOff = prepaid >= outstanding,
            lowerMonthly = m(lower.installments.getOrNull(paid)?.paymentMinor ?: 0),
            lowerSaving = m(saving(lower)),
            shorterMonths = (shorter.installments.size - paid).coerceAtLeast(0),
            shorterLast = m(shorter.installments.lastOrNull()?.paymentMinor ?: 0),
            shorterSaving = m(saving(shorter)),
        )
        return ui to (prepaid to penalty)
    }

    fun onAmount(text: String) = input.update { it.copy(amountText = text) }
    fun onPenalty(text: String) = input.update { it.copy(penaltyText = text) }
    fun onThreshold(text: String) = input.update { it.copy(thresholdText = text) }
    fun onDate(date: LocalDate) = input.update { it.copy(date = date) }
    fun onMode(mode: PrepaymentMode) = input.update { it.copy(mode = mode) }
    fun dismissError() = input.update { it.copy(saveFailed = false) }

    /** @param note a könyvelt tétel megjegyzése (a UI adja, a nyelvnek megfelelően). */
    fun save(note: String) {
        val i = input.value
        val (prepaid, penalty) = computed ?: return
        if (i.isSaving) return
        input.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val ok = runCatching { bankLoanRepository.addPrepayment(loanId, i.date, prepaid, penalty, i.mode, note) }.isSuccess
            input.update { it.copy(isSaving = false, done = ok, saveFailed = !ok) }
        }
    }
}
