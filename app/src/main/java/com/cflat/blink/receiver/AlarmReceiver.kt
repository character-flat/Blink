package com.cflat.blink.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.cflat.blink.service.EyeCareService

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action == EyeCareService.ACTION_ALARM_FIRED || action == EyeCareService.ACTION_WATCHDOG) {
            val serviceIntent = Intent(context, EyeCareService::class.java).apply {
                this.action = action
            }
            context.startForegroundService(serviceIntent)
        }
    }
}
