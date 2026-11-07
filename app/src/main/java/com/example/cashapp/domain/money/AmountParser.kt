package com.example.cashapp.domain.money

import java.math.BigDecimal
import java.math.RoundingMode

/** Felhasználó által beírt összegek / árfolyamok értelmezése. */
object AmountParser {

    private const val NBSP = ' '
    private const val UNICODE_MINUS = '−'

    /**
     * Összeg → legkisebb egység. `null`, ha érvénytelen, nem pozitív,
     * vagy több tizedesjegy van benne, mint amit a pénznem enged.
     *
     * - Szóközök (sima és nem törhető) elhagyva: „12 500” → 12500
     * - Tizedes nélküli pénznemnél (HUF, RSD) a „.” és „,” ezres tagolásnak számít: „12.500” → 12500
     * - Tizedes pénznemnél a „,” és a „.” is tizedesjel: „250,63” = „250.63”
     */
    fun parseAmount(text: String, decimals: Int): Long? =
        parseUnsigned(text, decimals)?.takeIf { it > 0 }

    /**
     * Előjeles összeg (pl. kezdő egyenleg): üres → 0, a „-” / „−” előjel és a 0 is megengedett.
     * `null`, ha érvénytelen.
     */
    fun parseSignedAmount(text: String, decimals: Int): Long? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return 0
        val negative = trimmed.startsWith('-') || trimmed.startsWith(UNICODE_MINUS)
        val value = parseUnsigned(if (negative) trimmed.drop(1) else trimmed, decimals) ?: return null
        return if (negative) -value else value
    }

    /** Nem negatív összeg (a 0 is) → legkisebb egység; `null`, ha érvénytelen. */
    private fun parseUnsigned(text: String, decimals: Int): Long? {
        val cleaned = text.filterNot { it.isWhitespace() || it == NBSP }
        if (cleaned.isEmpty()) return null
        val normalized = if (decimals == 0) cleaned.replace(".", "").replace(",", "") else cleaned.replace(',', '.')
        if (normalized.count { it == '.' } > 1) return null
        // Csak számjegyek és legfeljebb egy pont (pl. „1e30” tudományos alak nem).
        if (!normalized.all { it.isDigit() || it == '.' }) return null
        val value = normalized.toBigDecimalOrNull() ?: return null
        if (value.stripTrailingZeros().scale() > decimals) return null
        // Túl nagy szám (nem fér el Long-ban) → érvénytelen, nem összeomlás.
        return runCatching { value.movePointRight(decimals).longValueExact() }.getOrNull()
    }

    /** Árfolyam („399,5” vagy „399.5”) → Double; `null`, ha nem pozitív szám. */
    fun parseRate(text: String): Double? =
        text.trim().replace(',', '.').toBigDecimalOrNull()?.takeIf { it.signum() > 0 }?.toDouble()

    /** Szerkeszthető szöveg egy legkisebb egységben tárolt összegből (pl. 25063, 2 → „250,63”). */
    fun toEditText(amountMinor: Long, decimals: Int): String =
        BigDecimal.valueOf(amountMinor, decimals).toPlainString().replace('.', ',')

    /** Árfolyam szerkeszthető szövegként, legfeljebb 4 tizedessel, felesleges nullák nélkül. */
    fun rateToEditText(rate: Double): String =
        BigDecimal.valueOf(rate).setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString().replace('.', ',')
}
