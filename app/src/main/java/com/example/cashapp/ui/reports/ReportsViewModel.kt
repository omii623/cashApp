package com.example.cashapp.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cashapp.data.local.entity.CategoryEntity
import com.example.cashapp.data.local.entity.SubcategoryEntity
import com.example.cashapp.data.repository.CategoryRepository
import com.example.cashapp.data.repository.CurrencyRepository
import com.example.cashapp.data.repository.ReportRepository
import com.example.cashapp.data.settings.SettingsRepository
import com.example.cashapp.domain.model.CategoryType
import com.example.cashapp.domain.model.Currency
import com.example.cashapp.domain.model.TransactionType
import com.example.cashapp.domain.money.CurrencyConverter
import com.example.cashapp.domain.money.MoneyFormatter
import com.example.cashapp.domain.report.ReportCalculator
import com.example.cashapp.ui.home.HomeViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

enum class PeriodMode { WEEK, MONTH, YEAR }

data class ReportsUiState(
    val isLoading: Boolean = true,
    val mode: PeriodMode = PeriodMode.MONTH,
    val from: LocalDate = LocalDate.now(),
    val to: LocalDate = LocalDate.now(),
    val type: TransactionType = TransactionType.EXPENSE,
    /** A kiválasztott (lefúrt) kategória neve, ha alkategória-bontást mutatunk. */
    val drillName: String? = null,
    val drillCategoryId: Long? = null,
    /** A választóban megjelenő kategóriák (a kiválasztott típusból), az „Egyéb”-be kerültek is. */
    val categoryOptions: List<CategoryChoice> = emptyList(),
    val totalText: String = "",
    val slices: List<SliceUi> = emptyList(),
    val missingRates: Set<String> = emptySet(),
    val bars: List<BarUi> = emptyList(),
    /** A legnagyobb oszlop értéke (közös skála a bevételhez és a kiadáshoz). */
    val barMax: Long = 0,
    val selectedBar: Int? = null,
)

data class SliceUi(
    val id: Long?,
    val name: String,
    val color: Long,
    val icon: String?,
    val share: Float,
    val amountText: String,
    val percentText: String,
    val isOther: Boolean,
    /** Kategória (nem „Egyéb”, nem alkategória) → koppintva lefúrható. */
    val canDrill: Boolean,
)

data class CategoryChoice(val id: Long, val name: String, val color: Long, val icon: String)

