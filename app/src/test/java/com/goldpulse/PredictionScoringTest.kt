package com.goldpulse

import com.goldpulse.data.model.FactorContribution
import com.goldpulse.data.model.GoldPrediction
import com.goldpulse.data.model.IndicatorData
import com.goldpulse.data.model.PredictionBadge
import com.goldpulse.data.model.PricePoint
import com.goldpulse.domain.PredictionEngine
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

/**
 * Unit tests for the Gold Prediction Scoring Logic.
 * 
 * Tests verify:
 * - Factor weight calculations
 * - Score normalization
 * - Badge determination thresholds
 * - Forecast band calculations
 * - Edge cases and boundary conditions
 */
class PredictionScoringTest {
    
    private val predictionEngine = PredictionEngine()
    
    /**
     * Test: Verify all factor weights sum to 1.0
     */
    @Test
    fun `factor weights sum to 1`() {
        val totalWeight = PredictionEngine.WEIGHT_DXY + 
                          PredictionEngine.WEIGHT_US10Y + 
                          PredictionEngine.WEIGHT_BREAKEVEN + 
                          PredictionEngine.WEIGHT_VIX
        
        assertEquals("Factor weights must sum to 1.0", 1.0, totalWeight, 0.0001)
    }
    
    /**
     * Test: DXY inverse correlation - falling DXY should produce positive score
     */
    @Test
    fun `falling DXY produces bullish contribution`() {
        val indicators = listOf(
            IndicatorData(
                symbol = "DXY",
                name = "Dollar Index",
                value = 104.0,
                changePercent = -0.5, // Falling dollar
                timestamp = System.currentTimeMillis() / 1000,
                source = "FRED"
            )
        )
        
        val prediction = predictionEngine.calculatePrediction(indicators)
        val dxyFactor = prediction.factors.find { it.name.contains("DXY") }
        
        assertNotNull("DXY factor should exist", dxyFactor)
        assertTrue("Falling DXY should produce positive raw score", dxyFactor!!.rawScore > 0)
        assertEquals("bullish", dxyFactor.direction)
    }
    
    /**
     * Test: US10Y inverse correlation - rising yields should produce negative score
     */
    @Test
    fun `rising yields produce bearish contribution`() {
        val indicators = listOf(
            IndicatorData(
                symbol = "US10Y",
                name = "10-Year Treasury Yield",
                value = 4.5,
                changePercent = 0.3, // Rising yields
                timestamp = System.currentTimeMillis() / 1000,
                source = "FRED"
            )
        )
        
        val prediction = predictionEngine.calculatePrediction(indicators)
        val us10yFactor = prediction.factors.find { it.name.contains("US10Y") }
        
        assertNotNull("US10Y factor should exist", us10yFactor)
        assertTrue("Rising yields should produce negative raw score", us10yFactor!!.rawScore < 0)
        assertEquals("bearish", us10yFactor.direction)
    }
    
    /**
     * Test: Breakeven positive correlation - rising inflation expectations boost gold
     */
    @Test
    fun `rising breakeven produces bullish contribution`() {
        val indicators = listOf(
            IndicatorData(
                symbol = "Breakeven10Y",
                name = "10-Year Breakeven Inflation Rate",
                value = 2.5,
                changePercent = 0.2, // Rising inflation expectations
                timestamp = System.currentTimeMillis() / 1000,
                source = "FRED"
            )
        )
        
        val prediction = predictionEngine.calculatePrediction(indicators)
        val breakevenFactor = prediction.factors.find { it.name.contains("Breakeven") }
        
        assertNotNull("Breakeven factor should exist", breakevenFactor)
        assertTrue("Rising breakeven should produce positive raw score", breakevenFactor!!.rawScore > 0)
        assertEquals("bullish", breakevenFactor.direction)
    }
    
    /**
     * Test: VIX positive correlation - rising fear boosts gold
     */
    @Test
    fun `rising VIX produces bullish contribution`() {
        val indicators = listOf(
            IndicatorData(
                symbol = "VIX",
                name = "CBOE Volatility Index",
                value = 20.0,
                changePercent = 1.0, // Rising fear
                timestamp = System.currentTimeMillis() / 1000,
                source = "Yahoo Finance"
            )
        )
        
        val prediction = predictionEngine.calculatePrediction(indicators)
        val vixFactor = prediction.factors.find { it.name.contains("VIX") }
        
        assertNotNull("VIX factor should exist", vixFactor)
        assertTrue("Rising VIX should produce positive raw score", vixFactor!!.rawScore > 0)
        assertEquals("bullish", vixFactor.direction)
    }
    
