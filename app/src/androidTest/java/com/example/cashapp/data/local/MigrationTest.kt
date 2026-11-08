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
    fun migrate1To3_teljesLanc() {
        helper.createDatabase(dbName, 1).close()
        helper.runMigrationsAndValidate(dbName, 3, true, *Migrations.ALL).close()
        // A Room is meg tudja nyitni a migrált adatbázist (séma-ellenőrzéssel).
        Room.databaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, CashDatabase::class.java, dbName)
            .addMigrations(*Migrations.ALL)
            .build()
            .apply { openHelper.writableDatabase; close() }
    }
}
