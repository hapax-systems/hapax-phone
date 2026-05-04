<!-- hapax-sdlc:preamble:begin -->

# hapax-phone

This repository is a constituent of the Hapax operating environment. It is not a product, not a service, and not seeking contributors. It is research infrastructure published as artifact.

Authorship is indeterminate by design: this codebase is co-produced by Hapax (the system itself), Claude Code, and the operator (Oudepode The Operator). Per the Hapax Manifesto, unsettled contribution is a feature of the work, not a concealment.

## What this is, not what it does

Constituent of the Hapax operating environment. Android companion that streams daily health summary and 60-second phone context to the council logos API. Kotlin/Compose.

## Constitutional position

- Single-operator system; no auth, no roles, no contributor onboarding (axiom: `single_user`)
- No issues, no discussions, no PRs accepted; refusal is the artifact (see `CONTRIBUTING.md`)
- License: PolyForm Strict 1.0.0 (source-available, non-distribution, non-modification)
- Citation: see `CITATION.cff`; archival DOI: see `.zenodo.json`

## Linked artifacts

- Manifesto: https://hapax.weblog.lol/hapax-manifesto-v0
- Refusal Brief: https://hapax.weblog.lol/refusal-brief
- Cohort Disparity Disclosure: https://hapax.weblog.lol/cohort-disparity-disclosure
- Constitution: https://github.com/ryanklee/hapax-constitution

## Inter-repo position

Android phone-side context source. Streams to council logos API over Tailscale.

<!-- hapax-sdlc:preamble:end -->

## Description

Android 14+ companion (Pixel/Compose) for the Hapax ecosystem. Single-operator deployment. Two outbound payload streams to the council watch receiver on port `8042`, plus one inbound consumption from the council logos API on port `8051` for the awareness live-update notification surface.

