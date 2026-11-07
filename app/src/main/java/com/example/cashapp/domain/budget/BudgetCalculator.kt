package com.example.cashapp.domain.budget

import com.example.cashapp.domain.dashboard.DashboardCalculator
import com.example.cashapp.domain.money.CurrencyConverter

/** Költségkeret állapota egy kategóriára egy hónapban. */
object BudgetCalculator {

    enum class Level { OK, WARNING, OVER }

    data class Status(
        val spentMinor: Long,
        val limitMinor: Long,
        /** 0..1 között a sávhoz (túllépésnél 1). */
        val progress: Float,
        val level: Level,
        /** Volt olyan pénznemű kiadás, amit nem lehetett átváltani – az összeg alulbecsült. */
        val incomplete: Boolean,
    )

    /**
     * @param spentByCurrency a kategória havi kiadásai pénznemenként (legkisebb egységben)
     */
    fun status(
        limitMinor: Long,
        budgetCurrency: String,
        warnThresholdPercent: Int,
        spentByCurrency: List<DashboardCalculator.Amount>,
        converter: CurrencyConverter,
    ): Status {
        val sum = DashboardCalculator.sumIn(budgetCurrency, spentByCurrency, converter)
        val ratio = if (limitMinor > 0) sum.totalMinor.toDouble() / limitMinor else 0.0
        val level = when {
            ratio > 1.0 -> Level.OVER
            ratio * 100 >= warnThresholdPercent -> Level.WARNING
            else -> Level.OK
        }
        return Status(
            spentMinor = sum.totalMinor,
            limitMinor = limitMinor,
            progress = ratio.coerceIn(0.0, 1.0).toFloat(),
            level = level,
            incomplete = sum.missingCurrencies.isNotEmpty(),
        )
    }
}
