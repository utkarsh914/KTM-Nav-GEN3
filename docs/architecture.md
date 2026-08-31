# Architecture — KTM Navigator

End-state architecture after the navigation revamp (Nav SDK phases 1–6 + destination
entry). Companion to the design docs:
[`NAVIGATION_REVAMP_PLAN.md`](NAVIGATION_REVAMP_PLAN.md),
[`IMPLEMENTATION_CHECKLIST.md`](IMPLEMENTATION_CHECKLIST.md), and the wire-level
[`BCCU_BLE_PROTOCOL.md`](BCCU_BLE_PROTOCOL.md).

> **Provenance labels.** **Confirmed (Google/KTM)** = stated in vendor docs or
> decompiled vendor source; **Reverse-engineered** = derived from this repo / live
> GATT dumps; **Assumption** = working hypothesis to verify.

App id `com.navigator.ktm` · code namespace `com.navigator.app` (kept stable; see the
rebrand note in the checklist).

---

## 1. What the app does

The app is now **phone-first and map-centric**. The primary surface is a Google-Maps-style
map home (`NavigationHomeScreen`) built on the Nav SDK's bundled `NavigationView`: search →
confirm → route preview (with alternates) → on-phone turn-by-turn, while the same guidance
is streamed to the KTM/Husqvarna Gen-3 dash over BLE.

The app is now **navigation-only** (the D2 lean-down has landed): two mutually-exclusive
navigation engines, selectable in Settings, each with its own home. There are only two jobs:

1. **Navigate, phone-first (In-app maps engine)** — plan and run turn-by-turn in-app on the
   map home (`NavigationHomeScreen`), pushing the same guidance to the dash over BLE.
2. **Mirror another nav app (Notification-mirror engine)** — mirror Google Maps' turn
   notifications to the dash; the mirror home (`MirrorHomeScreen`) shows the same guidance.
   Offline-capable; also the home when no Nav SDK key/Play Services is available.

The engines are **mutually exclusive**: the Settings "Navigation engine" selector picks one,
`homeRoute()` maps it to `NAV_HOME` or `MIRROR_HOME`, and the mirror path is suppressed while
the in-app SDK engine is selected (so both never drive the dash at once).

The legacy dash-companion surface (handlebar-remote controller / on-screen D-pad / 2×2 grid
home), GPX ride recording, accelerometer engine-detect, and media/call handling have all been
**removed** in the D2 lean-down (commits `refactor(d2): ...`). Symbol Testing + Turn-icon
calibration are intentionally retained as hardware diagnostics.

See [`NAVIGATION_UX_REVAMP.md`](NAVIGATION_UX_REVAMP.md) for the design and
[`IMPLEMENTATION_CHECKLIST.md`](IMPLEMENTATION_CHECKLIST.md) for status.

---

## 2. Navigation: two sources, one pipeline

There are two ways guidance reaches the dash, unified behind one provider→encoder→dash
pipeline so the KTM protocol layer never sees the difference:

```
GoogleNavSdkProvider ─┐
NotificationNavProvider┼─► NormalizedNavigationState ─► KtmNavigationEncoder ─► DashWrite[]
(future RoutesApiProvider)                                                          │
                                       NavigationCoordinator applies DashWrite[] ────┘
                                                                     ▼
        BccuConnectionService.sendNavigationState / sendTurnIcon / sendGuidance / clearGuidance
                                        (BLE layer, internals UNCHANGED)
```

- **`GoogleNavSdkProvider`** — runs Google's Navigation SDK *inside the app* (we own the
  session). The rider enters a destination in-app; Google's engine streams a
  `NavInfo`/`StepInfo` feed we normalise. Needs network + Play Services + an API key.
- **`NotificationNavProvider`** — the offline-capable fallback: mirrors another nav app's
  (e.g. Google Maps) foreground notification. Passive; the app reads the notification.
- **`RoutesApiProvider`** — not built; the abstraction is ready for it.

Only one provider is active at a time, and the two engines are **mutually exclusive** per the
Settings "Navigation engine" selector (`AppSettings.navProvider`/`googleNavEnabled`). With the
in-app engine, `GoogleNavSdkProvider` runs the trip and the coordinator **auto-reverts** to
the (idle) notification provider on `ARRIVED`/`STOPPED`; while it's selected, the mirror path
is gated off in `AppNotificationListener` so Google Maps notifications don't also reach the
dash. With the notification-mirror engine, `NotificationNavProvider` drives the dash and the
in-app SDK is never started. The user can also **pause** mirror-to-dash on the mirror home
(`NotificationNavProvider.paused`).

### 2.1 Package map (`com.navigator.app.nav`)

