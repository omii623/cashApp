package com.example.cashapp.data.backup

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.cashapp.data.TestDb
import com.example.cashapp.data.local.CashDatabase
import com.example.cashapp.data.local.entity.TransactionEntity
import com.example.cashapp.data.repository.RecurringRepository
import com.example.cashapp.data.settings.SettingsRepository
import com.example.cashapp.domain.model.TransactionType
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** JSON mentés → visszaállítás: a táblák tartalma változatlanul visszajön. */
@RunWith(AndroidJUnit4::class)
class BackupManagerTest {

    private lateinit var db: CashDatabase
    private lateinit var manager: BackupManager
    private lateinit var file: File

    @Before
    fun setUp() {
        db = TestDb.inMemory()
        val context = TestDb.context
        manager = BackupManager(db, SettingsRepository(context), RecurringRepository(db), context.contentResolver)
        file = File(context.cacheDir, "backup-test.json")
    }

    @After
    fun tearDown() {
        db.close()
        file.delete()
    }

    private fun dump(): Map<String, List<List<String?>>> = BackupManager.TABLES.associateWith { table ->
        db.openHelper.readableDatabase.query("SELECT * FROM $table ORDER BY 1").use { c ->
            buildList {
                while (c.moveToNext()) add((0 until c.columnCount).map { if (c.isNull(it)) null else c.getString(it) })
            }
        }
    }

    @Test
    fun exportRestoreRoundtrip() = runTest {
        val (accountId, categoryId) = TestDb.seedBasics(db)
        val txId = db.transactionDao().insert(
            TransactionEntity(
                type = TransactionType.EXPENSE, date = LocalDate.of(2026, 1, 2), amountMinor = 1234, currencyCode = "HUF",
                accountId = accountId, accountAmountMinor = 1234, categoryId = categoryId, note = "ékezetes „idézőjel”",
            )
        )
        val before = dump()
        val uri = Uri.fromFile(file)
        manager.exportJson(uri)

        // Az adatok módosulnak a mentés után…
        db.transactionDao().deleteById(txId)
        db.transactionDao().insert(
            TransactionEntity(
                type = TransactionType.INCOME, date = LocalDate.of(2026, 2, 1), amountMinor = 1, currencyCode = "HUF",
                accountId = accountId, accountAmountMinor = 1,
            )
        )

        // …majd visszaállítjuk.
        val backup = manager.readBackup(uri)
        assertEquals(1, manager.summarize(backup).transactions)
        manager.restore(backup)

        assertEquals(before, dump())
    }
}
