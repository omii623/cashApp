package com.example.cashapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.cashapp.data.local.entity.LoanEntity
import com.example.cashapp.data.local.model.LoanWithBalance
import kotlinx.coroutines.flow.Flow

/**
 * Hátralék SQL-ben (az `l` alias a kölcsön): (nyitó összeg − ebből már visszafizetett) + növelések − törlesztések.
 * A nyitó adatokból könyvelt tételek (loanOpening) nem számítanak külön – azokat a nyitó mezők már tartalmazzák.
 * Az összeg a kölcsön pénznemében (loanAmountMinor) számít, akármilyen pénznemben fizették.
 * Túlfizetésnél a hátralék 0 (a kölcsön rendezett), nem megy mínuszba.
 * Növelés: kölcsönadásnál (LENT) a kiadás, kölcsönkérésnél (BORROWED) a bevétel – lásd LoanMath.
 */
private const val LOAN_OUTSTANDING_SQL = """l.openingAmountMinor - l.openingRepaidMinor
    + COALESCE((SELECT SUM(CASE
            WHEN (l.direction = 'LENT' AND t.type = 'EXPENSE') OR (l.direction = 'BORROWED' AND t.type = 'INCOME')
            THEN COALESCE(t.loanAmountMinor, t.amountMinor) ELSE -COALESCE(t.loanAmountMinor, t.amountMinor) END)
        FROM transactions t WHERE t.loanId = l.id AND t.loanOpening IS NULL), 0)"""

private const val LOAN_SELECT = """
    SELECT l.*, MAX(0, $LOAN_OUTSTANDING_SQL) AS outstandingMinor,
           (SELECT MAX(t.date) FROM transactions t WHERE t.loanId = l.id AND t.loanOpening IS NULL) AS lastMovementDate
    FROM loans l
"""

@Dao
interface LoanDao {

    /** Az összes kölcsön hátralékkal: előbb a nyitottak (és az új, üresek), aztán a rendezettek és a lezártak. */
    @Query(
        "$LOAN_SELECT ORDER BY (l.closedDate IS NOT NULL OR (outstandingMinor = 0 AND lastMovementDate IS NOT NULL)), " +
            "l.personName COLLATE NOCASE, l.id"
    )
    fun observeAll(): Flow<List<LoanWithBalance>>

    @Query("$LOAN_SELECT WHERE l.id = :id")
    fun observe(id: Long): Flow<LoanWithBalance?>

    @Query("$LOAN_SELECT WHERE l.id = :id")
    suspend fun getWithBalance(id: Long): LoanWithBalance?

    @Query("SELECT * FROM loans WHERE id = :id")
    suspend fun getById(id: Long): LoanEntity?

    /** Valódi mozgások száma (a nyitó adatokból könyvelt tételek nélkül). */
    @Query("SELECT COUNT(*) FROM transactions WHERE loanId = :id AND loanOpening IS NULL")
    suspend fun movementCount(id: Long): Int

    @Insert
    suspend fun insert(loan: LoanEntity): Long

    @Update
    suspend fun update(loan: LoanEntity)

    @Query("DELETE FROM transactions WHERE loanId = :id")
    suspend fun deleteMovements(id: Long)

    /** Lezárás ([date]) vagy újranyitás (null). */
    @Query("UPDATE loans SET closedDate = :date WHERE id = :id")
    suspend fun setClosed(id: Long, date: java.time.LocalDate?)

    @Query("DELETE FROM loans WHERE id = :id")
    suspend fun deleteById(id: Long)
}
