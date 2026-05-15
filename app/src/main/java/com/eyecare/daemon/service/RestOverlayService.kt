package com.eyecare.daemon.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import android.content.res.Configuration
import com.eyecare.daemon.ui.theme.BlinkTheme
import com.eyecare.daemon.util.PrefsManager

class RestOverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {

    companion object {
        private const val TAG = "RestOverlayService"
        fun start(context: Context) {
            Log.d(TAG, "start() called")
            context.startService(Intent(context, RestOverlayService::class.java))
        }
    }

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val _viewModelStore = ViewModelStore()

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = _viewModelStore

    private var windowManager: WindowManager? = null
    private var overlayView: ComposeView? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand - showing overlay")
        showOverlay()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        dismissOverlay()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        _viewModelStore.clear()
        super.onDestroy()
    }

    private fun showOverlay() {
        try {
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

            val opacity = PrefsManager.getOverlayOpacity(this)
            val dismissable = PrefsManager.isOverlayDismissable(this)
            val style = PrefsManager.getOverlayStyle(this)

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.CENTER
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                alpha = opacity
            }

            val restDurationMs = PrefsManager.getRestDurationMs(this)
            Log.d(TAG, "restDurationMs=$restDurationMs style=$style dismissable=$dismissable opacity=$opacity")

            overlayView = ComposeView(this).apply {
                setViewTreeLifecycleOwner(this@RestOverlayService)
                setViewTreeSavedStateRegistryOwner(this@RestOverlayService)
                setViewTreeViewModelStoreOwner(this@RestOverlayService)
                setContent {
                    BlinkTheme {
                        RestOverlayContent(
                            totalMs = restDurationMs,
                            dismissable = dismissable,
                            style = style,
                            onDismiss = { dismissAndStop() }
                        )
                    }
                }
            }

            windowManager?.addView(overlayView, params)
            lifecycleRegistry.currentState = Lifecycle.State.RESUMED
            Log.d(TAG, "Overlay added successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show overlay", e)
        }
    }

    private fun dismissOverlay() {
        try {
            overlayView?.let { windowManager?.removeView(it) }
        } catch (e: Exception) {
            Log.w(TAG, "Error dismissing overlay", e)
        }
        overlayView = null
    }

    private fun dismissAndStop() {
        dismissOverlay()
        stopSelf()
    }
}

// ---------------------------------------------------------------------------
// M3-Inspired Full-Screen Rest Overlay
// ---------------------------------------------------------------------------
// Design rationale (Material 3 guidelines):
//  - Color: Dark tonal surface (M3 dark scheme surface tint) with
//    teal/mint accent from the primary/tertiary palette.
//  - Motion: EaseInOutCubic for breathing (M3 Standard easing),
//    LinearEasing for continuous loops (ripples, orbit).
//    Staggered delays create layered rhythm per M3 motion guidance.
//  - Typography: Display-scale number (76sp Black), label tracking
//    (4sp letter-spacing) echoing M3 type scale emphasis.
//  - Shape: CircleShape pill badge (M3 full shape).
//  - Layout: Single focal point (the arc + icon) with clear hierarchy.
// ---------------------------------------------------------------------------

