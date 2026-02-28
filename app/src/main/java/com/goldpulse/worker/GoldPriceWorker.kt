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
import com.goldpulse.util.formatPriceTimestamp
import com.goldpulse.util.formatPriceType
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

            val latestByCurrency = mutableMapOf<String, com.goldpulse.data.model.PricePoint>()
            val primaryCurrency = settings.currency.uppercase()
            val previousPrimary = preferences.getLastPriceForCurrency(primaryCurrency)
            currencies.forEach { currency ->
                runCatching { repository.fetchCurrentPrice(currency) }
                    .getOrNull()
                    ?.let { latest ->
                        latestByCurrency[currency] = latest
                        preferences.saveLastPrice(latest.price, currency)
                    }
            }

            latestByCurrency[settings.currency.uppercase()]?.let { primaryPoint ->
                preferences.appendHistory(primaryPoint)
            }

            var shouldNotify = false
            var reasonBody: String? = null

            val latestPrimary = latestByCurrency[primaryCurrency]
            if (latestPrimary != null) {
                if (previousPrimary != null) {
                    val change = kotlin.math.abs(percentChange(previousPrimary, latestPrimary.price))
                    if (change >= settings.thresholdPercent) {
                        shouldNotify = true
                        val baseBody = applicationContext.getString(
                            R.string.notification_body,
                            String.format(Locale.US, "%.2f", change),
                            formatPrice(latestPrimary.price, primaryCurrency)
                        )
                        reasonBody = "$baseBody\n${latestPrimary.sourceLabel ?: "—"} • ${formatPriceType(latestPrimary.priceType)} • ${formatPriceTimestamp(latestPrimary.timestamp)}"
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
                        AlertDirection.ABOVE -> latest.price >= alert.targetPrice
                        AlertDirection.BELOW -> latest.price <= alert.targetPrice
                    }

                    val inCooldown = alert.lastTriggeredAt?.let { now - it < cooldownMs } ?: false
                    if (triggered && !inCooldown) {
                        shouldNotify = true
                        val baseBody = when (alert.direction) {
                            AlertDirection.ABOVE -> applicationContext.getString(
                                R.string.notification_above_body,
                                formatPrice(latest.price, alert.currency),
                                formatPrice(alert.targetPrice, alert.currency)
                            )
                            AlertDirection.BELOW -> applicationContext.getString(
                                R.string.notification_below_body,
                                formatPrice(latest.price, alert.currency),
                                formatPrice(alert.targetPrice, alert.currency)
                            )
                        }
                        reasonBody = "$baseBody\n${latest.sourceLabel ?: "—"} • ${formatPriceType(latest.priceType)} • ${formatPriceTimestamp(latest.timestamp)}"
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
