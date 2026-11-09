package com.example.cashapp.data.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Séma-migrációk ellenőrzése az exportált (app/schemas) JSON-ok alapján. */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val dbName = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), CashDatabase::class.java)

    @Test
    fun migrate1To2_feeMinorAlapertelmezes0() {
        helper.createDatabase(dbName, 1).use { db ->
            db.execSQL(
                "INSERT INTO exchange_details (transactionId, targetAccountId, targetAmountMinor, targetCurrencyCode, rate, rateSource) " +
                    "VALUES (1, 2, 1000, 'EUR', 0.0025, 'API')"
            )
        }
        helper.runMigrationsAndValidate(dbName, 2, true, Migrations.MIGRATION_1_2).use { db ->
            db.query("SELECT feeMinor FROM exchange_details WHERE transactionId = 1").use { c ->
                c.moveToFirst()
                assertEquals(0L, c.getLong(0))
            }
        }
    }

    @Test
    fun migrate2To3_duplikatumokTorlodnekEsEgyediIndex() {
        helper.createDatabase(dbName, 2).use { db ->
            // (1, 100): két PENDING → a kisebb id marad
            db.execSQL("INSERT INTO pending_recurring (id, ruleId, dueDate, status) VALUES (1, 1, 100, 'PENDING')")
            db.execSQL("INSERT INTO pending_recurring (id, ruleId, dueDate, status) VALUES (2, 1, 100, 'PENDING')")
            // (1, 101): PENDING + APPROVED → az elbírált marad
            db.execSQL("INSERT INTO pending_recurring (id, ruleId, dueDate, status) VALUES (3, 1, 101, 'PENDING')")
            db.execSQL("INSERT INTO pending_recurring (id, ruleId, dueDate, status) VALUES (4, 1, 101, 'APPROVED')")
            // egyedi sor
            db.execSQL("INSERT INTO pending_recurring (id, ruleId, dueDate, status) VALUES (5, 2, 100, 'PENDING')")
        }
        helper.runMigrationsAndValidate(dbName, 3, true, Migrations.MIGRATION_2_3).use { db ->
            db.query("SELECT id FROM pending_recurring ORDER BY id").use { c ->
                val ids = buildList { while (c.moveToNext()) add(c.getLong(0)) }
                assertEquals(listOf(1L, 4L, 5L), ids)
            }
        }
    }

    @Test
    fun migrate3To4_loansTablaEsLoanIdOszlop() {
        helper.createDatabase(dbName, 3).use { db ->
            db.execSQL("INSERT INTO currencies (code, name, symbol, decimals, isActive, sortOrder) VALUES ('HUF', 'Forint', 'Ft', 0, 1, 0)")
            db.execSQL("INSERT INTO accounts (id, name, currencyCode, initialBalanceMinor, color, icon, sortOrder, isArchived) VALUES (1, 'K', 'HUF', 0, 0, 'cash', 0, 0)")
            db.execSQL(
                "INSERT INTO transactions (id, type, date, amountMinor, currencyCode, accountId, accountAmountMinor, note, createdAt) " +
                    "VALUES (1, 'EXPENSE', 100, 500, 'HUF', 1, 500, '', 0)"
            )
        }
        helper.runMigrationsAndValidate(dbName, 4, true, Migrations.MIGRATION_3_4).use { db ->
            db.query("SELECT loanId FROM transactions WHERE id = 1").use { c ->
                c.moveToFirst()
                assertEquals(true, c.isNull(0))
            }
            db.query("SELECT COUNT(*) FROM loans").use { c -> c.moveToFirst(); assertEquals(0, c.getInt(0)) }
        }
    }

    @Test
    fun migrate1To4_teljesLanc() {
        helper.createDatabase(dbName, 1).close()
        helper.runMigrationsAndValidate(dbName, 4, true, *Migrations.ALL).close()
        // A Room is meg tudja nyitni a migrált adatbázist (séma-ellenőrzéssel).
        Room.databaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, CashDatabase::class.java, dbName)
            .addMigrations(*Migrations.ALL)
            .build()
            .apply { openHelper.writableDatabase; close() }
    }
}
