package com.example.cashapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.example.cashapp.data.local.entity.CurrencyEntity
import com.example.cashapp.data.local.entity.ExchangeRateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CurrencyDao {
    @Query("SELECT * FROM currencies ORDER BY sortOrder")
    fun observeAll(): Flow<List<CurrencyEntity>>

    @Query("SELECT * FROM currencies ORDER BY sortOrder")
    suspend fun getAll(): List<CurrencyEntity>

    @Query("SELECT COUNT(*) FROM currencies")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<CurrencyEntity>)

    @Upsert
    suspend fun upsert(item: CurrencyEntity)

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM currencies")
    suspend fun maxSortOrder(): Int

    /** Hány tétel / számla / szabály / keret / kölcsön használja a pénznemet (ha > 0: nem törölhető). */
    @Query(
        """
        SELECT (SELECT COUNT(*) FROM accounts WHERE currencyCode = :code)
             + (SELECT COUNT(*) FROM transactions WHERE currencyCode = :code)
             + (SELECT COUNT(*) FROM exchange_details WHERE targetCurrencyCode = :code)
             + (SELECT COUNT(*) FROM recurring_rules WHERE currencyCode = :code)
             + (SELECT COUNT(*) FROM budgets WHERE currencyCode = :code)
             + (SELECT COUNT(*) FROM loans WHERE currencyCode = :code)
        """
    )
    suspend fun usageCount(code: String): Int

    @Query("DELETE FROM currencies WHERE code = :code")
    suspend fun delete(code: String)
}

@Dao
interface ExchangeRateDao {
    @Query("SELECT * FROM exchange_rates")
    fun observeAll(): Flow<List<ExchangeRateEntity>>

    @Upsert
    suspend fun upsertAll(items: List<ExchangeRateEntity>)

    @Query("SELECT * FROM exchange_rates")
    suspend fun getAll(): List<ExchangeRateEntity>

    @Query("DELETE FROM exchange_rates WHERE baseCode = :code OR quoteCode = :code")
    suspend fun deleteFor(code: String)

    /** Régi kézi felülírások eltávolítása: az árfolyamok mindig a lekért, aktuális értékek. */
    @Query("UPDATE exchange_rates SET manualRate = NULL WHERE manualRate IS NOT NULL")
    suspend fun clearManualRates()

    @Query("DELETE FROM exchange_rates WHERE apiRate IS NULL")
    suspend fun deleteWithoutApiRate()

    @Query("SELECT MAX(fetchedAt) FROM exchange_rates")
    fun observeLastFetchedAt(): Flow<Long?>

    /** API-ból jött árfolyamok mentése; a régi kézi felülírások törlődnek. */
    @Transaction
    suspend fun saveApiRates(base: String, rates: Map<String, Double>, fetchedAt: Long) {
        clearManualRates()
        deleteWithoutApiRate()
        upsertAll(rates.map { (quote, rate) -> ExchangeRateEntity(base, quote, apiRate = rate, fetchedAt = fetchedAt) })
    }
}
