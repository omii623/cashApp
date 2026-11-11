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
    /** Már futó hitel: a hitelösszeg = jelenlegi hátralék, a futamidő = hátralévő részletek. */
    val running: Boolean = false,
    val principalText: String = "",
    val principalError: Boolean = false,
    val thmText: String = "",
    val thmError: Boolean = false,
    val monthsText: String = "",
    val monthsError: Boolean = false,
    val firstDueDate: LocalDate = LocalDate.now(),
    val originalText: String = "",
    val originalError: Boolean = false,
    val paidBeforeText: String = "",
    val paidBeforeError: Boolean = false,
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
                running = loan.originalPrincipalMinor != null || loan.paidMonthsBefore > 0,
                principalText = AmountParser.toEditText(loan.principalMinor, decimals),
                thmText = formatNumber(loan.thmPercent),
                monthsText = loan.termMonths.toString(),
                firstDueDate = loan.firstDueDate,
                originalText = loan.originalPrincipalMinor?.let { p -> AmountParser.toEditText(p, decimals) }.orEmpty(),
                paidBeforeText = loan.paidMonthsBefore.takeIf { m -> m > 0 }?.toString().orEmpty(),
                overrideText = loan.monthlyPaymentOverrideMinor?.let { p -> AmountParser.toEditText(p, decimals) }.orEmpty(),
                accounts = accountList,
                accountId = loan.accountId,
                categoryId = loan.categoryId,
                subcategoryId = loan.subcategoryId,
                note = loan.note,
            ).withOptions()
        }
    }

    /** Kategória- és alkategória-lista, pénznem és előnézet a jelenlegi mezők alapján. */
    private fun BankLoanEditUiState.withOptions(): BankLoanEditUiState {
        val currency = account?.let { currencies[it.currencyCode] }
        val decimals = currency?.decimals ?: 0
        val principal = AmountParser.parseAmount(principalText, decimals)
        val thm = parseThm(thmText)
        val months = parseMonths(monthsText)
        val override = if (overrideText.isBlank()) null else AmountParser.parseAmount(overrideText, decimals)
        val preview = if (currency != null && principal != null && thm != null && months != null && (overrideText.isBlank() || override != null)) {
            runCatching {
                val schedule = AmortizationSchedule.build(AmortizationSchedule.Params(principal, thm, months, firstDueDate, override))
                val calculated = LoanCalculator.calculate(principal, thm, months).monthlyPaymentMinor
                BankLoanPreview(
                    monthly = MoneyFormatter.format(override ?: calculated, currency),
                    calculatedMonthly = MoneyFormatter.format(calculated, currency),
                    total = MoneyFormatter.format(schedule.totalPaymentMinor, currency),
                    cost = MoneyFormatter.format(schedule.totalPaymentMinor - principal, currency),
                    lastDue = schedule.lastDueDate,
                )
            }.getOrNull()
        } else null
        return copy(
            currency = currency,
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
    fun onRunning(on: Boolean) = edit { it.copy(running = on) }
    fun onPrincipal(text: String) = edit { it.copy(principalText = text, principalError = false, originalError = false) }
    fun onThm(text: String) = edit { it.copy(thmText = text, thmError = false) }
    fun onMonths(text: String) = edit { it.copy(monthsText = text, monthsError = false) }
    fun onFirstDue(date: LocalDate) = edit { it.copy(firstDueDate = date) }
    fun onOriginal(text: String) = edit { it.copy(originalText = text, originalError = false) }
    fun onPaidBefore(text: String) = edit { it.copy(paidBeforeText = text, paidBeforeError = false) }
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
        // Futó hitelnél: eredeti összeg (≥ hátralék) és befizetett hónapok – mindkettő elhagyható.
        val originalValue = if (!s.running || s.originalText.isBlank()) null else AmountParser.parseAmount(s.originalText, decimals)
        val originalOk = !s.running || s.originalText.isBlank() ||
            (originalValue != null && (principal == null || originalValue >= principal))
        val paidBefore = if (!s.running || s.paidBeforeText.isBlank()) 0 else s.paidBeforeText.trim().toIntOrNull()?.takeIf { it >= 0 }
        val account = s.account
        if (name.isEmpty() || principal == null || thm == null || months == null || !overrideOk || !originalOk ||
            paidBefore == null || account == null || s.categoryId == null
        ) {
            _state.update {
                it.copy(
                    nameError = name.isEmpty(),
                    principalError = principal == null,
                    thmError = thm == null,
                    monthsError = months == null,
                    overrideError = !overrideOk,
                    originalError = !originalOk,
                    paidBeforeError = paidBefore == null,
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
                        originalPrincipalMinor = originalValue,
                        paidMonthsBefore = paidBefore,
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