    /**
     * Test: Bullish badge when score > 0.5
     */
    @Test
    fun `bullish badge for high positive score`() {
        val indicators = listOf(
            IndicatorData("DXY", "Dollar Index", 104.0, -2.0, 0, "FRED"),
            IndicatorData("US10Y", "10Y Yield", 4.5, -1.0, 0, "FRED"),
            IndicatorData("Breakeven10Y", "Breakeven", 2.5, 1.0, 0, "FRED"),
            IndicatorData("VIX", "VIX", 20.0, 2.0, 0, "Yahoo")
        )
        
        val prediction = predictionEngine.calculatePrediction(indicators)
        
        assertTrue("Score should be > 0.5 for bullish", prediction.score > PredictionEngine.THRESHOLD_BULLISH)
        assertEquals("Badge should be BULLISH", PredictionBadge.BULLISH, prediction.badge)
    }
    
    /**
     * Test: Bearish badge when score < -0.5
     */
    @Test
    fun `bearish badge for high negative score`() {
        val indicators = listOf(
            IndicatorData("DXY", "Dollar Index", 104.0, 2.0, 0, "FRED"),
            IndicatorData("US10Y", "10Y Yield", 4.5, 1.5, 0, "FRED"),
            IndicatorData("Breakeven10Y", "Breakeven", 2.5, -1.0, 0, "FRED"),
            IndicatorData("VIX", "VIX", 20.0, -2.0, 0, "Yahoo")
        )
        
        val prediction = predictionEngine.calculatePrediction(indicators)
        
        assertTrue("Score should be < -0.5 for bearish", prediction.score < PredictionEngine.THRESHOLD_BEARISH)
        assertEquals("Badge should be BEARISH", PredictionBadge.BEARISH, prediction.badge)
    }
    
    /**
     * Test: Neutral badge for score between -0.5 and 0.5
     */
    @Test
    fun `neutral badge for score near zero`() {
        val indicators = listOf(
            IndicatorData("DXY", "Dollar Index", 104.0, 0.1, 0, "FRED"),
            IndicatorData("US10Y", "10Y Yield", 4.5, -0.1, 0, "FRED"),
            IndicatorData("Breakeven10Y", "Breakeven", 2.5, 0.1, 0, "FRED"),
            IndicatorData("VIX", "VIX", 20.0, -0.1, 0, "Yahoo")
        )
        
        val prediction = predictionEngine.calculatePrediction(indicators)
        
        assertTrue("Score should be between -0.5 and 0.5 for neutral", 
            prediction.score >= PredictionEngine.THRESHOLD_BEARISH && 
            prediction.score <= PredictionEngine.THRESHOLD_BULLISH)
        assertEquals("Badge should be NEUTRAL", PredictionBadge.NEUTRAL, prediction.badge)
    }
    
    /**
     * Test: Weighted score calculation is correct
     */
    @Test
    fun `weighted score calculation is accurate`() {
        val indicators = listOf(
            IndicatorData("DXY", "Dollar Index", 104.0, -1.0, 0, "FRED")
        )
        
        val prediction = predictionEngine.calculatePrediction(indicators)
        val dxyFactor = prediction.factors.find { it.name.contains("DXY") }
        
        assertNotNull("DXY factor should exist", dxyFactor)
        
        // Expected: rawScore = -0.5 * (-1.0) = 0.5
        // weightedScore = 0.35 * 0.5 = 0.175
        val expectedRawScore = PredictionEngine.MULTIPLIER_DXY * (-1.0)
        val expectedWeightedScore = PredictionEngine.WEIGHT_DXY * expectedRawScore
        
        assertEquals("Raw score should match formula", expectedRawScore, dxyFactor!!.rawScore, 0.0001)
        assertEquals("Weighted score should match formula", expectedWeightedScore, dxyFactor.weightedScore, 0.0001)
    }
    
    /**
     * Test: Total score is sum of weighted scores
     */
    @Test
    fun `total score equals sum of weighted scores`() {
        val indicators = listOf(
            IndicatorData("DXY", "Dollar Index", 104.0, -1.0, 0, "FRED"),
            IndicatorData("US10Y", "10Y Yield", 4.5, -0.5, 0, "FRED"),
            IndicatorData("Breakeven10Y", "Breakeven", 2.5, 0.5, 0, "FRED"),
            IndicatorData("VIX", "VIX", 20.0, 1.0, 0, "Yahoo")
        )
        
        val prediction = predictionEngine.calculatePrediction(indicators)
        
        val sumOfWeightedScores = prediction.factors.sumOf { it.weightedScore }
        
        assertEquals("Total score should equal sum of weighted scores", 
            sumOfWeightedScores, prediction.score, 0.0001)
    }
    
    /**
     * Test: Empty indicators list produces zero score
     */
    @Test
    fun `empty indicators produces zero score`() {
        val prediction = predictionEngine.calculatePrediction(emptyList())
        
        assertEquals("Empty indicators should produce zero score", 0.0, prediction.score, 0.0001)
        assertEquals("Empty indicators should produce NEUTRAL badge", PredictionBadge.NEUTRAL, prediction.badge)
        assertTrue("Empty indicators should produce empty factors list", prediction.factors.isEmpty())
    }
    
