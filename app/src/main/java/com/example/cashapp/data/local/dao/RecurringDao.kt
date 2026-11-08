package com.example.cashapp.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.cashapp.data.local.entity.BudgetEntity
import com.example.cashapp.data.local.entity.PendingRecurringEntity
import com.example.cashapp.data.local.entity.RecurringRuleEntity
import com.example.cashapp.data.local.model.PendingItem
import com.example.cashapp.data.local.model.RuleItem
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringDao {

    /** Jóváhagyásra váró (és már esedékes) tételek száma. Az elhalasztott, még nem esedékes nem számít. */
    @Query(
        """
        SELECT COUNT(*) FROM pending_recurring
        WHERE status = 'PENDING' AND COALESCE(postponedTo, dueDate) <= :today
        """
    )
    fun observePendingCount(today: LocalDate): Flow<Int>

    /** Minden jóváhagyásra váró tétel (az elhalasztottak is), esedékesség szerint. */
    @Query(
        """
        SELECT p.id, p.ruleId, p.dueDate, p.postponedTo,
               r.name AS ruleName, r.type, r.amountMinor, r.currencyCode, r.accountId,
               a.name AS accountName,
               c.name AS categoryName, c.color AS categoryColor, c.icon AS categoryIcon,
               s.name AS subcategoryName
        FROM pending_recurring p
        JOIN recurring_rules r ON r.id = p.ruleId
        JOIN accounts a ON a.id = r.accountId
        JOIN categories c ON c.id = r.categoryId
        LEFT JOIN subcategories s ON s.id = r.subcategoryId
        WHERE p.status = 'PENDING'
        ORDER BY COALESCE(p.postponedTo, p.dueDate), p.id
        """
    )
    fun observePending(): Flow<List<PendingItem>>

    @Query(
        """
        SELECT r.*, a.name AS accountName,
               c.name AS categoryName, c.color AS categoryColor, c.icon AS categoryIcon
        FROM recurring_rules r
        JOIN accounts a ON a.id = r.accountId
        JOIN categories c ON c.id = r.categoryId
        ORDER BY r.isActive DESC, r.nextDueDate, r.id
        """
    )
    fun observeRules(): Flow<List<RuleItem>>

    @Query("SELECT * FROM recurring_rules WHERE id = :id")
    suspend fun getRule(id: Long): RecurringRuleEntity?

    @Query("SELECT * FROM recurring_rules WHERE isActive = 1 AND nextDueDate <= :today")
    suspend fun getDueRules(today: LocalDate): List<RecurringRuleEntity>

    @Query("SELECT * FROM pending_recurring WHERE id = :id")
    suspend fun getPending(id: Long): PendingRecurringEntity?

    @Insert
    suspend fun insertRule(rule: RecurringRuleEntity): Long

    @Update
    suspend fun updateRule(rule: RecurringRuleEntity)

    /** A függő tételek CASCADE-del törlődnek; a már jóváhagyott tételek megmaradnak (szabály nélkül). */
    @Delete
    suspend fun deleteRule(rule: RecurringRuleEntity)

    /** A már létező (ruleId, dueDate) tételt kihagyja – így az ismételt generálás nem duplikál. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPendingAll(items: List<PendingRecurringEntity>)

    @Update
    suspend fun updatePending(item: PendingRecurringEntity)

    /** Szabály módosításakor a még el nem bírált tételei elavulnak – ezeket töröljük. */
    @Query("DELETE FROM pending_recurring WHERE ruleId = :ruleId AND status = 'PENDING'")
    suspend fun deletePendingOfRule(ruleId: Long)
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets WHERE isActive = 1")
    fun observeActive(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE categoryId = :categoryId")
    suspend fun getForCategory(categoryId: Long): BudgetEntity?

    @Insert
    suspend fun insert(budget: BudgetEntity): Long

    @Update
    suspend fun update(budget: BudgetEntity)

    @Query("DELETE FROM budgets WHERE categoryId = :categoryId")
    suspend fun deleteForCategory(categoryId: Long)
}
