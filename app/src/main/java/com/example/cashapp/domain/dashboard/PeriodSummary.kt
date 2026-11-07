package com.example.cashapp.domain.dashboard

import com.example.cashapp.domain.dashboard.DashboardCalculator.Amount
import com.example.cashapp.domain.model.TransactionType
import com.example.cashapp.domain.money.CurrencyConverter
import java.time.LocalDate

/**
 * Bevétel / kiadás összesítés alap pénznemben (a Tranzakciók lap napjaihoz és fejlécéhez).
 * A váltás nem számít bele (csak pénz mozog a számlák között).
 */
object PeriodSummary {

    /** Egy tétel az összesítéshez: a számla pénznemében rögzített összeg. */
    data class Entry(
        val date: LocalDate,
        val type: TransactionType,
        val accountAmountMinor: Long,
        val accountCurrencyCode: String,
    )

    data class Totals(val incomeMinor: Long, val expenseMinor: Long) {
        val netMinor: Long get() = incomeMinor - expenseMinor
    }

    fun totals(entries: List<Entry>, base: String, converter: CurrencyConverter): Totals {
        fun sum(type: TransactionType) = DashboardCalculator.sumIn(
            base,
            entries.filter { it.type == type }.map { Amount(it.accountCurrencyCode, it.accountAmountMinor) },
            converter,
        ).totalMinor
        return Totals(sum(TransactionType.INCOME), sum(TransactionType.EXPENSE))
    }

    /** Napi összesítés, a bejövő sorrendet (legújabb elöl) megtartva. */
    fun byDay(entries: List<Entry>, base: String, converter: CurrencyConverter): Map<LocalDate, Totals> =
        entries.groupBy { it.date }.mapValues { (_, list) -> totals(list, base, converter) }
}
