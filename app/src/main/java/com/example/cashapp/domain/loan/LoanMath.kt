package com.example.cashapp.domain.loan

import com.example.cashapp.domain.model.LoanDirection
import com.example.cashapp.domain.model.TransactionType

/**
 * Magánkölcsön mozgásai rendes bevétel/kiadás tételekként kerülnek a számlára
 * (így az egyenleg magától helyes), de a kimutatásokból kimaradnak.
 *
 * - Kölcsönadás (LENT, növelés): kiadás – a pénz kimegy a számláról.
 * - Visszafizetést kapok (LENT, csökkentés): bevétel.
 * - Kölcsönt kapok (BORROWED, növelés): bevétel.
 * - Visszafizetek (BORROWED, csökkentés): kiadás.
 */
object LoanMath {

    /** Milyen típusú tétel lesz egy mozgásból. [increase]: nő a tartozás (új kölcsön), különben törlesztés. */
    fun transactionType(direction: LoanDirection, increase: Boolean): TransactionType = when (direction) {
        LoanDirection.LENT -> if (increase) TransactionType.EXPENSE else TransactionType.INCOME
        LoanDirection.BORROWED -> if (increase) TransactionType.INCOME else TransactionType.EXPENSE
    }

    /** Egy meglévő tétel növelte-e a tartozást (a [transactionType] fordítottja). */
    fun isIncrease(direction: LoanDirection, type: TransactionType): Boolean =
        transactionType(direction, increase = true) == type

    /** Hátralék = (nyitó összeg − ebből már visszafizetett) + növelések − törlesztések; legalább 0 (túlfizetés = rendezve). */
    fun outstanding(
        openingMinor: Long,
        movements: List<Pair<TransactionType, Long>>,
        direction: LoanDirection,
        openingRepaidMinor: Long = 0,
    ): Long =
        (openingMinor - openingRepaidMinor + movements.sumOf { (type, amount) -> if (isIncrease(direction, type)) amount else -amount })
            .coerceAtLeast(0)
}
