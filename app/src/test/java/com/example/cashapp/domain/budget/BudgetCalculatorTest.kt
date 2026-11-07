package com.example.cashapp.domain.budget

import com.example.cashapp.domain.budget.BudgetCalculator.Level
import com.example.cashapp.domain.dashboard.DashboardCalculator.Amount
import com.example.cashapp.domain.money.CurrencyConverter
import com.example.cashapp.domain.money.CurrencyConverter.ExchangeRate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BudgetCalculatorTest {
    private val converter = CurrencyConverter(
        listOf(ExchangeRate("EUR", "HUF", 400.0)),
        decimalsOf = { if (it == "EUR") 2 else 0 },
    )

    private fun status(vararg spent: Amount) =
        BudgetCalculator.status(120_000, "HUF", 80, spent.toList(), converter)

    @Test fun `keret alatt`() {
        val s = status(Amount("HUF", 62_000))
        assertEquals(Level.OK, s.level)
        assertEquals(62_000f / 120_000f, s.progress, 1e-4f)
    }

    @Test fun `küszöb felett figyelmeztet`() = assertEquals(Level.WARNING, status(Amount("HUF", 96_000)).level)

    @Test fun `túllépés`() {
        val s = status(Amount("HUF", 130_000))
        assertEquals(Level.OVER, s.level)
        assertEquals(1f, s.progress)
    }

    @Test fun `vegyes pénznem átváltva`() =
        assertEquals(100_000L, status(Amount("HUF", 60_000), Amount("EUR", 10_000)).spentMinor) // 100 € = 40 000 Ft

    @Test fun `hiányzó árfolyam jelölve`() = assertTrue(status(Amount("USD", 500)).incomplete)
}
