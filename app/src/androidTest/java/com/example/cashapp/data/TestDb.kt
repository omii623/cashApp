package com.example.cashapp.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.cashapp.data.local.CashDatabase
import com.example.cashapp.data.local.entity.AccountEntity
import com.example.cashapp.data.local.entity.CategoryEntity
import com.example.cashapp.data.local.entity.CurrencyEntity
import com.example.cashapp.domain.model.CategoryType

/** Közös segédek a műszeres tesztekhez: memóriabeli adatbázis + alapadatok. */
object TestDb {
    val context: Context get() = ApplicationProvider.getApplicationContext()

    fun inMemory(): CashDatabase =
        Room.inMemoryDatabaseBuilder(context, CashDatabase::class.java)
            .allowMainThreadQueries()
            .build()

    /** Egy HUF pénznem, egy számla és egy kiadás kategória; visszaadja: (számla id, kategória id). */
    suspend fun seedBasics(db: CashDatabase): Pair<Long, Long> {
        db.currencyDao().insertAll(listOf(CurrencyEntity("HUF", "Forint", "Ft", 0)))
        val accountId = db.accountDao().insert(AccountEntity(name = "Pénztárca", currencyCode = "HUF", color = 0xFF000000, icon = "wallet"))
        val categoryId = db.categoryDao().insert(CategoryEntity(name = "Élelmiszer", type = CategoryType.EXPENSE, icon = "food", color = 0xFF00FF00))
        return accountId to categoryId
    }
}