| Package / file | Role |
|---|---|
| `nav/model/NormalizedNavigationState.kt` | Provider-agnostic domain model (pure Kotlin). `NavSessionState`, ~50-value `NormalizedManeuver` (mirrors the SDK), rotation/driving-side, `remainingTimeSeconds` (not an epoch), passthrough overrides, `NavDestination`, `TravelMode`. |
| `nav/NavigationProvider.kt` | `NavigationProvider` + `RoutingNavigationProvider` interfaces. |
| `nav/NavigationCoordinator.kt` | Collects the active provider's state, runs the encoder, applies `DashWrite`s via `DashOutput`; owns provider switching + auto-revert + re-auth re-send. |
| `nav/ktm/KtmNavigationEncoder.kt` | **Reliability core** (stateful, pure): dedup, distance rounding, throttle, ETA deadband, state machine. |
| `nav/ktm/KtmManeuverMapping.kt` | The single lossy reduction from `NormalizedManeuver` → 58-entry KTM `TurnIcon` (exhaustive `when`). |
| `nav/ktm/DistanceFormatter.kt`, `EtaFormatter.kt`, `DashWrite.kt` | Pure formatting + the sealed write set. |
| `nav/providers/NotificationNavProvider.kt` | Notification-mirroring provider (session-end debounce). |
| `nav/providers/GoogleNavSdkProvider.kt` | Nav SDK provider (owns `Navigator`, maps the feed, arrival→ARRIVED). |
| `nav/providers/GoogleNavManeuverMap.kt` | SDK `Maneuver` int → `NormalizedManeuver` + rotation. |
| `nav/providers/GoogleNavSdkController.kt` | Activity-side handshake: `setApiKey` → `getNavigator` (ToS) → start; switches the coordinator. |
| `nav/providers/NavInfoReceivingService.kt` | Bound service the SDK streams `NavInfo` to. |
| `nav/destination/PlacesClient.kt` | Places API (New) over HTTPS: autocomplete, details, text search. |
| `nav/destination/MapsUrlResolver.kt` | Best-effort Google Maps link → coordinates. |

Pure, JVM-unit-tested pieces: the model, formatters, maneuver maps, encoder, and the URL
coordinate parsing (`app/src/test/...`).

### 2.2 The encoder (why it exists)

The old notification path sent Google Maps' strings straight to the dash with **no** dedup,
throttle, rounding, or state machine — ~5 GATT writes/sec, stale turns left on the dash after
nav ended, etc. `KtmNavigationEncoder` centralises reliability:

- **Dedup** — a write is emitted only when its formatted value changes.
- **Distance rounding buckets** — kills GPS jitter (`<1 km`→nearest 10 m, `<10 km`→0.1 km,
  `≥10 km`→integer km; imperial equivalents), always ≤8 chars for the KTM labels.
- **ETA deadband** — the SDK gives *remaining seconds*, not an epoch; `EtaFormatter` derives
  local `HH:MM` and a deadband stops it flapping across the minute boundary.
- **Throttle** — label churn is capped to ~1 write-set/second; icon/state/road changes always
  pass. Cooperates with the BLE layer's per-characteristic coalescing.
- **State machine** — IDLE / ENROUTE / REROUTING / ARRIVED / STOPPED: holds the last valid
  turn (never emits UNKNOWN as an arrow), blanks a stale turn while rerouting, END + clear on
  arrival, immediate vs debounced clear, `reset()` for BLE re-auth.

### 2.3 Passthrough overrides (a deliberate compromise — revisit)

The notification path produces *already-formatted strings* and a *resolved KTM icon*, not
structured metres/seconds/maneuvers. Rather than lossily parse those back into numbers,
`NormalizedNavigationState` carries optional overrides (`resolvedIcon`,
`preformatted{Distance,Eta,Remaining}`). When present the encoder uses them verbatim — so the
notification path stays byte-for-byte what it sent before **but gains** the encoder's
dedup/throttle/state-machine. The Nav SDK path leaves them null and uses the numeric fields.
**Decision to revisit** at the end of all phases (see checklist).

---

## 3. Map home & destination flow (`AppRoute.NAV_HOME` · `NavigationHomeScreen`)

The app's primary surface. One persistent Nav SDK `NavigationView` is the map across a small
stage machine `BROWSE → SEARCH → CONFIRM → PREVIEW → NAVIGATING`:

- **BROWSE** — full map + location dot; top search bar; a **Connect/Connected** pill (bound
  to `BccuConnectionService.connectionState`, tap → Pairing); recenter (bottom-left); a
  custom compass (top-right, below Settings) shown only when the map is rotated (tap resets
  to north). The built-in Google compass is disabled.
