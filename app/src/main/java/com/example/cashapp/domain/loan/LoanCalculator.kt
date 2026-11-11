package com.example.cashapp.domain.loan

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Hitelkalkulátor: annuitásos (egyenlő részletű) törlesztés.
 *
 * A havi kamatláb az éves ráta tizenketted része (`THM / 12`), ahogy a banki
 * törlesztőrészlet-kalkulátorok is számolnak: `A = K · i · (1+i)^n / ((1+i)^n − 1)`.
 * Pl. 1 000 000 Ft, 13,79%, 36 hónap → 34 075,72 → 34 076 Ft.
 * Minden összeg a pénznem legkisebb egységében (Long); a részlet a legkisebb egységre HALF_UP kerekítve.
 */
object LoanCalculator {

    data class Result(
        /** Havi törlesztőrészlet. */
        val monthlyPaymentMinor: Long,
        /** Teljes visszafizetendő összeg (részlet × hónapok). */
        val totalRepaymentMinor: Long,
        /** Teljes hitelköltség (visszafizetés − felvett összeg). */
        val totalCostMinor: Long,
    )

    /** Havi kamatláb az éves rátából: THM / 12 (pl. 13,79 → 0,0114917). */
    fun monthlyRate(thmPercent: Double): BigDecimal =
        BigDecimal.valueOf(thmPercent).divide(BigDecimal.valueOf(1200), MathContext.DECIMAL64)

    /**
     * @param principalMinor igényelt hitelösszeg (> 0)
     * @param thmPercent THM százalékban (≥ 0), pl. 15.0
     * @param months futamidő hónapban (≥ 1)
     */
    fun calculate(principalMinor: Long, thmPercent: Double, months: Int): Result {
        require(principalMinor > 0) { "A hitelösszeg legyen pozitív" }
        require(thmPercent >= 0) { "A THM nem lehet negatív" }
        require(months >= 1) { "A futamidő legalább 1 hónap" }
        val mc = MathContext.DECIMAL64
        val p = BigDecimal.valueOf(principalMinor)
        val n = BigDecimal.valueOf(months.toLong())
        val payment = if (thmPercent == 0.0) {
            p.divide(n, mc)
        } else {
            // A = P · r / (1 − (1 + r)^−n)
            val r = monthlyRate(thmPercent)
            val factor = BigDecimal.ONE.add(r).pow(months, mc)
            p.multiply(r).multiply(factor).divide(factor.subtract(BigDecimal.ONE), mc)
        }
        val monthly = payment.setScale(0, RoundingMode.HALF_UP).longValueExact()
        val total = monthly * months
        return Result(monthly, total, total - principalMinor)
    }

    // ------------------------------------------------------------------ előtörlesztés

    /** Egy változat az előtörlesztés után (csökkenő részlet vagy rövidebb futamidő). */
    data class PrepaymentOption(
        /** Az előtörlesztés utáni havi részlet. */
        val monthlyPaymentMinor: Long,
        /** Az előtörlesztés után még hátralévő hónapok száma. */
        val remainingMonths: Int,
        /** Az utolsó részlet (rövidebb futamidőnél kisebb lehet; csökkenő részletnél = havi részlet). */
        val lastPaymentMinor: Long,
        /** Teljes visszafizetés: eddigi részletek + előtörlesztés + büntető + hátralévő részletek. */
        val totalRepaymentMinor: Long,
        /** Megtakarítás az előtörlesztés nélküli teljes visszafizetéshez képest (lehet negatív is). */
        val savingMinor: Long,
        /**
         * Ugyanez mai pénzben (vásárlóerő): az eredeti és az új fizetések mai értékének különbsége.
         * Infláció nélkül egyenlő a [savingMinor]-ral.
         */
        val realSavingMinor: Long = savingMinor,
    )

