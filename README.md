# FusionDrop 1.1.0

FusionDrop is an Android file-sharing app with two direct-transfer paths:

1. **Phone Transfer** — the existing Wi-Fi Direct sender/receiver flow using a FusionDrop QR code.
2. **Link Share** — the sender starts a temporary HTTP server on their current Wi-Fi/hotspot. A friend opens the generated link in any modern browser and downloads the original files. The friend does not need the FusionDrop app.

The app uses Kotlin, Jetpack Compose, CameraX and ZXing. It does not require a cloud account, paid API, backend server or API key.

## What is new in 1.1.0

- Branded splash screen with a custom animated-vector FusionDrop mark. The camera-watermarked launcher image is no longer used as the splash icon.
- Clean FusionDrop launcher icon with no camera watermark.
- Dark navy, blue and cyan UI with animated page transitions, press feedback and three clear transfer modes.
- New **Share by browser link** mode.
- Responsive browser landing page with a welcome message, image previews, file names, sizes and per-file download links.
- Link is copied or shared through Android's native share sheet.
- A QR code is also shown for the browser link.
- Foreground notification keeps Link Share visible and provides a Stop action.
- Link Share streams the original file bytes; it does not resize, transcode, recompress or upload them to a cloud service.

## Link Share: how to use it

1. Connect your phone and your friend's phone to the **same Wi-Fi network**, or connect your friend's phone to your Android hotspot.
2. Open FusionDrop and select **Share by browser link**.
3. Choose the files. Optionally write a short welcome note for the browser page.
4. Tap **Start sharing** and use **Copy link** or Android's Share action.
5. Your friend pastes the link into Chrome, Firefox or another modern browser. The page shows the shared files and download controls.
6. Tap **Stop sharing** when finished. The server closes and that link stops working.

### Link Share network and security limits

- This is a **local-network link**, not a public Internet link. It will not work for a friend in another city or on unrelated mobile data.
- Both phones must be on the same reachable LAN. Some routers enable client/AP isolation; use a phone hotspot or a Wi-Fi network that allows devices to communicate if the link cannot open.
- The app binds a temporary server to the phone's LAN interface and an automatically selected port. Each share uses a random 192-bit path token. The server only exposes the selected files through that tokenized path.
- **The browser URL uses plain HTTP, not HTTPS.** Anyone who obtains the link and can reach the same network can download the shared files. Do not use public/untrusted Wi-Fi for sensitive files. Stop sharing immediately after the recipient finishes.
- Link Share uses a foreground service, wake lock and Wi-Fi lock while active. Android may still stop background work under extreme power-management or system conditions.
- Up to four browser connections are handled concurrently. Large downloads are streamed from the Android content provider; they are not loaded fully into memory.
- The welcome note appears on the browser page. It does **not** send an SMS or access the user's contacts.

## Phone Transfer

The original Send/Receive screens are retained. The sender selects files and shows a FusionDrop QR code. The receiver scans that code using CameraX/ZXing. The existing Wi-Fi Direct / TCP transfer implementation remains separate from Link Share.

Physical-device behaviour can vary by Android version and phone manufacturer. Wi-Fi Direct, Android 10+ Wi-Fi association and long-running transfer behaviour still require two-phone testing.

## Build with GitHub Actions (Android phone workflow)

This repository includes `.github/workflows/build.yml`. A push to `main` runs `gradle assembleDebug` and uploads `FusionDrop-debug-apk`.

**Current build configuration:** Java 17, Gradle 8.9, Android Gradle Plugin 8.7.3, Kotlin 2.0.21, compile SDK 35, target SDK 34, minimum SDK 24. The CI workflow installs Gradle; a Gradle wrapper JAR is not required.

### Updating the existing FusionDrop repository from Termux

Keep the existing `.git` folder. Extract this ZIP over your current `~/FusionDrop` working copy so the updated files replace the old files but the Git history stays intact:

```bash
termux-setup-storage
cd ~/FusionDrop || exit
unzip -o ~/storage/shared/Download/FusionDrop-1.1.0.zip -d ~/FusionDrop

git status --short
git diff --check || exit 1
git add -A
git commit -m "Build FusionDrop 1.1.0 with browser Link Share"
git push origin main
```

If your ZIP is stored under a different name or folder, change only the `unzip` source path. Do not delete the existing `.git` directory.

After push, open the repository's **Actions** tab. When **Android CI** succeeds, download the `FusionDrop-debug-apk` artifact, extract the APK and install it on the Android phone.

## Project structure

- `app/src/main/java/com/fusiondrop/MainActivity.kt` — Compose UI, navigation and existing phone-transfer flow.
- `app/src/main/java/com/fusiondrop/linkshare/LinkShareService.kt` — foreground service and Link Share lifecycle.
- `app/src/main/java/com/fusiondrop/linkshare/LinkShareServer.kt` — token-protected LAN HTTP server and browser download page.
- `app/src/main/java/com/fusiondrop/linkshare/LinkShareState.kt` — observable service state.
- `app/src/main/java/com/fusiondrop/connection/` — Wi-Fi Direct connection code.
- `app/src/main/java/com/fusiondrop/transfer/` — existing TCP transfer protocol and foreground service.
- `app/src/main/java/com/fusiondrop/scanner/QrScanner.kt` — CameraX + ZXing scanner.
- `app/src/main/res/drawable/avd_fusiondrop_splash.xml` — animated-vector splash animation.
- `app/src/main/res/drawable-nodpi/fusiondrop_logo.jpg` — clean, wide home-screen artwork.

## Known limitations and test status

- This ZIP has **not** been built with Android Gradle Plugin in the current build environment because Android SDK and Gradle are not installed here. The Link Share HTTP server was compiled with a small JVM test harness and tested for the welcome page, file-list endpoint and byte-for-byte download. GitHub Actions is the required full Android build check.
- Link Share needs physical-device testing on different Wi-Fi routers, Android hotspots, screen-off conditions and large files. The local test harness is not a substitute for two Android phones.
- The current phone-to-phone Wi-Fi Direct integration is an initial implementation and has not been validated across OEM devices.
- SHA-256 verification is not yet implemented in the current transfer protocol. The Settings screen labels it as planned instead of showing a misleading active switch.
- Transfer history is currently held in memory for the app session; it is not persistent.
- Phone-transfer cancellation/progress notification behaviour still needs deeper integration testing.
- Android 7–9 receive-to-Downloads handling needs a dedicated legacy-storage test. Android 10+ is the intended initial test baseline.

## Android local-network permission note

This app currently targets SDK 34. Android's current local-network guidance says apps targeting SDK 36 or lower retain implicit local-network access through `INTERNET`; apps targeting SDK 37 or higher must declare and request `ACCESS_LOCAL_NETWORK`. Revisit this before raising target SDK to 37. See the official Android reference in the source links below.

## Sources

- Android SplashScreen: https://developer.android.com/develop/ui/views/launch/splash-screen
- Android local-network permission: https://developer.android.com/privacy-and-security/local-network-permission
- Android foreground-service timeout: https://developer.android.com/develop/background-work/services/fgs/timeout
- Android Compose setup: https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler
