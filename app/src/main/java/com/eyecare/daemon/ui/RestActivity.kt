package com.eyecare.daemon.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.eyecare.daemon.service.RestOverlayContent
import com.eyecare.daemon.ui.theme.BlinkTheme
import com.eyecare.daemon.util.PrefsManager

class RestActivity : ComponentActivity() {

    private var restDone = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Fullscreen immersive
        window.insetsController?.let { controller ->
            controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            controller.systemBarsBehavior =
                WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        window.attributes.layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES

        val opacity = PrefsManager.getOverlayOpacity(this)
        window.attributes = window.attributes.apply { alpha = opacity }

        val restDurationMs = PrefsManager.getRestDurationMs(this)
        val dismissable = PrefsManager.isOverlayDismissable(this)
        val style = PrefsManager.getOverlayStyle(this)

        setContent {
            BlinkTheme {
                RestOverlayContent(
                    totalMs = restDurationMs,
                    dismissable = dismissable,
                    style = style,
                    onDismiss = { finishRest() }
                )
            }
        }
    }

    private fun finishRest() {
        restDone = true
        finish()
    }

    // Called when user presses Home button — relaunch if non-dismissable
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (!restDone && !PrefsManager.isOverlayDismissable(this)) {
            val intent = Intent(this, RestActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }
            startActivity(intent)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (PrefsManager.isOverlayDismissable(this)) {
            finishRest()
        }
        // Block back button in non-dismissable mode
    }
}
