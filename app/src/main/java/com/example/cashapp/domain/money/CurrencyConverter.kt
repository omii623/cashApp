package com.example.cashapp.domain.money

import kotlin.math.roundToLong

/**
 * Árfolyam-tábla alapján vált pénznemek között.
 *
 * A tárolt árfolyam jelentése: 1 egység [ExchangeRate.base] = [ExchangeRate.rate] egység [ExchangeRate.quote].
 * Ha nincs közvetlen pár, a fordított irányt (1/árfolyam), végül egy közbenső pénznemen
 * (pl. EUR) keresztüli keresztárfolyamot használja.
 */
class CurrencyConverter(
    rates: List<ExchangeRate>,
    private val decimalsOf: (String) -> Int,
) {
    data class ExchangeRate(val base: String, val quote: String, val rate: Double)

    private val direct: Map<Pair<String, String>, Double> = buildMap {
        rates.filter { it.rate > 0 }.forEach { r ->
            put(r.base to r.quote, r.rate)
            // A fordított irányt csak akkor töltjük ki, ha nincs rá saját (pl. kézi) érték.
            putIfAbsent(r.quote to r.base, 1.0 / r.rate)
        }
        // Explicit párok felülírják a számolt fordítottakat.
        rates.filter { it.rate > 0 }.forEach { put(it.base to it.quote, it.rate) }
    }

    private val codes: Set<String> = direct.keys.flatMap { listOf(it.first, it.second) }.toSet()

    /** 1 egység [from] hány egység [to]; `null`, ha nem számolható. */
    fun rate(from: String, to: String): Double? {
        if (from == to) return 1.0
        direct[from to to]?.let { return it }
        for (pivot in codes) {
            val a = direct[from to pivot] ?: continue
            val b = direct[pivot to to] ?: continue
            return a * b
        }
        return null
    }

    /** Legkisebb egységben megadott összeg átváltása; `null`, ha nincs árfolyam. */
    fun convert(amountMinor: Long, from: String, to: String): Long? {
        if (from == to) return amountMinor
        val r = rate(from, to) ?: return null
        return convertWithRate(amountMinor, decimalsOf(from), decimalsOf(to), r)
    }

    companion object {
        /** Átváltás adott árfolyammal (pl. kézzel megadott): 1 forrás egység = [rate] cél egység. */
        fun convertWithRate(amountMinor: Long, fromDecimals: Int, toDecimals: Int, rate: Double): Long {
            val major = amountMinor.toDouble() / MoneyFormatter.pow10(fromDecimals)
            return (major * rate * MoneyFormatter.pow10(toDecimals)).roundToLong()
        }
    }
}
