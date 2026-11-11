package com.example.cashapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.cashapp.data.local.entity.BankLoanEntity
import com.example.cashapp.data.local.entity.BankLoanEventEntity
import com.example.cashapp.data.local.model.BankLoanWithProgress
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/**
 * Befizetett részletek (a `b` alias a hitel): a követés előtt befizetettek ([BankLoanEntity.paidMonthsBefore])
 * + a hitelhez tartozó tételek, kivéve az előtörlesztések tételeit (azokra egy esemény hivatkozik).
 * A hátralék és a következő részlet a törlesztési tervből számol.
 */
private const val PAID_COUNT_SQL = """(b.paidMonthsBefore + (SELECT COUNT(*) FROM transactions t WHERE t.bankLoanId = b.id
    AND t.id NOT IN (SELECT e.transactionId FROM bank_loan_events e WHERE e.bankLoanId = b.id AND e.transactionId IS NOT NULL)))"""

private const val BANK_LOAN_SELECT = """
    SELECT b.*, $PAID_COUNT_SQL AS paidInstallments,
           (SELECT MAX(t.date) FROM transactions t WHERE t.bankLoanId = b.id) AS lastPaymentDate
    FROM bank_loans b
"""

@Dao
interface BankLoanDao {

    /** Az összes banki hitel: előbb a nyitottak, aztán a lezártak; azon belül név szerint. */
    @Query("$BANK_LOAN_SELECT ORDER BY (b.closedDate IS NOT NULL), b.name COLLATE NOCASE, b.id")
    fun observeAll(): Flow<List<BankLoanWithProgress>>

    @Query("$BANK_LOAN_SELECT WHERE b.id = :id")
    fun observe(id: Long): Flow<BankLoanWithProgress?>

    @Query("$BANK_LOAN_SELECT WHERE b.id = :id")
    suspend fun getWithProgress(id: Long): BankLoanWithProgress?

    @Query("SELECT * FROM bank_loans WHERE id = :id")
    suspend fun getById(id: Long): BankLoanEntity?

    /** Melyik hitelhez tartozik egy ismétlődő szabály (jóváhagyáskor a tétel ezzel kap bankLoanId-t). */
    @Query("SELECT * FROM bank_loans WHERE recurringRuleId = :ruleId LIMIT 1")
    suspend fun getByRuleId(ruleId: Long): BankLoanEntity?

    @Query("SELECT $PAID_COUNT_SQL FROM bank_loans b WHERE b.id = :id")
    suspend fun paidInstallments(id: Long): Int

    @Insert
    suspend fun insert(loan: BankLoanEntity): Long

    @Update
    suspend fun update(loan: BankLoanEntity)

    /** Lezárás ([date]) vagy újranyitás (null). */
    @Query("UPDATE bank_loans SET closedDate = :date WHERE id = :id")
    suspend fun setClosed(id: Long, date: LocalDate?)

    /** A hitel törlése; az eseményei CASCADE-del, a tételei hivatkozása SET NULL-lal. */
    @Query("DELETE FROM bank_loans WHERE id = :id")
    suspend fun deleteById(id: Long)

    // ------------------------------------------------------------------ események

    /** Az összes hitel eseményei (a lista képernyő hátralékához). */
    @Query("SELECT * FROM bank_loan_events ORDER BY bankLoanId, afterInstallment, date, id")
    fun observeAllEvents(): Flow<List<BankLoanEventEntity>>

    @Query("SELECT * FROM bank_loan_events WHERE bankLoanId = :loanId ORDER BY afterInstallment, date, id")
    fun observeEvents(loanId: Long): Flow<List<BankLoanEventEntity>>

    @Query("SELECT * FROM bank_loan_events WHERE bankLoanId = :loanId ORDER BY afterInstallment, date, id")
    suspend fun getEvents(loanId: Long): List<BankLoanEventEntity>

    @Query("SELECT * FROM bank_loan_events WHERE id = :id")
    suspend fun getEvent(id: Long): BankLoanEventEntity?

    @Insert
    suspend fun insertEvent(event: BankLoanEventEntity): Long

    @Update
    suspend fun updateEvent(event: BankLoanEventEntity)

    @Query("DELETE FROM bank_loan_events WHERE id = :id")
    suspend fun deleteEvent(id: Long)
}
