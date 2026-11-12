package com.example.cashapp.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.cashapp.data.local.entity.ExchangeDetailEntity
import com.example.cashapp.data.local.entity.TransactionEntity
import com.example.cashapp.data.local.model.AmountRow
import com.example.cashapp.data.local.model.CategoryCurrencyTotal
import com.example.cashapp.data.local.model.GroupTotal
import com.example.cashapp.data.local.model.TransactionListItem
import com.example.cashapp.data.local.model.TypeCurrencyTotal
import com.example.cashapp.domain.model.TransactionType
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/** Listaelem lekérdezés közös része ([TransactionListItem] mezői). */
private const val LIST_SELECT = """
        SELECT t.id, t.type, t.date, t.amountMinor, t.currencyCode, t.note,
               t.accountId, a.name AS accountName,
               t.accountAmountMinor, a.currencyCode AS accountCurrencyCode,
               c.name AS categoryName, c.color AS categoryColor, c.icon AS categoryIcon,
               s.name AS subcategoryName,
               e.targetAccountId, e.targetAmountMinor, e.targetCurrencyCode, e.rate AS exchangeRate,
               t.loanId, l.personName AS loanName, l.direction AS loanDirection, t.loanOpening,
               l.color AS loanColor, l.icon AS loanIcon,
               t.bankLoanId, b.name AS bankLoanName, b.kind AS bankLoanKind, b.color AS bankLoanColor, b.icon AS bankLoanIcon
        FROM transactions t
        JOIN accounts a ON a.id = t.accountId
        LEFT JOIN categories c ON c.id = t.categoryId
        LEFT JOIN subcategories s ON s.id = t.subcategoryId
        LEFT JOIN exchange_details e ON e.transactionId = t.id
        LEFT JOIN loans l ON l.id = t.loanId
        LEFT JOIN bank_loans b ON b.id = t.bankLoanId
"""

@Dao
interface TransactionDao {

    @Query("$LIST_SELECT ORDER BY t.date DESC, t.id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<TransactionListItem>>

    /** Egy számla összes tétele: saját tételei + a hozzá bejövő váltások. */
    @Query(
        """
        $LIST_SELECT
        WHERE t.accountId = :accountId OR e.targetAccountId = :accountId
        ORDER BY t.date DESC, t.id DESC
        """
    )
    fun observeForAccount(accountId: Long): Flow<List<TransactionListItem>>

    /** Egy magánkölcsön mozgásai (kölcsön / törlesztés), legújabb elöl – a nyitó adatokból könyveltek nélkül. */
    @Query("$LIST_SELECT WHERE t.loanId = :loanId AND t.loanOpening IS NULL ORDER BY t.date DESC, t.id DESC")
    fun observeForLoan(loanId: Long): Flow<List<TransactionListItem>>

    /** A kölcsön nyitó adataiból könyvelt tételek (legfeljebb egy összeg és egy visszafizetett rész). */
    @Query("$LIST_SELECT WHERE t.loanId = :loanId AND t.loanOpening IS NOT NULL")
    fun observeLoanOpenings(loanId: Long): Flow<List<TransactionListItem>>

    @Query("SELECT * FROM transactions WHERE loanId = :loanId AND loanOpening IS NOT NULL")
    suspend fun getLoanOpenings(loanId: Long): List<TransactionEntity>

    /**
     * Tranzakciók lap: szűrt lista. A null / üres paraméter azt jelenti, hogy arra nincs szűrés.
     * A keresés a megjegyzésben, kategória-, alkategória- és számlanévben keres.
     * Számlaszűrésnél a hozzá bejövő váltások is benne vannak.
     */
    @Query(
        """
        $LIST_SELECT
        WHERE t.date BETWEEN :from AND :to
          AND t.type IN (:types)
          AND (:accountId IS NULL OR t.accountId = :accountId OR e.targetAccountId = :accountId)
          AND (:categoryId IS NULL OR t.categoryId = :categoryId)
          AND (:query = ''
               OR t.note LIKE '%' || :query || '%'
               OR c.name LIKE '%' || :query || '%'
               OR s.name LIKE '%' || :query || '%'
               OR a.name LIKE '%' || :query || '%'
               OR l.personName LIKE '%' || :query || '%'
               OR b.name LIKE '%' || :query || '%')
        ORDER BY t.date DESC, t.id DESC
        """
    )
    fun observeFiltered(
        from: LocalDate,
        to: LocalDate,
        types: List<String>,
        accountId: Long?,
        categoryId: Long?,
        query: String,
    ): Flow<List<TransactionListItem>>


    /**
     * Bevétel/kiadás összegek a [from]..[to] (zárt) intervallumban, a **számla pénznemében**
     * csoportosítva. Így a rögzítéskor használt (akár kézi) árfolyam számít, és idegen
     * pénznemű tételhez sem kell utólag árfolyam.
     */
    @Query(
        """
        SELECT t.type AS type, a.currencyCode AS currencyCode, SUM(t.accountAmountMinor) AS totalMinor
        FROM transactions t
        JOIN accounts a ON a.id = t.accountId
        WHERE t.type IN ('INCOME', 'EXPENSE') AND t.date BETWEEN :from AND :to
        GROUP BY t.type, a.currencyCode
        """
    )
    fun observeTotals(from: LocalDate, to: LocalDate): Flow<List<TypeCurrencyTotal>>

    /** Kiadások kategóriánként a számla pénznemében (költségkeret „elköltött” értékéhez). */
    @Query(
        """
        SELECT t.categoryId AS categoryId, a.currencyCode AS currencyCode, SUM(t.accountAmountMinor) AS totalMinor
        FROM transactions t
        JOIN accounts a ON a.id = t.accountId
        WHERE t.type = 'EXPENSE' AND t.categoryId IS NOT NULL AND t.date BETWEEN :from AND :to
        GROUP BY t.categoryId, a.currencyCode
        """
    )
    fun observeExpenseByCategory(from: LocalDate, to: LocalDate): Flow<List<CategoryCurrencyTotal>>

    @Insert
    suspend fun insert(transaction: TransactionEntity): Long

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Delete
    suspend fun delete(transaction: TransactionEntity)

    // ------------------------------------------------------------------ Kimutatások

    /**
     * Összeg kategóriánként (a számla pénznemében) egy típusra és időszakra.
     * A magánkölcsön-mozgások és a banki hitel tételei (kategória nélküliek) külön csoportba kerülnek:
     * id = -1 (ReportCalculator.LOAN_GROUP_ID).
     */
    @Query(
        """
        SELECT CASE WHEN t.loanId IS NOT NULL OR t.bankLoanId IS NOT NULL THEN -1 ELSE t.categoryId END AS id,
               a.currencyCode AS currencyCode, SUM(t.accountAmountMinor) AS amountMinor
        FROM transactions t JOIN accounts a ON a.id = t.accountId
        WHERE t.type = :type AND t.date BETWEEN :from AND :to
        GROUP BY CASE WHEN t.loanId IS NOT NULL OR t.bankLoanId IS NOT NULL THEN -1 ELSE t.categoryId END, a.currencyCode
        """
    )
    fun observeCategoryTotals(type: String, from: LocalDate, to: LocalDate): Flow<List<GroupTotal>>

    /** Egy kategórián belül alkategóriánként (id = null: alkategória nélkül). */
    @Query(
        """
        SELECT t.subcategoryId AS id, a.currencyCode AS currencyCode, SUM(t.accountAmountMinor) AS amountMinor
        FROM transactions t JOIN accounts a ON a.id = t.accountId
        WHERE t.categoryId = :categoryId AND t.type = :type AND t.date BETWEEN :from AND :to
        GROUP BY t.subcategoryId, a.currencyCode
        """
    )
    fun observeSubcategoryTotals(categoryId: Long, type: String, from: LocalDate, to: LocalDate): Flow<List<GroupTotal>>

    /** Bevétel/kiadás tételek a havi oszlopdiagramhoz (a számla pénznemében). */
    @Query(
        """
        SELECT t.date AS date, t.type AS type, t.accountAmountMinor AS amountMinor, a.currencyCode AS currencyCode
        FROM transactions t JOIN accounts a ON a.id = t.accountId
        WHERE t.type IN ('INCOME', 'EXPENSE') AND t.date BETWEEN :from AND :to
        """
    )
    fun observeAmountRows(from: LocalDate, to: LocalDate): Flow<List<AmountRow>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): TransactionEntity?

