package com.goldpulse.data.repository

import com.goldpulse.data.model.PricePoint
import com.goldpulse.data.network.GoldApiService
import com.goldpulse.domain.GoldRepository
import com.goldpulse.ui.components.Timeframe
import java.time.LocalDate
import java.time.ZoneOffset

class GoldRepositoryImpl(
    private val api: GoldApiService
) : GoldRepository {

    override suspend fun fetchCurrentPrice(currency: String): PricePoint {
        val normalizedCurrency = currency.uppercase()

        val primary = runCatching {
            val response = api.getGoldPrice()
            val usdPrice = response.price
            val finalPrice = if (normalizedCurrency == "USD") usdPrice else usdPrice * fxRate(normalizedCurrency)
            PricePoint(
                price = finalPrice,
                timestamp = response.timestamp ?: (System.currentTimeMillis() / 1000)
            )
        }

        return primary.getOrElse {
            val latestFromCsv = fetchStooqDailySeries(normalizedCurrency).lastOrNull()
                ?: throw it
            latestFromCsv
        }
    }

    override suspend fun fetchHistoricalPrices(currency: String, timeframe: Timeframe): List<PricePoint> {
        val normalizedCurrency = currency.uppercase()

        val directSeries = runCatching { fetchStooqDailySeries(normalizedCurrency) }.getOrNull().orEmpty()
        if (directSeries.isNotEmpty()) return trimByTimeframe(directSeries, timeframe)

        val usdSeries = fetchStooqDailySeries("USD")
        if (usdSeries.isEmpty()) return emptyList()
        val fx = if (normalizedCurrency == "USD") 1.0 else fxRate(normalizedCurrency)

        val converted = usdSeries.map { it.copy(price = it.price * fx) }
        return trimByTimeframe(converted, timeframe)
    }

    private suspend fun fxRate(currency: String): Double {
        val fx = api.getFxRates("https://api.frankfurter.app/latest?from=USD&to=$currency")
        return fx.rates[currency] ?: 1.0
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
        return history.filter { it.timestamp >= (now - windowSeconds) }
    }
}
