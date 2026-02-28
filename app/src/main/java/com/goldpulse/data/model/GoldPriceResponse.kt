package com.goldpulse.data.model

import com.google.gson.annotations.SerializedName

data class GoldPriceResponse(
    @SerializedName("name") val name: String? = null,
    @SerializedName("symbol") val symbol: String? = null,
    @SerializedName("price") val price: Double,
    @SerializedName("bid") val bid: Double? = null,
    @SerializedName("ask") val ask: Double? = null,
    @SerializedName("timestamp") val timestamp: Long? = null,
    @SerializedName("updatedAt") val updatedAt: String? = null
)

data class FxRateResponse(
    @SerializedName("rates") val rates: Map<String, Double> = emptyMap()
)

data class PricePoint(
    val price: Double,
    val timestamp: Long,
    val sourceLabel: String? = "gold-api.com",
    val priceType: String? = "last"
)
