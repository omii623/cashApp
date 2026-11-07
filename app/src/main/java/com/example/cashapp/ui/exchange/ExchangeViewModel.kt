package com.example.cashapp.ui.exchange

import com.example.cashapp.domain.money.MoneyFormatter
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cashapp.data.local.entity.AccountEntity
import com.example.cashapp.data.local.entity.ExchangeDetailEntity
import com.example.cashapp.data.local.entity.TransactionEntity
import com.example.cashapp.data.repository.AccountRepository
import com.example.cashapp.data.repository.CategoryRepository
import com.example.cashapp.data.repository.CurrencyRepository
import com.example.cashapp.data.repository.ExchangeRateRepository
import com.example.cashapp.data.repository.TransactionRepository
import com.example.cashapp.domain.model.Currency
import com.example.cashapp.domain.model.RateSource
import com.example.cashapp.domain.model.TransactionType
import com.example.cashapp.domain.money.AmountParser
import com.example.cashapp.domain.money.CurrencyConverter
import com.example.cashapp.domain.money.ExchangeMath
import com.example.cashapp.ui.transaction.AccountOption
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ExchangeUiState(
    val isLoading: Boolean = true,
    val accounts: List<AccountOption> = emptyList(),
    val from: AccountOption? = null,
    val to: AccountOption? = null,
    val fromCurrency: Currency? = null,
    val toCurrency: Currency? = null,

    val fromAmountText: String = "",
    val toAmountText: String = "",

    /** null, ha a két számla pénzneme azonos (nincs mit váltani, 1:1). */
    val rate: ExchangeRateField? = null,
    /** Legutóbbi sikeres lekérés ideje (epoch ms). */
    val lastFetchedAt: Long? = null,
    val isFetching: Boolean = false,
    val fetchFailed: Boolean = false,

    val date: LocalDate = LocalDate.now(),
    val feeText: String = "",
    val note: String = "",

    val fromAmountError: Boolean = false,
    val toAmountError: Boolean = false,
    val sameAccountError: Boolean = false,
    val feeError: Boolean = false,
    /** Ha van díj: a forrás számláról levont teljes összeg (váltott + díj), pl. „100 500 Ft”. */
    val totalDebitText: String? = null,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    /** A mentés hibára futott (a képernyő nyitva marad, hibaüzenettel). */
    val saveFailed: Boolean = false,
    /** Meglévő váltás szerkesztése (más cím, Törlés gomb). */
    val isEditing: Boolean = false,
)

data class ExchangeRateField(
    /** „1 [leftCode] = [text] [rightCode]” */
    val leftCode: String,
    val rightCode: String,
    val text: String,
    /** Kézzel megadott (vagy a cél összegből visszaszámolt) árfolyam. */
    val isManual: Boolean,
    /** A tárolt (API) árfolyam ugyanebben az irányban, ha van – összehasonlításhoz. */
    val storedText: String?,
    val error: Boolean,
)

/**
 * Pénzváltás. [editId] != null → meglévő váltás szerkesztése: a két összeg pontosan úgy
 * töltődik be, ahogy rögzítve lett (a cél összeg „vezet”, így nincs kerekítési eltérés).
 */
