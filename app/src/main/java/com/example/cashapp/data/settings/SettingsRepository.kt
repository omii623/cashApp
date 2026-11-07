package com.example.cashapp.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.cashapp.data.local.seed.DefaultData
import com.example.cashapp.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Egyszerű felhasználói beállítások (nem relációs adat → DataStore). */
class SettingsRepository(context: Context) {

    private val store = context.dataStore

    val baseCurrency: Flow<String> =
        store.data.map { it[KEY_BASE_CURRENCY] ?: DefaultData.BASE_CURRENCY }

    val themeMode: Flow<ThemeMode> =
        store.data.map { prefs ->
            prefs[KEY_THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM
        }

    /** Főoldali összegek elrejtése (csillagozás) – megmarad újraindítás után is. */
    val hideAmounts: Flow<Boolean> = store.data.map { it[KEY_HIDE_AMOUNTS] ?: false }

    suspend fun setHideAmounts(hide: Boolean) {
        store.edit { it[KEY_HIDE_AMOUNTS] = hide }
    }

    /** Értesítés az esedékes ismétlődő tételekről (alapból be). */
    val recurringNotifications: Flow<Boolean> = store.data.map { it[KEY_RECURRING_NOTIFY] ?: true }

    suspend fun setRecurringNotifications(enabled: Boolean) {
        store.edit { it[KEY_RECURRING_NOTIFY] = enabled }
    }

    suspend fun setBaseCurrency(code: String) {
        store.edit { it[KEY_BASE_CURRENCY] = code }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[KEY_THEME] = mode.name }
    }

    /** Minden beállítás alaphelyzetbe (az „Összes adat törlése” része). */
    suspend fun clear() {
        store.edit { it.clear() }
    }

    /** Mentéshez: a beállítások szövegként (csak a ténylegesen beállítottak). */
    suspend fun snapshot(): Map<String, String> {
        val prefs = store.data.first()
        return buildMap {
            prefs[KEY_BASE_CURRENCY]?.let { put(BACKUP_BASE_CURRENCY, it) }
            prefs[KEY_THEME]?.let { put(BACKUP_THEME, it) }
            prefs[KEY_HIDE_AMOUNTS]?.let { put(BACKUP_HIDE_AMOUNTS, it.toString()) }
            prefs[KEY_RECURRING_NOTIFY]?.let { put(BACKUP_RECURRING_NOTIFY, it.toString()) }
        }
    }

    /** Visszaállításkor: a mentett beállítások (a hiányzók alapértékre állnak). */
    suspend fun restore(values: Map<String, String>) {
        store.edit { prefs ->
            prefs.clear()
            values[BACKUP_BASE_CURRENCY]?.let { prefs[KEY_BASE_CURRENCY] = it }
            values[BACKUP_THEME]?.takeIf { v -> ThemeMode.entries.any { it.name == v } }?.let { prefs[KEY_THEME] = it }
            values[BACKUP_HIDE_AMOUNTS]?.toBooleanStrictOrNull()?.let { prefs[KEY_HIDE_AMOUNTS] = it }
            values[BACKUP_RECURRING_NOTIFY]?.toBooleanStrictOrNull()?.let { prefs[KEY_RECURRING_NOTIFY] = it }
        }
    }

    private companion object {
        const val BACKUP_BASE_CURRENCY = "baseCurrency"
        const val BACKUP_THEME = "themeMode"
        const val BACKUP_HIDE_AMOUNTS = "hideAmounts"
        const val BACKUP_RECURRING_NOTIFY = "recurringNotifications"

        val KEY_BASE_CURRENCY = stringPreferencesKey("base_currency")
        val KEY_THEME = stringPreferencesKey("theme_mode")
        val KEY_HIDE_AMOUNTS = booleanPreferencesKey("hide_amounts")
        val KEY_RECURRING_NOTIFY = booleanPreferencesKey("recurring_notifications")
    }
}
