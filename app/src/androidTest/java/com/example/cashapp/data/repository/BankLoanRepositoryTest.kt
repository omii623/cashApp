package com.example.cashapp.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.cashapp.data.TestDb
import com.example.cashapp.data.local.CashDatabase
import com.example.cashapp.data.local.entity.AccountEntity
import com.example.cashapp.data.local.entity.BankLoanEntity
import com.example.cashapp.data.local.entity.CategoryEntity
import com.example.cashapp.data.local.entity.CurrencyEntity
import com.example.cashapp.data.local.entity.SubcategoryEntity
import com.example.cashapp.domain.loan.LoanCalculator
import com.example.cashapp.domain.model.BankLoanKind
import com.example.cashapp.domain.model.CategoryType
import com.example.cashapp.domain.model.PendingStatus
import com.example.cashapp.domain.model.PrepaymentMode
import com.example.cashapp.domain.model.RecurrenceFrequency
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Banki hitelek: ismétlődő szabály, részletek könyvelése, előtörlesztés, kamatváltozás, lezárás, törlés. */
@RunWith(AndroidJUnit4::class)
class BankLoanRepositoryTest {

    private lateinit var db: CashDatabase
    private lateinit var repo: BankLoanRepository
    private lateinit var recurring: RecurringRepository
    private var accountId = 0L
    private var categoryId = 0L
    private val first = LocalDate.of(2026, 1, 15)
    private val monthly = LoanCalculator.calculate(1_000_000, 13.79, 36).monthlyPaymentMinor // 34 076

    @Before
    fun setUp() = runTest {
        db = TestDb.inMemory()
        TestDb.seedBasics(db).let { (a, c) -> accountId = a; categoryId = c }
        repo = BankLoanRepository(db)
        recurring = RecurringRepository(db)
    }

    @After
    fun tearDown() = db.close()

    private fun loan(id: Long = 0) = BankLoanEntity(
        id = id, name = " Személyi ", bankName = "Bank", kind = BankLoanKind.PERSONAL, currencyCode = "EUR",
        principalMinor = 1_000_000, thmPercent = 13.79, termMonths = 36, firstDueDate = first,
        accountId = accountId, categoryId = categoryId,
    )

    private suspend fun rule(loanId: Long) = db.recurringDao().getRule(repo.get(loanId)!!.recurringRuleId!!)!!

    private suspend fun pendingStatuses(ruleId: Long) = db.openHelper.readableDatabase
        .query("SELECT dueDate, status FROM pending_recurring WHERE ruleId = $ruleId ORDER BY dueDate").use { c ->
            buildList { while (c.moveToNext()) add(LocalDate.ofEpochDay(c.getLong(0)) to PendingStatus.valueOf(c.getString(1))) }
        }

    @Test
    fun mentesreLetrejonAHaviSzabaly() = runTest {
        val id = repo.save(loan())
        val saved = repo.get(id)!!
        assertEquals("Személyi", saved.name)
        assertEquals("HUF", saved.currencyCode) // a számla pénzneme

        val r = rule(id)
        assertEquals(RecurrenceFrequency.MONTHLY, r.frequency)
        assertEquals(first, r.startDate)
        assertEquals(first, r.nextDueDate)
        assertEquals(LocalDate.of(2028, 12, 15), r.endDate)
        assertEquals(monthly, r.amountMinor)
        assertEquals(categoryId, r.categoryId)
        assertTrue(r.isActive)

        val o = repo.observe(id).first()!!
        assertEquals(1_000_000L, o.outstandingMinor)
        assertEquals(0, o.paidInstallments)
        assertEquals(first, o.nextInstallment!!.dueDate)
    }

    @Test
    fun jovahagyottReszletAHitelhezKotodik() = runTest {
        val id = repo.save(loan())
        val ruleId = rule(id).id
        recurring.generateDue(first.plusMonths(1)) // jan. 15. és febr. 15.
        val pending = db.recurringDao().observePending().first()
        assertEquals(2, pending.size)

        recurring.approve(pending.first().id, monthly, first)

        val o = repo.getOverview(id)!!
        assertEquals(1, o.paidInstallments)
        assertEquals(o.schedule.installments[0].balanceAfterMinor, o.outstandingMinor)
        val tx = db.transactionDao().getById(db.recurringDao().getPending(pending.first().id)!!.transactionId!!)!!
        assertEquals(id, tx.bankLoanId)
        assertEquals(ruleId, tx.recurringRuleId)
    }

    @Test
    fun reszletBefizetveGombNemDuplikal() = runTest {
        val id = repo.save(loan())
        val ruleId = rule(id).id
        // Az első részlet már függőben van → azt hagyja jóvá; a második még nem esedékes → jóváhagyottként felírja.
        recurring.generateDue(first)
        repo.payNextInstallment(id, first, monthly)
        repo.payNextInstallment(id, first.plusDays(3), monthly)
        assertEquals(2, repo.getOverview(id)!!.paidInstallments)

        // Később a generátor nem kéri újra a már befizetett esedékességeket.
        recurring.generateDue(first.plusMonths(2))
        assertEquals(
            listOf(
                first to PendingStatus.APPROVED,
                first.plusMonths(1) to PendingStatus.APPROVED,
                first.plusMonths(2) to PendingStatus.PENDING,
            ),
            pendingStatuses(ruleId),
        )
    }

