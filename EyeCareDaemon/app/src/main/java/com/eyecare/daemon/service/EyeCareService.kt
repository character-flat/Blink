package com.eyecare.daemon.service

import android.app.AlarmManager
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import com.eyecare.daemon.EyeCareApp
import com.eyecare.daemon.R
import com.eyecare.daemon.receiver.AlarmReceiver
import com.eyecare.daemon.ui.MainActivity
import com.eyecare.daemon.util.PrefsManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class EyeCareService : Service() {

    companion object {
        private const val TAG = "EyeCareService"
        private const val NOTIFICATION_ID = 1001
        private const val ALERT_NOTIFICATION_ID = 1002
        private const val ALARM_REQUEST_CODE = 2001

        const val ACTION_ALARM_FIRED = "com.eyecare.daemon.ALARM_FIRED"
        const val ACTION_START_TIMER = "com.eyecare.daemon.START_TIMER"
        const val ACTION_STOP_TIMER = "com.eyecare.daemon.STOP_TIMER"
        const val ACTION_NEED_EXACT_ALARM_PERMISSION = "com.eyecare.daemon.NEED_EXACT_ALARM_PERMISSION"
        const val LOCK_RESET_THRESHOLD_MS = 20_000L // reset only if locked > 20s

        private val _remainingMs = MutableStateFlow(20 * 60 * 1000L)
        val remainingMs: StateFlow<Long> = _remainingMs.asStateFlow()

        private val _isResting = MutableStateFlow(false)
        val isResting: StateFlow<Boolean> = _isResting.asStateFlow()

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
    }

    private val timerDurationMs: Long
        get() = PrefsManager.getWorkDurationMs(this)

    private val restDurationMs: Long
        get() = PrefsManager.getRestDurationMs(this)

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var countdownJob: Job? = null
    private var isScreenOn = true
    private var screenOffElapsed: Long = 0L
    private var remainingMsAtScreenOff: Long = 0L

    // Persisted so we can resume after OS-killed service restart
    private var timerStartElapsed: Long
        get() = getSharedPreferences("svc_state", Context.MODE_PRIVATE)
            .getLong("timer_start_elapsed", 0L)
        set(value) = getSharedPreferences("svc_state", Context.MODE_PRIVATE)
            .edit().putLong("timer_start_elapsed", value).apply()

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenOn = false
                    screenOffElapsed = SystemClock.elapsedRealtime()
                    remainingMsAtScreenOff = _remainingMs.value
                    Log.d(TAG, "Screen OFF - pausing timer (${remainingMsAtScreenOff}ms remaining)")
                    cancelAlarm()
                    stopCountdown()
                }
                Intent.ACTION_SCREEN_ON -> {
                    isScreenOn = true
                    val lockedForMs = SystemClock.elapsedRealtime() - screenOffElapsed
                    Log.d(TAG, "Screen ON - was locked for ${lockedForMs}ms")
                    if (lockedForMs >= LOCK_RESET_THRESHOLD_MS) {
                        Log.d(TAG, "Locked > 20s — starting fresh cycle")
                        startFreshCycle()
                    } else {
                        Log.d(TAG, "Locked < 20s — resuming from ${remainingMsAtScreenOff}ms")
                        resumeCycle(remainingMsAtScreenOff)
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        registerScreenReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Always call startForeground first to satisfy Android 14 FGS requirement
        if (getSystemService(android.app.NotificationManager::class.java)
                .activeNotifications.none { it.id == NOTIFICATION_ID }) {
            startForeground(NOTIFICATION_ID, buildServiceNotification("Resuming..."))
        }

        when (intent?.action) {
            ACTION_ALARM_FIRED -> handleAlarmFired()
            ACTION_STOP_TIMER -> {
                stopSelf()
                return START_NOT_STICKY
            }
            null -> {
                // OS restarted the service after killing it (START_STICKY).
                // Only resume if we have a valid prior start time; otherwise start fresh.
                if (timerStartElapsed > 0L) {
                    Log.d(TAG, "Service restarted by OS - resuming existing countdown")
                    _isRunning.value = true
                    startForeground(NOTIFICATION_ID, buildServiceNotification("Resuming..."))
                    startCountdown() // resume countdown from saved timerStartElapsed
                } else {
                    Log.d(TAG, "Service restarted by OS with no prior state - starting fresh")
                    _isRunning.value = true
                    startForeground(NOTIFICATION_ID, buildServiceNotification("Starting..."))
                    startFreshCycle()
                }
            }
            else -> {
                // Explicit start from user or boot receiver
                Log.d(TAG, "Service started explicitly")
                startForeground(NOTIFICATION_ID, buildServiceNotification("Starting..."))
                _isRunning.value = true
                startFreshCycle()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        _isRunning.value = false
        _isResting.value = false
        timerStartElapsed = 0L
        cancelAlarm()
        stopCountdown()
        unregisterReceiver(screenReceiver)
        serviceScope.cancel()
    }

    private fun registerScreenReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        registerReceiver(screenReceiver, filter)
    }

    private fun startFreshCycle() {
        _isResting.value = false
        _remainingMs.value = timerDurationMs
        timerStartElapsed = 0L // will be set properly inside scheduleExactAlarm
        scheduleExactAlarm()
        startCountdown()
    }

    private fun resumeCycle(remainingMs: Long) {
        if (remainingMs <= 0) {
            startFreshCycle()
            return
        }
        _isResting.value = false
        _remainingMs.value = remainingMs
        // Schedule alarm for the remaining time
        val alarmManager = getSystemService(AlarmManager::class.java)
        val intent = Intent(this, AlarmReceiver::class.java).apply { action = ACTION_ALARM_FIRED }
        val pendingIntent = PendingIntent.getBroadcast(
            this, ALARM_REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        timerStartElapsed = SystemClock.elapsedRealtime() - (timerDurationMs - remainingMs)
        val triggerAt = SystemClock.elapsedRealtime() + remainingMs
        if (alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent)
        } else {
            alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent)
        }
        startCountdown()
    }

    private fun scheduleExactAlarm() {
        val alarmManager = getSystemService(AlarmManager::class.java)

        if (!alarmManager.canScheduleExactAlarms()) {
            Log.w(TAG, "Cannot schedule exact alarms - permission not granted. Sending broadcast.")
            sendBroadcast(Intent(ACTION_NEED_EXACT_ALARM_PERMISSION))
            // Fall back to inexact alarm so app doesn't crash
            scheduleInexactAlarm()
            return
        }

        val intent = Intent(this, AlarmReceiver::class.java).apply {
            action = ACTION_ALARM_FIRED
        }
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerAt = SystemClock.elapsedRealtime() + timerDurationMs
        timerStartElapsed = SystemClock.elapsedRealtime()

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            triggerAt,
            pendingIntent
        )
        Log.d(TAG, "Exact alarm scheduled for ${timerDurationMs / 1000}s from now")
    }

    private fun scheduleInexactAlarm() {
        val alarmManager = getSystemService(AlarmManager::class.java)
        val intent = Intent(this, AlarmReceiver::class.java).apply {
            action = ACTION_ALARM_FIRED
        }
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val triggerAt = SystemClock.elapsedRealtime() + timerDurationMs
        timerStartElapsed = SystemClock.elapsedRealtime()
        alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent)
        Log.w(TAG, "Inexact alarm scheduled (exact alarm permission missing)")
    }

    private fun cancelAlarm() {
        val alarmManager = getSystemService(AlarmManager::class.java)
        val intent = Intent(this, AlarmReceiver::class.java).apply {
            action = ACTION_ALARM_FIRED
        }
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    private fun startCountdown() {
        stopCountdown()
        countdownJob = serviceScope.launch {
            while (isActive) {
                val elapsed = SystemClock.elapsedRealtime() - timerStartElapsed
                val remaining = (timerDurationMs - elapsed).coerceAtLeast(0)
                _remainingMs.value = remaining
                updateNotification(formatTime(remaining))
                if (remaining <= 0) break
                delay(1000)
            }
        }
    }

    private fun stopCountdown() {
        countdownJob?.cancel()
        countdownJob = null
    }

    private fun handleAlarmFired() {
        Log.d(TAG, "ALARM FIRED - Time to rest eyes!")
        _isResting.value = true
        _remainingMs.value = 0
        stopCountdown()
        showAlertNotification()

        // Launch overlay service (draws over other apps)
        if (android.provider.Settings.canDrawOverlays(this)) {
            RestOverlayService.start(this)
        }

        serviceScope.launch {
            delay(restDurationMs)
            dismissAlertNotification()
            if (isScreenOn) {
                startFreshCycle()
            }
        }
    }

    private fun showAlertNotification() {
        val notification = NotificationCompat.Builder(this, EyeCareApp.CHANNEL_ALERT)
            .setContentTitle("👁️ Rest Your Eyes!")
            .setContentText("Look at something 20 feet away for 20 seconds")
            .setSmallIcon(R.drawable.ic_eye)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 500, 200, 500, 200, 500))
            .build()

        val manager = getSystemService(android.app.NotificationManager::class.java)
        manager.notify(ALERT_NOTIFICATION_ID, notification)
        updateNotification("🧘 Resting... (${restDurationMs / 1000}s)")
    }

    private fun dismissAlertNotification() {
        val manager = getSystemService(android.app.NotificationManager::class.java)
        manager.cancel(ALERT_NOTIFICATION_ID)
    }

    private fun buildServiceNotification(text: String): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, EyeCareApp.CHANNEL_SERVICE)
            .setContentTitle("20-20-20 Timer Active")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_eye)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(android.app.NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildServiceNotification(text))
    }

    private fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d remaining", minutes, seconds)
    }
}
