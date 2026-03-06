package com.goldpulse.data.network

import com.goldpulse.BuildConfig
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

object NetworkModule {

    private const val BASE_URL = "https://api.gold-api.com/"

    private val allowedHosts = setOf(
        "api.gold-api.com",
        "api.frankfurter.app",
        "stooq.com"
    )

    companion object {
        private const val CONNECT_TIMEOUT_SECONDS = 15L
        private const val READ_TIMEOUT_SECONDS = 15L
        private const val CALL_TIMEOUT_SECONDS = 20L
        private const val CACHE_SIZE_BYTES = 15L * 1024 * 1024
        private const val IDLE_CONNECTION_SECONDS = 30L
    }

    private val logging by lazy {
        HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
        }
    }

    private fun requireAllowedHost(url: HttpUrl) {
        require(url.isHttps) { "Only HTTPS endpoints are allowed" }
        require(url.host in allowedHosts) { "Endpoint host is not allowed: ${url.host}" }
    }

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .connectionPool(
                ConnectionPool(
                    maxIdleConnections = 5,
                    keepAliveDuration = IDLE_CONNECTION_SECONDS,
                    timeUnit = TimeUnit.SECONDS
                )
            )
            .cache(Cache(File("/tmp/goldpulse-http-cache"), CACHE_SIZE_BYTES))
            .addInterceptor { chain ->
                val original = chain.request()
                requireAllowedHost(original.url)
                val request = original.newBuilder()
                    .header("Cache-Control", "public, max-age=60, stale-while-revalidate=120")
                    .header("X-Requested-With", "GoldPulse")
                    .build()
                chain.proceed(request)
            }
            .addNetworkInterceptor { chain ->
                val response = chain.proceed(chain.request())
                response.newBuilder()
                    .header("X-Content-Type-Options", "nosniff")
                    .header("Referrer-Policy", "no-referrer")
                    .build()
            }
            .addInterceptor(logging)
            .build()
    }

    val api: GoldApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GoldApiService::class.java)
    }
}
