package com.example.cashapp.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.cashapp.data.local.entity.CategoryEntity
import com.example.cashapp.data.local.entity.SubcategoryEntity
import com.example.cashapp.domain.model.CategoryType
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE type = :type AND isArchived = 0 ORDER BY sortOrder, id")
    fun observeActive(type: CategoryType): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE type = :type AND isArchived = 0 ORDER BY sortOrder, id")
    suspend fun getActive(type: CategoryType): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: Long): CategoryEntity?

    /** Archivált is (szerkesztésnél a tétel eredeti alkategóriája). */
    @Query("SELECT * FROM subcategories WHERE id = :id")
    suspend fun getSubcategoryById(id: Long): SubcategoryEntity?

    @Query("SELECT * FROM subcategories WHERE categoryId = :categoryId AND isArchived = 0 ORDER BY sortOrder, id")
    fun observeSubcategories(categoryId: Long): Flow<List<SubcategoryEntity>>

    @Query("SELECT * FROM subcategories WHERE categoryId = :categoryId AND isArchived = 0 ORDER BY sortOrder, id")
    suspend fun getSubcategories(categoryId: Long): List<SubcategoryEntity>

    /** Egy típus összes aktív alkategóriája (az űrlap egyszerre tölti be, kategóriaváltáskor nem kérdez újra). */
    @Query(
        """
        SELECT s.* FROM subcategories s
        JOIN categories c ON c.id = s.categoryId
        WHERE c.type = :type AND c.isArchived = 0 AND s.isArchived = 0
        ORDER BY s.sortOrder, s.id
        """
    )
    fun observeSubcategoriesOfType(type: CategoryType): Flow<List<SubcategoryEntity>>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM categories WHERE type = :type")
    suspend fun maxSortOrder(type: CategoryType): Int

    // --- Használat: ha van rá hivatkozás, törlés helyett archiválunk (a régi tételek megmaradnak).

    @Query(
        """
        SELECT (SELECT COUNT(*) FROM transactions WHERE categoryId = :id)
             + (SELECT COUNT(*) FROM recurring_rules WHERE categoryId = :id)
        """
    )
    suspend fun usageCount(id: Long): Int

    @Query(
        """
        SELECT (SELECT COUNT(*) FROM transactions WHERE subcategoryId = :id)
             + (SELECT COUNT(*) FROM recurring_rules WHERE subcategoryId = :id)
        """
    )
    suspend fun subcategoryUsageCount(id: Long): Int

    @Insert
    suspend fun insert(category: CategoryEntity): Long

    @Update
    suspend fun update(category: CategoryEntity)

    @Update
    suspend fun updateAll(categories: List<CategoryEntity>)

    @Delete
    suspend fun delete(category: CategoryEntity)

    @Insert
    suspend fun insertSubcategories(items: List<SubcategoryEntity>)

    @Insert
    suspend fun insertSubcategory(item: SubcategoryEntity): Long

    @Update
    suspend fun updateSubcategory(item: SubcategoryEntity)

    @Delete
    suspend fun deleteSubcategory(item: SubcategoryEntity)
}
