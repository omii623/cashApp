package com.example.cashapp.domain.loan

import com.example.cashapp.domain.loan.AmortizationSchedule.Event
import com.example.cashapp.domain.loan.AmortizationSchedule.Params
import com.example.cashapp.domain.model.PrepaymentMode
import java.time.LocalDate
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AmortizationScheduleTest {

    private val first = LocalDate.of(2026, 1, 15)
    private val base = Params(1_000_000, 13.79, 36, first)

    /** Minden tervre igaz: a tőkerészek összege a nyilvántartott hitelösszeg, a hátralék 0-ra fut ki, a sorok összefüggnek. */
    private fun assertConsistent(s: AmortizationSchedule.Schedule, principal: Long) {
        assertEquals(principal, s.installments.sumOf { it.principalMinor } + s.prepaidMinor)
        assertEquals(0L, s.installments.last().balanceAfterMinor)
        s.installments.forEach { assertEquals(it.paymentMinor, it.interestMinor + it.principalMinor) }
        s.installments.zipWithNext().forEach { (a, b) -> assertEquals(a.balanceAfterMinor - b.prepaidBeforeMinor, b.balanceBeforeMinor) }
    }

    @Test
    fun alapterv_egyezik_a_kalkulatorral() {
        val s = AmortizationSchedule.build(base)
        assertEquals(36, s.installments.size)
        assertTrue(s.installments.dropLast(1).all { it.paymentMinor == 34_076L })
        // Az utolsó részlet a kerekítés miatt pár forinttal eltérhet (a hátralék pontosan 0-ra fut ki).
        assertTrue(abs(s.installments.last().paymentMinor - 34_076L) <= 36)
        assertTrue(abs(s.totalPaymentMinor - LoanCalculator.calculate(1_000_000, 13.79, 36).totalRepaymentMinor) <= 36)
        assertEquals(s.totalPaymentMinor - 1_000_000, s.totalInterestMinor)
        assertConsistent(s, 1_000_000)
    }

    @Test
    fun elso_sor_kamata_es_toke_resze() {
        val row = AmortizationSchedule.build(base).installments.first()
        // 1 000 000 · 0,1379 / 12 = 11 491,67 → a hátralék 977 415,67 → 977 416; tőke 22 584, kamat 11 492
        assertEquals(1_000_000L, row.balanceBeforeMinor)
        assertEquals(977_416L, row.balanceAfterMinor)
        assertEquals(22_584L, row.principalMinor)
        assertEquals(11_492L, row.interestMinor)
    }

    @Test
    fun a_kamat_aranya_csokken() {
        val rows = AmortizationSchedule.build(base).installments.dropLast(1)
        rows.zipWithNext().forEach { (a, b) -> assertTrue(b.interestMinor <= a.interestMinor) }
    }

    @Test
    fun esedekessegek_honaponkent_honap_vegen_igazitva() {
        val s = AmortizationSchedule.build(Params(100_000, 10.0, 3, LocalDate.of(2026, 1, 31)))
        assertEquals(
            listOf(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 31)),
            s.installments.map { it.dueDate },
        )
        assertEquals(LocalDate.of(2026, 3, 31), s.lastDueDate)
    }

    @Test
    fun nulla_kamat() {
        val s = AmortizationSchedule.build(Params(1_000_000, 0.0, 3, first))
        assertEquals(listOf(333_333L, 333_333L, 333_334L), s.installments.map { it.paymentMinor })
        assertEquals(0L, s.totalInterestMinor)
        assertConsistent(s, 1_000_000)
    }

    @Test
    fun elotorlesztes_rovidebb_futamido_egyezik_a_kalkulatorral() {
        val calc = LoanCalculator.prepayment(1_000_000, 13.79, 36, 300_000, 12, 1.0, 200_000)
        val s = AmortizationSchedule.build(base, listOf(Event.Prepayment(12, 300_000, PrepaymentMode.SHORTER_TERM)))
        assertEquals(12 + calc.shorterTerm.remainingMonths, s.installments.size)
        assertEquals(calc.balanceBeforeMinor, s.installments[12].balanceBeforeMinor + 300_000)
        assertEquals(300_000L, s.installments[12].prepaidBeforeMinor)
        assertEquals(calc.shorterTerm.lastPaymentMinor, s.installments.last().paymentMinor)
        // Teljes visszafizetés: részletek + előtörlesztés + büntető
        assertEquals(calc.shorterTerm.totalRepaymentMinor, s.totalPaymentMinor + s.prepaidMinor + calc.penaltyMinor)
        assertConsistent(s, 1_000_000)
    }

    @Test
    fun elotorlesztes_csokkeno_reszlet_egyezik_a_kalkulatorral() {
        val calc = LoanCalculator.prepayment(1_000_000, 13.79, 36, 300_000, 12, 1.0, 200_000)
        val s = AmortizationSchedule.build(base, listOf(Event.Prepayment(12, 300_000, PrepaymentMode.LOWER_PAYMENT)))
        assertEquals(36, s.installments.size)
        assertTrue(s.installments.subList(12, 35).all { it.paymentMinor == calc.lowerPayment.monthlyPaymentMinor })
        assertTrue(abs(s.totalPaymentMinor + s.prepaidMinor + calc.penaltyMinor - calc.lowerPayment.totalRepaymentMinor) <= 24)
        assertConsistent(s, 1_000_000)
    }

    @Test
    fun hatraleknal_nagyobb_elotorlesztes_lezarja_a_tervet() {
        val s = AmortizationSchedule.build(base, listOf(Event.Prepayment(12, 2_000_000, PrepaymentMode.SHORTER_TERM)))
        assertEquals(12, s.installments.size)
        assertEquals(711_185L, s.prepaidMinor)
        assertEquals(0L, s.outstandingAfter(12))
        assertNull(s.nextAfter(12))
    }

    @Test
    fun elotorlesztes_az_elso_reszlet_elott() {
        val s = AmortizationSchedule.build(base, listOf(Event.Prepayment(0, 400_000, PrepaymentMode.LOWER_PAYMENT)))
        assertEquals(600_000L, s.installments.first().balanceBeforeMinor)
        assertEquals(LoanCalculator.calculate(600_000, 13.79, 36).monthlyPaymentMinor, s.installments.first().paymentMinor)
        assertConsistent(s, 1_000_000)
    }

    @Test
    fun kamatvaltozas_uj_reszlet_a_hatralekra() {
        val plain = AmortizationSchedule.build(base)
        val s = AmortizationSchedule.build(base, listOf(Event.RateChange(12, 8.0)))
        val balance = plain.installments[11].balanceAfterMinor
        assertEquals(balance, s.installments[12].balanceBeforeMinor)
        assertEquals(LoanCalculator.calculate(balance, 8.0, 24).monthlyPaymentMinor, s.installments[12].paymentMinor)
        assertEquals(8.0, s.installments[12].thmPercent, 0.0)
        assertEquals(13.79, s.installments[11].thmPercent, 0.0)
        assertEquals(36, s.installments.size)
        assertTrue(s.totalInterestMinor < plain.totalInterestMinor)
        assertConsistent(s, 1_000_000)
    }

    @Test
    fun kamatvaltozas_rovidebb_futamidos_elotorlesztes_utan_a_maradek_honapokra() {
        val prepaid = AmortizationSchedule.build(base, listOf(Event.Prepayment(12, 300_000, PrepaymentMode.SHORTER_TERM)))
        val s = AmortizationSchedule.build(
            base,
            listOf(Event.Prepayment(12, 300_000, PrepaymentMode.SHORTER_TERM), Event.RateChange(15, 10.0)),
        )
        // A rövidebb terv szerint a 15. részlet után ennyi hónap maradt → a futamidő nem nő vissza 36-ra.
        assertEquals(prepaid.installments.size, s.installments.size)
        assertConsistent(s, 1_000_000)
    }

    @Test
    fun felulirt_reszlet() {
        // Nagyobb részlet → hamarabb kifut; kisebb → az utolsó részlet nagyobb.
        val higher = AmortizationSchedule.build(base.copy(paymentOverrideMinor = 40_000))
        assertTrue(higher.installments.size < 36)
        assertConsistent(higher, 1_000_000)
        val lower = AmortizationSchedule.build(base.copy(paymentOverrideMinor = 34_000))
        assertEquals(36, lower.installments.size)
        assertTrue(lower.installments.last().paymentMinor > 34_076)
        assertConsistent(lower, 1_000_000)
    }

    @Test
    fun hatralek_kovetkezo_reszlet_es_fizetett_kamat() {
        val s = AmortizationSchedule.build(base)
        assertEquals(1_000_000L, s.outstandingAfter(0))
        assertEquals(s.installments[2].balanceAfterMinor, s.outstandingAfter(3))
        assertEquals(4, s.nextAfter(3)!!.index)
        assertEquals(s.installments.take(3).sumOf { it.interestMinor }, s.interestPaidAfter(3))
        assertEquals(0L, s.outstandingAfter(36))
        assertNull(s.nextAfter(36))
    }

    @Test
    fun korabban_felvett_hitel_banki_hatralek_nelkul_azonos_az_alaptervvel() {
        val plain = AmortizationSchedule.build(base)
        val s = AmortizationSchedule.build(base.copy(openingPaid = 12))
        assertEquals(plain.installments, s.installments)
        assertEquals(plain.installments[11].balanceAfterMinor, s.outstandingAfter(12))
    }

    @Test
    fun korabban_felvett_hitel_banki_hatralekkal_folytatodik() {
        // 12 részlet után a bank szerint 650 000 Ft a hátralék (a számolt 711 185 Ft helyett, pl. korábbi előtörlesztés miatt).
        val s = AmortizationSchedule.build(base.copy(openingPaid = 12, openingBalanceMinor = 650_000))
        assertEquals(36, s.installments.size)
        assertEquals(650_000L, s.outstandingAfter(12))
        assertEquals(650_000L, s.installments[12].bankBalanceBeforeMinor)
        assertEquals(LoanCalculator.calculate(650_000, 13.79, 24).monthlyPaymentMinor, s.installments[12].paymentMinor)
        // A követés előtti részletek változatlanok.
        assertEquals(AmortizationSchedule.build(base).installments.take(12), s.installments.take(12))
        assertEquals(0L, s.installments.last().balanceAfterMinor)
        // A befizetett részek a banki hátralékból: a 13. részlettől a tőkerészek összege 650 000 Ft.
        assertEquals(650_000L, s.installments.drop(12).sumOf { it.principalMinor })
    }

    @Test
    fun korabban_felvett_hitel_felulirt_reszlettel_a_reszlet_marad() {
        val s = AmortizationSchedule.build(base.copy(paymentOverrideMinor = 34_100, openingPaid = 12, openingBalanceMinor = 650_000))
        assertEquals(34_100L, s.installments[12].paymentMinor)
        assertTrue(s.installments.size < 36) // kisebb hátralék, azonos részlet → hamarabb kifut
    }

    @Test
    fun esedekes_reszletek_szama_egy_datum_elott() {
        assertEquals(0, AmortizationSchedule.installmentsDueBefore(first, 36, first))
        assertEquals(1, AmortizationSchedule.installmentsDueBefore(first, 36, first.plusDays(1)))
        assertEquals(10, AmortizationSchedule.installmentsDueBefore(first, 36, first.plusMonths(10)))
        assertEquals(36, AmortizationSchedule.installmentsDueBefore(first, 36, first.plusYears(10)))
    }

    @Test
    fun ervenytelen_bemenet() {
        assertThrows(IllegalArgumentException::class.java) { AmortizationSchedule.build(base.copy(openingPaid = 36)) }
        assertThrows(IllegalArgumentException::class.java) { AmortizationSchedule.build(base.copy(openingPaid = 5, openingBalanceMinor = 0)) }
        assertThrows(IllegalArgumentException::class.java) { AmortizationSchedule.build(base.copy(principalMinor = 0)) }
        assertThrows(IllegalArgumentException::class.java) { AmortizationSchedule.build(base.copy(months = 0)) }
        assertThrows(IllegalArgumentException::class.java) { AmortizationSchedule.build(base.copy(thmPercent = -1.0)) }
        assertThrows(IllegalArgumentException::class.java) { AmortizationSchedule.build(base.copy(paymentOverrideMinor = 0)) }
    }
}
