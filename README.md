# PinLight

An LSPosed module that replaces the RGB notification LED. On the Always-On Display it shows only a smaller clock and, under it, a row of up to 4 tiny app-coloured icons for pending notifications. Date/weather, charging text, the fingerprint icon and Pixel's own icon row are hidden on AOD. The awake lock screen is untouched.

```
        07
        03          <- clock, scaled to 60%
   ●  ●  ●  ●       <- newest on the left
```

Target: Pixel 7 on stock Pixel OS, rooted with Magisk + Zygisk and Vector (JingMatrix LSPosed, libxposed API 102).

## How it works

| Part | Process | Job |
|---|---|---|
| `PinListenerService` | app | Reads pending notifications through the public listener API, keeps the newest one per app (up to 4), and sends the icon masks and colours to System UI |
| `PinLightModule` | System UI | Modern libxposed (API 101/102) entry. Hooks no methods: once System UI has started it creates the overlay |
| `PinOverlay` / `PinView` | System UI | A transparent full-screen window that draws the pin row under the clock, only while the display dozes (AOD). The row follows the clock, so System UI's own burn-in shift moves it too |
| `PinSettings` | both | Look settings (clock size/colour/position, icon count, rows, size, spacing, colour). Saved in the app, pushed to System UI on every change |
| `SleepSignals` | System UI | Hooks System UI's "started going to sleep / waking up" events so the AOD is restyled before the screen-off animation. Falls back to screen and display broadcasts |
| `AodStyler` | System UI | On AOD, resizes/moves the clock (animation matrix, so System UI's burn-in shift still works), recolours it via a hardware layer, and hides other keyguard views, matched by resource id / class name. Restores them when the screen wakes. Dumps the AOD view tree to logcat once per boot (`DUMP` lines, tag `PinLight`) |

No System UI methods are hooked. If a Pixel update renames a view id, that element simply reappears on AOD; nothing crashes.

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
