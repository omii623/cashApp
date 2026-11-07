package com.example.cashapp.ui.transaction

import com.example.cashapp.data.local.entity.TransactionEntity
import com.example.cashapp.domain.model.Currency
import com.example.cashapp.domain.model.TransactionType
import java.time.LocalDate

data class TransactionFormUiState(
    val type: TransactionType,
    val isLoading: Boolean = true,

    val amountText: String = "",
    val currency: Currency? = null,
    val currencies: List<Currency> = emptyList(),

    val account: AccountOption? = null,
    val accounts: List<AccountOption> = emptyList(),

    val date: LocalDate = LocalDate.now(),

    val categories: List<CategoryOption> = emptyList(),
    val selectedCategoryId: Long? = null,
    val subcategories: List<SubcategoryOption> = emptyList(),
    val selectedSubcategoryId: Long? = null,

    val note: String = "",

    /** Ha a tétel pénzneme ≠ számla pénzneme, árfolyam kell. */
    val rate: RateField? = null,

    val amountError: Boolean = false,
    val categoryError: Boolean = false,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    /** A mentés hibára futott (a képernyő nyitva marad, hibaüzenettel). */
    val saveFailed: Boolean = false,
    /** Meglévő tétel szerkesztése (más cím + Törlés gomb). */
    val isEditing: Boolean = false,

    /** A mentendő tétel, ha minden mező érvényes; különben `null`. */
    val draft: TransactionEntity? = null,
)

data class AccountOption(val id: Long, val name: String, val currencyCode: String)

data class CategoryOption(val id: Long, val name: String, val color: Long, val icon: String)

data class SubcategoryOption(val id: Long, val name: String)

/**
 * Árfolyam mező. A felhasználó mindig az „1 drágább = X olcsóbb” irányt látja
 * (pl. 1 EUR = 399 HUF), akkor is, ha a tétel HUF-ban van és a számla EUR-ban.
 */
data class RateField(
    /** Bal oldal: „1 [leftCode] = ” */
    val leftCode: String,
    /** Jobb oldal: „… [rightCode]” */
    val rightCode: String,
    val text: String,
    /** Igaz, ha a mező a lekért (tárolt) árfolyamot mutatja, nem kézi értéket. */
    val isDefault: Boolean,
    /** Nincs tárolt árfolyam, kézzel kell megadni. */
    val noStoredRate: Boolean,
    val error: Boolean,
    /** Pl. „≈ 3 990 000 Ft” – ennyivel változik a számla egyenlege. */
    val accountAmountPreview: String?,
)
