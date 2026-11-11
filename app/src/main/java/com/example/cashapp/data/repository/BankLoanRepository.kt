package com.example.cashapp.data.repository

import androidx.room.withTransaction
import com.example.cashapp.data.local.CashDatabase
import com.example.cashapp.data.local.entity.BankLoanEntity
import com.example.cashapp.data.local.entity.BankLoanEventEntity
import com.example.cashapp.data.local.entity.PendingRecurringEntity
import com.example.cashapp.data.local.entity.TransactionEntity
import com.example.cashapp.data.local.model.BankLoanWithProgress
import com.example.cashapp.domain.loan.AmortizationSchedule
import com.example.cashapp.domain.model.BankLoanEventType
import com.example.cashapp.domain.model.CategoryType
import com.example.cashapp.domain.model.PendingStatus
import com.example.cashapp.domain.model.PrepaymentMode
import com.example.cashapp.domain.model.TransactionType
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Banki hitel + a törlesztési terve és a befizetések állása (a UI ebből dolgozik).
 * A befizetett részletek a követés előtt befizetetteket ([BankLoanEntity.paidMonthsBefore]) is tartalmazzák.
 */
data class BankLoanOverview(
    val loan: BankLoanEntity,
    val events: List<BankLoanEventEntity>,
    val schedule: AmortizationSchedule.Schedule,
    /** Befizetett részletek (a követés előtt befizetettekkel együtt). */
    val paidInstallments: Int,
    val lastPaymentDate: LocalDate?,
) {
    val outstandingMinor: Long get() = schedule.outstandingAfter(paidInstallments)
    val nextInstallment: AmortizationSchedule.Installment? get() = schedule.nextAfter(paidInstallments)
    val isClosed: Boolean get() = loan.closedDate != null
    val isPaidOff: Boolean get() = nextInstallment == null

    /** Progresshez: összes / befizetett részlet. */
    val totalMonthsForProgress: Int get() = schedule.installments.size
    val paidMonthsForProgress: Int get() = paidInstallments.coerceAtMost(schedule.installments.size)
}

/**
 * Banki kölcsönök (terv.md 4.12 / 9).
 *
 * A részletek a hitel havi ismétlődő szabályán át könyvelődnek (Jóváhagyásra vár lista vagy „Részlet befizetve”),
 * mindig rendes kiadás tételként a hitel számláján, `bankLoanId`-val. A szabályt a [BankLoanRuleSync] tartja
 * összhangban a törlesztési tervvel minden változás után.
 */
class BankLoanRepository(private val db: CashDatabase) {

    private val dao = db.bankLoanDao()
    private val accountDao = db.accountDao()
    private val categoryDao = db.categoryDao()
    private val transactionDao = db.transactionDao()
    private val recurringDao = db.recurringDao()
    private val ruleSync = BankLoanRuleSync(db)

    fun observeAll(): Flow<List<BankLoanOverview>> =
        combine(dao.observeAll(), dao.observeAllEvents()) { loans, events ->
            val byLoan = events.groupBy { it.bankLoanId }
            loans.map { it.toOverview(byLoan[it.loan.id].orEmpty()) }
        }

    fun observe(id: Long): Flow<BankLoanOverview?> =
        combine(dao.observe(id), dao.observeEvents(id)) { loan, events -> loan?.toOverview(events) }

    suspend fun get(id: Long): BankLoanEntity? = dao.getById(id)

    suspend fun getOverview(id: Long): BankLoanOverview? = dao.getWithProgress(id)?.toOverview(dao.getEvents(id))

    private fun BankLoanWithProgress.toOverview(events: List<BankLoanEventEntity>) =
        BankLoanOverview(loan, events, loan.schedule(events), paidInstallments, lastPaymentDate)

