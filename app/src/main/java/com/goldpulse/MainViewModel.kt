package com.goldpulse

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.goldpulse.data.local.AlertDirection
import com.goldpulse.data.local.AppPreferences
import com.goldpulse.data.local.PriceAlert
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

enum class DataQuality { LIVE, DELAYED, PARTIAL }

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
    val insufficientIntradayData: Boolean = false,
    val alerts: List<PriceAlert> = emptyList(),
    val quality: DataQuality = DataQuality.DELAYED,
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
        observeAlerts()
        refreshPrice()
    }

    private fun observeSettings() = viewModelScope.launch {
        prefs.settingsFlow.collect { settings ->
            _uiState.update { it.copy(settings = settings) }
            loadHistory(currentTimeframe)
        }
    }

    private fun observeAlerts() = viewModelScope.launch {
        prefs.alertsFlow.collect { alerts ->
            _uiState.update { it.copy(alerts = alerts.sortedBy { a -> a.currency + a.targetPrice }) }
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

            val oldPrices = _uiState.value.pricesByCurrency
            val prices = linkedMapOf<String, Double>()
            currencies.forEach { c ->
                val fetched = runCatching { repository.fetchCurrentPrice(c).price }.getOrNull()
                val fallback = oldPrices[c]
                if (fetched != null) prices[c] = fetched
                else if (fallback != null) prices[c] = fallback
            }

            val primary = currencies.first()
            val primaryValue = prices[primary]
                ?: runCatching { repository.fetchCurrentPrice(primary).price }.getOrNull()
                ?: oldPrices[primary]

            if (primaryValue != null) {
                prices[primary] = primaryValue
                val primaryPoint = PricePoint(
                    price = primaryValue,
                    timestamp = System.currentTimeMillis() / 1000
                )

                prefs.saveLastPrice(primaryPoint.price, primary)
                prefs.appendHistory(primaryPoint)
            }

            val time = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
            _uiState.update {
                val partial = prices.size < currencies.size
                it.copy(
                    currentPrice = primaryValue ?: it.currentPrice,
                    pricesByCurrency = prices,
                    lastUpdatedText = time,
                    parityWarning = false,
                    staleData = primaryValue == null,
                    quality = when {
                        partial -> DataQuality.PARTIAL
                        primaryValue == null -> DataQuality.DELAYED
                        else -> DataQuality.LIVE
                    }
                )
            }

            loadHistory(currentTimeframe)
        }.onFailure {
            _uiState.update { state ->
                state.copy(
                    error = it.message ?: "Unknown error",
                    staleData = true,
                    quality = DataQuality.DELAYED
                )
            }
        }
        _uiState.update { it.copy(loading = false) }
    }

    fun onTimeframeChanged(timeframe: Timeframe) {
        currentTimeframe = timeframe
        loadHistory(timeframe)
    }

    fun addAlert(currency: String, direction: AlertDirection, targetPrice: Double) = viewModelScope.launch {
        if (targetPrice <= 0.0) return@launch
        val current = prefs.alertsFlow.first()
        val next = current + PriceAlert(currency = currency.uppercase(), direction = direction, targetPrice = targetPrice)
        prefs.saveAlerts(next)
    }

    fun removeAlert(alertId: String) = viewModelScope.launch {
        val current = prefs.alertsFlow.first()
        prefs.saveAlerts(current.filterNot { it.id == alertId })
    }

    private fun loadHistory(timeframe: Timeframe) = viewModelScope.launch {
        _uiState.update { it.copy(historyLoading = true) }

        val currency = _uiState.value.settings.currency
        val history = runCatching {
            repository.fetchHistoricalPrices(currency, timeframe)
        }.getOrDefault(emptyList())
            .distinctBy { it.timestamp }
            .sortedBy { it.timestamp }

        val now = System.currentTimeMillis() / 1000
        val isStale = history.lastOrNull()?.let { abs(now - it.timestamp) > 48 * 60 * 60 } ?: false
        val insufficientIntradayData = timeframe == Timeframe.DAY_1 && history.size < 2

        _uiState.update {
            it.copy(
                historyLoading = false,
                history = history,
                openPrice = history.firstOrNull()?.price,
                highPrice = history.maxOfOrNull { p -> p.price },
                lowPrice = history.minOfOrNull { p -> p.price },
                dailyChangePercent = computeDailyChangePercent(history),
                staleData = isStale,
                insufficientIntradayData = insufficientIntradayData,
                quality = when {
                    it.quality == DataQuality.PARTIAL -> DataQuality.PARTIAL
                    insufficientIntradayData || isStale -> DataQuality.DELAYED
                    else -> DataQuality.LIVE
                }
            )
        }
    }

    private fun computeDailyChangePercent(history: List<PricePoint>): Double? {
        if (history.size < 2) return null

        val now = System.currentTimeMillis() / 1000
        val minAge = 20L * 60 * 60
        val maxAge = 28L * 60 * 60

        val latest = history.lastOrNull() ?: return null
        val candidate = history
            .dropLast(1)
            .map { point -> point to (now - point.timestamp) }
            .filter { (_, age) -> age in minAge..maxAge }
            .minByOrNull { (_, age) -> abs(age - 24L * 60 * 60) }
            ?.first
            ?: return null

        if (candidate.price == 0.0) return null
        return ((latest.price - candidate.price) / candidate.price) * 100.0
    }

    fun updateSettings(settings: SettingsState) = viewModelScope.launch {
        prefs.updateSettings(settings)
        BackgroundModeController.apply(getApplication(), settings)
        loadHistory(currentTimeframe)
    }
}
