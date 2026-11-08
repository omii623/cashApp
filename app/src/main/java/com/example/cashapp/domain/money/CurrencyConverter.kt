package com.example.cashapp.domain.money

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Árfolyam-tábla alapján vált pénznemek között.
 *
 * A tárolt árfolyam jelentése: 1 egység [ExchangeRate.base] = [ExchangeRate.rate] egység [ExchangeRate.quote].
 * Ha nincs közvetlen pár, a fordított irányt (1/árfolyam), végül egy közbenső pénznemen
 * (pl. EUR) keresztüli keresztárfolyamot használja.
 *
 * A közbenső pénznem választása determinisztikus: előbb a [preferredPivots] sorrendjében,
 * majd a többi kód ábécérendjében próbálja.
 */
class CurrencyConverter(
    rates: List<ExchangeRate>,
    preferredPivots: List<String> = listOf("EUR"),
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

    private val pivots: List<String> = run {
        val codes = direct.keys.flatMap { listOf(it.first, it.second) }.toSortedSet()
        (preferredPivots.filter { it in codes } + codes).distinct()
    }

    /** 1 egység [from] hány egység [to]; `null`, ha nem számolható. */
    fun rate(from: String, to: String): Double? {
        if (from == to) return 1.0
        direct[from to to]?.let { return it }
        for (pivot in pivots) {
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
        private val LONG_MIN = BigDecimal.valueOf(Long.MIN_VALUE)
        private val LONG_MAX = BigDecimal.valueOf(Long.MAX_VALUE)

        /**
         * Átváltás adott árfolyammal (pl. kézzel megadott): 1 forrás egység = [rate] cél egység.
         * Pontos tizedes számolás (BigDecimal), fél-felfelé kerekítés; a Long tartományon
         * kívüli eredményt a határra vágja (mint korábban a roundToLong).
         */
        fun convertWithRate(amountMinor: Long, fromDecimals: Int, toDecimals: Int, rate: Double): Long {
            require(!rate.isNaN() && !rate.isInfinite()) { "Érvénytelen árfolyam: $rate" }
            val result = BigDecimal.valueOf(amountMinor)
                .multiply(BigDecimal.valueOf(rate))
                .movePointRight(toDecimals - fromDecimals)
                .setScale(0, RoundingMode.HALF_UP)
            return when {
                result < LONG_MIN -> Long.MIN_VALUE
                result > LONG_MAX -> Long.MAX_VALUE
                else -> result.longValueExact()
            }
        }
    }
}
