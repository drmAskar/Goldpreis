package com.goldpulse

import android.app.Application
import com.goldpulse.data.local.AppPreferences
import com.goldpulse.service.BackgroundModeController
import com.goldpulse.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class GoldPulseApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannels(this)

        appScope.launch {
            val settings = AppPreferences(this@GoldPulseApp).settingsFlow.first()
            BackgroundModeController.apply(this@GoldPulseApp, settings)
        }
    }
}
