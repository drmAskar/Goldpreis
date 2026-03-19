package com.goldpulse.data.network

import com.goldpulse.data.model.IndicatorData
import com.goldpulse.data.model.IndicatorPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * API service for fetching macroeconomic indicators.
 * 
 * Data Sources:
 * - DXY: Yahoo Finance (DXY symbol) or FRED (DTWEXBGS)
 * - US10Y: Yahoo Finance (^TNX) or FRED (DGS10)
 * - Breakeven10Y: FRED (T10YIE) - primary source
 * - VIX: Yahoo Finance (^VIX) - risk proxy
 * 
 * Fallback Chain:
 * 1. Primary: FRED API (Federal Reserve Economic Data)
 * 2. Fallback: Yahoo Finance (via CSV download)
 */
class IndicatorApiService {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    
    /**
     * Fetch DXY (US Dollar Index) data.
     * Primary: FRED DTWEXBGS
     * Fallback: Yahoo Finance DXY
     */
    suspend fun fetchDXY(): IndicatorData? = withContext(Dispatchers.IO) {
        fetchFromFred("DTWEXBGS", "DXY", "Dollar Index")
            ?: fetchFromYahooFinance("DX-Y.NYB", "DXY", "Dollar Index")
    }
    
    /**
     * Fetch US10Y (10-Year Treasury Yield) data.
     * Primary: FRED DGS10
     * Fallback: Yahoo Finance ^TNX
     */
    suspend fun fetchUS10Y(): IndicatorData? = withContext(Dispatchers.IO) {
        fetchFromFred("DGS10", "US10Y", "10-Year Treasury Yield")
            ?: fetchFromYahooFinance("^TNX", "US10Y", "10-Year Treasury Yield")
    }
    
    /**
     * Fetch Breakeven10Y (10-Year Breakeven Inflation Rate).
     * Primary: FRED T10YIE
     * Fallback: Calculated from TIPS vs nominal spread
     */
    suspend fun fetchBreakeven10Y(): IndicatorData? = withContext(Dispatchers.IO) {
        fetchFromFred("T10YIE", "Breakeven10Y", "10-Year Breakeven Inflation Rate")
            ?: calculateBreakevenFromTIPS()
    }
    
    /**
     * Fetch VIX (CBOE Volatility Index) as risk proxy.
     * Primary: Yahoo Finance ^VIX
     */
    suspend fun fetchVIX(): IndicatorData? = withContext(Dispatchers.IO) {
        fetchFromYahooFinance("^VIX", "VIX", "CBOE Volatility Index")
    }
    
    /**
     * Fetch all indicators required for prediction.
     */
    suspend fun fetchAllIndicators(): List<IndicatorData> = withContext(Dispatchers.IO) {
        val indicators = mutableListOf<IndicatorData?>()
        
        // Fetch in parallel-like sequence with error handling
        indicators.add(fetchDXY())
        indicators.add(fetchUS10Y())
        indicators.add(fetchBreakeven10Y())
        indicators.add(fetchVIX())
        
        indicators.filterNotNull()
    }
    
