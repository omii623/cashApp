package com.example.cashapp.data.repository

import com.example.cashapp.data.local.dao.CurrencyDao
import com.example.cashapp.data.local.dao.ExchangeRateDao
import com.example.cashapp.data.local.entity.CurrencyEntity
import java.util.Locale
import com.example.cashapp.domain.model.Currency
import com.example.cashapp.domain.money.CurrencyConverter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class CurrencyRepository(
    private val currencyDao: CurrencyDao,
    private val rateDao: ExchangeRateDao,
) {
    /** Pénznemek kód szerint. */
    fun observeCurrencies(): Flow<Map<String, Currency>> =
        currencyDao.observeAll().map { list -> list.associate { it.code to it.toDomain() } }

    /** Mindig a legfrissebb (kézi felülírást is figyelembe vevő) árfolyamokkal dolgozó átváltó. */
    fun observeConverter(): Flow<CurrencyConverter> =
        combine(observeCurrencies(), rateDao.observeAll()) { currencies, rates ->
            CurrencyConverter(
                rates = rates.mapNotNull { r ->
                    r.effectiveRate?.let { CurrencyConverter.ExchangeRate(r.baseCode, r.quoteCode, it) }
                },
                decimalsOf = { code -> currencies[code]?.decimals ?: 2 },
            )
        }

    /** Pénznem törlésének eredménye. */
    enum class RemoveResult { REMOVED, IN_USE, PROTECTED }

    /**
     * Új pénznem felvétele ISO kód alapján (név, szimbólum, tizedesek a rendszerből).
     * @return hamis, ha ismeretlen kód, már létezik, vagy elérte a [MAX_CURRENCIES] darabot
     */
    suspend fun addCurrency(code: String): Boolean {
        val iso = runCatching { java.util.Currency.getInstance(code) }.getOrNull() ?: return false
        val existing = currencyDao.getAll()
        if (existing.size >= MAX_CURRENCIES || existing.any { it.code == iso.currencyCode }) return false
        currencyDao.insertAll(
            listOf(
                CurrencyEntity(
                    code = iso.currencyCode,
                    name = iso.getDisplayName(HUNGARIAN).replaceFirstChar { it.titlecase(HUNGARIAN) },
                    symbol = iso.getSymbol(HUNGARIAN).takeIf { it != iso.currencyCode } ?: iso.currencyCode,
                    decimals = iso.defaultFractionDigits.coerceIn(0, 3),
                    sortOrder = currencyDao.maxSortOrder() + 1,
                )
            )
        )
        return true
    }

    /** Törlés csak akkor, ha semmi nem használja; az alap pénznem és az EUR (árfolyam-alap) védett. */
    suspend fun removeCurrency(code: String, baseCurrency: String): RemoveResult {
        if (code == baseCurrency || code == RATE_BASE) return RemoveResult.PROTECTED
        if (currencyDao.usageCount(code) > 0) return RemoveResult.IN_USE
        rateDao.deleteFor(code)
        currencyDao.delete(code)
        return RemoveResult.REMOVED
    }

    companion object {
        const val MAX_CURRENCIES = 10
        /** Az árfolyamok EUR alapon jönnek, ezért az EUR nem törölhető. */
        const val RATE_BASE = "EUR"
        private val HUNGARIAN: Locale = Locale.forLanguageTag("hu")
    }
}
