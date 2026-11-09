package com.example.cashapp.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.cashapp.data.TestDb
import com.example.cashapp.data.local.CashDatabase
import com.example.cashapp.data.local.entity.LoanEntity
import com.example.cashapp.data.local.entity.TransactionEntity
import com.example.cashapp.data.repository.LoanRepository.MovementDraft
import com.example.cashapp.domain.model.LoanDirection
import com.example.cashapp.domain.model.TransactionType
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoanRepositoryTest {

    private lateinit var db: CashDatabase
    private lateinit var repo: LoanRepository
    private var accountId = 0L
    private var categoryId = 0L
    private val day = LocalDate.of(2026, 11, 9)

    @Before
    fun setUp() = runTest {
        db = TestDb.inMemory()
        repo = LoanRepository(db)
        TestDb.seedBasics(db).let { (a, c) -> accountId = a; categoryId = c }
    }

    @After
    fun tearDown() = db.close()

    private suspend fun balance() = db.accountDao().observeWithBalance(accountId).first()!!.balanceMinor
    private suspend fun outstanding(id: Long) = db.loanDao().getWithBalance(id)!!.outstandingMinor
    private fun mv(increase: Boolean, amount: Long) = MovementDraft(increase, amount, accountId, day, "")

    @Test
    fun kolcsonadasEsVisszafizetes_hatralekEsSzamlaEgyenleg() = runTest {
        val id = repo.save(LoanEntity(personName = "Pisti", direction = LoanDirection.LENT, currencyCode = "HUF", openingAmountMinor = 1_000, startDate = day))
        repo.saveMovement(id, mv(increase = true, amount = 10_000))
        assertEquals(11_000, outstanding(id))
        assertEquals(-10_000, balance())

        repo.saveMovement(id, mv(increase = false, amount = 4_000))
        assertEquals(7_000, outstanding(id))
        assertEquals(-6_000, balance())
    }

    @Test
    fun kolcsonkeres_bevetelAzElejen() = runTest {
        val id = repo.save(LoanEntity(personName = "Bank", direction = LoanDirection.BORROWED, currencyCode = "HUF", startDate = day))
        repo.saveMovement(id, mv(increase = true, amount = 50_000))
        assertEquals(50_000, outstanding(id))
        assertEquals(50_000, balance())
        repo.saveMovement(id, mv(increase = false, amount = 50_000))
        assertEquals(0, outstanding(id))
        assertEquals(0, balance())
    }

    @Test
    fun kolcsonOsszegEsMarVisszafizetett_hatralekAKulonbseg() = runTest {
        // 1000 Ft kölcsön, ebből 600 már visszafizetve → 400 a hátralék, számla nem mozdul.
        val id = repo.save(LoanEntity(personName = "Pisti", direction = LoanDirection.LENT, currencyCode = "HUF",
            openingAmountMinor = 1_000, openingRepaidMinor = 600, startDate = day))
        assertEquals(400, outstanding(id))
        assertEquals(0, balance())
        repo.saveMovement(id, mv(increase = false, amount = 400))
        assertEquals(0, outstanding(id))
    }

    @Test
    fun marVisszafizetettNemLehetTobbAKolcsonnel() = runTest {
        try {
            repo.save(LoanEntity(personName = "Pisti", direction = LoanDirection.LENT, currencyCode = "HUF",
                openingAmountMinor = 1_000, openingRepaidMinor = 1_200, startDate = day))
            fail("el kellett volna utasítani")
        } catch (_: IllegalArgumentException) { }
    }

    @Test
    fun marVisszafizetettModositasaUtanSemMegyMinuszbaAHatralek() = runTest {
        val id = repo.save(LoanEntity(personName = "Pisti", direction = LoanDirection.LENT, currencyCode = "HUF",
            openingAmountMinor = 1_000, startDate = day))
        repo.saveMovement(id, mv(increase = false, amount = 700))
        repo.save(repo.get(id)!!.copy(openingRepaidMinor = 500))
        assertEquals(0, outstanding(id))
    }

    @Test
    fun masPenznembenMasSzamlara_hatralekAKolcsonPenzneme() = runTest {
        // HUF-os kölcsön, a visszafizetés EUR-ban érkezik egy EUR-os számlára.
        db.currencyDao().insertAll(listOf(com.example.cashapp.data.local.entity.CurrencyEntity("EUR", "Euró", "€", 2)))
        val eurAccount = db.accountDao().insert(com.example.cashapp.data.local.entity.AccountEntity(name = "EUR", currencyCode = "EUR", color = 0, icon = "cash"))
        val id = repo.save(LoanEntity(personName = "Pisti", direction = LoanDirection.LENT, currencyCode = "HUF", openingAmountMinor = 40_000, startDate = day))
        repo.saveMovement(id, MovementDraft(
            increase = false, amountMinor = 5_000, accountId = eurAccount, date = day, note = "",
            currencyCode = "EUR", accountAmountMinor = 5_000, loanAmountMinor = 20_000,
        ))
        assertEquals(20_000, outstanding(id))
        assertEquals(5_000, db.accountDao().observeWithBalance(eurAccount).first()!!.balanceMinor)
        // Túlfizetés (24 000 Ft-nak megfelelő összeg) → rendezett, a hátralék 0.
        repo.saveMovement(id, MovementDraft(
            increase = false, amountMinor = 6_000, accountId = eurAccount, date = day, note = "",
            currencyCode = "EUR", accountAmountMinor = 6_000, loanAmountMinor = 24_000,
        ))
        assertEquals(0, outstanding(id))
    }

    @Test
    fun lezarasMaradekkal_esUjranyitas() = runTest {
        val id = repo.save(LoanEntity(personName = "Pisti", direction = LoanDirection.LENT, currencyCode = "HUF", startDate = day))
        repo.saveMovement(id, mv(increase = true, amount = 10_000))
        repo.saveMovement(id, mv(increase = false, amount = 3_000))
        repo.close(id, day)
        val closed = db.loanDao().getWithBalance(id)!!
        assertEquals(day, closed.loan.closedDate)
        assertEquals(7_000, closed.outstandingMinor) // a leírt maradék látszik
        assertEquals(-7_000, balance())              // a számlát nem érinti
        // Szerkesztés nem nyitja újra.
        repo.save(repo.get(id)!!.copy(personName = "Pista", closedDate = null))
        assertEquals(day, repo.get(id)!!.closedDate)
        repo.reopen(id)
        assertNull(repo.get(id)!!.closedDate)
    }

    @Test
    fun tulfizetesRendeziAKolcsont() = runTest {
        val id = repo.save(LoanEntity(personName = "Pisti", direction = LoanDirection.LENT, currencyCode = "HUF", startDate = day))
        repo.saveMovement(id, mv(increase = true, amount = 5_000))
        repo.saveMovement(id, mv(increase = false, amount = 6_000))
        // A hátralék nem megy mínuszba: 0, a kölcsön rendezett. A számlára a teljes összeg megérkezik.
        assertEquals(0, outstanding(id))
        assertEquals(1_000, balance())
    }

    @Test
    fun szerkesztesnelASajatOsszegNemSzamitTulfizetesnek() = runTest {
        val id = repo.save(LoanEntity(personName = "Pisti", direction = LoanDirection.LENT, currencyCode = "HUF", startDate = day))
        repo.saveMovement(id, mv(increase = true, amount = 5_000))
        val repay = repo.saveMovement(id, mv(increase = false, amount = 3_000))
        repo.saveMovement(id, mv(increase = false, amount = 5_000), transactionId = repay)
        assertEquals(0, outstanding(id))
    }

    @Test
    fun kolcsonMozgasBeleszamitAKimutatasokba() = runTest {
        val id = repo.save(LoanEntity(personName = "Pisti", direction = LoanDirection.LENT, currencyCode = "HUF", startDate = day))
        repo.saveMovement(id, mv(increase = true, amount = 10_000))
        repo.saveMovement(id, mv(increase = false, amount = 4_000))
        db.transactionDao().insert(
            TransactionEntity(type = TransactionType.EXPENSE, date = day, amountMinor = 700, currencyCode = "HUF",
                accountId = accountId, accountAmountMinor = 700, categoryId = categoryId)
        )
        val totals = db.transactionDao().observeTotals(day, day).first().associate { it.type to it.totalMinor }
        assertEquals(10_700L, totals[TransactionType.EXPENSE])
        assertEquals(4_000L, totals[TransactionType.INCOME])
        assertEquals(3, db.transactionDao().observeAmountRows(day, day).first().size)
        // A kategória-bontásban saját csoportban (id = -1), nem a kategória nélküliek között.
        val byCategory = db.transactionDao().observeCategoryTotals("EXPENSE", day, day).first().associate { it.id to it.amountMinor }
        assertEquals(10_000L, byCategory[-1L])
        assertEquals(700L, byCategory[categoryId])
        assertNull(byCategory[null])
        // A listában a kölcsön nevével jelenik meg.
        val list = db.transactionDao().observeRecent(10).first()
        assertTrue(list.any { it.loanName == "Pisti" && it.loanDirection == LoanDirection.LENT })
    }

    @Test
    fun nyitoOsszegekKonyvelese_szamlaEsKimutatas_hatralekNemDuplazodik() = runTest {
        // 1000 kölcsön, ebből 600 visszafizetve – mindkettő könyvelve a számlára.
        val loan = LoanEntity(personName = "Pisti", direction = LoanDirection.LENT, currencyCode = "HUF",
            openingAmountMinor = 1_000, openingRepaidMinor = 600, startDate = day)
        val id = repo.save(loan, LoanRepository.OpeningBooking(bookAmount = true, bookRepaid = true, accountId = accountId))
        assertEquals(400, outstanding(id))
        assertEquals(-400, balance())
        val totals = db.transactionDao().observeTotals(day, day).first().associate { it.type to it.totalMinor }
        assertEquals(1_000L, totals[TransactionType.EXPENSE])
        assertEquals(600L, totals[TransactionType.INCOME])
        // A mozgások listájában nincsenek (a nyitó sorok mutatják őket), és nem zárolják az irányt.
        assertTrue(repo.observeMovements(id).first().isEmpty())
        assertEquals(2, repo.observeOpenings(id).first().size)

        // Csak az összeg maradjon könyvelve, és módosuljon.
        repo.save(repo.get(id)!!.copy(openingAmountMinor = 1_500), LoanRepository.OpeningBooking(bookAmount = true, accountId = accountId))
        assertEquals(900, outstanding(id))
        assertEquals(-1_500, balance())
        assertEquals(1, repo.getOpenings(id).size)

        // Kikapcsolva: a tétel törlődik, a számla visszaáll, a hátralék marad.
        repo.save(repo.get(id)!!)
        assertEquals(900, outstanding(id))
        assertEquals(0, balance())
        assertTrue(repo.getOpenings(id).isEmpty())
    }

    @Test
    fun nyitoKonyvelesIranyValtaskorATipusIsValtozik() = runTest {
        val id = repo.save(
            LoanEntity(personName = "Anya", direction = LoanDirection.LENT, currencyCode = "HUF", openingAmountMinor = 500, startDate = day),
            LoanRepository.OpeningBooking(bookAmount = true, accountId = accountId),
        )
        assertEquals(-500, balance())
        repo.save(repo.get(id)!!.copy(direction = LoanDirection.BORROWED), LoanRepository.OpeningBooking(bookAmount = true, accountId = accountId))
        assertEquals(500, balance())
        assertEquals(500, outstanding(id))
    }

    @Test
    fun kolcsonTorlese_mozgasokkalEgyutt_egyenlegVisszaall() = runTest {
        val id = repo.save(LoanEntity(personName = "Pisti", direction = LoanDirection.LENT, currencyCode = "HUF", startDate = day))
        repo.saveMovement(id, mv(increase = true, amount = 10_000))
        repo.delete(id)
        assertNull(repo.get(id))
        assertEquals(0, balance())
    }

    @Test
    fun mozgasUtanIranyNemValtoztathato() = runTest {
        val id = repo.save(LoanEntity(personName = "Pisti", direction = LoanDirection.LENT, currencyCode = "HUF", startDate = day))
        repo.saveMovement(id, mv(increase = true, amount = 1_000))
        val loan = repo.get(id)!!
        try {
            repo.save(loan.copy(direction = LoanDirection.BORROWED))
            fail("az irányváltást el kellett volna utasítani")
        } catch (_: IllegalArgumentException) { }
        repo.save(loan.copy(personName = "Pista"))
        assertEquals("Pista", repo.get(id)!!.personName)
    }
}
