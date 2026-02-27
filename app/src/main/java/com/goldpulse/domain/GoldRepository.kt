package com.goldpulse.domain

import com.goldpulse.data.model.PricePoint
import com.goldpulse.ui.components.Timeframe

interface GoldRepository {
    suspend fun fetchCurrentPrice(currency: String): PricePoint
    suspend fun fetchHistoricalPrices(currency: String, timeframe: Timeframe): List<PricePoint>
}