class ExchangeViewModel(
    private val editId: Long?,
    private val accountRepository: AccountRepository,
    private val currencyRepository: CurrencyRepository,
    private val exchangeRateRepository: ExchangeRateRepository,
    private val transactionRepository: TransactionRepository,
) : ViewModel() {

    /** Melyik mezőt írta be utoljára a felhasználó – a másik kettő ebből számolódik. */
    private enum class Lead { FROM, TO }

    private data class Input(
        val fromId: Long? = null,
        val toId: Long? = null,
        val fromText: String = "",
        val toText: String = "",
        /** Kézzel beírt árfolyam (megjelenítési irányban); null = tárolt árfolyam. */
        val rateText: String? = null,
        val lead: Lead = Lead.FROM,
        val date: LocalDate = LocalDate.now(),
        val feeText: String = "",
        val note: String = "",
        val showErrors: Boolean = false,
        val isSaving: Boolean = false,
        val isSaved: Boolean = false,
        val saveFailed: Boolean = false,
        val isFetching: Boolean = false,
        val fetchFailed: Boolean = false,
        val isLoaded: Boolean = true,
        /** Szerkesztésnél: amíg semmi nem változott, az eredeti árfolyam-forrás marad. */
        val originalSource: RateSource? = null,
    )

    private val input = MutableStateFlow(Input(isLoaded = editId == null))

    /** Szerkesztett váltás eredeti (esetleg archivált) számlái. */
    private val pinnedAccounts = MutableStateFlow<List<AccountEntity>>(emptyList())

    init {
        if (editId != null) viewModelScope.launch { loadForEdit(editId) }
    }

    private suspend fun loadForEdit(id: Long) {
        val tx = transactionRepository.get(id)
        val detail = tx?.let { transactionRepository.getExchangeDetail(it.id) }
        if (tx == null || detail == null) {
            input.update { it.copy(isSaved = true) } // közben törölték → vissza
            return
        }
        // Az eredeti számlák akkor is választhatók maradnak, ha azóta archiválták őket.
        pinnedAccounts.value = listOfNotNull(accountRepository.get(tx.accountId), accountRepository.get(detail.targetAccountId))
        val currencies = currencyRepository.observeCurrencies().first()
        input.value = Input(
            fromId = tx.accountId,
            toId = detail.targetAccountId,
            // A tárolt forrás összegben benne van a díj → a mezőkbe szétválasztva töltjük vissza.
            fromText = AmountParser.toEditText(tx.amountMinor - detail.feeMinor, currencies[tx.currencyCode]?.decimals ?: 0),
            feeText = if (detail.feeMinor > 0) AmountParser.toEditText(detail.feeMinor, currencies[tx.currencyCode]?.decimals ?: 0) else "",
            toText = AmountParser.toEditText(detail.targetAmountMinor, currencies[detail.targetCurrencyCode]?.decimals ?: 0),
            lead = Lead.TO,
            date = tx.date,
            note = tx.note,
            isLoaded = true,
            originalSource = detail.rateSource,
        )
    }

    /** Bármely módosítás után már a számolt forrás (API / kézi) érvényes. */
    private fun Input.touched() = copy(originalSource = null)

    private data class Data(
        val accounts: List<AccountEntity>,
        val currencies: Map<String, Currency>,
        val converter: CurrencyConverter,
        val lastFetchedAt: Long?,
    )

    private val data = combine(
        accountRepository.observeActive(),
        currencyRepository.observeCurrencies(),
        currencyRepository.observeConverter(),
        exchangeRateRepository.observeLastFetchedAt(),
        ::Data,
    )

    /** A számolt értékek (állapot + mentéshez szükséges nyers adatok). */
    private data class Computed(val state: ExchangeUiState, val draft: Draft?)

    private data class Draft(
        val from: AccountEntity,
        val to: AccountEntity,
        val fromMinor: Long,
        val toMinor: Long,
        val rate: Double,
        val source: RateSource,
        val feeMinor: Long?,
    )

    private val computed = combine(input, data, pinnedAccounts) { i, d, pinned ->
        compute(i, d.copy(accounts = d.accounts + pinned.filter { p -> d.accounts.none { it.id == p.id } }))
    }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Computed(ExchangeUiState(), null))

    val uiState: StateFlow<ExchangeUiState> = computed.map { it.state }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ExchangeUiState())

    // ------------------------------------------------------------------ események

    fun onFromAccountSelect(id: Long) = input.update { it.touched().copy(fromId = id, rateText = null) }

    fun onToAccountSelect(id: Long) = input.update { it.touched().copy(toId = id, rateText = null) }

    /** A két számla felcserélése (⇅ gomb). */
    fun swapAccounts() = input.update { s ->
        val st = uiState.value
        s.touched().copy(fromId = st.to?.id, toId = st.from?.id, rateText = null, lead = Lead.FROM, fromText = st.fromAmountText)
    }

    fun onFromAmountChange(text: String) = input.update { it.freezeRateIfNeeded().touched().copy(fromText = text, lead = Lead.FROM) }

    fun onToAmountChange(text: String) = input.update {
        it.touched().copy(toText = text, lead = Lead.TO, fromText = uiState.value.fromAmountText)
    }

    fun onRateChange(text: String) = input.update { it.touched().copy(rateText = text, lead = Lead.FROM, fromText = uiState.value.fromAmountText) }

    /** Vissza a tárolt (lekért) árfolyamra. */
    fun onRateReset() = input.update { it.touched().copy(rateText = null, lead = Lead.FROM, fromText = uiState.value.fromAmountText) }

    fun dismissSaveError() = input.update { it.copy(saveFailed = false) }

    fun onDateSelect(date: LocalDate) = input.update { it.copy(date = date) }

    fun onFeeChange(text: String) = input.update { it.copy(feeText = text) }

    fun onNoteChange(text: String) = input.update { it.copy(note = text) }

    /** „Aktuális lekérése”: friss árfolyam az internetről; a kézi értéket elveti. */
    fun fetchRates() {
        if (input.value.isFetching) return
        input.update { it.copy(isFetching = true, fetchFailed = false) }
        viewModelScope.launch {
            val ok = runCatching { exchangeRateRepository.refresh() }.isSuccess
            input.update {
                if (ok) it.touched().copy(isFetching = false, rateText = null, lead = Lead.FROM, fromText = uiState.value.fromAmountText)
                else it.copy(isFetching = false, fetchFailed = true)
            }
        }
    }

    fun save() {
        val draft = computed.value.draft
        if (draft == null || input.value.isSaving) {
            input.update { it.copy(showErrors = true) }
            return
        }
        input.update { it.copy(isSaving = true) }
        val s = input.value
        viewModelScope.launch {
            val fee = draft.feeMinor ?: 0
            // A forrás számláról a váltott összeg + a díj megy le (egyetlen tételként).
            val debit = draft.fromMinor + fee
            val transaction = TransactionEntity(
                type = TransactionType.EXCHANGE,
                date = s.date,
                amountMinor = debit,
                currencyCode = draft.from.currencyCode,
                accountId = draft.from.id,
                accountAmountMinor = debit,
                note = s.note.trim(),
            )
            val detail = ExchangeDetailEntity(
                transactionId = editId ?: 0,
                targetAccountId = draft.to.id,
                targetAmountMinor = draft.toMinor,
                targetCurrencyCode = draft.to.currencyCode,
                rate = draft.rate,
                rateSource = draft.source,
                feeMinor = fee,
            )
            // Hiba esetén ne omoljon össze, de ne is tűnjön sikeresnek: a beírt adatok megmaradnak.
            val ok = runCatching {
                if (editId != null) transactionRepository.updateExchange(editId, transaction, detail)
                else transactionRepository.saveExchange(transaction, detail)
            }.isSuccess
            input.update { it.copy(isSaving = false, isSaved = ok, saveFailed = !ok) }
        }
    }

    /** Csak szerkesztésnél: a váltás törlése (a cél oldallal együtt). */
    fun delete() {
        val id = editId ?: return
        viewModelScope.launch {
            transactionRepository.delete(id)
            input.update { it.copy(isSaved = true) }
        }
    }

    // ------------------------------------------------------------------ számítás

    /** Ha a cél összeg vezetett, a belőle számolt árfolyamot rögzítjük, mielőtt a forrás vezetne. */
    private fun Input.freezeRateIfNeeded(): Input =
        if (lead == Lead.TO) copy(rateText = uiState.value.rate?.text, fromText = uiState.value.fromAmountText) else this

    private fun compute(input: Input, data: Data): Computed {
        val accounts = data.accounts
        if (accounts.isEmpty() || data.currencies.isEmpty() || !input.isLoaded) {
            return Computed(ExchangeUiState(isEditing = editId != null, isSaved = input.isSaved), null)
        }
        val from = accounts.firstOrNull { it.id == input.fromId } ?: accounts.first()
        // Alapértelmezett cél: első eltérő pénznemű számla, különben bármely másik.
        val to = accounts.firstOrNull { it.id == input.toId }
            ?: accounts.firstOrNull { it.currencyCode != from.currencyCode }
            ?: accounts.firstOrNull { it.id != from.id }
            ?: from
        val fromCur = data.currencies.getValue(from.currencyCode)
        val toCur = data.currencies.getValue(to.currencyCode)
        val sameCurrency = fromCur.code == toCur.code

        val stored = if (sameCurrency) 1.0 else data.converter.rate(fromCur.code, toCur.code)
        val inverted = ExchangeMath.isDisplayInverted(stored, fromCur.decimals, toCur.decimals)
        val storedShownText = stored?.let { AmountParser.rateToEditText(ExchangeMath.toDisplay(it, inverted)) }

        var fromMinor: Long?
        var toMinor: Long?
        var rate: Double?
        val fromText: String
        val toText: String
        val rateText: String
        val manual: Boolean

        if (input.lead == Lead.TO && !sameCurrency) {
            fromText = input.fromText
            toText = input.toText
            fromMinor = AmountParser.parseAmount(fromText, fromCur.decimals)
            toMinor = AmountParser.parseAmount(toText, toCur.decimals)
            rate = if (fromMinor != null && toMinor != null)
                ExchangeMath.rateFromAmounts(fromMinor, fromCur.decimals, toMinor, toCur.decimals) else null
            rateText = rate?.let { AmountParser.rateToEditText(ExchangeMath.toDisplay(it, inverted)) } ?: ""
            manual = true
        } else {
            fromText = input.fromText
            fromMinor = AmountParser.parseAmount(fromText, fromCur.decimals)
            rateText = if (sameCurrency) "1" else input.rateText ?: storedShownText ?: ""
            rate = if (sameCurrency) 1.0 else AmountParser.parseRate(rateText)?.let { ExchangeMath.fromDisplay(it, inverted) }
            toMinor = if (fromMinor != null && rate != null)
                CurrencyConverter.convertWithRate(fromMinor, fromCur.decimals, toCur.decimals, rate) else null
            toText = toMinor?.let { AmountParser.toEditText(it, toCur.decimals) } ?: ""
            manual = !sameCurrency && input.rateText != null && input.rateText != storedShownText
        }

        val feeMinor = input.feeText.takeIf { it.isNotBlank() }?.let { AmountParser.parseAmount(it, fromCur.decimals) }
        val feeInvalid = input.feeText.isNotBlank() && feeMinor == null
        val sameAccount = from.id == to.id

        // Szerkesztésnél, amíg semmi nem változott, az eredeti forrás (API / kézi) marad.
        val source = input.originalSource ?: if (manual) RateSource.MANUAL else RateSource.API
        val draft = if (fromMinor != null && toMinor != null && toMinor > 0 && rate != null && !sameAccount && !feeInvalid) {
            Draft(from, to, fromMinor, toMinor, rate, source, feeMinor)
        } else null

        val state = ExchangeUiState(
            isLoading = false,
            accounts = accounts.map { AccountOption(it.id, it.name, it.currencyCode) },
            from = AccountOption(from.id, from.name, from.currencyCode),
            to = AccountOption(to.id, to.name, to.currencyCode),
            fromCurrency = fromCur,
            toCurrency = toCur,
            fromAmountText = fromText,
            toAmountText = toText,
            rate = if (sameCurrency) null else ExchangeRateField(
                leftCode = if (inverted) toCur.code else fromCur.code,
                rightCode = if (inverted) fromCur.code else toCur.code,
                text = rateText,
                isManual = source == RateSource.MANUAL,
                storedText = storedShownText,
                error = input.showErrors && rate == null,
            ),
            lastFetchedAt = data.lastFetchedAt,
            isFetching = input.isFetching,
            fetchFailed = input.fetchFailed,
            date = input.date,
            feeText = input.feeText,
            note = input.note,
            fromAmountError = input.showErrors && fromMinor == null,
            toAmountError = input.showErrors && (toMinor == null || toMinor <= 0),
            sameAccountError = sameAccount,
            feeError = feeInvalid,
            totalDebitText = if (fromMinor != null && feeMinor != null && feeMinor > 0)
                MoneyFormatter.format(fromMinor + feeMinor, fromCur) else null,
            isSaving = input.isSaving,
            isSaved = input.isSaved,
            saveFailed = input.saveFailed,
            isEditing = editId != null,
        )
        return Computed(state, draft)
    }

}