    /**
     * Fetch indicator data from FRED API.
     * FRED provides free economic data via their API.
     */
    private fun fetchFromFred(seriesId: String, symbol: String, name: String): IndicatorData? {
        return try {
            // FRED API endpoint (uses public JSON endpoint, no API key needed for basic access)
            val url = "https://fred.stlouisfed.org/graph/fredgraph.csv?id=$seriesId&cosd=2024-01-01"
            
            val request = Request.Builder()
                .url(url)
                .build()
            
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return null
            
            val csv = response.body?.string() ?: return null
            val lines = csv.lines().filter { it.isNotBlank() }
            
            // Parse CSV: header is "DATE,VALUE"
            val dataPoints = lines.drop(1).mapNotNull { line ->
                val parts = line.split(",")
                if (parts.size >= 2) {
                    val value = parts[1].toDoubleOrNull()
                    if (value != null && !value.isNaN()) value else null
                } else null
            }
            
            if (dataPoints.size < 2) return null
            
            val currentValue = dataPoints.last()
            val previousValue = dataPoints[dataPoints.size - 2]
            val changePercent = if (previousValue != 0.0) {
                ((currentValue - previousValue) / abs(previousValue)) * 100.0
            } else 0.0
            
            IndicatorData(
                symbol = symbol,
                name = name,
                value = currentValue,
                changePercent = changePercent,
                timestamp = System.currentTimeMillis() / 1000,
                source = "FRED"
            )
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Fetch indicator data from Yahoo Finance.
     * Uses Yahoo Finance CSV download endpoint.
     */
    private fun fetchFromYahooFinance(symbol: String, outputSymbol: String, name: String): IndicatorData? {
        return try {
            // Yahoo Finance download endpoint
            val url = "https://query1.finance.yahoo.com/v7/finance/download/$symbol?period1=${System.currentTimeMillis() / 1000 - 30 * 24 * 60 * 60}&period2=${System.currentTimeMillis() / 1000}&interval=1d&events=history"
            
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()
            
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return null
            
            val csv = response.body?.string() ?: return null
            val lines = csv.lines().filter { it.isNotBlank() }
            
            // Parse Yahoo CSV: Date,Open,High,Low,Close,Adj Close,Volume
            val dataPoints = lines.drop(1).mapNotNull { line ->
                val parts = line.split(",")
                if (parts.size >= 5) {
                    val close = parts[4].toDoubleOrNull()
                    if (close != null && !close.isNaN()) close else null
                } else null
            }.reversed() // Yahoo returns newest first after reversal
            
            if (dataPoints.size < 2) return null
            
            val currentValue = dataPoints.first()
            val previousValue = dataPoints[1]
            val changePercent = if (previousValue != 0.0) {
                ((currentValue - previousValue) / abs(previousValue)) * 100.0
            } else 0.0
            
            IndicatorData(
                symbol = outputSymbol,
                name = name,
                value = currentValue,
                changePercent = changePercent,
                timestamp = System.currentTimeMillis() / 1000,
                source = "Yahoo Finance"
            )
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Calculate breakeven inflation from TIPS vs nominal yield spread.
     * Fallback when direct breakeven data is unavailable.
     */
    private suspend fun calculateBreakevenFromTIPS(): IndicatorData? = withContext(Dispatchers.IO) {
        try {
            // Fetch both nominal 10Y and TIPS yield
            val nominal = fetchFromFred("DGS10", "DGS10", "10-Year Nominal")
                ?: fetchFromYahooFinance("^TNX", "DGS10", "10-Year Nominal")
            
            val tips = fetchFromFred("DFII10", "DFII10", "10-Year TIPS")
                ?: fetchFromYahooFinance("^FVX", "DFII10", "10-Year TIPS")
            
            if (nominal != null && tips != null) {
                val breakeven = nominal.value - tips.value
                val changePercent = nominal.changePercent - tips.changePercent
                
                IndicatorData(
                    symbol = "Breakeven10Y",
                    name = "10-Year Breakeven Inflation Rate",
                    value = breakeven,
                    changePercent = changePercent,
                    timestamp = System.currentTimeMillis() / 1000,
                    source = "Calculated (Nominal - TIPS)"
                )
            } else null
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Fetch historical indicator data for charting.
     */
    suspend fun fetchIndicatorHistory(symbol: String, days: Int = 90): List<IndicatorPoint> = withContext(Dispatchers.IO) {
        val fredId = when (symbol) {
            "DXY" -> "DTWEXBGS"
            "US10Y" -> "DGS10"
            "Breakeven10Y" -> "T10YIE"
            "VIX" -> "VIXCLS"
            else -> return@withContext emptyList()
        }
        
        try {
            val url = "https://fred.stlouisfed.org/graph/fredgraph.csv?id=$fredId&cosd=2024-01-01"
            
            val request = Request.Builder()
                .url(url)
                .build()
            
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext emptyList()
            
            val csv = response.body?.string() ?: return@withContext emptyList()
            val lines = csv.lines().filter { it.isNotBlank() }
            
            lines.drop(1).mapNotNull { line ->
                val parts = line.split(",")
                if (parts.size >= 2) {
                    val dateStr = parts[0]
                    val value = parts[1].toDoubleOrNull()
                    if (value != null && !value.isNaN()) {
                        // Parse date to timestamp
                        val timestamp = runCatching {
                            java.time.LocalDate.parse(dateStr)
                                .atStartOfDay()
                                .toEpochSecond(java.time.ZoneOffset.UTC)
                        }.getOrNull() ?: 0L
                        
                        IndicatorPoint(timestamp, value)
                    } else null
                } else null
            }.takeLast(days)
        } catch (e: Exception) {
            emptyList()
        }
    }
}
