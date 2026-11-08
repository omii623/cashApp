package com.example.cashapp.domain.money

import com.example.cashapp.domain.dashboard.DashboardCalculator
import com.example.cashapp.domain.dashboard.DashboardCalculator.Amount
import com.example.cashapp.domain.money.CurrencyConverter.ExchangeRate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CurrencyConverterTest {
    private val decimals = mapOf("HUF" to 0, "RSD" to 0, "EUR" to 2, "USD" to 2)

    // EUR alapú árfolyamok, ahogy az API adja.
    private val converter = CurrencyConverter(
        rates = listOf(
            ExchangeRate("EUR", "HUF", 400.0),
            ExchangeRate("EUR", "RSD", 117.0),
            ExchangeRate("EUR", "USD", 1.10),
        ),
        decimalsOf = { decimals.getValue(it) },
    )

    @Test fun `azonos pénznem változatlan`() =
        assertEquals(12_345L, converter.convert(12_345, "HUF", "HUF"))

    @Test fun `közvetlen pár`() =
        assertEquals(40_000L, converter.convert(10_000, "EUR", "HUF")) // 100,00 € → 40 000 Ft

    @Test fun `fordított irány`() =
        assertEquals(25_000L, converter.convert(100_000, "HUF", "EUR")) // 100 000 Ft → 250,00 €

    @Test fun `keresztárfolyam EUR-on át`() =
        assertEquals(11_700L, converter.convert(40_000, "HUF", "RSD")) // 40 000 Ft = 100 € = 11 700 din.

    @Test fun `kézi felülírás erősebb a számolt fordítottnál`() {
        val c = CurrencyConverter(
            listOf(ExchangeRate("EUR", "HUF", 400.0), ExchangeRate("HUF", "EUR", 1 / 399.0)),
            decimalsOf = { decimals.getValue(it) },
        )
        assertEquals(399.0, 1 / c.rate("HUF", "EUR")!!, 1e-9)
        assertEquals(400.0, c.rate("EUR", "HUF")!!, 1e-9)
    }

    @Test fun `hiányzó árfolyam null`() {
        val c = CurrencyConverter(emptyList(), decimalsOf = { decimals.getValue(it) })
        assertNull(c.convert(100, "HUF", "EUR"))
    }

    @Test fun `pénznemenkénti bontásban a 0 is benne van`() {
        val byCurrency = DashboardCalculator.groupByCurrency(
            listOf(Amount("HUF", 100), Amount("HUF", -40), Amount("EUR", 0), Amount("RSD", 0)),
        )
        assertEquals(mapOf("HUF" to 60L, "EUR" to 0L, "RSD" to 0L), byCurrency)
    }

    @Test fun `nulla összegű pénznem nem számít hiányzónak`() {
        val c = CurrencyConverter(emptyList(), decimalsOf = { decimals.getValue(it) })
        val sum = DashboardCalculator.sumIn("HUF", listOf(Amount("HUF", 100), Amount("EUR", 0)), c)
        assertEquals(emptySet<String>(), sum.missingCurrencies)
    }

    @Test fun `összesítés alap pénznemben, hiányzók jelölve`() {
        val c = CurrencyConverter(listOf(ExchangeRate("EUR", "HUF", 400.0)), decimalsOf = { decimals.getValue(it) })
        val sum = DashboardCalculator.sumIn(
            "HUF",
            listOf(Amount("HUF", 820_000), Amount("EUR", 65_000), Amount("RSD", 45_000)),
            c,
        )
        assertEquals(820_000L + 260_000L, sum.totalMinor)
        assertEquals(setOf("RSD"), sum.missingCurrencies)
    }

    @Test fun `keresztárfolyam közbenső pénzneme EUR, a megadás sorrendjétől függetlenül`() {
        // HUF→RSD USD-n és EUR-on át is számolható, eltérő eredménnyel.
        val rates = listOf(
            ExchangeRate("USD", "HUF", 300.0),
            ExchangeRate("USD", "RSD", 100.0),
            ExchangeRate("EUR", "HUF", 400.0),
            ExchangeRate("EUR", "RSD", 117.0),
        )
        listOf(rates, rates.reversed()).forEach { r ->
            val c = CurrencyConverter(r, decimalsOf = { decimals.getValue(it) })
            assertEquals(117.0 / 400.0, c.rate("HUF", "RSD")!!, 1e-12)
        }
    }

    @Test fun `EUR híján ábécérendben az első közbenső pénznem`() {
        val rates = listOf(
            ExchangeRate("USD", "HUF", 300.0),
            ExchangeRate("USD", "RSD", 100.0),
            ExchangeRate("CHF", "HUF", 420.0),
            ExchangeRate("CHF", "RSD", 120.0),
        )
        listOf(rates, rates.reversed()).forEach { r ->
            val c = CurrencyConverter(r, decimalsOf = { 0 }, preferredPivots = emptyList())
            assertEquals(120.0 / 420.0, c.rate("HUF", "RSD")!!, 1e-12)
        }
    }

    @Test fun `megadott előnyben részesített közbenső pénznem`() {
        val c = CurrencyConverter(
            listOf(
                ExchangeRate("USD", "HUF", 300.0),
                ExchangeRate("USD", "RSD", 100.0),
                ExchangeRate("EUR", "HUF", 400.0),
                ExchangeRate("EUR", "RSD", 117.0),
            ),
            decimalsOf = { 0 },
            preferredPivots = listOf("USD"),
        )
        assertEquals(100.0 / 300.0, c.rate("HUF", "RSD")!!, 1e-12)
    }

    @Test fun `kerekítés fél-felfelé, bináris pontatlanság nélkül`() {
        // 1,005 * 100 Double-ben 100,49999…, decimálisan pontosan 100,5 → 101.
        assertEquals(101L, CurrencyConverter.convertWithRate(10_000, 2, 0, 1.005))
        assertEquals(1L, CurrencyConverter.convertWithRate(5, 1, 0, 1.0)) // 0,5 → 1
        assertEquals(-1L, CurrencyConverter.convertWithRate(-5, 1, 0, 1.0)) // -0,5 → -1
        assertEquals(0L, CurrencyConverter.convertWithRate(4, 1, 0, 1.0))
        assertEquals(3_990_000L, CurrencyConverter.convertWithRate(1_000_000, 2, 0, 399.0))
        assertEquals(250_000L, CurrencyConverter.convertWithRate(1_000_000, 0, 2, 0.0025)) // 1 000 000 Ft → 2 500,00 €
    }

    @Test fun `túlcsordulás a Long határára vágva`() {
        assertEquals(Long.MAX_VALUE, CurrencyConverter.convertWithRate(Long.MAX_VALUE, 0, 2, 400.0))
        assertEquals(Long.MIN_VALUE, CurrencyConverter.convertWithRate(Long.MIN_VALUE, 0, 2, 400.0))
        assertEquals(Long.MAX_VALUE, CurrencyConverter.convertWithRate(Long.MAX_VALUE, 0, 0, 1.0))
    }
}
