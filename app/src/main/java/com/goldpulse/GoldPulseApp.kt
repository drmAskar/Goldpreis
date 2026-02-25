package com.goldpulse

import android.app.Application
import com.goldpulse.data.local.AppPreferences
import com.goldpulse.util.NotificationHelper
import com.goldpulse.worker.WorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class GoldPulseApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannel(this)

        appScope.launch {
            val settings = AppPreferences(this@GoldPulseApp).settingsFlow.first()
            WorkScheduler.applySettings(this@GoldPulseApp, settings)
        }
    }
}
