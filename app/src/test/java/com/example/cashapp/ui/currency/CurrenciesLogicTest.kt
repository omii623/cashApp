package com.example.cashapp.ui.currency

import com.example.cashapp.domain.model.Currency
import com.example.cashapp.domain.money.CurrencyConverter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CurrenciesLogicTest {
    private val huf = Currency("HUF", "Forint", "Ft", 0)
    private val rsd = Currency("RSD", "Dinár", "din.", 0)
    private val eur = Currency("EUR", "Euró", "€", 2)
    private val usd = Currency("USD", "Dollár", "$", 2)
    private val chf = Currency("CHF", "Frank", "CHF", 2)
    private val currencies = listOf(huf, rsd, eur, usd)
    private val decimals = (currencies + chf).associate { it.code to it.decimals }

    private fun converter(vararg quotes: Pair<String, Double>) = CurrencyConverter(
        quotes.map { (code, rate) -> CurrencyConverter.ExchangeRate("EUR", code, rate) },
    ) { decimals[it] ?: 2 }

    private val conv = converter("HUF" to 400.0, "RSD" to 117.0, "USD" to 1.25)

    @Test fun `árfolyamok az alap pénznemhez, drágább balra`() {
        val lines = CurrenciesViewModel.rateLines(currencies, "HUF", conv).associateBy { it.code }
        assertEquals(3, lines.size)
        assertEquals("1 EUR = 400 HUF", lines.getValue("EUR").let { "1 ${it.leftCode} = ${it.text} ${it.rightCode}" })
        assertEquals("320", lines.getValue("USD").text)
        assertEquals("RSD", lines.getValue("RSD").leftCode) // 1 RSD = 3,4188 HUF
    }

    @Test fun `EUR alapnál a forint jobbra kerül`() {
        val line = CurrenciesViewModel.rateLines(currencies, "EUR", conv).single { it.code == "HUF" }
        assertEquals("EUR", line.leftCode)
        assertEquals("HUF", line.rightCode)
    }

    @Test fun `árfolyam nélküli pénznem`() {
        val line = CurrenciesViewModel.rateLines(currencies + chf, "HUF", conv).single { it.code == "CHF" }
        assertNull(line.text)
    }

    @Test fun `átváltó minden más pénznemre`() {
        val result = CurrenciesViewModel.convertAll("100", eur, currencies, conv).associate { it.currency.code to it.text }
        assertEquals(setOf("HUF", "RSD", "USD"), result.keys)
        assertTrue(result.getValue("HUF")!!.startsWith("40"))   // 40 000 Ft
        assertTrue(result.getValue("USD")!!.contains("125"))     // 125,00 $
    }

    @Test fun `átváltó érvénytelen összegre üres`() {
        assertTrue(CurrenciesViewModel.convertAll("", eur, currencies, conv).isEmpty())
        assertTrue(CurrenciesViewModel.convertAll("abc", eur, currencies, conv).isEmpty())
    }
}

class CurrencyFilterTest {
    private fun ok(code: String) = isCurrentCurrency(java.util.Currency.getInstance(code))

    @Test fun `élő pénznemek megmaradnak`() = listOf("CHF", "GBP", "PLN", "CZK", "JPY", "BAM").forEach { assertTrue(it, ok(it)) }

    @Test fun `megszűnt és nem valódi pénznemek kiesnek`() =
        listOf("AFA", "ADP", "DEM", "HRK", "XAU", "XDR").forEach { org.junit.Assert.assertFalse(it, ok(it)) }
}