@Composable
fun RestOverlayContent(
    totalMs: Long,
    dismissable: Boolean = false,
    style: String = "expressive",
    onDismiss: () -> Unit
) {
    var remainingMs by remember { mutableLongStateOf(totalMs) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        vibrateDevice(context, longArrayOf(0, 150, 100, 200))
        val startTime = System.currentTimeMillis()
        while (remainingMs > 0) {
            kotlinx.coroutines.delay(16)
            remainingMs = (totalMs - (System.currentTimeMillis() - startTime)).coerceAtLeast(0)
        }
        vibrateDevice(context, longArrayOf(0, 200, 120, 200, 120, 300))
        onDismiss()
    }

    val progress = (remainingMs.toFloat() / totalMs).coerceIn(0f, 1f)
    val secs = ((remainingMs / 1000) + 1).coerceAtMost(totalMs / 1000)

    val inf = rememberInfiniteTransition(label = "ambient")
    val breathe by inf.animateFloat(
        initialValue = 0.92f, targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(2800, easing = EaseInOutCubic), RepeatMode.Reverse),
        label = "breathe"
    )
    val glowAlpha by inf.animateFloat(
        initialValue = 0.12f, targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(2000, easing = EaseInOutCubic), RepeatMode.Reverse),
        label = "glow"
    )
    val ripple1 by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(3600, easing = LinearEasing)), "r1")
    val ripple2 by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(3600, delayMillis = 1200, easing = LinearEasing)), "r2")
    val ripple3 by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(3600, delayMillis = 2400, easing = LinearEasing)), "r3")
    val orbitAngle by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(12000, easing = LinearEasing)), "orbit")

    val colorScheme = MaterialTheme.colorScheme
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val fadeIn by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(500, easing = EaseOutCubic),
        label = "fadeIn"
    )

    Box(modifier = Modifier.fillMaxSize().graphicsLayer(alpha = fadeIn)) {
        when (style) {
            "calm" -> CalmOverlayBody(
                progress, secs, breathe, glowAlpha,
                colorScheme.primary, colorScheme.tertiary,
                colorScheme.surface, colorScheme.surfaceContainer, colorScheme.onSurface,
                isLandscape
            )
            "minimal" -> MinimalOverlayBody(
                progress, secs,
                colorScheme.primary, colorScheme.tertiary,
                colorScheme.surface, colorScheme.onSurface,
                isLandscape
            )
            else -> ExpressiveOverlayBody(
                progress, secs, breathe, glowAlpha,
                ripple1, ripple2, ripple3, orbitAngle,
                colorScheme.primary, colorScheme.tertiary,
                colorScheme.surface, colorScheme.surfaceContainer, colorScheme.onSurface,
                isLandscape
            )
        }

        if (dismissable) {
            val isLand = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp)
                    .navigationBarsPadding(),
                contentAlignment = if (isLand) Alignment.CenterEnd else Alignment.BottomCenter
            ) {
                LargeFloatingActionButton(
                    onClick = onDismiss,
                    containerColor = colorScheme.errorContainer,
                    contentColor = colorScheme.onErrorContainer,
                    shape = CircleShape
                ) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "Dismiss",
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }
    }
}

// ── Expressive style (current full design) ─────────────────────────────────

