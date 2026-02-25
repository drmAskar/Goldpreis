package com.goldpulse.data.model

import com.google.gson.annotations.SerializedName

data class GoldPriceResponse(
    @SerializedName("metal") val metal: String? = null,
    @SerializedName("currency") val currency: String? = null,
    @SerializedName("price") val price: Double,
    @SerializedName("timestamp") val timestamp: Long? = null,
    @SerializedName("updatedAt") val updatedAt: String? = null
)

data class PricePoint(
    val price: Double,
    val timestamp: Long
)
