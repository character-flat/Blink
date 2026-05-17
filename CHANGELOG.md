# Changelog

## v1.1.0

### Reliability Improvements
- Service calls `startForeground()` in `onCreate()` — closes the empty-process kill window
- Timer state persisted across OS-initiated restarts (no longer cleared in `onDestroy`)
- Watchdog alarm fires every 5 minutes via `setExactAndAllowWhileIdle` to auto-restart dead service
- Direct boot support (`LOCKED_BOOT_COMPLETED`) — service starts before device unlock
- `AlarmReceiver` independently restarts service if found dead

### UX Improvements
- OEM-specific battery settings guide (Xiaomi, Samsung, OPPO/Realme/OnePlus, Vivo, Motorola, Huawei, ASUS, Nokia)
- Each OEM guide includes auto-start whitelist steps and force-stop warning
- Shizuku is now fully optional — app works without it (only used for one-tap battery whitelist)

### Housekeeping
- Package renamed from `com.eyecare.daemon` to `com.cflat.blink`
- License: GNU General Public License v3

## v1.0.0

- Initial release
- 20-20-20 rule timer with exact alarms
- Foreground service with persistent notification
- Full-screen overlay with 3 styles (Expressive, Calm, Minimal)
- Dynamic Material 3 theming
- Shizuku integration for battery whitelist
- Boot receiver for auto-start
- Configurable work/rest durations, overlay opacity, dismiss mode
