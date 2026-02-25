package com.goldpulse.data.repository

import com.goldpulse.data.model.PricePoint
import com.goldpulse.data.network.GoldApiService
import com.goldpulse.domain.GoldRepository

class GoldRepositoryImpl(
    private val api: GoldApiService
) : GoldRepository {
    override suspend fun fetchCurrentPrice(currency: String): PricePoint {
        val response = api.getGoldPrice(currency = currency)
        return PricePoint(
            price = response.price,
            timestamp = response.timestamp ?: (System.currentTimeMillis() / 1000)
        )
    }
}
