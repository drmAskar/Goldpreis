package com.goldpulse.domain

import com.goldpulse.data.model.*
import com.goldpulse.data.network.IndicatorApiService
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Gold Prediction Engine
 * 
 * Calculates a composite gold price prediction score based on macroeconomic indicators.
 * 
 * ## Scoring Formula
 * 
 * The gold score is calculated as a weighted sum of normalized factor scores:
 * 
 * ```
 * Gold Score = Σ (Factor Weight × Normalized Factor Score)
 * ```
 * 
 * ### Factors and Weights
 * 
 * | Factor | Weight | Correlation | Scoring Formula |
 * |--------|--------|-------------|-----------------|
 * | DXY (Dollar Index) | 35% | Inverse | Score = -0.5 × (% change) |
 * | US10Y (10Y Yield) | 30% | Inverse | Score = -0.4 × (% change) |
 * | Breakeven10Y (Inflation) | 20% | Positive | Score = +0.5 × (% change) |
 * | VIX (Risk Proxy) | 15% | Positive | Score = +0.3 × (% change) |
 * 
 * ### Score Interpretation
 * 
 * - **Bullish** (Score > 0.5): Conditions favor higher gold prices
 * - **Neutral** (-0.5 ≤ Score ≤ 0.5): Mixed signals, no clear direction
 * - **Bearish** (Score < -0.5): Conditions favor lower gold prices
 * 
 * ## Data Sources
 * 
 * - **DXY**: FRED (DTWEXBGS) or Yahoo Finance (DX-Y.NYB)
 * - **US10Y**: FRED (DGS10) or Yahoo Finance (^TNX)
 * - **Breakeven10Y**: FRED (T10YIE) or calculated from TIPS spread
 * - **VIX**: Yahoo Finance (^VIX) or FRED (VIXCLS)
 * 
 * ## Forecast Band
 * 
 * The forecast band is calculated using:
 * - Moving Average (MA) of recent gold prices
 * - Standard Deviation (σ) of recent prices
 * - Upper Band = MA + σ
 * - Lower Band = MA - σ
 */
