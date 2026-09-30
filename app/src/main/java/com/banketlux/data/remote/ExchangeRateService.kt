package com.banketlux.data.remote

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class ExchangeRateResponse(
    val result: String = "",
    val time_last_update_unix: Long = 0,
    val rates: Map<String, Double> = emptyMap()
)

data class FetchedRate(val eurToRsd: Double, val fetchedAtEpochMillis: Long)

/**
 * Preuzima dnevni kurs EUR -> RSD sa open.er-api.com (besplatno, bez ključa).
 * Aplikacija radi i bez interneta — tada se koristi poslednji sačuvani kurs.
 */
class ExchangeRateService(
    private val endpoint: String = "https://open.er-api.com/v6/latest/EUR",
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    suspend fun fetchEurToRsd(): Result<FetchedRate> = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
            }
            val payload = try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    error("HTTP ${connection.responseCode}")
                }
                connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } finally {
                connection.disconnect()
            }

            val decoded = json.decodeFromString(ExchangeRateResponse.serializer(), payload)
            val rate = decoded.rates["RSD"]
            require(decoded.result == "success") { "Servis kursa je vratio grešku." }
            require(rate != null && rate.isFinite() && rate > 0.0) { "Kurs za RSD nije dostupan." }

            FetchedRate(
                eurToRsd = rate,
                fetchedAtEpochMillis = if (decoded.time_last_update_unix > 0) {
                    decoded.time_last_update_unix * 1000
                } else {
                    System.currentTimeMillis()
                }
            )
        }
    }
}
