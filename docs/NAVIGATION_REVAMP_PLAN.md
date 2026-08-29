# Navigation Revamp — Google Navigation SDK → KTM TFT

Status: **planning complete, implementation pending**
Target vehicle: 2026 KTM 390 Adventure X (Gen-3 "connected" BCCU dash), also KTM/Husqvarna Gen-3.
Scope: replace brittle notification-parsing as the navigation source with an officially
supported Google navigation source, while reusing the existing, working KTM BLE/TFT
protocol layer unchanged.

This document is the authoritative low-level design (LLD). A phase-by-phase tracking
checklist lives in [`IMPLEMENTATION_CHECKLIST.md`](IMPLEMENTATION_CHECKLIST.md).

> **Provenance labels.** Throughout: **Confirmed (Google)** = stated in current Google
> docs; **Reverse-engineered** = derived from this repo / decompiled apps / binary
> inspection of the AARs; **Assumption** = our working hypothesis, to verify during
> implementation. All API-level facts here were verified against Navigation SDK for
> Android **7.9.0** (Aug 2026) and the Places API (New) web service.

---

## 1. Objectives

- **Objective 1 (investigation only):** determine whether a full-screen map/graphical
  navigation is possible on the KTM TFT.
- **Objective 2 (primary):** build a reliable Google-based turn-by-turn navigation source
  that feeds the existing KTM protocol, replacing notification parsing as the preferred
  mechanism.

---

## 2. Feasibility conclusions (from investigation)

### 2.1 Full-screen map on the KTM TFT — NOT possible over the KTM link

Evidence, labelled by confidence:

- **Reverse-engineered (this repo):** the BCCU navigation GATT service exposes only
  *predefined fields* — a fixed 58-entry turn-icon **enum** (`BccuProtocol.TurnIcon`),
  short **text labels** (distance/road/info/ETA/remaining, all length-capped), and a
  16-char notification banner, plus a telemetry RPC channel. There is **no
  characteristic that accepts a bitmap, tile, framebuffer or arbitrary graphics.** The
  dash renders its own built-in glyphs; unknown icon codes do not render.
- **Confirmed (KTM):** KTMconnect provides an *optional turn-to-turn navigation app via
  the TFT* — turn-by-turn only. No Android Auto / CarPlay / screen mirroring on the KTM
  dash.
- **Field evidence:** riders wanting a full map on a 390 Adventure add a **separate
  aftermarket CarPlay/Android Auto screen** (Ottocast/Carpuride/etc.) or a phone mount —
  precisely because the KTM dash cannot do it.
- **Model note:** press coverage indicates the 390 Adventure **X** ships with an LCD
  (the R gets the full-color TFT). If the unit is the plain LCD, dash nav may not be
  exposed at all; if it is the connected TFT variant, this app applies as-is.

**Verdict:** category "demonstrably impossible over the current protocol". Full-screen
map would require different hardware (aftermarket screen). Out of scope. We keep the
goal as accurate turn-by-turn to the dash.

### 2.2 Reading Google Maps' active navigation session — NOT possible

Google exposes **no public API** for a third-party app to inspect the route / maneuvers /
progress of a session running inside the consumer Google Maps app. Not via Maps SDK,
Navigation SDK, Routes API, nor any intent/deep link. The current notification-scraping
approach is the only way to observe consumer-Maps state, and it is brittle by design.
This limitation is real and is documented as such.

### 2.3 What Google DOES support — Navigation SDK for Android (chosen)

The **Navigation SDK for Android → Custom navigation experience → turn-by-turn data
feed** runs Google's own navigation engine **inside our app** (we own the session) and
delivers, ~1 Hz, a `NavInfo`/`StepInfo` feed with the fields we need:

- `Maneuver` **enum** (turn/sharp/slight/keep/fork/merge/ramp/roundabout with
  clockwise/counterclockwise + exit, U-turn, destination, ferry…),
- full and simplified **road name**, **distance to next step**, **remaining time**,
  **distance to destination**, driving side, lane guidance,
- nav **state**: `ENROUTE` / `REROUTING` / `STOPPED` (reroute handled by the SDK).

Google's docs call out this exact scenario ("navigation-only guidance projected to a
small screen … for two-wheeled vehicle drivers, with minimal distractions").

**Costs, limits and caveats — all Confirmed (Google) unless noted:**

- **Beta surface.** The `NavInfo` / turn-by-turn feed reference is explicitly marked
  *"This API is in beta and subject to change without guaranteeing backward
  compatibility."* We depend on a Preview API; pin the SDK version and re-verify on every
  bump. Some `NavInfo` distance/time getters are already deprecated in favour of
  `...FinalDestination...` variants.
- **Requires network.** The SDK fetches the route over HTTPS. This is a **functional
  regression** versus notification mirroring, which works with Google Maps' *offline*
  maps — relevant for rural India. Mitigated by keeping the notification provider as a
  first-class, user-selectable path (§3, §6).
- **Requires Google Play Services** on the device at runtime (plus ≥2 GB RAM, OpenGL ES
  2.0, and in-app attribution/licensing text). It does **not** require the Google Maps
  *app* to be installed.
- **Runs its own foreground service + persistent notification** during guidance
  (`NavigationService`, `foregroundServiceType="location"`), which is *"difficult to
  consolidate"* per Google. We already run our own FGS, so a second notification is the
  **default** unless we consolidate — see §5.8.
- **Cannot coexist with the Maps SDK for Android** in the same app; the Nav SDK bundles
  Maps and we must `exclude` `play-services-maps` from all transitive deps (§5.7).
