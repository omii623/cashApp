package com.example.cashapp.data.backup

import java.math.BigDecimal
import java.math.MathContext

/** A CSV egy sora (a tétel a lekérdezésből, az összegek legkisebb egységben). */
data class CsvRow(
    val date: String,
    val type: String,
    val amountMinor: Long,
    val currencyCode: String,
    val accountName: String,
    val accountAmountMinor: Long,
    val accountCurrencyCode: String,
    val categoryName: String?,
    val subcategoryName: String?,
    val targetAccountName: String?,
    val targetAmountMinor: Long?,
    val targetCurrencyCode: String?,
    val rate: Double?,
    val feeMinor: Long?,
    val note: String,
    /** Magánkölcsön mozgásánál a másik fél neve, banki hitel részleténél / előtörlesztésénél a hitel neve. */
    val loanName: String? = null,
)

/**
 * Tételek CSV-ben, magyar Excelhez igazítva: `;` elválasztó, tizedesvessző, UTF-8 BOM
 * (különben az Excel az ékezeteket rosszul mutatja).
 */
object CsvExport {
    private const val SEP = ";"
    private const val BOM = "﻿"

    private val HEADER = listOf(
        "Dátum", "Típus", "Összeg", "Pénznem", "Számla", "Összeg a számla pénznemében", "Számla pénzneme",
        "Kategória", "Alkategória", "Cél számla", "Cél összeg", "Cél pénznem", "Árfolyam", "Díj", "Megjegyzés", "Kölcsön",
    )

    private val TYPE_NAMES = mapOf("INCOME" to "Bevétel", "EXPENSE" to "Kiadás", "EXCHANGE" to "Váltás")

    fun build(rows: List<CsvRow>, decimalsOf: (String) -> Int): String = buildString {
        append(BOM)
        appendLine(HEADER.joinToString(SEP))
        rows.forEach { r ->
            appendLine(
                listOf(
                    r.date,
                    TYPE_NAMES[r.type] ?: r.type,
                    money(r.amountMinor, decimalsOf(r.currencyCode)),
                    r.currencyCode,
                    r.accountName,
                    money(r.accountAmountMinor, decimalsOf(r.accountCurrencyCode)),
                    r.accountCurrencyCode,
                    r.categoryName.orEmpty(),
                    r.subcategoryName.orEmpty(),
                    r.targetAccountName.orEmpty(),
                    r.targetAmountMinor?.let { money(it, decimalsOf(r.targetCurrencyCode.orEmpty())) }.orEmpty(),
                    r.targetCurrencyCode.orEmpty(),
                    r.rate?.let { BigDecimal.valueOf(it).round(MathContext(8)).stripTrailingZeros().toPlainString().replace('.', ',') }.orEmpty(),
                    r.feeMinor?.takeIf { it > 0 }?.let { money(it, decimalsOf(r.currencyCode)) }.orEmpty(),
                    r.note,
                    r.loanName.orEmpty(),
                ).joinToString(SEP) { escape(it) }
            )
        }
    }

    /** 25063, 2 → „250,63” (ezres tagolás nélkül, hogy az Excel számként olvassa). */
    private fun money(minor: Long, decimals: Int): String =
        BigDecimal.valueOf(minor, decimals).toPlainString().replace('.', ',')

    /** Idézőjelbe tesz, ha elválasztó, idézőjel vagy sortörés van benne. */
    private fun escape(value: String): String =
        if (value.any { it == ';' || it == '"' || it == '\n' || it == '\r' }) "\"" + value.replace("\"", "\"\"") + "\"" else value
}
