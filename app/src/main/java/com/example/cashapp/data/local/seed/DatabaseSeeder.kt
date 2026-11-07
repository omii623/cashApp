package com.example.cashapp.data.local.seed

import androidx.room.withTransaction
import com.example.cashapp.data.local.CashDatabase
import com.example.cashapp.data.local.entity.AccountEntity
import com.example.cashapp.data.local.entity.CategoryEntity
import com.example.cashapp.data.local.entity.SubcategoryEntity

/** Üres adatbázis feltöltése az alapadatokkal. Idempotens: csak üres DB-n fut. */
class DatabaseSeeder(private val db: CashDatabase) {

    suspend fun seedIfEmpty() {
        if (db.currencyDao().count() > 0) return
        db.withTransaction {
            db.currencyDao().insertAll(DefaultData.currencies)

            db.accountDao().insertAll(
                DefaultData.accounts.mapIndexed { i, a ->
                    AccountEntity(
                        name = a.name, currencyCode = a.currencyCode,
                        icon = a.icon, color = a.color, sortOrder = i,
                    )
                }
            )

            val categoryDao = db.categoryDao()
            DefaultData.categories.forEachIndexed { i, c ->
                val id = categoryDao.insert(
                    CategoryEntity(name = c.name, type = c.type, icon = c.icon, color = c.color, sortOrder = i)
                )
                categoryDao.insertSubcategories(
                    c.subcategories.mapIndexed { j, name ->
                        SubcategoryEntity(categoryId = id, name = name, sortOrder = j)
                    }
                )
            }
        }
    }
}
