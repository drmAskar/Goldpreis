package com.goldpulse.data.repository

import com.goldpulse.data.model.PricePoint
import com.goldpulse.data.network.GoldApiService
import com.goldpulse.domain.GoldRepository

class GoldRepositoryImpl(
    private val api: GoldApiService
) : GoldRepository {
    override suspend fun fetchCurrentPrice(currency: String): PricePoint {
        val response = api.getGoldPrice()
        val usdPrice = response.price
        val normalizedCurrency = currency.uppercase()

        val finalPrice = if (normalizedCurrency == "USD") {
            usdPrice
        } else {
            val fx = api.getFxRates("https://api.frankfurter.app/latest?from=USD&to=$normalizedCurrency")
            val rate = fx.rates[normalizedCurrency] ?: 1.0
            usdPrice * rate
        }

        return PricePoint(
            price = finalPrice,
            timestamp = response.timestamp ?: (System.currentTimeMillis() / 1000)
        )
    }
}
