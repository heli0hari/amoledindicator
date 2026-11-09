# Installing PinLight on a Pixel

PinLight turns the Pixel Always-On Display into an LED replacement: just a smaller clock and a row of app-coloured notification icons under it.

It needs **root**: it runs as a Vector (LSPosed) module inside System UI.

- **Time:** about 30–45 min the first time.
- **Tested on:** Pixel 7, Android 16, Magisk 31.1, Vector 2.2.
- **Should work on:** other Pixels on Android 15–16 (untested).

> ⚠️ **Read before you start**
> - Unlocking the bootloader **erases the phone**. Back up first.
> - **Carrier-locked Pixels** (e.g. bought from Verizon) usually can't be unlocked. If *OEM unlocking* is greyed out, stop here.
> - Rooting can break **Google Wallet and some banking apps**.
> - Every monthly Pixel update removes root. See [After a Pixel update](#after-a-pixel-update); it takes 5 minutes.

---

## 0. One-time PC setup (Windows)

1. Install [Android Studio](https://developer.android.com/studio). This gives you `adb` and `fastboot`, plus a way to build PinLight. If you only want the tools, download **SDK Platform-Tools** from Google instead.
2. Open **PowerShell** and create short names for the tools. This lasts for the current window:
   ```powershell
   Set-Alias adb      "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
   Set-Alias fastboot "$env:LOCALAPPDATA\Android\Sdk\platform-tools\fastboot.exe"
   ```
   If you downloaded Platform-Tools separately, point the aliases at that folder instead.

> Paste commands **one line at a time**.

## 1. Prepare the phone

1. **Settings → About phone → tap *Build number* 7 times.**
2. **Settings → System → Developer options:** turn on **OEM unlocking** and **USB debugging**.
3. Plug the phone in. Run `adb devices` and tap **Allow** on the phone. The command should list your phone with the word `device`.

## 2. Unlock the bootloader (wipes the phone)

```powershell
adb reboot bootloader
fastboot devices
fastboot flashing unlock
```
On the phone, press **Volume** to select *Unlock the bootloader*, then **Power** to confirm. The phone wipes itself and reboots. Set it up again, then turn on **USB debugging** again (step 1.2).

> `fastboot devices` shows nothing? Windows needs the **Google USB Driver**. Install it from Android Studio → SDK Manager → SDK Tools, then update the phone's driver in Device Manager.

## 3. Root with Magisk

1. Find your exact build: **Settings → About phone → Build number** (e.g. `BP2A.250605.031`).
2. Download the **factory image for that exact build** from [Google's factory images page](https://developers.google.com/android/images), then unzip it, and unzip the `image-…zip` inside.
3. Copy the boot image to the phone's Downloads folder:
   - **Pixel 7 and newer:** `init_boot.img`
   - **Pixel 6 / 6a and older:** `boot.img`
4. Install the [Magisk app](https://github.com/topjohnwu/Magisk/releases) on the phone. Tap **Install → Select and Patch a File** and pick the image.
5. Copy the `magisk_patched-….img` file from the phone's Downloads back to the PC, then:
   ```powershell
   adb reboot bootloader
   fastboot flash init_boot "C:\path\to\magisk_patched-XXXXX.img"
   fastboot reboot
   ```
   On a Pixel 6 or older, use `fastboot flash boot …` instead.
   > Make sure the partition name is in the command. Leaving it out gives `unknown partition`.
6. Open Magisk. **Installed** should now show a version number.

## 4. Turn on Zygisk and install Vector

1. **Magisk → Settings → Zygisk → On.** Then **reboot**.
2. Download the **Zygisk zip** of [Vector](https://github.com/JingMatrix/Vector/releases), the maintained LSPosed fork.
3. **Magisk → Modules → Install from storage →** pick the zip, then **reboot**.
4. Open Vector from its notification or its home-screen shortcut. The card should say **Active**.

## 5. Install PinLight

**Easiest — download it:** get the latest `PinLight-x.y.z.apk` from the repo's **Releases** page, then either open it on the phone, or run:
```powershell
adb install -r PinLight-x.y.z.apk
```

**Or build it yourself** (in the project folder, in PowerShell):
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew assembleDebug
adb install -r -t app\build\outputs\apk\debug\app-debug.apk
```

> Release APKs and your own builds are signed with different keys. To switch between them, uninstall PinLight first, then re-enable it in Vector and reboot.

> Don't install with Android Studio's ▶ **Run** button. It can hot-swap code without replacing the installed APK, and Vector loads the installed APK.

## 6. Switch it on

1. **Vector → Modules → PinLight → Enable.** System UI is ticked automatically and shows *Fixed by the module*.
2. **Reboot.**
3. Open **PinLight → Notification access** and allow it.
   > Shows **"App was denied access"**? Apps installed from a downloaded APK are blocked from this permission by Android. Go to **Settings → Apps → PinLight → ⋮ → Allow restricted settings**, then try again. Installs done with `adb install` don't need this.
4. The app's header should say **System UI hook: Connected**.
5. Phone settings:
   - **Settings → Display → Lock screen → Always show time and info → On.** Without AOD, the screen is fully off and nothing can light up.
   - *(Optional)* **Settings → Notifications → Notifications on lock screen → Don't show any.** PinLight still sees every notification.
6. Lock the phone. You should see only the clock, with app-coloured icons under it when something is pending.

Tune the clock and icons in the PinLight app. Changes apply to the AOD right away.

---

## After a Pixel update

Monthly updates replace the boot image, so root is lost and PinLight stops. Vector and PinLight stay installed.

Repeat **step 3** with the **new** build's `init_boot.img`, then reboot. Nothing else needs to be done.

## Updating PinLight

Install the newer APK from **Releases** over the old one (no uninstall needed), then **reboot**. A reboot is always needed, because System UI only loads the module at boot.

---

## For the maintainer: publishing releases

GitHub Actions (`.github/workflows/build.yml`) builds a release APK on every push to `main`. You can download it under **Actions → latest run → Artifacts**. Pushing a version tag also publishes it on the **Releases** page:
```powershell
git tag v0.2.0
git push origin v0.2.0
```

**One-time: signing key.** Every release must be signed with the same key, or updates fail. Create the key once and keep `pinlight.jks` and its password somewhere safe, outside the repo:
```powershell
& "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -genkeypair -v -keystore pinlight.jks -alias pinlight -keyalg RSA -keysize 2048 -validity 10000
[Convert]::ToBase64String([IO.File]::ReadAllBytes("$PWD\pinlight.jks")) | Set-Clipboard
```
Then go to **GitHub → repo → Settings → Secrets and variables → Actions → New repository secret** and add:

| Secret | Value |
|---|---|
| `SIGNING_KEYSTORE_BASE64` | paste from the clipboard (the second command above) |
| `SIGNING_STORE_PASSWORD` | the keystore password you chose |
| `SIGNING_KEY_ALIAS` | `pinlight` |
| `SIGNING_KEY_PASSWORD` | the key password (same as the keystore password if you pressed Enter) |

Without these secrets the workflow still builds, but each APK gets a throwaway key.

## Troubleshooting

| Symptom | Fix |
|---|---|
| App says **System UI hook: not loaded** | Vector → Modules: is PinLight enabled? Reboot after enabling. |
| Vector says **Needs attention** | Zygisk is off, or you didn't reboot after flashing Vector. |
| Default AOD (date, fingerprint, "Charged") still shows | You're on an old PinLight build. Reinstall with `adb install -r -t`, then reboot. |
| *App was denied access* when granting notification access | Settings → Apps → PinLight → ⋮ → **Allow restricted settings** |
| Icons don't appear | Notification access is off, or the notification is *silent*. Only alerting notifications count, like an LED. |
| `adb shell su` → *Permission denied* | Magisk → Settings → Superuser access → **Apps and ADB**. |
| Need logs | `adb reboot`, then `adb wait-for-device logcat -s PinLight > log.txt`. Lock the phone, then press Ctrl+C. |

## Uninstall

Disable PinLight in Vector and reboot, then uninstall the app. To remove root completely, use **Magisk → Uninstall → Restore images**.
