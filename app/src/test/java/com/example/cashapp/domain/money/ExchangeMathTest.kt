package com.example.cashapp.domain.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExchangeMathTest {
    @Test fun `HUF → EUR fordítva látszik`() = assertTrue(ExchangeMath.isDisplayInverted(1 / 363.0, 0, 2))
    @Test fun `EUR → HUF egyenesen látszik`() = assertFalse(ExchangeMath.isDisplayInverted(363.0, 2, 0))
    @Test fun `tárolt árfolyam nélkül tizedesből tippel`() = assertTrue(ExchangeMath.isDisplayInverted(null, 0, 2))

    @Test fun `oda-vissza`() {
        val shown = ExchangeMath.toDisplay(1 / 363.0, inverted = true)
        assertEquals(363.0, shown, 1e-9)
        assertEquals(1 / 363.0, ExchangeMath.fromDisplay(shown, inverted = true), 1e-12)
    }

    @Test fun `árfolyam a két összegből`() =
        // 100 000 Ft → 275,48 € ⇒ 1 Ft = 0,0027548 €
        assertEquals(0.0027548, ExchangeMath.rateFromAmounts(100_000, 0, 27_548, 2)!!, 1e-9)

    @Test fun `nulla összegnél nincs árfolyam`() = assertNull(ExchangeMath.rateFromAmounts(0, 0, 100, 2))
}
