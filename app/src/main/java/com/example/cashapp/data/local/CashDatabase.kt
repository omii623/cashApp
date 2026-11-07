package com.example.cashapp.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.cashapp.data.local.dao.AccountDao
import com.example.cashapp.data.local.dao.BudgetDao
import com.example.cashapp.data.local.dao.CategoryDao
import com.example.cashapp.data.local.dao.CurrencyDao
import com.example.cashapp.data.local.dao.ExchangeRateDao
import com.example.cashapp.data.local.dao.RecurringDao
import com.example.cashapp.data.local.dao.TransactionDao
import com.example.cashapp.data.local.entity.AccountEntity
import com.example.cashapp.data.local.entity.BudgetEntity
import com.example.cashapp.data.local.entity.CategoryEntity
import com.example.cashapp.data.local.entity.CurrencyEntity
import com.example.cashapp.data.local.entity.ExchangeDetailEntity
import com.example.cashapp.data.local.entity.ExchangeRateEntity
import com.example.cashapp.data.local.entity.PendingRecurringEntity
import com.example.cashapp.data.local.entity.RecurringRuleEntity
import com.example.cashapp.data.local.entity.SubcategoryEntity
import com.example.cashapp.data.local.entity.TransactionEntity
import java.time.LocalDate

/**
 * Séma módosításakor: [version] emelése + Migration hozzáadása a [build] függvényben.
 * A sémák JSON-ban az `app/schemas` mappába kerülnek (verziókövetendő).
 */
@Database(
    entities = [
        CurrencyEntity::class,
        AccountEntity::class,
        CategoryEntity::class,
        SubcategoryEntity::class,
        TransactionEntity::class,
        ExchangeDetailEntity::class,
        ExchangeRateEntity::class,
        RecurringRuleEntity::class,
        PendingRecurringEntity::class,
        BudgetEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class CashDatabase : RoomDatabase() {
    abstract fun currencyDao(): CurrencyDao
    abstract fun exchangeRateDao(): ExchangeRateDao
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun recurringDao(): RecurringDao
    abstract fun budgetDao(): BudgetDao

    companion object {
        private const val NAME = "cashapp.db"

        fun build(context: Context): CashDatabase =
            Room.databaseBuilder(context, CashDatabase::class.java, NAME)
                .addMigrations(*Migrations.ALL)
                .build()
    }
}

/** Séma-migrációk. Új verziónál: új Migration ide + az [ALL] listába. Meglévőt ne módosíts! */
object Migrations {

    /**
     * v1 → v2: a váltási díj a váltás része lett (exchange_details.feeMinor).
     * A régi, külön kiadásként rögzített díjak megmaradnak kiadásnak – nem írjuk át őket.
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE exchange_details ADD COLUMN feeMinor INTEGER NOT NULL DEFAULT 0")
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}

class Converters {
    @TypeConverter
    fun fromLocalDate(date: LocalDate?): Long? = date?.toEpochDay()

    @TypeConverter
    fun toLocalDate(epochDay: Long?): LocalDate? = epochDay?.let(LocalDate::ofEpochDay)
}
