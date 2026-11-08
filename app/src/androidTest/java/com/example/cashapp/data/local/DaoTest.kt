package com.example.cashapp.data.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.cashapp.data.TestDb
import com.example.cashapp.data.local.entity.PendingRecurringEntity
import com.example.cashapp.data.local.entity.RecurringRuleEntity
import com.example.cashapp.data.local.entity.TransactionEntity
import com.example.cashapp.domain.model.PendingStatus
import com.example.cashapp.domain.model.RecurrenceFrequency
import com.example.cashapp.domain.model.TransactionType
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** TransactionDao / RecurringDao fő lekérdezései memóriabeli adatbázison. */
@RunWith(AndroidJUnit4::class)
class DaoTest {

    private lateinit var db: CashDatabase
    private var accountId = 0L
    private var categoryId = 0L
    private val today = LocalDate.of(2026, 3, 15)

    @Before
    fun setUp() = runTest {
        db = TestDb.inMemory()
        TestDb.seedBasics(db).let { (a, c) -> accountId = a; categoryId = c }
    }

    @After
    fun tearDown() = db.close()

    private fun tx(type: TransactionType, amount: Long, date: LocalDate, note: String = "") = TransactionEntity(
        type = type, date = date, amountMinor = amount, currencyCode = "HUF", accountId = accountId,
        accountAmountMinor = amount, categoryId = categoryId.takeIf { type == TransactionType.EXPENSE }, note = note,
    )

    @Test
    fun osszesitesEsSzures() = runTest {
        val dao = db.transactionDao()
        dao.insert(tx(TransactionType.EXPENSE, 1000, today, "kenyér"))
        dao.insert(tx(TransactionType.EXPENSE, 500, today.minusDays(1), "tej"))
        dao.insert(tx(TransactionType.INCOME, 9000, today))
        dao.insert(tx(TransactionType.EXPENSE, 7777, today.minusMonths(2))) // időszakon kívül

        val from = today.withDayOfMonth(1)
        val to = today.withDayOfMonth(today.lengthOfMonth())
        val totals = dao.observeTotals(from, to).first().associate { it.type to it.totalMinor }
        assertEquals(1500L, totals[TransactionType.EXPENSE])
        assertEquals(9000L, totals[TransactionType.INCOME])

        val byCategory = dao.observeExpenseByCategory(from, to).first()
        assertEquals(1500L, byCategory.single { it.categoryId == categoryId }.totalMinor)

        val filtered = dao.observeFiltered(from, to, listOf("EXPENSE"), accountId, null, "kenyér").first()
        assertEquals(listOf("kenyér"), filtered.map { it.note })

        // Legújabb elöl
        val recent = dao.observeRecent(10).first()
        assertEquals(4, recent.size)
        assertEquals(today.minusMonths(2), recent.last().date)
    }

    @Test
    fun ismetlodoTetelekLekerdezeseiEsDuplikatumKizaras() = runTest {
        val dao = db.recurringDao()
        val ruleId = dao.insertRule(
            RecurringRuleEntity(
                name = "Albérlet", type = TransactionType.EXPENSE, amountMinor = 100_000, currencyCode = "HUF",
                accountId = accountId, categoryId = categoryId, frequency = RecurrenceFrequency.MONTHLY,
                startDate = today.minusMonths(1), nextDueDate = today,
            )
        )
        assertEquals(listOf(ruleId), dao.getDueRules(today).map { it.id })
        assertEquals(emptyList<Long>(), dao.getDueRules(today.minusDays(1)).map { it.id })

        dao.insertPendingAll(listOf(PendingRecurringEntity(ruleId = ruleId, dueDate = today)))
        // Ugyanaz az esedékesség még egyszer: az egyedi index miatt kimarad (IGNORE)
        dao.insertPendingAll(
            listOf(
                PendingRecurringEntity(ruleId = ruleId, dueDate = today),
                PendingRecurringEntity(ruleId = ruleId, dueDate = today.plusMonths(1)),
            )
        )
        assertEquals(2, dao.observePending().first().size)
        // A jövőbeli még nem esedékes → nem számít
        assertEquals(1, dao.observePendingCount(today).first())

        val first = dao.observePending().first().first()
        dao.updatePending(dao.getPending(first.id)!!.copy(status = PendingStatus.SKIPPED))
        assertEquals(0, dao.observePendingCount(today).first())

        dao.deletePendingOfRule(ruleId)
        assertEquals(0, dao.observePending().first().size)
        assertEquals(PendingStatus.SKIPPED, dao.getPending(first.id)?.status) // az elbírált megmarad
    }
}
