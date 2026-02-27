package com.goldpulse.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.goldpulse.R
import com.goldpulse.data.local.AppPreferences
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
            val latest = repository.fetchCurrentPrice(settings.currency)
            val previous = preferences.lastPriceFlow.first()

            preferences.saveLastPrice(latest.price)
            preferences.appendHistory(latest)

            if (previous != null) {
                val change = kotlin.math.abs(percentChange(previous, latest.price))
                if (change >= settings.thresholdPercent) {
                    NotificationHelper.ensureChannels(applicationContext)
                    val title = applicationContext.getString(R.string.notification_title)
                    val body = applicationContext.getString(
                        R.string.notification_body,
                        String.format(Locale.US, "%.2f", change),
                        formatPrice(latest.price, settings.currency)
                    )
                    NotificationHelper.showAlert(applicationContext, title, body)
                }
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