- **SEARCH** — focused field + live Places Autocomplete (`PlacesClient`), plus **Recent** and
  **Favorites** (Home/Work/Saved) quick-access when the query is empty. Backed by
  `PlacesStore` (recents deduped + capped; favorites with unique Home/Work slots) persisted
  as JSON in a private `SharedPreferences`.
- **CONFIRM** — red pin + place card (name / address / straight-line distance) with **Get
  Directions** and a **Save toggle** (brand-accent filled bookmark → Saved list; Home/Work
  are managed in Settings → Saved places, `SavedPlacesScreen`).
- **PREVIEW** — `RoutesClient` (Routes API `computeRoutes`, TWO_WHEELER→DRIVE, alternates)
  draws the selected route (Google blue) + alternates (desaturated blue); a **route chip per
  option** selects reliably. ETA/distance + **Start**. The origin is a **fresh** location fix
  so the route-token origin matches the SDK's GPS.
- **NAVIGATING** — `GoogleNavSdkController.startNavigation` begins guidance and the SDK's
  stock `NavigationView` UI renders on the phone (maneuver header + ETA card + re-center +
  report). Overlay: a circular **END** (top-left, below the header); hardware Back also ends.
  Auto-returns to BROWSE on arrival.

**Start on the selected route.** The picked route's Routes API `routeToken` is passed via
`NavDestination.routeToken` → `Navigator.setDestinations(waypoint, CustomRoutesOptions(token,
TWO_WHEELER))`, with graceful fallback to default routing. Honored reliably in many cases;
the SDK may recompute to the fastest for some alternates when stationary (road-snapping) —
best-effort, validated on-ride (see checklist).

**Search backends.**
- **Autocomplete** — Places Autocomplete (New) over HTTPS with an `origin` for straight-line
  `distanceMeters`; tap a result → Place Details (New) `location` → CONFIRM.
- **Shared link** — a Google Maps link shared into the app routes to the map home, which
  resolves it via `MapsUrlResolver` (inline `!3d!4d` / `?q=` / `destination=` / `ll=` /
  `geo:` coords, else follow the short link + Places Text Search on the place name) and drops
  into CONFIRM.
- **Drop a pin** — long-press the map to drop a destination pin (labelled "Dropped pin") and
  jump to CONFIRM.

### 3.1 API-key authentication (important)

The single API key is **Android-app-restricted** (package + SHA-1). That works automatically
for the **SDKs** (Nav SDK, bundled `MapView`), which attach the app signature themselves. But
the **Places REST** calls are raw HTTPS, so they must send the app identity as headers:

- `X-Android-Package: com.navigator.ktm`
- `X-Android-Cert: <signing-cert SHA-1, uppercase hex, no colons>` (computed at runtime from
  `PackageInfo.signingInfo`, so it matches the debug cert now and any release cert later).

Without these, Places returns `PERMISSION_DENIED` ("client application &lt;empty&gt; blocked").
See `PlacesClient.androidAuth()`.

---

## 4. Google Navigation SDK — findings & limitations

Confirmed against Nav SDK **7.9.0**. Full investigation in the plan §2/§10; the load-bearing
points:

- **`NavInfo` turn-by-turn feed is a Preview/beta API** — "subject to change without
  guaranteeing backward compatibility". The SDK version is pinned; a reflection completeness
  test over the SDK `Maneuver` constants is the tripwire for new maneuvers.
- **Requires network** to fetch a route — a real regression vs the offline notification path.
  Mitigated by keeping the notification provider first-class and user-selectable.
- **Requires Google Play Services** at runtime (+ ≥2 GB RAM, OpenGL ES 2.0). Not the Maps app.
- **Runs its own foreground service + notification** during guidance → **two notifications**
  today. Consolidation via `NavigationApi.initForegroundServiceManager*` is deferred to the
  reliability pass (plan §5.8).
- **Cannot coexist with the Maps SDK** — the Nav SDK bundles Maps; `play-services-maps` is
  excluded from all configurations. The map-pin picker uses the *bundled* Maps.
- **Pricing** — self-serve, SKU "Navigation Request", billed per destination on
  `setDestination(s)`; free cap 1,000/mo global, 7,000/mo India. Places/Maps SKUs have large
  India free caps. See plan §12. A single rider is effectively free.
- **Two-wheeler routing** is available in India; the provider requests `TWO_WHEELER` and falls
  back to `DRIVING` if a route can't be produced.

### 4.1 What is NOT possible (investigated, ruled out)

- **Full-screen / graphical map on the KTM TFT** — the BCCU nav service exposes only a fixed
  turn-icon enum + short text labels + a 16-char banner; no bitmap/framebuffer characteristic.
  Reverse-engineered from this repo and consistent with KTM offering only turn-by-turn.
- **Reading the consumer Google Maps app's active session** — no public API (Maps SDK, Nav
  SDK, Routes API, intents). Notification scraping is the only observation point, which is why
  it exists as the fallback.

