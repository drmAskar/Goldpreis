package com.goldpulse.worker

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.goldpulse.data.local.SettingsState
import java.util.concurrent.TimeUnit

object WorkScheduler {
    private const val UNIQUE_WORK_NAME = "goldpulse_price_check"

    fun applySettings(context: Context, settings: SettingsState) {
        val workManager = WorkManager.getInstance(context)
        if (!settings.backgroundNotificationsEnabled) {
            workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
            return
        }

        val request = OneTimeWorkRequestBuilder<GoldPriceWorker>()
            .setInitialDelay(settings.checkIntervalMinutes.toLong().coerceAtLeast(1L), TimeUnit.MINUTES)
            .build()

        workManager.enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun enqueueNext(context: Context, delayMinutes: Long) {
        val request = OneTimeWorkRequestBuilder<GoldPriceWorker>()
            .setInitialDelay(delayMinutes.coerceAtLeast(1L), TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
