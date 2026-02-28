package com.goldpulse.data.repository

import com.goldpulse.data.model.PricePoint
import com.goldpulse.data.network.GoldApiService
import com.goldpulse.domain.GoldRepository
import com.goldpulse.ui.components.Timeframe
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap

class GoldRepositoryImpl(
    private val api: GoldApiService
) : GoldRepository {

    private val currentCache = ConcurrentHashMap<String, Pair<Long, PricePoint>>()
    private val historyCache = ConcurrentHashMap<String, Pair<Long, List<PricePoint>>>()

    override suspend fun fetchCurrentPrice(currency: String): PricePoint {
        val normalizedCurrency = currency.uppercase()
        currentCache[normalizedCurrency]?.let { (ts, point) ->
            if (System.currentTimeMillis() - ts < 30_000) return point
        }

        val value = retryWithBackoff {
            val response = api.getGoldPrice()
            val usdPrice = response.price
            val finalPrice = if (normalizedCurrency == "USD") {
                usdPrice
            } else {
                val fx = fxRate(normalizedCurrency)
                    ?: throw IllegalStateException("FX rate unavailable for $normalizedCurrency")
                usdPrice * fx
            }
            PricePoint(
                price = finalPrice,
                timestamp = response.timestamp ?: (System.currentTimeMillis() / 1000)
            )
        }

        val result = if (value != null) {
            value
        } else {
            val direct = fetchStooqDailySeries(normalizedCurrency).lastOrNull()
            if (direct != null) {
                direct
            } else {
                if (normalizedCurrency == "USD") {
                    fetchStooqDailySeries("USD").lastOrNull()
                } else {
                    null
                }
            }
        } ?: throw IllegalStateException("No market data available for $normalizedCurrency")

        currentCache[normalizedCurrency] = System.currentTimeMillis() to result
        return result
    }

    override suspend fun fetchHistoricalPrices(currency: String, timeframe: Timeframe): List<PricePoint> {
        val normalizedCurrency = currency.uppercase()
        val cacheKey = "$normalizedCurrency:${timeframe.name}"

        historyCache[cacheKey]?.let { (ts, points) ->
            if (System.currentTimeMillis() - ts < 3 * 60_000) return points
        }

        val directSeries = retryWithBackoff { fetchStooqDailySeries(normalizedCurrency) }.orEmpty()
        val result = if (directSeries.isNotEmpty()) {
            trimByTimeframe(directSeries, timeframe)
        } else {
            val usdSeries = fetchStooqDailySeries("USD")
            if (usdSeries.isEmpty()) {
                emptyList()
            } else if (normalizedCurrency == "USD") {
                trimByTimeframe(usdSeries, timeframe)
            } else {
                val fx = fxRate(normalizedCurrency)
                if (fx == null) {
                    historyCache[cacheKey]?.second ?: emptyList()
                } else {
                    val converted = usdSeries.map { it.copy(price = it.price * fx) }
                    trimByTimeframe(converted, timeframe)
                }
            }
        }

        historyCache[cacheKey] = System.currentTimeMillis() to result
        return result
    }

    private suspend fun fxRate(currency: String): Double? {
        if (currency == "USD") return 1.0
        val fx = retryWithBackoff {
            api.getFxRates("https://api.frankfurter.app/latest?from=USD&to=$currency")
        }
        return fx?.rates?.get(currency)
    }

    private suspend fun fetchStooqDailySeries(currency: String): List<PricePoint> {
        val symbol = "xau$currency"
        val csv = api.getCsv("https://stooq.com/q/d/l/?s=$symbol&i=d").string()
        val rows = csv.lineSequence().drop(1).filter { it.isNotBlank() }

        return rows.mapNotNull { row ->
            val cols = row.split(',')
            if (cols.size < 5) return@mapNotNull null
            val date = runCatching { LocalDate.parse(cols[0]) }.getOrNull() ?: return@mapNotNull null
            val close = cols[4].toDoubleOrNull() ?: return@mapNotNull null
            PricePoint(
                price = close,
                timestamp = date.atStartOfDay().toEpochSecond(ZoneOffset.UTC)
            )
        }.toList().sortedBy { it.timestamp }
    }

    private suspend fun <T> retryWithBackoff(
        attempts: Int = 3,
        initialDelayMs: Long = 250,
        maxDelayMs: Long = 1800,
        block: suspend () -> T
    ): T? {
        var delayMs = initialDelayMs
        repeat(attempts) { index ->
            runCatching { return block() }
            if (index < attempts - 1) {
                delay(delayMs)
                delayMs = (delayMs * 2).coerceAtMost(maxDelayMs)
            }
        }
        return null
    }

    private fun trimByTimeframe(history: List<PricePoint>, timeframe: Timeframe): List<PricePoint> {
        if (timeframe == Timeframe.MAX) return history
        val now = System.currentTimeMillis() / 1000
        val windowSeconds = when (timeframe) {
            Timeframe.DAY_1 -> 24L * 60 * 60
            Timeframe.WEEK_1 -> 7L * 24 * 60 * 60
            Timeframe.MONTH_1 -> 30L * 24 * 60 * 60
            Timeframe.MONTH_3 -> 90L * 24 * 60 * 60
            Timeframe.MONTH_6 -> 180L * 24 * 60 * 60
            Timeframe.YEAR_1 -> 365L * 24 * 60 * 60
            Timeframe.YEAR_5 -> 365L * 24 * 60 * 60 * 5
            Timeframe.MAX -> Long.MAX_VALUE
        }
        val filtered = history.filter { it.timestamp >= (now - windowSeconds) }
        return when {
            timeframe == Timeframe.DAY_1 -> filtered
            filtered.isNotEmpty() -> filtered
            else -> history.takeLast(30)
        }
    }
}
