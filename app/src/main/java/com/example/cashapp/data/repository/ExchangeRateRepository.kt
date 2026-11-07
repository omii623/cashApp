package com.example.cashapp.data.repository

import com.example.cashapp.data.local.dao.CurrencyDao
import com.example.cashapp.data.local.dao.ExchangeRateDao
import com.example.cashapp.data.local.entity.ExchangeRateEntity
import com.example.cashapp.data.remote.RateApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/** Árfolyamok frissítése az internetről. Az eredmény a [CurrencyRepository.observeConverter]-en keresztül látszik. */
class ExchangeRateRepository(
    private val rateDao: ExchangeRateDao,
    private val currencyDao: CurrencyDao,
    private val api: RateApi = RateApi(),
) {
    fun observeLastFetchedAt(): Flow<Long?> = rateDao.observeLastFetchedAt()

    fun observeRates(): Flow<List<ExchangeRateEntity>> = rateDao.observeAll()

    /** Lekéri az aktuális árfolyamokat az app pénznemeire. Hiba esetén kivételt dob. */
    suspend fun refresh() {
        val codes = currencyDao.observeAll().first().map { it.code }.toSet() - BASE
        val rates = api.fetchEurRates().filterKeys { it in codes }
        check(rates.isNotEmpty()) { "A válaszban nincs használható árfolyam" }
        rateDao.saveApiRates(BASE, rates, System.currentTimeMillis())
    }

    /**
     * Induláskor hívva: ha nincs árfolyam, vagy a legutóbbi régebbi [maxAgeMs]-nál, csendben frissít.
     * Hálózati hiba nem baj – marad a tárolt érték (offline működés).
     */
    suspend fun refreshIfStale(maxAgeMs: Long = 12 * 60 * 60 * 1000L) {
        val last = rateDao.observeLastFetchedAt().first()
        if (last == null || System.currentTimeMillis() - last > maxAgeMs) {
            runCatching { refresh() }
        }
    }

    private companion object {
        const val BASE = "EUR"
    }
}
