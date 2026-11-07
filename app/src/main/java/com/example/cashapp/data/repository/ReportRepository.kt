package com.example.cashapp.data.repository

import com.example.cashapp.data.local.dao.TransactionDao
import com.example.cashapp.domain.model.TransactionType
import com.example.cashapp.domain.report.ReportCalculator.Entry
import com.example.cashapp.domain.report.ReportCalculator.GroupAmount
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Kimutatások adatai (csak olvasás). A számítás a [com.example.cashapp.domain.report.ReportCalculator]-ban van. */
class ReportRepository(private val dao: TransactionDao) {

    fun observeByCategory(type: TransactionType, from: LocalDate, to: LocalDate): Flow<List<GroupAmount>> =
        dao.observeCategoryTotals(type.name, from, to).map { list -> list.map { GroupAmount(it.id, it.currencyCode, it.amountMinor) } }

    fun observeBySubcategory(categoryId: Long, type: TransactionType, from: LocalDate, to: LocalDate): Flow<List<GroupAmount>> =
        dao.observeSubcategoryTotals(categoryId, type.name, from, to).map { list ->
            list.map { GroupAmount(it.id, it.currencyCode, it.amountMinor) }
        }

    fun observeEntries(from: LocalDate, to: LocalDate): Flow<List<Entry>> =
        dao.observeAmountRows(from, to).map { list -> list.map { Entry(it.date, it.type, it.amountMinor, it.currencyCode) } }
}