@Composable
private fun ExpressiveOverlayBody(
    progress: Float, secs: Long, breathe: Float, glowAlpha: Float,
    ripple1: Float, ripple2: Float, ripple3: Float, orbitAngle: Float,
    primary: Color, tertiary: Color, surfaceDark: Color, surfaceMid: Color, softWhite: Color,
    isLandscape: Boolean
) {
    val arcSize = if (isLandscape) 200.dp else 280.dp
    val arcInnerSize = if (isLandscape) 180.dp else 250.dp
    val glowSize = if (isLandscape) 100.dp else 140.dp
    val iconSize = if (isLandscape) 44.dp else 60.dp
    val numberFontSize = if (isLandscape) 56.sp else 76.sp
    val titleFontSize = if (isLandscape) 22.sp else 28.sp

    @Composable
    fun ArcAndEye() {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(arcSize)) {
            Canvas(modifier = Modifier.fillMaxSize().graphicsLayer(rotationZ = orbitAngle)) {
                val dotCount = 12
                val radius = size.minDimension / 2 - 4.dp.toPx()
                for (i in 0 until dotCount) {
                    val angle = Math.toRadians((i * 360.0 / dotCount)).toFloat()
                    drawCircle(tertiary.copy(alpha = 0.25f), 2.dp.toPx(),
                        Offset(center.x + radius * kotlin.math.cos(angle), center.y + radius * kotlin.math.sin(angle)))
                }
            }
            Canvas(modifier = Modifier.size(arcInnerSize)) {
                val segments = 60; val gapDeg = 2.5f
                val segDeg = (360f / segments) - gapDeg
                val strokeW = 5.dp.toPx(); val pad = strokeW + 8.dp.toPx()
                val arcSz = Size(size.width - pad * 2, size.height - pad * 2)
                val tl = Offset(pad, pad)
                val filledExact = progress * segments
                val filledFull = filledExact.toInt(); val filledFrac = filledExact - filledFull
                for (i in 0 until segments) {
                    val t = i.toFloat() / segments
                    val segColor = Color(
                        red   = primary.red   + (tertiary.red   - primary.red)   * t,
                        green = primary.green + (tertiary.green - primary.green) * t,
                        blue  = primary.blue  + (tertiary.blue  - primary.blue)  * t,
                        alpha = 1f
                    )
                    val segAlpha = when {
                        i < filledFull  -> 0.95f
                        i == filledFull -> (filledFrac * 0.95f).coerceIn(0.07f, 0.95f)
                        else            -> 0.07f
                    }
                    drawArc(segColor.copy(alpha = segAlpha), -90f + i * (360f / segments), segDeg,
                        false, tl, arcSz, style = Stroke(strokeW, cap = StrokeCap.Round))
                }
            }
            Canvas(modifier = Modifier.size(glowSize).graphicsLayer(scaleX = breathe, scaleY = breathe)) {
                drawCircle(Brush.radialGradient(listOf(primary.copy(alpha = glowAlpha), Color.Transparent)), size.minDimension / 2)
            }
            Icon(Icons.Rounded.Visibility, "Rest", tint = tertiary,
                modifier = Modifier.size(iconSize).graphicsLayer(scaleX = breathe, scaleY = breathe))
        }
    }

    @Composable
    fun TextInfo(alignment: Alignment.Horizontal = Alignment.CenterHorizontally) {
        Column(horizontalAlignment = alignment) {
            Text("$secs", fontSize = numberFontSize, fontWeight = FontWeight.Black,
                color = softWhite, textAlign = TextAlign.Center, letterSpacing = (-2).sp)
            Text("SECONDS", fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                color = tertiary.copy(alpha = 0.7f), textAlign = TextAlign.Center, letterSpacing = 4.sp)
            Spacer(Modifier.height(if (isLandscape) 16.dp else 36.dp))
            Text("Rest Your Eyes", fontSize = titleFontSize, fontWeight = FontWeight.Bold,
                color = softWhite, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text("Look at something 20 feet away\nand breathe slowly",
                fontSize = if (isLandscape) 12.sp else 14.sp,
                color = softWhite.copy(alpha = 0.5f), textAlign = TextAlign.Center, lineHeight = 20.sp)
            Spacer(Modifier.height(if (isLandscape) 12.dp else 20.dp))
            Box(modifier = Modifier.clip(CircleShape).background(tertiary.copy(alpha = 0.1f))
                .padding(horizontal = 16.dp, vertical = 6.dp)) {
                Text("blink", fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    color = tertiary.copy(alpha = 0.8f), letterSpacing = 2.sp)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()
        .background(Brush.radialGradient(listOf(surfaceMid, surfaceDark), radius = 1600f)),
        contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f; val cy = size.height / 2f
            val maxR = size.minDimension * 0.6f
            listOf(ripple1, ripple2, ripple3).forEach { phase ->
                drawCircle(primary.copy(alpha = (1f - phase) * 0.18f), maxR * phase,
                    Offset(cx, cy), style = Stroke(1.5f))
            }
        }
        if (isLandscape) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize().padding(horizontal = 48.dp)) {
                ArcAndEye(); Spacer(Modifier.width(40.dp)); TextInfo(Alignment.Start)
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp)) {
                ArcAndEye(); Spacer(Modifier.height(44.dp)); TextInfo()
            }
        }
    }
}

// ── Calm style (smooth arc, no ripples/orbit, gentle) ─────────────────────

