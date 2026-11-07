package com.example.cashapp.data.backup

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import androidx.room.withTransaction
import com.example.cashapp.data.local.CashDatabase
import com.example.cashapp.data.local.seed.DefaultData
import com.example.cashapp.data.repository.RecurringRepository
import com.example.cashapp.data.settings.SettingsRepository
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Export / import (terv.md 4.11):
 * - JSON mentés: az összes tábla + beállítások; visszaállításkor a jelenlegi adatok helyére kerül.
 * - CSV: a tételek táblázatként (csak export).
 *
 * A táblák nyers SQL-lel mennek, így a mentés minden oszlopot tartalmaz, és új oszlopnál sem kell ide nyúlni.
 */
class BackupManager(
    private val database: CashDatabase,
    private val settingsRepository: SettingsRepository,
    private val recurringRepository: RecurringRepository,
    private val contentResolver: ContentResolver,
) {
    /** Visszaállítás előtti összesítő a megerősítő ablakhoz. */
    data class Summary(val exportedAt: Long, val accounts: Int, val transactions: Int, val categories: Int, val rules: Int)

    // ---------------------------------------------------------------- JSON mentés

    suspend fun exportJson(uri: Uri) = withContext(Dispatchers.IO) {
        val backup = database.withTransaction {
            Backup(
                dbVersion = sql.version,
                exportedAt = System.currentTimeMillis(),
                settings = settingsRepository.snapshot(),
                tables = TABLES.associateWith { readTable(it) },
            )
        }
        write(uri, BackupCodec.encode(backup))
    }

    /** Beolvas és ellenőriz egy mentést (még nem ír semmit). */
    suspend fun readBackup(uri: Uri): Backup = withContext(Dispatchers.IO) {
        val text = contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            ?: throw BackupFormatException(BackupFormatException.Reason.UNREADABLE)
        BackupCodec.decode(text, maxDbVersion = sql.version).also { b ->
            if (b.count("currencies") == 0 || b.count("accounts") == 0) throw BackupFormatException(BackupFormatException.Reason.EMPTY)
        }
    }

    fun summarize(b: Backup) = Summary(b.exportedAt, b.count("accounts"), b.count("transactions"), b.count("categories"), b.count("recurring_rules"))

    /**
     * A jelenlegi adatok cseréje a mentésre, egyetlen tranzakcióban:
     * ha bármi hibás (pl. sérült hivatkozás), semmi nem változik.
     */
    suspend fun restore(backup: Backup) = withContext(Dispatchers.IO) {
        database.withTransaction {
            TABLES.asReversed().forEach { sql.execSQL("DELETE FROM $it") }
            TABLES.forEach { table -> insertRows(table, backup.tables[table].orEmpty()) }
            // Hivatkozási hibát a tranzakció végén is ellenőrzünk (pl. hiányzó számla).
            sql.query("PRAGMA foreign_key_check").use { c ->
                if (c.moveToFirst()) throw BackupFormatException(BackupFormatException.Reason.BROKEN_REFERENCE, c.getString(0))
            }
        }
        settingsRepository.restore(validSettings(backup))
        // A mentés óta esedékessé vált ismétlődő tételek azonnal jelenjenek meg (ne csak a következő indításkor).
        runCatching { recurringRepository.generateDue() }
    }

    // ---------------------------------------------------------------- CSV export

    suspend fun exportCsv(uri: Uri) = withContext(Dispatchers.IO) {
        val decimals = mutableMapOf<String, Int>()
        sql.query("SELECT code, decimals FROM currencies").use { c -> while (c.moveToNext()) decimals[c.getString(0)] = c.getInt(1) }
        val rows = mutableListOf<CsvRow>()
        sql.query(CSV_QUERY).use { c ->
            while (c.moveToNext()) {
                rows += CsvRow(
                    date = LocalDate.ofEpochDay(c.getLong(0)).toString(),
                    type = c.getString(1),
                    amountMinor = c.getLong(2),
                    currencyCode = c.getString(3),
                    accountName = c.getString(4),
                    accountAmountMinor = c.getLong(5),
                    accountCurrencyCode = c.getString(6),
                    categoryName = c.stringOrNull(7),
                    subcategoryName = c.stringOrNull(8),
                    targetAccountName = c.stringOrNull(9),
                    targetAmountMinor = c.longOrNull(10),
                    targetCurrencyCode = c.stringOrNull(11),
                    rate = c.doubleOrNull(12) ?: c.doubleOrNull(13),
                    feeMinor = c.longOrNull(14),
                    note = c.getString(15).orEmpty(),
                )
            }
        }
        write(uri, CsvExport.build(rows) { decimals[it] ?: 2 })
    }

    // ---------------------------------------------------------------- segédek

    private val sql get() = database.openHelper.writableDatabase

    private fun readTable(table: String): Rows = sql.query("SELECT * FROM $table").use { c ->
        buildList {
            while (c.moveToNext()) {
                add((0 until c.columnCount).associate { i -> c.getColumnName(i) to c.value(i) })
            }
        }
    }

    /** Csak a táblában ténylegesen létező oszlopok kerülnek be (régebbi mentésnél a hiányzók alapértéket kapnak). */
    private fun insertRows(table: String, rows: Rows) {
        if (rows.isEmpty()) return
        val columns = sql.query("PRAGMA table_info($table)").use { c -> buildSet { while (c.moveToNext()) add(c.getString(1)) } }
        rows.forEach { row ->
            val entries = row.filterKeys { it in columns }
            if (entries.isEmpty()) return@forEach
            val names = entries.keys.joinToString(",") { "`$it`" }
            val marks = entries.keys.joinToString(",") { "?" }
            sql.execSQL("INSERT INTO $table ($names) VALUES ($marks)", entries.values.toTypedArray())
        }
    }

    private fun write(uri: Uri, text: String) {
        val out = contentResolver.openOutputStream(uri, "wt") ?: error("A fájl nem írható")
        out.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

    private fun Cursor.value(i: Int): Any? = when (getType(i)) {
        Cursor.FIELD_TYPE_NULL -> null
        Cursor.FIELD_TYPE_INTEGER -> getLong(i)
        Cursor.FIELD_TYPE_FLOAT -> getDouble(i)
        else -> getString(i)
    }

    private fun Cursor.stringOrNull(i: Int) = if (isNull(i)) null else getString(i)
    private fun Cursor.longOrNull(i: Int) = if (isNull(i)) null else getLong(i)
    private fun Cursor.doubleOrNull(i: Int) = if (isNull(i)) null else getDouble(i)

    companion object {
        /**
         * Az alap pénznem csak a mentésben ténylegesen szereplő pénznem lehet. Ha nincs ilyen
         * (pl. kézzel szerkesztett fájl), az alapértelmezett, vagy ha az sincs, az első pénznem lesz.
         */
        internal fun validSettings(backup: Backup): Map<String, String> {
            val codes = backup.tables["currencies"].orEmpty().mapNotNull { it["code"] as? String }
            if (codes.isEmpty()) return backup.settings
            val wanted = backup.settings["baseCurrency"] ?: DefaultData.BASE_CURRENCY
            val base = when {
                wanted in codes -> wanted
                DefaultData.BASE_CURRENCY in codes -> DefaultData.BASE_CURRENCY
                else -> codes.first()
            }
            return backup.settings + ("baseCurrency" to base)
        }

        /** Beszúrási sorrend: előbb az, amire hivatkoznak (törlés fordítva). */
        val TABLES = listOf(
            "currencies", "accounts", "categories", "subcategories", "recurring_rules",
            "transactions", "exchange_details", "pending_recurring", "budgets", "exchange_rates",
        )

        private const val CSV_QUERY = """
            SELECT t.date, t.type, t.amountMinor, t.currencyCode, a.name, t.accountAmountMinor, a.currencyCode,
                   c.name, s.name, ta.name, e.targetAmountMinor, e.targetCurrencyCode, e.rate, t.rateToAccount,
                   e.feeMinor, t.note
            FROM transactions t
            JOIN accounts a ON a.id = t.accountId
            LEFT JOIN categories c ON c.id = t.categoryId
            LEFT JOIN subcategories s ON s.id = t.subcategoryId
            LEFT JOIN exchange_details e ON e.transactionId = t.id
            LEFT JOIN accounts ta ON ta.id = e.targetAccountId
            ORDER BY t.date, t.id
        """
    }
}
