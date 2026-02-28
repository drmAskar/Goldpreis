package com.goldpulse.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.goldpulse.R
import com.goldpulse.data.local.AppPreferences
import com.goldpulse.data.network.NetworkModule
import com.goldpulse.data.repository.GoldRepositoryImpl
import com.goldpulse.util.NotificationHelper
import com.goldpulse.util.formatPrice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class ForegroundPriceService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START, null -> startLoop()
        }
        return START_STICKY
    }

    private fun startLoop() {
        serviceScope.launch {
            val prefs = AppPreferences(applicationContext)
            val repo = GoldRepositoryImpl(NetworkModule.api)
            NotificationHelper.ensureChannels(applicationContext)

            startForeground(
                NotificationHelper.ONGOING_NOTIFICATION_ID,
                NotificationHelper.buildOngoingNotification(
                    context = applicationContext,
                    title = getString(R.string.notification_persistent_title),
                    body = getString(R.string.notification_persistent_loading)
                )
            )

            while (isActive) {
                val settings = prefs.settingsFlow.first()
                if (!settings.persistentForegroundEnabled) {
                    stopSelf()
                    break
                }

                val display = runCatching {
                    val latest = repo.fetchCurrentPrice(settings.currency)
                    prefs.saveLastPrice(latest.price)
                    prefs.appendHistory(latest)
                    val updated = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

                    val history24h = prefs.historyFlow.first()
                        .asSequence()
                        .filter { it.timestamp >= (latest.timestamp - 24L * 60 * 60) }
                        .sortedBy { it.timestamp }
                        .toList()
                    val previous = history24h.dropLast(1).lastOrNull()

                    val trendPercent = previous?.price
                        ?.takeIf { abs(it) > 0.000001 }
                        ?.let { ((latest.price - it) / it) * 100.0 }
                    val trendUp = trendPercent?.let { it >= 0.0 }
                    val marker = when {
                        trendPercent == null -> ""
                        trendPercent >= 0 -> "▲"
                        else -> "▼"
                    }

                    val body = if (settings.persistentTickerEnabled && trendPercent != null) {
                        val pctText = String.format(Locale.getDefault(), "%.2f%%", abs(trendPercent))
                        getString(
                            R.string.notification_persistent_ticker_body,
                            formatPrice(latest.price, settings.currency),
                            marker,
                            pctText,
                            updated
                        )
                    } else {
                        getString(
                            R.string.notification_persistent_body,
                            formatPrice(latest.price, settings.currency),
                            updated
                        )
                    }

                    body to if (settings.persistentTickerEnabled) trendUp else null
                }.getOrElse {
                    getString(R.string.notification_persistent_error) to null
                }

                NotificationHelper.updateOngoing(
                    context = applicationContext,
                    body = display.first,
                    trendUp = display.second
                )

                val delayMs = settings.checkIntervalMinutes.coerceAtLeast(1) * 60_000L
                delay(delayMs)
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.goldpulse.action.START_FOREGROUND"
        const val ACTION_STOP = "com.goldpulse.action.STOP_FOREGROUND"
    }
}
