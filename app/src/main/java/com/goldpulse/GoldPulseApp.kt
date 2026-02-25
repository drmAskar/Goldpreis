package com.goldpulse

import android.app.Application
import com.goldpulse.util.NotificationHelper
import com.goldpulse.worker.WorkScheduler

class GoldPulseApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannel(this)
        WorkScheduler.start(this)
    }
}
