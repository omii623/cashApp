package com.example.cashapp.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.cashapp.data.local.entity.AccountEntity
import com.example.cashapp.data.local.model.AccountWithBalance
import kotlinx.coroutines.flow.Flow

/** Számla egyenlege SQL-ben (az `a` alias a számla). Egy helyen, hogy minden lekérdezés ugyanígy számoljon. */
private const val ACCOUNT_BALANCE_SQL = """a.initialBalanceMinor
    + COALESCE((SELECT SUM(t.accountAmountMinor) FROM transactions t
                WHERE t.accountId = a.id AND t.type = 'INCOME'), 0)
    - COALESCE((SELECT SUM(t.accountAmountMinor) FROM transactions t
                WHERE t.accountId = a.id AND t.type IN ('EXPENSE', 'EXCHANGE')), 0)
    + COALESCE((SELECT SUM(e.targetAmountMinor) FROM exchange_details e
                WHERE e.targetAccountId = a.id), 0)"""

@Dao
interface AccountDao {

    /**
     * Egyenleg = kezdő egyenleg + bevételek − kiadások − kimenő váltások + bejövő váltások,
     * mind a számla saját pénznemében. (Ugyanez a képlet van az alábbi lekérdezésekben is.)
     */
    @Query(
        """
        SELECT a.*, $ACCOUNT_BALANCE_SQL AS balanceMinor
        FROM accounts a
        WHERE a.isArchived = 0
        ORDER BY a.sortOrder, a.id
        """
    )
    fun observeActiveWithBalance(): Flow<List<AccountWithBalance>>

    /** Az összes számla (archiváltak is) egyenleggel – a Számlák oldalhoz. */
    @Query(
        """
        SELECT a.*, $ACCOUNT_BALANCE_SQL AS balanceMinor
        FROM accounts a
        ORDER BY a.isArchived, a.sortOrder, a.id
        """
    )
    fun observeAllWithBalance(): Flow<List<AccountWithBalance>>

    @Query("SELECT a.*, $ACCOUNT_BALANCE_SQL AS balanceMinor FROM accounts a WHERE a.id = :id")
    fun observeWithBalance(id: Long): Flow<AccountWithBalance?>

    @Query("SELECT * FROM accounts WHERE isArchived = 0 ORDER BY sortOrder, id")
    fun observeActive(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE isArchived = 0 ORDER BY sortOrder, id")
    suspend fun getActive(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getById(id: Long): AccountEntity?

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM accounts")
    suspend fun maxSortOrder(): Int

    /** Hány tétel / szabály hivatkozik a számlára (ha > 0: csak archiválható, pénzneme nem váltható). */
    @Query(
        """
        SELECT (SELECT COUNT(*) FROM transactions WHERE accountId = :id)
             + (SELECT COUNT(*) FROM exchange_details WHERE targetAccountId = :id)
             + (SELECT COUNT(*) FROM recurring_rules WHERE accountId = :id)
        """
    )
    suspend fun usageCount(id: Long): Int

    @Insert
    suspend fun insert(account: AccountEntity): Long

    @Insert
    suspend fun insertAll(accounts: List<AccountEntity>)

    @Update
    suspend fun update(account: AccountEntity)

    @Update
    suspend fun updateAll(accounts: List<AccountEntity>)

    @Delete
    suspend fun delete(account: AccountEntity)
}
