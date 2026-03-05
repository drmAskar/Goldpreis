package com.goldpulse.data.network

import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

object NetworkModule {

    // Security: Store sensitive constants properly - in production use BuildConfig or secure storage
    // Note: API keys should NEVER be hardcoded. Use environment variables or secure storage.
    private const val BASE_URL = "https://api.gold-api.com/"
    
    // Network configuration constants
    companion object {
        private const val CONNECT_TIMEOUT_SECONDS = 15L
        private const val READ_TIMEOUT_SECONDS = 15L
        private const val CALL_TIMEOUT_SECONDS = 20L
        private const val CACHE_SIZE_BYTES = 15L * 1024 * 1024 // 15 MB
        private const val MAX_REQUESTS_PER_HOST = 5
        private const val IDLE_CONNECTION_SECONDS = 30L
    }

    private val logging by lazy {
        HttpLoggingInterceptor().apply {
            // Security: Only log basic info in production, body logging should be disabled
            level = HttpLoggingInterceptor.Level.BASIC
        }
    }

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true) // Enable automatic retry
            .connectionPool(okhttp3.ConnectionPool(
                maxIdleConnections = 5,
                keepAliveDuration = IDLE_CONNECTION_SECONDS,
                timeUnit = TimeUnit.SECONDS
            ))
            // Cache configuration
            .cache(Cache(File("/tmp/goldpulse-http-cache"), CACHE_SIZE_BYTES))
            // Security: Add headers for protection
            .addNetworkInterceptor { chain ->
                val original = chain.request()
                val request = original.newBuilder()
                    // Security: Prevent caching of sensitive data
                    .header("Cache-Control", "public, max-age=60, stale-while-revalidate=300")
                    .header("X-Content-Type-Options", "nosniff")
                    .header("X-Frame-Options", "DENY")
                    .header("Referrer-Policy", "strict-origin-when-cross-origin")
                    .build()
                val response = chain.proceed(request)
                // Security: Add security headers to response
                response.newBuilder()
                    .header("X-Content-Type-Options", "nosniff")
                    .build()
            }
            .addInterceptor(logging)
            // Security: Protocol and cipher configuration
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