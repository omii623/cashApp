package com.example.cashapp.data.backup

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.cashapp.data.TestDb
import com.example.cashapp.data.local.CashDatabase
import com.example.cashapp.data.local.entity.AccountEntity
import com.example.cashapp.data.local.entity.BankLoanEntity
import com.example.cashapp.data.local.entity.BankLoanEventEntity
import com.example.cashapp.data.local.entity.CurrencyEntity
import com.example.cashapp.data.local.entity.LoanEntity
import com.example.cashapp.data.local.entity.TransactionEntity
import com.example.cashapp.data.repository.LoanRepository
import com.example.cashapp.domain.model.LoanDirection
import com.example.cashapp.data.repository.RecurringRepository
import com.example.cashapp.data.settings.SettingsRepository
import com.example.cashapp.domain.model.TransactionType
import com.example.cashapp.domain.model.BankLoanEventType
import com.example.cashapp.domain.model.BankLoanKind
import com.example.cashapp.domain.model.PrepaymentMode
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    /** Magánkölcsönök minden adatával: nyitó összegek (könyvelve is), más pénznemű mozgás, lezárás, szín / ikon. */
    @Test
    fun exportRestoreRoundtrip_kolcsonokkel() = runTest {
        val (accountId, _) = TestDb.seedBasics(db)
        db.currencyDao().insertAll(listOf(CurrencyEntity("EUR", "Euró", "€", 2)))
        val eurAccount = db.accountDao().insert(AccountEntity(name = "EUR", currencyCode = "EUR", color = 0, icon = "cash"))
        val loans = LoanRepository(db)
        val day = LocalDate.of(2026, 11, 9)
        val lent = loans.save(
            LoanEntity(personName = "Pisti", direction = LoanDirection.LENT, currencyCode = "HUF",
                openingAmountMinor = 1_000, openingRepaidMinor = 600, startDate = day, color = 0xFF5C6BC0, icon = "letter:PT"),
            LoanRepository.OpeningBooking(bookAmount = true, accountId = accountId),
        )
        loans.saveMovement(lent, LoanRepository.MovementDraft(true, 10_000, accountId, day, "kölcsön"))
        loans.saveMovement(lent, LoanRepository.MovementDraft(
            increase = false, amountMinor = 2_000, accountId = eurAccount, date = day, note = "",
            currencyCode = "EUR", accountAmountMinor = 2_000, loanAmountMinor = 8_000,
        ))
        val borrowed = loans.save(LoanEntity(personName = "Anya", direction = LoanDirection.BORROWED, currencyCode = "HUF", startDate = day))
        loans.saveMovement(borrowed, LoanRepository.MovementDraft(true, 5_000, accountId, day, ""))
        loans.close(borrowed, day)

        val before = dump()
        val outstandingBefore = db.loanDao().getWithBalance(lent)!!.outstandingMinor
        val uri = Uri.fromFile(file)
        manager.exportJson(uri)

        // Módosítás a mentés után: az egyik kölcsön törlődik, a másik újranyílik.
        loans.delete(lent)
        loans.reopen(borrowed)

        val backup = manager.readBackup(uri)
        assertEquals(2, backup.tables["loans"]!!.size)
        manager.restore(backup)

        assertEquals(before, dump())
        assertEquals(outstandingBefore, db.loanDao().getWithBalance(lent)!!.outstandingMinor)
        assertEquals(day, loans.get(borrowed)!!.closedDate)
        assertEquals("letter:PT", loans.get(lent)!!.icon)
        assertEquals(1, loans.getOpenings(lent).size)
    }

    /** Régebbi (v3-as, kölcsön előtti) mentés is visszaállítható: nincs loans tábla, a tételeknél nincs loanId. */
    @Test
    fun regiMentesKolcsonNelkulIsVisszaallithato() = runTest {
        val (accountId, categoryId) = TestDb.seedBasics(db)
        db.transactionDao().insert(
            TransactionEntity(type = TransactionType.EXPENSE, date = LocalDate.of(2026, 1, 2), amountMinor = 500,
                currencyCode = "HUF", accountId = accountId, accountAmountMinor = 500, categoryId = categoryId)
        )
        val uri = Uri.fromFile(file)
        manager.exportJson(uri)
        val current = manager.readBackup(uri)
        val loanColumns = setOf("loanId", "loanOpening", "loanAmountMinor")
        val old = current.copy(
            dbVersion = 3,
            tables = (current.tables - "loans").mapValues { (table, rows) ->
                if (table == "transactions") rows.map { r -> r.filterKeys { it !in loanColumns } } else rows
            },
        )
        manager.restore(old)

        val txs = db.openHelper.readableDatabase.query("SELECT loanId, amountMinor FROM transactions").use { c ->
            buildList { while (c.moveToNext()) add((if (c.isNull(0)) null else c.getLong(0)) to c.getLong(1)) }
        }
        assertEquals(listOf<Pair<Long?, Long>>(null to 500L), txs)
        assertTrue(db.loanDao().observeAll().first().isEmpty())
    }

    /** Banki hitel minden adatával: valós THM, futó hitel adatai, részlet tétel és előtörlesztés esemény. */
    @Test
    fun exportRestoreRoundtrip_bankiHitellel() = runTest {
        val (accountId, categoryId) = TestDb.seedBasics(db)
        val day = LocalDate.of(2026, 11, 15)
        val dao = db.bankLoanDao()
        val loanId = dao.insert(
            BankLoanEntity(
                name = "Lakáshitel", bankName = "Bank", kind = BankLoanKind.MORTGAGE, currencyCode = "HUF",
                principalMinor = 3_950_000, thmPercent = 6.37, termMonths = 72, firstDueDate = day,
                monthlyPaymentOverrideMinor = 66_000, originalPrincipalMinor = 6_000_000, paidMonthsBefore = 48,
                accountId = accountId, categoryId = categoryId, color = 0xFF5C6BC0, icon = "home",
            )
        )
        val txId = db.transactionDao().insert(
            TransactionEntity(
                type = TransactionType.EXPENSE, date = day, amountMinor = 250_000, currencyCode = "HUF",
                accountId = accountId, accountAmountMinor = 250_000, categoryId = categoryId, bankLoanId = loanId,
            )
        )
        dao.insertEvent(
            BankLoanEventEntity(
                bankLoanId = loanId, date = day, afterInstallment = 0, type = BankLoanEventType.PREPAYMENT,
                amountMinor = 245_000, penaltyMinor = 5_000, mode = PrepaymentMode.LOWER_PAYMENT, transactionId = txId,
            )
        )

        val before = dump()
        val uri = Uri.fromFile(file)
        manager.exportJson(uri)
        dao.deleteById(loanId)

        val backup = manager.readBackup(uri)
        assertEquals(1, backup.tables["bank_loans"]!!.size)
        assertEquals(1, backup.tables["bank_loan_events"]!!.size)
        manager.restore(backup)

        assertEquals(before, dump())
        assertEquals(6.37, dao.getById(loanId)!!.thmPercent, 0.0)
        assertEquals(loanId, db.transactionDao().getById(txId)!!.bankLoanId)
    }

    /** v4-es (banki hitel előtti) mentés is visszaállítható: nincsenek bank_loan táblák, a tételeknél nincs bankLoanId. */
    @Test
    fun regiMentesBankiHitelNelkulIsVisszaallithato() = runTest {
        val (accountId, categoryId) = TestDb.seedBasics(db)
        db.transactionDao().insert(
            TransactionEntity(type = TransactionType.EXPENSE, date = LocalDate.of(2026, 1, 2), amountMinor = 700,
                currencyCode = "HUF", accountId = accountId, accountAmountMinor = 700, categoryId = categoryId)
        )
        val uri = Uri.fromFile(file)
        manager.exportJson(uri)
        val current = manager.readBackup(uri)
        val old = current.copy(
            dbVersion = 4,
            tables = (current.tables - "bank_loans" - "bank_loan_events").mapValues { (table, rows) ->
                if (table == "transactions") rows.map { r -> r - "bankLoanId" } else rows
            },
        )
        manager.restore(old)

        val txs = db.openHelper.readableDatabase.query("SELECT bankLoanId, amountMinor FROM transactions").use { c ->
            buildList { while (c.moveToNext()) add((if (c.isNull(0)) null else c.getLong(0)) to c.getLong(1)) }
        }
        assertEquals(listOf<Pair<Long?, Long>>(null to 700L), txs)
        assertTrue(db.bankLoanDao().observeAll().first().isEmpty())
    }
}
