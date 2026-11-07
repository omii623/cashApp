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
}
