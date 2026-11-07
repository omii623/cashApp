package com.example.cashapp.domain.money

/**
 * Váltási számítások. A belső árfolyam mindig „1 forrás egység = r cél egység”,
 * de a felhasználó az „1 drágább = X olcsóbb” irányt látja (pl. 1 EUR = 363 HUF).
 */
object ExchangeMath {

    /**
     * Igaz, ha a megjelenítéshez meg kell fordítani a belső árfolyamot.
     * Tárolt árfolyam híján a tizedesjegyek alapján tippel (a HUF/RSD az „olcsóbb”).
     */
    fun isDisplayInverted(rate: Double?, fromDecimals: Int, toDecimals: Int): Boolean =
        if (rate != null && rate > 0) rate < 1 else fromDecimals < toDecimals

    fun toDisplay(rate: Double, inverted: Boolean): Double = if (inverted) 1 / rate else rate

    fun fromDisplay(shown: Double, inverted: Boolean): Double = if (inverted) 1 / shown else shown

    /** A két összegből visszaszámolt belső árfolyam (forrás → cél); `null`, ha nem számolható. */
    fun rateFromAmounts(fromMinor: Long, fromDecimals: Int, toMinor: Long, toDecimals: Int): Double? {
        if (fromMinor <= 0 || toMinor <= 0) return null
        val from = fromMinor.toDouble() / MoneyFormatter.pow10(fromDecimals)
        val to = toMinor.toDouble() / MoneyFormatter.pow10(toDecimals)
        return to / from
    }
}
