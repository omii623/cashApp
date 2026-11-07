package com.example.cashapp.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cashapp.data.local.model.AccountWithBalance
import com.example.cashapp.data.local.model.TransactionListItem
import com.example.cashapp.data.local.model.TypeCurrencyTotal
import com.example.cashapp.data.repository.AccountRepository
import com.example.cashapp.data.repository.CurrencyRepository
import com.example.cashapp.data.repository.RecurringRepository
import com.example.cashapp.data.repository.TransactionRepository
import com.example.cashapp.data.settings.SettingsRepository
import com.example.cashapp.domain.dashboard.DashboardCalculator
import com.example.cashapp.domain.dashboard.DashboardCalculator.Amount
import com.example.cashapp.domain.model.Currency
import com.example.cashapp.domain.model.TransactionType
import com.example.cashapp.domain.money.CurrencyConverter
import com.example.cashapp.domain.money.MoneyFormatter
import com.example.cashapp.ui.components.TransactionRowMapper
import java.time.YearMonth
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(
    private val settingsRepository: SettingsRepository,
    currencyRepository: CurrencyRepository,
    accountRepository: AccountRepository,
    transactionRepository: TransactionRepository,
    recurringRepository: RecurringRepository,
) : ViewModel() {

    private val month: YearMonth = YearMonth.now()

    /** Az átváltáshoz szükséges „környezet” egyben. */
    private data class MoneyContext(
        val base: String,
        val currencies: Map<String, Currency>,
        val converter: CurrencyConverter,
        val hideAmounts: Boolean,
    )

    private val moneyContext = combine(
        settingsRepository.baseCurrency,
        currencyRepository.observeCurrencies(),
        currencyRepository.observeConverter(),
        settingsRepository.hideAmounts,
        ::MoneyContext,
    )

    /** Összegek elrejtése / megjelenítése (a teljes egyenleg kártyán lévő szem gomb). */
    fun toggleHideAmounts() {
        viewModelScope.launch { settingsRepository.setHideAmounts(!uiState.value.amountsHidden) }
    }

    val uiState: StateFlow<HomeUiState> = combine(
        moneyContext,
        accountRepository.observeActiveWithBalance(),
        transactionRepository.observeMonthTotals(month),
        transactionRepository.observeRecent(RECENT_LIMIT),
        recurringRepository.observePendingCount(),
    ) { ctx, accounts, totals, recent, pending ->
        buildState(ctx, accounts, totals, recent, pending)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private fun buildState(
        ctx: MoneyContext,
        accounts: List<AccountWithBalance>,
        totals: List<TypeCurrencyTotal>,
        recent: List<TransactionListItem>,
        pendingCount: Int,
    ): HomeUiState {
        // Pénznemek betöltése előtt (első indítás, seed fut) nincs mit mutatni.
        val baseCurrency = ctx.currencies[ctx.base] ?: return HomeUiState(isLoading = true, month = month)
        fun fmt(minor: Long, code: String, plus: Boolean = false): String =
            ctx.currencies[code]?.let { MoneyFormatter.format(minor, it, plus) } ?: "$minor $code"

        val balanceAmounts = accounts.map { Amount(it.account.currencyCode, it.balanceMinor) }
        val total = DashboardCalculator.sumIn(ctx.base, balanceAmounts, ctx.converter)

        val income = DashboardCalculator.sumIn(ctx.base, totals.amountsOf(TransactionType.INCOME), ctx.converter)
        val expense = DashboardCalculator.sumIn(ctx.base, totals.amountsOf(TransactionType.EXPENSE), ctx.converter)
        val net = income.totalMinor - expense.totalMinor

        return HomeUiState(
            isLoading = false,
            month = month,
            baseCurrencyCode = ctx.base,
            totalBalance = MoneyFormatter.format(total.totalMinor, baseCurrency),
            balancesByCurrency = DashboardCalculator.groupByCurrency(balanceAmounts)
                .entries
                .sortedBy { ctx.currencies.keys.indexOf(it.key) }
                .map { (code, minor) -> fmt(minor, code) },
            missingRates = total.missingCurrencies + income.missingCurrencies + expense.missingCurrencies,
            monthIncome = MoneyFormatter.formatNumber(income.totalMinor, baseCurrency.decimals, showPlusSign = true),
            monthExpense = MoneyFormatter.formatNumber(-expense.totalMinor, baseCurrency.decimals),
            monthNet = MoneyFormatter.formatNumber(net, baseCurrency.decimals, showPlusSign = true),
            monthNetNegative = net < 0,
            pendingCount = pendingCount,
            recent = recent.map { TransactionRowMapper.map(it, ctx.currencies) },
        ).let { if (ctx.hideAmounts) it.masked() else it }
    }

    /** Minden pénzösszeg helyére csillagok; a szerkezet (sorok, színek) marad. */
    private fun HomeUiState.masked() = copy(
        amountsHidden = true,
        totalBalance = MASK,
        balancesByCurrency = emptyList(),
        monthIncome = MASK,
        monthExpense = MASK,
        monthNet = MASK,
        monthNetNegative = false,
        recent = recent.map { it.copy(amountText = MASK) },
    )

    private fun List<TypeCurrencyTotal>.amountsOf(type: TransactionType) =
        filter { it.type == type }.map { Amount(it.currencyCode, it.totalMinor) }

    companion object {
        const val RECENT_LIMIT = 5
        const val MASK = "*****"
    }
}