@Composable
private fun CalmOverlayBody(
    progress: Float, secs: Long, breathe: Float, glowAlpha: Float,
    primary: Color, tertiary: Color, surfaceDark: Color, surfaceMid: Color, softWhite: Color,
    isLandscape: Boolean
) {
    val arcSize = if (isLandscape) 200.dp else 260.dp

    @Composable
    fun ArcAndEye() {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(arcSize)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeW = 6.dp.toPx(); val pad = strokeW + 4.dp.toPx()
                val arcSz = Size(size.width - pad * 2, size.height - pad * 2)
                val tl = Offset(pad, pad)
                drawArc(primary.copy(alpha = 0.12f), -90f, 360f, false, tl, arcSz, style = Stroke(strokeW, cap = StrokeCap.Round))
                drawArc(primary, -90f, 360f * progress, false, tl, arcSz, style = Stroke(strokeW, cap = StrokeCap.Round))
            }
            Canvas(modifier = Modifier.size(if (isLandscape) 90.dp else 120.dp)
                .graphicsLayer(scaleX = breathe, scaleY = breathe)) {
                drawCircle(Brush.radialGradient(listOf(primary.copy(alpha = glowAlpha * 0.6f), Color.Transparent)), size.minDimension / 2)
            }
            Icon(Icons.Rounded.Visibility, "Rest", tint = primary.copy(alpha = 0.85f),
                modifier = Modifier.size(if (isLandscape) 40.dp else 52.dp)
                    .graphicsLayer(scaleX = breathe, scaleY = breathe))
        }
    }

    @Composable
    fun TextInfo(alignment: Alignment.Horizontal = Alignment.CenterHorizontally) {
        Column(horizontalAlignment = alignment) {
            Text("$secs", fontSize = if (isLandscape) 56.sp else 72.sp, fontWeight = FontWeight.Light,
                color = softWhite, textAlign = TextAlign.Center)
            Text("seconds", fontSize = 13.sp, color = primary.copy(alpha = 0.7f),
                textAlign = TextAlign.Center, letterSpacing = 2.sp)
            Spacer(Modifier.height(if (isLandscape) 12.dp else 28.dp))
            Text("Rest Your Eyes", fontSize = if (isLandscape) 20.sp else 26.sp, fontWeight = FontWeight.Medium,
                color = softWhite, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text("Look 20 feet away and breathe", fontSize = if (isLandscape) 12.sp else 14.sp,
                color = softWhite.copy(alpha = 0.45f), textAlign = TextAlign.Center)
        }
    }

    Box(modifier = Modifier.fillMaxSize()
        .background(Brush.radialGradient(listOf(surfaceMid, surfaceDark), radius = 1400f)),
        contentAlignment = Alignment.Center) {
        if (isLandscape) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize().padding(horizontal = 48.dp)) {
                ArcAndEye(); Spacer(Modifier.width(40.dp)); TextInfo(Alignment.Start)
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp)) {
                ArcAndEye(); Spacer(Modifier.height(40.dp)); TextInfo()
            }
        }
    }
}

// ── Minimal style (clean, just progress indicator + number) ───────────────

@Composable
private fun MinimalOverlayBody(
    progress: Float, secs: Long,
    primary: Color, tertiary: Color, surfaceDark: Color, softWhite: Color,
    isLandscape: Boolean
) {
    Box(modifier = Modifier.fillMaxSize().background(surfaceDark), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.size(if (isLandscape) 220.dp else 300.dp),
            color = primary,
            trackColor = primary.copy(alpha = 0.1f),
            strokeWidth = 4.dp,
            strokeCap = StrokeCap.Round
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$secs",
                fontSize = if (isLandscape) 80.sp else 96.sp,
                fontWeight = FontWeight.Thin,
                color = softWhite,
                letterSpacing = (-4).sp)
            Text("sec", fontSize = 14.sp, fontWeight = FontWeight.Normal,
                color = primary.copy(alpha = 0.7f), letterSpacing = 3.sp)
            Spacer(Modifier.height(if (isLandscape) 8.dp else 16.dp))
            Text("Rest your eyes",
                fontSize = if (isLandscape) 16.sp else 18.sp,
                fontWeight = FontWeight.Normal,
                color = softWhite.copy(alpha = 0.55f))
        }
    }
}

internal fun vibrateDevice(context: Context, pattern: LongArray) {
    try {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
    } catch (_: Exception) { }
}
