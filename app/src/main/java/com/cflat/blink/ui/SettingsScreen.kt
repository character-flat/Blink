package com.cflat.blink.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cflat.blink.util.PrefsManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    context: Context,
    onBack: () -> Unit
) {
    // ── State ──────────────────────────────────────────────────────────
    var workMinutes by remember {
        mutableFloatStateOf(PrefsManager.getWorkDurationMs(context) / 60000f)
    }
    var restSeconds by remember {
        mutableFloatStateOf(PrefsManager.getRestDurationMs(context) / 1000f)
    }
    var alertMode by remember { mutableStateOf(PrefsManager.getAlertMode(context)) }
    var overlayDismissable by remember { mutableStateOf(PrefsManager.isOverlayDismissable(context)) }
    var overlayOpacity by remember { mutableFloatStateOf(PrefsManager.getOverlayOpacity(context)) }
    var overlayStyle by remember { mutableStateOf(PrefsManager.getOverlayStyle(context)) }
    var autoStart by remember { mutableStateOf(PrefsManager.isAutoStartEnabled(context)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {

            // ── Timers ───────────────────────────────────────────────
            SettingsSectionHeader("Timers", Icons.Rounded.Timer)
            SettingsCard {
                SliderRow(
                    label = "Work Duration",
                    value = workMinutes,
                    displayText = "${workMinutes.roundToInt()} min",
                    range = 1f..60f,
                    steps = 58,
                    onValueChange = { workMinutes = it },
                    onValueChangeFinished = {
                        PrefsManager.setWorkDurationMs(
                            context, workMinutes.roundToInt() * 60 * 1000L
                        )
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SliderRow(
                    label = "Rest Duration",
                    value = restSeconds,
                    displayText = "${restSeconds.roundToInt()} sec",
                    range = 10f..60f,
                    steps = 49,
                    onValueChange = { restSeconds = it },
                    onValueChangeFinished = {
                        PrefsManager.setRestDurationMs(
                            context, restSeconds.roundToInt() * 1000L
                        )
                    }
                )
            }

            Spacer(Modifier.height(12.dp))

            // ── Alert Mode ───────────────────────────────────────────
            SettingsSectionHeader("Alert", Icons.Rounded.Notifications)
            SettingsCard {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Alert Mode",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "How you're reminded to take a break",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    @OptIn(ExperimentalMaterial3Api::class)
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        listOf(
                            "both" to "Both",
                            "overlay" to "Overlay",
                            "notification" to "Notif"
                        ).forEachIndexed { index, (key, label) ->
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(index, 3),
                                onClick = {
                                    alertMode = key
                                    PrefsManager.setAlertMode(context, key)
                                },
                                selected = alertMode == key,
                                label = { Text(label, style = MaterialTheme.typography.labelMedium) }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Overlay Appearance ───────────────────────────────────
            SettingsSectionHeader("Overlay", Icons.Rounded.Layers)
            SettingsCard {
                // Dismissable toggle
                ListItem(
                    headlineContent = { Text("Dismissable", fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("Show a close button during rest") },
                    leadingContent = {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = overlayDismissable,
                            onCheckedChange = {
                                overlayDismissable = it
                                PrefsManager.setOverlayDismissable(context, it)
                            }
                        )
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                // Opacity slider
                SliderRow(
                    label = "Opacity",
                    value = overlayOpacity,
                    displayText = "${(overlayOpacity * 100).roundToInt()}%",
                    range = 0.3f..1.0f,
                    steps = 13,
                    onValueChange = { overlayOpacity = it },
                    onValueChangeFinished = {
                        PrefsManager.setOverlayOpacity(context, overlayOpacity)
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                // Style selector
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Overlay Style",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Visual theme of the rest screen",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    @OptIn(ExperimentalMaterial3Api::class)
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        listOf(
                            "expressive" to "Expressive",
                            "calm" to "Calm",
                            "minimal" to "Minimal"
                        ).forEachIndexed { index, (key, label) ->
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(index, 3),
                                onClick = {
                                    overlayStyle = key
                                    PrefsManager.setOverlayStyle(context, key)
                                },
                                selected = overlayStyle == key,
                                label = { Text(label, style = MaterialTheme.typography.labelMedium) }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── System ───────────────────────────────────────────────
            SettingsSectionHeader("System", Icons.Rounded.PowerSettingsNew)
            SettingsCard {
                // Battery Optimization
                val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                var isIgnoring by remember {
                    mutableStateOf(pm.isIgnoringBatteryOptimizations(context.packageName))
                }
                val scope = rememberCoroutineScope()

                ListItem(
                    headlineContent = { Text("Battery Optimization", fontWeight = FontWeight.SemiBold) },
                    supportingContent = {
                        Text(
                            if (isIgnoring) "Unrestricted — timers are reliable"
                            else "Restricted — tap to fix"
                        )
                    },
                    leadingContent = {
                        Icon(
                            Icons.Rounded.Shield,
                            contentDescription = null,
                            tint = if (isIgnoring) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error
                        )
                    },
                    trailingContent = {
                        if (!isIgnoring) {
                            FilledTonalButton(
                                onClick = {
                                    @Suppress("BatteryLife")
                                    val intent = Intent(
                                        AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                        android.net.Uri.parse("package:${context.packageName}")
                                    )
                                    context.startActivity(intent)
                                    scope.launch {
                                        delay(1500)
                                        isIgnoring = pm.isIgnoringBatteryOptimizations(context.packageName)
                                    }
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) { Text("Disable") }
                        } else {
                            Icon(
                                Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                ListItem(
                    headlineContent = { Text("Start on Boot", fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("Resume automatically after reboot") },
                    leadingContent = {
                        Icon(
                            Icons.Rounded.PowerSettingsNew,
                            contentDescription = null,
                            tint = if (autoStart) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = autoStart,
                            onCheckedChange = {
                                autoStart = it
                                PrefsManager.setAutoStartEnabled(context, it)
                            }
                        )
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                )
            }

            Spacer(Modifier.height(12.dp))

            // ── OEM-specific guidance ────────────────────────────────
            val oemGuide = getOemBatteryGuide()
            if (oemGuide != null) {
                SettingsSectionHeader("Device-Specific", Icons.Rounded.PhoneAndroid)
                SettingsCard {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            oemGuide.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            oemGuide.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        oemGuide.steps.forEach { step ->
                            Text(
                                step,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ── Shared helpers ─────────────────────────────────────────────────────────

@Composable
private fun SettingsSectionHeader(title: String, icon: ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(content = content)
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    displayText: String,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                displayText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = range,
            steps = steps,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ── OEM battery guidance ──────────────────────────────────────────────────────

private data class OemBatteryGuide(
    val title: String,
    val description: String,
    val steps: List<String>
)

private fun getOemBatteryGuide(): OemBatteryGuide? {
    val manufacturer = Build.MANUFACTURER.lowercase()
    return when {
        manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") ->
            OemBatteryGuide(
                title = "Xiaomi/MIUI Battery Settings",
                description = "MIUI aggressively kills background apps. Follow these steps:",
                steps = listOf(
                    "1. Settings > Apps > Manage Apps > Blink > Autostart > enable",
                    "2. Settings > Apps > Manage Apps > Blink > Battery Saver > No restrictions",
                    "3. Security app > Battery > App Battery Saver > Blink > No restrictions",
                    "4. Lock Blink in Recents (swipe down on the app card)",
                    "",
                    "Note: If the system force-stops Blink, autostart on boot won't work until you open the app once."
                )
            )
        manufacturer.contains("samsung") ->
            OemBatteryGuide(
                title = "Samsung Battery Settings",
                description = "Samsung's battery optimization can stop background apps. Follow these steps:",
                steps = listOf(
                    "1. Settings > Battery > Background usage limits > Never sleeping apps > Add Blink",
                    "2. Settings > Apps > Blink > Battery > Unrestricted",
                    "3. Lock Blink in Recents (tap the app icon > Lock)",
                    "",
                    "Note: If the system force-stops Blink, autostart on boot won't work until you open the app once."
                )
            )
        manufacturer.contains("huawei") || manufacturer.contains("honor") ->
            OemBatteryGuide(
                title = "Huawei/Honor Battery Settings",
                description = "EMUI has aggressive power management. Follow these steps:",
                steps = listOf(
                    "1. Settings > Battery > App launch > Blink > Manage manually > enable all toggles",
                    "2. Settings > Apps > Apps > Blink > Battery > enable Run in background",
                    "3. Lock Blink in Recents (swipe down on the app card)"
                )
            )
        manufacturer.contains("oppo") || manufacturer.contains("realme") || manufacturer.contains("oneplus") ->
            OemBatteryGuide(
                title = "OPPO/Realme/OnePlus Battery Settings",
                description = "ColorOS aggressively manages background apps. Follow these steps:",
                steps = listOf(
                    "1. Settings > App management > App list > Blink > Auto-launch > Allow",
                    "2. Settings > Battery > More settings > Optimize battery use > Blink > Don't optimize",
                    "3. Settings > Apps > App management > Blink > Battery usage > Allow background activity",
                    "4. Lock Blink in Recents (tap the lock icon on the app card)",
                    "",
                    "Note: If the system force-stops Blink, autostart on boot won't work until you open the app once."
                )
            )
        manufacturer.contains("vivo") || manufacturer.contains("iqoo") ->
            OemBatteryGuide(
                title = "Vivo/iQOO Battery Settings",
                description = "Funtouch OS restricts background apps. Follow these steps:",
                steps = listOf(
                    "1. Settings > Apps > Autostart > enable Blink",
                    "2. Settings > Battery > Background power consumption > Blink > Don't restrict",
                    "3. Settings > Apps > Blink > Battery > Allow high background power consumption",
                    "4. Lock Blink in Recents (swipe down on the app card)",
                    "",
                    "Note: If the system force-stops Blink, autostart on boot won't work until you open the app once."
                )
            )
        manufacturer.contains("motorola") || manufacturer.contains("lenovo") ->
            OemBatteryGuide(
                title = "Motorola Battery Settings",
                description = "Moto's system can still kill foreground services. Follow these steps:",
                steps = listOf(
                    "1. Settings > Battery > Battery optimization > All apps > Blink > Don't optimize",
                    "2. Settings > Apps > Blink > Battery > Allow background activity",
                    "3. Settings > Battery > Adaptive battery > turn off (optional, affects all apps)",
                    "",
                    "Note: If the system force-stops Blink, autostart on boot won't work until you open the app once."
                )
            )
        manufacturer.contains("asus") ->
            OemBatteryGuide(
                title = "ASUS Battery Settings",
                description = "ASUS power management can restrict background apps. Follow these steps:",
                steps = listOf(
                    "1. Settings > Battery > PowerMaster > Battery optimization > Blink > Don't optimize",
                    "2. Settings > Battery > PowerMaster > Auto-start manager > enable Blink"
                )
            )
        manufacturer.contains("nokia") ->
            OemBatteryGuide(
                title = "Nokia Battery Settings",
                description = "Nokia uses DuraSpeed which limits background processes. Follow these steps:",
                steps = listOf(
                    "1. Settings > Battery > Background activity > enable Blink",
                    "2. Settings > Apps > Blink > Battery > Unrestricted"
                )
            )
        else -> null
    }
}