    @Test
    fun utolsoReszletOsszegeASzabalyban() = runTest {
        val id = repo.save(loan())
        val schedule = repo.getOverview(id)!!.schedule
        repeat(35) { repo.payNextInstallment(id, first.plusMonths(it.toLong()), schedule.installments[it].paymentMinor) }
        assertEquals(schedule.installments.last().paymentMinor, rule(id).amountMinor)

        repo.payNextInstallment(id, first.plusMonths(35), schedule.installments.last().paymentMinor)
        val o = repo.getOverview(id)!!
        assertTrue(o.isPaidOff)
        assertEquals(0L, o.outstandingMinor)
        assertFalse(rule(id).isActive)
    }

    @Test
    fun elotorlesztesRovidebbFutamidovel() = runTest {
        val id = repo.save(loan())
        repeat(12) { repo.payNextInstallment(id, first.plusMonths(it.toLong()), monthly) }
        recurring.generateDue(first.plusMonths(30)) // a jövőbeli (most még a régi terv szerinti) esedékességek is
        val calc = LoanCalculator.prepayment(1_000_000, 13.79, 36, 300_000, 12, 1.0, 200_000)

        repo.addPrepayment(id, first.plusMonths(12), 300_000, calc.penaltyMinor, PrepaymentMode.SHORTER_TERM, "Előtörlesztés")

        val o = repo.getOverview(id)!!
        assertEquals(12, o.paidInstallments) // az előtörlesztés tétele nem részlet
        assertEquals(calc.balanceBeforeMinor - 300_000, o.outstandingMinor)
        assertEquals(12 + calc.shorterTerm.remainingMonths, o.schedule.installments.size)
        val r = rule(id)
        assertEquals(o.schedule.lastDueDate, r.endDate)
        // A terv vége utáni függő tételek törlődtek, a korábbiak megmaradtak.
        assertTrue(pendingStatuses(r.id).all { (date, _) -> !date.isAfter(r.endDate) })
        assertTrue(pendingStatuses(r.id).any { (date, status) -> date == first.plusMonths(12) && status == PendingStatus.PENDING })

        val tx = db.transactionDao().getById(o.events.single().transactionId!!)!!
        assertEquals(300_000L + calc.penaltyMinor, tx.amountMinor)
        assertEquals(id, tx.bankLoanId)

        // Visszavonás: a tétel is törlődik, a terv visszaáll.
        repo.deleteEvent(o.events.single().id)
        assertNull(db.transactionDao().getById(tx.id))
        assertEquals(36, repo.getOverview(id)!!.schedule.installments.size)
        assertEquals(LocalDate.of(2028, 12, 15), rule(id).endDate)
    }

    @Test
    fun elotorlesztesCsokkenoReszlettelEsKamatvaltozas() = runTest {
        val id = repo.save(loan())
        repeat(12) { repo.payNextInstallment(id, first.plusMonths(it.toLong()), monthly) }
        val calc = LoanCalculator.prepayment(1_000_000, 13.79, 36, 300_000, 12, 1.0, 200_000)
        repo.addPrepayment(id, first.plusMonths(12), 300_000, calc.penaltyMinor, PrepaymentMode.LOWER_PAYMENT, "")
        assertEquals(calc.lowerPayment.monthlyPaymentMinor, rule(id).amountMinor)

        val balance = repo.getOverview(id)!!.outstandingMinor
        repo.changeRate(id, first.plusMonths(12), 8.0)
        assertEquals(LoanCalculator.calculate(balance, 8.0, 24).monthlyPaymentMinor, rule(id).amountMinor)
        assertEquals(2, repo.getOverview(id)!!.events.size)
    }

    @Test
    fun lezarasEsUjranyitas() = runTest {
        val id = repo.save(loan())
        val ruleId = rule(id).id
        recurring.generateDue(first.plusMonths(1))
        repo.close(id, first.plusMonths(1))
        assertFalse(rule(id).isActive)
        assertTrue(pendingStatuses(ruleId).isEmpty())
        assertThrows(IllegalArgumentException::class.java) { kotlinx.coroutines.runBlocking { repo.payNextInstallment(id, first, monthly) } }

        repo.reopen(id)
        assertTrue(rule(id).isActive)
        assertEquals(first, rule(id).nextDueDate)
        recurring.generateDue(first.plusMonths(1))
        assertEquals(2, pendingStatuses(ruleId).size) // a lezáráskor törölt esedékességek visszajönnek
    }

