package com.example.cashapp.data

import com.example.cashapp.data.local.CashDatabase
import com.example.cashapp.data.local.seed.DatabaseSeeder
import com.example.cashapp.data.repository.ExchangeRateRepository
import com.example.cashapp.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * „Összes adat törlése”: minden tábla és beállítás törlődik, majd az app
 * úgy indul, mint első telepítéskor (alap pénznemek, kategóriák, számlák).
 * Visszafordíthatatlan – a UI kétszer kérdez rá.
 */
class DataResetter(
    private val database: CashDatabase,
    private val settingsRepository: SettingsRepository,
    private val exchangeRateRepository: ExchangeRateRepository,
) {
    suspend fun resetAll() = withContext(Dispatchers.IO) {
        database.clearAllTables()
        settingsRepository.clear()
        DatabaseSeeder(database).seedIfEmpty()
        exchangeRateRepository.refreshIfStale()
    }
}
