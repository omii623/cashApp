package com.example.cashapp.domain.loan

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LoanCalculatorTest {

    @Test
    fun havi_rata_az_eves_tizenketted_resze() {
        assertEquals(0, BigDecimal("0.01149166666666667").compareTo(LoanCalculator.monthlyRate(13.79).setScale(17, java.math.RoundingMode.HALF_UP)))
        assertEquals(0, BigDecimal.ZERO.compareTo(LoanCalculator.monthlyRate(0.0)))
    }

    @Test
    fun banki_pelda_1M_13_79_szazalek_36_honap() {
        // K = 1 000 000, i = 0,1379 / 12, n = 36 → A = 34 075,72 → 34 076 Ft
        val res = LoanCalculator.calculate(1_000_000, 13.79, 36)
        assertEquals(34_076L, res.monthlyPaymentMinor)
        assertEquals(34_076L * 36, res.totalRepaymentMinor)
        assertEquals(34_076L * 36 - 1_000_000, res.totalCostMinor)
    }

    @Test
    fun masik_ismert_ertek() {
        // 1 000 000 Ft, 15%, 60 hónap → 23 789,93 → 23 790 Ft (független számítással ellenőrizve)
        assertEquals(23_790L, LoanCalculator.calculate(1_000_000, 15.0, 60).monthlyPaymentMinor)
    }

    @Test
    fun nulla_kamat_eseten_egyenlo_reszek() {
        val res = LoanCalculator.calculate(1_200_000, 0.0, 12)
        assertEquals(100_000L, res.monthlyPaymentMinor)
        assertEquals(0L, res.totalCostMinor)
    }

    @Test
    fun egy_honapra_a_toke_plusz_egy_havi_kamat() {
        // 100 000 Ft, 12%, 1 hónap → 100 000 · 1,01 = 101 000 Ft
        assertEquals(101_000L, LoanCalculator.calculate(100_000, 12.0, 1).monthlyPaymentMinor)
    }

    @Test
    fun ervenytelen_bemenet() {
        assertThrows(IllegalArgumentException::class.java) { LoanCalculator.calculate(0, 10.0, 12) }
        assertThrows(IllegalArgumentException::class.java) { LoanCalculator.calculate(1000, -1.0, 12) }
        assertThrows(IllegalArgumentException::class.java) { LoanCalculator.calculate(1000, 10.0, 0) }
    }

    // ------------------------------------------------------------------ előtörlesztés
    // A várt értékek független (Python) számításból: havi ráta THM/12, részlet és hátralék egész forintra kerekítve.

    @Test
    fun elotorlesztes_buntetovel_kuszob_feletti_reszre() {
        // 1M, 13,79%, 36 hó; 300 000 Ft a 12. hónap után; 1% büntető 200 000 Ft felett
        val r = LoanCalculator.prepayment(1_000_000, 13.79, 36, 300_000, 12, 1.0, 200_000)
        assertEquals(711_185L, r.balanceBeforeMinor)
        assertEquals(300_000L, r.prepaidMinor)
        assertEquals(1_000L, r.penaltyMinor) // (300 000 − 200 000) × 1%
        assertEquals(19_701L, r.lowerPayment.monthlyPaymentMinor)
        assertEquals(24, r.lowerPayment.remainingMonths)
        assertEquals(1_182_736L, r.lowerPayment.totalRepaymentMinor)
        assertEquals(44_000L, r.lowerPayment.savingMinor)
        assertEquals(34_076L, r.shorterTerm.monthlyPaymentMinor)
        assertEquals(14, r.shorterTerm.remainingMonths)
        assertEquals(2_200L, r.shorterTerm.lastPaymentMinor)
        assertEquals(1_155_100L, r.shorterTerm.totalRepaymentMinor)
        assertEquals(71_636L, r.shorterTerm.savingMinor)
    }

    @Test
    fun kuszob_alatt_nincs_buntetes() {
        val r = LoanCalculator.prepayment(1_000_000, 13.79, 36, 150_000, 12, 1.0, 200_000)
        assertEquals(0L, r.penaltyMinor)
        assertEquals(26_888L, r.lowerPayment.monthlyPaymentMinor)
        assertEquals(22_512L, r.lowerPayment.savingMinor)
        assertEquals(19, r.shorterTerm.remainingMonths)
        assertEquals(12_354L, r.shorterTerm.lastPaymentMinor)
        assertEquals(42_102L, r.shorterTerm.savingMinor)
    }

    @Test
    fun hatraleknal_nagyobb_elotorlesztes_kifizeti_a_hitelt() {
        val r = LoanCalculator.prepayment(1_000_000, 13.79, 36, 2_000_000, 12, 1.0, 200_000)
        assertTrue(r.paidOff)
        assertEquals(711_185L, r.prepaidMinor)
        assertEquals(5_112L, r.penaltyMinor) // (711 185 − 200 000) × 1%
        assertEquals(0, r.lowerPayment.remainingMonths)
        assertEquals(1_125_209L, r.lowerPayment.totalRepaymentMinor)
        assertEquals(101_527L, r.shorterTerm.savingMinor)
    }

    @Test
    fun kamatmentes_hitel_elotorlesztese() {
        val r = LoanCalculator.prepayment(1_200_000, 0.0, 12, 300_000, 3, 2.0, 0)
        assertEquals(900_000L, r.balanceBeforeMinor)
        assertEquals(6_000L, r.penaltyMinor)
        assertEquals(66_667L, r.lowerPayment.monthlyPaymentMinor)
        assertEquals(-6_003L, r.lowerPayment.savingMinor) // kamat nincs, csak a büntető és a kerekítés
        assertEquals(6, r.shorterTerm.remainingMonths)
        assertEquals(-6_000L, r.shorterTerm.savingMinor)
    }

    @Test
    fun elotorlesztes_ervenytelen_honap() {
        assertThrows(IllegalArgumentException::class.java) { LoanCalculator.prepayment(1_000_000, 10.0, 12, 1000, 12, 0.0, 0) }
        assertThrows(IllegalArgumentException::class.java) { LoanCalculator.prepayment(1_000_000, 10.0, 12, 1000, 0, 0.0, 0) }
    }

    // ------------------------------------------------------------------ infláció
    // Várt értékek független (Python) számításból: havi infláció (1+π)^(1/12) − 1.

    @Test
    fun vasarloero_5_szazalek_inflacioval() {
        val r = LoanCalculator.realValue(1_000_000, 34_076, 36, 5.0)
        assertEquals(1_138_863L, r.totalRepaymentMinor)
        assertEquals(138_863L, r.totalCostMinor)
        assertEquals(29_436L, r.lastPaymentMinor)
    }

    @Test
    fun inflacio_nelkul_a_nevleges_ertek() {
        val r = LoanCalculator.realValue(1_000_000, 34_076, 36, 0.0)
        assertEquals(1_226_736L, r.totalRepaymentMinor)
        assertEquals(34_076L, r.lastPaymentMinor)
    }

    @Test
    fun kamatnal_nagyobb_inflacio_negativ_valos_koltseg() {
        val r = LoanCalculator.realValue(1_000_000, 34_076, 36, 20.0)
        assertEquals(937_726L, r.totalRepaymentMinor)
        assertEquals(-62_274L, r.totalCostMinor)
        assertEquals(19_720L, r.lastPaymentMinor)
    }

    @Test
    fun elotorlesztes_megtakaritas_mai_ertekben() {
        // Független (Python) számítás: 5% inflációval 25 747 / 41 829 Ft; 0%-nál = névleges (44 000 / 71 636 Ft)
        val r = LoanCalculator.prepayment(1_000_000, 13.79, 36, 300_000, 12, 1.0, 200_000, inflationPercent = 5.0)
        assertEquals(44_000L, r.lowerPayment.savingMinor)
        assertEquals(25_747L, r.lowerPayment.realSavingMinor)
        assertEquals(71_636L, r.shorterTerm.savingMinor)
        assertEquals(41_829L, r.shorterTerm.realSavingMinor)
        val zero = LoanCalculator.prepayment(1_000_000, 13.79, 36, 300_000, 12, 1.0, 200_000, inflationPercent = 0.0)
        assertEquals(44_000L, zero.lowerPayment.realSavingMinor)
        assertEquals(71_636L, zero.shorterTerm.realSavingMinor)
        val off = LoanCalculator.prepayment(1_000_000, 13.79, 36, 2_000_000, 12, 1.0, 200_000, inflationPercent = 5.0)
        assertEquals(58_390L, off.shorterTerm.realSavingMinor)
    }
}