- **Pricing/licensing.** Self-serve pay-as-you-go, **no Mobility agreement** for standard
  use. SKU **"Navigation Request"**, billed **per destination** on `setDestination(s)`
  and `simulateLocationsAlongNewRoute(...)`. Free cap **1,000 destinations/month global**;
  the **India** price list applies for India-based billing with a **7,000/month** free cap
  then $8/1,000. Starting guidance, reroutes and traffic updates add no charge. See §12.
- **Sends destination/location to Google** — a departure from the project's "nothing
  leaves the phone" default; hence opt-in, build-time/runtime key, and a README privacy
  note (§9).
- **Coverage.** India **is** supported, including **two-wheeler** routing (one of ~40
  two-wheeler countries). India lacks biking directions and traffic-light/stop-sign
  callouts (not needed here).
- **SDK/toolchain floor.** Nav SDK v7 needs `targetSdk` 36, `minSdk` ≥ 24, Kotlin 2.3,
  AGP 8.13.2, Gradle 8.13, core-library desugaring, multidex. This is a large toolchain
  jump from the repo's current Kotlin 1.9.24 / AGP 8.6 / SDK 34 — see §5.7 and Phase 1.

### 2.4 Second choice (future) — Routes API + own GPS map-matching

Compute the route ourselves (HTTPS; returns polyline + per-step maneuver enum +
distances) and maintain navigation state ourselves via GPS map-matching. Lighter (no
SDK, no Play Services), cheaper, available in India, but we own reroute/progress/
maneuver-advance logic. The architecture below is designed so this drops in later as a
`RoutesApiProvider` implementing one interface + one maneuver map. Because
`NormalizedManeuver` mirrors the SDK maneuver set closely (§5.1), a Routes API provider
maps cleanly onto the same domain model.

---

## 3. Locked decisions

| # | Decision | Choice |
|---|----------|--------|
| 1 | Primary nav provider | **Google Navigation SDK**, abstraction ready for Routes API later |
| 2 | API key handling | **`local.properties` → `BuildConfig`**, applied at runtime via `NavigationApi.setApiKey()`; opt-in |
| 3 | Packaging | **Single APK, runtime-gated** (SDK compiled in; active only with key + Play Services) |
| 4 | SDK level | **Bump** `compileSdk`/`targetSdk` 34 → 36 for Nav SDK v7 |
| 5 | Product flow | **Nav SDK primary** (in-app destination entry, we own the session); notification-mirroring is the fallback |
| 6 | Process | Plan → docs → implement on approval |
| 7 | APK size | **Accept it.** Single APK, `isMinifyEnabled = false` preserved (protects TFLite + reflection-heavy BLE). Free size wins only: `resConfigs("en")`, `abiFilters("arm64-v8a")` / ABI splits |
| 8 | Toolchain migration | Done **first, as Phase 1**, standalone, gated on an on-bike smoke test before any nav code |
| 9 | Domain-model granularity | `NormalizedManeuver` **mirrors the SDK `Maneuver` set ~1:1**; the lossy collapse to KTM codes happens **once**, in `KtmManeuverMapping` |
| 10 | Provider default | Nav SDK default when key + Play Services present, **but user-toggleable** in Settings, with network/notification tradeoffs stated |
| 11 | Destination search | **Places API (New) over plain HTTPS** — no Places SDK dependency, no dependency conflict, full Compose UI control |
| 12 | Map pin picker | Nav SDK's **bundled** Maps (`MapView` in an `AndroidView`); **no `mapId`**, Lite Mode never set |
| 13 | Maps URL import | **Share-sheet (`ACTION_SEND`) + paste box**, both into one best-effort `MapsUrlResolver`; clearly labelled as unofficial |
| 14 | Destination screen | Full: **Search · Map · Link**, plus saved waypoint + recents |

