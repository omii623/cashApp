package com.example.cashapp.domain.report

import com.example.cashapp.domain.model.TransactionType.EXCHANGE
import com.example.cashapp.domain.model.TransactionType.EXPENSE
import com.example.cashapp.domain.model.TransactionType.INCOME
import com.example.cashapp.domain.money.CurrencyConverter
import com.example.cashapp.domain.money.CurrencyConverter.ExchangeRate
import com.example.cashapp.domain.report.ReportCalculator.Entry
import com.example.cashapp.domain.report.ReportCalculator.GroupAmount
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportCalculatorTest {
    private val converter = CurrencyConverter(listOf(ExchangeRate("EUR", "HUF", 400.0))) { if (it == "EUR") 2 else 0 }

    @Test fun `bontás csökkenő sorrendben, arányokkal`() {
        val b = ReportCalculator.breakdown(
            listOf(GroupAmount(1, "HUF", 30_000), GroupAmount(2, "HUF", 60_000), GroupAmount(1, "EUR", 2_500)), // 25 € = 10 000 Ft
            "HUF", converter,
        )
        assertEquals(100_000L, b.totalMinor)
        assertEquals(listOf(2L, 1L), b.slices.map { it.id })
        assertEquals(0.6f, b.slices[0].share, 1e-4f)
        assertEquals(0.4f, b.slices[1].share, 1e-4f)
    }

    @Test fun `limit felett Egyéb szelet`() {
        val amounts = (1L..8L).map { GroupAmount(it, "HUF", it * 1_000) }
        val b = ReportCalculator.breakdown(amounts, "HUF", converter, maxSlices = 6)
        assertEquals(6, b.slices.size)
        assertTrue(b.slices.last().isOther)
        assertEquals(1_000L + 2_000 + 3_000, b.slices.last().amountMinor) // a 3 legkisebb
    }

    @Test fun `pont a limitnél nincs Egyéb`() {
        val b = ReportCalculator.breakdown((1L..6L).map { GroupAmount(it, "HUF", 100) }, "HUF", converter, maxSlices = 6)
        assertEquals(6, b.slices.size)
        assertTrue(b.slices.none { it.isOther })
    }

    @Test fun `üres bontás`() = assertEquals(0, ReportCalculator.breakdown(emptyList(), "HUF", converter).slices.size)

    @Test fun `havi sorozat üres hónappal, váltás nélkül`() {
        val months = listOf(YearMonth.of(2026, 9), YearMonth.of(2026, 10), YearMonth.of(2026, 11))
        val result = ReportCalculator.monthly(
            listOf(
                Entry(LocalDate.of(2026, 9, 1), INCOME, 620_000, "HUF"),
                Entry(LocalDate.of(2026, 11, 3), EXPENSE, 1_000, "EUR"), // 4 000 Ft
                Entry(LocalDate.of(2026, 11, 4), EXCHANGE, 100_000, "HUF"),
            ),
            months, "HUF", converter,
        )
        assertEquals(listOf(620_000L, 0L, 0L), result.map { it.incomeMinor })
        assertEquals(listOf(0L, 0L, 4_000L), result.map { it.expenseMinor })
    }
}
