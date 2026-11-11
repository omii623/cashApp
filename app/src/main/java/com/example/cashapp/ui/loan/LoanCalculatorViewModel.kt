package com.example.cashapp.ui.loan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cashapp.data.repository.CurrencyRepository
import com.example.cashapp.data.settings.SettingsRepository
import com.example.cashapp.domain.loan.LoanCalculator
import com.example.cashapp.domain.model.Currency
import com.example.cashapp.domain.money.AmountParser
import com.example.cashapp.domain.money.MoneyFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class LoanCalculatorUiState(
    val currency: Currency? = null,
    val amountText: String = "",
    /** Beírt, de érvénytelen összeg (üresen nincs hiba, csak nincs eredmény). */
    val amountError: Boolean = false,
    val thmPercent: Double = LoanCalculatorViewModel.DEFAULT_THM,
    val months: Int = LoanCalculatorViewModel.DEFAULT_MONTHS,
    /** Megformázott eredmények; null, ha nincs érvényes összeg. */
    val monthlyPayment: String? = null,
    val totalRepayment: String? = null,
    val totalCost: String? = null,
    // --- előtörlesztés (üres mezők = nincs számítás)
    val prepayText: String = "",
    val prepayError: Boolean = false,
    /** Hányadik havi részlet befizetése után történik az előtörlesztés. */
    val prepayMonthText: String = LoanCalculatorViewModel.DEFAULT_PREPAY_MONTH.toString(),
    /** Érvénytelen hónap: 1 és (futamidő − 1) között kell lennie. */
    val prepayMonthError: Boolean = false,
    val penaltyText: String = "",
    val penaltyError: Boolean = false,
    val thresholdText: String = "",
    val thresholdError: Boolean = false,
    val prepay: PrepaymentUi? = null,
    /** Éves infláció %; 0 → nincs vásárlóerő-számítás. */
    val inflationPercent: Double = 0.0,
    /** Mai értéken (csak ha infláció > 0 és van eredmény). */
    val realTotal: String? = null,
    val realCost: String? = null,
    val realLast: String? = null,
    /** A valós költség negatív (az infláció nagyobb, mint a kamat). */
    val realCostNegative: Boolean = false,
)

/** Az előtörlesztés eredménye megformázva. */
data class PrepaymentUi(
    val month: Int,
    val balanceBefore: String,
    val prepaid: String,
    val penalty: String,
    val paidOff: Boolean,
    /** Csökkenő részlet (azonos futamidő). */
    val lowerMonthly: String,
    val lowerSaving: String,
    /** Rövidebb futamidő (azonos részlet). */
    val shorterMonths: Int,
    val shorterLast: String,
    val shorterSaving: String,
    /** Megtakarítás mai pénzben (csak ha van infláció). */
    val lowerRealSaving: String? = null,
    val shorterRealSaving: String? = null,
)

