package com.example.cashapp.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.cashapp.domain.model.CategoryType
import com.example.cashapp.domain.model.Currency
import com.example.cashapp.domain.model.PendingStatus
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
    ],
    indices = [
        Index("accountId"), Index("currencyCode"), Index("categoryId"),
        Index("subcategoryId"), Index("recurringRuleId"), Index("date"),
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
    indices = [Index("ruleId"), Index("transactionId"), Index("status")],
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
