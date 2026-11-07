package com.example.cashapp.data.repository

import androidx.room.withTransaction
import com.example.cashapp.data.local.CashDatabase
import com.example.cashapp.data.local.entity.BudgetEntity
import com.example.cashapp.data.local.entity.CategoryEntity
import com.example.cashapp.data.local.entity.SubcategoryEntity
import com.example.cashapp.domain.model.CategoryType
import kotlinx.coroutines.flow.Flow

/**
 * Kategóriák, alkategóriák és a hozzájuk tartozó költségkeret kezelése.
 *
 * Törlési szabály: ha egy kategóriára / alkategóriára tétel vagy ismétlődő szabály hivatkozik,
 * nem töröljük, csak archiváljuk (eltűnik a listákból, a régi tételeknél megmarad a neve).
 */
class CategoryRepository(private val db: CashDatabase) {

    private val categoryDao = db.categoryDao()
    private val budgetDao = db.budgetDao()

    fun observeCategories(type: CategoryType): Flow<List<CategoryEntity>> = categoryDao.observeActive(type)

    fun observeSubcategories(type: CategoryType): Flow<List<SubcategoryEntity>> =
        categoryDao.observeSubcategoriesOfType(type)

    /** Archivált is (szerkesztésnél a tétel eredeti kategóriája). */
    suspend fun getCategory(id: Long): CategoryEntity? = categoryDao.getById(id)

    /** Archivált is (szerkesztésnél a tétel eredeti alkategóriája). */
    suspend fun getSubcategory(id: Long): SubcategoryEntity? = categoryDao.getSubcategoryById(id)

    fun observeBudgets(): Flow<List<BudgetEntity>> = budgetDao.observeActive()

    /** Szerkesztéshez: kategória + alkategóriák + keret egyben. */
    suspend fun loadForEdit(id: Long): CategoryForEdit? {
        val category = categoryDao.getById(id) ?: return null
        return CategoryForEdit(category, categoryDao.getSubcategories(id), budgetDao.getForCategory(id))
    }

    data class CategoryForEdit(
        val category: CategoryEntity,
        val subcategories: List<SubcategoryEntity>,
        val budget: BudgetEntity?,
    )

    /** Mentendő kategória. [id] = null → új kategória. */
    data class CategoryDraft(
        val id: Long?,
        val name: String,
        val type: CategoryType,
        val color: Long,
        val icon: String,
        /** Az alkategóriák a kívánt sorrendben; id = null → új. */
        val subcategories: List<SubcategoryDraft>,
        /** Kitörölt meglévő alkategóriák. */
        val removedSubcategoryIds: List<Long>,
        /** null → nincs keret (a meglévő törlődik). */
        val budget: BudgetDraft?,
    )

    data class SubcategoryDraft(val id: Long?, val name: String)

    data class BudgetDraft(
        val limitMinor: Long,
        val currencyCode: String,
        val warnThresholdPercent: Int,
        val notify: Boolean,
    )

    /** Kategória mentése minden kapcsolódó változással együtt, egy tranzakcióban. Visszaadja az id-t. */
    suspend fun save(draft: CategoryDraft): Long = db.withTransaction {
        val categoryId = if (draft.id == null) {
            categoryDao.insert(
                CategoryEntity(
                    name = draft.name, type = draft.type, icon = draft.icon, color = draft.color,
                    sortOrder = categoryDao.maxSortOrder(draft.type) + 1,
                )
            )
        } else {
            val existing = checkNotNull(categoryDao.getById(draft.id)) { "Nincs ilyen kategória: ${draft.id}" }
            categoryDao.update(existing.copy(name = draft.name, color = draft.color, icon = draft.icon))
            draft.id
        }

        val existingSubs = categoryDao.getSubcategories(categoryId).associateBy { it.id }
        draft.removedSubcategoryIds.forEach { id -> existingSubs[id]?.let { removeSubcategory(it) } }
        draft.subcategories.forEachIndexed { index, sub ->
            val existing = sub.id?.let { existingSubs[it] }
            if (existing == null) {
                categoryDao.insertSubcategory(SubcategoryEntity(categoryId = categoryId, name = sub.name, sortOrder = index))
            } else if (existing.name != sub.name || existing.sortOrder != index) {
                categoryDao.updateSubcategory(existing.copy(name = sub.name, sortOrder = index))
            }
        }

        val budget = draft.budget
        if (budget == null) {
            budgetDao.deleteForCategory(categoryId)
        } else {
            val existing = budgetDao.getForCategory(categoryId)
            val entity = BudgetEntity(
                id = existing?.id ?: 0,
                categoryId = categoryId,
                limitMinor = budget.limitMinor,
                currencyCode = budget.currencyCode,
                warnThresholdPercent = budget.warnThresholdPercent,
                notify = budget.notify,
            )
            if (existing == null) budgetDao.insert(entity) else budgetDao.update(entity)
        }
        categoryId
    }

    /** @return igaz, ha végleg törölve; hamis, ha használatban volt és csak archiválva lett. */
    suspend fun deleteCategory(id: Long): Boolean = db.withTransaction {
        val category = categoryDao.getById(id) ?: return@withTransaction true
        if (categoryDao.usageCount(id) > 0) {
            categoryDao.update(category.copy(isArchived = true))
            budgetDao.deleteForCategory(id)
            false
        } else {
            // Az alkategóriák és a keret CASCADE-del törlődnek.
            categoryDao.delete(category)
            true
        }
    }

    /** Sorrend mentése: a lista sorrendje lesz az új sortOrder. */
    suspend fun reorder(type: CategoryType, orderedIds: List<Long>) = db.withTransaction {
        val byId = categoryDao.getActive(type).associateBy { it.id }
        categoryDao.updateAll(orderedIds.mapIndexedNotNull { index, id -> byId[id]?.copy(sortOrder = index) })
    }

    /** Alkategória gyors átnevezése a listából. */
    suspend fun renameSubcategory(sub: SubcategoryEntity, name: String) =
        categoryDao.updateSubcategory(sub.copy(name = name))

    /** Új alkategória a lista végére. */
    suspend fun addSubcategory(categoryId: Long, name: String) = db.withTransaction {
        val order = categoryDao.getSubcategories(categoryId).maxOfOrNull { it.sortOrder + 1 } ?: 0
        categoryDao.insertSubcategory(SubcategoryEntity(categoryId = categoryId, name = name, sortOrder = order))
    }

    suspend fun deleteSubcategory(sub: SubcategoryEntity): Boolean = db.withTransaction { removeSubcategory(sub) }

    private suspend fun removeSubcategory(sub: SubcategoryEntity): Boolean =
        if (categoryDao.subcategoryUsageCount(sub.id) > 0) {
            categoryDao.updateSubcategory(sub.copy(isArchived = true))
            false
        } else {
            categoryDao.deleteSubcategory(sub)
            true
        }
}
