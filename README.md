# PinLight

An LSPosed module that replaces the RGB notification LED. When notifications are pending, tiny icons appear beside the camera hole on the lock screen and AOD. Each icon is drawn in its app's colour. Nothing else is shown.

```
   [L2] [L1]  ( o )  [R1] [R2]      newest = L1, then R1, L2, R2
```

Target: Pixel 7 on stock Pixel OS, rooted with Magisk + Zygisk and Vector (JingMatrix LSPosed, libxposed API 102).

## How it works

| Part | Process | Job |
|---|---|---|
| `PinListenerService` | app | Reads pending notifications through the public listener API, keeps the newest one per app (up to 4), and sends the icon masks and colours to System UI |
| `PinLightModule` | System UI | Modern libxposed (API 101/102) entry. The only hook: it waits for System UI's `Application.onCreate`, then creates the overlay |
| `PinOverlay` / `PinView` | System UI | A transparent window above the keyguard that draws the pins next to the cutout. It is visible only while the phone is locked, and shifts by 2dp each minute to prevent burn-in |

None of System UI's internal classes are hooked, so Pixel monthly updates are unlikely to break the module.

**What counts as pending:** notifications that alert (importance DEFAULT or higher) and can be dismissed. Silent, ongoing and foreground-service notifications are ignored, the same way an LED behaves.

**Colour:** the app's `notification.color`, or the vibrant colour of its icon if it has none. Brightness is raised so dark brand colours stay visible on black.

## Build and install

1. Open in Android Studio and run **Build → Build APK(s)**.
2. `adb install app/build/outputs/apk/debug/app-debug.apk`
3. In LSPosed, enable **PinLight** and tick **System UI** as the scope.
4. Restart System UI: `adb shell su -c "killall com.android.systemui"`, or reboot.
5. Open PinLight and grant **Notification access**.
6. In Settings → Display → Lock screen, turn on **Always show time and info** (AOD). With AOD off, the panel is fully off and nothing can light up.

## Test

- Tap **Preview 4 pins (8s)** while unlocked. Four coloured dots should appear around the camera.
- Logs: `adb logcat | grep -i PinLight`, or check the LSPosed log.

## Known limits / to verify on device

- **AOD frame freeze:** in deep doze the AOD frame may not refresh until the next pulse or minute tick, so a new pin can appear a few seconds late.
- **Pixel's own AOD icon row** still shows under the clock. Hiding it would need a System UI internals hook, planned for v2.
- **Window type 2015** (`TYPE_SECURE_SYSTEM_OVERLAY`) is expected to draw over the keyguard and AOD. If pins show on the lock screen but not on AOD, this is the first thing to change.
