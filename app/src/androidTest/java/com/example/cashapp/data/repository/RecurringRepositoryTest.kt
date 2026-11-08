package com.example.cashapp.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.cashapp.data.TestDb
import com.example.cashapp.data.local.CashDatabase
import com.example.cashapp.data.local.entity.RecurringRuleEntity
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

@RunWith(AndroidJUnit4::class)
class RecurringRepositoryTest {

    private lateinit var db: CashDatabase

    @Before
    fun setUp() { db = TestDb.inMemory() }

    @After
    fun tearDown() = db.close()

    @Test
    fun generateDueKetszerHivvaSemDuplikal() = runTest {
        val (accountId, categoryId) = TestDb.seedBasics(db)
        val repo = RecurringRepository(db)
        val today = LocalDate.of(2026, 3, 15)
        val ruleId = repo.saveRule(
            RecurringRuleEntity(
                name = "Napi kávé", type = TransactionType.EXPENSE, amountMinor = 500, currencyCode = "HUF",
                accountId = accountId, categoryId = categoryId, frequency = RecurrenceFrequency.DAILY,
                startDate = today.minusDays(4), nextDueDate = today.minusDays(4),
            ),
            today,
        )
        assertEquals(5, db.recurringDao().observePending().first().size)

        // Másodszori hívás ugyanarra a napra: nincs új tétel
        assertEquals(0, repo.generateDue(today))
        // Akkor sem duplikál, ha a következő esedékesség (pl. párhuzamos futás miatt) visszamaradt
        val rule = db.recurringDao().getRule(ruleId)!!
        db.recurringDao().updateRule(rule.copy(nextDueDate = today.minusDays(4)))
        repo.generateDue(today)
        repo.generateDue(today)

        val pending = db.recurringDao().observePending().first()
        assertEquals(5, pending.size)
        assertEquals(5, pending.map { it.dueDate }.toSet().size)
    }
}