    data class PrepaymentResult(
        /** Hátralék a megadott hónap részletének befizetése után, az előtörlesztés előtt. */
        val balanceBeforeMinor: Long,
        /** Ténylegesen előtörlesztett összeg (legfeljebb a hátralék). */
        val prepaidMinor: Long,
        /** Büntető: (előtörlesztés − küszöb) × büntető%, ha az előtörlesztés a küszöb fölött van. */
        val penaltyMinor: Long,
        /** Igaz, ha az előtörlesztés a teljes hátralékot kifizeti. */
        val paidOff: Boolean,
        val lowerPayment: PrepaymentOption,
        val shorterTerm: PrepaymentOption,
    )

    /**
     * Előtörlesztés a [month]. havi részlet befizetése után (1 ≤ month < months).
     * A havi részlet és a hátralék egész egységre (forintra) kerekítve; a büntető csak a [thresholdMinor]
     * feletti részre számít. Két változat: azonos futamidő csökkenő részlettel, illetve azonos részlet
     * rövidebb futamidővel (az utolsó részlet kisebb lehet).
     * [inflationPercent] > 0 esetén a megtakarítást mai pénzben is kiszámolja (havi infláció kamatos kamattal).
     */
    fun prepayment(
        principalMinor: Long,
        thmPercent: Double,
        months: Int,
        prepaymentMinor: Long,
        month: Int,
        penaltyPercent: Double,
        thresholdMinor: Long,
        inflationPercent: Double = 0.0,
    ): PrepaymentResult {
        require(prepaymentMinor > 0) { "Az előtörlesztés legyen pozitív" }
        require(month in 1 until months) { "Az előtörlesztés hónapja a futamidőn belül legyen" }
        require(penaltyPercent >= 0 && thresholdMinor >= 0) { "A büntető és a küszöb nem lehet negatív" }
        val mc = MathContext.DECIMAL64
        val base = calculate(principalMinor, thmPercent, months)
        val a = base.monthlyPaymentMinor
        val growth = BigDecimal.ONE.add(monthlyRate(thmPercent))

        // Hátralék a month. részlet után (a kerekített részlettel), egész egységre kerekítve.
        var balance = BigDecimal.valueOf(principalMinor)
        repeat(month) { balance = balance.multiply(growth, mc).subtract(BigDecimal.valueOf(a)) }
        val balanceBefore = balance.setScale(0, RoundingMode.HALF_UP).longValueExact().coerceAtLeast(0)

        val prepaid = minOf(prepaymentMinor, balanceBefore)
        val penalty = BigDecimal.valueOf((prepaid - thresholdMinor).coerceAtLeast(0))
            .multiply(BigDecimal.valueOf(penaltyPercent), mc).divide(BigDecimal.valueOf(100), mc)
            .setScale(0, RoundingMode.HALF_UP).longValueExact()
        val rest = balanceBefore - prepaid
        val paidSoFar = a * month + prepaid + penalty
        val remainingMonths = months - month

        // Mai érték: a t. hónap fizetése d^t-vel szorozva, d = (1 + infláció)^(−1/12).
        val d = BigDecimal.valueOf(Math.pow(1 + inflationPercent.coerceAtLeast(0.0) / 100, -1.0 / 12))
        fun pv(flows: List<Pair<Int, Long>>): BigDecimal =
            flows.fold(BigDecimal.ZERO) { acc, (t, v) -> acc.add(BigDecimal.valueOf(v).multiply(d.pow(t, mc), mc), mc) }
        val baseFlows = (1..months).map { it to a }
        val baseValue = pv(baseFlows)
        // Közös rész: az első month részlet + az előtörlesztés és a büntető a month. hónapban.
        val commonFlows = (1..month).map { it to a } + (month to prepaid + penalty)

        fun option(monthly: Long, count: Int, last: Long, total: Long, flows: List<Pair<Int, Long>>) = PrepaymentOption(
            monthlyPaymentMinor = monthly,
            remainingMonths = count,
            lastPaymentMinor = last,
            totalRepaymentMinor = total,
            savingMinor = base.totalRepaymentMinor - total,
            realSavingMinor = baseValue.subtract(pv(flows), mc).setScale(0, RoundingMode.HALF_UP).longValueExact(),
        )

        if (rest == 0L) {
            val paidOffOption = option(0, 0, 0, paidSoFar, commonFlows)
            return PrepaymentResult(balanceBefore, prepaid, penalty, true, paidOffOption, paidOffOption)
        }

        // 1) Azonos futamidő, csökkenő részlet.
        val lower = calculate(rest, thmPercent, remainingMonths).monthlyPaymentMinor
        val lowerFlows = commonFlows + (1..remainingMonths).map { month + it to lower }
        val lowerOption = option(lower, remainingMonths, lower, paidSoFar + lower * remainingMonths, lowerFlows)

        // 2) Azonos részlet, rövidebb futamidő: addig fizet, amíg a hátralék elfogy; az utolsó részlet kisebb lehet.
        var b = BigDecimal.valueOf(rest)
        var count = 0
        var total = paidSoFar
        var last = 0L
        val aDec = BigDecimal.valueOf(a)
        val shorterFlows = commonFlows.toMutableList()
        while (b.signum() > 0) {
            b = b.multiply(growth, mc)
            count++
            if (b <= aDec) {
                last = b.setScale(0, RoundingMode.HALF_UP).longValueExact()
                total += last
                shorterFlows += (month + count) to last
                b = BigDecimal.ZERO
            } else {
                b = b.subtract(aDec)
                total += a
                shorterFlows += (month + count) to a
            }
        }
        val shorterOption = option(a, count, last, total, shorterFlows)
        return PrepaymentResult(balanceBefore, prepaid, penalty, false, lowerOption, shorterOption)
    }

