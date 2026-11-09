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
import com.example.cashapp.data.local.dao.LoanDao
import com.example.cashapp.data.local.dao.ExchangeRateDao
import com.example.cashapp.data.local.dao.RecurringDao
import com.example.cashapp.data.local.dao.TransactionDao
import com.example.cashapp.data.local.entity.AccountEntity
import com.example.cashapp.data.local.entity.BudgetEntity
import com.example.cashapp.data.local.entity.CategoryEntity
import com.example.cashapp.data.local.entity.CurrencyEntity
import com.example.cashapp.data.local.entity.ExchangeDetailEntity
import com.example.cashapp.data.local.entity.ExchangeRateEntity
import com.example.cashapp.data.local.entity.LoanEntity
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
        LoanEntity::class,
    ],
    version = 4,
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
    abstract fun loanDao(): LoanDao

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

    /**
     * v2 → v3: egyedi index a pending_recurring (ruleId, dueDate) párosra.
     * Előtte a duplikátumokat töröljük: esedékességenként a már elbírált (nem PENDING) sor marad,
     * ha nincs ilyen, a legkisebb azonosítójú.
     */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                DELETE FROM pending_recurring
                WHERE id NOT IN (
                    SELECT (
                        SELECT p2.id FROM pending_recurring p2
                        WHERE p2.ruleId = p1.ruleId AND p2.dueDate = p1.dueDate
                        ORDER BY CASE WHEN p2.status = 'PENDING' THEN 1 ELSE 0 END, p2.id
                        LIMIT 1
                    )
                    FROM pending_recurring p1
                    GROUP BY p1.ruleId, p1.dueDate
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_pending_recurring_ruleId_dueDate` " +
                    "ON `pending_recurring` (`ruleId`, `dueDate`)"
            )
        }
    }

    /**
     * v3 → v4: magánkölcsönök. Új `loans` tábla, és a tételek opcionálisan egy kölcsönhöz
     * tartozhatnak (transactions.loanId). A meglévő tételek loanId-ja NULL marad.
     */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `loans` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`personName` TEXT NOT NULL, `direction` TEXT NOT NULL, `currencyCode` TEXT NOT NULL, " +
                    "`openingAmountMinor` INTEGER NOT NULL, `openingRepaidMinor` INTEGER NOT NULL, `startDate` INTEGER NOT NULL, `note` TEXT NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL, `closedDate` INTEGER, `color` INTEGER, `icon` TEXT, " +
                    "FOREIGN KEY(`currencyCode`) REFERENCES `currencies`(`code`) ON UPDATE NO ACTION ON DELETE NO ACTION )"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_loans_currencyCode` ON `loans` (`currencyCode`)")
            db.execSQL(
                "ALTER TABLE `transactions` ADD COLUMN `loanId` INTEGER DEFAULT NULL " +
                    "REFERENCES `loans`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_loanId` ON `transactions` (`loanId`)")
            db.execSQL("ALTER TABLE `transactions` ADD COLUMN `loanOpening` TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE `transactions` ADD COLUMN `loanAmountMinor` INTEGER DEFAULT NULL")
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
}

class Converters {
    @TypeConverter
    fun fromLocalDate(date: LocalDate?): Long? = date?.toEpochDay()

    @TypeConverter
    fun toLocalDate(epochDay: Long?): LocalDate? = epochDay?.let(LocalDate::ofEpochDay)
}
