package com.example.cashapp.ui.category

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cashapp.R
import com.example.cashapp.data.local.entity.SubcategoryEntity
import com.example.cashapp.data.repository.CategoryRepository
import com.example.cashapp.data.repository.CurrencyRepository
import com.example.cashapp.data.repository.TransactionRepository
import com.example.cashapp.domain.budget.BudgetCalculator
import com.example.cashapp.domain.dashboard.DashboardCalculator.Amount
import com.example.cashapp.domain.model.CategoryType
import com.example.cashapp.domain.money.MoneyFormatter
import java.time.YearMonth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CategoriesUiState(
    val type: CategoryType = CategoryType.EXPENSE,
    val isLoading: Boolean = true,
    val items: List<CategoryItemUi> = emptyList(),
    /** Egyszeri üzenet (snackbar), pl. „archiválva”. */
    @StringRes val message: Int? = null,
)

data class CategoryItemUi(
    val id: Long,
    val name: String,
    val color: Long,
    val icon: String,
    val subcategories: List<SubcategoryEntity>,
    val budget: BudgetUi?,
)

data class BudgetUi(
    /** Pl. „62 000 / 120 000 Ft” */
    val text: String,
    val progress: Float,
    val level: BudgetCalculator.Level,
)

@OptIn(ExperimentalCoroutinesApi::class)
class CategoriesViewModel(
    private val categoryRepository: CategoryRepository,
    currencyRepository: CurrencyRepository,
    transactionRepository: TransactionRepository,
) : ViewModel() {

    private val type = MutableStateFlow(CategoryType.EXPENSE)
    private val message = MutableStateFlow<Int?>(null)

    private val budgetContext = combine(
        categoryRepository.observeBudgets(),
        transactionRepository.observeMonthExpenseByCategory(YearMonth.now()),
        currencyRepository.observeCurrencies(),
        currencyRepository.observeConverter(),
    ) { budgets, spent, currencies, converter ->
        budgets.associate { b ->
            val status = BudgetCalculator.status(
                limitMinor = b.limitMinor,
                budgetCurrency = b.currencyCode,
                warnThresholdPercent = b.warnThresholdPercent,
                spentByCurrency = spent.filter { it.categoryId == b.categoryId }.map { Amount(it.currencyCode, it.totalMinor) },
                converter = converter,
            )
            val currency = currencies[b.currencyCode]
            val text = if (currency == null) "" else
                MoneyFormatter.formatNumber(status.spentMinor, currency.decimals) + " / " +
                    MoneyFormatter.format(status.limitMinor, currency)
            b.categoryId to BudgetUi(text, status.progress, status.level)
        }
    }

    val uiState: StateFlow<CategoriesUiState> = type.flatMapLatest { t ->
        combine(
            categoryRepository.observeCategories(t),
            categoryRepository.observeSubcategories(t),
            budgetContext,
            message,
        ) { categories, subs, budgets, msg ->
            val subsByCategory = subs.groupBy { it.categoryId }
            CategoriesUiState(
                type = t,
                isLoading = false,
                items = categories.map { c ->
                    CategoryItemUi(c.id, c.name, c.color, c.icon, subsByCategory[c.id].orEmpty(), budgets[c.id])
                },
                message = msg,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoriesUiState())

    fun selectType(t: CategoryType) {
        type.value = t
    }

    /** Kategória mozgatása egy hellyel feljebb (-1) vagy lejjebb (+1). */
    fun move(id: Long, delta: Int) {
        val ids = uiState.value.items.map { it.id }.toMutableList()
        val from = ids.indexOf(id)
        val to = from + delta
        if (from < 0 || to !in ids.indices) return
        ids.add(to, ids.removeAt(from))
        viewModelScope.launch { categoryRepository.reorder(type.value, ids) }
    }

    fun addSubcategory(categoryId: Long, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { categoryRepository.addSubcategory(categoryId, trimmed) }
    }

    fun renameSubcategory(sub: SubcategoryEntity, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed == sub.name) return
        viewModelScope.launch { categoryRepository.renameSubcategory(sub, trimmed) }
    }

    fun deleteSubcategory(sub: SubcategoryEntity) {
        viewModelScope.launch {
            val deleted = categoryRepository.deleteSubcategory(sub)
            if (!deleted) message.value = R.string.cat_archived_subcategory
        }
    }

    fun messageShown() {
        message.value = null
    }
}
