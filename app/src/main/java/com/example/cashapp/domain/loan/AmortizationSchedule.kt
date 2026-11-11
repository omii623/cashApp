package com.example.cashapp.domain.loan

import com.example.cashapp.domain.model.PrepaymentMode
import com.example.cashapp.domain.model.RecurrenceFrequency
import com.example.cashapp.domain.recurring.Recurrence
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.LocalDate

/**
 * Banki hitel törlesztési terve (terv.md 9): annuitás, a [LoanCalculator]-ral azonos számolással.
 *
 * - Havi kamat = THM / 12; a hátralék pontosan (BigDecimal) halad: `B ← B · (1 + r) − A`.
 * - Soronként a hátralék egész egységre kerekítve; a tőkerész = előző − új (kerekített) hátralék,
 *   a kamatrész = részlet − tőkerész. Így a tőkerészek összege pontosan a hitelösszeg.
 * - Az utolsó részlet a maradék hátralék kamattal (`B · (1 + r)`, kerekítve) – a hátralék pontosan 0-ra fut ki.
 *   Ha a részlet nagyobb a szükségesnél (pl. felülírt részlet), a hitel korábban fut ki; ha kisebb,
 *   az utolsó részlet nagyobb lesz.
 * - Az esemény a megadott részlet **után** érvényes (0 = az első részlet előtt). Előtörlesztés után a hátralék
 *   egész egységre kerekítve indul újra (mint a [LoanCalculator.prepayment]-nél):
 *   [PrepaymentMode.LOWER_PAYMENT] azonos hátralévő hónapokkal új részlet, [PrepaymentMode.SHORTER_TERM] azonos
 *   részlettel addig, amíg a hátralék elfogy. Kamatváltozás: új részlet a hátralékra a hátralévő hónapokra.
 * - A büntető nem csökkenti a hátralékot (külön kiadás), ezért itt nem szerepel.
 * - Korábban felvett hitelnél [Params.openingBalanceMinor]: a bank szerinti hátralék az első
 *   [Params.openingPaid] részlet után. Ha megadják, a terv onnan ezzel a hátralékkal folytatódik (a részlet
 *   a hátralévő hónapokra újraszámolva, hacsak nincs felülírt részlet) – így a korábbi, nem rögzített
 *   előtörlesztések / kamatváltozások is kiegyenlítődnek.
 */
object AmortizationSchedule {

    data class Params(
        val principalMinor: Long,
        val thmPercent: Double,
        val months: Int,
        val firstDueDate: LocalDate,
        /** A bank által ténylegesen terhelt részlet; null → a számolt annuitás. Csak az első eseményig érvényes. */
        val paymentOverrideMinor: Long? = null,
        /** A követés kezdete előtt már befizetett (könyveletlen) részletek száma. */
        val openingPaid: Int = 0,
        /** A bank szerinti hátralék [openingPaid] részlet után; null → a számolt érték. */
        val openingBalanceMinor: Long? = null,
    )

    sealed interface Event {
        /** Hányadik részlet után (0 = az első részlet előtt). */
        val afterInstallment: Int

        data class Prepayment(override val afterInstallment: Int, val amountMinor: Long, val mode: PrepaymentMode) : Event

        data class RateChange(override val afterInstallment: Int, val newThmPercent: Double) : Event
    }

    data class Installment(
        /** Sorszám a nyilvántartott részben (1-től). */
        val index: Int,
        val dueDate: LocalDate,
        /** Hátralék a részlet előtt (az ezt megelőző események után). */
        val balanceBeforeMinor: Long,
        val paymentMinor: Long,
        val interestMinor: Long,
        val principalMinor: Long,
        val balanceAfterMinor: Long,
        /** Az erre a részletre érvényes THM. */
        val thmPercent: Double,
        /** A részlet előtt (az előző részlet után) ténylegesen előtörlesztett összeg; 0, ha nem volt. */
        val prepaidBeforeMinor: Long = 0,
        /** Nem null: e részlet előtt a bank szerinti hátralékra igazodott a terv (korábban felvett hitel). */
        val bankBalanceBeforeMinor: Long? = null,
    )

    data class Schedule(val installments: List<Installment>, val prepaidMinor: Long) {
        val totalPaymentMinor: Long get() = installments.sumOf { it.paymentMinor }
        val totalInterestMinor: Long get() = installments.sumOf { it.interestMinor }
        val lastDueDate: LocalDate? get() = installments.lastOrNull()?.dueDate

        /** Hátralék [paid] befizetett részlet után (az addig esedékes előtörlesztésekkel együtt); 0, ha kifutott. */
        fun outstandingAfter(paid: Int): Long = installments.getOrNull(paid.coerceAtLeast(0))?.balanceBeforeMinor ?: 0

        /** A következő (még be nem fizetett) részlet; null, ha minden részlet befizetve. */
        fun nextAfter(paid: Int): Installment? = installments.getOrNull(paid.coerceAtLeast(0))

        /** Eddig kamatra ment összeg [paid] befizetett részlet után. */
        fun interestPaidAfter(paid: Int): Long = installments.take(paid.coerceAtLeast(0)).sumOf { it.interestMinor }
    }

    /** Biztonsági határ (pl. túl kicsi felülírt részletnél ne fusson végtelen ciklus). */
    private const val MAX_INSTALLMENTS = 1200

    fun dueDate(firstDueDate: LocalDate, index: Int): LocalDate =
        Recurrence.occurrence(firstDueDate, RecurrenceFrequency.MONTHLY, 1, (index - 1).toLong())

