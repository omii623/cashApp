package com.example.cashapp.ui.bankloan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cashapp.data.local.entity.AccountEntity
import com.example.cashapp.data.local.entity.BankLoanEntity
import com.example.cashapp.data.local.entity.CategoryEntity
import com.example.cashapp.data.local.entity.SubcategoryEntity
import com.example.cashapp.data.repository.AccountRepository
import com.example.cashapp.data.repository.BankLoanRepository
import com.example.cashapp.data.repository.CategoryRepository
import com.example.cashapp.data.repository.CurrencyRepository
import com.example.cashapp.domain.loan.AmortizationSchedule
import com.example.cashapp.domain.loan.LoanCalculator
import com.example.cashapp.domain.model.BankLoanKind
import com.example.cashapp.domain.model.CategoryType
import com.example.cashapp.domain.model.Currency
import com.example.cashapp.domain.money.AmountParser
import com.example.cashapp.domain.money.MoneyFormatter
import com.example.cashapp.ui.loan.formatNumber
import com.example.cashapp.ui.transaction.CategoryOption
import com.example.cashapp.ui.transaction.SubcategoryOption
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Élő előnézet az űrlap alapján (a törlesztési tervből). */
data class BankLoanPreview(
    val monthly: String,
    /** A számolt annuitás (felülírt részletnél a súgóban látszik). */
    val calculatedMonthly: String,
    val total: String,
    val cost: String,
    val lastDue: LocalDate?,
    /** Korábban felvett hitelnél: a számolt hátralék a már befizetett részletek után (a banki hátralék súgója). */
    val calculatedBalance: String? = null,
    /** Korábban felvett hitelnél: a hátralék most (a banki hátralékkal, ha meg van adva) és a hátralévő részletek. */
    val currentBalance: String? = null,
    val remainingInstallments: Int = 0,
)

/**
 * Kitöltés a kalkulátorból („Mentés hitelként”): a beírt összeg szövegként (a számla pénznemére nem kell
 * átszámolni, a tizedesek a mentéskor ellenőrződnek), THM, futamidő.
 */
data class BankLoanPrefill(val principalText: String?, val thmPercent: Double?, val months: Int?)

data class BankLoanEditUiState(
    val isLoading: Boolean = true,
    val isNew: Boolean = true,
    val name: String = "",
    val nameError: Boolean = false,
    val bankName: String = "",
    val kind: BankLoanKind = BankLoanKind.PERSONAL,
    val principalText: String = "",
    val principalError: Boolean = false,
    val thmText: String = "",
    val thmError: Boolean = false,
    val monthsText: String = "",
    val monthsError: Boolean = false,
    val firstDueDate: LocalDate = LocalDate.now(),
    /** A követés előtt már befizetett részletek (üres → 0). Új hitelnél a dátumból számolódik, amíg át nem írják. */
    val paidBeforeText: String = "",
    val paidBeforeEdited: Boolean = false,
    val paidBeforeError: Boolean = false,
    /** A bank szerinti hátralék a már befizetett részletek után (nem kötelező). */
    val bankBalanceText: String = "",
    val bankBalanceError: Boolean = false,
    val overrideText: String = "",
    val overrideError: Boolean = false,
    val accounts: List<AccountEntity> = emptyList(),
    val accountId: Long? = null,
    val categories: List<CategoryOption> = emptyList(),
    val categoryId: Long? = null,
    val categoryError: Boolean = false,
    val subcategories: List<SubcategoryOption> = emptyList(),
    val subcategoryId: Long? = null,
    val note: String = "",
    val currency: Currency? = null,
    val preview: BankLoanPreview? = null,
    val isSaving: Boolean = false,
    val saveFailed: Boolean = false,
    /** Mentés után a hitel azonosítója; törlés után -1. */
    val doneId: Long? = null,
) {
    val account: AccountEntity? get() = accounts.firstOrNull { it.id == accountId }

    /** Korábban felvett hitel: az első részlet a múltban van → látszanak a „már befizetett” mezők. */
    val isPast: Boolean get() = firstDueDate.isBefore(LocalDate.now()) || paidBeforeText.isNotBlank()
}

