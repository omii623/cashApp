package com.example.cashapp.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cashapp.data.local.entity.AccountEntity
import com.example.cashapp.data.local.entity.CategoryEntity
import com.example.cashapp.data.local.model.TransactionListItem
import com.example.cashapp.data.repository.AccountRepository
import com.example.cashapp.data.repository.CategoryRepository
import com.example.cashapp.data.repository.CurrencyRepository
import com.example.cashapp.data.repository.TransactionRepository
import com.example.cashapp.data.settings.SettingsRepository
import com.example.cashapp.domain.dashboard.PeriodSummary
import com.example.cashapp.domain.model.CategoryType
import com.example.cashapp.domain.model.Currency
import com.example.cashapp.domain.model.TransactionFilter
import com.example.cashapp.domain.model.TransactionType
import com.example.cashapp.domain.money.CurrencyConverter
import com.example.cashapp.domain.money.MoneyFormatter
import com.example.cashapp.ui.components.TransactionRowMapper
import com.example.cashapp.ui.components.TransactionRowUi
import com.example.cashapp.ui.home.HomeViewModel
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class TransactionsUiState(
    val isLoading: Boolean = true,
    val filter: TransactionFilter = TransactionFilter(),
    val accounts: List<FilterOption> = emptyList(),
    val categories: List<FilterOption> = emptyList(),
    val days: List<DayGroup> = emptyList(),
    /** Az időszak (szűrt) összesítője alap pénznemben. */
    val incomeText: String = "",
    val expenseText: String = "",
    val netText: String = "",
    val netNegative: Boolean = false,
)

data class FilterOption(val id: Long, val label: String)

data class DayGroup(
    val date: LocalDate,
    /** A nap bevétel − kiadás egyenlege, pl. „−15 700 Ft” (váltás nélkül). */
    val netText: String,
    val netNegative: Boolean,
    val rows: List<TransactionRowUi>,
)

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionsViewModel(
    private val transactionRepository: TransactionRepository,
    accountRepository: AccountRepository,
    categoryRepository: CategoryRepository,
    currencyRepository: CurrencyRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val filter = MutableStateFlow(TransactionFilter())

    private data class Context(
        val base: String,
        val currencies: Map<String, Currency>,
        val converter: CurrencyConverter,
        val hidden: Boolean,
    )

    private val context = combine(
        settingsRepository.baseCurrency,
        currencyRepository.observeCurrencies(),
        currencyRepository.observeConverter(),
        settingsRepository.hideAmounts,
        ::Context,
    )

    private val options = combine(
        accountRepository.observeActive(),
        categoryRepository.observeCategories(CategoryType.INCOME),
        categoryRepository.observeCategories(CategoryType.EXPENSE),
    ) { accounts: List<AccountEntity>, income: List<CategoryEntity>, expense: List<CategoryEntity> ->
        accounts.map { FilterOption(it.id, "${it.name} (${it.currencyCode})") } to
            (income + expense).map { FilterOption(it.id, it.name) }
    }

    private val items = filter.flatMapLatest { transactionRepository.observeFiltered(it) }

    val uiState: StateFlow<TransactionsUiState> = combine(filter, items, context, options) { f, list, ctx, opts ->
        buildState(f, list, ctx, opts.first, opts.second)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionsUiState())

    // ------------------------------------------------------------------ szűrők

    fun previousMonth() = filter.update { it.copy(month = it.month.minusMonths(1)) }

    fun nextMonth() = filter.update { it.copy(month = it.month.plusMonths(1)) }

    fun setType(type: TransactionType?) = filter.update {
        // Váltásnak nincs kategóriája → a kategóriaszűrő ilyenkor értelmetlen.
        it.copy(type = type, categoryId = if (type == TransactionType.EXCHANGE) null else it.categoryId)
    }

    fun setAccount(id: Long?) = filter.update { it.copy(accountId = id) }

    fun setCategory(id: Long?) = filter.update { it.copy(categoryId = id) }

    fun setQuery(query: String) = filter.update { it.copy(query = query) }

    fun clearFilters() = filter.update { TransactionFilter(month = it.month) }

    // ------------------------------------------------------------------ állapot

    private fun buildState(
        f: TransactionFilter,
        list: List<TransactionListItem>,
        ctx: Context,
        accounts: List<FilterOption>,
        categories: List<FilterOption>,
    ): TransactionsUiState {
        val base = ctx.currencies[ctx.base]
        fun money(minor: Long, plus: Boolean = false): String = when {
            ctx.hidden -> HomeViewModel.MASK
            base == null -> ""
            else -> MoneyFormatter.format(minor, base, plus)
        }

        val entries = list.map { PeriodSummary.Entry(it.date, it.type, it.accountAmountMinor, it.accountCurrencyCode) }
        val totals = PeriodSummary.totals(entries, ctx.base, ctx.converter)
        val byDay = PeriodSummary.byDay(entries, ctx.base, ctx.converter)

        // A lista már dátum szerint csökkenő sorrendben jön → a csoportok sorrendje is az.
        val days = list.groupBy { it.date }.map { (date, dayItems) ->
            val dayTotals = byDay.getValue(date)
            DayGroup(
                date = date,
                netText = money(dayTotals.netMinor, plus = true),
                netNegative = !ctx.hidden && dayTotals.netMinor < 0,
                rows = dayItems.map { item ->
                    val row = TransactionRowMapper.map(item, ctx.currencies, perspectiveAccountId = f.accountId)
                    if (ctx.hidden) row.copy(amountText = HomeViewModel.MASK) else row
                },
            )
        }
        return TransactionsUiState(
            isLoading = false,
            filter = f,
            accounts = accounts,
            categories = categories,
            days = days,
            incomeText = money(totals.incomeMinor, plus = true),
            expenseText = money(-totals.expenseMinor),
            netText = money(totals.netMinor, plus = true),
            netNegative = !ctx.hidden && totals.netMinor < 0,
        )
    }
}
