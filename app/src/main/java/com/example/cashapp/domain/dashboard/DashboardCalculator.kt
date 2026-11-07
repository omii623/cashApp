package com.example.cashapp.domain.dashboard

import com.example.cashapp.domain.money.CurrencyConverter

/** Pénznemenként vegyes összegek alap pénznemre összesítése. */
object DashboardCalculator {

    data class Amount(val currencyCode: String, val amountMinor: Long)

    /**
     * @property totalMinor az átváltható tételek összege alap pénznemben
     * @property missingCurrencies azok a pénznemek, amelyekhez nincs árfolyam (kimaradtak)
     */
    data class Sum(val totalMinor: Long, val missingCurrencies: Set<String>)

    fun sumIn(base: String, amounts: List<Amount>, converter: CurrencyConverter): Sum {
        var total = 0L
        val missing = mutableSetOf<String>()
        for (a in amounts) {
            if (a.amountMinor == 0L) continue // 0-hoz nem kell árfolyam, ne is jelezzük hiányzónak
            val converted = converter.convert(a.amountMinor, a.currencyCode, base)
            if (converted == null) missing += a.currencyCode else total += converted
        }
        return Sum(total, missing)
    }

    /**
     * Pénznemenkénti részösszegek (pl. „820 000 Ft · 650 € · …” sorhoz).
     * Minden pénznem benne van, amelyikhez van tétel a listában – a 0 is.
     */
    fun groupByCurrency(amounts: List<Amount>): Map<String, Long> =
        amounts.groupBy { it.currencyCode }
            .mapValues { (_, list) -> list.sumOf { it.amountMinor } }
}
