package com.example.cashapp.ui.loan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cashapp.data.local.entity.AccountEntity
import com.example.cashapp.data.local.entity.LoanEntity
import com.example.cashapp.data.repository.AccountRepository
import com.example.cashapp.data.repository.CurrencyRepository
import com.example.cashapp.data.repository.LoanRepository
import com.example.cashapp.data.repository.LoanRepository.MovementDraft
import com.example.cashapp.domain.loan.LoanMath
import com.example.cashapp.domain.model.Currency
import com.example.cashapp.domain.model.LoanDirection
import com.example.cashapp.domain.model.TransactionType
import com.example.cashapp.domain.money.AmountParser
import com.example.cashapp.domain.money.CurrencyConverter
import com.example.cashapp.domain.money.MoneyFormatter
import com.example.cashapp.ui.transaction.RateField
import java.time.LocalDate
import kotlin.math.pow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoanMovementUiState(
    val isLoading: Boolean = true,
    val isEditing: Boolean = false,
    val personName: String = "",
    val direction: LoanDirection = LoanDirection.LENT,
    /** Igaz: új kölcsön (nő a tartozás); hamis: törlesztés. */
    val increase: Boolean = true,
    val loanCurrencyCode: String = "",
    val amountText: String = "",
    /** A beírt összeg pénzneme (bármelyik lehet, mint a bevétel/kiadásnál). */
    val currency: Currency? = null,
    val currencies: List<Currency> = emptyList(),
    val account: AccountEntity? = null,
    val accounts: List<AccountEntity> = emptyList(),
    val date: LocalDate = LocalDate.now(),
    val note: String = "",
    /** Beírt pénznem ≠ számla pénzneme → árfolyam a számlára (mint a bevétel/kiadás űrlapon). */
    val rate: RateField? = null,
    /** Se a beírt, se a számla pénzneme nem a kölcsöné → árfolyam a kölcsön pénznemére is kell. */
    val loanRate: RateField? = null,
    /** Ha a beírt összeg nem a kölcsön pénznemében van: ennyivel változik a hátralék (megformázva). */
    val loanAmountPreview: String? = null,
    val amountError: Boolean = false,
    val isSaving: Boolean = false,
    val saveFailed: Boolean = false,
    val isDone: Boolean = false,
    val draft: MovementDraft? = null,
) {
    /** Pénz jön be a számlára (zöld) vagy megy ki (piros) – mint a bevétel/kiadás. */
    val isIncome: Boolean get() = LoanMath.transactionType(direction, increase) == TransactionType.INCOME
}

/**
 * Kölcsön mozgás (kölcsön / törlesztés) teljes képernyős űrlapja – új vagy meglévő ([editId]).
 * A pénznem és a számla szabadon választható, a logika a bevétel/kiadás űrlapéval egyezik;
 * a hátralékhoz az összeget a kölcsön pénznemére is átváltja.
 */
