package com.example.cashapp.domain.dashboard

import com.example.cashapp.domain.dashboard.PeriodSummary.Entry
import com.example.cashapp.domain.model.TransactionType.EXCHANGE
import com.example.cashapp.domain.model.TransactionType.EXPENSE
import com.example.cashapp.domain.model.TransactionType.INCOME
import com.example.cashapp.domain.money.CurrencyConverter
import com.example.cashapp.domain.money.CurrencyConverter.ExchangeRate
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class PeriodSummaryTest {
    private val converter = CurrencyConverter(listOf(ExchangeRate("EUR", "HUF", 400.0))) { if (it == "EUR") 2 else 0 }
    private val d1 = LocalDate.of(2026, 11, 1)
    private val d2 = LocalDate.of(2026, 11, 6)

    private val entries = listOf(
        Entry(d2, EXPENSE, 12_500, "HUF"),
        Entry(d2, EXPENSE, 1_000, "EUR"),      // 10,00 € = 4 000 Ft
        Entry(d2, EXCHANGE, 100_000, "HUF"),   // nem számít
        Entry(d1, INCOME, 620_000, "HUF"),
    )

    @Test fun `havi összesítés`() {
        val t = PeriodSummary.totals(entries, "HUF", converter)
        assertEquals(620_000L, t.incomeMinor)
        assertEquals(16_500L, t.expenseMinor)
        assertEquals(603_500L, t.netMinor)
    }

    @Test fun `napi bontás`() {
        val byDay = PeriodSummary.byDay(entries, "HUF", converter)
        assertEquals(-16_500L, byDay.getValue(d2).netMinor)
        assertEquals(620_000L, byDay.getValue(d1).netMinor)
    }
}