class BankLoanEditViewModel(
    private val loanId: Long?,
    private val prefill: BankLoanPrefill?,
    private val bankLoanRepository: BankLoanRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val currencyRepository: CurrencyRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(BankLoanEditUiState(isNew = loanId == null))
    val uiState: StateFlow<BankLoanEditUiState> = _state.asStateFlow()

    private var currencies: Map<String, Currency> = emptyMap()
    private var categories: List<CategoryEntity> = emptyList()
    private var subcategories: List<SubcategoryEntity> = emptyList()
    /** Szerkesztésnél a megtartandó mezők. */
    private var original: BankLoanEntity? = null

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        currencies = currencyRepository.observeCurrencies().first()
        val accounts = accountRepository.observeActive().first()
        categories = categoryRepository.observeCategories(CategoryType.EXPENSE).first()
        subcategories = categoryRepository.observeSubcategories(CategoryType.EXPENSE).first()
        val loan = loanId?.let { bankLoanRepository.get(it) }
        original = loan

        if (loan == null) {
            // Alapértelmezés: a „hitel” ikonú kategória (az alapadatokban a „Hitel”) az első alkategóriájával.
            val category = categories.firstOrNull { it.icon == LOAN_CATEGORY_ICON } ?: categories.firstOrNull()
            val account = accounts.firstOrNull { it.icon == BANK_ACCOUNT_ICON } ?: accounts.firstOrNull()
            _state.update {
                it.copy(
                    isLoading = false,
                    accounts = accounts,
                    accountId = account?.id,
                    categoryId = category?.id,
                    subcategoryId = category?.takeIf { c -> c.icon == LOAN_CATEGORY_ICON }
                        ?.let { c -> subcategories.firstOrNull { s -> s.categoryId == c.id }?.id },
                    principalText = prefill?.principalText.orEmpty(),
                    thmText = prefill?.thmPercent?.let(::formatNumber).orEmpty(),
                    monthsText = prefill?.months?.toString().orEmpty(),
                ).withOptions()
            }
            return
        }

        val accountList = accounts.toMutableList()
        // Archivált számla esetén is látsszon a jelenlegi.
        if (accountList.none { it.id == loan.accountId }) accountRepository.get(loan.accountId)?.let { accountList += it }
        val decimals = currencies[loan.currencyCode]?.decimals ?: 0
        _state.update {
            it.copy(
                isLoading = false,
                name = loan.name,
                bankName = loan.bankName,
                kind = loan.kind,
                principalText = AmountParser.toEditText(loan.principalMinor, decimals),
                thmText = formatNumber(loan.thmPercent),
                monthsText = loan.termMonths.toString(),
                firstDueDate = loan.firstDueDate,
                // Mentett hitelnél a tárolt érték marad (nem számolódik újra a mai dátumból).
                paidBeforeText = loan.paidMonthsBefore.takeIf { m -> m > 0 }?.toString().orEmpty(),
                paidBeforeEdited = true,
                bankBalanceText = loan.bankBalanceMinor?.let { b -> AmountParser.toEditText(b, decimals) }.orEmpty(),
                overrideText = loan.monthlyPaymentOverrideMinor?.let { p -> AmountParser.toEditText(p, decimals) }.orEmpty(),
                accounts = accountList,
                accountId = loan.accountId,
                categoryId = loan.categoryId,
                subcategoryId = loan.subcategoryId,
                note = loan.note,
            ).withOptions()
        }
    }

    /** A követés előtt befizetett részletek: üres → 0; különben 0 és futamidő − 1 között. */
    private fun parsePaidBefore(text: String, months: Int?): Int? =
        if (text.isBlank()) 0 else text.trim().toIntOrNull()?.takeIf { it >= 0 && (months == null || it < months) }

    /**
     * Kategória- és alkategória-lista, pénznem, a „már befizetett” részletek automatikus értéke (új hitelnél,
     * amíg kézzel át nem írják: az első részlet dátumától máig esedékesek) és az előnézet.
     */
    private fun BankLoanEditUiState.withOptions(): BankLoanEditUiState {
        val currency = account?.let { currencies[it.currencyCode] }
        val decimals = currency?.decimals ?: 0
        val principal = AmountParser.parseAmount(principalText, decimals)
        val thm = parseThm(thmText)
        val months = parseMonths(monthsText)
        val paidText = if (paidBeforeEdited || months == null) paidBeforeText else {
            AmortizationSchedule.installmentsDueBefore(firstDueDate, months, LocalDate.now())
                .coerceAtMost(months - 1).takeIf { it > 0 }?.toString().orEmpty()
        }
        val paidBefore = parsePaidBefore(paidText, months)
        val override = if (overrideText.isBlank()) null else AmountParser.parseAmount(overrideText, decimals)
        val bankBalance = if (bankBalanceText.isBlank()) null else AmountParser.parseAmount(bankBalanceText, decimals)
        val inputsOk = currency != null && principal != null && thm != null && months != null && paidBefore != null &&
            (overrideText.isBlank() || override != null) && (bankBalanceText.isBlank() || bankBalance != null)
        val preview = if (inputsOk) {
            runCatching {
                val params = AmortizationSchedule.Params(principal!!, thm!!, months!!, firstDueDate, override, paidBefore!!)
                val calculatedSchedule = AmortizationSchedule.build(params)
                val schedule = if (bankBalance != null && paidBefore > 0)
                    AmortizationSchedule.build(params.copy(openingBalanceMinor = bankBalance)) else calculatedSchedule
                val calculated = LoanCalculator.calculate(principal, thm, months).monthlyPaymentMinor
                fun m(v: Long) = MoneyFormatter.format(v, currency!!)
                BankLoanPreview(
                    monthly = m(schedule.nextAfter(paidBefore)?.paymentMinor ?: override ?: calculated),
                    calculatedMonthly = m(calculated),
                    total = m(schedule.totalPaymentMinor),
                    cost = m(schedule.totalPaymentMinor - principal),
                    lastDue = schedule.lastDueDate,
                    calculatedBalance = if (paidBefore > 0) m(calculatedSchedule.outstandingAfter(paidBefore)) else null,
                    currentBalance = if (paidBefore > 0) m(schedule.outstandingAfter(paidBefore)) else null,
                    remainingInstallments = (schedule.installments.size - paidBefore).coerceAtLeast(0),
                )
            }.getOrNull()
        } else null
        return copy(
            currency = currency,
            paidBeforeText = paidText,
            // A ViewModel listái (az állapot azonos nevű mezői itt elfednék őket).
            categories = this@BankLoanEditViewModel.categories.map { CategoryOption(it.id, it.name, it.color, it.icon) },
            subcategories = this@BankLoanEditViewModel.subcategories
                .filter { it.categoryId == categoryId }
                .map { SubcategoryOption(it.id, it.name) },
            preview = preview,
        )
    }

    private fun edit(block: (BankLoanEditUiState) -> BankLoanEditUiState) = _state.update { block(it).withOptions() }

    fun onName(text: String) = edit { it.copy(name = text, nameError = false) }
    fun onBankName(text: String) = edit { it.copy(bankName = text) }
    fun onKind(kind: BankLoanKind) = edit { it.copy(kind = kind) }
    fun onPrincipal(text: String) = edit { it.copy(principalText = text, principalError = false) }
    fun onThm(text: String) = edit { it.copy(thmText = text, thmError = false) }
    fun onMonths(text: String) = edit { it.copy(monthsText = text, monthsError = false, paidBeforeError = false) }
    fun onFirstDue(date: LocalDate) = edit { it.copy(firstDueDate = date) }
    fun onPaidBefore(text: String) = edit { it.copy(paidBeforeText = text, paidBeforeEdited = true, paidBeforeError = false) }
    fun onBankBalance(text: String) = edit { it.copy(bankBalanceText = text, bankBalanceError = false) }
    fun onOverride(text: String) = edit { it.copy(overrideText = text, overrideError = false) }
    fun onAccount(id: Long) = edit { it.copy(accountId = id) }
    fun onCategory(id: Long) = edit { if (it.categoryId == id) it else it.copy(categoryId = id, subcategoryId = null, categoryError = false) }
    fun onSubcategory(id: Long) = edit { it.copy(subcategoryId = if (it.subcategoryId == id) null else id) }
    fun onNote(text: String) = edit { it.copy(note = text) }
    fun dismissError() = _state.update { it.copy(saveFailed = false) }

    fun save() {
        val s = _state.value
        if (s.isLoading || s.isSaving) return
        val decimals = s.currency?.decimals ?: 0
        val name = s.name.trim()
        val principal = AmountParser.parseAmount(s.principalText, decimals)
        val thm = parseThm(s.thmText)
        val months = parseMonths(s.monthsText)
        val override = if (s.overrideText.isBlank()) null else AmountParser.parseAmount(s.overrideText, decimals)
        val overrideOk = s.overrideText.isBlank() || override != null
        val paidBefore = parsePaidBefore(s.paidBeforeText, months)
        // Banki hátralék csak akkor számít, ha van már befizetett részlet.
        val bankBalance = if (s.bankBalanceText.isBlank() || paidBefore == 0) null else AmountParser.parseAmount(s.bankBalanceText, decimals)
        val bankBalanceOk = s.bankBalanceText.isBlank() || paidBefore == 0 || bankBalance != null
        val account = s.account
        if (name.isEmpty() || principal == null || thm == null || months == null || !overrideOk || paidBefore == null ||
            !bankBalanceOk || account == null || s.categoryId == null
        ) {
            _state.update {
                it.copy(
                    nameError = name.isEmpty(),
                    principalError = principal == null,
                    thmError = thm == null,
                    monthsError = months == null,
                    overrideError = !overrideOk,
                    paidBeforeError = paidBefore == null,
                    bankBalanceError = !bankBalanceOk,
                    categoryError = s.categoryId == null,
                )
            }
            return
        }
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val base = original
            val result = runCatching {
                bankLoanRepository.save(
                    BankLoanEntity(
                        id = loanId ?: 0,
                        name = name,
                        bankName = s.bankName.trim(),
                        kind = s.kind,
                        currencyCode = account.currencyCode,
                        principalMinor = principal,
                        thmPercent = thm,
                        termMonths = months,
                        firstDueDate = s.firstDueDate,
                        monthlyPaymentOverrideMinor = override,
                        paidMonthsBefore = paidBefore,
                        bankBalanceMinor = bankBalance,
                        accountId = account.id,
                        categoryId = s.categoryId,
                        subcategoryId = s.subcategoryId,
                        note = s.note.trim(),
                        color = base?.color,
                        icon = base?.icon,
                    )
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
            bankLoanRepository.delete(id)
            _state.update { it.copy(doneId = -1) }
        }
    }

    companion object {
        /** Az alapadatok „Hitel” kategóriájának ikonja (nyelvfüggetlen azonosításhoz). */
        const val LOAN_CATEGORY_ICON = "loan"
        const val BANK_ACCOUNT_ICON = "bank"
        /** Biztonsági határ (100 év). */
        const val MAX_MONTHS = 1200

        fun parseThm(text: String): Double? =
            text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 && it.isFinite() }

        fun parseMonths(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it in 1..MAX_MONTHS }
    }
}
