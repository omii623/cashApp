package com.example.cashapp.domain.report

import com.example.cashapp.domain.dashboard.DashboardCalculator
import com.example.cashapp.domain.dashboard.DashboardCalculator.Amount
import com.example.cashapp.domain.model.TransactionType
import com.example.cashapp.domain.money.CurrencyConverter
import java.time.LocalDate
import java.time.YearMonth

/** Kimutatások számításai (alap pénznemben). Androidtól független, unit tesztelt. */
object ReportCalculator {

    /** A kategória-bontásban a magánkölcsön-mozgások csoportjának azonosítója (valódi kategória id-ja sosem negatív). */
    const val LOAN_GROUP_ID = -1L

    /** Egy kategória (vagy alkategória) összege egy pénznemben. `id` = null → nincs besorolva. */
    data class GroupAmount(val id: Long?, val currencyCode: String, val amountMinor: Long)

    /** Egy szelet: [id] = null az „Egyéb” (összevont) szelet. */
    data class Slice(val id: Long?, val amountMinor: Long, val share: Float, val isOther: Boolean)

    data class Breakdown(val totalMinor: Long, val slices: List<Slice>, val missingCurrencies: Set<String>)

    /**
     * Kategóriánkénti bontás csökkenő sorrendben. Legfeljebb [maxSlices] szelet marad
     * külön; a többi egy „Egyéb” szeletbe kerül (a kördiagram így olvasható marad).
     */
    fun breakdown(
        amounts: List<GroupAmount>,
        base: String,
        converter: CurrencyConverter,
        maxSlices: Int = 6,
    ): Breakdown {
        val missing = mutableSetOf<String>()
        val byId = amounts.groupBy { it.id }.mapValues { (_, list) ->
            val sum = DashboardCalculator.sumIn(base, list.map { Amount(it.currencyCode, it.amountMinor) }, converter)
            missing += sum.missingCurrencies
            sum.totalMinor
        }.filterValues { it > 0 }

        val total = byId.values.sum()
        if (total == 0L) return Breakdown(0, emptyList(), missing)

        val sorted = byId.entries.sortedByDescending { it.value }
        // Ha csak eggyel lenne több a limitnél, felesleges „Egyéb”-et csinálni belőle.
        val keep = if (sorted.size <= maxSlices) sorted.size else maxSlices - 1
        val slices = sorted.take(keep).map { (id, v) -> Slice(id, v, v.toFloat() / total, isOther = false) }.toMutableList()
        val rest = sorted.drop(keep).sumOf { it.value }
        if (rest > 0) slices += Slice(null, rest, rest.toFloat() / total, isOther = true)
        return Breakdown(total, slices, missing)
    }

    /** Egy tétel a havi sorozathoz (a számla pénznemében). */
    data class Entry(val date: LocalDate, val type: TransactionType, val amountMinor: Long, val currencyCode: String)

    data class MonthTotals(val month: YearMonth, val incomeMinor: Long, val expenseMinor: Long)

    /** Havi bevétel/kiadás a megadott hónapokra (üres hónap is szerepel 0-val). Váltás nem számít. */
    fun monthly(entries: List<Entry>, months: List<YearMonth>, base: String, converter: CurrencyConverter): List<MonthTotals> {
        val byMonth = entries.groupBy { YearMonth.from(it.date) }
        return months.map { m ->
            val list = byMonth[m].orEmpty()
            fun sum(type: TransactionType) = DashboardCalculator.sumIn(
                base, list.filter { it.type == type }.map { Amount(it.currencyCode, it.amountMinor) }, converter,
            ).totalMinor
            MonthTotals(m, sum(TransactionType.INCOME), sum(TransactionType.EXPENSE))
        }
    }
}