class PredictionEngine(
    private val indicatorApi: IndicatorApiService = IndicatorApiService()
) {
    // Factor weights (must sum to 1.0)
    companion object {
        const val WEIGHT_DXY = 0.35
        const val WEIGHT_US10Y = 0.30
        const val WEIGHT_BREAKEVEN = 0.20
        const val WEIGHT_VIX = 0.15
        
        // Scoring multipliers
        const val MULTIPLIER_DXY = -0.5      // Inverse correlation
        const val MULTIPLIER_US10Y = -0.4    // Inverse correlation
        const val MULTIPLIER_BREAKEVEN = 0.5 // Positive correlation
        const val MULTIPLIER_VIX = 0.3       // Positive correlation
        
        // Score thresholds for badges
        const val THRESHOLD_BULLISH = 0.5
        const val THRESHOLD_BEARISH = -0.5
        
        // Minimum data points for forecast
        const val MIN_FORECAST_POINTS = 30
    }
    
    /**
     * Calculate gold prediction from current indicators.
     * 
     * @param indicators List of indicator data
     * @param goldHistory Historical gold prices for forecast calculation
     * @return GoldPrediction with score, badge, and factor breakdown
     */
    fun calculatePrediction(
        indicators: List<IndicatorData>,
        goldHistory: List<PricePoint> = emptyList()
    ): GoldPrediction {
        val factors = mutableListOf<FactorContribution>()
        var totalScore = 0.0
        
        // Process DXY (Dollar Index)
        val dxy = indicators.find { it.symbol == "DXY" }
        if (dxy != null) {
            val rawScore = MULTIPLIER_DXY * dxy.changePercent
            val weightedScore = WEIGHT_DXY * rawScore
            totalScore += weightedScore
            
            factors.add(FactorContribution(
                name = "DXY (Dollar Index)",
                nameAr = "مؤشر الدولار (DXY)",
                weight = WEIGHT_DXY,
                rawScore = rawScore,
                weightedScore = weightedScore,
                direction = when {
                    rawScore > 0.1 -> "bullish"
                    rawScore < -0.1 -> "bearish"
                    else -> "neutral"
                },
                directionAr = when {
                    rawScore > 0.1 -> "صاعد"
                    rawScore < -0.1 -> "هابط"
                    else -> "محايد"
                }
            ))
        }
        
        // Process US10Y (10-Year Treasury Yield)
        val us10y = indicators.find { it.symbol == "US10Y" }
        if (us10y != null) {
            val rawScore = MULTIPLIER_US10Y * us10y.changePercent
            val weightedScore = WEIGHT_US10Y * rawScore
            totalScore += weightedScore
            
            factors.add(FactorContribution(
                name = "US10Y (10Y Yield)",
                nameAr = "عائد السندات 10 سنوات",
                weight = WEIGHT_US10Y,
                rawScore = rawScore,
                weightedScore = weightedScore,
                direction = when {
                    rawScore > 0.1 -> "bullish"
                    rawScore < -0.1 -> "bearish"
                    else -> "neutral"
                },
                directionAr = when {
                    rawScore > 0.1 -> "صاعد"
                    rawScore < -0.1 -> "هابط"
                    else -> "محايد"
                }
            ))
        }
        
        // Process Breakeven10Y (Inflation Expectations)
        val breakeven = indicators.find { it.symbol == "Breakeven10Y" }
        if (breakeven != null) {
            val rawScore = MULTIPLIER_BREAKEVEN * breakeven.changePercent
            val weightedScore = WEIGHT_BREAKEVEN * rawScore
            totalScore += weightedScore
            
            factors.add(FactorContribution(
                name = "Breakeven10Y (Inflation)",
                nameAr = "توقعات التضخم",
                weight = WEIGHT_BREAKEVEN,
                rawScore = rawScore,
                weightedScore = weightedScore,
                direction = when {
                    rawScore > 0.1 -> "bullish"
                    rawScore < -0.1 -> "bearish"
                    else -> "neutral"
                },
                directionAr = when {
                    rawScore > 0.1 -> "صاعد"
                    rawScore < -0.1 -> "هابط"
                    else -> "محايد"
                }
            ))
        }
        
        // Process VIX (Risk Proxy)
        val vix = indicators.find { it.symbol == "VIX" }
        if (vix != null) {
            val rawScore = MULTIPLIER_VIX * vix.changePercent
            val weightedScore = WEIGHT_VIX * rawScore
            totalScore += weightedScore
            
            factors.add(FactorContribution(
                name = "VIX (Risk Proxy)",
                nameAr = "مؤشر الخوف (VIX)",
                weight = WEIGHT_VIX,
                rawScore = rawScore,
                weightedScore = weightedScore,
                direction = when {
                    rawScore > 0.1 -> "bullish"
                    rawScore < -0.1 -> "bearish"
                    else -> "neutral"
                },
                directionAr = when {
                    rawScore > 0.1 -> "صاعد"
                    rawScore < -0.1 -> "هابط"
                    else -> "محايد"
                }
            ))
        }
        
        // Determine badge
        val badge = when {
            totalScore > THRESHOLD_BULLISH -> PredictionBadge.BULLISH
            totalScore < THRESHOLD_BEARISH -> PredictionBadge.BEARISH
            else -> PredictionBadge.NEUTRAL
        }
        
        val badgeAr = when (badge) {
            PredictionBadge.BULLISH -> "صاعد"
            PredictionBadge.NEUTRAL -> "محايد"
            PredictionBadge.BEARISH -> "هابط"
        }
        
        // Calculate forecast band from gold history
        val (forecastMA, forecastStd, upperBand, lowerBand) = calculateForecastBand(goldHistory)
        
        return GoldPrediction(
            score = totalScore,
            badge = badge,
            badgeAr = badgeAr,
            factors = factors,
            forecastBandUpper = upperBand,
            forecastBandLower = lowerBand,
            forecastMA = forecastMA,
            forecastStd = forecastStd,
            timestamp = System.currentTimeMillis() / 1000,
            indicators = indicators
        )
    }
    
    /**
     * Calculate forecast band using MA and standard deviation.
     * 
     * @param history Historical gold prices
     * @return Tuple of (MA, Std, Upper Band, Lower Band)
     */
    private fun calculateForecastBand(history: List<PricePoint>): ForecastBand {
        if (history.size < MIN_FORECAST_POINTS) {
            return ForecastBand(0.0, 0.0, 0.0, 0.0)
        }
        
        val prices = history.takeLast(MIN_FORECAST_POINTS).map { it.price }
        val ma = prices.average()
        
        val variance = prices.map { (it - ma) * (it - ma) }.average()
        val std = sqrt(variance)
        
        return ForecastBand(
            ma = ma,
            std = std,
            upperBand = ma + std,
            lowerBand = ma - std
        )
    }
    
    private data class ForecastBand(
        val ma: Double,
        val std: Double,
        val upperBand: Double,
        val lowerBand: Double
    )
    
    /**
     * Fetch all indicators and calculate prediction.
     */
    suspend fun fetchAndCalculate(goldHistory: List<PricePoint> = emptyList()): GoldPrediction {
        val indicators = indicatorApi.fetchAllIndicators()
        return calculatePrediction(indicators, goldHistory)
    }
}
