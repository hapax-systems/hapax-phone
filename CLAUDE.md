# hapax-phone

Android companion app (Pixel/Android 14+) for the hapax ecosystem. Streams two payloads to the council logos API:

- **HealthSummaryPayload** — daily, fired at 7 AM by WorkManager. Resting HR, HR min/max/mean, HRV, steps, active minutes, sleep stages, SpO2, skin temp deviation. Read via Health Connect.
- **PhoneContextPayload** — every 60s, fired by a foreground service. Activity type/confidence (Google Play Services on-device ML), screen state, battery %, charging, ringer mode, network type.

Sister app to `hapax-watch`. Phone covers what the watch can't: ambient context, daily health rollups, ringer state.

## Build

```bash
export ANDROID_HOME=~/Android/Sdk
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk
./gradlew assembleDebug
./gradlew test
```

Requires JDK 21, Android SDK 35 (compileSdk), minSdk 34, targetSdk 35. Gradle 8.x via the wrapper.

## Architecture

- **`MainActivity`** — Compose UI for granting Health Connect permissions, manual IP override, and triggering an immediate sync.
- **`HealthConnectReader`** — Reads HR / HRV / steps / sleep / SpO2 / skin temp from Health Connect Client.
- **`HealthSyncWorker`** — WorkManager periodic worker (12h interval, initial delay aligned to 7 AM). Calls `HapaxTransport.send(HealthSummaryPayload)`.
- **`ContextService`** — Foreground service, `START_STICKY`. Polls device state every 60s, POSTs `PhoneContextPayload` to `/phone/context`. Notification is LOW priority.
- **`ActivityRecognitionReceiver`** — Google Play Services on-device ML updates the service's activity state every 60s.
- **`HapaxTransport`** — OkHttp client, 10s timeouts, exponential backoff (5s → 120s).

## Data Schema

`HealthSummaryPayload`: `device_id`, `date`, `resting_hr`, `hr_min`, `hr_max`, `hr_mean`, `hrv_mean_ms`, `steps`, `active_minutes`, `sleep_start`, `sleep_end`, `sleep_duration_min`, `deep_min`, `rem_min`, `spo2_mean`, `skin_temp_deviation_c`.

`PhoneContextPayload`: `device_id`, `ts`, `activity_type`, `activity_confidence`, `screen_on`, `ringer_mode` (normal/vibrate/silent), `battery_pct`, `charging`, `network_type` (wifi/cellular/none).

Both payloads target the council watch-receiver (port `8042`), not the logos API.

## Network

URL resolution priority:

1. Manual IP override (DataStore-persisted)
2. Cached mDNS `_hapax._tcp` discovery (5 min TTL)
3. Fresh mDNS query (5s timeout)
4. Hardcoded fallback `100.117.1.83:8042` (Tailscale IP, watch receiver endpoint)

Endpoints: `POST /phone/health-summary`, `POST /phone/context`.

## Deploy

```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

Requires phone connected via ADB (USB or `adb connect <ip>:5555` over WiFi).

## Gotchas

- **Permissions**: Health Connect read permissions (heart rate, HRV, steps, sleep, SpO2, skin temp, exercise) + `ACTIVITY_RECOGNITION` + `INTERNET` + `FOREGROUND_SERVICE`. Runtime grant flow via `HealthPermissionsRationaleActivity`.
- **Cleartext traffic**: `android:usesCleartextTraffic="true"` in the manifest — mDNS discovery requires HTTP over the LAN.
- **Background limits**: foreground service notification keeps the OS from killing `ContextService`. WorkManager respects Doze and may delay the 7 AM sync slightly.
- **Activity recognition**: on-device ML, ~1% battery/day. Requires Google Play Services.
- **Hardcoded device ID**: `"pixel10"` — both payloads carry it. No per-device discovery; override in source if running on another device.
- **Health Connect availability**: Android 14+ ships it; older versions need the Health Connect APK installed. Missing records degrade gracefully (null fields).

## Sister app

`hapax-watch` covers heart rate streaming + watch-face complications. Phone and watch send to the same council endpoint port (`8042`) but with different payload schemas. The council `watch_receiver.py` discriminates by payload shape.