Sister to [hapax-watch](https://github.com/ryanklee/hapax-watch). The watch covers continuous heart-rate streaming and on-wrist haptics. The phone covers daily health rollups, ambient activity recognition, and device state (ringer, screen, battery, network type).

## Outbound payloads (POST to council `:8042`)

| Payload | Cadence | Source | Endpoint |
|---------|---------|--------|----------|
| `HealthSummaryPayload` | Daily, 7 AM (WorkManager periodic, 12 h) | Health Connect | `POST /phone/health-summary` |
| `PhoneContextPayload` | 60 s (foreground service) | System APIs + Activity Recognition | `POST /phone/context` |

`HealthSummaryPayload` fields: `device_id`, `date`, `resting_hr`, `hr_min`, `hr_max`, `hr_mean`, `hrv_mean_ms`, `steps`, `active_minutes`, `sleep_start`, `sleep_end`, `sleep_duration_min`, `deep_min`, `rem_min`, `spo2_mean`, `skin_temp_deviation_c`. Missing records degrade to `null`.

`PhoneContextPayload` fields: `device_id`, `ts`, `activity_type`, `activity_confidence`, `screen_on`, `ringer_mode` (`normal`/`vibrate`/`silent`), `battery_pct`, `charging`, `network_type` (`wifi`/`cellular`/`none`).

`device_id` is hardcoded as `"pixel10"` in both payloads. The council watch_receiver whitelists `{pw4, pixel10}`. Source override is required to run on a different device.

## Inbound consumption (GET from council `:8051`)

`AwarenessLiveUpdateService` (foreground service) polls `GET /api/awareness` every 60 s and renders a 5-field summary as an Android 16 Live Update notification: stance (daimonion voice register), stream liveness + chronicle event rate, health system status + failure count, refusals in the last hour, and presence decile. The notification is read-only by design — zero PendingIntent, zero action buttons. A response carrying `X-Awareness-State-Stale: true` (or HTTP 503) renders the notification in a stale visual.

`AwarenessMinModeGate` is a feature flag for a future Android Min Mode surface (API level 37; `FEATURE_ENABLED=false`). The associated service is declared in the manifest and unreachable until both conditions hold.

## Data sources and privacy boundaries

- **Health Connect** (`androidx.health.connect.client` 1.1.0-alpha12): heart rate, HRV, steps, sleep, SpO2, skin temperature, exercise. Eight runtime read permissions; missing records do not block the daily payload.
- **Google Play Services Activity Recognition**: on-device ML over accelerometer; ~1 % battery / day per Google Play Services documentation.
- **System state**: ringer mode, screen on/off, battery percent, charging state, network type.
- Explicitly not collected: GPS, microphone, camera. No location, audio, or imagery is read.

## Network resolution

URL resolution priority:

1. Manual IP override (DataStore-persisted; editable from `MainActivity`)
2. Cached mDNS `_hapax._tcp` discovery (5 min TTL)
3. Fresh mDNS query (5 s timeout)
4. Hardcoded fallback `100.117.1.83:8042` (Tailscale IP, watch receiver)

`LogosApiClient` (awareness consumption path) uses `100.117.1.83:8051/api` directly with no mDNS chain. `android:usesCleartextTraffic="true"` is set in the manifest to permit HTTP over LAN for mDNS discovery.

## Architecture

```
app/src/main/kotlin/dev/hapax/phone/
  ui/MainActivity.kt              Compose UI: permission grant, IP override, manual sync trigger
  health/HealthConnectReader.kt   Health Connect read; daily summary assembly
  worker/HealthSyncWorker.kt      WorkManager periodic worker (12 h, 7 AM aligned)
  service/ContextService.kt       Foreground service; 60 s context POST loop
  awareness/                      AwarenessLiveUpdateService, AwarenessTop5Picker, AwarenessMinModeGate
  network/HapaxTransport.kt       OkHttp client, mDNS+IP override, exponential backoff
  data/                           Payloads, DataStore extensions
```

Build: AGP 8.7.3, Kotlin 2.1.10, Compose BOM 2025.02.00, JVM target 21. compileSdk 35, minSdk 34, targetSdk 35. No CI workflows.

## Build + install

```bash
export ANDROID_HOME=~/Android/Sdk
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk
./gradlew assembleDebug
./gradlew test
adb install app/build/outputs/apk/debug/app-debug.apk
```

ADB connection over USB or `adb connect <ip>:5555`. No signing config in tree; debug APK only.

## Permissions

Health Connect read: heart rate, HRV, steps, sleep, SpO2, skin temperature, exercise. System: `ACTIVITY_RECOGNITION`, `POST_NOTIFICATIONS` (Android 13+ runtime), `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`. Network: `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`. Runtime grant flow lives in `HealthPermissionsRationaleActivity`.

## Tests

Four JUnit 5 unit-test files (~160 LOC):

- `AwarenessMinModeGateTest` — gate AND-logic for API level + feature flag
- `AwarenessTop5PickerTest` — presence scoring, refusal counting, stale fallback
- `AwarenessNotificationInvariantTest` — regex check enforcing zero PendingIntent, zero actions
- `HealthSummaryPayloadTest` — JSON serialization round-trip, optional-field handling

## Counterpart routes on hapax-council

| Endpoint | Implementation | Purpose |
|----------|---------------|---------|
| `POST /phone/health-summary` (port 8042) | `agents/watch_receiver.py` | Persists to `~/hapax-state/watch/phone_health_summary.json` and mirrors as RAG markdown |
| `POST /phone/context` (port 8042) | `agents/watch_receiver.py` | Persists to `~/hapax-state/watch/phone_context.json` |
| `GET /api/awareness` (port 8051) | `logos/api/routes/awareness.py` | Returns awareness state; 503 + `X-Awareness-State-Stale: true` when stale |

## Ecosystem

| Repository | Role |
|-----------|------|
| [hapax-council](https://github.com/ryanklee/hapax-council) | Primary research artifact — voice daemon, grounding system, experiment infrastructure |
| [hapax-constitution](https://github.com/ryanklee/hapax-constitution) | Governance specification — axioms, implications, canons, precedents |
| [hapax-officium](https://github.com/ryanklee/hapax-officium) | Supporting software — management decision support |
| [hapax-watch](https://github.com/ryanklee/hapax-watch) | Wear OS biometric companion |
| **hapax-phone** (this repo) | Android health + context companion |
| [hapax-mcp](https://github.com/ryanklee/hapax-mcp) | MCP server bridging the logos APIs to Claude Code |

## License

PolyForm Strict 1.0.0 — see [LICENSE](LICENSE).
