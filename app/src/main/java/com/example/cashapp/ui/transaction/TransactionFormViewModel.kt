package com.example.cashapp.ui.transaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cashapp.data.local.entity.AccountEntity
import com.example.cashapp.data.local.entity.CategoryEntity
import com.example.cashapp.data.local.entity.SubcategoryEntity
import com.example.cashapp.data.local.entity.TransactionEntity
import com.example.cashapp.data.repository.AccountRepository
import com.example.cashapp.data.repository.BankLoanRepository
import com.example.cashapp.data.repository.CategoryRepository
import com.example.cashapp.data.repository.CurrencyRepository
import com.example.cashapp.data.repository.TransactionRepository
import com.example.cashapp.domain.model.CategoryType
import com.example.cashapp.domain.model.Currency
import com.example.cashapp.domain.model.TransactionType
import com.example.cashapp.domain.money.AmountParser
import com.example.cashapp.domain.money.CurrencyConverter
import com.example.cashapp.domain.money.MoneyFormatter
import com.example.cashapp.ui.bankloan.iconKey
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Bevétel és kiadás közös űrlapja. A [type] dönti el, melyik kategóriák jelennek meg.
 * [editId] != null → meglévő tétel szerkesztése (betöltés, frissítés, törlés).
 */
class TransactionFormViewModel(
    private val type: TransactionType,
    private val editId: Long?,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val currencyRepository: CurrencyRepository,
    private val transactionRepository: TransactionRepository,
    private val bankLoanRepository: BankLoanRepository? = null,
) : ViewModel() {

    init {
        require(type != TransactionType.EXCHANGE) { "Váltásnak külön képernyője van" }
        if (editId != null) viewModelScope.launch { loadForEdit(editId) }
    }

    /** A meglévő tétel mezőinek betöltése az űrlapba. */
    private suspend fun loadForEdit(id: Long) {
        val tx = transactionRepository.get(id) ?: run {
            input.update { it.copy(isSaved = true) } // közben törölték → vissza
            return
        }
        // Szerkesztésnél az eredeti számla / kategória / alkategória akkor is választható marad,
        // ha azóta archiválták – különben a mentés csendben átírná őket.
        pinned.value = Pinned(
            account = accountRepository.get(tx.accountId),
            category = tx.categoryId?.let { categoryRepository.getCategory(it) },
            subcategory = tx.subcategoryId?.let { categoryRepository.getSubcategory(it) },
            // Banki hitel tétele kategória nélküli: a hitel jelöli, kategóriát nem kell választani.
            bankLoan = tx.bankLoanId?.takeIf { tx.categoryId == null }?.let { bankLoanRepository?.get(it) }
                ?.let { BankLoanTag(it.name, it.color, it.icon ?: it.kind.iconKey()) },
        )
        val decimals = currencyRepository.observeCurrencies().first()[tx.currencyCode]?.decimals ?: 0
        // A használt árfolyam ugyanabban az irányban, ahogy az űrlap mutatja (1 drágább = X olcsóbb).
        val rateText = tx.rateToAccount?.let { r -> AmountParser.rateToEditText(if (r < 1) 1 / r else r) }
        input.update {
            it.copy(
                amountText = AmountParser.toEditText(tx.amountMinor, decimals),
                currencyCode = tx.currencyCode,
                accountId = tx.accountId,
                date = tx.date,
                categoryId = tx.categoryId,
                subcategoryId = tx.subcategoryId,
                note = tx.note,
                rateText = rateText,
                isLoaded = true,
            )
        }
    }

    private val categoryType = if (type == TransactionType.INCOME) CategoryType.INCOME else CategoryType.EXPENSE

    /** A felhasználó által módosított mezők. `null` = „alapértelmezett”. */
    private data class Input(
        val amountText: String = "",
        val currencyCode: String? = null,
        val accountId: Long? = null,
        val date: LocalDate = LocalDate.now(),
        val categoryId: Long? = null,
        val subcategoryId: Long? = null,
        val note: String = "",
        val rateText: String? = null,
        val showErrors: Boolean = false,
        val isSaving: Boolean = false,
        val isSaved: Boolean = false,
        val saveFailed: Boolean = false,
        /** Szerkesztésnél: a meglévő tétel már betöltődött (addig töltő ikon). */
        val isLoaded: Boolean = false,
    )

    private val input = MutableStateFlow(Input(isLoaded = editId == null))

    /** Szerkesztett tétel eredeti (esetleg archivált) hivatkozásai. */
    private data class Pinned(
        val account: AccountEntity? = null,
        val category: CategoryEntity? = null,
        val subcategory: SubcategoryEntity? = null,
        val bankLoan: BankLoanTag? = null,
    )

    private val pinned = MutableStateFlow(Pinned())

    private data class Data(
        val accounts: List<AccountEntity>,
        val currencies: Map<String, Currency>,
        val categories: List<CategoryEntity>,
        val subcategories: List<SubcategoryEntity>,
        val converter: CurrencyConverter,
    )

    private val data = combine(
        accountRepository.observeActive(),
        currencyRepository.observeCurrencies(),
        categoryRepository.observeCategories(categoryType),
        categoryRepository.observeSubcategories(categoryType),
        currencyRepository.observeConverter(),
        ::Data,
    )

    val uiState: StateFlow<TransactionFormUiState> = combine(input, data, pinned) { i, d, p -> buildState(i, d.withPinned(p), p.bankLoan) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionFormUiState(type))

    // ------------------------------------------------------------------ események

    fun onAmountChange(text: String) = input.update { it.copy(amountText = text) }

    /** Számlaváltáskor a pénznem és az árfolyam visszaáll a számláéra. */
    fun onAccountSelect(id: Long) = input.update { it.copy(accountId = id, currencyCode = null, rateText = null) }

    fun onCurrencySelect(code: String) = input.update { it.copy(currencyCode = code, rateText = null) }

    fun onDateSelect(date: LocalDate) = input.update { it.copy(date = date) }

    fun onCategorySelect(id: Long) = input.update {
        if (it.categoryId == id) it else it.copy(categoryId = id, subcategoryId = null)
    }

    /** Újra koppintva a kijelölt alkategóriára: kijelölés megszüntetése. */
    fun onSubcategorySelect(id: Long) = input.update {
        it.copy(subcategoryId = if (it.subcategoryId == id) null else id)
    }

    fun onNoteChange(text: String) = input.update { it.copy(note = text) }

    fun onRateChange(text: String) = input.update { it.copy(rateText = text) }

    /** Kézi árfolyam elvetése, vissza a tárolt értékre. */
    fun onRateReset() = input.update { it.copy(rateText = null) }

    fun save() {
        val state = uiState.value
        val draft = state.draft
        if (draft == null || state.isSaving) {
            input.update { it.copy(showErrors = true) }
            return
        }
        input.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            // Hiba esetén ne omoljon össze, de ne is tűnjön sikeresnek: a beírt adatok megmaradnak.
            val ok = runCatching {
                if (editId == null) transactionRepository.saveIncomeOrExpense(draft)
                else transactionRepository.updateIncomeOrExpense(editId, draft)
            }.isSuccess
            input.update { it.copy(isSaving = false, isSaved = ok, saveFailed = !ok) }
        }
    }

    fun dismissSaveError() = input.update { it.copy(saveFailed = false) }

    /** Csak szerkesztésnél. */
    fun delete() {
        val id = editId ?: return
        viewModelScope.launch {
            transactionRepository.delete(id)
            input.update { it.copy(isSaved = true) }
        }
    }

    // ------------------------------------------------------------------ állapot számítás

    private fun buildState(input: Input, data: Data, bankLoan: BankLoanTag?): TransactionFormUiState {
        val account = data.accounts.firstOrNull { it.id == input.accountId } ?: data.accounts.firstOrNull()
        val currencyCode = input.currencyCode ?: account?.currencyCode
        val currency = currencyCode?.let { data.currencies[it] }
        if (account == null || currency == null || !input.isLoaded) {
            return TransactionFormUiState(type, isLoading = true, isEditing = editId != null, isSaved = input.isSaved)
        }
        val accountCurrency = data.currencies.getValue(account.currencyCode)

        val amountMinor = AmountParser.parseAmount(input.amountText, currency.decimals)
        val category = data.categories.firstOrNull { it.id == input.categoryId }
        val subcategories = data.subcategories.filter { it.categoryId == category?.id }

        // --- árfolyam (tétel pénzneme → számla pénzneme)
        var rateToAccount: Double? = 1.0
        var rateField: RateField? = null
        if (currency.code != account.currencyCode) {
            val stored = data.converter.rate(currency.code, account.currencyCode)
            // A megjelenítés iránya: 1 drágább = X olcsóbb. Tárolt árfolyam híján a tizedesek döntenek.
            val inverted = if (stored != null) stored < 1 else currency.decimals < accountCurrency.decimals
            val shownDefault = stored?.let { if (inverted) 1 / it else it }
            val text = input.rateText ?: shownDefault?.let(AmountParser::rateToEditText) ?: ""
            rateToAccount = AmountParser.parseRate(text)?.let { if (inverted) 1 / it else it }

            val accountAmount = if (amountMinor != null && rateToAccount != null)
                CurrencyConverter.convertWithRate(amountMinor, currency.decimals, accountCurrency.decimals, rateToAccount)
            else null
            rateField = RateField(
                leftCode = if (inverted) account.currencyCode else currency.code,
                rightCode = if (inverted) currency.code else account.currencyCode,
                text = text,
                isDefault = input.rateText == null && stored != null,
                noStoredRate = stored == null,
                error = input.showErrors && rateToAccount == null,
                accountAmountPreview = accountAmount?.let { "≈ " + MoneyFormatter.format(it, accountCurrency) },
            )
        }

        val draft = if (amountMinor != null && (category != null || bankLoan != null) && rateToAccount != null) {
            TransactionEntity(
                type = type,
                date = input.date,
                amountMinor = amountMinor,
                currencyCode = currency.code,
                accountId = account.id,
                accountAmountMinor = if (rateField == null) amountMinor else
                    CurrencyConverter.convertWithRate(amountMinor, currency.decimals, accountCurrency.decimals, rateToAccount),
                rateToAccount = if (rateField == null) null else rateToAccount,
                categoryId = category?.id,
                subcategoryId = input.subcategoryId?.takeIf { id -> subcategories.any { it.id == id } },
                note = input.note.trim(),
            ).takeIf { it.accountAmountMinor > 0 }
        } else null

        return TransactionFormUiState(
            type = type,
            isLoading = false,
            amountText = input.amountText,
            currency = currency,
            currencies = data.currencies.values.toList(),
            account = account.toOption(),
            accounts = data.accounts.map { it.toOption() },
            date = input.date,
            categories = data.categories.map { CategoryOption(it.id, it.name, it.color, it.icon) },
            selectedCategoryId = category?.id,
            subcategories = subcategories.map { SubcategoryOption(it.id, it.name) },
            selectedSubcategoryId = input.subcategoryId,
            note = input.note,
            rate = rateField,
            amountError = input.showErrors && amountMinor == null,
            categoryError = input.showErrors && category == null && bankLoan == null,
            bankLoan = bankLoan,
            isSaving = input.isSaving,
            isSaved = input.isSaved,
            saveFailed = input.saveFailed,
            isEditing = editId != null,
            draft = draft,
        )
    }

    private fun Data.withPinned(p: Pinned) = copy(
        accounts = accounts.plusIfMissing(p.account) { it.id },
        categories = categories.plusIfMissing(p.category) { it.id },
        subcategories = subcategories.plusIfMissing(p.subcategory) { it.id },
    )

    private fun <T> List<T>.plusIfMissing(item: T?, id: (T) -> Long): List<T> =
        if (item == null || any { id(it) == id(item) }) this else this + item

    private fun AccountEntity.toOption() = AccountOption(id, name, currencyCode)
}