    /**
     * Test: Forecast band calculation with sufficient history
     */
    @Test
    fun `forecast band calculated with sufficient history`() {
        val history = (1..50).map { i ->
            PricePoint(
                price = 2000.0 + (i % 10), // Prices between 2000-2009
                timestamp = System.currentTimeMillis() / 1000 - (50 - i) * 86400,
                sourceLabel = "test",
                priceType = "last"
            )
        }
        
        val prediction = predictionEngine.calculatePrediction(emptyList(), history)
        
        assertTrue("Forecast MA should be > 0", prediction.forecastMA > 0)
        assertTrue("Forecast Std should be >= 0", prediction.forecastStd >= 0)
        assertTrue("Upper band should be >= lower band", 
            prediction.forecastBandUpper >= prediction.forecastBandLower)
        
        // Verify band relationship
        assertEquals("Upper band should be MA + Std", 
            prediction.forecastMA + prediction.forecastStd, 
            prediction.forecastBandUpper, 
            0.0001)
        assertEquals("Lower band should be MA - Std", 
            prediction.forecastMA - prediction.forecastStd, 
            prediction.forecastBandLower, 
            0.0001)
    }
    
    /**
     * Test: Forecast band returns zeros for insufficient history
     */
    @Test
    fun `forecast band zeros for insufficient history`() {
        val shortHistory = (1..20).map { i ->
            PricePoint(
                price = 2000.0,
                timestamp = System.currentTimeMillis() / 1000 - i * 86400,
                sourceLabel = "test",
                priceType = "last"
            )
        }
        
        val prediction = predictionEngine.calculatePrediction(emptyList(), shortHistory)
        
        assertEquals("Forecast MA should be 0 for insufficient data", 0.0, prediction.forecastMA, 0.0001)
        assertEquals("Forecast Std should be 0 for insufficient data", 0.0, prediction.forecastStd, 0.0001)
    }
    
    /**
     * Test: Minimum forecast points constant is valid
     */
    @Test
    fun `minimum forecast points is_valid`() {
        assertTrue("MIN_FORECAST_POINTS should be positive", PredictionEngine.MIN_FORECAST_POINTS > 0)
        assertTrue("MIN_FORECAST_POINTS should be >= 30 for chart requirement", 
            PredictionEngine.MIN_FORECAST_POINTS >= 30)
    }
    
    /**
     * Test: Threshold constants are valid
     */
    @Test
    fun `threshold constants are valid`() {
        assertTrue("BULLISH threshold should be positive", PredictionEngine.THRESHOLD_BULLISH > 0)
        assertTrue("BEARISH threshold should be negative", PredictionEngine.THRESHOLD_BEARISH < 0)
        assertTrue("BULLISH threshold should be > BEARISH threshold", 
            PredictionEngine.THRESHOLD_BULLISH > PredictionEngine.THRESHOLD_BEARISH)
    }
    
    /**
     * Test: Multiplier signs match correlation expectations
     */
    @Test
    fun `multipliers match correlation expectations`() {
        // Inverse correlation multipliers should be negative
        assertTrue("DXY multiplier should be negative (inverse correlation)", 
            PredictionEngine.MULTIPLIER_DXY < 0)
        assertTrue("US10Y multiplier should be negative (inverse correlation)", 
            PredictionEngine.MULTIPLIER_US10Y < 0)
        
        // Positive correlation multipliers should be positive
        assertTrue("Breakeven multiplier should be positive (positive correlation)", 
            PredictionEngine.MULTIPLIER_BREAKEVEN > 0)
        assertTrue("VIX multiplier should be positive (positive correlation)", 
            PredictionEngine.MULTIPLIER_VIX > 0)
    }
    
    /**
     * Test: Arabic badge labels are correct
     */
    @Test
    fun `arabic badge labels are correct`() {
        val bullishIndicators = listOf(
            IndicatorData("DXY", "Dollar Index", 104.0, -5.0, 0, "FRED")
        )
        val bearishIndicators = listOf(
            IndicatorData("DXY", "Dollar Index", 104.0, 5.0, 0, "FRED")
        )
        val neutralIndicators = listOf(
            IndicatorData("DXY", "Dollar Index", 104.0, 0.0, 0, "FRED")
        )
        
        val bullishPrediction = predictionEngine.calculatePrediction(bullishIndicators)
        val bearishPrediction = predictionEngine.calculatePrediction(bearishIndicators)
        val neutralPrediction = predictionEngine.calculatePrediction(neutralIndicators)
        
        assertEquals("صاعد", bullishPrediction.badgeAr)
        assertEquals("هابط", bearishPrediction.badgeAr)
        assertEquals("محايد", neutralPrediction.badgeAr)
    }
}