/** Hitelkalkulátor: igényelt összeg + THM + futamidő → havi törlesztő, teljes visszafizetés, hitelköltség. */
class LoanCalculatorViewModel(
    settingsRepository: SettingsRepository,
    currencyRepository: CurrencyRepository,
) : ViewModel() {

    private data class Input(
        val amountText: String = "",
        val thmPercent: Double = DEFAULT_THM,
        val months: Int = DEFAULT_MONTHS,
        val prepayText: String = "",
        val prepayMonthText: String = DEFAULT_PREPAY_MONTH.toString(),
        val penaltyText: String = "",
        val thresholdText: String = "",
        val inflationPercent: Double = 0.0,
    )

    private val input = MutableStateFlow(Input())

    val uiState: StateFlow<LoanCalculatorUiState> = combine(
        input,
        settingsRepository.baseCurrency,
        currencyRepository.observeCurrencies(),
    ) { i, base, currencies ->
        val currency = currencies[base]
        val principal = currency?.let { AmountParser.parseAmount(i.amountText, it.decimals) }
        // Extrém kézi értékeknél (pl. óriási futamidő) az összeg nem fér el → nincs eredmény, nem omlik össze.
        val result = principal?.let { runCatching { LoanCalculator.calculate(it, i.thmPercent, i.months) }.getOrNull() }
        // Előtörlesztés: csak ha az összeg meg van adva; az üres büntető / küszöb 0-nak számít.
        val decimals = currency?.decimals ?: 0
        val prepayAmount = AmountParser.parseAmount(i.prepayText, decimals)
        val prepayMonth = i.prepayMonthText.trim().toIntOrNull()?.takeIf { it in 1 until i.months }
        val penalty = if (i.penaltyText.isBlank()) 0.0
            else i.penaltyText.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 && it.isFinite() }
        val threshold = if (i.thresholdText.isBlank()) 0L else AmountParser.parseSignedAmount(i.thresholdText, decimals)?.takeIf { it >= 0 }
        val prepay = if (currency != null && principal != null && prepayAmount != null && prepayMonth != null && penalty != null && threshold != null) {
            runCatching {
                LoanCalculator.prepayment(principal, i.thmPercent, i.months, prepayAmount, prepayMonth, penalty, threshold, i.inflationPercent)
            }.getOrNull()?.let { r ->
                fun m(v: Long) = MoneyFormatter.format(v, currency)
                PrepaymentUi(
                    month = prepayMonth,
                    balanceBefore = m(r.balanceBeforeMinor),
                    prepaid = m(r.prepaidMinor),
                    penalty = m(r.penaltyMinor),
                    paidOff = r.paidOff,
                    lowerMonthly = m(r.lowerPayment.monthlyPaymentMinor),
                    lowerSaving = m(r.lowerPayment.savingMinor),
                    shorterMonths = r.shorterTerm.remainingMonths,
                    shorterLast = m(r.shorterTerm.lastPaymentMinor),
                    shorterSaving = m(r.shorterTerm.savingMinor),
                    lowerRealSaving = if (i.inflationPercent > 0) m(r.lowerPayment.realSavingMinor) else null,
                    shorterRealSaving = if (i.inflationPercent > 0) m(r.shorterTerm.realSavingMinor) else null,
                )
            }
        } else null
        val real = if (currency != null && principal != null && result != null && i.inflationPercent > 0) {
            runCatching { LoanCalculator.realValue(principal, result.monthlyPaymentMinor, i.months, i.inflationPercent) }.getOrNull()
        } else null
        LoanCalculatorUiState(
            inflationPercent = i.inflationPercent,
            realTotal = real?.let { MoneyFormatter.format(it.totalRepaymentMinor, currency!!) },
            realCost = real?.let { MoneyFormatter.format(it.totalCostMinor, currency!!) },
            realLast = real?.let { MoneyFormatter.format(it.lastPaymentMinor, currency!!) },
            realCostNegative = (real?.totalCostMinor ?: 0) < 0,
            prepayText = i.prepayText,
            prepayError = i.prepayText.isNotBlank() && prepayAmount == null,
            prepayMonthText = i.prepayMonthText,
            prepayMonthError = i.prepayText.isNotBlank() && prepayMonth == null,
            penaltyText = i.penaltyText,
            penaltyError = penalty == null,
            thresholdText = i.thresholdText,
            thresholdError = threshold == null,
            prepay = prepay,
            currency = currency,
            amountText = i.amountText,
            amountError = i.amountText.isNotBlank() && principal == null,
            thmPercent = i.thmPercent,
            months = i.months,
            monthlyPayment = result?.let { MoneyFormatter.format(it.monthlyPaymentMinor, currency) },
            totalRepayment = result?.let { MoneyFormatter.format(it.totalRepaymentMinor, currency) },
            totalCost = result?.let { MoneyFormatter.format(it.totalCostMinor, currency) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LoanCalculatorUiState())

    fun onAmount(text: String) = input.update { it.copy(amountText = text) }
    /** Éves infláció (≥ 0, legfeljebb 2 tizedes). A csúszka 0–[MAX_INFLATION_SLIDER]; kézzel bármekkora. */
    fun onInflation(value: Double) = input.update { it.copy(inflationPercent = (Math.round(value * 100) / 100.0).coerceAtLeast(0.0)) }
    fun onPrepay(text: String) = input.update { it.copy(prepayText = text) }
    fun onPrepayMonth(text: String) = input.update { it.copy(prepayMonthText = text) }
    fun onPenalty(text: String) = input.update { it.copy(penaltyText = text) }
    fun onThreshold(text: String) = input.update { it.copy(thresholdText = text) }

    /**
     * THM (≥ 0, legfeljebb 2 tizedes). A csúszka 0–[MAX_THM_SLIDER] között, 0,1-es lépéssel állít;
     * kézzel bármekkora nem negatív érték beírható.
     */
    fun onThm(value: Double) = input.update { it.copy(thmPercent = (Math.round(value * 100) / 100.0).coerceAtLeast(0.0)) }

    /** Futamidő hónapban (≥ 1). A csúszka [MIN_MONTHS]–[MAX_MONTHS_SLIDER] között állít; kézzel bármekkora beírható. */
    fun onMonths(value: Int) = input.update { it.copy(months = value.coerceAtLeast(MIN_MONTHS)) }

    companion object {
        const val DEFAULT_THM = 15.0
        const val DEFAULT_MONTHS = 60
        const val DEFAULT_PREPAY_MONTH = 12
        /** Az infláció csúszka felső határa (kézzel ennél több is beírható). */
        const val MAX_INFLATION_SLIDER = 10.0
        /** A THM csúszka felső határa (kézzel ennél több is beírható). */
        const val MAX_THM_SLIDER = 50.0
        const val MIN_MONTHS = 1
        /** A hónap csúszka felső határa (kézzel ennél több is beírható). */
        const val MAX_MONTHS_SLIDER = 360
    }
}