    // ------------------------------------------------------------------ infláció (vásárlóerő)

    data class RealValue(
        /** A részletek összege mai pénzben (mindegyik visszaszámolva a mai értékére). */
        val totalRepaymentMinor: Long,
        /** Valós hitelköltség: mai értékű visszafizetés − felvett összeg (negatív: az infláció „lenyeli” a kamatot). */
        val totalCostMinor: Long,
        /** Az utolsó részlet mai értéken. */
        val lastPaymentMinor: Long,
    )

    /**
     * Vásárlóerő: a [monthlyPaymentMinor] részletek mai értéke [inflationPercent] éves infláció mellett.
     * A havi infláció kamatos kamattal: `(1 + infláció)^(1/12) − 1` (az éves infláció éves árváltozás).
     * A t. hónap részletének mai értéke: `A / (1 + havi infláció)^t`.
     */
    fun realValue(principalMinor: Long, monthlyPaymentMinor: Long, months: Int, inflationPercent: Double): RealValue {
        require(months >= 1) { "A futamidő legalább 1 hónap" }
        require(inflationPercent >= 0) { "Az infláció nem lehet negatív" }
        val mc = MathContext.DECIMAL64
        // havi diszkontfaktor: d = (1 + infláció)^(−1/12)
        val d = BigDecimal.valueOf(Math.pow(1 + inflationPercent / 100, -1.0 / 12))
        val a = BigDecimal.valueOf(monthlyPaymentMinor)
        val dn = d.pow(months, mc)
        // Σ_{t=1..n} d^t = d · (1 − d^n) / (1 − d); infláció nélkül egyszerűen n
        val sum = if (inflationPercent == 0.0) BigDecimal.valueOf(months.toLong())
            else d.multiply(BigDecimal.ONE.subtract(dn), mc).divide(BigDecimal.ONE.subtract(d), mc)
        val total = a.multiply(sum, mc).setScale(0, RoundingMode.HALF_UP).longValueExact()
        val last = a.multiply(dn, mc).setScale(0, RoundingMode.HALF_UP).longValueExact()
        return RealValue(total, total - principalMinor, last)
    }
}
