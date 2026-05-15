package com.eyecare.daemon.ui

import android.Manifest
import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eyecare.daemon.service.EyeCareService
import com.eyecare.daemon.ui.theme.BlinkTheme
import com.eyecare.daemon.util.PrefsManager
import com.eyecare.daemon.util.ShizukuUtils

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* handled by system */ }

    private val exactAlarmReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == EyeCareService.ACTION_NEED_EXACT_ALARM_PERMISSION) {
                openExactAlarmSettings()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        requestNotificationPermission()
        checkExactAlarmPermission()
        checkOverlayPermission()
        ShizukuUtils.initialize()

        registerReceiver(
            exactAlarmReceiver,
            IntentFilter(EyeCareService.ACTION_NEED_EXACT_ALARM_PERMISSION),
            RECEIVER_NOT_EXPORTED
        )

        setContent {
            BlinkTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var showSettings by remember { mutableStateOf(false) }
                    if (showSettings) {
                        SettingsScreen(
                            context = this@MainActivity,
                            onBack = { showSettings = false }
                        )
                    } else {
                        EyeCareScreen(
                            onStartService = ::startEyeCareService,
                            onStopService = ::stopEyeCareService,
                            onOpenSettings = { showSettings = true },
                            context = this
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(exactAlarmReceiver)
        ShizukuUtils.cleanup()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun checkExactAlarmPermission() {
        val alarmManager = getSystemService(AlarmManager::class.java)
        if (!alarmManager.canScheduleExactAlarms()) {
            openExactAlarmSettings()
        }
    }

    private fun openExactAlarmSettings() {
        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.parse("package:$packageName")
        }
        startActivity(intent)
    }

    private fun checkOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        }
    }

    private fun startEyeCareService() {
        val intent = Intent(this, EyeCareService::class.java)
        startForegroundService(intent)
    }

    private fun stopEyeCareService() {
        val intent = Intent(this, EyeCareService::class.java).apply {
            action = EyeCareService.ACTION_STOP_TIMER
        }
        startService(intent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EyeCareScreen(
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onOpenSettings: () -> Unit,
    context: android.content.Context
) {
    val remainingMs by EyeCareService.remainingMs.collectAsStateWithLifecycle()
    val isResting by EyeCareService.isResting.collectAsStateWithLifecycle()
    val isRunning by EyeCareService.isRunning.collectAsStateWithLifecycle()

    val workDurationMs = PrefsManager.getWorkDurationMs(context)
    val totalDuration = workDurationMs.toFloat()
    val progress = if (isResting) 0f else (remainingMs / totalDuration).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(targetValue = progress, label = "progress")

    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val errorColor = MaterialTheme.colorScheme.error
    val outlineColor = MaterialTheme.colorScheme.outline

    val arcColorStart by animateColorAsState(
        targetValue = when {
            isResting -> errorColor
            isRunning && progress < 0.15f -> tertiaryColor
            isRunning -> primaryColor
            else -> outlineColor
        },
        animationSpec = tween(600), label = "arcColorStart"
    )

    val glowColor by animateColorAsState(
        targetValue = when {
            isResting -> errorColor.copy(alpha = 0.3f)
            isRunning -> primaryColor.copy(alpha = 0.10f)
            else -> Color.Transparent
        },
        animationSpec = tween(600), label = "glow"
    )

    val fabContainerColor by animateColorAsState(
        targetValue = if (isRunning) MaterialTheme.colorScheme.errorContainer
        else MaterialTheme.colorScheme.primaryContainer,
        animationSpec = tween(400), label = "fabBg"
    )
    val fabContentColor by animateColorAsState(
        targetValue = if (isRunning) MaterialTheme.colorScheme.onErrorContainer
        else MaterialTheme.colorScheme.onPrimaryContainer,
        animationSpec = tween(400), label = "fabFg"
    )

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onOpenSettings,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Icon(Icons.Rounded.Settings, contentDescription = "Settings")
            }
        },
        floatingActionButtonPosition = FabPosition.End
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.weight(1f))

            // Header
            Text(
                text = "Blink",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (isResting) "Time to rest your eyes"
                       else if (isRunning) "Protecting your vision"
                       else "Start to protect your eyes",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Main Timer Ring
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(280.dp)
                    .clip(CircleShape)
                    .background(glowColor)
            ) {
                Canvas(modifier = Modifier.size(250.dp)) {
                    val strokeWidth = 12.dp.toPx()
                    val totalSegments = 60
                    val gapAngle = 2f
                    val segmentAngle = (360f / totalSegments) - gapAngle
                    val filledSegments = (totalSegments * animatedProgress).toInt()

                    for (i in 0 until totalSegments) {
                        val startAngle = -90f + i * (segmentAngle + gapAngle)
                        val isActive = i < filledSegments
                        val segmentColor = if (isActive) arcColorStart else arcColorStart.copy(alpha = 0.08f)

                        drawArc(
                            color = segmentColor,
                            startAngle = startAngle,
                            sweepAngle = segmentAngle,
                            useCenter = false,
                            topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                            size = Size(size.width - strokeWidth, size.height - strokeWidth),
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }
                }

                // Center content
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (isResting) {
                        PulsingEye()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Look Away",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "20ft for 20s",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    } else {
                        val minutes = (remainingMs / 1000) / 60
                        val seconds = (remainingMs / 1000) % 60
                        Text(
                            text = String.format("%02d", minutes),
                            fontSize = 72.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = String.format("%02d", seconds),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            letterSpacing = 2.sp
                        )
                        if (!isRunning) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "PAUSED",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.outline,
                                textAlign = TextAlign.Center,
                                letterSpacing = 3.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            // Start/Stop FAB
            LargeFloatingActionButton(
                onClick = { if (isRunning) onStopService() else onStartService() },
                containerColor = fabContainerColor,
                contentColor = fabContentColor,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.size(72.dp)
            ) {
                Icon(
                    imageVector = if (isRunning) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                    contentDescription = if (isRunning) "Stop" else "Start",
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun PulsingEye() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eyeScale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eyeAlpha"
    )

    Icon(
        imageVector = Icons.Rounded.Visibility,
        contentDescription = "Eye",
        tint = MaterialTheme.colorScheme.error.copy(alpha = alpha),
        modifier = Modifier.size((48 * scale).dp)
    )
}

@Composable
fun StatusDot(active: Boolean) {
    val color by animateColorAsState(
        targetValue = if (active) Color(0xFF4CAF50) else Color(0xFFE53935),
        label = "dotColor"
    )
    val infiniteTransition = rememberInfiniteTransition(label = "dot")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotAlpha"
    )

    Box(
        modifier = Modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = if (active) 1f else dotAlpha))
    )
}
