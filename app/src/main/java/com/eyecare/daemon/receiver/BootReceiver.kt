package com.eyecare.daemon.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.eyecare.daemon.service.EyeCareService
import com.eyecare.daemon.util.PrefsManager

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            if (PrefsManager.isAutoStartEnabled(context)) {
                val serviceIntent = Intent(context, EyeCareService::class.java)
                context.startForegroundService(serviceIntent)
            }
        }
    }
}
