package com.example.cashapp.ui.recurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cashapp.data.local.entity.AccountEntity
import com.example.cashapp.data.local.entity.CategoryEntity
import com.example.cashapp.data.local.entity.RecurringRuleEntity
import com.example.cashapp.data.local.entity.SubcategoryEntity
import com.example.cashapp.data.repository.AccountRepository
import com.example.cashapp.data.repository.CategoryRepository
import com.example.cashapp.data.repository.CurrencyRepository
import com.example.cashapp.data.repository.RecurringRepository
import com.example.cashapp.domain.model.CategoryType
import com.example.cashapp.domain.model.Currency
import com.example.cashapp.domain.model.RecurrenceFrequency
import com.example.cashapp.domain.model.TransactionType
import com.example.cashapp.domain.money.AmountParser
import com.example.cashapp.ui.transaction.AccountOption
import com.example.cashapp.ui.transaction.CategoryOption
import com.example.cashapp.ui.transaction.SubcategoryOption
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RuleEditUiState(
    val isLoading: Boolean = true,
    val isNew: Boolean = true,
    val name: String = "",
    val type: TransactionType = TransactionType.EXPENSE,
    val amountText: String = "",
    /** A kiválasztott számla pénzneme – a szabály mindig ebben van. */
    val currency: Currency? = null,
    val accounts: List<AccountOption> = emptyList(),
    val account: AccountOption? = null,
    val categories: List<CategoryOption> = emptyList(),
    val selectedCategoryId: Long? = null,
    val subcategories: List<SubcategoryOption> = emptyList(),
    val selectedSubcategoryId: Long? = null,
    val frequency: RecurrenceFrequency = RecurrenceFrequency.MONTHLY,
    val intervalText: String = "1",
    val startDate: LocalDate = LocalDate.now(),
    val hasEnd: Boolean = false,
    val endDate: LocalDate = LocalDate.now().plusYears(1),
    val note: String = "",
    val isActive: Boolean = true,
    val nameError: Boolean = false,
    val amountError: Boolean = false,
    val categoryError: Boolean = false,
    val intervalError: Boolean = false,
    val endError: Boolean = false,
    val isSaving: Boolean = false,
    val isDone: Boolean = false,
    /** A mentés hibára futott (a képernyő nyitva marad, hibaüzenettel). */
    val saveFailed: Boolean = false,
) {
    /** Érvényes időköz (az előnézethez); hibás bevitelnél 1. */
    val interval: Int get() = intervalText.toIntOrNull()?.takeIf { it in 1..999 } ?: 1
}

