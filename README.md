<p align="center">
  <img src="https://img.shields.io/badge/Android-14%2B-brightgreen?logo=android&logoColor=white" />
  <img src="https://img.shields.io/badge/Kotlin-2.1-7F52FF?logo=kotlin&logoColor=white" />
  <img src="https://img.shields.io/badge/Jetpack_Compose-Material_3-4285F4?logo=jetpackcompose&logoColor=white" />
  <img src="https://img.shields.io/github/v/release/character-flat/Blink" />
  <img src="https://img.shields.io/badge/APK_size-1.76_MB-orange" />
</p>

# Blink

**A bulletproof 20-20-20 rule timer for Android that actually works.**

Blink is a persistent eye-care daemon that reminds you to rest your eyes every 20 minutes. Unlike other timer apps, it uses exact alarms and a foreground service to survive Android's aggressive battery optimizations — your timer fires *on time*, every time, even during Doze mode.

When it's time, a beautiful fullscreen **overlay** appears on top of whatever you're doing (no app switching, no interruptions) and counts down 20 seconds for you to look at something 20 feet away.

---

## ✨ Features

### Core
| Feature | Description |
|---|---|
| **Exact Alarms** | Uses `AlarmManager.setExactAndAllowWhileIdle` — fires during deep sleep & Doze |
| **Foreground Service** | Persistent `specialUse` FGS with live countdown notification |
| **True Overlay** | `TYPE_APPLICATION_OVERLAY` floats on top of all apps without replacing them |
| **Smart Screen Detection** | Pauses on screen off, resets after >20s lock, resumes on short locks |
| **Boot Receiver** | Optional auto-start when device reboots |

### Customization
| Feature | Description |
|---|---|
| **3 Overlay Styles** | **Expressive** (animated rings, ripple waves, orbital dots), **Calm** (smooth arc, gentle glow), **Minimal** (clean progress circle) |
| **Dynamic M3 Theming** | Colors pulled from your wallpaper via `dynamicColorScheme()` |
| **Alert Modes** | Choose between: Overlay only, Notification only, or Both |
| **Adjustable Timers** | Work: 1–60 min, Rest: 10–60 sec |
| **Overlay Opacity** | 30–100% transparency |
| **Dismissable Mode** | Optional close button (red FAB) during rest overlay |
| **Landscape Support** | Responsive layout adapts to orientation |

### Battery & Reliability
| Feature | Description |
|---|---|
| **Shizuku Integration** | Auto-whitelists app from battery optimization via `cmd deviceidle whitelist` |
| **Battery Optimization** | Built-in toggle to request unrestricted battery access |
| **START_STICKY** | Service restarts itself if killed by the OS |

---

## 📦 Download

**[→ Latest Release](https://github.com/character-flat/Blink/releases/latest)**

| Build | Size | Description |
|---|---|---|
| `blink-v1.0.0-release.apk` | **1.76 MB** | Signed, R8-minified release build |
| `blink-v1.0.0-debug.apk` | 10.4 MB | Debug build with logging |

### Requirements
- Android 14+ (API 34)
- **Display over other apps** permission (for the overlay)
- Notification permission
- Exact alarm permission

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────┐
│  MainActivity (Jetpack Compose / M3)            │
│  ├── Timer Ring (segmented arc, 60fps)          │
│  ├── Start/Stop FAB                             │
│  └── Settings FAB → SettingsScreen              │
├─────────────────────────────────────────────────┤
│  EyeCareService (Foreground Service)            │
│  ├── AlarmManager (exact, idle-safe)            │
│  ├── BroadcastReceiver (SCREEN_ON/OFF)          │
│  ├── Countdown coroutine (1s tick)              │
│  └── Persistent notification (live countdown)   │
├─────────────────────────────────────────────────┤
│  RestOverlayService (TYPE_APPLICATION_OVERLAY)  │
│  ├── ComposeView in WindowManager               │
│  ├── 3 style composables (Expressive/Calm/Min) │
│  ├── 60fps raw progress (no animateFloatAsState)│
│  └── Vibration at start + end                   │
├─────────────────────────────────────────────────┤
│  ShizukuUtils → cmd deviceidle whitelist        │
│  BootReceiver → auto-start on reboot            │
│  AlarmReceiver → wakes EyeCareService           │
│  PrefsManager → SharedPreferences wrapper       │
└─────────────────────────────────────────────────┘
```

### Key Design Decisions

- **No WorkManager** — too imprecise for 20-minute intervals. `AlarmManager.setExactAndAllowWhileIdle` is the only reliable option.
- **True overlay, not Activity** — `TYPE_APPLICATION_OVERLAY` stays on top without replacing the current app. Activities would force-switch context.
- **ComposeView in Service** — requires setting all three tree owners (`LifecycleOwner`, `SavedStateRegistryOwner`, `ViewModelStoreOwner`) manually.
- **Raw 16ms countdown** — `animateFloatAsState` on an already-updating `mutableState` causes jitter. Direct `delay(16)` loop gives smooth ~60fps arc animation.
- **R8 on debug builds** — `material-icons-extended` is ~30MB unshrinked. R8 tree-shakes unused icons down to <2MB.

---

## 🔨 Build

```bash
# Debug
./gradlew assembleDebug

# Release (requires signing key)
./gradlew assembleRelease
```

### Tech Stack

- **Language:** Kotlin 2.1
- **UI:** Jetpack Compose + Material 3 (Dynamic Color)
- **Min SDK:** 34 (Android 14)
- **Build:** Gradle 8.9, AGP 8.7.3, Compose BOM 2024.12.01
- **Dependencies:** Shizuku 13.1.5, Coroutines 1.8.1

---

## 📄 License

GNU General Public License v3.