    @Query("SELECT * FROM exchange_details WHERE transactionId = :transactionId")
    suspend fun getExchangeDetail(transactionId: Long): ExchangeDetailEntity?

    /** A váltás cél oldala CASCADE-del törlődik vele együtt. */
    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Insert
    suspend fun insertExchangeDetail(detail: ExchangeDetailEntity)

    @Update
    suspend fun updateExchangeDetail(detail: ExchangeDetailEntity)

    /** Meglévő váltás módosítása egy tranzakcióban. */
    @Transaction
    suspend fun updateExchange(transaction: TransactionEntity, detail: ExchangeDetailEntity) {
        update(transaction)
        updateExchangeDetail(detail.copy(transactionId = transaction.id))
    }

    /**
     * Meglévő bevétel/kiadás módosítása egy tranzakcióban: az eredeti beolvasása és a
     * felülírás atomi. A létrehozási idő, az ismétlődő-szabály és a banki hitel hivatkozás megmarad.
     */
    @Transaction
    suspend fun updateIncomeOrExpenseKeepingMeta(id: Long, edited: TransactionEntity) {
        val original = checkNotNull(getById(id)) { "Nincs ilyen tétel: $id" }
        require(original.type == edited.type) { "A tétel típusa nem változhat" }
        update(
            edited.copy(
                id = id,
                createdAt = original.createdAt,
                recurringRuleId = original.recurringRuleId,
                bankLoanId = original.bankLoanId,
            )
        )
    }

    /** Meglévő váltás módosítása egy tranzakcióban, az eredeti létrehozási idő megtartásával. */
    @Transaction
    suspend fun updateExchangeKeepingMeta(id: Long, transaction: TransactionEntity, detail: ExchangeDetailEntity) {
        val original = checkNotNull(getById(id)) { "Nincs ilyen tétel: $id" }
        require(original.type == TransactionType.EXCHANGE)
        updateExchange(transaction.copy(id = id, createdAt = original.createdAt), detail)
    }

    /** Váltás mentése egy tranzakcióban: forrás tétel (díjjal együtt) + cél oldal. */
    @Transaction
    suspend fun insertExchange(transaction: TransactionEntity, detail: ExchangeDetailEntity): Long {
        val id = insert(transaction)
        insertExchangeDetail(detail.copy(transactionId = id))
        return id
    }
}