    /** Hány részlet esedékes [date] előtt (az aznapi még nem): korábban felvett hitelnél a már befizetettek. */
    fun installmentsDueBefore(firstDueDate: LocalDate, months: Int, date: LocalDate): Int =
        (1..months).count { dueDate(firstDueDate, it).isBefore(date) }

    fun build(params: Params, events: List<Event> = emptyList()): Schedule {
        require(params.principalMinor > 0) { "A hitelösszeg legyen pozitív" }
        require(params.thmPercent >= 0) { "A THM nem lehet negatív" }
        require(params.months >= 1) { "A futamidő legalább 1 hónap" }
        require(params.paymentOverrideMinor == null || params.paymentOverrideMinor > 0) { "A részlet legyen pozitív" }
        require(params.openingPaid in 0 until params.months) { "A már befizetett részletek száma kevesebb legyen a futamidőnél" }
        require(params.openingBalanceMinor == null || params.openingBalanceMinor > 0) { "A hátralék legyen pozitív" }
        val mc = MathContext.DECIMAL64
        val byInstallment = events.groupBy { it.afterInstallment }

        var thm = params.thmPercent
        var growth = BigDecimal.ONE.add(LoanCalculator.monthlyRate(thm))
        var payment = params.paymentOverrideMinor
            ?: LoanCalculator.calculate(params.principalMinor, thm, params.months).monthlyPaymentMinor
        /** Hátralévő hónapok a jelenlegi terv szerint (null: rövidebb futamidő – amíg a hátralék el nem fogy). */
        var remaining: Int? = params.months
        var balance = BigDecimal.valueOf(params.principalMinor)
        var prepaidTotal = 0L
        val rows = mutableListOf<Installment>()

        var index = 0
        while (balance.signum() > 0 && index < MAX_INSTALLMENTS) {
            // Korábban felvett hitel: a bank szerinti hátralékkal folytatódik a hátralévő hónapokra.
            val bankBalanceHere = params.openingBalanceMinor.takeIf { index == params.openingPaid && index > 0 }
            if (bankBalanceHere != null) {
                balance = BigDecimal.valueOf(bankBalanceHere)
                val months = remaining ?: (params.months - index)
                payment = params.paymentOverrideMinor
                    ?: LoanCalculator.calculate(bankBalanceHere, thm, months).monthlyPaymentMinor
                remaining = months
            }
            // Az előző (index.) részlet után érvényes események, a megadás sorrendjében.
            var prepaidHere = 0L
            for (event in byInstallment[index].orEmpty()) {
                val rounded = balance.setScale(0, RoundingMode.HALF_UP).longValueExact()
                when (event) {
                    is Event.Prepayment -> {
                        require(event.amountMinor > 0) { "Az előtörlesztés legyen pozitív" }
                        val prepaid = minOf(event.amountMinor, rounded)
                        prepaidHere += prepaid
                        val rest = rounded - prepaid
                        balance = BigDecimal.valueOf(rest)
                        if (rest == 0L) break
                        when (event.mode) {
                            PrepaymentMode.LOWER_PAYMENT -> {
                                val months = remaining ?: monthsToPayOff(rest, growth, payment, mc)
                                payment = LoanCalculator.calculate(rest, thm, months).monthlyPaymentMinor
                                remaining = months
                            }
                            PrepaymentMode.SHORTER_TERM -> remaining = null
                        }
                    }
                    is Event.RateChange -> {
                        require(event.newThmPercent >= 0) { "A THM nem lehet negatív" }
                        val months = remaining ?: monthsToPayOff(rounded, growth, payment, mc)
                        thm = event.newThmPercent
                        growth = BigDecimal.ONE.add(LoanCalculator.monthlyRate(thm))
                        balance = BigDecimal.valueOf(rounded)
                        payment = LoanCalculator.calculate(rounded, thm, months).monthlyPaymentMinor
                        remaining = months
                    }
                }
            }
            prepaidTotal += prepaidHere
            if (balance.signum() <= 0) break

            index++
            val before = balance.setScale(0, RoundingMode.HALF_UP).longValueExact()
            val due = balance.multiply(growth, mc)
            val isLast = remaining == 1 || due <= BigDecimal.valueOf(payment)
            val paid: Long
            if (isLast) {
                paid = due.setScale(0, RoundingMode.HALF_UP).longValueExact()
                balance = BigDecimal.ZERO
            } else {
                paid = payment
                balance = due.subtract(BigDecimal.valueOf(payment))
            }
            val after = balance.setScale(0, RoundingMode.HALF_UP).longValueExact()
            val principal = before - after
            rows += Installment(
                index = index,
                dueDate = dueDate(params.firstDueDate, index),
                balanceBeforeMinor = before,
                paymentMinor = paid,
                interestMinor = paid - principal,
                principalMinor = principal,
                balanceAfterMinor = after,
                thmPercent = thm,
                prepaidBeforeMinor = prepaidHere,
                bankBalanceBeforeMinor = bankBalanceHere,
            )
            remaining = remaining?.minus(1)
        }
        return Schedule(rows, prepaidTotal)
    }

    /** Hány hónap alatt fut ki a hátralék a megadott részlettel (rövidebb futamidős terv folytatásához). */
    private fun monthsToPayOff(balanceMinor: Long, growth: BigDecimal, payment: Long, mc: MathContext): Int {
        var b = BigDecimal.valueOf(balanceMinor)
        val a = BigDecimal.valueOf(payment)
        var count = 0
        while (b.signum() > 0 && count < MAX_INSTALLMENTS) {
            b = b.multiply(growth, mc)
            count++
            b = if (b <= a) BigDecimal.ZERO else b.subtract(a)
        }
        return count.coerceAtLeast(1)
    }
}
