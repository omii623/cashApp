package com.example.cashapp.ui.components

import com.example.cashapp.data.local.model.TransactionListItem
import com.example.cashapp.domain.loan.LoanMath
import com.example.cashapp.domain.model.Currency
import com.example.cashapp.domain.model.TransactionType
import com.example.cashapp.domain.money.MoneyFormatter
import java.util.Locale

/** [TransactionListItem] → megformázott [TransactionRowUi]. Minden lista ezt használja. */
object TransactionRowMapper {

    /**
     * @param perspectiveAccountId ha egy számla nézetéből listázunk: a váltás ott
     *   bejövő (+cél összeg) vagy kimenő (−forrás összeg) lesz. `null` → általános nézet.
     */
    fun map(
        item: TransactionListItem,
        currencies: Map<String, Currency>,
        perspectiveAccountId: Long? = null,
    ): TransactionRowUi {
        fun fmt(minor: Long, code: String, plus: Boolean = false) =
            currencies[code]?.let { MoneyFormatter.format(minor, it, plus) } ?: "$minor $code"

        val amountText = with(item) {
            when (type) {
                TransactionType.INCOME -> fmt(amountMinor, currencyCode, plus = true)
                TransactionType.EXPENSE -> fmt(-amountMinor, currencyCode)
                TransactionType.EXCHANGE -> when {
                    targetAmountMinor == null || targetCurrencyCode == null -> fmt(amountMinor, currencyCode)
                    perspectiveAccountId == null -> fmt(targetAmountMinor, targetCurrencyCode)
                    perspectiveAccountId == targetAccountId -> fmt(targetAmountMinor, targetCurrencyCode, plus = true)
                    else -> fmt(-amountMinor, currencyCode)
                }
            }
        }
        return TransactionRowUi(
            id = item.id,
            type = item.type,
            date = item.date,
            categoryName = item.categoryName,
            subcategoryName = item.subcategoryName,
            categoryColor = item.categoryColor ?: item.loanColor,
            categoryIcon = item.categoryIcon ?: item.loanIcon,
            accountName = item.accountName,
            fromCurrency = item.currencyCode,
            toCurrency = item.targetCurrencyCode,
            rateText = item.exchangeRate?.let { rateText(it, item.currencyCode, item.targetCurrencyCode, currencies) },
            amountText = amountText,
            loanId = item.loanId,
            loanName = item.loanName,
            loanIncrease = item.loanDirection?.let { LoanMath.isIncrease(it, item.type) } ?: false,
        )
    }

    /** Mindig az „1 drágább egység = X olcsóbb” irányt mutatja, pl. 1 € = 399 Ft. */
    fun rateText(rate: Double, from: String, to: String?, currencies: Map<String, Currency>): String? {
        if (to == null || rate <= 0) return null
        val fromSym = currencies[from]?.symbol ?: from
        val toSym = currencies[to]?.symbol ?: to
        return if (rate >= 1) "1 $fromSym = ${formatRate(rate)} $toSym"
        else "1 $toSym = ${formatRate(1 / rate)} $fromSym"
    }

    private fun formatRate(value: Double): String =
        "%.2f".format(Locale.ROOT, value).trimEnd('0').trimEnd('.').replace('.', ',')
}
