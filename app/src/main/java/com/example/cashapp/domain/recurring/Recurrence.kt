package com.example.cashapp.domain.recurring

import com.example.cashapp.domain.model.RecurrenceFrequency
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * Ismétlődés dátumai. Minden időpont a kezdő dátumból számolódik (nem az előzőből),
 * így a hónap végi „csúszás” nem halmozódik: a 31-én induló havi tétel
 * februárban 28/29-én, márciusban újra 31-én esedékes.
 */
object Recurrence {

    /** A [k]-adik (0-tól) esedékesség. */
    fun occurrence(start: LocalDate, frequency: RecurrenceFrequency, interval: Int, k: Long): LocalDate {
        val step = k * interval.coerceAtLeast(1)
        return when (frequency) {
            RecurrenceFrequency.DAILY -> start.plusDays(step)
            RecurrenceFrequency.WEEKLY -> start.plusWeeks(step)
            RecurrenceFrequency.MONTHLY -> clampDay(YearMonth.from(start).plusMonths(step), start.dayOfMonth)
            RecurrenceFrequency.YEARLY -> clampDay(YearMonth.from(start).plusYears(step), start.dayOfMonth)
        }
    }

    /** Az első esedékesség, amely ≥ [date] (ha [date] a kezdés előtt van: maga a kezdés). */
    fun firstOnOrAfter(start: LocalDate, frequency: RecurrenceFrequency, interval: Int, date: LocalDate): LocalDate {
        if (!date.isAfter(start)) return start
        // Becslés egy lépésben (régi napi szabálynál ne kelljen több ezer lépést iterálni),
        // utána legfeljebb pár lépés korrekció a hónap végi levágás miatt.
        val unit = when (frequency) {
            RecurrenceFrequency.DAILY -> ChronoUnit.DAYS
            RecurrenceFrequency.WEEKLY -> ChronoUnit.WEEKS
            RecurrenceFrequency.MONTHLY -> ChronoUnit.MONTHS
            RecurrenceFrequency.YEARLY -> ChronoUnit.YEARS
        }
        var k = maxOf(0L, unit.between(start, date) / interval.coerceAtLeast(1) - 1)
        while (k > 0 && !occurrence(start, frequency, interval, k).isBefore(date)) k--
        var d = occurrence(start, frequency, interval, k)
        while (d.isBefore(date)) d = occurrence(start, frequency, interval, ++k)
        return d
    }

    /** Az első esedékesség, amely > [date]. */
    fun nextAfter(start: LocalDate, frequency: RecurrenceFrequency, interval: Int, date: LocalDate): LocalDate =
        firstOnOrAfter(start, frequency, interval, date.plusDays(1))

    /**
     * Esedékes dátumok [from]-tól (≥) [until]-ig (≤), legfeljebb [limit] darab
     * (védelem, ha valaki évekkel korábbi napi szabályt hoz létre).
     * [end] a szabály záró dátuma (ha van).
     */
    fun dueDates(
        start: LocalDate,
        frequency: RecurrenceFrequency,
        interval: Int,
        from: LocalDate,
        until: LocalDate,
        end: LocalDate?,
        limit: Int = 366,
    ): List<LocalDate> {
        val result = mutableListOf<LocalDate>()
        var d = firstOnOrAfter(start, frequency, interval, from)
        while (!d.isAfter(until) && (end == null || !d.isAfter(end)) && result.size < limit) {
            result += d
            d = nextAfter(start, frequency, interval, d)
        }
        return result
    }

    private fun clampDay(month: YearMonth, day: Int): LocalDate = month.atDay(minOf(day, month.lengthOfMonth()))
}