    @Test
    fun torlesUtanATetelekMaradnakASzabalyTorlodik() = runTest {
        val id = repo.save(loan())
        val ruleId = rule(id).id
        val txId = repo.payNextInstallment(id, first, monthly)
        repo.delete(id)
        assertNull(repo.get(id))
        assertNull(db.recurringDao().getRule(ruleId))
        val tx = db.transactionDao().getById(txId)!!
        assertNull(tx.bankLoanId)
        assertEquals(monthly, tx.amountMinor)
    }

    @Test
    fun modositasUjElsoEsedekesseggel() = runTest {
        val id = repo.save(loan())
        recurring.generateDue(first)
        val newFirst = first.plusDays(5)
        repo.save(loan(id).copy(firstDueDate = newFirst, termMonths = 24))
        val r = rule(id)
        assertEquals(newFirst, r.startDate)
        assertEquals(newFirst, r.nextDueDate)
        assertEquals(newFirst.plusMonths(23), r.endDate)
        assertTrue(pendingStatuses(r.id).isEmpty()) // a régi esedékesség elavult
    }

    @Test
    fun alkategoriaASzabalybanEsATetelekben() = runTest {
        val subId = db.categoryDao().insertSubcategory(SubcategoryEntity(categoryId = categoryId, name = "Hiteltörlesztés"))
        val id = repo.save(loan().copy(subcategoryId = subId))
        assertEquals(subId, rule(id).subcategoryId)
        val txId = repo.payNextInstallment(id, first, monthly)
        assertEquals(subId, db.transactionDao().getById(txId)!!.subcategoryId)

        // Másik kategória alkategóriája nem választható.
        val other = db.categoryDao().insert(CategoryEntity(name = "Egyéb", type = CategoryType.EXPENSE, icon = "x", color = 0))
        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking { repo.save(loan().copy(categoryId = other, subcategoryId = subId)) }
        }
    }

    @Test
    fun korabbanFelvettHitel_bankiHatralekkal() = runTest {
        // Az első részlet jan. 15-én volt; 10 részlet már befizetve (könyvelés nélkül), a bank szerint 700 000 Ft a hátralék.
        val id = repo.save(loan().copy(paidMonthsBefore = 10, bankBalanceMinor = 700_000))
        val o = repo.getOverview(id)!!
        assertEquals(10, o.paidInstallments)
        assertEquals(700_000L, o.outstandingMinor)
        assertEquals(36, o.totalMonthsForProgress)
        assertEquals(10, o.paidMonthsForProgress)
        val next = o.nextInstallment!!
        assertEquals(11, next.index)
        assertEquals(first.plusMonths(10), next.dueDate)
        assertEquals(LoanCalculator.calculate(700_000, 13.79, 26).monthlyPaymentMinor, next.paymentMinor)

        // A szabály a 11. részlettől indul: a korábbi esedékességeket nem kéri jóváhagyásra.
        val r = rule(id)
        assertEquals(first, r.startDate)
        assertEquals(first.plusMonths(10), r.nextDueDate)
        assertEquals(next.paymentMinor, r.amountMinor)
        recurring.generateDue(first.plusMonths(10))
        assertEquals(listOf(first.plusMonths(10) to PendingStatus.PENDING), pendingStatuses(r.id))

        repo.payNextInstallment(id, first.plusMonths(10), next.paymentMinor)
        assertEquals(11, repo.getOverview(id)!!.paidInstallments)
        assertEquals(1, db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM transactions").use { c -> c.moveToFirst(); c.getInt(0) })
    }

    @Test
    fun korabbanFelvettHitel_bankiHatralekNelkulASzamoltErtek() = runTest {
        val id = repo.save(loan().copy(paidMonthsBefore = 12))
        val o = repo.getOverview(id)!!
        assertEquals(o.schedule.installments[11].balanceAfterMinor, o.outstandingMinor)
        assertEquals(monthly, o.nextInstallment!!.paymentMinor)
    }

    @Test
    fun ervenytelenAdatok() = runTest {
        val incomeCategory = db.categoryDao().insert(CategoryEntity(name = "Fizetés", type = CategoryType.INCOME, icon = "x", color = 0))
        assertThrows(IllegalArgumentException::class.java) { kotlinx.coroutines.runBlocking { repo.save(loan().copy(name = " ")) } }
        assertThrows(IllegalArgumentException::class.java) { kotlinx.coroutines.runBlocking { repo.save(loan().copy(categoryId = incomeCategory)) } }
        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking { repo.save(loan().copy(paidMonthsBefore = 36)) } // nem maradna részlet
        }
        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking { repo.save(loan().copy(bankBalanceMinor = 500_000)) } // befizetett részlet nélkül
        }
        db.currencyDao().insertAll(listOf(CurrencyEntity("EUR", "Euró", "€", 2)))
        val eur = db.accountDao().insert(AccountEntity(name = "EUR", currencyCode = "EUR", color = 0, icon = "cash"))
        assertEquals("EUR", repo.get(repo.save(loan().copy(accountId = eur)))!!.currencyCode)
        assertNotNull(rule(repo.observeAll().first().first { it.loan.accountId == eur }.loan.id))
    }
}
