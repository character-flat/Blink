package com.eyecare.daemon.util

import android.content.Context

object PrefsManager {

    private const val PREFS_NAME = "eye_care_prefs"
    private const val KEY_AUTO_START = "auto_start_on_boot"
    private const val KEY_WORK_DURATION_MS = "work_duration_ms"
    private const val KEY_REST_DURATION_MS = "rest_duration_ms"

    private const val DEFAULT_WORK_DURATION_MS = 20 * 60 * 1000L
    private const val DEFAULT_REST_DURATION_MS = 20 * 1000L

    fun isAutoStartEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_START, false)
    }

    fun setAutoStartEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTO_START, enabled)
            .apply()
    }

    fun getWorkDurationMs(context: Context): Long {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_WORK_DURATION_MS, DEFAULT_WORK_DURATION_MS)
    }

    fun setWorkDurationMs(context: Context, value: Long) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_WORK_DURATION_MS, value)
            .apply()
    }

    fun getRestDurationMs(context: Context): Long {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_REST_DURATION_MS, DEFAULT_REST_DURATION_MS)
    }

    fun setRestDurationMs(context: Context, value: Long) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_REST_DURATION_MS, value)
            .apply()
    }
}
