package com.goldpulse.domain

import com.goldpulse.data.model.PricePoint

interface GoldRepository {
    suspend fun fetchCurrentPrice(currency: String): PricePoint
}
