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
import com.example.cashapp.data.local.dao.BankLoanDao
import com.example.cashapp.data.local.dao.BudgetDao
import com.example.cashapp.data.local.dao.CategoryDao
import com.example.cashapp.data.local.dao.CurrencyDao
import com.example.cashapp.data.local.dao.LoanDao
import com.example.cashapp.data.local.dao.ExchangeRateDao
import com.example.cashapp.data.local.dao.RecurringDao
import com.example.cashapp.data.local.dao.TransactionDao
import com.example.cashapp.data.local.entity.AccountEntity
import com.example.cashapp.data.local.entity.BankLoanEntity
import com.example.cashapp.data.local.entity.BankLoanEventEntity
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
        BankLoanEntity::class,
        BankLoanEventEntity::class,
    ],
    version = 5,
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
    abstract fun bankLoanDao(): BankLoanDao

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

    /**
     * v4 → v5: banki kölcsönök. Új `bank_loans` és `bank_loan_events` tábla, és a tételek
     * opcionálisan egy banki hitelhez tartozhatnak (transactions.bankLoanId, törléskor NULL).
     */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Ismétlődő szabály kategória nélkül is lehet (banki hitel részletei) → a tábla újraépítése
            // (SQLite-ban a NOT NULL nem vehető le). A migráció alatt a külső kulcsok nincsenek érvényesítve,
            // így a régi tábla eldobása nem törli a függő tételeket.
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `recurring_rules_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`name` TEXT NOT NULL, `type` TEXT NOT NULL, `amountMinor` INTEGER NOT NULL, `currencyCode` TEXT NOT NULL, " +
                    "`accountId` INTEGER NOT NULL, `categoryId` INTEGER, `subcategoryId` INTEGER, `note` TEXT NOT NULL, " +
                    "`frequency` TEXT NOT NULL, `interval` INTEGER NOT NULL, `startDate` INTEGER NOT NULL, `endDate` INTEGER, " +
                    "`nextDueDate` INTEGER NOT NULL, `isActive` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION , " +
                    "FOREIGN KEY(`currencyCode`) REFERENCES `currencies`(`code`) ON UPDATE NO ACTION ON DELETE NO ACTION , " +
                    "FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION , " +
                    "FOREIGN KEY(`subcategoryId`) REFERENCES `subcategories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )"
            )
            db.execSQL(
                "INSERT INTO `recurring_rules_new` (`id`, `name`, `type`, `amountMinor`, `currencyCode`, `accountId`, " +
                    "`categoryId`, `subcategoryId`, `note`, `frequency`, `interval`, `startDate`, `endDate`, `nextDueDate`, `isActive`) " +
                    "SELECT `id`, `name`, `type`, `amountMinor`, `currencyCode`, `accountId`, `categoryId`, `subcategoryId`, " +
                    "`note`, `frequency`, `interval`, `startDate`, `endDate`, `nextDueDate`, `isActive` FROM `recurring_rules`"
            )
            db.execSQL("DROP TABLE `recurring_rules`")
            db.execSQL("ALTER TABLE `recurring_rules_new` RENAME TO `recurring_rules`")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_rules_accountId` ON `recurring_rules` (`accountId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_rules_currencyCode` ON `recurring_rules` (`currencyCode`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_rules_categoryId` ON `recurring_rules` (`categoryId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_rules_subcategoryId` ON `recurring_rules` (`subcategoryId`)")

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `bank_loans` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`name` TEXT NOT NULL, `bankName` TEXT NOT NULL, `kind` TEXT NOT NULL, `currencyCode` TEXT NOT NULL, " +
                    "`principalMinor` INTEGER NOT NULL, `thmPercent` REAL NOT NULL, `termMonths` INTEGER NOT NULL, " +
                    "`firstDueDate` INTEGER NOT NULL, `monthlyPaymentOverrideMinor` INTEGER, " +
                    "`paidMonthsBefore` INTEGER NOT NULL, `bankBalanceMinor` INTEGER, `accountId` INTEGER NOT NULL, " +
                    "`recurringRuleId` INTEGER, `note` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `closedDate` INTEGER, " +
                    "`color` INTEGER, `icon` TEXT, " +
                    "FOREIGN KEY(`currencyCode`) REFERENCES `currencies`(`code`) ON UPDATE NO ACTION ON DELETE NO ACTION , " +
                    "FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION , " +
                    "FOREIGN KEY(`recurringRuleId`) REFERENCES `recurring_rules`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_bank_loans_currencyCode` ON `bank_loans` (`currencyCode`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_bank_loans_accountId` ON `bank_loans` (`accountId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_bank_loans_recurringRuleId` ON `bank_loans` (`recurringRuleId`)")

            db.execSQL(
                "ALTER TABLE `transactions` ADD COLUMN `bankLoanId` INTEGER DEFAULT NULL " +
                    "REFERENCES `bank_loans`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_bankLoanId` ON `transactions` (`bankLoanId`)")

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `bank_loan_events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`bankLoanId` INTEGER NOT NULL, `date` INTEGER NOT NULL, `afterInstallment` INTEGER NOT NULL, " +
                    "`type` TEXT NOT NULL, `amountMinor` INTEGER, `penaltyMinor` INTEGER, `mode` TEXT, " +
                    "`newThmPercent` REAL, `transactionId` INTEGER, " +
                    "FOREIGN KEY(`bankLoanId`) REFERENCES `bank_loans`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                    "FOREIGN KEY(`transactionId`) REFERENCES `transactions`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_bank_loan_events_bankLoanId` ON `bank_loan_events` (`bankLoanId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_bank_loan_events_transactionId` ON `bank_loan_events` (`transactionId`)")
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
}

class Converters {
    @TypeConverter
    fun fromLocalDate(date: LocalDate?): Long? = date?.toEpochDay()

    @TypeConverter
    fun toLocalDate(epochDay: Long?): LocalDate? = epochDay?.let(LocalDate::ofEpochDay)
}
