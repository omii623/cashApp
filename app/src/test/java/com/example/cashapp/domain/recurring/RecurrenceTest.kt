package com.example.cashapp.domain.recurring

import com.example.cashapp.domain.model.RecurrenceFrequency.DAILY
import com.example.cashapp.domain.model.RecurrenceFrequency.MONTHLY
import com.example.cashapp.domain.model.RecurrenceFrequency.WEEKLY
import com.example.cashapp.domain.model.RecurrenceFrequency.YEARLY
import com.example.cashapp.domain.model.RecurrenceFrequency
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class RecurrenceTest {
    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)

    @Test fun `havi 31-e rövid hónapban a hónap utolsó napja`() {
        val start = d(2026, 1, 31)
        assertEquals(d(2026, 2, 28), Recurrence.occurrence(start, MONTHLY, 1, 1))
        assertEquals(d(2026, 3, 31), Recurrence.occurrence(start, MONTHLY, 1, 2)) // nem csúszik 28-ra
        assertEquals(d(2026, 4, 30), Recurrence.occurrence(start, MONTHLY, 1, 3))
    }

    @Test fun `kéthavonta`() = assertEquals(d(2026, 5, 15), Recurrence.occurrence(d(2026, 1, 15), MONTHLY, 2, 2))

    @Test fun `szökőnap éves szabálynál`() {
        assertEquals(d(2025, 2, 28), Recurrence.occurrence(d(2024, 2, 29), YEARLY, 1, 1))
        assertEquals(d(2028, 2, 29), Recurrence.occurrence(d(2024, 2, 29), YEARLY, 1, 4))
    }

    @Test fun `hetente`() = assertEquals(d(2026, 11, 20), Recurrence.occurrence(d(2026, 11, 6), WEEKLY, 1, 2))

    @Test fun `első esedékesség egy nap után`() {
        assertEquals(d(2026, 12, 1), Recurrence.firstOnOrAfter(d(2026, 1, 1), MONTHLY, 1, d(2026, 11, 7)))
        assertEquals(d(2026, 11, 1), Recurrence.firstOnOrAfter(d(2026, 1, 1), MONTHLY, 1, d(2026, 11, 1)))
        assertEquals(d(2027, 1, 1), Recurrence.firstOnOrAfter(d(2027, 1, 1), MONTHLY, 1, d(2026, 11, 7)))
    }

    @Test fun `következő szigorúan utána`() =
        assertEquals(d(2026, 12, 1), Recurrence.nextAfter(d(2026, 1, 1), MONTHLY, 1, d(2026, 11, 1)))

    @Test fun `elmaradt esedékességek listája`() {
        val dates = Recurrence.dueDates(d(2026, 9, 6), MONTHLY, 1, from = d(2026, 9, 6), until = d(2026, 11, 7), end = null)
        assertEquals(listOf(d(2026, 9, 6), d(2026, 10, 6), d(2026, 11, 6)), dates)
    }

    @Test fun `záró dátum után nincs több`() {
        val dates = Recurrence.dueDates(d(2026, 11, 1), DAILY, 1, d(2026, 11, 1), d(2026, 11, 30), end = d(2026, 11, 3))
        assertEquals(3, dates.size)
    }

    @Test fun `limit védi az elárasztást`() {
        val dates = Recurrence.dueDates(d(2020, 1, 1), DAILY, 1, d(2020, 1, 1), d(2026, 11, 7), end = null, limit = 10)
        assertEquals(10, dates.size)
    }

    /** A gyors (becsléses) keresés ugyanazt adja, mint a lépésenkénti. */
    @Test fun `firstOnOrAfter egyezik a lépésenkénti kereséssel`() {
        val starts = listOf(d(2024, 1, 31), d(2024, 2, 29), d(2025, 3, 15), d(2023, 8, 30))
        for (start in starts) for (f in RecurrenceFrequency.entries) for (interval in 1..3) {
            var date = start.minusDays(3)
            while (date.isBefore(start.plusYears(4))) {
                var k = 0L
                var expected = start
                while (expected.isBefore(date)) expected = Recurrence.occurrence(start, f, interval, ++k)
                assertEquals("$start $f/$interval $date", expected, Recurrence.firstOnOrAfter(start, f, interval, date))
                date = date.plusDays(5)
            }
        }
    }

    @Test fun `régi napi szabály gyorsan`() =
        assertEquals(d(2026, 11, 7), Recurrence.firstOnOrAfter(d(1990, 1, 1), DAILY, 1, d(2026, 11, 7)))
}
