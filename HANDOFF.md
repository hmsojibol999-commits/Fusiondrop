# FusionDrop 1.1.0 — developer handoff

## Product

Offline-first Android file sharing. The existing Wi-Fi Direct / TCP phone-transfer feature is preserved. Version 1.1.0 adds a browser-based LAN sharing mode.

## Main architecture

### Phone Transfer (existing)

- Compose screens and orchestration: `app/src/main/java/com/fusiondrop/MainActivity.kt`
- Wi-Fi Direct group owner and association: `connection/WifiDirectConnection.kt`, `WifiNetworkJoiner.kt`, `LegacyWifiP2pJoiner.kt`
- TCP protocol and file streaming: `transfer/TransferProtocol.kt`
- CameraX + ZXing QR scanner: `scanner/QrScanner.kt`
- Foreground service: `transfer/TransferService.kt`

### Link Share (new)

- UI: `LinkShareScreen` in `MainActivity.kt`
- Service and notification: `linkshare/LinkShareService.kt`
- Runtime UI state: `linkshare/LinkShareState.kt`
- LAN server and self-contained HTML/JS page: `linkshare/LinkShareServer.kt`
- Service receives selected `content://` URIs through an explicit Intent extra. The activity takes persistable read permission from the document picker when available.
- Service describes file name, MIME type and length using `ContentResolver`. Unknown sizes remain unknown; the download uses connection-close framing instead of buffering whole files.
- Server binds `0.0.0.0` on an ephemeral port, obtains a site-local IPv4 address from active network interfaces, and creates a random 24-byte token.
- Routes: `/s/{token}` (welcome page), `/api/{token}/files` (JSON file list and sender note), `/preview/{token}/{index}` (image preview), `/download/{token}/{index}` (attachment download).
- Only GET and HEAD are accepted. The selected content URI is streamed in 128 KiB buffers. No image/video conversion or compression occurs.
- Server has a fixed four-thread executor. Stop action closes the server socket and executor; it also clears observable UI state.
- A foreground `dataSync` service with Wi-Fi/wake locks keeps sharing available while the activity is backgrounded. Stop is exposed in both the UI and notification.
- Browser page contains all CSS/JS inline; it has no external CDN or cloud dependency. Dynamic file names are inserted with DOM `textContent` rather than HTML interpolation.

## Visual design

- Dark navy / blue / cyan palette; three clear home transfer cards.
- Animated content transition between screens and subtle press-scale feedback on mode cards.
- Splash uses an AnimatedVectorDrawable under Android 12+. It is approximately 900 ms and does not intentionally delay app startup. AndroidX compatibility displays a static icon on older Android versions where launch-screen AVD animation is not supported.
- App launcher icon is a clean transfer/fusion mark without the previous camera watermark.
- The supplied clean wide artwork remains the Home Screen hero image at `drawable-nodpi/fusiondrop_logo.jpg`.

## Build configuration

- JDK 17
- Gradle 8.9 (installed by GitHub Actions)
- Android Gradle Plugin 8.7.3
- Kotlin 2.0.21
- Compose BOM 2024.12.01
- compile SDK 35, target SDK 34, min SDK 24
- CI: `.github/workflows/build.yml`, task `gradle assembleDebug`

## Security and network model

- Link Share is LAN-only. It cannot provide an Internet-routable link to a remote friend without a relay/server, which is intentionally out of scope for this zero-cost/offline design.
- Link URL is plain `http://` and token-protected, not TLS-encrypted. Anyone who gets the link and can access the same LAN can download the selected files. Do not present this as end-to-end encrypted; use trusted Wi-Fi and stop sharing after use.
- Token is 24 cryptographically random bytes (192 bits) encoded as hex; port is selected by the OS.
- Android's current local-network guidance: target SDK 37+ requires `ACCESS_LOCAL_NETWORK`; target SDK 36 or lower retains implicit access through `INTERNET`. Before raising target SDK to 37, add a runtime permission rationale and permission handling. Current target is 34.
- The service uses `dataSync` foreground type. Android 15+ introduces a six-hour per-24-hour limit for apps targeting SDK 35+, and `onTimeout` stops the service; current target remains 34.

## Known incomplete / unverified areas

1. No full Android Gradle build was run in the current environment: Android SDK and Gradle are unavailable. The GitHub Actions build must be checked after the user pushes this ZIP.
2. Link Share server passed a JVM integration test with a stub content resolver for: page response, JSON list and byte-for-byte download. This does not validate Android `ContentResolver`, foreground-service lifecycle, real Wi-Fi routing, phone hotspot behaviour or manufacturer restrictions.
3. Test on at least two physical Android devices: same router, sender hotspot, screen-off, backgrounding, link copy/open, one image, Unicode file name, unknown-size URI, large video, disconnect while downloading, multiple simultaneous downloaders and Stop action.
4. Routers with client/AP isolation prevent peer access. The UI and README explain same-network requirement but do not automatically diagnose router isolation.
5. Link Share uses HTTP without TLS. Do not share confidential material over untrusted Wi-Fi.
6. Phone-transfer Wi-Fi Direct flow still needs broad OEM validation and cancellation path review.
7. SHA-256 verification is not implemented in the current protocol; settings marks it as planned.
8. History is session-memory only.
9. Android 7–9 storage/Downloads handling needs separate validation; first physical tests should use Android 10+.
10. Consider future additions only after the current transfer tests pass: persistent history, sender identity label, resumable download support, checksum verification, HTTPS/local certificate strategy and optional Android 17 local-network permission flow when target SDK is raised.

## Build and release handoff

1. Extract `FusionDrop-1.1.0.zip` over the existing Git working tree, keeping `.git`.
2. Run `git diff --check`, commit and push to `main`.
3. Check GitHub Actions `Android CI`.
4. Install the produced debug APK on both phones and execute the real-device matrix above.
5. Capture the first Android build error/log in full before changing source; do not rewrite unrelated code.
