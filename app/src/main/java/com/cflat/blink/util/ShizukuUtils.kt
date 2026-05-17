package com.cflat.blink.util

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

object ShizukuUtils {

    private const val TAG = "ShizukuUtils"
    private const val SHIZUKU_PERMISSION_CODE = 1001

    private val _shizukuStatus = MutableStateFlow(ShizukuStatus.UNKNOWN)
    val shizukuStatus: StateFlow<ShizukuStatus> = _shizukuStatus.asStateFlow()

    enum class ShizukuStatus {
        UNKNOWN,
        NOT_INSTALLED,
        NOT_RUNNING,
        PERMISSION_DENIED,
        READY,
        WHITELISTED
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        Log.d(TAG, "Shizuku binder received")
        checkPermission()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        Log.d(TAG, "Shizuku binder dead")
        _shizukuStatus.value = ShizukuStatus.NOT_RUNNING
    }

    private val permissionResultListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == SHIZUKU_PERMISSION_CODE) {
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    _shizukuStatus.value = ShizukuStatus.READY
                } else {
                    _shizukuStatus.value = ShizukuStatus.PERMISSION_DENIED
                }
            }
        }

    fun initialize() {
        Shizuku.addBinderReceivedListener(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(permissionResultListener)

        if (Shizuku.pingBinder()) {
            checkPermission()
        } else {
            _shizukuStatus.value = ShizukuStatus.NOT_RUNNING
        }
    }

    fun cleanup() {
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        Shizuku.removeRequestPermissionResultListener(permissionResultListener)
    }

    private fun checkPermission() {
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            _shizukuStatus.value = ShizukuStatus.READY
        } else if (Shizuku.shouldShowRequestPermissionRationale()) {
            _shizukuStatus.value = ShizukuStatus.PERMISSION_DENIED
        } else {
            Shizuku.requestPermission(SHIZUKU_PERMISSION_CODE)
        }
    }

    fun requestPermission() {
        if (Shizuku.pingBinder()) {
            Shizuku.requestPermission(SHIZUKU_PERMISSION_CODE)
        }
    }

    suspend fun whitelistFromBatteryOptimization(context: Context): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val packageName = context.packageName
                val command = "cmd deviceidle whitelist +$packageName"

                val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val output = reader.readText()
                val exitCode = process.waitFor()

                if (exitCode == 0) {
                    Log.d(TAG, "Battery whitelist success: $output")
                    _shizukuStatus.value = ShizukuStatus.WHITELISTED
                    Result.success(Unit)
                } else {
                    val error = BufferedReader(InputStreamReader(process.errorStream)).readText()
                    Log.e(TAG, "Battery whitelist failed: $error")
                    Result.failure(RuntimeException("Exit code $exitCode: $error"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Shizuku command failed", e)
                Result.failure(e)
            }
        }

    suspend fun checkIfWhitelisted(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val packageName = context.packageName
            val command = "cmd deviceidle whitelist"

            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readText()
            process.waitFor()

            output.contains(packageName)
        } catch (e: Exception) {
            Log.e(TAG, "Whitelist check failed", e)
            false
        }
    }
}
