package com.goldpulse.data.network

import com.goldpulse.data.model.FxRateResponse
import com.goldpulse.data.model.GoldPriceResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Url

interface GoldApiService {
    @GET("price/{metal}")
    suspend fun getGoldPrice(
        @Path("metal") metal: String = "XAU"
    ): GoldPriceResponse

    @GET
    suspend fun getFxRates(
        @Url url: String
    ): FxRateResponse
}
