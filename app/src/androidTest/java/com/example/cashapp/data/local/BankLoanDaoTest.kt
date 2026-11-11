package com.example.cashapp.data.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.cashapp.data.TestDb
import com.example.cashapp.data.local.entity.BankLoanEntity
import com.example.cashapp.data.local.entity.BankLoanEventEntity
import com.example.cashapp.data.local.entity.RecurringRuleEntity
import com.example.cashapp.data.local.entity.TransactionEntity
import com.example.cashapp.domain.model.BankLoanEventType
import com.example.cashapp.domain.model.BankLoanKind
import com.example.cashapp.domain.model.PrepaymentMode
import com.example.cashapp.domain.model.RecurrenceFrequency
import com.example.cashapp.domain.model.TransactionType
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** BankLoanDao: befizetett részletek száma, sorrend, és a hivatkozások viselkedése törléskor (DB v5). */
@RunWith(AndroidJUnit4::class)
class BankLoanDaoTest {

    private lateinit var db: CashDatabase
    private var accountId = 0L
    private var categoryId = 0L
    private val first = LocalDate.of(2026, 1, 15)

    @Before
    fun setUp() = runTest {
        db = TestDb.inMemory()
        TestDb.seedBasics(db).let { (a, c) -> accountId = a; categoryId = c }
    }

    @After
    fun tearDown() = db.close()

    private fun loan(name: String, ruleId: Long? = null) = BankLoanEntity(
        name = name, bankName = "Bank", kind = BankLoanKind.PERSONAL, currencyCode = "HUF",
        principalMinor = 1_000_000, thmPercent = 13.79, termMonths = 36, firstDueDate = first,
        accountId = accountId, categoryId = categoryId, recurringRuleId = ruleId,
    )

    private fun payment(loanId: Long, date: LocalDate, amount: Long = 34_076) = TransactionEntity(
        type = TransactionType.EXPENSE, date = date, amountMinor = amount, currencyCode = "HUF",
        accountId = accountId, accountAmountMinor = amount, categoryId = categoryId, bankLoanId = loanId,
    )

    private suspend fun rule() = db.recurringDao().insertRule(
        RecurringRuleEntity(
            name = "Részlet", type = TransactionType.EXPENSE, amountMinor = 34_076, currencyCode = "HUF",
            accountId = accountId, categoryId = categoryId, frequency = RecurrenceFrequency.MONTHLY,
            startDate = first, nextDueDate = first,
        )
    )

    @Test
    fun befizetettReszletekAzElotorlesztesNelkul() = runTest {
        val dao = db.bankLoanDao()
        val id = dao.insert(loan("Személyi"))
        val tx = db.transactionDao()
        repeat(3) { tx.insert(payment(id, first.plusMonths(it.toLong()))) }
        val prepaymentTx = tx.insert(payment(id, first.plusMonths(3), amount = 200_000))
        dao.insertEvent(
            BankLoanEventEntity(
                bankLoanId = id, date = first.plusMonths(3), afterInstallment = 3, type = BankLoanEventType.PREPAYMENT,
                amountMinor = 200_000, penaltyMinor = 0, mode = PrepaymentMode.SHORTER_TERM, transactionId = prepaymentTx,
            )
        )

        assertEquals(3, dao.paidInstallments(id))
        val withProgress = dao.observe(id).first()!!
        assertEquals(3, withProgress.paidInstallments)
        assertEquals(first.plusMonths(3), withProgress.lastPaymentDate)
        assertEquals(1, dao.getEvents(id).size)
    }

    @Test
    fun nyitottakElol_lezartakHatul() = runTest {
        val dao = db.bankLoanDao()
        val closed = dao.insert(loan("Autó"))
        dao.insert(loan("Lakás"))
        dao.insert(loan("Bútor"))
        dao.setClosed(closed, first)

        assertEquals(listOf("Bútor", "Lakás", "Autó"), dao.observeAll().first().map { it.loan.name })
        assertEquals(0, dao.observeAll().first().first().paidInstallments)
    }

    @Test
    fun torlesutan_tetelekMaradnak_esemenyekTorlodnek() = runTest {
        val dao = db.bankLoanDao()
        val id = dao.insert(loan("Személyi"))
        val txId = db.transactionDao().insert(payment(id, first))
        dao.insertEvent(
            BankLoanEventEntity(bankLoanId = id, date = first, afterInstallment = 1, type = BankLoanEventType.RATE_CHANGE, newThmPercent = 9.5)
        )

        dao.deleteById(id)

        assertNull(dao.getById(id))
        assertTrue(dao.getEvents(id).isEmpty())
        val remaining = db.transactionDao().getById(txId)!!
        assertNull(remaining.bankLoanId)
        assertEquals(34_076L, remaining.amountMinor)
    }

    @Test
    fun szabalyTorlesekorAHivatkozasNullazodik() = runTest {
        val dao = db.bankLoanDao()
        val ruleId = rule()
        val id = dao.insert(loan("Személyi", ruleId))
        assertEquals(id, dao.getByRuleId(ruleId)?.id)

        db.recurringDao().deleteRule(db.recurringDao().getRule(ruleId)!!)

        assertNull(dao.getById(id)!!.recurringRuleId)
        assertNull(dao.getByRuleId(ruleId))
    }
}
