package com.cflat.blink.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.UserManager
import android.util.Log
import com.cflat.blink.service.EyeCareService
import com.cflat.blink.util.PrefsManager

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_LOCKED_BOOT_COMPLETED) return

        Log.d(TAG, "Received: $action")

        if (action == Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            val userManager = context.getSystemService(UserManager::class.java)
            if (!userManager.isUserUnlocked) {
                Log.d(TAG, "Device locked boot - starting service early")
                startService(context)
                return
            }
        }

        if (PrefsManager.isAutoStartEnabled(context)) {
            Log.d(TAG, "Auto-start enabled - starting service")
            startService(context)
        }
    }

    private fun startService(context: Context) {
        val serviceIntent = Intent(context, EyeCareService::class.java)
        context.startForegroundService(serviceIntent)
    }
}
