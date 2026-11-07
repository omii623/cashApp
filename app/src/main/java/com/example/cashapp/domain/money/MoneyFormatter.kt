package com.example.cashapp.domain.money

import com.example.cashapp.domain.model.Currency
import kotlin.math.absoluteValue

/**
 * Magyar formátumú pénzösszeg: ezres tagolás nem törhető szóközzel, tizedesvessző,
 * a jel az összeg után. Pl. `1 245 300 Ft`, `250,63 €`, `−6 000 din.`
 */
object MoneyFormatter {

    private const val NBSP = ' '
    private const val MINUS = '−'

    fun format(amountMinor: Long, currency: Currency, showPlusSign: Boolean = false): String =
        formatNumber(amountMinor, currency.decimals, showPlusSign) + NBSP + currency.symbol

    /** Csak a szám, pénznemjel nélkül (pl. kártyákon, ahol a pénznem máshol látszik). */
    fun formatNumber(amountMinor: Long, decimals: Int, showPlusSign: Boolean = false): String {
        val abs = amountMinor.absoluteValue
        val factor = pow10(decimals)
        val whole = abs / factor
        val fraction = abs % factor

        val sb = StringBuilder()
        when {
            amountMinor < 0 -> sb.append(MINUS)
            showPlusSign && amountMinor > 0 -> sb.append('+')
        }
        sb.append(groupThousands(whole))
        if (decimals > 0) {
            sb.append(',').append(fraction.toString().padStart(decimals, '0'))
        }
        return sb.toString()
    }

    private fun groupThousands(value: Long): String {
        val digits = value.toString()
        val sb = StringBuilder()
        digits.forEachIndexed { i, c ->
            if (i > 0 && (digits.length - i) % 3 == 0) sb.append(NBSP)
            sb.append(c)
        }
        return sb.toString()
    }

    fun pow10(n: Int): Long {
        var r = 1L
        repeat(n) { r *= 10 }
        return r
    }
}
