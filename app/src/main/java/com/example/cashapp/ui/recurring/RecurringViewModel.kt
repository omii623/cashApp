package com.example.cashapp.ui.recurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cashapp.data.local.model.PendingItem
import com.example.cashapp.data.local.model.RuleItem
import com.example.cashapp.data.repository.CurrencyRepository
import com.example.cashapp.data.repository.RecurringRepository
import com.example.cashapp.data.settings.SettingsRepository
import com.example.cashapp.domain.model.Currency
import com.example.cashapp.domain.model.RecurrenceFrequency
import com.example.cashapp.domain.model.TransactionType
import com.example.cashapp.ui.bankloan.iconKey
import com.example.cashapp.domain.money.MoneyFormatter
import com.example.cashapp.ui.home.HomeViewModel
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RecurringUiState(
    val isLoading: Boolean = true,
    val pending: List<PendingUi> = emptyList(),
    val rules: List<RuleUi> = emptyList(),
)

enum class DueState { TODAY, OVERDUE, POSTPONED }

data class PendingUi(
    val id: Long,
    val type: TransactionType,
    val ruleName: String,
    /** Null: banki hitel részlete (a jelvény a hitelé). */
    val categoryName: String?,
    val subcategoryName: String?,
    /** Null: alapértelmezett (kiadás) szín. */
    val categoryColor: Long?,
    val categoryIcon: String?,
    val accountName: String,
    val amountText: String,
    /** Jóváhagyó ablakhoz: szerkeszthető összeg és a pénznem. */
    val amountMinor: Long,
    val currency: Currency?,
    val date: LocalDate,
    val dueState: DueState,
)

data class RuleUi(
    val id: Long,
    val name: String,
    val type: TransactionType,
    val categoryName: String?,
    val categoryColor: Long?,
    val categoryIcon: String?,
    /** Nem null: a banki hitel szabálya – a hitel oldalán kezelhető, nem a szabályszerkesztőben. */
    val bankLoanId: Long? = null,
    val accountName: String,
    val amountText: String,
    val frequency: RecurrenceFrequency,
    val interval: Int,
    val nextDue: LocalDate,
    val isActive: Boolean,
    /** Lejárt a záró dátum – már nem lesz több esedékesség. */
    val isEnded: Boolean,
)

class RecurringViewModel(
    private val recurringRepository: RecurringRepository,
    currencyRepository: CurrencyRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val uiState: StateFlow<RecurringUiState> = combine(
        recurringRepository.observePending(),
        recurringRepository.observeRules(),
        currencyRepository.observeCurrencies(),
        settingsRepository.hideAmounts,
    ) { pending, rules, currencies, hidden ->
        val today = LocalDate.now()
        RecurringUiState(
            isLoading = false,
            pending = pending.map { it.toUi(currencies, hidden, today) },
            rules = rules.map { it.toUi(currencies, hidden) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecurringUiState())

    fun approve(pendingId: Long, amountMinor: Long, date: LocalDate) {
        viewModelScope.launch { recurringRepository.approve(pendingId, amountMinor, date) }
    }

    fun skip(pendingId: Long) {
        viewModelScope.launch { recurringRepository.skip(pendingId) }
    }

    fun postpone(pendingId: Long, to: LocalDate) {
        viewModelScope.launch { recurringRepository.postpone(pendingId, to) }
    }

    fun setActive(ruleId: Long, active: Boolean) {
        viewModelScope.launch { recurringRepository.setActive(ruleId, active) }
    }

    private fun money(minor: Long, code: String, type: TransactionType, currencies: Map<String, Currency>, hidden: Boolean): String {
        if (hidden) return HomeViewModel.MASK
        val c = currencies[code] ?: return "$minor $code"
        return if (type == TransactionType.INCOME) MoneyFormatter.format(minor, c, showPlusSign = true)
        else MoneyFormatter.format(-minor, c)
    }

    private fun PendingItem.toUi(currencies: Map<String, Currency>, hidden: Boolean, today: LocalDate) = PendingUi(
        id = id,
        type = type,
        ruleName = ruleName,
        categoryName = categoryName,
        subcategoryName = subcategoryName,
        categoryColor = categoryColor ?: bankLoanColor,
        categoryIcon = categoryIcon ?: bankLoanIcon ?: bankLoanKind?.iconKey(),
        accountName = accountName,
        amountText = money(amountMinor, currencyCode, type, currencies, hidden),
        amountMinor = amountMinor,
        currency = currencies[currencyCode],
        date = effectiveDate,
        dueState = when {
            effectiveDate.isAfter(today) -> DueState.POSTPONED
            effectiveDate.isBefore(today) -> DueState.OVERDUE
            else -> DueState.TODAY
        },
    )

    private fun RuleItem.toUi(currencies: Map<String, Currency>, hidden: Boolean) = RuleUi(
        id = rule.id,
        name = rule.name,
        type = rule.type,
        categoryName = categoryName,
        categoryColor = categoryColor ?: bankLoanColor,
        categoryIcon = categoryIcon ?: bankLoanIcon ?: bankLoanKind?.iconKey(),
        bankLoanId = bankLoanId,
        accountName = accountName,
        amountText = money(rule.amountMinor, rule.currencyCode, rule.type, currencies, hidden),
        frequency = rule.frequency,
        interval = rule.interval,
        nextDue = rule.nextDueDate,
        isActive = rule.isActive,
        isEnded = rule.endDate?.let { rule.nextDueDate.isAfter(it) } ?: false,
    )
}
