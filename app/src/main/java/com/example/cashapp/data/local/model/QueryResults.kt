package com.example.cashapp.data.local.model

import androidx.room.Embedded
import com.example.cashapp.data.local.entity.AccountEntity
import com.example.cashapp.data.local.entity.BankLoanEntity
import com.example.cashapp.data.local.entity.LoanEntity
import com.example.cashapp.data.local.entity.RecurringRuleEntity
import com.example.cashapp.domain.model.LoanDirection
import com.example.cashapp.domain.model.LoanOpeningPart
import com.example.cashapp.domain.model.TransactionType
import java.time.LocalDate

/** Számla + számolt egyenleg (a számla saját pénznemében). */
data class AccountWithBalance(
    @Embedded val account: AccountEntity,
    val balanceMinor: Long,
)

/** Összeg típusonként és pénznemenként egy időszakra. */
data class TypeCurrencyTotal(
    val type: TransactionType,
    val currencyCode: String,
    val totalMinor: Long,
)

/** Csoportösszeg (kategória / alkategória szerint) egy pénznemben. */
data class GroupTotal(val id: Long?, val currencyCode: String, val amountMinor: Long)

/** Egy tétel összege a havi kimutatáshoz. */
data class AmountRow(val date: LocalDate, val type: TransactionType, val amountMinor: Long, val currencyCode: String)

/** Jóváhagyásra váró ismétlődő tétel a szabály adataival. */
data class PendingItem(
    val id: Long,
    val ruleId: Long,
    val dueDate: LocalDate,
    val postponedTo: LocalDate?,
    val ruleName: String,
    val type: TransactionType,
    val amountMinor: Long,
    val currencyCode: String,
    val accountId: Long,
    val accountName: String,
    val categoryName: String,
    val categoryColor: Long,
    val categoryIcon: String,
    val subcategoryName: String?,
) {
    /** Az elhalasztás után ez számít esedékességnek. */
    val effectiveDate: LocalDate get() = postponedTo ?: dueDate
}

/** Ismétlődő szabály a listához. */
data class RuleItem(
    @Embedded val rule: RecurringRuleEntity,
    val accountName: String,
    val categoryName: String,
    val categoryColor: Long,
    val categoryIcon: String,
)

/** Összeg kategóriánként és pénznemenként. */
data class CategoryCurrencyTotal(
    val categoryId: Long,
    val currencyCode: String,
    val totalMinor: Long,
)

/** Tranzakció listaelem a kapcsolódó nevekkel együtt. */
data class TransactionListItem(
    val id: Long,
    val type: TransactionType,
    val date: LocalDate,
    val amountMinor: Long,
    val currencyCode: String,
    val note: String,
    val accountId: Long,
    val accountName: String,
    /** Az összeg a forrás számla pénznemében (összesítésekhez). */
    val accountAmountMinor: Long,
    val accountCurrencyCode: String,
    val categoryName: String?,
    val categoryColor: Long?,
    val categoryIcon: String?,
    val subcategoryName: String?,
    val targetAccountId: Long?,
    val targetAmountMinor: Long?,
    val targetCurrencyCode: String?,
    val exchangeRate: Double?,
    /** Magánkölcsön mozgásánál a kölcsön adatai (különben null). */
    val loanId: Long? = null,
    val loanName: String? = null,
    val loanDirection: LoanDirection? = null,
    /** Nem null: a kölcsön nyitó adatából könyvelt tétel. */
    val loanOpening: LoanOpeningPart? = null,
    /** A kölcsön választott jelölése (null: alapértelmezett). */
    val loanColor: Long? = null,
    val loanIcon: String? = null,
)

/** Magánkölcsön + számolt hátralék (a kölcsön pénznemében; 0 = rendezve). */
data class LoanWithBalance(
    @Embedded val loan: LoanEntity,
    val outstandingMinor: Long,
    val lastMovementDate: LocalDate?,
)

/**
 * Banki hitel + a befizetett részletek száma (a követés előtt befizetettekkel együtt, előtörlesztések nélkül)
 * és az utolsó hozzá könyvelt tétel dátuma. A hátralék a törlesztési tervből számol.
 */
data class BankLoanWithProgress(
    @Embedded val loan: BankLoanEntity,
    val paidInstallments: Int,
    val lastPaymentDate: LocalDate?,
)