---

## 5. Key decisions

| # | Decision |
|---|---|
| Primary provider | Google Navigation SDK, abstraction ready for Routes API |
| API key | `local.properties` → `BuildConfig`, applied at runtime via `NavigationApi.setApiKey()`; opt-in |
| Packaging | Single APK, runtime-gated (SDK compiled in; active only with key + Play Services) |
| SDK level | compileSdk/targetSdk 36; minSdk 26; Kotlin 2.3 / AGP 8.13.2 / Gradle 8.13 |
| Provider default | Nav SDK when key + Play Services present, user-toggleable in Settings |
| APK size | Accepted (~29 MB debug). Levers: `abiFilters("arm64-v8a")`, `androidResources.localeFilters("en")`; **minify off** (protects TFLite + reflection-heavy BLE) |
| Domain model | `NormalizedManeuver` mirrors the SDK set; single lossy collapse in `KtmManeuverMapping` |
| Destination search | Places API (New) over HTTPS (no Places SDK — avoids the Maps-SDK conflict) |
| App identity | `applicationId` = `com.navigator.ktm`; code namespace kept `com.navigator.app` |

---

## 6. Build, run & test

### 6.1 Toolchain
Kotlin 2.3.0, AGP 8.13.2, Gradle 8.13, JDK 17+, compileSdk/targetSdk 36, core-library
desugaring, multidex. Open in a recent Android Studio; it uses its bundled JDK (JBR).

### 6.2 Google Cloud setup (once)
In one Cloud project **with billing enabled**:
1. Enable **Navigation SDK for Android**, **Places API (New)**, and **Maps SDK for Android**
   (the last for the map-pin picker). (Geocoding API only if a URL-name fallback is added.)
2. Create an API key. Restrict it: **Android apps** → package `com.navigator.ktm` + your
   signing SHA-1 (debug SHA-1 from `./gradlew :app:signingReport`; add the release SHA-1 for
   release builds). API restrictions → the three APIs above.
3. Put it in `local.properties` (gitignored):
   ```
   NAV_SDK_API_KEY=AIza...
   ```
   It feeds `BuildConfig.NAV_SDK_API_KEY` (applied via `NavigationApi.setApiKey()`) and the
   manifest `com.google.android.geo.API_KEY` (for `MapView`). A **keyless** build still
   compiles and runs the notification fallback.

### 6.3 Unit tests (no device)
```
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:testDebugUnitTest
```
Covers the maneuver maps, encoder scenarios, formatters, replay, and the Maps-URL parsing.

### 6.4 On-device / on-bike
Build + run from Android Studio. Pair with the dash, then on the map home: search / long-press
to drop a pin / share a Maps link → a destination → confirm → route preview → Start; verify
turn arrow / distance / road / ETA / remaining on the TFT, reroute, and arrival. The pure
`NavigationReplayTest` exercises the encoder without a bike; the Nav SDK `Simulator` is
billable, so prefer the replay harness for routine testing.

---

## 7. Privacy & attribution

- **Notification-mirroring mode** stays fully on-device: it reads another app's notification
  and forwards text to the dash. Nothing leaves the phone.
- **In-app Google navigation mode** sends your **destination and location to Google** (that's
  how the SDK/Places work) and requires network + Play Services. This is a deliberate
  departure from "nothing leaves the phone"; it is opt-in (needs a key) and user-toggleable
  (Settings → Navigation → In-app Google navigation → off = mirror-only, offline).
- **Attribution** — Google's Terms require the Nav SDK ToS to be accepted (shown on first use)
  and in-app attribution/licensing text. *(Pending: surface the attribution/licensing text and
  a Play data-safety disclosure — tracked for release.)*

---

## 8. Known limitations & deferred items (→ reliability pass, Phase 7)

- Verify the notification-mirroring leg of auto-revert on a bike.
- Reconnect-after-ignition-cycle failure (observed; suspected pre-existing / targetSdk-36).
- FGS/notification consolidation (two notifications during Google nav).
- 16 KB native-lib alignment (`libtensorflowlite_jni.so`, `libandroidx.graphics.path.so`).
- BLE-layer deprecation warnings (`getDefaultAdapter`, GATT callback overrides) — left in the
  frozen layer.
- Roundabout RH/LH + section→angle mapping: **hardware-verified** (RH = clockwise; `RAB_SECT_N`
  is an exit-angle glyph, `turnAngle = (8−N)·22.5°`); glyph now chosen by exit angle, not the
  ordinal exit count.
- Off-main-thread TFLite in the notification path; migrate the in-app UI to read normalized
  state; units hardcoded metric in the SDK provider; automatic provider selection is
  toggle-gated but not location/region aware.
