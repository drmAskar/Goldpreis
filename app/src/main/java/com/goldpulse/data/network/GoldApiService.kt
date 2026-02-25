package com.goldpulse.data.network

import com.goldpulse.data.model.GoldPriceResponse
import retrofit2.http.GET
import retrofit2.http.Path

interface GoldApiService {
    @GET("price/{metal}/{currency}")
    suspend fun getGoldPrice(
        @Path("metal") metal: String = "XAU",
        @Path("currency") currency: String
    ): GoldPriceResponse
}
