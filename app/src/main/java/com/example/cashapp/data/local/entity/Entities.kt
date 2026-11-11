package com.example.cashapp.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.cashapp.domain.model.BankLoanEventType
import com.example.cashapp.domain.model.BankLoanKind
import com.example.cashapp.domain.model.CategoryType
import com.example.cashapp.domain.model.Currency
import com.example.cashapp.domain.model.LoanDirection
import com.example.cashapp.domain.model.LoanOpeningPart
import com.example.cashapp.domain.model.PendingStatus
import com.example.cashapp.domain.model.PrepaymentMode
import com.example.cashapp.domain.model.RateSource
import com.example.cashapp.domain.model.RecurrenceFrequency
import com.example.cashapp.domain.model.TransactionType
import java.time.LocalDate

/*
 * Az adatbázis teljes sémája (terv.md 3. pont). Minden összeg a pénznem legkisebb
 * egységében (Long) van. Séma-változtatásnál: DB verzió emelése + migráció (CashDatabase).
 */

@Entity(tableName = "currencies")
data class CurrencyEntity(
    @PrimaryKey val code: String,
    val name: String,
    val symbol: String,
    val decimals: Int,
    val isActive: Boolean = true,
    val sortOrder: Int = 0,
) {
    fun toDomain() = Currency(code, name, symbol, decimals)
}

@Entity(
    tableName = "accounts",
    foreignKeys = [ForeignKey(CurrencyEntity::class, ["code"], ["currencyCode"])],
    indices = [Index("currencyCode")],
)
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val currencyCode: String,
    val initialBalanceMinor: Long = 0,
    val color: Long,
    val icon: String,
    val sortOrder: Int = 0,
    val isArchived: Boolean = false,
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: CategoryType,
    val icon: String,
    val color: Long,
    val sortOrder: Int = 0,
    val isArchived: Boolean = false,
)

