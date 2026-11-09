package com.example.cashapp.domain.loan

import com.example.cashapp.domain.model.LoanDirection.BORROWED
import com.example.cashapp.domain.model.LoanDirection.LENT
import com.example.cashapp.domain.model.TransactionType.EXPENSE
import com.example.cashapp.domain.model.TransactionType.INCOME
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoanMathTest {

    @Test
    fun kolcsonadas_kiadas_visszafizetes_bevetel() {
        assertEquals(EXPENSE, LoanMath.transactionType(LENT, increase = true))
        assertEquals(INCOME, LoanMath.transactionType(LENT, increase = false))
    }

    @Test
    fun kolcsonkeres_bevetel_torlesztes_kiadas() {
        assertEquals(INCOME, LoanMath.transactionType(BORROWED, increase = true))
        assertEquals(EXPENSE, LoanMath.transactionType(BORROWED, increase = false))
    }

    @Test
    fun isIncrease_aTipusForditottja() {
        assertTrue(LoanMath.isIncrease(LENT, EXPENSE))
        assertFalse(LoanMath.isIncrease(LENT, INCOME))
        assertTrue(LoanMath.isIncrease(BORROWED, INCOME))
        assertFalse(LoanMath.isIncrease(BORROWED, EXPENSE))
    }

    @Test
    fun hatralek_nyitoOsszegPluszNovelesMinuszTorlesztes() {
        // 10 000 nyitó, +5 000 újabb kölcsön, −12 000 visszafizetés → 3 000
        assertEquals(3_000L, LoanMath.outstanding(10_000, listOf(EXPENSE to 5_000L, INCOME to 12_000L), LENT))
        assertEquals(3_000L, LoanMath.outstanding(10_000, listOf(INCOME to 5_000L, EXPENSE to 12_000L), BORROWED))
        assertEquals(0L, LoanMath.outstanding(0, emptyList(), LENT))
        // 1000 kölcsön, ebből 600 már visszafizetve → 400
        assertEquals(400L, LoanMath.outstanding(1_000, emptyList(), LENT, openingRepaidMinor = 600))
        // Túlfizetés → 0 (rendezve), nem negatív
        assertEquals(0L, LoanMath.outstanding(1_000, listOf(INCOME to 1_500L), LENT))
    }
}
