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
import com.eyecare.daemon.ui.theme.EyeCareDaemonTheme
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
            }

            val restDurationMs = PrefsManager.getRestDurationMs(this)
            Log.d(TAG, "restDurationMs=$restDurationMs")

            overlayView = ComposeView(this).apply {
                setViewTreeLifecycleOwner(this@RestOverlayService)
                setViewTreeSavedStateRegistryOwner(this@RestOverlayService)
                setViewTreeViewModelStoreOwner(this@RestOverlayService)
                setContent {
                    EyeCareDaemonTheme {
                        RestOverlayContent(
                            totalMs = restDurationMs,
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
fun RestOverlayContent(totalMs: Long, onDismiss: () -> Unit) {
    var remainingMs by remember { mutableLongStateOf(totalMs) }
    val context = LocalContext.current

    // Vibrate at start; vibrate at end before dismissing
    LaunchedEffect(Unit) {
        vibrateDevice(context, longArrayOf(0, 150, 100, 200))
        val startTime = System.currentTimeMillis()
        while (remainingMs > 0) {
            kotlinx.coroutines.delay(16) // ~60fps for buttery smooth arc
            remainingMs = (totalMs - (System.currentTimeMillis() - startTime)).coerceAtLeast(0)
        }
        vibrateDevice(context, longArrayOf(0, 200, 120, 200, 120, 300))
        onDismiss()
    }

    // Direct continuous progress — no animation layer, already 60fps smooth
    val progress = (remainingMs.toFloat() / totalMs).coerceIn(0f, 1f)

    // M3 expressive motion: infinite transitions for ambient feel
    val inf = rememberInfiniteTransition(label = "ambientMotion")

    // Breathing scale (M3 Standard easing for enter/exit emphasis)
    val breathe by inf.animateFloat(
        initialValue = 0.92f, targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            tween(2800, easing = EaseInOutCubic), RepeatMode.Reverse
        ), label = "breathe"
    )

    // 3 staggered expanding ripple rings
    val ripple1 by inf.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3600, easing = LinearEasing)),
        label = "r1"
    )
    val ripple2 by inf.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(3600, delayMillis = 1200, easing = LinearEasing)
        ), label = "r2"
    )
    val ripple3 by inf.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(3600, delayMillis = 2400, easing = LinearEasing)
        ), label = "r3"
    )

    // Glow pulse behind eye icon
    val glowAlpha by inf.animateFloat(
        initialValue = 0.12f, targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            tween(2000, easing = EaseInOutCubic), RepeatMode.Reverse
        ), label = "glow"
    )

    // Slow orbit rotation for decorative outer dots
    val orbitAngle by inf.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(12000, easing = LinearEasing)),
        label = "orbit"
    )

    val secs = ((remainingMs / 1000) + 1).coerceAtMost(totalMs / 1000)

    // M3 dynamic colors from system wallpaper / accent
    val colorScheme = MaterialTheme.colorScheme
    val primary     = colorScheme.primary
    val tertiary    = colorScheme.tertiary
    val surfaceDark = colorScheme.surface
    val surfaceMid  = colorScheme.surfaceContainer
    val softWhite   = colorScheme.onSurface

    // Fade-in on appear (M3 Standard easing for enter)
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val fadeIn by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(500, easing = EaseOutCubic),
        label = "fadeIn"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer(alpha = fadeIn)
            .background(
                Brush.radialGradient(
                    colors = listOf(surfaceMid, surfaceDark),
                    radius = 1600f
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // ── Layer 1: Expanding ripple rings ──────────────────────────
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val maxR = size.minDimension * 0.6f

            listOf(ripple1, ripple2, ripple3).forEach { phase ->
                val r = maxR * phase
                val a = (1f - phase) * 0.18f
                drawCircle(
                    color = primary.copy(alpha = a),
                    radius = r,
                    center = Offset(cx, cy),
                    style = Stroke(width = 1.5f)
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
        ) {
            // ── Layer 2: Segmented countdown arc + breathing eye ─────
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(280.dp)
            ) {
                // Outer rotating decorative dots
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(rotationZ = orbitAngle)
                ) {
                    val dotCount = 12
                    val radius = size.minDimension / 2 - 4.dp.toPx()
                    for (i in 0 until dotCount) {
                        val angle = Math.toRadians((i * 360.0 / dotCount)).toFloat()
                        val x = center.x + radius * kotlin.math.cos(angle)
                        val y = center.y + radius * kotlin.math.sin(angle)
                        drawCircle(
                            color = tertiary.copy(alpha = 0.25f),
                            radius = 2.dp.toPx(),
                            center = Offset(x, y)
                        )
                    }
                }

                // 60-segment progress arc — smooth continuous fill
                Canvas(modifier = Modifier.size(250.dp)) {
                    val segments = 60
                    val gapDeg = 2.5f
                    val segDeg = (360f / segments) - gapDeg
                    val strokeW = 5.dp.toPx()
                    val pad = strokeW + 8.dp.toPx()
                    val arcSz = Size(size.width - pad * 2, size.height - pad * 2)
                    val tl = Offset(pad, pad)
                    val filledExact = progress * segments
                    val filledFull = filledExact.toInt()
                    val filledFrac = filledExact - filledFull

                    for (i in 0 until segments) {
                        val angle = -90f + i * (360f / segments)
                        val segAlpha: Float
                        val segColor: Color

                        when {
                            i < filledFull -> {
                                // Fully lit segment — gradient from primary→tertiary
                                val t = i.toFloat() / segments
                                segColor = Color(
                                    red   = primary.red   + (tertiary.red   - primary.red)   * t,
                                    green = primary.green + (tertiary.green - primary.green) * t,
                                    blue  = primary.blue  + (tertiary.blue  - primary.blue)  * t,
                                    alpha = 1f
                                )
                                segAlpha = 0.95f
                            }
                            i == filledFull -> {
                                // Partially lit segment — smooth fade
                                val t = i.toFloat() / segments
                                segColor = Color(
                                    red   = primary.red   + (tertiary.red   - primary.red)   * t,
                                    green = primary.green + (tertiary.green - primary.green) * t,
                                    blue  = primary.blue  + (tertiary.blue  - primary.blue)  * t,
                                    alpha = 1f
                                )
                                segAlpha = (filledFrac * 0.95f).coerceIn(0.07f, 0.95f)
                            }
                            else -> {
                                segColor = Color.White
                                segAlpha = 0.07f
                            }
                        }

                        drawArc(
                            color = segColor.copy(alpha = segAlpha),
                            startAngle = angle,
                            sweepAngle = segDeg,
                            useCenter = false,
                            topLeft = tl,
                            size = arcSz,
                            style = Stroke(width = strokeW, cap = StrokeCap.Round)
                        )
                    }
                }

                // Radial glow disc (breathing)
                Canvas(
                    modifier = Modifier
                        .size(140.dp)
                        .graphicsLayer(scaleX = breathe, scaleY = breathe)
                ) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(primary.copy(alpha = glowAlpha), Color.Transparent)
                        ),
                        radius = size.minDimension / 2
                    )
                }

                // Center eye icon
                Icon(
                    imageVector = Icons.Rounded.Visibility,
                    contentDescription = "Rest your eyes",
                    tint = tertiary,
                    modifier = Modifier
                        .size(60.dp)
                        .graphicsLayer(scaleX = breathe, scaleY = breathe)
                )
            }

            Spacer(Modifier.height(44.dp))

            // ── Layer 3: Large countdown number ──────────────────────
            Text(
                text = "$secs",
                fontSize = 76.sp,
                fontWeight = FontWeight.Black,
                color = softWhite,
                textAlign = TextAlign.Center,
                letterSpacing = (-2).sp
            )
            Text(
                text = "SECONDS",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = tertiary.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                letterSpacing = 4.sp
            )

            Spacer(Modifier.height(36.dp))

            // ── Layer 4: Title + subtitle ────────────────────────────
            Text(
                text = "Rest Your Eyes",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = softWhite,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Look at something 20 feet away\nand breathe slowly",
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                color = softWhite.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(Modifier.height(20.dp))

            // ── Layer 5: Pill badge ──────────────────────────────────
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(tertiary.copy(alpha = 0.1f))
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "20 \u2022 20 \u2022 20",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = tertiary.copy(alpha = 0.8f),
                    letterSpacing = 2.sp
                )
            }
        }
    }
}

private fun vibrateDevice(context: Context, pattern: LongArray) {
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
