# FusionDrop

Offline phone-to-phone file transfer for Android. FusionDrop uses a Wi-Fi Direct group and a raw TCP stream; payload bytes are not compressed, decoded, resized, or re-encoded. Minimum Android 7.0 (API 24), target/compile API 34, Kotlin + Jetpack Compose.

## Current implementation status

- Home UI, file picker, QR generation, CameraX/ZXing scanner UI, transfer foreground service, file streaming protocol, Downloads/FlashDrop MediaStore output, adaptive/legacy icon assets, and GitHub Actions build workflow are included.
- The TCP protocol is implemented as sequential metadata + byte streams on one socket. Sender uses a bounded four-buffer producer/consumer queue (1 MiB buffers). Received files are written incrementally.
- The QR scanner decodes QR payloads locally; no ML Kit or Google Play Services dependency is used.
- Wi-Fi Direct group creation, QR credentials/device address, Android 10+ `WifiNetworkSpecifier` joining, and Android 7–9 `WifiP2pManager.connect` joining are implemented as an initial integration. **They have not been validated on physical devices**; OEM Wi-Fi Direct behavior and permission handling can differ.
- The TCP transfer engine uses a single socket and a bounded four-buffer producer/consumer pipeline. It streams in 1 MiB chunks and verifies exact byte counts through the protocol framing. No compression or media transformation is applied.
- **Not yet production-complete:** SHA-256 is a visible setting placeholder and is not wired into the protocol; transfer history is in-memory only; notification progress and cancel action are not wired; the UI cancel button does not yet close both ends of the socket; Android 9 and below storage destination handling is not implemented; restore of the previous Wi-Fi state is not implemented; average speed/ETA and per-file status list need finishing.
- API 24–28 receiving uses the legacy Wi-Fi P2P association path, but saving to `Downloads/FlashDrop` currently relies on `MediaStore.Downloads` (API 29+). Use Android 10+ for initial end-to-end tests until legacy storage is added.

These limitations are intentionally documented rather than represented as finished features. Treat this as an advanced working foundation, not a release-ready build, until the two-phone test matrix passes.

## Build on GitHub (phone-friendly)

1. Extract the ZIP.
2. Create a GitHub repository.
3. In Termux, from the extracted project folder:

```bash
git init
git add .
git commit -m "Initial FusionDrop project"
git branch -M main
git remote add origin https://github.com/YOUR_USERNAME/FusionDrop.git
git push -u origin main
```

4. Open the repository's **Actions** tab. Wait for **Android CI** to finish.
5. Open the successful run and download the `FusionDrop-debug-apk` artifact. Extract it and install the APK on both phones.

The workflow uses Temurin 17, Gradle 8.9, Android Gradle Plugin 8.7.3, Kotlin 2.0.21 and runs `gradle assembleDebug`; a Gradle wrapper JAR is not required.

## Replace the logo

Replace `app/src/main/res/drawable-nodpi/fusiondrop_logo.jpg` with your new original image (keep the same filename). Then regenerate the launcher assets under `app/src/main/res/mipmap-*` and `store-icon-512.png`. The home screen loads `R.drawable.fusiondrop_logo`. To preserve the current image exactly, do not crop, recolor, retouch, or re-encode it. Adaptive foreground art should contain the whole image inside the central safe area.

## Permissions

Wi-Fi Direct needs nearby Wi-Fi/location permissions depending on Android version. The QR scanner needs camera permission. Android 13+ notifications are requested for transfer status. Permission prompts should be preceded by an explanation in the final UX.

## First real-device tests

1. Two Android phones, same app build, Wi-Fi enabled.
2. Select one small JPG and one multi-GB video; verify byte-for-byte SHA-256 externally after transfer.
3. Test Android 9, 10, 13 and 14 separately, including Wi-Fi Direct disabled/unsupported devices.
4. Test duplicate names, Unicode filenames, disconnect mid-file, low storage, app backgrounding, cancellation and screen-off behavior.

## Sources

- Android Wi-Fi Direct: https://developer.android.com/develop/connectivity/wifi/wifip2p
- Wi-Fi P2P config builder: https://developer.android.com/reference/android/net/wifi/p2p/WifiP2pConfig.Builder
- Foreground service types: https://developer.android.com/about/versions/14/changes/fgs-types-required
- Compose compiler plugin: https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler
