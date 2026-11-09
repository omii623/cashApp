package com.example.cashapp.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {
    private val sample = Backup(
        dbVersion = 2,
        exportedAt = 1_794_000_000_000,
        settings = mapOf("baseCurrency" to "HUF", "hideAmounts" to "true"),
        tables = mapOf(
            "currencies" to listOf(mapOf("code" to "HUF", "decimals" to 0L, "name" to "Magyar forint")),
            "transactions" to listOf(
                mapOf("id" to 1L, "date" to 20764L, "amountMinor" to 5555L, "rateToAccount" to 364.648, "note" to "„idézet”; ékezet", "subcategoryId" to null),
            ),
            "budgets" to emptyList(),
        ),
    )

    @Test fun `oda-vissza ugyanaz`() {
        val back = BackupCodec.decode(BackupCodec.encode(sample), maxDbVersion = 2)
        assertEquals(sample.dbVersion, back.dbVersion)
        assertEquals(sample.exportedAt, back.exportedAt)
        assertEquals(sample.settings, back.settings)
        val tx = back.tables.getValue("transactions").single()
        assertEquals(5555L, tx["amountMinor"])
        assertEquals(364.648, tx["rateToAccount"])
        assertEquals("„idézet”; ékezet", tx["note"])
        assertTrue(tx.containsKey("subcategoryId"))
        assertNull(tx["subcategoryId"])
        assertEquals(0, back.count("budgets"))
    }

    private fun reason(json: String, max: Int = 2) =
        assertThrows(BackupFormatException::class.java) { BackupCodec.decode(json, max) }.reason

    @Test fun `nem JSON`() = assertEquals(BackupFormatException.Reason.NOT_JSON, reason("hello"))

    @Test fun `idegen JSON`() = assertEquals(BackupFormatException.Reason.NOT_CASHAPP, reason("""{"a":1}"""))

    @Test fun `újabb adatbázis-verzió elutasítva`() {
        assertEquals(BackupFormatException.Reason.NEWER_VERSION, reason(BackupCodec.encode(sample.copy(dbVersion = 3))))
    }

    @Test fun `régebbi verzió elfogadva`() = assertEquals(1, BackupCodec.decode(BackupCodec.encode(sample.copy(dbVersion = 1)), 2).dbVersion)

    @Test fun `érvényes alap pénznem marad`() = assertEquals("HUF", BackupManager.validSettings(sample).getValue("baseCurrency"))

    @Test fun `ismeretlen alap pénznem cserélődik`() {
        val b = sample.copy(
            settings = mapOf("baseCurrency" to "XYZ"),
            tables = sample.tables + ("currencies" to listOf(mapOf("code" to "EUR"), mapOf("code" to "USD"))),
        )
        assertEquals("EUR", BackupManager.validSettings(b).getValue("baseCurrency"))
        assertEquals("HUF", BackupManager.validSettings(sample.copy(settings = mapOf("baseCurrency" to "XYZ"))).getValue("baseCurrency"))
    }
}

class CsvExportTest {
    private val decimals = mapOf("HUF" to 0, "EUR" to 2)

    private fun row(type: String = "EXPENSE", note: String = "", amount: Long = 12_500, cur: String = "HUF") = CsvRow(
        date = "2026-11-07", type = type, amountMinor = amount, currencyCode = cur, accountName = "Bankszámla",
        accountAmountMinor = 12_500, accountCurrencyCode = "HUF", categoryName = "Élelmiszer", subcategoryName = "Bevásárlás",
        targetAccountName = null, targetAmountMinor = null, targetCurrencyCode = null, rate = null, feeMinor = null, note = note,
    )

    private fun lines(vararg rows: CsvRow) = CsvExport.build(rows.toList()) { decimals[it] ?: 2 }.removePrefix("﻿").lines()

    @Test fun `fejléc és sor`() {
        val l = lines(row(note = "Lidl"))
        assertTrue(l[0].startsWith("Dátum;Típus;Összeg"))
        assertEquals("2026-11-07;Kiadás;12500;HUF;Bankszámla;12500;HUF;Élelmiszer;Bevásárlás;;;;;;Lidl;", l[1])
    }

    @Test fun `kölcsön oszlop`() {
        val l = lines(row(note = "").copy(categoryName = null, subcategoryName = null, loanName = "Pisti"))
        assertTrue(l[0].endsWith(";Megjegyzés;Kölcsön"))
        assertTrue(l[1].endsWith(";Pisti"))
    }

    @Test fun `tizedesvessző`() = assertTrue(lines(row(amount = 1299, cur = "EUR"))[1].contains(";12,99;EUR;"))

    @Test fun `pontosvessző és idézőjel escape`() =
        assertTrue(lines(row(note = "a;b \"c\""))[1].endsWith(";\"a;b \"\"c\"\"\";"))

    @Test fun `BOM az elején`() = assertTrue(CsvExport.build(emptyList()) { 0 }.startsWith("﻿"))
}
