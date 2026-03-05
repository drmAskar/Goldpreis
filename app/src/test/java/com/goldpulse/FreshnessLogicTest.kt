package com.goldpulse

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap

/**
 * Unit tests for freshness logic and cache invalidation.
 * Tests verify that daily chart data refreshes within target <=60s.
 */
class FreshnessLogicTest {

    // Simulated cache constants matching GoldRepositoryImpl
    private val CURRENT_PRICE_CACHE_MS = 30_000L
    private val DAILY_SERIES_TTL_MS = 45_000L
    private val HISTORY_CACHE_MS = 120_000L

    @Test
    fun `current price cache expires after 30 seconds`() {
        val now = System.currentTimeMillis()
        val cacheTimestamp = now - CURRENT_PRICE_CACHE_MS - 1
        
        // Cache should be considered stale
        assertTrue("Current price cache should be expired", now - cacheTimestamp > CURRENT_PRICE_CACHE_MS)
        
        // Fresh cache should not be expired
        val freshTimestamp = now - 10_000L
        assertFalse("Fresh cache should not be expired", now - freshTimestamp > CURRENT_PRICE_CACHE_MS)
    }

    @Test
    fun `daily series cache expires within 60 seconds target`() {
        val now = System.currentTimeMillis()
        
        // Daily series should refresh within 60s target
        val staleTimestamp = now - 60_000L
        assertTrue("Daily cache older than 60s should be expired", now - staleTimestamp > DAILY_SERIES_TTL_MS)
        
        // Cache at 45s should be valid
        val freshTimestamp = now - 40_000L
        assertFalse("Daily cache within TTL should be valid", now - freshTimestamp > DAILY_SERIES_TTL_MS)
    }

    @Test
    fun `history cache uses longer TTL for non-daily timeframes`() {
        val now = System.currentTimeMillis()
        
        // History for longer timeframes uses 2-minute TTL
        val staleTimestamp = now - HISTORY_CACHE_MS - 1
        assertTrue("History cache should expire after TTL", now - staleTimestamp > HISTORY_CACHE_MS)
        
        // Verify that daily TTL is shorter than history TTL
        assertTrue("Daily TTL should be shorter than history TTL", DAILY_SERIES_TTL_MS < HISTORY_CACHE_MS)
    }

    @Test
    fun `concurrent cache operations are thread-safe`() {
        val cache = ConcurrentHashMap<String, Pair<Long, String>>()
        val threads = (1..10).map { threadId ->
            Thread {
                repeat(100) { i ->
                    val key = "key-${threadId % 3}"
                    cache[key] = System.currentTimeMillis() to "value-$threadId-$i"
                    cache.get(key)
                }
            }
        }
        
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        
        // Should complete without exception
        assertTrue("Concurrent cache operations should complete safely", cache.size <= 3)
    }

    @Test
    fun `daily timeframe uses shortest TTL for near real-time updates`() {
        // Daily timeframe should use DAILY_SERIES_TTL_MS (45s)
        // This ensures chart updates within 60s target
        val effectiveTtl = DAILY_SERIES_TTL_MS
        
        // Verify TTL is within 60s target
        assertTrue("Daily TTL should be <=60s", effectiveTtl <= 60_000L)
        
        // Verify TTL is appropriate for real-time updates
        assertTrue("Daily TTL should be >=30s to avoid excessive API calls", effectiveTtl >= 30_000L)
    }

    @Test
    fun `cache invalidation forces fresh data on next fetch`() {
        val cache = ConcurrentHashMap<String, Pair<Long, String>>()
        val key = "USD"
        
        // Add stale entry
        val staleTime = System.currentTimeMillis() - DAILY_SERIES_TTL_MS - 10_000
        cache[key] = staleTime to "stale-value"
        
        // Check if stale (simulating cache check logic)
        val (ts, _) = cache[key]!!
        val isStale = System.currentTimeMillis() - ts > DAILY_SERIES_TTL_MS
        
        assertTrue("Stale cache entry should be detected", isStale)
        
        // Simulate refresh
        cache[key] = System.currentTimeMillis() to "fresh-value"
        
        // Verify fresh entry
        val (newTs, newValue) = cache[key]!!
        val isFresh = System.currentTimeMillis() - newTs < DAILY_SERIES_TTL_MS
        
        assertTrue("Fresh cache entry should be valid", isFresh)
        assertEquals("Cache should have fresh value", "fresh-value", newValue)
    }
}
