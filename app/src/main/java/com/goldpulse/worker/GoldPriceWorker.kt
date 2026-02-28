package com.goldpulse.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.goldpulse.R
import com.goldpulse.data.local.AlertDirection
import com.goldpulse.data.local.AppPreferences
import com.goldpulse.data.local.PriceAlert
import com.goldpulse.data.network.NetworkModule
import com.goldpulse.data.repository.GoldRepositoryImpl
import com.goldpulse.util.NotificationHelper
import com.goldpulse.util.formatPrice
import com.goldpulse.util.percentChange
import kotlinx.coroutines.flow.first
import java.util.Locale

class GoldPriceWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val preferences = AppPreferences(applicationContext)
        val settings = preferences.settingsFlow.first()
        if (!settings.backgroundNotificationsEnabled) return Result.success()

        val repository = GoldRepositoryImpl(NetworkModule.api)

        return try {
            val currencies = settings.currenciesCsv
                .split(',')
                .map { it.trim().uppercase() }
                .filter { it.isNotBlank() }
                .ifEmpty { listOf(settings.currency.uppercase()) }

            val latestByCurrency = mutableMapOf<String, Double>()
            currencies.forEach { currency ->
                runCatching { repository.fetchCurrentPrice(currency).price }
                    .getOrNull()
                    ?.let { latest ->
                        latestByCurrency[currency] = latest
                        preferences.saveLastPrice(latest, currency)
                    }
            }

            latestByCurrency[settings.currency.uppercase()]?.let { primaryPrice ->
                preferences.appendHistory(
                    com.goldpulse.data.model.PricePoint(
                        price = primaryPrice,
                        timestamp = System.currentTimeMillis() / 1000
                    )
                )
            }

            var shouldNotify = false
            var reasonBody: String? = null

            val primaryCurrency = settings.currency.uppercase()
            val latestPrimary = latestByCurrency[primaryCurrency]
            if (latestPrimary != null) {
                val previous = preferences.getLastPriceForCurrency(primaryCurrency)
                if (previous != null) {
                    val change = kotlin.math.abs(percentChange(previous, latestPrimary))
                    if (change >= settings.thresholdPercent) {
                        shouldNotify = true
                        reasonBody = applicationContext.getString(
                            R.string.notification_body,
                            String.format(Locale.US, "%.2f", change),
                            formatPrice(latestPrimary, primaryCurrency)
                        )
                    }
                }
            }

            var alerts = preferences.alertsFlow.first()
            // Backward-compatible migration for old single-threshold settings.
            if (alerts.isEmpty() && (settings.alertAbovePrice != null || settings.alertBelowPrice != null)) {
                val migrated = buildList {
                    settings.alertAbovePrice?.let { add(PriceAlert(currency = primaryCurrency, direction = AlertDirection.ABOVE, targetPrice = it)) }
                    settings.alertBelowPrice?.let { add(PriceAlert(currency = primaryCurrency, direction = AlertDirection.BELOW, targetPrice = it)) }
                }
                if (migrated.isNotEmpty()) {
                    preferences.saveAlerts(migrated)
                    alerts = migrated
                }
            }

            if (alerts.isNotEmpty()) {
                val now = System.currentTimeMillis()
                val cooldownMs = 6 * 60 * 60 * 1000L
                var changed = false
                val updatedAlerts = alerts.map { alert ->
                    val latest = latestByCurrency[alert.currency] ?: return@map alert
                    if (!alert.enabled) return@map alert

                    val triggered = when (alert.direction) {
                        AlertDirection.ABOVE -> latest >= alert.targetPrice
                        AlertDirection.BELOW -> latest <= alert.targetPrice
                    }

                    val inCooldown = alert.lastTriggeredAt?.let { now - it < cooldownMs } ?: false
                    if (triggered && !inCooldown) {
                        shouldNotify = true
                        reasonBody = when (alert.direction) {
                            AlertDirection.ABOVE -> applicationContext.getString(
                                R.string.notification_above_body,
                                formatPrice(latest, alert.currency),
                                formatPrice(alert.targetPrice, alert.currency)
                            )
                            AlertDirection.BELOW -> applicationContext.getString(
                                R.string.notification_below_body,
                                formatPrice(latest, alert.currency),
                                formatPrice(alert.targetPrice, alert.currency)
                            )
                        }
                        changed = true
                        alert.copy(lastTriggeredAt = now)
                    } else {
                        alert
                    }
                }
                if (changed) preferences.saveAlerts(updatedAlerts)
            }

            if (shouldNotify) {
                NotificationHelper.ensureChannels(applicationContext)
                val title = applicationContext.getString(R.string.notification_title)
                NotificationHelper.showAlert(applicationContext, title, reasonBody.orEmpty())
            }

            Result.success()
        } catch (_: Exception) {
            Result.retry()
        } finally {
            val latestSettings = preferences.settingsFlow.first()
            if (latestSettings.backgroundNotificationsEnabled) {
                WorkScheduler.enqueueNext(
                    context = applicationContext,
                    delayMinutes = latestSettings.checkIntervalMinutes.toLong()
                )
            }
        }
    }
}
