package com.example.cashapp.data.repository

import androidx.room.withTransaction
import com.example.cashapp.data.local.CashDatabase
import com.example.cashapp.data.local.entity.PendingRecurringEntity
import com.example.cashapp.data.local.entity.RecurringRuleEntity
import com.example.cashapp.data.local.entity.TransactionEntity
import com.example.cashapp.data.local.model.PendingItem
import com.example.cashapp.data.local.model.RuleItem
import com.example.cashapp.domain.model.PendingStatus
import com.example.cashapp.domain.recurring.Recurrence
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/**
 * Ismétlődő tételek (terv.md 4.9 / 7).
 *
 * Alapelv: tranzakció **soha nem jön létre automatikusan**. A [generateDue] csak
 * „jóváhagyásra vár” bejegyzéseket készít; a könyvelés a felhasználó jóváhagyásakor történik.
 * A szabály pénzneme mindig a számláé, így jóváhagyáskor nem kell árfolyam.
 */
class RecurringRepository(private val db: CashDatabase) {

    private val dao = db.recurringDao()
    private val accountDao = db.accountDao()
    private val transactionDao = db.transactionDao()
    private val bankLoanDao = db.bankLoanDao()
    private val bankLoanRuleSync = BankLoanRuleSync(db)

    fun observePendingCount(today: LocalDate = LocalDate.now()): Flow<Int> = dao.observePendingCount(today)

    fun observePending(): Flow<List<PendingItem>> = dao.observePending()

    fun observeRules(): Flow<List<RuleItem>> = dao.observeRules()

    suspend fun getRule(id: Long): RecurringRuleEntity? = dao.getRule(id)

    /**
     * Szabály mentése. Új szabálynál, vagy ha az ütemezés (kezdés, gyakoriság, záró dátum)
     * megváltozott, a következő esedékesség újraszámolódik, és a régi, el nem bírált
     * tételei törlődnek (azok a régi ütemezés szerint készültek).
     */
    suspend fun saveRule(rule: RecurringRuleEntity, today: LocalDate = LocalDate.now()): Long =
        saveRuleInternal(rule, today).also { generateDue(today) } // ha már esedékes, azonnal megjelenjen

    private suspend fun saveRuleInternal(rule: RecurringRuleEntity, today: LocalDate): Long = db.withTransaction {
        val account = checkNotNull(accountDao.getById(rule.accountId)) { "Nincs ilyen számla" }
        val normalized = rule.copy(currencyCode = account.currencyCode)
        val existing = rule.id.takeIf { it != 0L }?.let { dao.getRule(it) }
        if (existing == null) {
            dao.insertRule(normalized.copy(id = 0, nextDueDate = normalized.startDate))
        } else {
            val scheduleChanged = existing.startDate != normalized.startDate ||
                existing.frequency != normalized.frequency ||
                existing.interval != normalized.interval ||
                existing.endDate != normalized.endDate
            if (scheduleChanged) {
                dao.deletePendingOfRule(existing.id)
                // A múltbeli esedékességeket nem generáljuk újra – mától folytatódik.
                val from = maxOf(normalized.startDate, today)
                val next = Recurrence.firstOnOrAfter(normalized.startDate, normalized.frequency, normalized.interval, from)
                dao.updateRule(normalized.copy(nextDueDate = next, isActive = normalized.isActive && isWithinEnd(next, normalized.endDate)))
            } else {
                dao.updateRule(normalized.copy(nextDueDate = existing.nextDueDate))
            }
            existing.id
        }
    }

    /** Be/kikapcsolás. Visszakapcsoláskor a kihagyott időszakot nem pótolja: mától folytatódik. */
    suspend fun setActive(id: Long, active: Boolean, today: LocalDate = LocalDate.now()) {
        db.withTransaction {
            val rule = dao.getRule(id) ?: return@withTransaction
            if (!active) {
                dao.updateRule(rule.copy(isActive = false))
            } else {
                val next = if (rule.nextDueDate.isBefore(today))
                    Recurrence.firstOnOrAfter(rule.startDate, rule.frequency, rule.interval, today) else rule.nextDueDate
                dao.updateRule(rule.copy(isActive = isWithinEnd(next, rule.endDate), nextDueDate = next))
            }
        }
        if (active) generateDue(today)
    }

    suspend fun deleteRule(id: Long) {
        dao.getRule(id)?.let { dao.deleteRule(it) }
    }

    /**
     * Esedékes tételek létrehozása minden aktív szabályhoz (indításkor és naponta fut).
     * Elmaradt napokat is pótolja (pl. ha napokig nem volt megnyitva az app).
     * @return az újonnan létrejött jóváhagyandó tételek száma
     */
    suspend fun generateDue(today: LocalDate = LocalDate.now()): Int = db.withTransaction {
        var created = 0
        for (rule in dao.getDueRules(today)) {
            val dates = Recurrence.dueDates(
                start = rule.startDate,
                frequency = rule.frequency,
                interval = rule.interval,
                from = rule.nextDueDate,
                until = today,
                end = rule.endDate,
            )
            dao.insertPendingAll(dates.map { PendingRecurringEntity(ruleId = rule.id, dueDate = it) })
            created += dates.size
            val next = Recurrence.nextAfter(rule.startDate, rule.frequency, rule.interval, dates.lastOrNull() ?: today)
            dao.updateRule(rule.copy(nextDueDate = next, isActive = isWithinEnd(next, rule.endDate)))
        }
        created
    }

    /**
     * Jóváhagyás: létrejön a tranzakció (a megadott összeggel és dátummal).
     * Banki hitel szabályánál a tétel a hitelhez kötődik (bankLoanId), és a szabály a következő részlethez igazodik.
     */
    suspend fun approve(pendingId: Long, amountMinor: Long, date: LocalDate) = db.withTransaction {
        require(amountMinor > 0) { "Az összeg legyen pozitív" }
        val pending = dao.getPending(pendingId) ?: return@withTransaction
        if (pending.status != PendingStatus.PENDING) return@withTransaction
        // A szabályt közben törölhették (a függő tétel CASCADE-del vele megy) → nincs teendő.
        val rule = dao.getRule(pending.ruleId) ?: return@withTransaction
        val bankLoanId = bankLoanDao.getByRuleId(rule.id)?.id
        val txId = transactionDao.insert(
            TransactionEntity(
                type = rule.type,
                date = date,
                amountMinor = amountMinor,
                currencyCode = rule.currencyCode,
                accountId = rule.accountId,
                accountAmountMinor = amountMinor,
                categoryId = rule.categoryId,
                subcategoryId = rule.subcategoryId,
                note = rule.note.ifBlank { rule.name },
                recurringRuleId = rule.id,
                bankLoanId = bankLoanId,
            )
        )
        dao.updatePending(pending.copy(status = PendingStatus.APPROVED, transactionId = txId))
        bankLoanId?.let { bankLoanRuleSync.sync(it) }
    }

    suspend fun skip(pendingId: Long) {
        dao.getPending(pendingId)?.let { dao.updatePending(it.copy(status = PendingStatus.SKIPPED)) }
    }

    suspend fun postpone(pendingId: Long, to: LocalDate) {
        dao.getPending(pendingId)?.let { dao.updatePending(it.copy(postponedTo = to)) }
    }

    private fun isWithinEnd(date: LocalDate, end: LocalDate?) = end == null || !date.isAfter(end)
}
