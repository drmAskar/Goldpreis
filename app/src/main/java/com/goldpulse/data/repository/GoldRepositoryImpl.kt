package com.goldpulse.data.repository

import com.goldpulse.data.model.PricePoint
import com.goldpulse.data.network.GoldApiService
import com.goldpulse.domain.GoldRepository
import com.goldpulse.ui.components.Timeframe
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs

class GoldRepositoryImpl(
    private val api: GoldApiService
) : GoldRepository {

    private val currentCache = ConcurrentHashMap<String, Pair<Long, PricePoint>>()
    private val historyCache = ConcurrentHashMap<String, Pair<Long, List<PricePoint>>>()
    private val dailySeriesCache = ConcurrentHashMap<String, Pair<Long, List<PricePoint>>>()
    private val lastNetworkCallAtMs = AtomicLong(0L)

    // Cache duration constants - optimized for near real-time updates
    companion object {
        private const val CURRENT_PRICE_CACHE_MS = 30_000L // 30 seconds
        private const val HISTORY_CACHE_MS = 120_000L // 2 minutes for longer timeframes
        private const val DAILY_SERIES_TTL_MS = 45_000L // 45 seconds for daily series freshness (target <=60s)
        private const val MAX_RETRIES = 3
        private const val INITIAL_DELAY_MS = 250L
        private const val MAX_DELAY_MS = 1800L
        private const val MIN_NETWORK_GAP_MS = 800L
    }

    override suspend fun fetchCurrentPrice(currency: String): PricePoint {
        val normalizedCurrency = validateCurrency(currency)

        currentCache[normalizedCurrency]?.let { (ts, point) ->
            if (System.currentTimeMillis() - ts < CURRENT_PRICE_CACHE_MS) return point
        }

        val value = retryWithBackoff {
            val response = api.getGoldPrice()
            val baseUsd = response.bid?.let { bid ->
                response.ask?.let { ask ->
                    (bid + ask) / 2.0
                }
            } ?: response.price

            val priceType = if (response.bid != null && response.ask != null) "midpoint" else "last"

            val finalPrice = if (normalizedCurrency == "USD") {
                baseUsd
            } else {
                val fx = fxRate(normalizedCurrency) ?: throw IllegalStateException("FX rate unavailable for $normalizedCurrency")
                baseUsd * fx
            }

            PricePoint(
                price = finalPrice,
                timestamp = response.timestamp ?: (System.currentTimeMillis() / 1000),
                sourceLabel = if (normalizedCurrency == "USD") "gold-api.com" else "gold-api.com + frankfurter.app",
                priceType = priceType
            )
        }

        val result = if (value != null) {
            value
        } else {
            // Fallback: fetch from stooq
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
        val normalizedCurrency = validateCurrency(currency)

        val cacheKey = "$normalizedCurrency:${timeframe.name}"
        
        // Use shorter TTL for daily timeframe to ensure fresh data (target <=60s refresh)
        val effectiveTtl = if (timeframe == Timeframe.DAY_1) DAILY_SERIES_TTL_MS else HISTORY_CACHE_MS
        historyCache[cacheKey]?.let { (ts, points) ->
            if (System.currentTimeMillis() - ts < effectiveTtl) return points
        }

        // FIXED: Fetch daily data consistently - stooq provides daily close prices
        // Use proper fallback chain: try direct currency first, then USD with conversion
        val directSeries = retryWithBackoff { fetchStooqDailySeries(normalizedCurrency) }.orEmpty()

        val result = if (directSeries.isNotEmpty()) {
            trimByTimeframe(directSeries, timeframe)
        } else {
            // Fallback: try USD and convert if needed
            val usdSeries = fetchStooqDailySeries("USD")
            if (usdSeries.isEmpty()) {
                emptyList()
            } else if (normalizedCurrency == "USD") {
                trimByTimeframe(usdSeries, timeframe)
            } else {
                val fx = fxRate(normalizedCurrency)
                if (fx == null) {
                    // No FX rate available - return empty or cached
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
        val fx = retryWithBackoff { api.getFxRates("https://api.frankfurter.app/latest?from=USD&to=$currency") }
        return fx?.rates?.get(currency)
    }

    private suspend fun fetchStooqDailySeries(currency: String): List<PricePoint> {
        // Check daily series cache for faster repeated access
        dailySeriesCache[currency]?.let { (ts, points) ->
            if (System.currentTimeMillis() - ts < DAILY_SERIES_TTL_MS) return points
        }
        
        val symbol = "xau$currency"
        val csv = api.getCsv("https://stooq.com/q/d/l/?s=$symbol&i=d").string()
        val rows = csv.lineSequence()
            .drop(1) // Skip header
            .filter { it.isNotBlank() }

        val result = rows.mapNotNull { row ->
            val cols = row.split(',')
            if (cols.size < 5) return@mapNotNull null
            val date = runCatching { LocalDate.parse(cols[0]) }.getOrNull() ?: return@mapNotNull null
            val close = cols[4].toDoubleOrNull() ?: return@mapNotNull null
            PricePoint(
                price = close,
                timestamp = date.atStartOfDay().toEpochSecond(ZoneOffset.UTC),
                sourceLabel = "stooq.com",
                priceType = "last"
            )
        }.toList().sortedBy { it.timestamp }
        
        // Cache the daily series for near real-time access
        dailySeriesCache[currency] = System.currentTimeMillis() to result
        return result
    }

    private fun validateCurrency(currency: String): String {
        val normalized = currency.trim().uppercase()
        require(normalized.matches(Regex("^[A-Z]{3}$"))) { "Invalid currency code" }
        return normalized
    }

    private suspend fun throttleNetworkCalls() {
        val now = System.currentTimeMillis()
        val last = lastNetworkCallAtMs.get()
        val waitMs = (MIN_NETWORK_GAP_MS - (now - last)).coerceAtLeast(0L)
        if (waitMs > 0) delay(waitMs)
        lastNetworkCallAtMs.set(System.currentTimeMillis())
    }

    private suspend fun <T> retryWithBackoff(
        attempts: Int = MAX_RETRIES,
        initialDelayMs: Long = INITIAL_DELAY_MS,
        maxDelayMs: Long = MAX_DELAY_MS,
        block: suspend () -> T
    ): T? {
        var delayMs = initialDelayMs
        repeat(attempts) { index ->
            throttleNetworkCalls()
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
