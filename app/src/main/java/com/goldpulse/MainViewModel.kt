package com.goldpulse

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.goldpulse.data.local.AppPreferences
import com.goldpulse.data.local.SettingsState
import com.goldpulse.data.model.PricePoint
import com.goldpulse.data.network.NetworkModule
import com.goldpulse.data.repository.GoldRepositoryImpl
import com.goldpulse.service.BackgroundModeController
import com.goldpulse.ui.components.Timeframe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

data class UiState(
    val loading: Boolean = false,
    val historyLoading: Boolean = false,
    val currentPrice: Double? = null,
    val pricesByCurrency: Map<String, Double> = emptyMap(),
    val lastUpdatedText: String = "",
    val settings: SettingsState = SettingsState(),
    val history: List<PricePoint> = emptyList(),
    val openPrice: Double? = null,
    val highPrice: Double? = null,
    val lowPrice: Double? = null,
    val dailyChangePercent: Double? = null,
    val parityWarning: Boolean = false,
    val dataSourceLabel: String = "gold-api.com + stooq.com + frankfurter.app",
    val staleData: Boolean = false,
    val error: String? = null
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = AppPreferences(app.applicationContext)
    private val repository = GoldRepositoryImpl(NetworkModule.api)
    private var currentTimeframe: Timeframe = Timeframe.DAY_1

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        observeSettings()
        observeLocalHistory()
        refreshPrice()
    }

    private fun observeSettings() = viewModelScope.launch {
        prefs.settingsFlow.collect { settings ->
            _uiState.update { it.copy(settings = settings) }
            loadHistory(currentTimeframe)
        }
    }

    private fun observeLocalHistory() = viewModelScope.launch {
        prefs.historyFlow.collect { history ->
            _uiState.update {
                it.copy(currentPrice = history.lastOrNull()?.price ?: it.currentPrice)
            }
        }
    }

    fun refreshPrice() = viewModelScope.launch {
        if (_uiState.value.loading) return@launch

        _uiState.update { it.copy(loading = true, error = null) }
        runCatching {
            val settings = prefs.settingsFlow.first()
            val currencies = settings.currenciesCsv
                .split(',')
                .map { it.trim().uppercase() }
                .filter { it.isNotEmpty() }
                .distinct()
                .ifEmpty { listOf("USD") }

            val prices = linkedMapOf<String, Double>()
            currencies.forEach { c ->
                prices[c] = repository.fetchCurrentPrice(c).price
            }

            val primary = currencies.first()
            val primaryPoint = PricePoint(
                price = prices[primary] ?: 0.0,
                timestamp = System.currentTimeMillis() / 1000
            )

            prefs.saveLastPrice(primaryPoint.price)
            prefs.appendHistory(primaryPoint)

            val time = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
            _uiState.update {
                it.copy(
                    currentPrice = primaryPoint.price,
                    pricesByCurrency = prices,
                    lastUpdatedText = time,
                    parityWarning = !isParityConsistent(prices),
                    staleData = false
                )
            }

            loadHistory(currentTimeframe)
        }.onFailure {
            _uiState.update { state ->
                state.copy(
                    error = it.message ?: "Unknown error",
                    staleData = true
                )
            }
        }
        _uiState.update { it.copy(loading = false) }
    }

    fun onTimeframeChanged(timeframe: Timeframe) {
        currentTimeframe = timeframe
        loadHistory(timeframe)
    }

    private fun loadHistory(timeframe: Timeframe) = viewModelScope.launch {
        val currency = _uiState.value.settings.currency
        _uiState.update { it.copy(historyLoading = true) }
        val remote = runCatching {
            repository.fetchHistoricalPrices(currency, timeframe)
        }.getOrDefault(emptyList())
        val local = prefs.historyFlow.first()
        val merged = (remote + local)
            .distinctBy { it.timestamp }
            .sortedBy { it.timestamp }

        val now = System.currentTimeMillis() / 1000
        val isStale = merged.lastOrNull()?.let { abs(now - it.timestamp) > 18 * 60 * 60 } ?: true

        _uiState.update {
            it.copy(
                historyLoading = false,
                history = merged,
                openPrice = merged.firstOrNull()?.price,
                highPrice = merged.maxOfOrNull { p -> p.price },
                lowPrice = merged.minOfOrNull { p -> p.price },
                dailyChangePercent = computeDailyChangePercent(merged),
                staleData = isStale
            )
        }
    }

    private fun computeDailyChangePercent(history: List<PricePoint>): Double? {
        if (history.size < 2) return null
        val last = history.last().price
        val dayAgoTs = (System.currentTimeMillis() / 1000) - (24 * 60 * 60)
        val base = history.lastOrNull { it.timestamp <= dayAgoTs }?.price ?: history.first().price
        if (base == 0.0) return null
        return ((last - base) / base) * 100.0
    }

    private fun isParityConsistent(pricesByCurrency: Map<String, Double>): Boolean {
        if (pricesByCurrency.size <= 1) return true
        val values = pricesByCurrency.values.filter { it > 0 }
        if (values.size <= 1) return true
        val avg = values.average()
        return values.all { kotlin.math.abs(it - avg) / avg < 0.35 }
    }

    fun updateSettings(settings: SettingsState) = viewModelScope.launch {
        prefs.updateSettings(settings)
        BackgroundModeController.apply(getApplication(), settings)
        loadHistory(currentTimeframe)
    }
}