    /**
     * Új hitel vagy meglévő módosítása; utána a havi szabály létrejön / frissül.
     * A pénznem mindig a terhelt számláé. A lezárás, a szabály és a létrehozás ideje nem az űrlapról jön.
     * @throws IllegalArgumentException érvénytelen adatnál
     */
    suspend fun save(loan: BankLoanEntity): Long = db.withTransaction {
        require(loan.name.isNotBlank()) { "Adj meg nevet" }
        require(loan.principalMinor > 0) { "A hitelösszeg legyen pozitív" }
        require(loan.thmPercent >= 0) { "A THM nem lehet negatív" }
        require(loan.termMonths >= 1) { "A futamidő legalább 1 hónap" }
        require(loan.monthlyPaymentOverrideMinor == null || loan.monthlyPaymentOverrideMinor > 0) { "A részlet legyen pozitív" }
        require(loan.paidMonthsBefore in 0 until loan.termMonths) { "A már befizetett részletek száma kevesebb legyen a futamidőnél" }
        require(loan.bankBalanceMinor == null || (loan.bankBalanceMinor > 0 && loan.paidMonthsBefore > 0)) {
            "A bank szerinti hátralék csak korábban felvett hitelnél adható meg, és pozitív legyen"
        }
        val account = checkNotNull(accountDao.getById(loan.accountId)) { "Nincs ilyen számla" }
        val category = checkNotNull(categoryDao.getById(loan.categoryId)) { "Nincs ilyen kategória" }
        require(category.type == CategoryType.EXPENSE) { "A kategória kiadás kategória legyen" }
        loan.subcategoryId?.let { subId ->
            val sub = checkNotNull(categoryDao.getSubcategoryById(subId)) { "Nincs ilyen alkategória" }
            require(sub.categoryId == category.id) { "Az alkategória nem ehhez a kategóriához tartozik" }
        }
        val normalized = loan.copy(name = loan.name.trim(), bankName = loan.bankName.trim(), note = loan.note.trim(), currencyCode = account.currencyCode)

        val existing = loan.id.takeIf { it != 0L }?.let { dao.getById(it) }
        val id = if (existing == null) {
            dao.insert(normalized.copy(id = 0, recurringRuleId = null, closedDate = null))
        } else {
            dao.update(
                normalized.copy(
                    createdAt = existing.createdAt,
                    closedDate = existing.closedDate,
                    recurringRuleId = existing.recurringRuleId,
                )
            )
            existing.id
        }
        ruleSync.sync(id)
        id
    }

    /**
     * „Részlet befizetve”: a következő be nem fizetett részlet könyvelése ([date] napon, [amountMinor] összeggel).
     * Ha az esedékességhez már van jóváhagyásra váró tétel, azt hagyja jóvá; különben közvetlenül könyvel, és egy
     * jóváhagyott esedékességet is felír, hogy a generátor később ne kérje újra.
     * @return a létrejött tétel azonosítója
     */
    suspend fun payNextInstallment(loanId: Long, date: LocalDate, amountMinor: Long): Long = db.withTransaction {
        require(amountMinor > 0) { "Az összeg legyen pozitív" }
        val overview = checkNotNull(getOverview(loanId)) { "Nincs ilyen hitel: $loanId" }
        val loan = overview.loan
        require(!overview.isClosed) { "A hitel le van zárva" }
        val next = checkNotNull(overview.nextInstallment) { "Minden részlet be van fizetve" }

        val pending = loan.recurringRuleId?.let { recurringDao.getPendingFor(it, next.dueDate) }
        val txId = transactionDao.insert(
            TransactionEntity(
                type = TransactionType.EXPENSE,
                date = date,
                amountMinor = amountMinor,
                currencyCode = loan.currencyCode,
                accountId = loan.accountId,
                accountAmountMinor = amountMinor,
                categoryId = loan.categoryId,
                subcategoryId = loan.subcategoryId,
                note = loan.name,
                recurringRuleId = loan.recurringRuleId,
                bankLoanId = loan.id,
            )
        )
        loan.recurringRuleId?.let { ruleId ->
            when {
                pending == null -> recurringDao.insertPendingAll(
                    listOf(PendingRecurringEntity(ruleId = ruleId, dueDate = next.dueDate, status = PendingStatus.APPROVED, transactionId = txId))
                )
                pending.status == PendingStatus.PENDING ->
                    recurringDao.updatePending(pending.copy(status = PendingStatus.APPROVED, transactionId = txId))
                else -> Unit // már elbírált (pl. kihagyott) esedékesség: csak a tétel jön létre
            }
        }
        ruleSync.sync(loanId)
        txId
    }

