package com.example.cashapp.domain.money

import com.example.cashapp.domain.model.Currency
import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyFormatterTest {
    private val huf = Currency("HUF", "Forint", "Ft", 0)
    private val eur = Currency("EUR", "Euró", "€", 2)
    private val nb = ' '

    @Test fun `forint ezres tagolással`() =
        assertEquals("1${nb}245${nb}300${nb}Ft", MoneyFormatter.format(1_245_300, huf))

    @Test fun `euro tizedesvesszővel`() =
        assertEquals("250,63${nb}€", MoneyFormatter.format(25_063, eur))

    @Test fun `euro kis összeg vezető nullával`() =
        assertEquals("0,05${nb}€", MoneyFormatter.format(5, eur))

    @Test fun `negatív összeg mínusz jellel`() =
        assertEquals("−12${nb}500${nb}Ft", MoneyFormatter.format(-12_500, huf))

    @Test fun `pozitív előjel kérésre`() =
        assertEquals("+620${nb}000", MoneyFormatter.formatNumber(620_000, 0, showPlusSign = true))

    @Test fun `nulla előjel nélkül`() =
        assertEquals("0", MoneyFormatter.formatNumber(0, 0, showPlusSign = true))

    @Test fun `négyjegyű szám is tagolt`() =
        assertEquals("8${nb}990", MoneyFormatter.formatNumber(8_990, 0))
}