data class BarUi(
    val month: YearMonth,
    val incomeMinor: Long,
    val expenseMinor: Long,
    val incomeText: String,
    val expenseText: String,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ReportsViewModel(
    private val reportRepository: ReportRepository,
    categoryRepository: CategoryRepository,
    currencyRepository: CurrencyRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private data class Query(
        val mode: PeriodMode = PeriodMode.MONTH,
        val anchor: LocalDate = LocalDate.now(),
        val type: TransactionType = TransactionType.EXPENSE,
        val drillCategoryId: Long? = null,
        val selectedBar: Int? = null,
    ) {
        val range: Pair<LocalDate, LocalDate> get() = when (mode) {
            PeriodMode.WEEK -> anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).let { it to it.plusDays(6) }
            PeriodMode.MONTH -> YearMonth.from(anchor).let { it.atDay(1) to it.atEndOfMonth() }
            PeriodMode.YEAR -> anchor.withDayOfYear(1).let { it to it.withMonth(12).withDayOfMonth(31) }
        }

        /** Az oszlopdiagram hónapjai: évnél az év 12 hónapja, egyébként az utolsó 6 hónap. */
        val barMonths: List<YearMonth> get() = when (mode) {
            PeriodMode.YEAR -> (1..12).map { YearMonth.of(anchor.year, it) }
            else -> YearMonth.from(anchor).let { end -> (5 downTo 0).map { end.minusMonths(it.toLong()) } }
        }
    }

    private val query = MutableStateFlow(Query())

    private data class Ctx(
        val base: String,
        val currencies: Map<String, Currency>,
        val converter: CurrencyConverter,
        val hidden: Boolean,
    )

    private val ctx = combine(
        settingsRepository.baseCurrency,
        currencyRepository.observeCurrencies(),
        currencyRepository.observeConverter(),
        settingsRepository.hideAmounts,
        ::Ctx,
    )

    private val categories: Flow<List<CategoryEntity>> = combine(
        categoryRepository.observeCategories(CategoryType.INCOME),
        categoryRepository.observeCategories(CategoryType.EXPENSE),
    ) { a, b -> a + b }

    private val subcategories: Flow<List<SubcategoryEntity>> = combine(
        categoryRepository.observeSubcategories(CategoryType.INCOME),
        categoryRepository.observeSubcategories(CategoryType.EXPENSE),
    ) { a, b -> a + b }

    /** A kördiagram nyers adatai: kategóriánként, vagy lefúrva alkategóriánként. */
    private val groupAmounts = query.flatMapLatest { q ->
        val (from, to) = q.range
        if (q.drillCategoryId != null) reportRepository.observeBySubcategory(q.drillCategoryId, q.type, from, to)
        else reportRepository.observeByCategory(q.type, from, to)
    }

    private val barEntries = query.flatMapLatest { q ->
        val months = q.barMonths
        if (months.isEmpty()) flowOf(emptyList()) else reportRepository.observeEntries(months.first().atDay(1), months.last().atEndOfMonth())
    }

    val uiState: StateFlow<ReportsUiState> = combine(query, groupAmounts, barEntries, ctx, combine(categories, subcategories, ::Pair)) {
            q, groups, entries, c, (cats, subs) ->
        build(q, groups, entries, c, cats, subs)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportsUiState())

    // ------------------------------------------------------------------ események

    fun setMode(mode: PeriodMode) = query.update { it.copy(mode = mode, selectedBar = null) }

    fun previous() = query.update { it.copy(anchor = shift(it, -1), selectedBar = null) }

    fun next() = query.update { it.copy(anchor = shift(it, +1), selectedBar = null) }

    fun setType(type: TransactionType) = query.update { it.copy(type = type, drillCategoryId = null) }

    fun drillInto(categoryId: Long) = query.update { it.copy(drillCategoryId = categoryId) }

    fun drillUp() = query.update { it.copy(drillCategoryId = null) }

    /** A választóból: null → minden kategória, különben az adott kategória alkategóriái. */
    fun selectCategory(categoryId: Long?) = query.update { it.copy(drillCategoryId = categoryId) }

    /** Oszlop kijelölése (újra koppintva megszűnik). */
    fun selectBar(index: Int) = query.update { it.copy(selectedBar = if (it.selectedBar == index) null else index) }

    private fun shift(q: Query, delta: Long): LocalDate = when (q.mode) {
        PeriodMode.WEEK -> q.anchor.plusWeeks(delta)
        PeriodMode.MONTH -> q.anchor.plusMonths(delta)
        PeriodMode.YEAR -> q.anchor.plusYears(delta)
    }

    // ------------------------------------------------------------------ állapot

    private fun build(
        q: Query,
        groups: List<ReportCalculator.GroupAmount>,
        entries: List<ReportCalculator.Entry>,
        c: Ctx,
        cats: List<CategoryEntity>,
        subs: List<SubcategoryEntity>,
    ): ReportsUiState {
        val base = c.currencies[c.base]
        fun money(minor: Long) = when {
            c.hidden -> HomeViewModel.MASK
            base == null -> ""
            else -> MoneyFormatter.format(minor, base)
        }
        val catById = cats.associateBy { it.id }
        val subById = subs.associateBy { it.id }
        val drillCategory = q.drillCategoryId?.let { catById[it] }

        val breakdown = ReportCalculator.breakdown(groups, c.base, c.converter)
        val slices = breakdown.slices.map { s ->
            val cat = if (drillCategory == null) s.id?.let { catById[it] } else null
            val name = when {
                s.isOther -> null
                drillCategory != null -> s.id?.let { subById[it]?.name }
                else -> cat?.name
            }
            SliceUi(
                id = s.id,
                name = name ?: "",
                // Lefúráskor az alkategóriák a kategória színének árnyalatai lesznek (a UI számolja).
                color = cat?.color ?: drillCategory?.color ?: 0,
                icon = cat?.icon,
                share = s.share,
                amountText = money(s.amountMinor),
                percentText = "${(s.share * 100).let { if (it < 0.5f) "<1" else it.roundToInt().toString() }}%",
                isOther = s.isOther,
                canDrill = drillCategory == null && !s.isOther && s.id != null,
            )
        }

        val monthly = ReportCalculator.monthly(entries, q.barMonths, c.base, c.converter)
        return ReportsUiState(
            isLoading = false,
            mode = q.mode,
            from = q.range.first,
            to = q.range.second,
            type = q.type,
            drillName = drillCategory?.name,
            drillCategoryId = drillCategory?.id,
            categoryOptions = cats
                .filter { it.type == (if (q.type == TransactionType.INCOME) CategoryType.INCOME else CategoryType.EXPENSE) }
                .map { CategoryChoice(it.id, it.name, it.color, it.icon) },
            totalText = money(breakdown.totalMinor),
            slices = slices,
            missingRates = breakdown.missingCurrencies,
            bars = monthly.map { BarUi(it.month, it.incomeMinor, it.expenseMinor, money(it.incomeMinor), money(it.expenseMinor)) },
            barMax = monthly.maxOfOrNull { maxOf(it.incomeMinor, it.expenseMinor) } ?: 0,
            selectedBar = q.selectedBar,
        )
    }
}
