package com.example.cashapp.data.repository

import com.example.cashapp.data.local.dao.TransactionDao
import com.example.cashapp.data.local.entity.ExchangeDetailEntity
import com.example.cashapp.data.local.entity.TransactionEntity
import com.example.cashapp.data.local.model.CategoryCurrencyTotal
import com.example.cashapp.data.local.model.TransactionListItem
import com.example.cashapp.data.local.model.TypeCurrencyTotal
import com.example.cashapp.domain.model.TransactionFilter
import com.example.cashapp.domain.model.TransactionType
import java.time.YearMonth
import kotlinx.coroutines.flow.Flow

class TransactionRepository(private val transactionDao: TransactionDao) {

    fun observeRecent(limit: Int): Flow<List<TransactionListItem>> = transactionDao.observeRecent(limit)

    fun observeForAccount(accountId: Long): Flow<List<TransactionListItem>> = transactionDao.observeForAccount(accountId)

    fun observeMonthTotals(month: YearMonth): Flow<List<TypeCurrencyTotal>> =
        transactionDao.observeTotals(month.atDay(1), month.atEndOfMonth())

    fun observeMonthExpenseByCategory(month: YearMonth): Flow<List<CategoryCurrencyTotal>> =
        transactionDao.observeExpenseByCategory(month.atDay(1), month.atEndOfMonth())

    fun observeFiltered(filter: TransactionFilter): Flow<List<TransactionListItem>> =
        transactionDao.observeFiltered(
            from = filter.month.atDay(1),
            to = filter.month.atEndOfMonth(),
            types = (filter.type?.let(::listOf) ?: TransactionType.entries).map { it.name },
            accountId = filter.accountId,
            categoryId = filter.categoryId,
            query = filter.query.trim(),
        )

    suspend fun get(id: Long): TransactionEntity? = transactionDao.getById(id)

    suspend fun getExchangeDetail(transactionId: Long): ExchangeDetailEntity? = transactionDao.getExchangeDetail(transactionId)

    /** Tétel törlése (váltásnál a cél oldallal együtt). */
    suspend fun delete(id: Long) = transactionDao.deleteById(id)

    /**
     * Meglévő bevétel/kiadás módosítása. Az eredeti létrehozási idő és ismétlődő-szabály
     * hivatkozás megmarad (azokat az űrlap nem szerkeszti).
     */
    suspend fun updateIncomeOrExpense(id: Long, edited: TransactionEntity) {
        val original = checkNotNull(transactionDao.getById(id)) { "Nincs ilyen tétel: $id" }
        require(original.type == edited.type) { "A tétel típusa nem változhat" }
        require(edited.amountMinor > 0 && edited.accountAmountMinor > 0) { "Az összeg legyen pozitív" }
        transactionDao.update(edited.copy(id = id, createdAt = original.createdAt, recurringRuleId = original.recurringRuleId))
    }

    /** Meglévő váltás módosítása (forrás tétel + cél oldal). */
    suspend fun updateExchange(id: Long, transaction: TransactionEntity, detail: ExchangeDetailEntity) {
        val original = checkNotNull(transactionDao.getById(id)) { "Nincs ilyen tétel: $id" }
        require(original.type == TransactionType.EXCHANGE)
        validateExchange(transaction, detail)
        transactionDao.updateExchange(transaction.copy(id = id, createdAt = original.createdAt), detail)
    }

    /** Bevétel vagy kiadás mentése (a váltásnak cél oldala is van → [saveExchange]). */
    suspend fun saveIncomeOrExpense(transaction: TransactionEntity): Long {
        require(transaction.type != TransactionType.EXCHANGE) { "Váltást nem ezzel kell menteni" }
        require(transaction.amountMinor > 0 && transaction.accountAmountMinor > 0) { "Az összeg legyen pozitív" }
        return transactionDao.insert(transaction)
    }

    /**
     * Pénzváltás mentése egy adatbázis-tranzakcióban: forrás tétel + cél oldal.
     * A díj a forrás tétel összegében van ([ExchangeDetailEntity.feeMinor] csak nyilvántartja).
     */
    suspend fun saveExchange(transaction: TransactionEntity, detail: ExchangeDetailEntity): Long {
        validateExchange(transaction, detail)
        return transactionDao.insertExchange(transaction, detail)
    }

    private fun validateExchange(transaction: TransactionEntity, detail: ExchangeDetailEntity) {
        require(transaction.type == TransactionType.EXCHANGE)
        require(transaction.amountMinor > 0 && detail.targetAmountMinor > 0) { "Az összeg legyen pozitív" }
        require(transaction.accountId != detail.targetAccountId) { "A két számla nem lehet ugyanaz" }
        require(detail.feeMinor in 0 until transaction.amountMinor) { "A díj nem lehet negatív vagy nagyobb az összegnél" }
    }
}
