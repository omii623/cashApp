package com.example.cashapp.domain.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AmountParserTest {
    @Test fun `forint szóközzel`() = assertEquals(12_500L, AmountParser.parseAmount("12 500", 0))
    @Test fun `forint ponttal tagolva`() = assertEquals(12_500L, AmountParser.parseAmount("12.500", 0))
    @Test fun `euro vesszővel`() = assertEquals(25_063L, AmountParser.parseAmount("250,63", 2))
    @Test fun `euro ponttal`() = assertEquals(25_063L, AmountParser.parseAmount("250.63", 2))
    @Test fun `euro egész`() = assertEquals(25_000L, AmountParser.parseAmount("250", 2))
    @Test fun `euro egy tizedes`() = assertEquals(25_050L, AmountParser.parseAmount("250,5", 2))
    @Test fun `túl sok tizedes`() = assertNull(AmountParser.parseAmount("1,234", 2))
    @Test fun `nulla érvénytelen`() = assertNull(AmountParser.parseAmount("0", 0))
    @Test fun `negatív érvénytelen`() = assertNull(AmountParser.parseAmount("-5", 0))
    @Test fun `szöveg érvénytelen`() = assertNull(AmountParser.parseAmount("abc", 0))
    @Test fun `üres érvénytelen`() = assertNull(AmountParser.parseAmount("  ", 0))
    @Test fun `két tizedesjel érvénytelen`() = assertNull(AmountParser.parseAmount("1,2,3", 2))
    @Test fun `túl nagy szám érvénytelen, nem omlik össze`() = assertNull(AmountParser.parseAmount("99999999999999999999", 0))
    @Test fun `túl nagy szám tizedessel érvénytelen`() = assertNull(AmountParser.parseAmount("999999999999999999", 2))
    @Test fun `tudományos alak érvénytelen`() = assertNull(AmountParser.parseAmount("1e30", 0))
    @Test fun `előjeles túl nagy érvénytelen`() = assertNull(AmountParser.parseSignedAmount("-99999999999999999999", 0))

    @Test fun `előjeles üres nulla`() = assertEquals(0L, AmountParser.parseSignedAmount("", 0))
    @Test fun `előjeles nulla`() = assertEquals(0L, AmountParser.parseSignedAmount("0,00", 2))
    @Test fun `előjeles negatív`() = assertEquals(-150_000L, AmountParser.parseSignedAmount("-150 000", 0))
    @Test fun `előjeles unicode mínusz`() = assertEquals(-2_550L, AmountParser.parseSignedAmount("−25,50", 2))
    @Test fun `előjeles pozitív`() = assertEquals(12_500L, AmountParser.parseSignedAmount("12500", 0))
    @Test fun `előjeles szöveg érvénytelen`() = assertNull(AmountParser.parseSignedAmount("abc", 0))

    @Test fun `árfolyam vesszővel`() = assertEquals(399.5, AmountParser.parseRate("399,5")!!, 1e-9)
    @Test fun `árfolyam nulla érvénytelen`() = assertNull(AmountParser.parseRate("0"))

    @Test fun `szerkeszthető szöveg`() = assertEquals("250,63", AmountParser.toEditText(25_063, 2))
    @Test fun `árfolyam szöveg`() = assertEquals("401,2", AmountParser.rateToEditText(401.2))

    @Test fun `átváltás kézi árfolyammal`() =
        assertEquals(3_990_000L, CurrencyConverter.convertWithRate(1_000_000, 2, 0, 399.0)) // 10 000 € → 3 990 000 Ft
}
