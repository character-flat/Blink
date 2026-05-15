# Blink — 20-20-20 Eye Care Daemon

A bulletproof 20-20-20 rule timer for **Android 14+** that survives aggressive OS battery management.

Every **20 minutes** of screen time, Blink reminds you to look at something **20 feet away** for **20 seconds** — with a stunning fullscreen overlay.

## Features

- ⏱️ **Exact AlarmManager** — `setExactAndAllowWhileIdle` ensures timers fire even during Doze
- 🔒 **Foreground Service** — persistent, unkillable timer with live notification
- 🎨 **3 Overlay Styles** — Expressive (animated rings, ripples, orbital dots), Calm, Minimal
- 🌈 **Dynamic M3 Theming** — wallpaper-based colors via `dynamicColorScheme()`
- 📱 **Landscape & Portrait** — responsive overlay layout
- ⚙️ **Configurable** — work duration (1-60 min), rest duration (10-60s), opacity, dismiss button
- 🔔 **Alert Modes** — Both (overlay + notification), Overlay only, Notification only
- 🛡️ **Shizuku Integration** — auto-whitelist from battery optimizations
- 🔄 **Smart Screen Detection** — pauses on screen off, resets if locked >20s
- 🚀 **Boot Receiver** — optional auto-start on reboot
- 📦 **Tiny** — 1.76 MB release APK (R8 + resource shrinking)

## Requirements

- Android 14+ (SDK 34)
- Overlay permission (Display over other apps)
- Notification permission
- Exact alarm permission

## Download

See the [releases/](releases/) folder for pre-built APKs.

## Build

```bash
./gradlew assembleRelease
```

## Tech Stack

- Kotlin + Jetpack Compose + Material 3
- AlarmManager + Foreground Service (specialUse)
- ComposeView in Service (overlay)
- Shizuku for `cmd deviceidle whitelist`

## License

MIT
