package com.goldpulse.service

import android.content.Context
import android.content.Intent
import android.os.Build
import com.goldpulse.data.local.SettingsState
import com.goldpulse.worker.WorkScheduler

object BackgroundModeController {
    fun apply(context: Context, settings: SettingsState) {
        WorkScheduler.applySettings(context, settings)
        if (settings.persistentForegroundEnabled) {
            val startIntent = Intent(context, ForegroundPriceService::class.java).apply {
                action = ForegroundPriceService.ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(startIntent)
            } else {
                context.startService(startIntent)
            }
        } else {
            context.stopService(Intent(context, ForegroundPriceService::class.java))
        }
    }
}