### Provider selection logic
```
if (BuildConfig.NAV_SDK_API_KEY not blank
        && Google Play Services available
        && settings.navProvider != NOTIFICATION)      // user can force NOTIFICATION
    -> GoogleNavSdkProvider    (primary: in-app "enter destination -> navigate")
else
    -> NotificationNavProvider (fallback: start in Google Maps, we mirror)
```
The provider is swappable at runtime; the KTM protocol layer never sees the difference.
The Settings toggle (decision #10) lets a rider pick the offline-capable notification path
even when a key is present.

---

## 4. Existing system (what we keep, and the one relaxation)

The KTM output layer is correct, carefully engineered, and reused verbatim:

- `ble/BccuProtocol.kt` — GATT UUIDs + wire formats.
  - Service `71ced1ac-0700-44f5-9454-806ff70b3e02`.
  - `0703` NAVIGATION_STATE `[flags: bit0 guidanceOn, bit1 gpsIconOn][volume]` — dash
    needs `guidanceOn=ON` before rendering.
  - `0704` TURN_ICON `[visibility][iconByte]` (enum).
  - `0705` TURN_DISTANCE (<=8), `0706` TURN_INFO (<=16), `0707` TURN_ROAD (<=32),
    `0708` ETA (<=8), `0709` REMAINING_DISTANCE (<=8) — `[visibility][UTF-8]`, ellipsized.
  - `070a` NOTIFICATION banner `[visibility][icon][<=16 UTF-8]`.
- `ble/BccuCrypto.kt` — AES-CBC/NoPadding; framed data plane; SHA-512 session-key pool.
- `ble/BccuConnectionService.kt` — handshake state machine, reconnect watchdog, serial
  GATT queue with per-characteristic coalescing, and the public methods we call:
  `sendNavigationState()`, `sendTurnIcon()`, `sendGuidance(distance, road, eta, remaining)`,
  `clearGuidanceIfRunning()` (debounced clear).

**Frozen:** `BccuProtocol`, `BccuCrypto`, and the BLE/handshake/reconnect/GATT-queue
internals of `BccuConnectionService`. The notification-side classes
(`AppNotificationListener`, `TurnIconHeuristic`, `ManeuverClassifier`, TFLite model) are
preserved and become the fallback provider's guts.

**Permitted relaxation (was "public methods only").** The coordinator needs behaviour the
current public surface can't express, so **additive public methods on
`BccuConnectionService` are allowed** — without touching the frozen internals. Concretely:

- `clearGuidance()` and `scheduleGuidanceClear()` are **private**
  (`BccuConnectionService.kt:1376`, `:1361`); the only public entry
  (`clearGuidanceIfRunning()`) is *always* 4 s-debounced. ARRIVED wants a **prompt** clear,
  so we add an immediate-clear entry point.
- Re-send of `NavigationState` + the current guidance snapshot on BLE re-auth needs a
  public hook (today `sendNavigationState(guidanceOn=true,…)` is sent exactly once, at
  authentication — `BccuConnectionService.kt:967`).

---

## 5. Target architecture

```
GoogleNavSdkProvider ─┐
NotificationNavProvider┼─► NormalizedNavigationState ─► KtmNavigationEncoder ─► DashWrite[]
(future RoutesApiProvider)                                                          │
                                             NavigationCoordinator applies DashWrite[]│
                                                                                      ▼
                        BccuConnectionService.sendNavigationState/sendTurnIcon/sendGuidance/clear*
                                                      (internals UNCHANGED)
```

New package: `com.navigator.app.nav` (+ `nav/destination` for destination entry).

### 5.1 Domain model (pure Kotlin — no Android deps, unit-testable)
`nav/model/NormalizedNavigationState.kt`

Decision #9: `NormalizedManeuver` mirrors the SDK `Maneuver` set ~1:1 so no information is
lost before the KTM boundary, and so a future `RoutesApiProvider` maps cleanly.

```kotlin
enum class NavSessionState { IDLE, ENROUTE, REROUTING, ARRIVED, STOPPED }
// NOTE: ARRIVED is NOT an SDK nav state. NavInfo.getNavState() only yields
// ENROUTE / REROUTING / STOPPED (+UNKNOWN). ARRIVED is synthesised from
// Navigator.addArrivalListener()/ArrivalEvent (see §5.6).

enum class NormalizedManeuver {
  DEPART, NAME_CHANGE, STRAIGHT,
  SLIGHT_LEFT, LEFT, SHARP_LEFT, SLIGHT_RIGHT, RIGHT, SHARP_RIGHT,
  KEEP_LEFT, KEEP_RIGHT, FORK_LEFT, FORK_RIGHT,
  MERGE_LEFT, MERGE_RIGHT, MERGE_UNSPECIFIED,
  ON_RAMP_LEFT, ON_RAMP_RIGHT, ON_RAMP_SLIGHT_LEFT, ON_RAMP_SLIGHT_RIGHT,
  ON_RAMP_SHARP_LEFT, ON_RAMP_SHARP_RIGHT, ON_RAMP_KEEP_LEFT, ON_RAMP_KEEP_RIGHT,
  ON_RAMP_UNSPECIFIED,
  OFF_RAMP_LEFT, OFF_RAMP_RIGHT, OFF_RAMP_SLIGHT_LEFT, OFF_RAMP_SLIGHT_RIGHT,
  OFF_RAMP_SHARP_LEFT, OFF_RAMP_SHARP_RIGHT, OFF_RAMP_KEEP_LEFT, OFF_RAMP_KEEP_RIGHT,
  OFF_RAMP_UNSPECIFIED,
  UTURN_LEFT, UTURN_RIGHT,
  // Roundabouts carry BOTH a shape and a rotation; exit number is separate.
  ROUNDABOUT_LEFT, ROUNDABOUT_RIGHT, ROUNDABOUT_SLIGHT_LEFT, ROUNDABOUT_SLIGHT_RIGHT,
  ROUNDABOUT_SHARP_LEFT, ROUNDABOUT_SHARP_RIGHT, ROUNDABOUT_STRAIGHT,
  ROUNDABOUT_UTURN, ROUNDABOUT_EXIT, ROUNDABOUT_GENERIC,
  DESTINATION, DESTINATION_LEFT, DESTINATION_RIGHT,
  FERRY_BOAT, FERRY_TRAIN,
  UNKNOWN
}

enum class RoundaboutRotation { CLOCKWISE, COUNTERCLOCKWISE, UNKNOWN }
enum class DrivingSide { LEFT, RIGHT, UNKNOWN }
enum class DistanceUnits { METRIC, IMPERIAL }

data class NormalizedNavigationState(
  val sessionState: NavSessionState,
  val maneuver: NormalizedManeuver = NormalizedManeuver.UNKNOWN,
  val roundaboutRotation: RoundaboutRotation = RoundaboutRotation.UNKNOWN,
  val roundaboutExit: Int? = null,          // null when absent OR SDK returned -1
  val drivingSide: DrivingSide = DrivingSide.UNKNOWN, // StepInfo.getDrivingSide()
  val distanceToManeuverMeters: Int? = null, // NavInfo.getDistanceToCurrentStepMeters()
  val roadName: String? = null,              // StepInfo.getSimpleRoadName() ?: getFullRoadName()
  val remainingTimeSeconds: Int? = null,     // NavInfo.getTimeToFinalDestinationSeconds() — NOT an epoch
  val remainingDistanceMeters: Int? = null,  // NavInfo.getDistanceToFinalDestinationMeters()
  val nextManeuver: NormalizedManeuver? = null,
  val units: DistanceUnits = DistanceUnits.METRIC,
  val producedAtMs: Long = 0L,
)
```

**Field-source corrections (Confirmed against the 7.9.0 reference):**
- Distance-to-next-turn is **`NavInfo.getDistanceToCurrentStepMeters()`**, *not* a
  `StepInfo` field. `StepInfo` only exposes `getDistanceFromPrevStepMeters()` (wrong
  direction).
- Remaining distance/time use `getDistanceToFinalDestinationMeters()` /
  `getTimeToFinalDestinationSeconds()`; the `...ToDestination...` variants are deprecated.
- **There is no ETA epoch in the SDK.** We store `remainingTimeSeconds` and let
  `EtaFormatter` derive the wall-clock "HH:MM" (see §5.4), which avoids per-tick epoch
  recomputation flapping across minute boundaries.
- `getRoundaboutTurnNumber()` returns `Integer` — *"Only set for roundabouts, otherwise
  -1"*, and may itself be null. `roundaboutExit` therefore guards **both** null and -1.
- `Maneuver`, `NavState`, `TravelMode`, `ErrorCode` are `@IntDef` **ints**, not Kotlin
  enums — `StepInfo.getManeuver()` returns `int`. Mapping is table-driven and the map's
  completeness is enforced by a reflection test (§8), since `when` exhaustiveness gives
  nothing here.

### 5.2 Provider interfaces
`nav/NavigationProvider.kt`
```kotlin
interface NavigationProvider {
  val id: String
  val state: StateFlow<NormalizedNavigationState>
  fun attach()
  fun detach()
}

/** Providers that OWN the session (Nav SDK now; Routes API later). */
interface RoutingNavigationProvider : NavigationProvider {
  fun startNavigation(dest: NavDestination, mode: TravelMode)
  fun stopNavigation()
}

data class NavDestination(val lat: Double, val lng: Double, val label: String?)
enum class TravelMode { TWO_WHEELER, DRIVING }
```
`NotificationNavProvider` implements `NavigationProvider` (passive). `GoogleNavSdkProvider`
implements `RoutingNavigationProvider` (active). Future `RoutesApiProvider` implements
`RoutingNavigationProvider`.

### 5.3 Pure maneuver mapping (table-driven, unit-tested)
`nav/ktm/KtmManeuverMapping.kt` — `NormalizedManeuver (+rotation, +exit, +drivingSide) ->
BccuProtocol.TurnIcon`. This is the **single** lossy collapse from the rich domain model to
the 58 KTM codes.

| Normalized | KTM `TurnIcon` |
|---|---|
| DEPART | START(20) |
| NAME_CHANGE / STRAIGHT / *_UNSPECIFIED straights | GO_STRAIGHT(2) |
| SLIGHT_LEFT / LEFT / SHARP_LEFT | LIGHT_LEFT(11) / QUITE_LEFT(12) / HEAVY_LEFT(13) |
| SLIGHT_RIGHT / RIGHT / SHARP_RIGHT | LIGHT_RIGHT(6) / QUITE_RIGHT(7) / HEAVY_RIGHT(8) |
| KEEP_LEFT / FORK_LEFT / ON_RAMP_KEEP_LEFT / OFF_RAMP_KEEP_LEFT | KEEP_LEFT(10) |
| KEEP_RIGHT / FORK_RIGHT / ON_RAMP_KEEP_RIGHT / OFF_RAMP_KEEP_RIGHT | KEEP_RIGHT(5) |
| MERGE_LEFT / ON_RAMP_LEFT / ON_RAMP_SLIGHT_LEFT / ON_RAMP_SHARP_LEFT | ENTER_HIGHWAY_LEFT_LANE(15) |
| MERGE_RIGHT / ON_RAMP_RIGHT / ON_RAMP_SLIGHT_RIGHT / ON_RAMP_SHARP_RIGHT | ENTER_HIGHWAY_RIGHT_LANE(14) |
| MERGE_UNSPECIFIED / ON_RAMP_UNSPECIFIED | GO_STRAIGHT(2) (no generic-merge glyph) |
| OFF_RAMP_LEFT / *_SLIGHT_LEFT / *_SHARP_LEFT | LEAVE_HIGHWAY_LEFT_LANE(17) |
| OFF_RAMP_RIGHT / *_SLIGHT_RIGHT / *_SHARP_RIGHT | LEAVE_HIGHWAY_RIGHT_LANE(16) |
| UTURN_LEFT / UTURN_RIGHT / ROUNDABOUT_UTURN | UTURN_LEFT(4) / UTURN_RIGHT(3) (by rotation) |
| ROUNDABOUT_* + rotation CW  + exit n | RAB_SECT_{n}_RH (26–41), n clamped 1..16 |
| ROUNDABOUT_* + rotation CCW + exit n | RAB_SECT_{n}_LH (42–57), n clamped 1..16 |
| ROUNDABOUT_* with exit null/-1 | fallback by shape → nearest graded turn (see below) |
| DESTINATION / DESTINATION_LEFT / DESTINATION_RIGHT | END(21) |
| FERRY_BOAT / FERRY_TRAIN | FERRY(22) |
| UNKNOWN / anything unmapped | UNDEFINED(1) — never emit a wrong arrow |

**Rotation source.** Use `StepInfo.getDrivingSide()` to decide CW vs CCW rather than
guessing: left-hand-traffic (India) → clockwise roundabouts. `roundaboutRotation` is filled
from the SDK maneuver's own clockwise/counterclockwise label; `drivingSide` is the tie-break
when a provider doesn't supply rotation.

**RH/LH is Reverse-engineered** and must be verified on hardware. The repo already emits
`RAB_SECT_{n}_RH` for Indian riders (`TurnIconHeuristic.kt:141`) and renders `_LH` as
"exits left" (`TurnIconGlyph.kt:167`). Theory (LHT→clockwise, and `_LH`≈left-hand traffic)
suggests `_LH` for India, i.e. the opposite — so **empirical wins**: push both variants to
the dash via `SymbolTestScreen.kt:97` / `TurnCalibrationScreen.kt:131` and lock the mapping
to what actually renders correctly. Overridable via the existing Turn-icon Calibration
screen regardless.

**Roundabout-without-exit fallback.** KTM has no generic roundabout glyph; every
`RAB_SECT_n` needs an `n`. When exit is null/-1 we do **not** invent one — we map by the
roundabout's shape to the nearest graded turn (`ROUNDABOUT_SHARP_LEFT`→HEAVY_LEFT, etc.),
or `GO_STRAIGHT` for `ROUNDABOUT_STRAIGHT`, or `UNDEFINED` for `ROUNDABOUT_GENERIC`.

**Newly-assigned KTM codes** (previously unused): `KEEP_MIDDLE(9)`,
`HIGHWAY_KEEP_LEFT(19)` / `HIGHWAY_KEEP_RIGHT(18)` (used for highway keep/fork variants when
distinguishable), `PASS_STATION(23)`, `HEAD_TO(24)`, `CHANGE_LINE(25)` remain available for
future providers; documented so they aren't rediscovered later.

### 5.4 Encoder (stateful logic, pure, unit-tested) — reliability core
`nav/ktm/KtmNavigationEncoder.kt`: `NormalizedNavigationState -> List<DashWrite>` where
`DashWrite` is a sealed set (`SetNavState`, `TurnIcon`, `TurnDistance`, `TurnRoad`, `Eta`,
`RemainingDistance`, `ClearGuidance(immediate: Boolean)`). Holds the last-sent snapshot and
enforces:

- **Dedup** — emit a write only when the formatted value changed.
- **Distance rounding buckets** (kills GPS jitter): `< 1000 m` → nearest 10 m;
  `>= 1 km` → 0.1 km; imperial equivalents. Fits the KTM `<=8`-char labels.
- **ETA** — `EtaFormatter` converts `remainingTimeSeconds` to a local wall-clock "HH:MM"
  zero-padded (firmware wants bare numeric time), with **minute-boundary hysteresis** so a
  ~1 Hz feed doesn't oscillate the shown minute and defeat dedup.
- **State machine:**
  - IDLE→ENROUTE: `SetNavState(guidanceOn=true)` then first guidance.
  - ENROUTE: deduped guidance writes.
  - REROUTING: blank maneuver/distance (or reroute banner) so the dash is never stuck on
    a stale turn; keep `guidanceOn`.
  - ARRIVED: `TurnIcon.END`, then `ClearGuidance(immediate=false)`.
  - STOPPED/cancel: `ClearGuidance(immediate=…)` (immediate on explicit stop; debounced on
    a transient remove).
- **Throttle** — <= 1 write-set/second even if a provider spams; must **cooperate with**,
  not fight, the existing per-characteristic GATT coalescing (`BccuConnectionService.kt:614`),
  which already collapses stale queued frames to latest-value-wins.
- **Missing/short maneuver** — no write on unchanged maneuver+distance; missing maneuver
  holds last valid, never emits UNKNOWN as a turn.

Supporting pure helpers: `ktm/DistanceFormatter.kt`, `ktm/EtaFormatter.kt`,
`ktm/DashWrite.kt`.

### 5.5 Coordinator (plumbing, thin)
`nav/NavigationCoordinator.kt`, owned by `BccuConnectionService`:
- selects the active provider (per §3 logic, honouring the Settings toggle),
- collects `provider.state`, runs each state through the encoder,
- forwards each `DashWrite` to the existing `BccuConnectionService.send*/clear*`,
- exposes normalized state to the UI (`DirectionScreen` reads it directly rather than a
  re-formatted mirror; see §5.6 note),
- feeds live metres to `TurnBeeper` (see below),
- on BLE re-auth, re-sends `NavigationState` and the current guidance snapshot so a
  reconnect never leaves the TFT stale (uses the new public re-auth hook, §4).

**`TurnBeeper` handoff.** Today `AppNotificationListener` calls
`TurnBeeper.onGuidance(distanceText, icon, roadText)`, and `TurnBeeper` re-parses the
distance *string* back to metres (`TurnBeeper.kt:165`). Once the encoder rounds into
buckets that re-parse degrades. The coordinator feeds `TurnBeeper` the
`distanceToManeuverMeters` **integer** from the normalized state instead.

### 5.6 Providers
- `nav/providers/NotificationNavProvider.kt` — wraps today's logic; `AppNotificationListener`
  / `TurnIconHeuristic` / `ManeuverClassifier` (TFLite) preserved, but now emit a
  `NormalizedNavigationState` instead of calling the BLE service directly. **Refactor
  hygiene while here:** move bitmap render + TFLite inference + disk I/O off the listener's
  main thread; construct `AppSettings` once, not per notification
  (`AppNotificationListener.kt:39,214`); fix the existing asymmetry where the in-app nav
  view clears immediately (`:222`) while the dash clear is 4 s-debounced.
- `nav/providers/GoogleNavSdkProvider.kt` (+ `NavInfoReceivingService`) —
  `NavigationApi.getNavigator`; set destination + `TravelMode.TWO_WHEELER` (fallback
  `DRIVING` if a route can't be produced); register the turn-by-turn service
  (`registerServiceForNavUpdates`); map `NavInfo`/`StepInfo` → `NormalizedNavigationState`;
  synthesise `ARRIVED` from `Navigator.addArrivalListener()`; read driving side from
  `StepInfo.getDrivingSide()`. Handles ToS consent (`NavigationApi.areTermsAccepted` /
  `showTermsAndConditionsDialog`, both need an Activity), reuses the existing location
  permission, and consolidates the foreground notification (§5.8).

**UI note.** `NotificationRepository.NavGuidance` currently stores *presentation strings*
(`NotificationRepository.kt:28`). Migrate `DirectionScreen` (`:49-51`), `GridMenuScreen`
(`:93-94`) and `MainActivity` (`:118,177,432`) to read the normalized state and format once,
rather than perpetuating pre-formatted mirrors.

### 5.7 Settings / Gradle / manifest
- `AppSettings.navProvider`: `NOTIFICATION` | `GOOGLE_NAV_SDK` (decision #10 toggle).
- `build.gradle.kts` (full requirement set for Nav SDK 7.9.0):
  - `compileSdk`/`targetSdk` = **36**; `minSdk` 26 already satisfies the ≥24 floor.
  - Kotlin **2.3.0**, AGP **8.13.2**, Gradle **8.13**; migrate Compose to the
    `org.jetbrains.kotlin.plugin.compose` plugin (the old `composeOptions`
    `kotlinCompilerExtensionVersion` route is gone under Kotlin 2.x); bump the Compose BOM.
  - `coreLibraryDesugaringEnabled = true` + `coreLibraryDesugaring
    "com.android.tools:desugar_jdk_libs_nio:2.1.5"`.
  - `multiDexEnabled = true`.
  - Nav SDK dep `com.google.android.libraries.navigation:navigation:7.9.0`, with
    `configurations.all { exclude group: "com.google.android.gms", module:
    "play-services-maps" }`.
  - `resConfigs("en")` and `abiFilters("arm64-v8a")` (or ABI splits) — the only size levers
    we take, since minify stays off (decision #7).
  - Read `NAV_SDK_API_KEY` from `local.properties` → `BuildConfig`; apply at runtime via
    `NavigationApi.setApiKey()` so a **keyless** build compiles and runs the fallback with
    nothing to fail on. Add `testImplementation` JUnit.
  - Note: the SDK docs' `dexOptions { javaMaxHeapSize }` snippet is **stale** under AGP 8;
    use `org.gradle.jvmargs` instead.
- `AndroidManifest.xml`: register `NavInfoReceivingService`; add the `ACTION_SEND`
  `text/plain` intent filter on `MainActivity` (decision #13); `ACCESS_BACKGROUND_LOCATION`
  is already declared (`:26`).
- **API key restrictions:** the key must have **Navigation SDK for Android** and **Places
  API (New)** enabled; add **Geocoding API** only if the URL-name fallback is built (§5.9).

### 5.8 Foreground-service / notification consolidation
The Nav SDK starts its own FGS + persistent notification on `startGuidance()`; we already
run `BccuConnectionService` as an FGS (`AndroidManifest.xml:80`). Without action the rider
sees **two** notifications.

Plan: use `NavigationApi.initForegroundServiceManagerProvider(application, notificationId,
NotificationContentProvider)` (or `initForegroundServiceManagerMessageAndIntent`) to make
the SDK reuse **our** notification id.
- It is a **one-shot singleton** — initialising twice throws `IllegalStateException`; call
  `clearForegroundServiceManager()` first and wrap in try/catch.
- Taking full control via a `NotificationContentProvider` **removes the SDK's own
  turn-prompt content** from the notification (accepted — the dash is the display surface,
  not the phone notification).
- `ACCESS_BACKGROUND_LOCATION` is already declared, which avoids the Android 14+
  `SecurityException` path when the location FGS starts in the background; still request
  "Allow all the time" for reliable background fixes. The SDK (≥5.4.0) also swallows that
  `SecurityException` itself.

### 5.9 Destination entry (`nav/destination/`)
Decision #11–14. Pure-HTTPS Places, bundled-Maps pin picker, best-effort URL import,
recents/saved.

- `DestinationSource` — sealed: `Search(placeId)`, `MapPin(lat,lng)`, `SharedUrl(raw)`,
  `SavedWaypoint`, `Recent`.
- `PlacesAutocompleteClient` — `POST https://places.googleapis.com/v1/places:autocomplete`
  (`HttpURLConnection`, mirroring `GeminiSummarizer.kt:52`). Sends `input`, an `origin`
  `{latitude,longitude}` from the last GPS fix so the response carries **`distanceMeters`**
  per suggestion, `regionCode="in"`, `locationBias` around the rider, and a **session
  token**. 250 ms debounce; cancel in-flight on new keystroke. Distance is **straight-line**
  — label it as such and suppress absent/zero values (Google: don't show a zero distance).
- `PlaceDetailsClient` — `GET https://places.googleapis.com/v1/places/{PLACE_ID}` with
  header `X-Goog-FieldMask: id,location` (Place Details **Essentials**; this terminates the
  session and preserves the autocomplete session discount — an IDs-Only field mask would
  forfeit it).
- `MapsUrlResolver` — resolves a Google Maps link to coordinates. **Unofficial/best-effort**
  (Google exposes no link-resolution API). Follows the 302 for `maps.app.goo.gl` (still live;
  app-generated goo.gl links were exempted from the Aug 2025 shutdown), then extracts, in
  order of trust: `!3d<lat>!4d<lng>` (the true pin) → `?q=lat,lng` / `?ll=` →
  `api=1&query=`/`&destination=`. **Never trusts `/@lat,lng,zoom`** (viewport centre, not
  the pin). Optional Geocoding-by-name fallback if only a place name survives. UI labels it
  best-effort and always shows the resolved point on the map for confirmation.
- `RecentDestinationsStore` — capped recents list in `AppSettings`, alongside the existing
  `waypoint`/`waypointName` (`SettingsScreen.kt:442`).
- `AppRoute.DESTINATION` screen — three sections **Search · Map · Link**, with saved
  waypoint + recents pinned on top. The map is the Nav SDK's bundled `MapView` inside an
  `AndroidView` with lifecycle forwarding; `setOnMapLongClickListener` drops the pin,
  `addMarker(MarkerOptions)` shows it. **No `mapId`** (avoids the Dynamic Maps SKU and keeps
  the load in the free/unbilled path), **never** Lite Mode (throws), custom `LocationSource`
  is a no-op. Do **not** use `maps-compose` (pulls the excluded `play-services-maps`).
- Confirm sheet → `RoutingNavigationProvider.startNavigation(NavDestination,
  TravelMode.TWO_WHEELER)`.
- Reachable from `GridMenuScreen` and `DirectionScreen`; also the `ACTION_SEND` share target
  routes here with the shared text pre-filled into `MapsUrlResolver`.

---

## 6. Product flow (Nav SDK primary, notification selectable)

1. First run: accept Nav SDK Terms (`showTermsAndConditionsDialog`, from an Activity) +
   location permission (already handled).
2. Rider opens the **Destination** screen and either: types in **Search** (sees matches +
   straight-line distance, taps one), long-presses a point on the **Map**, pastes or shares
   a Google Maps **Link**, or picks a **saved/recent** place.
3. `GoogleNavSdkProvider.startNavigation()` owns the session with
   `TravelMode.TWO_WHEELER`; the SDK streams `NavInfo`.
4. Coordinator → encoder → KTM dash shows turn arrow, distance, road, ETA, remaining.
5. Reroute / arrival / cancel handled via SDK states + arrival listener + encoder state
   machine.
6. **Provider choice (decision #10).** Default is Nav SDK when key + Play Services are
   present, but a Settings toggle lets the rider force the **notification** path (offline
   maps, no second notification). With no key / no Play Services the app silently uses
   `NotificationNavProvider` — the previous behaviour, preserved.

---

## 7. Reliability requirements → where handled

| Concern | Handled by |
|---|---|
| GPS jitter, small distance changes, duplicate/stale/missing instructions | Encoder dedup + rounding buckets + throttle + hold-last |
| Rapid consecutive updates | Encoder throttle + BLE queue coalescing (existing) |
| ETA flapping across the minute boundary | `EtaFormatter` hysteresis on `remainingTimeSeconds` |
| U-turns, roundabouts (+exit/rotation), highway on/off ramps, merge, name-change | Maneuver table (§5.3) over the ~1:1 domain model |
| Rerouting, destination reached, cancel, Google recalculating | Nav SDK states + arrival listener + encoder state machine |
| Temporary GPS loss | Nav SDK location engine; encoder holds last valid, REROUTING blanks turn |
| No network / offline | User-selectable notification provider (Nav SDK needs network) |
| Bluetooth disconnect/reconnect, stale nav after reconnect | Existing service reconnect + coordinator re-sends NavigationState + snapshot on re-auth |
| Two foreground notifications | FGS consolidation via `ForegroundServiceManager` (§5.8) |
| Main-thread work in the notification path | Provider refactor moves render/TFLite/IO off-thread (§5.6) |
| Screen off, background, battery optimisation | Existing foreground service + permissions (unchanged) |
| Unit conversion, distance rounding | DistanceFormatter / EtaFormatter |
| `TurnBeeper` ramp vs. bucketed distances | Coordinator feeds integer metres, not re-parsed strings (§5.5) |

---

## 8. Testing (no motorcycle required)

New `app/src/test/` (JVM/JUnit; none exist today — add the test toolchain to Gradle):

- `KtmManeuverMappingTest` — every maneuver incl. roundabout exits + rotation + driving
  side + the exit-null fallback.
- `KtmNavigationEncoderTest` — straight, L/R, slight, sharp, U-turn, roundabout+exit,
  highway on/off ramp, merge, name-change, arrival, reroute, missing maneuver, very short
  distance, unit conversion, **duplicate updates → no redundant writes**, stale→clear,
  cancel (immediate vs debounced).
- `DistanceFormatterTest` / `EtaFormatterTest` — rounding, locale, `<=` label-width,
  minute-boundary hysteresis.
- `GoogleNavManeuverMapTest` — **reflection-based completeness**: enumerate the SDK
  `Maneuver` `@IntDef` constants via reflection and assert every one maps to a
  `NormalizedManeuver` (so a future SDK bump that adds a constant fails the build). No SDK
  instantiation.
- `MapsUrlResolverTest` — recorded URL shapes (place link with `@` + `!3d/!4d`, `?q=`,
  `?ll=`, `api=1&query=`, `api=1&destination=`, coordinate-only, resolved short link);
  asserts `!3d/!4d` wins over `@`, and that `/@` alone is never used as the pin.
- `PlacesAutocompleteParserTest` — canned JSON incl. missing/zero `distanceMeters`,
  structured main/secondary text, session handling.
- `NavigationReplayTest` — feed a recorded `List<NormalizedNavigationState>`; assert the
  ordered `DashWrite`s (record → replay → inspect KTM messages).
- Debug `am broadcast` replay hook to feed a canned sequence on a real phone and log the
  encoded writes (no bike).

**Billing note for testing:** the Nav SDK `Simulator` / `simulateLocationsAlongNewRoute`
is **billable** (per destination). The pure replay harness above is free — prefer it for
routine testing and reserve on-SDK simulation for pre-release checks.

**Test-with-dash:** connect to a bike/dash, run a real Nav SDK route, verify turn arrow /
distance / road / ETA / remaining update and clear correctly; verify reroute and arrival;
verify roundabout RH/LH renders correctly (§5.3) and lock the mapping accordingly.

---

## 9. Documentation deliverables

- This plan (`docs/NAVIGATION_REVAMP_PLAN.md`).
- Checklist (`docs/IMPLEMENTATION_CHECKLIST.md`).
- `docs/architecture.md` — end-state architecture, data flow, provider model, Google SDK
  investigation + limitations (incl. beta-API and offline caveats), full-screen TFT
  investigation, decisions, known limitations, build/run/test instructions.
  Confirmed-vs-reverse-engineered labelled.
- `docs/BCCU_BLE_PROTOCOL.md` — the protocol write-up currently referenced by code
  comments but **missing** from the repo (UUIDs, payloads, crypto, handshake, provenance).
- **Attribution/licensing text** shown in-app (Nav SDK requirement) and a **Play data
  safety** disclosure for destination/location sent to Google.
- **README privacy note** — the Nav SDK path departs from "nothing leaves the phone";
  document what is sent, when, and how to stay on the offline notification path.

---

## 10. Risks / watch-list

- **Toolchain jump (highest risk).** Kotlin 1.9.24 → 2.3 (K2 compiler + Compose compiler
  plugin migration), AGP 8.6 → 8.13.2, Gradle 8.9 → 8.13, SDK 34 → 36, desugaring,
  multidex — all mandatory for Nav SDK v7 and all landing at once. Recompiles the frozen
  BLE layer under a new compiler. Retired first, in Phase 1, gated on an on-bike smoke test.
- **Android 15 + 16 behaviour changes at targetSdk 36**, against this app's unusually broad
  surface: `NotificationListenerService`, `AccessibilityService`, `SYSTEM_ALERT_WINDOW`,
  `BOOT_COMPLETED` → FGS start in `AutoConnectReceiver`, FGS runtime-permission enforcement,
  and FGS types `connectedDevice|location`. Auto-connect-on-ignition is the most exposed;
  verify on a fresh install. Needs its own investigation, not a single bullet.
- **Beta API.** `NavInfo`/turn-by-turn feed is Preview and may break across SDK bumps; pin
  the version, keep the reflection completeness test, re-verify on upgrade.
- **Offline regression.** Nav SDK needs network to fetch a route; notification mirroring
  works offline. Mitigated by the selectable notification provider (decision #10).
- **APK size.** Single-APK Nav SDK (~34 MB AAR) with minify off (decision #7) yields a large
  sideload; `resConfigs`/`abiFilters` are the only levers taken. Measure in Phase 2.
- **FGS/notification double-up** is the default until §5.8 consolidation is done.
- **Battery.** A full nav engine on top of BLE + GPS + accelerometer + TFLite; see the
  SDK's `optimize-power` guidance.
- **Roundabout CW/CCW → RH/LH** is reverse-engineered; verify on hardware and lock; keep the
  Turn-icon Calibration override.
- **Two-wheeler routing** availability confirmed for India, but per-route failures fall back
  to `DRIVING`; the SDK has no explicit "unsupported travel mode" error, so treat a missing
  route as the fallback trigger.
- **Nav SDK ToS consent** needs an Activity and must coexist with our foreground service;
  verify no double-notification / conflict (§5.8).
- **Maps SDK exclusion.** The Nav SDK forbids `play-services-maps`; both the map picker
  (bundled Maps) and search (pure HTTPS) are designed to respect this. Do not add
  `maps-compose` or the Places SDK.
- **Undocumented map-load billing.** A Nav SDK map load **with** a `mapId` bills to Dynamic
  Maps; **without** a `mapId` it is not listed as a billable trigger (almost certainly free,
  but Google does not state it). We ship without a `mapId`; the Dynamic Maps free cap
  (India 70,000/mo) covers us either way. Confirm with a small live billing check.
- **Maps URL import** is unofficial and fragile (see §5.9); it is a convenience over the
  map picker, never the only path.

---

## 11. Out of scope

- Full-screen / graphical map on the KTM TFT (proven infeasible over the protocol).
- Reading the consumer Google Maps app's active session (no public API).
- Routes API provider implementation (architecture is ready for it; not built now).
- Places SDK for Android (rejected in favour of pure-HTTPS Places API (New); avoids the
  Maps-SDK dependency conflict and APK growth).
- Older "MY RIDE" (Bluetooth Classic) bikes.

---

## 12. Cost model (single rider, India billing)

Per-month free caps that apply to this app (India price list; global caps are lower where
noted):

| SKU | Trigger | India free cap/mo | After (per 1,000) |
|---|---|---|---|
| Navigation Request | each destination in `setDestination(s)` / simulate | **7,000** (global 1,000) | $8.00 |
| Places Autocomplete Requests | per keystroke-batch without a live session token | 70,000 | $0.85 |
| Places Autocomplete Session Usage | requests ≥13 within a token session | unlimited / $0 | — |
| Place Details Essentials | `location` field mask (session terminator) | 70,000 | $1.50 |
| Dynamic Maps | map load **with** a `mapId` (we ship without) | 70,000 | $2.10 |
| Geocoding (only if URL-name fallback built) | address→coords | 70,000 | $1.50 |

Practically: only **Navigation Request** (7,000 route starts/month) has a realistically
approachable cap; everything else is effectively free for one rider. Use session tokens for
autocomplete, terminate on the Essentials details call, keep the picker map `mapId`-less,
and prefer the free replay harness over billable SDK simulation in testing.
