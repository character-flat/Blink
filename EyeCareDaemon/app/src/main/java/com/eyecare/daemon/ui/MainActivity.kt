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
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Settings
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eyecare.daemon.service.EyeCareService
import com.eyecare.daemon.ui.theme.EyeCareDaemonTheme
import com.eyecare.daemon.util.PrefsManager
import com.eyecare.daemon.util.ShizukuUtils
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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
            EyeCareDaemonTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    EyeCareScreen(
                        onStartService = ::startEyeCareService,
                        onStopService = ::stopEyeCareService,
                        onAutoStartChanged = { PrefsManager.setAutoStartEnabled(this, it) },
                        isAutoStartEnabled = PrefsManager.isAutoStartEnabled(this),
                        context = this
                    )
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
    onAutoStartChanged: (Boolean) -> Unit,
    isAutoStartEnabled: Boolean,
    context: android.content.Context
) {
    val remainingMs by EyeCareService.remainingMs.collectAsStateWithLifecycle()
    val isResting by EyeCareService.isResting.collectAsStateWithLifecycle()
    val isRunning by EyeCareService.isRunning.collectAsStateWithLifecycle()

    var autoStart by remember { mutableStateOf(isAutoStartEnabled) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val workDurationMs = PrefsManager.getWorkDurationMs(context)
    val totalDuration = workDurationMs.toFloat()
    val progress = if (isResting) 0f else (remainingMs / totalDuration).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(targetValue = progress, label = "progress")

    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val errorColor = MaterialTheme.colorScheme.error
    val outlineColor = MaterialTheme.colorScheme.outline

    // State-dependent colors
    val arcColorStart by animateColorAsState(
        targetValue = when {
            isResting -> errorColor
            isRunning && progress < 0.15f -> tertiaryColor
            isRunning -> primaryColor
            else -> outlineColor
        },
        animationSpec = tween(600),
        label = "arcColorStart"
    )
    val arcColorEnd by animateColorAsState(
        targetValue = when {
            isResting -> errorColor.copy(alpha = 0.5f)
            isRunning && progress < 0.15f -> errorColor.copy(alpha = 0.7f)
            isRunning -> tertiaryColor.copy(alpha = 0.6f)
            else -> outlineColor.copy(alpha = 0.4f)
        },
        animationSpec = tween(600),
        label = "arcColorEnd"
    )

    val glowColor by animateColorAsState(
        targetValue = when {
            isResting -> errorColor.copy(alpha = 0.3f)
            isRunning -> primaryColor.copy(alpha = 0.10f)
            else -> Color.Transparent
        },
        animationSpec = tween(600),
        label = "glow"
    )

    val fabContainerColor by animateColorAsState(
        targetValue = if (isRunning) MaterialTheme.colorScheme.errorContainer
        else MaterialTheme.colorScheme.primaryContainer,
        animationSpec = tween(400),
        label = "fabBg"
    )
    val fabContentColor by animateColorAsState(
        targetValue = if (isRunning) MaterialTheme.colorScheme.onErrorContainer
        else MaterialTheme.colorScheme.onPrimaryContainer,
        animationSpec = tween(400),
        label = "fabFg"
    )

    // Settings bottom sheet
    if (showSettingsSheet) {
        SettingsBottomSheet(
            context = context,
            onDismiss = { showSettingsSheet = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
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
                val radius = (size.width - strokeWidth) / 2
                val center = Offset(size.width / 2, size.height / 2)
                val totalSegments = 60
                val gapAngle = 2f // gap between segments in degrees
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

        Spacer(modifier = Modifier.height(36.dp))

        // Settings Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            onClick = { showSettingsSheet = true }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                    Column {
                        Text(
                            text = "Timer Settings",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        val workMin = (workDurationMs / 60000).toInt()
                        val restSec = (PrefsManager.getRestDurationMs(context) / 1000).toInt()
                        Text(
                            text = "${workMin}min work · ${restSec}s rest",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = "Open settings",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Battery Optimization Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            var isIgnoring by remember {
                mutableStateOf(pm.isIgnoringBatteryOptimizations(context.packageName))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Shield,
                        contentDescription = null,
                        tint = if (isIgnoring) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(26.dp)
                    )
                    Column {
                        Text(
                            text = "Battery Optimization",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (isIgnoring) "Unrestricted — timers are reliable"
                                   else "Restricted — tap Disable to fix",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (!isIgnoring) {
                    FilledTonalButton(
                        onClick = {
                            @Suppress("BatteryLife")
                            val intent = Intent(
                                android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                android.net.Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                            // re-check after a short delay
                            scope.launch {
                                kotlinx.coroutines.delay(1500)
                                isIgnoring = pm.isIgnoringBatteryOptimizations(context.packageName)
                            }
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Disable")
                    }
                } else {
                    StatusDot(active = true)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Auto-start Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PowerSettingsNew,
                        contentDescription = null,
                        tint = if (autoStart) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(26.dp)
                    )
                    Column {
                        Text(
                            text = "Start on Boot",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Resume automatically after reboot",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = autoStart,
                    onCheckedChange = {
                        autoStart = it
                        onAutoStartChanged(it)
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBottomSheet(
    context: android.content.Context,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var workMinutes by remember {
        mutableFloatStateOf((PrefsManager.getWorkDurationMs(context) / 60000f))
    }
    var restSeconds by remember {
        mutableFloatStateOf((PrefsManager.getRestDurationMs(context) / 1000f))
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Timer Settings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Work duration slider
            Text(
                text = "Work Duration",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${workMinutes.roundToInt()} minutes",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.fillMaxWidth()
            )
            Slider(
                value = workMinutes,
                onValueChange = { workMinutes = it },
                onValueChangeFinished = {
                    PrefsManager.setWorkDurationMs(
                        context,
                        workMinutes.roundToInt() * 60 * 1000L
                    )
                },
                valueRange = 1f..60f,
                steps = 58,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Rest duration slider
            Text(
                text = "Rest Duration",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${restSeconds.roundToInt()} seconds",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.fillMaxWidth()
            )
            Slider(
                value = restSeconds,
                onValueChange = { restSeconds = it },
                onValueChangeFinished = {
                    PrefsManager.setRestDurationMs(
                        context,
                        restSeconds.roundToInt() * 1000L
                    )
                },
                valueRange = 10f..60f,
                steps = 49,
                modifier = Modifier.fillMaxWidth()
            )
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
