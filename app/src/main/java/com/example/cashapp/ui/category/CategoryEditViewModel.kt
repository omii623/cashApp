package com.example.cashapp.ui.category

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cashapp.data.repository.CategoryRepository
import com.example.cashapp.data.repository.CategoryRepository.BudgetDraft
import com.example.cashapp.data.repository.CategoryRepository.CategoryDraft
import com.example.cashapp.data.repository.CategoryRepository.SubcategoryDraft
import com.example.cashapp.data.repository.CurrencyRepository
import com.example.cashapp.data.settings.SettingsRepository
import com.example.cashapp.domain.model.CategoryIcon
import com.example.cashapp.domain.model.CategoryType
import com.example.cashapp.domain.model.Currency
import com.example.cashapp.domain.money.AmountParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CategoryEditUiState(
    val isLoading: Boolean = true,
    val isNew: Boolean = true,
    val type: CategoryType = CategoryType.EXPENSE,
    val name: String = "",
    val nameError: Boolean = false,
    val color: Long = CategoryPalette.colors.first(),
    /** Tárolt formában (lásd [CategoryIcon]). */
    val icon: String = "other",
    val subcategories: List<EditableSubcategory> = emptyList(),

    // Költségkeret (csak kiadás kategóriánál)
    val budgetEnabled: Boolean = false,
    val budgetAmountText: String = "",
    val budgetAmountError: Boolean = false,
    val budgetCurrency: String = "",
    val currencies: List<Currency> = emptyList(),
    val budgetThreshold: Int = 80,
    val budgetNotify: Boolean = true,

    val isSaving: Boolean = false,
    /** Mentés / törlés után vissza lehet lépni. */
    val isDone: Boolean = false,
) {
    val supportsBudget: Boolean get() = type == CategoryType.EXPENSE
}

/** Szerkesztés alatti alkategória. [key] csak a képernyőn belüli azonosító; [id] = null → új. */
data class EditableSubcategory(val key: Long, val id: Long?, val name: String)

class CategoryEditViewModel(
    private val categoryId: Long?,
    initialType: CategoryType,
    private val categoryRepository: CategoryRepository,
    private val currencyRepository: CurrencyRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CategoryEditUiState(type = initialType, isNew = categoryId == null))
    val uiState: StateFlow<CategoryEditUiState> = _state.asStateFlow()

    private val removedSubIds = mutableListOf<Long>()
    private var nextKey = 1L

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val currencies = currencyRepository.observeCurrencies().first()
        val base = settingsRepository.baseCurrency.first()
        val loaded = categoryId?.let { categoryRepository.loadForEdit(it) }
        if (loaded == null) {
            _state.update {
                it.copy(isLoading = false, currencies = currencies.values.toList(), budgetCurrency = base)
            }
            return
        }
        val budget = loaded.budget
        val budgetCurrency = budget?.currencyCode ?: base
        _state.update {
            it.copy(
                isLoading = false,
                type = loaded.category.type,
                name = loaded.category.name,
                color = loaded.category.color,
                icon = loaded.category.icon,
                subcategories = loaded.subcategories.map { s -> EditableSubcategory(nextKey++, s.id, s.name) },
                currencies = currencies.values.toList(),
                budgetEnabled = budget != null,
                budgetAmountText = budget?.let { b ->
                    AmountParser.toEditText(b.limitMinor, currencies[b.currencyCode]?.decimals ?: 0)
                } ?: "",
                budgetCurrency = budgetCurrency,
                budgetThreshold = budget?.warnThresholdPercent ?: 80,
                budgetNotify = budget?.notify ?: true,
            )
        }
    }

    // ------------------------------------------------------------------ alap adatok

    fun onNameChange(text: String) = _state.update { it.copy(name = text, nameError = false) }

    fun onColorSelect(color: Long) = _state.update { it.copy(color = color) }

    /** Ikon vagy betű választása. */
    fun onIconSelect(icon: CategoryIcon) = _state.update { it.copy(icon = icon.encode()) }

    /** Csak új kategóriánál választható (meglévőnél a tételek miatt nem váltható). */
    fun onTypeSelect(type: CategoryType) = _state.update { if (it.isNew) it.copy(type = type) else it }

    // ------------------------------------------------------------------ alkategóriák

    fun addSubcategory(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        _state.update { it.copy(subcategories = it.subcategories + EditableSubcategory(nextKey++, null, trimmed)) }
    }

    fun renameSubcategory(key: Long, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        _state.update { s -> s.copy(subcategories = s.subcategories.map { if (it.key == key) it.copy(name = trimmed) else it }) }
    }

    fun removeSubcategory(key: Long) = _state.update { s ->
        s.subcategories.firstOrNull { it.key == key }?.id?.let(removedSubIds::add)
        s.copy(subcategories = s.subcategories.filterNot { it.key == key })
    }

    fun moveSubcategory(key: Long, delta: Int) = _state.update { s ->
        val list = s.subcategories.toMutableList()
        val from = list.indexOfFirst { it.key == key }
        val to = from + delta
        if (from < 0 || to !in list.indices) return@update s
        list.add(to, list.removeAt(from))
        s.copy(subcategories = list)
    }

    // ------------------------------------------------------------------ költségkeret

    fun onBudgetEnabledChange(enabled: Boolean) = _state.update { it.copy(budgetEnabled = enabled, budgetAmountError = false) }

    fun onBudgetAmountChange(text: String) = _state.update { it.copy(budgetAmountText = text, budgetAmountError = false) }

    fun onBudgetCurrencySelect(code: String) = _state.update { it.copy(budgetCurrency = code) }

    fun onBudgetThresholdChange(percent: Int) = _state.update { it.copy(budgetThreshold = percent.coerceIn(10, 100)) }

    fun onBudgetNotifyChange(notify: Boolean) = _state.update { it.copy(budgetNotify = notify) }

    // ------------------------------------------------------------------ mentés / törlés

    fun save() {
        val s = _state.value
        if (s.isSaving || s.isLoading) return
        val name = s.name.trim()
        val decimals = s.currencies.firstOrNull { it.code == s.budgetCurrency }?.decimals ?: 0
        val budgetMinor = if (s.supportsBudget && s.budgetEnabled) AmountParser.parseAmount(s.budgetAmountText, decimals) else null
        val budgetInvalid = s.supportsBudget && s.budgetEnabled && budgetMinor == null
        if (name.isEmpty() || budgetInvalid) {
            _state.update { it.copy(nameError = name.isEmpty(), budgetAmountError = budgetInvalid) }
            return
        }
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            categoryRepository.save(
                CategoryDraft(
                    id = categoryId,
                    name = name,
                    type = s.type,
                    color = s.color,
                    icon = s.icon,
                    subcategories = s.subcategories.map { SubcategoryDraft(it.id, it.name) },
                    removedSubcategoryIds = removedSubIds.toList(),
                    budget = budgetMinor?.let { BudgetDraft(it, s.budgetCurrency, s.budgetThreshold, s.budgetNotify) },
                )
            )
            _state.update { it.copy(isSaving = false, isDone = true) }
        }
    }

    fun delete() {
        val id = categoryId ?: return
        viewModelScope.launch {
            categoryRepository.deleteCategory(id)
            _state.update { it.copy(isDone = true) }
        }
    }
}
