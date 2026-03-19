package com.goldpulse.data.model

/**
 * Data model for macroeconomic indicators used in gold price prediction.
 * 
 * Sources:
 * - DXY (US Dollar Index): Yahoo Finance / FRED
 * - US10Y (10-Year Treasury Yield): Yahoo Finance / FRED
 * - Breakeven10Y (10-Year Breakeven Inflation Rate): FRED
 * - VIX (CBOE Volatility Index): Yahoo Finance (risk proxy)
 */
data class IndicatorData(
    val symbol: String,
    val name: String,
    val value: Double,
    val changePercent: Double,
    val timestamp: Long,
    val source: String
)

/**
 * Historical indicator point for charting.
 */
data class IndicatorPoint(
    val timestamp: Long,
    val value: Double
)

/**
 * Factor contribution to the gold score.
 */
data class FactorContribution(
    val name: String,
    val nameAr: String,
    val weight: Double,
    val rawScore: Double,
    val weightedScore: Double,
    val direction: String, // "bullish", "bearish", "neutral"
    val directionAr: String
)

/**
 * Gold prediction result with score and breakdown.
 * 
 * Scoring Formula (Total Score = Σ (Factor Weight × Normalized Score)):
 * 
 * | Factor | Weight | Scoring Logic |
 * |--------|--------|---------------|
 * | DXY (Dollar Index) | 35% | Inverse correlation: Lower DXY → Higher gold price. Score = -0.5 × (DXY change %) |
 * | US10Y (10Y Yield) | 30% | Inverse correlation: Lower yields → Higher gold price. Score = -0.4 × (Yield change %) |
 * | Breakeven10Y (Inflation Expectation) | 20% | Positive correlation: Higher inflation expectations → Higher gold price. Score = +0.5 × (Breakeven change %) |
 * | VIX (Risk Proxy) | 15% | Positive correlation: Higher fear → Higher gold price. Score = +0.3 × (VIX change %) |
 * 
 * Final Score Interpretation:
 * - Score > 0.5: Bullish (green badge)
 * - Score -0.5 to 0.5: Neutral (yellow badge)
 * - Score < -0.5: Bearish (red badge)
 */
data class GoldPrediction(
    val score: Double,
    val badge: PredictionBadge,
    val badgeAr: String,
    val factors: List<FactorContribution>,
    val forecastBandUpper: Double,
    val forecastBandLower: Double,
    val forecastMA: Double,
    val forecastStd: Double,
    val timestamp: Long,
    val indicators: List<IndicatorData>
)

enum class PredictionBadge {
    BULLISH,
    NEUTRAL,
    BEARISH
}

/**
 * State for the prediction screen.
 */
data class PredictionState(
    val loading: Boolean = false,
    val prediction: GoldPrediction? = null,
    val goldHistory: List<PricePoint> = emptyList(),
    val error: String? = null,
    val lastUpdated: String = ""
)
