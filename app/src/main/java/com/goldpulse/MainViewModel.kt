package com.goldpulse

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.goldpulse.data.local.AppPreferences
import com.goldpulse.data.local.SettingsState
import com.goldpulse.data.model.PricePoint
import com.goldpulse.data.network.NetworkModule
import com.goldpulse.data.repository.GoldRepositoryImpl
import com.goldpulse.worker.WorkScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class UiState(
    val loading: Boolean = false,
    val currentPrice: Double? = null,
    val pricesByCurrency: Map<String, Double> = emptyMap(),
    val lastUpdatedText: String = "",
    val settings: SettingsState = SettingsState(),
    val history: List<PricePoint> = emptyList(),
    val error: String? = null
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = AppPreferences(app.applicationContext)
    private val repository = GoldRepositoryImpl(NetworkModule.api)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        observeSettings()
        observeHistory()
        refreshPrice()
    }

    private fun observeSettings() = viewModelScope.launch {
        prefs.settingsFlow.collect { settings ->
            _uiState.update { it.copy(settings = settings) }
        }
    }

    private fun observeHistory() = viewModelScope.launch {
        prefs.historyFlow.collect { history ->
            _uiState.update { it.copy(history = history, currentPrice = history.lastOrNull()?.price ?: it.currentPrice) }
        }
    }

    fun refreshPrice() = viewModelScope.launch {
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
                    lastUpdatedText = time
                )
            }
        }.onFailure {
            _uiState.update { state -> state.copy(error = it.message ?: "Unknown error") }
        }
        _uiState.update { it.copy(loading = false) }
    }

    fun updateSettings(settings: SettingsState) = viewModelScope.launch {
        prefs.updateSettings(settings)
        WorkScheduler.start(getApplication(), settings.checkIntervalMinutes.toLong())
    }
}
