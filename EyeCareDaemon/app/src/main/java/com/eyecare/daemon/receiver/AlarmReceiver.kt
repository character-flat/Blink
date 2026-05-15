package com.eyecare.daemon.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.eyecare.daemon.service.EyeCareService

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == EyeCareService.ACTION_ALARM_FIRED) {
            val serviceIntent = Intent(context, EyeCareService::class.java).apply {
                action = EyeCareService.ACTION_ALARM_FIRED
            }
            context.startForegroundService(serviceIntent)
        }
    }
}