class LoanMovementViewModel(
    private val loanId: Long,
    private val increase: Boolean,
    private val editId: Long?,
    private val loanRepository: LoanRepository,
    accountRepository: AccountRepository,
    currencyRepository: CurrencyRepository,
) : ViewModel() {

    /** A felhasználó által módosított mezők. `null` = alapértelmezett. */
    private data class Input(
        val amountText: String = "",
        val currencyCode: String? = null,
        val accountId: Long? = null,
        val date: LocalDate = LocalDate.now(),
        val note: String = "",
        val rateText: String? = null,
        val loanRateText: String? = null,
        val increase: Boolean,
        val showErrors: Boolean = false,
        val isSaving: Boolean = false,
        val saveFailed: Boolean = false,
        val isDone: Boolean = false,
        val loan: LoanEntity? = null,
        /** Szerkesztésnél az eredeti (esetleg archivált) számla is választható marad. */
        val pinnedAccount: AccountEntity? = null,
    )

    private val input = MutableStateFlow(Input(increase = increase))

    private data class Data(val accounts: List<AccountEntity>, val currencies: Map<String, Currency>, val converter: CurrencyConverter)

    private val data = combine(
        accountRepository.observeActive(),
        currencyRepository.observeCurrencies(),
        currencyRepository.observeConverter(),
        ::Data,
    )

    val uiState: StateFlow<LoanMovementUiState> = combine(input, data) { i, d -> buildState(i, d) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LoanMovementUiState(isEditing = editId != null, increase = increase))

    init {
        viewModelScope.launch {
            val loan = loanRepository.get(loanId) ?: run { input.update { it.copy(isDone = true) }; return@launch }
            val tx = editId?.let { loanRepository.getMovement(it) }
            if (tx == null) {
                input.update { it.copy(loan = loan) }
                return@launch
            }
            val d = data.first()
            val cur = d.currencies[tx.currencyCode]
            val account = accountRepository.get(tx.accountId)
            // Árfolyamok a mentett összegekből, ugyanabban az irányban, ahogy az űrlap mutatja (1 drágább = X olcsóbb).
            val rateText = tx.rateToAccount?.let { r -> AmountParser.rateToEditText(if (r < 1) 1 / r else r) }
            val loanCur = d.currencies[loan.currencyCode]
            val loanRateText = if (cur != null && loanCur != null && account != null &&
                tx.currencyCode != loan.currencyCode && account.currencyCode != loan.currencyCode
            ) {
                val r = (tx.loanAmountMinor ?: tx.amountMinor) / 10.0.pow(loanCur.decimals) / (tx.amountMinor / 10.0.pow(cur.decimals))
                AmountParser.rateToEditText(if (r < 1) 1 / r else r)
            } else null
            input.update {
                it.copy(
                    loan = loan,
                    increase = LoanMath.isIncrease(loan.direction, tx.type),
                    amountText = AmountParser.toEditText(tx.amountMinor, cur?.decimals ?: 0),
                    currencyCode = tx.currencyCode,
                    accountId = tx.accountId,
                    pinnedAccount = account,
                    date = tx.date,
                    note = tx.note,
                    rateText = rateText,
                    loanRateText = loanRateText,
                )
            }
        }
    }

    // ------------------------------------------------------------------ események

    fun onAmount(text: String) = input.update { it.copy(amountText = text) }

    /** Számlaváltáskor a pénznem és az árfolyamok visszaállnak (mint a bevétel/kiadásnál). */
    fun onAccount(id: Long) = input.update { it.copy(accountId = id, currencyCode = null, rateText = null, loanRateText = null) }

    fun onCurrency(code: String) = input.update { it.copy(currencyCode = code, rateText = null, loanRateText = null) }
    fun onRate(text: String) = input.update { it.copy(rateText = text) }
    fun onRateReset() = input.update { it.copy(rateText = null) }
    fun onLoanRate(text: String) = input.update { it.copy(loanRateText = text) }
    fun onLoanRateReset() = input.update { it.copy(loanRateText = null) }
    fun onDate(date: LocalDate) = input.update { it.copy(date = date) }
    fun onNote(text: String) = input.update { it.copy(note = text) }
    fun dismissError() = input.update { it.copy(saveFailed = false) }

    fun save() {
        val state = uiState.value
        val draft = state.draft
        if (draft == null || state.isSaving) {
            input.update { it.copy(showErrors = true) }
            return
        }
        input.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                loanRepository.saveMovement(loanId, draft, editId)
                input.update { it.copy(isSaving = false, isDone = true) }
            } catch (e: Exception) {
                input.update { it.copy(isSaving = false, saveFailed = true) }
            }
        }
    }

    fun delete() {
        val id = editId ?: return
        viewModelScope.launch {
            loanRepository.deleteMovement(id)
            input.update { it.copy(isDone = true) }
        }
    }

    // ------------------------------------------------------------------ állapot számítás

    private fun buildState(i: Input, d: Data): LoanMovementUiState {
        val loan = i.loan
        val accounts = d.accounts.let { list ->
            if (i.pinnedAccount == null || list.any { it.id == i.pinnedAccount.id }) list else list + i.pinnedAccount
        }
        // Alapértelmezett számla: az első, a kölcsön pénznemével egyező; ha nincs ilyen, az első.
        val account = accounts.firstOrNull { it.id == i.accountId }
            ?: loan?.let { l -> accounts.firstOrNull { it.currencyCode == l.currencyCode } }
            ?: accounts.firstOrNull()
        val currency = (i.currencyCode ?: account?.currencyCode)?.let { d.currencies[it] }
        val loanCurrency = loan?.let { d.currencies[it.currencyCode] }
        if (loan == null || account == null || currency == null || loanCurrency == null) {
            return LoanMovementUiState(
                isLoading = loan == null, isEditing = editId != null, increase = i.increase, isDone = i.isDone,
                personName = loan?.personName.orEmpty(), direction = loan?.direction ?: LoanDirection.LENT,
                loanCurrencyCode = loan?.currencyCode.orEmpty(), currencies = d.currencies.values.toList(),
            )
        }
        val accountCurrency = d.currencies.getValue(account.currencyCode)
        val amountMinor = AmountParser.parseAmount(i.amountText, currency.decimals)

        // Beírt pénznem → számla pénzneme
        var rateToAccount: Double? = 1.0
        var rateField: RateField? = null
        if (currency.code != account.currencyCode) {
            val (field, rate) = rateField(currency, accountCurrency, d.converter, i.rateText, amountMinor, i.showErrors) { MoneyFormatter.format(it, accountCurrency) }
            rateField = field
            rateToAccount = rate
        }
        val accountAmount = when {
            amountMinor == null || rateToAccount == null -> null
            rateField == null -> amountMinor
            else -> CurrencyConverter.convertWithRate(amountMinor, currency.decimals, accountCurrency.decimals, rateToAccount)
        }

        // Összeg a kölcsön pénznemében (a hátralékhoz)
        var loanRateField: RateField? = null
        val loanAmount: Long? = when {
            amountMinor == null -> null
            currency.code == loanCurrency.code -> amountMinor
            account.currencyCode == loanCurrency.code -> accountAmount
            else -> {
                val (field, rate) = rateField(currency, loanCurrency, d.converter, i.loanRateText, amountMinor, i.showErrors) { MoneyFormatter.format(it, loanCurrency) }
                loanRateField = field
                rate?.let { CurrencyConverter.convertWithRate(amountMinor, currency.decimals, loanCurrency.decimals, it) }
            }
        }
        val loanPreview = loanAmount?.takeIf { currency.code != loanCurrency.code }?.let { MoneyFormatter.format(it, loanCurrency) }

        val draft = if (amountMinor != null && accountAmount != null && loanAmount != null && accountAmount > 0 && loanAmount > 0) {
            MovementDraft(
                increase = i.increase,
                amountMinor = amountMinor,
                accountId = account.id,
                date = i.date,
                note = i.note,
                currencyCode = currency.code,
                accountAmountMinor = accountAmount,
                rateToAccount = if (rateField == null) null else rateToAccount,
                loanAmountMinor = loanAmount,
            )
        } else null

        return LoanMovementUiState(
            isLoading = false,
            isEditing = editId != null,
            personName = loan.personName,
            direction = loan.direction,
            increase = i.increase,
            loanCurrencyCode = loan.currencyCode,
            amountText = i.amountText,
            currency = currency,
            currencies = d.currencies.values.toList(),
            account = account,
            accounts = accounts,
            date = i.date,
            note = i.note,
            rate = rateField,
            loanRate = loanRateField,
            loanAmountPreview = loanPreview,
            amountError = i.showErrors && amountMinor == null,
            isSaving = i.isSaving,
            saveFailed = i.saveFailed,
            isDone = i.isDone,
            draft = draft,
        )
    }

    /**
     * Árfolyam mező [from] → [to] irányban, a bevétel/kiadás űrlappal azonos módon:
     * mindig „1 drágább = X olcsóbb” formában mutatja; a tárolt árfolyam az alapérték, kézzel felülírható.
     * @return a mező és a használt árfolyam (1 [from] = ? [to]); `null`, ha érvénytelen.
     */
    private fun rateField(
        from: Currency,
        to: Currency,
        converter: CurrencyConverter,
        override: String?,
        amountMinor: Long?,
        showErrors: Boolean,
        formatTarget: (Long) -> String,
    ): Pair<RateField, Double?> {
        val stored = converter.rate(from.code, to.code)
        val inverted = if (stored != null) stored < 1 else from.decimals < to.decimals
        val shownDefault = stored?.let { if (inverted) 1 / it else it }
        val text = override ?: shownDefault?.let(AmountParser::rateToEditText) ?: ""
        val rate = AmountParser.parseRate(text)?.let { if (inverted) 1 / it else it }
        val converted = if (amountMinor != null && rate != null) CurrencyConverter.convertWithRate(amountMinor, from.decimals, to.decimals, rate) else null
        return RateField(
            leftCode = if (inverted) to.code else from.code,
            rightCode = if (inverted) from.code else to.code,
            text = text,
            isDefault = override == null && stored != null,
            noStoredRate = stored == null,
            error = showErrors && rate == null,
            accountAmountPreview = converted?.let { "≈ " + formatTarget(it) },
        ) to rate
    }

    companion object {
        /** A mozgás fajtájának felirata (gomb, űrlap címe) a kölcsön irányától függ. */
        fun kindLabel(direction: LoanDirection, increase: Boolean): Int = when (direction) {
            LoanDirection.LENT -> if (increase) com.example.cashapp.R.string.loan_lent_increase else com.example.cashapp.R.string.loan_lent_decrease
            LoanDirection.BORROWED -> if (increase) com.example.cashapp.R.string.loan_borrowed_increase else com.example.cashapp.R.string.loan_borrowed_decrease
        }
    }
}
