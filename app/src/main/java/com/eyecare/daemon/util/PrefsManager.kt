package com.eyecare.daemon.util

import android.content.Context

object PrefsManager {

    private const val PREFS_NAME = "eye_care_prefs"
    private const val KEY_AUTO_START = "auto_start_on_boot"
    private const val KEY_WORK_DURATION_MS = "work_duration_ms"
    private const val KEY_REST_DURATION_MS = "rest_duration_ms"
    private const val KEY_ALERT_MODE = "alert_mode"            // "both" | "overlay" | "notification"
    private const val KEY_OVERLAY_DISMISSABLE = "overlay_dismissable"
    private const val KEY_OVERLAY_OPACITY = "overlay_opacity"  // 0.4..1.0
    private const val KEY_OVERLAY_STYLE = "overlay_style"      // "expressive" | "calm" | "minimal"

    private const val DEFAULT_WORK_DURATION_MS = 20 * 60 * 1000L
    private const val DEFAULT_REST_DURATION_MS = 20 * 1000L

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isAutoStartEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO_START, false)

    fun setAutoStartEnabled(context: Context, enabled: Boolean) =
        prefs(context).edit().putBoolean(KEY_AUTO_START, enabled).apply()

    fun getWorkDurationMs(context: Context): Long =
        prefs(context).getLong(KEY_WORK_DURATION_MS, DEFAULT_WORK_DURATION_MS)

    fun setWorkDurationMs(context: Context, value: Long) =
        prefs(context).edit().putLong(KEY_WORK_DURATION_MS, value).apply()

    fun getRestDurationMs(context: Context): Long =
        prefs(context).getLong(KEY_REST_DURATION_MS, DEFAULT_REST_DURATION_MS)

    fun setRestDurationMs(context: Context, value: Long) =
        prefs(context).edit().putLong(KEY_REST_DURATION_MS, value).apply()

    fun getAlertMode(context: Context): String =
        prefs(context).getString(KEY_ALERT_MODE, "both") ?: "both"

    fun setAlertMode(context: Context, mode: String) =
        prefs(context).edit().putString(KEY_ALERT_MODE, mode).apply()

    fun isOverlayDismissable(context: Context): Boolean =
        prefs(context).getBoolean(KEY_OVERLAY_DISMISSABLE, false)

    fun setOverlayDismissable(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_OVERLAY_DISMISSABLE, value).apply()

    fun getOverlayOpacity(context: Context): Float =
        prefs(context).getFloat(KEY_OVERLAY_OPACITY, 1.0f)

    fun setOverlayOpacity(context: Context, value: Float) =
        prefs(context).edit().putFloat(KEY_OVERLAY_OPACITY, value).apply()

    fun getOverlayStyle(context: Context): String =
        prefs(context).getString(KEY_OVERLAY_STYLE, "expressive") ?: "expressive"

    fun setOverlayStyle(context: Context, style: String) =
        prefs(context).edit().putString(KEY_OVERLAY_STYLE, style).apply()
}