@Entity(
    tableName = "subcategories",
    foreignKeys = [ForeignKey(CategoryEntity::class, ["id"], ["categoryId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("categoryId")],
)
data class SubcategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long,
    val name: String,
    val sortOrder: Int = 0,
    val isArchived: Boolean = false,
)

/**
 * Egy könyvelési tétel.
 * - [amountMinor]/[currencyCode]: a beírt összeg (mindig pozitív, az előjelet a [type] adja).
 * - [accountAmountMinor]: ugyanez a számla pénznemében – az egyenleg ebből számol.
 *   Ha a két pénznem egyezik, a kettő azonos; különben [rateToAccount] a használt árfolyam.
 * - Váltásnál a cél oldal az [ExchangeDetailEntity]-ben van.
 */
@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(AccountEntity::class, ["id"], ["accountId"]),
        ForeignKey(CurrencyEntity::class, ["code"], ["currencyCode"]),
        ForeignKey(CategoryEntity::class, ["id"], ["categoryId"]),
        ForeignKey(SubcategoryEntity::class, ["id"], ["subcategoryId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(RecurringRuleEntity::class, ["id"], ["recurringRuleId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(LoanEntity::class, ["id"], ["loanId"]),
        ForeignKey(BankLoanEntity::class, ["id"], ["bankLoanId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [
        Index("accountId"), Index("currencyCode"), Index("categoryId"),
        Index("subcategoryId"), Index("recurringRuleId"), Index("date"), Index("loanId"), Index("bankLoanId"),
    ],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: TransactionType,
    val date: LocalDate,
    val amountMinor: Long,
    val currencyCode: String,
    val accountId: Long,
    val accountAmountMinor: Long,
    val rateToAccount: Double? = null,
    val categoryId: Long? = null,
    val subcategoryId: Long? = null,
    val note: String = "",
    val recurringRuleId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    /** Magánkölcsön mozgása (kölcsön / törlesztés); bevételként / kiadásként a kimutatásokban is szerepel. */
    val loanId: Long? = null,
    /**
     * Ha nem null: a kölcsön nyitó adatából (összeg / már visszafizetett rész) könyvelt tétel.
     * Ezt a kölcsön szerkesztője kezeli, és a hátralékba nem számít külön (a nyitó mezők már tartalmazzák).
     */
    val loanOpening: LoanOpeningPart? = null,
    /**
     * Kölcsön mozgásánál az összeg a **kölcsön pénznemében** (a hátralék ebből számol). Más pénznemű
     * visszafizetésnél / számlánál eltérhet az [amountMinor]-tól és az [accountAmountMinor]-tól.
     */
    val loanAmountMinor: Long? = null,
    /**
     * Banki hitel részlete vagy előtörlesztése (DB v5). Rendes kiadás tétel; a hitel törlésekor
     * megmarad (valódi kiadás volt), csak a hivatkozás nullázódik.
     */
    val bankLoanId: Long? = null,
)

/**
 * Magánkölcsön (barát, családtag – kamat nélkül).
 * - [openingAmountMinor]: a rögzítés előtt már létrejött kölcsön összege (nem mozgat számlát).
 * - [openingRepaidMinor]: ebből a rögzítés előtt már visszafizetett rész (≤ [openingAmountMinor]).
 * - [closedDate]: ha nem null, a kölcsön le van zárva (a maradék hátralék leírva, nem könyvel semmit).
 * - [color] / [icon]: jelölés, mint a kategóriáknál (ikon kulcs vagy `letter:` + betű). Null → az irány
 *   színe és a név első betűje.
 * - A további mozgások a [TransactionEntity.loanId]-val hivatkozó tételek; a hátralék ezekből számolódik.
 */
@Entity(
    tableName = "loans",
    foreignKeys = [ForeignKey(CurrencyEntity::class, ["code"], ["currencyCode"])],
    indices = [Index("currencyCode")],
)
data class LoanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personName: String,
    val direction: LoanDirection,
    val currencyCode: String,
    val openingAmountMinor: Long = 0,
    val openingRepaidMinor: Long = 0,
    val startDate: LocalDate,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val closedDate: LocalDate? = null,
    val color: Long? = null,
    val icon: String? = null,
)

/**
 * Banki hitel (DB v5), annuitásos törlesztéssel (terv.md 4.12 / 9).
 * - [principalMinor] / [termMonths] / [firstDueDate]: a **nyilvántartott** rész. Futó hitelnél ez a
 *   rögzítéskori hátralék, a hátralévő hónapok és a következő esedékesség.
 * - [originalPrincipalMinor] / [paidMonthsBefore]: futó hitelnél az eredeti összeg és a rögzítés előtt
 *   már befizetett hónapok (csak a progress kijelzéséhez); új hitelnél null / 0.
 * - [monthlyPaymentOverrideMinor]: a bank által ténylegesen terhelt részlet, ha eltér a számolttól.
 * - [currencyCode] mindig a terhelt [accountId] számla pénzneme; a részletek a [categoryId] / [subcategoryId] alá kerülnek.
 * - [recurringRuleId]: a részletekhez generált havi ismétlődő szabály (a szabály törlésekor null).
 * - A befizetett részletek a [TransactionEntity.bankLoanId]-val hivatkozó tételek; az előtörlesztések
 *   és kamatváltozások a [BankLoanEventEntity] sorai.
 */
@Entity(
    tableName = "bank_loans",
    foreignKeys = [
        ForeignKey(CurrencyEntity::class, ["code"], ["currencyCode"]),
        ForeignKey(AccountEntity::class, ["id"], ["accountId"]),
        ForeignKey(CategoryEntity::class, ["id"], ["categoryId"]),
        ForeignKey(SubcategoryEntity::class, ["id"], ["subcategoryId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(RecurringRuleEntity::class, ["id"], ["recurringRuleId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("currencyCode"), Index("accountId"), Index("categoryId"), Index("subcategoryId"), Index("recurringRuleId")],
)
data class BankLoanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val bankName: String = "",
    val kind: BankLoanKind = BankLoanKind.OTHER,
    val currencyCode: String,
    val principalMinor: Long,
    val thmPercent: Double,
    val termMonths: Int,
    val firstDueDate: LocalDate,
    val monthlyPaymentOverrideMinor: Long? = null,
    val originalPrincipalMinor: Long? = null,
    val paidMonthsBefore: Int = 0,
    val accountId: Long,
    val categoryId: Long,
    val subcategoryId: Long? = null,
    val recurringRuleId: Long? = null,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val closedDate: LocalDate? = null,
    val color: Long? = null,
    val icon: String? = null,
)

/**
 * Banki hitel eseménye (DB v5): a [afterInstallment]. részlet után érvényes.
 * - [BankLoanEventType.PREPAYMENT]: [amountMinor] előtörlesztés + [penaltyMinor] büntető, [mode] szerinti
 *   folytatással; a könyvelt kiadás tétel a [transactionId].
 * - [BankLoanEventType.RATE_CHANGE]: [newThmPercent] az új éves ráta.
 */
@Entity(
    tableName = "bank_loan_events",
    foreignKeys = [
        ForeignKey(BankLoanEntity::class, ["id"], ["bankLoanId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(TransactionEntity::class, ["id"], ["transactionId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("bankLoanId"), Index("transactionId")],
)
data class BankLoanEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bankLoanId: Long,
    val date: LocalDate,
    val afterInstallment: Int,
    val type: BankLoanEventType,
    val amountMinor: Long? = null,
    val penaltyMinor: Long? = null,
    val mode: PrepaymentMode? = null,
    val newThmPercent: Double? = null,
    val transactionId: Long? = null,
)

/** Váltás cél oldala (1:1 a [TransactionEntity]-vel, ahol type = EXCHANGE). */
@Entity(
    tableName = "exchange_details",
    foreignKeys = [
        ForeignKey(TransactionEntity::class, ["id"], ["transactionId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(AccountEntity::class, ["id"], ["targetAccountId"]),
        ForeignKey(CurrencyEntity::class, ["code"], ["targetCurrencyCode"]),
    ],
    indices = [Index("targetAccountId"), Index("targetCurrencyCode")],
)
data class ExchangeDetailEntity(
    @PrimaryKey val transactionId: Long,
    val targetAccountId: Long,
    val targetAmountMinor: Long,
    val targetCurrencyCode: String,
    /** 1 forrás egység = [rate] cél egység (a díj nélküli forrás összegre vonatkozik). */
    val rate: Double,
    val rateSource: RateSource,
    /**
     * Váltási díj a forrás pénznemében. Benne van a forrás tétel összegében
     * (TransactionEntity.amountMinor = váltott összeg + díj), itt csak azért tároljuk külön,
     * hogy szerkesztéskor és megjelenítéskor szétválasztható legyen. (DB v2 óta.)
     */
    @ColumnInfo(defaultValue = "0")
    val feeMinor: Long = 0,
)

/**
 * Árfolyam-cache (EUR alapú, a lekért értékek).
 */
@Entity(tableName = "exchange_rates", primaryKeys = ["baseCode", "quoteCode"])
data class ExchangeRateEntity(
    val baseCode: String,
    val quoteCode: String,
    val apiRate: Double? = null,
    val manualRate: Double? = null,
    val fetchedAt: Long? = null,
) {
    /** Mindig a lekért (aktuális) árfolyam; a [manualRate] oszlop csak a régi adatok miatt maradt meg. */
    val effectiveRate: Double? get() = apiRate
}

@Entity(
    tableName = "recurring_rules",
    foreignKeys = [
        ForeignKey(AccountEntity::class, ["id"], ["accountId"]),
        ForeignKey(CurrencyEntity::class, ["code"], ["currencyCode"]),
        ForeignKey(CategoryEntity::class, ["id"], ["categoryId"]),
        ForeignKey(SubcategoryEntity::class, ["id"], ["subcategoryId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("accountId"), Index("currencyCode"), Index("categoryId"), Index("subcategoryId")],
)
data class RecurringRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: TransactionType,
    val amountMinor: Long,
    val currencyCode: String,
    val accountId: Long,
    val categoryId: Long,
    val subcategoryId: Long? = null,
    val note: String = "",
    val frequency: RecurrenceFrequency,
    val interval: Int = 1,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val nextDueDate: LocalDate,
    val isActive: Boolean = true,
)

@Entity(
    tableName = "pending_recurring",
    foreignKeys = [
        ForeignKey(RecurringRuleEntity::class, ["id"], ["ruleId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(TransactionEntity::class, ["id"], ["transactionId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [
        Index("ruleId"), Index("transactionId"), Index("status"),
        // Egy szabály egy esedékességéhez legfeljebb egy tétel (DB v3 óta) – kétszeri generálás se duplikáljon.
        Index("ruleId", "dueDate", unique = true),
    ],
)
data class PendingRecurringEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ruleId: Long,
    val dueDate: LocalDate,
    val status: PendingStatus = PendingStatus.PENDING,
    /** Elhalasztásnál az új dátum; a listában ez számít. */
    val postponedTo: LocalDate? = null,
    val transactionId: Long? = null,
)

/** Kategóriánkénti havi költségkeret (kategóriánként legfeljebb egy). */
@Entity(
    tableName = "budgets",
    foreignKeys = [
        ForeignKey(CategoryEntity::class, ["id"], ["categoryId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(CurrencyEntity::class, ["code"], ["currencyCode"]),
    ],
    indices = [Index("categoryId", unique = true), Index("currencyCode")],
)
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long,
    val limitMinor: Long,
    val currencyCode: String,
    val warnThresholdPercent: Int = 80,
    val notify: Boolean = true,
    val isActive: Boolean = true,
)
