package com.example.cashapp.data.remote

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class RateApiTest {
    @Test fun `currency-api formátum`() {
        val r = RateApi.parseCurrencyApi("""{"date":"2026-11-07","eur":{"huf":363.09,"rsd":117.56,"usd":1.114}}""")
        assertEquals(363.09, r.getValue("HUF"), 1e-9)
        assertEquals(117.56, r.getValue("RSD"), 1e-9)
    }

    @Test fun `open-er-api formátum`() {
        val r = RateApi.parseOpenErApi("""{"result":"success","rates":{"HUF":363.2,"RSD":117.5}}""")
        assertEquals(363.2, r.getValue("HUF"), 1e-9)
    }

    @Test fun `ha minden forrás hibás, kivételt dob`() = runBlocking {
        val api = RateApi(
            listOf(
                RateApi.Source("http://127.0.0.1:1/nincs") { error("nem hívódhat") },
                RateApi.Source("http://127.0.0.1:1/ez-sem") { emptyMap() },
            ),
            timeoutMs = 500,
        )
        val result = runCatching { api.fetchEurRates() }
        assert(result.isFailure)
    }
}