    /**
     * Előtörlesztés az eddig befizetett részletek után: kiadás tétel (előtörlesztés + [penaltyMinor] büntető)
     * a hitel számláján, és egy esemény, amely a törlesztési tervet [mode] szerint módosítja.
     * Az előtörlesztés legfeljebb a hátralék lehet. [note]: a tétel megjegyzése (a UI adja, nyelv szerint).
     */
    suspend fun addPrepayment(
        loanId: Long,
        date: LocalDate,
        amountMinor: Long,
        penaltyMinor: Long,
        mode: PrepaymentMode,
        note: String,
    ): Long = db.withTransaction {
        require(amountMinor > 0) { "Az előtörlesztés legyen pozitív" }
        require(penaltyMinor >= 0) { "A büntető nem lehet negatív" }
        val overview = checkNotNull(getOverview(loanId)) { "Nincs ilyen hitel: $loanId" }
        val loan = overview.loan
        require(!overview.isClosed) { "A hitel le van zárva" }
        val outstanding = overview.outstandingMinor
        require(outstanding > 0) { "A hitel már ki van fizetve" }
        val prepaid = minOf(amountMinor, outstanding)
        val total = prepaid + penaltyMinor
        val txId = transactionDao.insert(
            TransactionEntity(
                type = TransactionType.EXPENSE,
                date = date,
                amountMinor = total,
                currencyCode = loan.currencyCode,
                accountId = loan.accountId,
                accountAmountMinor = total,
                categoryId = loan.categoryId,
                subcategoryId = loan.subcategoryId,
                note = note.trim(),
                bankLoanId = loan.id,
            )
        )
        val eventId = dao.insertEvent(
            BankLoanEventEntity(
                bankLoanId = loanId,
                date = date,
                afterInstallment = overview.paidInstallments,
                type = BankLoanEventType.PREPAYMENT,
                amountMinor = prepaid,
                penaltyMinor = penaltyMinor,
                mode = mode,
                transactionId = txId,
            )
        )
        ruleSync.sync(loanId)
        eventId
    }

    /** Kamatváltozás az eddig befizetett részletek után: a hátralévő részletek az új THM-mel számolnak. */
    suspend fun changeRate(loanId: Long, date: LocalDate, newThmPercent: Double): Long = db.withTransaction {
        require(newThmPercent >= 0) { "A THM nem lehet negatív" }
        val overview = checkNotNull(getOverview(loanId)) { "Nincs ilyen hitel: $loanId" }
        require(!overview.isPaidOff) { "A hitel már ki van fizetve" }
        val eventId = dao.insertEvent(
            BankLoanEventEntity(
                bankLoanId = loanId,
                date = date,
                afterInstallment = overview.paidInstallments,
                type = BankLoanEventType.RATE_CHANGE,
                newThmPercent = newThmPercent,
            )
        )
        ruleSync.sync(loanId)
        eventId
    }

    /** Esemény visszavonása; előtörlesztésnél a könyvelt tétele is törlődik. */
    suspend fun deleteEvent(eventId: Long) = db.withTransaction {
        val event = dao.getEvent(eventId) ?: return@withTransaction
        dao.deleteEvent(eventId)
        event.transactionId?.let { transactionDao.deleteById(it) }
        ruleSync.sync(event.bankLoanId)
    }

    /** Lezárás: a havi szabály kikapcsol, a függő részletei törlődnek. Számlát nem mozgat; újranyitható. */
    suspend fun close(id: Long, date: LocalDate = LocalDate.now()) = db.withTransaction {
        dao.setClosed(id, date)
        ruleSync.sync(id)
    }

    /** Újranyitás: a szabály a következő be nem fizetett részlettől folytatódik. */
    suspend fun reopen(id: Long) = db.withTransaction {
        dao.setClosed(id, null)
        ruleSync.sync(id)
    }

    /**
     * A hitel, az eseményei és a havi szabálya törlődik. A már könyvelt tételek (részletek, előtörlesztések)
     * megmaradnak – valódi kiadások voltak –, csak a hitelhez kötésük szűnik meg.
     */
    suspend fun delete(id: Long) = db.withTransaction {
        val loan = dao.getById(id) ?: return@withTransaction
        dao.deleteById(id)
        loan.recurringRuleId?.let { ruleId -> recurringDao.getRule(ruleId)?.let { recurringDao.deleteRule(it) } }
    }
}