@OptIn(ExperimentalCoroutinesApi::class)
class RuleEditViewModel(
    private val editId: Long?,
    private val recurringRepository: RecurringRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    currencyRepository: CurrencyRepository,
) : ViewModel() {

    /** A felhasználó által szerkesztett mezők (a listák a [data]-ból jönnek). */
    private data class Input(
        val loaded: Boolean,
        val name: String = "",
        val type: TransactionType = TransactionType.EXPENSE,
        val amountText: String = "",
        val accountId: Long? = null,
        val categoryId: Long? = null,
        val subcategoryId: Long? = null,
        val frequency: RecurrenceFrequency = RecurrenceFrequency.MONTHLY,
        val intervalText: String = "1",
        val startDate: LocalDate = LocalDate.now(),
        val hasEnd: Boolean = false,
        val endDate: LocalDate = LocalDate.now().plusYears(1),
        val note: String = "",
        val isActive: Boolean = true,
        val showErrors: Boolean = false,
        val isSaving: Boolean = false,
        val isDone: Boolean = false,
        val saveFailed: Boolean = false,
    )

    private val input = MutableStateFlow(Input(loaded = editId == null))

    /** A szerkesztett szabály eredeti (esetleg archivált) számlája / kategóriája / alkategóriája. */
    private data class Pinned(
        val account: AccountEntity? = null,
        val category: CategoryEntity? = null,
        val subcategory: SubcategoryEntity? = null,
    )

    private val pinned = MutableStateFlow(Pinned())

    private data class Data(
        val accounts: List<AccountEntity>,
        val currencies: Map<String, Currency>,
        val categories: List<CategoryEntity>,
        val subcategories: List<SubcategoryEntity>,
    )

    private val categoryType = input.map {
        if (it.type == TransactionType.INCOME) CategoryType.INCOME else CategoryType.EXPENSE
    }.distinctUntilChanged()

    private val data = combine(
        accountRepository.observeActive(),
        currencyRepository.observeCurrencies(),
        categoryType.flatMapLatest { categoryRepository.observeCategories(it) },
        categoryType.flatMapLatest { categoryRepository.observeSubcategories(it) },
        ::Data,
    )

    val uiState: StateFlow<RuleEditUiState> = combine(input, data, pinned) { i, d, p -> buildState(i, d.withPinned(p, i.type)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RuleEditUiState(isNew = editId == null))

    init {
        if (editId != null) viewModelScope.launch {
            val rule = recurringRepository.getRule(editId) ?: run { input.update { it.copy(isDone = true) }; return@launch }
            // Archivált számla/kategória is maradjon kiválasztva – különben mentéskor csendben lecserélődne.
            pinned.value = Pinned(
                account = accountRepository.get(rule.accountId),
                category = rule.categoryId?.let { categoryRepository.getCategory(it) },
                subcategory = rule.subcategoryId?.let { categoryRepository.getSubcategory(it) },
            )
            val decimals = currencyRepository.observeCurrencies().first()[rule.currencyCode]?.decimals ?: 0
            input.value = Input(
                loaded = true,
                name = rule.name,
                type = rule.type,
                amountText = AmountParser.toEditText(rule.amountMinor, decimals),
                accountId = rule.accountId,
                categoryId = rule.categoryId,
                subcategoryId = rule.subcategoryId,
                frequency = rule.frequency,
                intervalText = rule.interval.toString(),
                startDate = rule.startDate,
                hasEnd = rule.endDate != null,
                endDate = rule.endDate ?: rule.startDate.plusYears(1),
                note = rule.note,
                isActive = rule.isActive,
            )
        }
    }

    // ------------------------------------------------------------------ események

    fun onName(text: String) = input.update { it.copy(name = text) }
    fun onType(type: TransactionType) = input.update {
        if (it.type == type) it else it.copy(type = type, categoryId = null, subcategoryId = null)
    }
    fun onAmount(text: String) = input.update { it.copy(amountText = text) }
    fun onAccount(id: Long) = input.update { it.copy(accountId = id) }
    fun onCategory(id: Long) = input.update { if (it.categoryId == id) it else it.copy(categoryId = id, subcategoryId = null) }
    fun onSubcategory(id: Long) = input.update { it.copy(subcategoryId = if (it.subcategoryId == id) null else id) }
    fun onFrequency(f: RecurrenceFrequency) = input.update { it.copy(frequency = f) }
    fun onInterval(text: String) = input.update { it.copy(intervalText = text.filter(Char::isDigit).take(3)) }
    fun onStart(date: LocalDate) = input.update { it.copy(startDate = date) }
    fun onHasEnd(has: Boolean) = input.update { it.copy(hasEnd = has) }
    fun onEnd(date: LocalDate) = input.update { it.copy(endDate = date) }
    fun onNote(text: String) = input.update { it.copy(note = text) }
    fun onActive(active: Boolean) = input.update { it.copy(isActive = active) }

    fun save() {
        val s = uiState.value
        val i = input.value
        val decimals = s.currency?.decimals ?: 0
        val amount = AmountParser.parseAmount(s.amountText, decimals)
        val interval = s.intervalText.toIntOrNull()?.takeIf { it in 1..999 }
        val account = s.account
        val valid = s.name.isNotBlank() && amount != null && s.selectedCategoryId != null && interval != null &&
            account != null && !(s.hasEnd && s.endDate.isBefore(s.startDate))
        if (!valid || i.isSaving) {
            input.update { it.copy(showErrors = true) }
            return
        }
        input.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val ok = runCatching { recurringRepository.saveRule(
                RecurringRuleEntity(
                    id = editId ?: 0,
                    name = s.name.trim(),
                    type = s.type,
                    amountMinor = amount!!,
                    currencyCode = account!!.currencyCode,
                    accountId = account.id,
                    categoryId = s.selectedCategoryId!!,
                    subcategoryId = s.selectedSubcategoryId,
                    note = s.note.trim(),
                    frequency = s.frequency,
                    interval = interval!!,
                    startDate = s.startDate,
                    endDate = if (s.hasEnd) s.endDate else null,
                    nextDueDate = s.startDate, // a repository számolja újra
                    isActive = s.isActive,
                )
            ) }.isSuccess
            input.update { it.copy(isSaving = false, isDone = ok, saveFailed = !ok) }
        }
    }

    fun dismissSaveError() = input.update { it.copy(saveFailed = false) }

    fun delete() {
        val id = editId ?: return
        viewModelScope.launch {
            recurringRepository.deleteRule(id)
            input.update { it.copy(isDone = true) }
        }
    }

    // ------------------------------------------------------------------ állapot

    private fun buildState(i: Input, d: Data): RuleEditUiState {
        if (!i.loaded || d.accounts.isEmpty()) return RuleEditUiState(isNew = editId == null, isDone = i.isDone)
        val account = d.accounts.firstOrNull { it.id == i.accountId } ?: d.accounts.first()
        val currency = d.currencies[account.currencyCode]
        val category = d.categories.firstOrNull { it.id == i.categoryId }
        val subs = d.subcategories.filter { it.categoryId == category?.id }
        return RuleEditUiState(
            isLoading = false,
            isNew = editId == null,
            name = i.name,
            type = i.type,
            amountText = i.amountText,
            currency = currency,
            accounts = d.accounts.map { AccountOption(it.id, it.name, it.currencyCode) },
            account = AccountOption(account.id, account.name, account.currencyCode),
            categories = d.categories.map { CategoryOption(it.id, it.name, it.color, it.icon) },
            selectedCategoryId = category?.id,
            subcategories = subs.map { SubcategoryOption(it.id, it.name) },
            selectedSubcategoryId = i.subcategoryId?.takeIf { id -> subs.any { it.id == id } },
            frequency = i.frequency,
            intervalText = i.intervalText,
            startDate = i.startDate,
            hasEnd = i.hasEnd,
            endDate = i.endDate,
            note = i.note,
            isActive = i.isActive,
            nameError = i.showErrors && i.name.isBlank(),
            amountError = i.showErrors && AmountParser.parseAmount(i.amountText, currency?.decimals ?: 0) == null,
            categoryError = i.showErrors && category == null,
            intervalError = i.showErrors && i.intervalText.toIntOrNull()?.takeIf { it in 1..999 } == null,
            endError = i.showErrors && i.hasEnd && i.endDate.isBefore(i.startDate),
            isSaving = i.isSaving,
            isDone = i.isDone,
            saveFailed = i.saveFailed,
        )
    }

    private fun Data.withPinned(p: Pinned, type: TransactionType): Data {
        val catType = if (type == TransactionType.INCOME) CategoryType.INCOME else CategoryType.EXPENSE
        val category = p.category?.takeIf { it.type == catType }
        return copy(
            accounts = accounts.plusIfMissing(p.account) { it.id },
            categories = categories.plusIfMissing(category) { it.id },
            subcategories = subcategories.plusIfMissing(p.subcategory?.takeIf { category != null }) { it.id },
        )
    }

    private fun <T> List<T>.plusIfMissing(item: T?, id: (T) -> Long): List<T> =
        if (item == null || any { id(it) == id(item) }) this else this + item
}
