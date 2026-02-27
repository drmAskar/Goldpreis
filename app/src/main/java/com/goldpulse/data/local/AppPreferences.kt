package com.goldpulse.data.local

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.goldpulse.data.model.PricePoint
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class SettingsState(
    val thresholdPercent: Double = 1.0,
    val currency: String = "USD",
    val currenciesCsv: String = "USD,EUR,AED",
    val themeName: String = "Purple",
    val checkIntervalMinutes: Int = 10,
    val backgroundNotificationsEnabled: Boolean = true,
    val persistentForegroundEnabled: Boolean = false,
    val alertAbovePrice: Double? = null,
    val alertBelowPrice: Double? = null
)

class AppPreferences(context: Context) {
    private val gson = Gson()
    private val appContext = context.applicationContext

    private val dataStore = getDataStore(appContext)

    val settingsFlow: Flow<SettingsState> = dataStore.data.map { prefs ->
        val savedCurrency = prefs[KEY_CURRENCY] ?: "USD"
        SettingsState(
            thresholdPercent = prefs[KEY_THRESHOLD] ?: 1.0,
            currency = savedCurrency,
            currenciesCsv = prefs[KEY_CURRENCIES] ?: savedCurrency,
            themeName = prefs[KEY_THEME] ?: "Purple",
            checkIntervalMinutes = prefs[KEY_INTERVAL] ?: 10,
            backgroundNotificationsEnabled = prefs[KEY_BG_ENABLED] ?: true,
            persistentForegroundEnabled = prefs[KEY_PERSISTENT_ENABLED] ?: false,
            alertAbovePrice = prefs[KEY_ALERT_ABOVE]?.toDoubleOrNull(),
            alertBelowPrice = prefs[KEY_ALERT_BELOW]?.toDoubleOrNull()
        )
    }

    val lastPriceFlow: Flow<Double?> = dataStore.data.map { it[KEY_LAST_PRICE] }

    val historyFlow: Flow<List<PricePoint>> = dataStore.data.map { prefs ->
        val json = prefs[KEY_HISTORY] ?: return@map emptyList()
        val type = object : TypeToken<List<PricePoint>>() {}.type
        runCatching { gson.fromJson<List<PricePoint>>(json, type) }.getOrDefault(emptyList())
    }

    suspend fun updateSettings(settings: SettingsState) {
        dataStore.edit { prefs ->
            prefs[KEY_THRESHOLD] = settings.thresholdPercent
            prefs[KEY_CURRENCY] = settings.currency
            prefs[KEY_CURRENCIES] = settings.currenciesCsv
            prefs[KEY_THEME] = settings.themeName
            prefs[KEY_INTERVAL] = settings.checkIntervalMinutes
            prefs[KEY_BG_ENABLED] = settings.backgroundNotificationsEnabled
            prefs[KEY_PERSISTENT_ENABLED] = settings.persistentForegroundEnabled
            prefs[KEY_ALERT_ABOVE] = settings.alertAbovePrice?.toString() ?: ""
            prefs[KEY_ALERT_BELOW] = settings.alertBelowPrice?.toString() ?: ""
        }
    }

    suspend fun saveLastPrice(price: Double) {
        dataStore.edit { it[KEY_LAST_PRICE] = price }
    }

    suspend fun appendHistory(point: PricePoint, maxItems: Int = 600) {
        dataStore.edit { prefs ->
            val type = object : TypeToken<List<PricePoint>>() {}.type
            val current = runCatching {
                gson.fromJson<List<PricePoint>>(prefs[KEY_HISTORY] ?: "[]", type)
            }.getOrDefault(emptyList())

            val updated = (current + point).takeLast(maxItems)
            prefs[KEY_HISTORY] = gson.toJson(updated)
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>? = null

        private fun getDataStore(context: Context): androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences> {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PreferenceDataStoreFactory.create(
                    produceFile = { context.preferencesDataStoreFile("goldpulse.preferences_pb") }
                ).also { INSTANCE = it }
            }
        }

        private val KEY_THRESHOLD = doublePreferencesKey("threshold_percent")
        private val KEY_CURRENCY = stringPreferencesKey("target_currency")
        private val KEY_INTERVAL = intPreferencesKey("check_interval_minutes")
        private val KEY_CURRENCIES = stringPreferencesKey("target_currencies_csv")
        private val KEY_THEME = stringPreferencesKey("theme_name")
        private val KEY_LAST_PRICE = doublePreferencesKey("last_price")
        private val KEY_HISTORY = stringPreferencesKey("price_history_json")
        private val KEY_BG_ENABLED = booleanPreferencesKey("background_notifications_enabled")
        private val KEY_PERSISTENT_ENABLED = booleanPreferencesKey("persistent_foreground_enabled")
        private val KEY_ALERT_ABOVE = stringPreferencesKey("alert_above_price")
        private val KEY_ALERT_BELOW = stringPreferencesKey("alert_below_price")
    }
}
