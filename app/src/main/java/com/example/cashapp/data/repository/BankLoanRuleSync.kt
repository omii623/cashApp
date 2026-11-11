package com.example.cashapp.data.repository

import com.example.cashapp.data.local.CashDatabase
import com.example.cashapp.data.local.entity.BankLoanEntity
import com.example.cashapp.data.local.entity.BankLoanEventEntity
import com.example.cashapp.data.local.entity.RecurringRuleEntity
import com.example.cashapp.domain.loan.AmortizationSchedule
import com.example.cashapp.domain.model.BankLoanEventType
import com.example.cashapp.domain.model.PrepaymentMode
import com.example.cashapp.domain.model.RecurrenceFrequency
import com.example.cashapp.domain.model.TransactionType

/** A hitel törlesztési terve a tárolt adatokból és eseményekből. */
fun BankLoanEntity.schedule(events: List<BankLoanEventEntity>): AmortizationSchedule.Schedule =
    AmortizationSchedule.build(
        AmortizationSchedule.Params(
            principalMinor = principalMinor,
            thmPercent = thmPercent,
            months = termMonths,
            firstDueDate = firstDueDate,
            paymentOverrideMinor = monthlyPaymentOverrideMinor,
        ),
        events.mapNotNull { it.toDomain() },
    )

private fun BankLoanEventEntity.toDomain(): AmortizationSchedule.Event? = when (type) {
    BankLoanEventType.PREPAYMENT -> amountMinor?.takeIf { it > 0 }?.let {
        AmortizationSchedule.Event.Prepayment(afterInstallment, it, mode ?: PrepaymentMode.SHORTER_TERM)
    }
    BankLoanEventType.RATE_CHANGE -> newThmPercent?.let { AmortizationSchedule.Event.RateChange(afterInstallment, it) }
}

/**
 * A banki hitel havi ismétlődő szabályának összehangolása a törlesztési tervvel (terv.md 9).
 *
 * Nem a [RecurringRepository.saveRule]-t használja: az ütemezés változásakor az összes el nem bírált tételt
 * törölné és mától folytatná, a hitelnél viszont a még be nem fizetett (akár múltbeli) részleteknek meg kell
 * maradniuk. Itt csak a terv vége utáni függő tételek törlődnek.
 *
 * - kezdés = első esedékesség (így a szabály esedékességei = a részletek esedékességei),
 * - záró dátum = az utolsó részlet, összeg = a következő be nem fizetett részlet,
 * - lezárt vagy kifizetett hitelnél a szabály kikapcsol, függő tételei törlődnek.
 *
 * A [RecurringRepository] jóváhagyáskor is meghívja (pl. az utolsó részlet összege eltérhet).
 */
class BankLoanRuleSync(db: CashDatabase) {

    private val bankLoanDao = db.bankLoanDao()
    private val recurringDao = db.recurringDao()

    /** Hívd tranzakción belül. */
    suspend fun sync(loanId: Long) {
        val loan = bankLoanDao.getById(loanId) ?: return
        val schedule = loan.schedule(bankLoanDao.getEvents(loanId))
        val next = schedule.nextAfter(bankLoanDao.paidInstallments(loanId))
        val end = schedule.lastDueDate ?: loan.firstDueDate
        val active = loan.closedDate == null && next != null
        val existing = loan.recurringRuleId?.let { recurringDao.getRule(it) }

        if (existing == null) {
            if (next == null || !active) return
            val ruleId = recurringDao.insertRule(
                RecurringRuleEntity(
                    name = loan.name,
                    type = TransactionType.EXPENSE,
                    amountMinor = next.paymentMinor,
                    currencyCode = loan.currencyCode,
                    accountId = loan.accountId,
                    categoryId = loan.categoryId,
                    subcategoryId = loan.subcategoryId,
                    frequency = RecurrenceFrequency.MONTHLY,
                    interval = 1,
                    startDate = loan.firstDueDate,
                    endDate = end,
                    nextDueDate = next.dueDate,
                )
            )
            bankLoanDao.update(loan.copy(recurringRuleId = ruleId))
            return
        }

        var nextDueDate = existing.nextDueDate
        when {
            !active -> recurringDao.deletePendingOfRule(existing.id)
            // Más lett az első esedékesség → a régi esedékességek érvénytelenek.
            existing.startDate != loan.firstDueDate || existing.frequency != RecurrenceFrequency.MONTHLY || existing.interval != 1 -> {
                recurringDao.deletePendingOfRule(existing.id)
                nextDueDate = next!!.dueDate
            }
            else -> {
                recurringDao.deletePendingOfRuleAfter(existing.id, end)
                // Legkésőbb a következő be nem fizetett részlettől folytatódik (pl. újranyitás után a lezáráskor
                // törölt tételek visszajönnek). A már meglévő (függő / kihagyott / jóváhagyott) esedékességeket
                // az egyedi (ruleId, dueDate) index miatt a generátor nem hozza létre újra.
                nextDueDate = minOf(nextDueDate, next!!.dueDate)
            }
        }
        recurringDao.updateRule(
            existing.copy(
                name = loan.name,
                type = TransactionType.EXPENSE,
                amountMinor = next?.paymentMinor ?: existing.amountMinor,
                currencyCode = loan.currencyCode,
                accountId = loan.accountId,
                categoryId = loan.categoryId,
                subcategoryId = loan.subcategoryId,
                frequency = RecurrenceFrequency.MONTHLY,
                interval = 1,
                startDate = loan.firstDueDate,
                endDate = end,
                nextDueDate = nextDueDate,
                isActive = active && !nextDueDate.isAfter(end),
            )
        )
    }
}
