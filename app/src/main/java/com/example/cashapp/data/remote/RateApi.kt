package com.example.cashapp.data.remote

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Árfolyam-lekérés EUR alapon (terv.md 6. pont).
 * Ingyenes, kulcs nélküli forrás, amely az RSD-t is ismeri; ha nem elérhető, a tartalékokat próbálja.
 */
class RateApi(
    private val sources: List<Source> = DEFAULT_SOURCES,
    private val timeoutMs: Int = 10_000,
) {
    /** Egy forrás: URL + a válasz feldolgozása (EUR → { kód: árfolyam }). */
    data class Source(val url: String, val parse: (String) -> Map<String, Double>)

    /**
     * @return pl. {"HUF": 401.2, "RSD": 117.1, "USD": 1.08} – 1 EUR ennyi egység.
     * @throws Exception ha egyik forrás sem működik
     */
    suspend fun fetchEurRates(): Map<String, Double> = withContext(Dispatchers.IO) {
        var lastError: Exception? = null
        for (source in sources) {
            try {
                val rates = source.parse(download(source.url))
                if (rates.isNotEmpty()) return@withContext rates
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("Nincs elérhető árfolyam-forrás")
    }

    private fun download(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.setRequestProperty("Accept", "application/json")
            check(conn.responseCode == 200) { "HTTP ${conn.responseCode}: $url" }
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        /** fawazahmed0 currency-api: {"date": "...", "eur": {"huf": 401.2, ...}} – kisbetűs kódok. */
        fun parseCurrencyApi(json: String): Map<String, Double> {
            val eur = JSONObject(json).getJSONObject("eur")
            return eur.keys().asSequence().associate { it.uppercase() to eur.getDouble(it) }
        }

        /** open.er-api.com: {"result": "success", "rates": {"HUF": 401.2, ...}} */
        fun parseOpenErApi(json: String): Map<String, Double> {
            val root = JSONObject(json)
            check(root.optString("result") == "success") { "open.er-api hiba" }
            val rates = root.getJSONObject("rates")
            return rates.keys().asSequence().associateWith { rates.getDouble(it) }
        }

        val DEFAULT_SOURCES = listOf(
            Source("https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/eur.json", ::parseCurrencyApi),
            Source("https://latest.currency-api.pages.dev/v1/currencies/eur.json", ::parseCurrencyApi),
            Source("https://open.er-api.com/v6/latest/EUR", ::parseOpenErApi),
        )
    }
}
