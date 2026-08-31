# D2 Lean-Down — Navigation-Only App

Status: **PLANNED** (not started). Execution deferred — other issues to be tackled first.

> **Reconciled after the pre-D2 "navigation engine" work (commits `741b531`,
> `ddeb91e`).** That work front-loaded the mirror home: there is now a dedicated
> **`MIRROR_HOME` / `MirrorHomeScreen`** and a **Navigation engine selector**
> (Settings). So D2 no longer collapses to "home is always `NAV_HOME`" — instead
> it removes the *legacy MAIN grid + remote UI* while keeping two engine homes.
> See the amended Phase 3.

## Goal

Strip the app down to its navigation-only north-star (revamp decision **D2**):
remove ride recording / GPX, the handlebar remote / D-pad, and non-navigation
calibration. The app keeps **two mutually-exclusive navigation engines**, each
with its own home, chosen in Settings:

- **In-app maps (Google Nav SDK)** → `NAV_HOME` / `NavigationHomeScreen`.
- **Notification mirror** (Google Maps notifications → dash) → `MIRROR_HOME` /
  `MirrorHomeScreen`.

## Confirmed scope decisions

- **Keep the turn-mirror engine** (Google Maps app notifications → dash turns) and
  its `TURN_CALIBRATION` screen — this is *navigation* calibration, not "non-nav".
  Retain `AppNotificationListener`, `TurnIconHeuristic`, `IconHashCache`,
  `getTurnCalibration`, `NotificationRepository`, `NotificationNavProvider`.
- **Keep the engine selector.** `AppSettings.navProvider` / `googleNavEnabled` and
  `homeRoute()` → `NAV_HOME` (SDK) or `MIRROR_HOME` (mirror) stay. `navAppOverride`
  stays (used by the mirror engine's `isNavigationNotification`). *(All already in
  place from the pre-D2 work.)*
- **Remove only the legacy `MAIN` grid + remote UI**, not the mirror home. Delete
  the `MAIN` route/dispatch and `GridMenuScreen`; the mirror engine's home is the
  new `MIRROR_HOME` / `MirrorHomeScreen` (already built).
- **`DirectionScreen` is superseded by `MirrorHomeScreen`** → delete it (the mirror
  guidance display now lives in `MirrorHomeScreen`, which was written fresh rather
  than refactored from `DirectionScreen`).
- **Remove engine-detect entirely** — `VibrationMonitor`, `VIBRATION_CALIBRATION`,
  engine settings, and `TurnBeeper`'s engine-off muting.
- **Remove media + call handling** — only reachable via the deleted remote buttons.
- **Keep dash mirroring of call/message icons; drop the `NotificationScreen`
  phone-side viewer** (only reachable via the deleted MAIN/remote pipeline).

## Architecture context (why the phasing)

After the pre-D2 work the app has **three** homes (one to remove) and **two**
nav-to-dash engines:

- `NAV_HOME` (`NavigationHomeScreen`) — phone-first map home (Google Nav SDK). **Keep.**
- `MIRROR_HOME` (`MirrorHomeScreen`) — mirror-engine home (mirrored maneuver, ETA,
  Open Google Maps, pause-to-dash). **Keep** (new; already built). Selected when the
  engine = Notification mirror or the SDK is unavailable.
- `MAIN` (`GridMenuScreen` in `RemoteDpadScaffold`) — legacy "dash-companion" home,
  driven by the handlebar remote's `ControllerStateMachine`. **Remove.**
- Turn-mirror engine (`AppNotificationListener` → `TurnIconHeuristic` →
  `IconHashCache`/`getTurnCalibration` → `NotificationNavProvider` → dash) — separate
  nav pathway. **Kept.**

`DESTINATION`/`DestinationScreen` is only reachable from `DirectionScreen`, which
lives inside `MAIN` → both orphaned and removed (NAV_HOME replaces them; shared
`PlacesClient`/`RoutesClient`/`MapsUrlResolver` stay). `exitApp()` logic currently
lives in the remote's `ControllerStateMachine.Actions` but is used by **both**
`NAV_HOME onExit` and `MIRROR_HOME onExit` → must be extracted to a standalone
helper before the state machine is deleted.

Executed as **4 independently-buildable phases, one commit each**. Build/test after
each phase. Sequenced smallest → largest blast radius.

### Already completed by the pre-D2 "navigation engine" work

Not part of D2's deletions, but already in place — don't redo/undo these:

- `MirrorHomeScreen` (mirror-engine home) + `MIRROR_HOME` route.
- Navigation engine selector in Settings (`onEngineChanged`) backed by
  `navProvider`/`googleNavEnabled`.
- `homeRoute()` → `NAV_HOME` / `MIRROR_HOME`.
- Mutual exclusivity (mirror suppressed when SDK engine selected) + pause-to-dash
  (`NotificationNavProvider.paused`).
- Bluetooth-enable prompt on open; lock-screen/keep-awake while navigating;
  notification deep-links to the active nav screen; long-press pin drop.

---

## Phase 1 — Ride recording / GPX (isolated)

**Delete:** `location/RouteRecorder.kt`, `ui/screens/RidesScreen.kt`.

- **`MainActivity`:** remove `RIDES` enum; `importedGpx` flow + `collectAsState` +
  `LaunchedEffect` (402-405); `importGpxFromIntent` fn (292-318) + its calls (223,
  277); initial-route RIDES branch (383); BackHandler RIDES (447); RIDES dispatch
  (589-595); `onOpenRides` wiring (546, 558).
- **`BccuConnectionService`:** remove `routeRecorder` field/construction/`reevaluate`/
  `stop`, the `routeRecorder?.onEngineState(on)` line in the engine coroutine (leave
  the coroutine + `TurnBeeper.engineOn = on` — `VibrationMonitor` still exists until
  Phase 2), and `reevaluateRouteRecordingIfRunning`.
- **`AppSettings`:** remove `routeAutoRecordEnabled` + `KEY_ROUTE_RECORD`.
- **`SettingsScreen`:** remove `onOpenRides` param, "View rides" row, "Auto-record
  routes" toggle, "Export recorded routes" + `exportRoutes()` helper,
  `routeRecordEnabled` state; **strip only the `reevaluateRouteRecordingIfRunning`
  call** from the "Power saver" row (the row itself is removed in Phase 2).
- **`GridMenuScreen`:** strip ride icon + `onOpenRides`; **strip only the
  `reevaluateRouteRecordingIfRunning` call** from the PWR SAVE chip (chip removed in
  Phase 2; screen deleted in P3).
- **`res/xml/file_paths.xml`:** remove `routes/` line.
- **`AndroidManifest`:** remove GPX `ACTION_VIEW` intent-filters (65-83).

## Phase 2 — Engine-detect + power-saver removal (keep Symbol Testing)

**Scope note:** Symbol Testing is intentionally **retained** as a hardware
diagnostic (a deliberate deviation from the original "remove diagnostics" idea) —
so `SymbolTestScreen`, its routing, its icon-test storage, and the ★ "confirmed
working" badge in `TurnCalibrationScreen` all stay. Power saver only ever
throttled ride-recording + engine-detect (both removed), so it becomes dead and
is removed here across Settings / GridMenu / Onboarding.

**Delete:** `sensors/VibrationMonitor.kt`, `ui/screens/VibrationCalibrationScreen.kt`.
**Keep:** `ui/screens/SymbolTestScreen.kt`.

- **`MainActivity`:** remove only `VIBRATION_CALIBRATION` (enum, back branch,
  dispatch, `onOpenVibrationCalibration` wiring). **Keep all `SYMBOL_TEST` wiring**
  (`onOpenSymbolTest`, back branch, dispatch).
- **`BccuConnectionService`:** remove `vibrationMonitor` field/construction/destroy,
  the `vibrationMonitor?.currentSpeedKmh` line (keep `TurnBeeper.gpsSpeedKmh`), the
  entire `VibrationMonitor.engineOn.collect` coroutine, and
  `reevaluateEngineDetectIfRunning`.
- **`TurnBeeper`:** remove `engineOn` field + its `duckedNow()` engine branch — keep
  the beeper (GPS-stationary duck stays). **`AppNotificationListener`:** remove the
  `TurnBeeper.engineOn = VibrationMonitor.engineOn.value` feed.
- **`AppSettings`:** remove `engineDetectEnabled`, `vibrationIdleRms`,
  `vibrationEngineRms`, `vibrationSensitivity`, **and `powerSaveEnabled`** (+ keys).
  **Keep** `getIconTestResult`/`setIconTestResult`/`KEY_ICON_TEST_PREFIX` (used by
  Symbol Testing + the ★ badge).
- **`SettingsScreen`:** remove `onOpenVibrationCalibration` param; the "Engine
  detect (vibration)" toggle, "Calibrate engine detect", "Detection sensitivity"
  rows (+ `engineDetect`/`vibSensitivity` states); **and the "Power saver" row**
  (+ `powerSave` state). **Keep** `onOpenSymbolTest` + the "Symbol testing" row and
  the "Send test notification/guidance" rows.
- **`GridMenuScreen`:** remove the **PWR SAVE** chip + `powerSave` state.
- **`OnboardingScreen`:** remove the **power-save** step/toggle.
- **`TurnCalibrationScreen`:** unchanged — the ★ badge (`getIconTestResult`) stays
  (Symbol Testing retained).

## Phase 3 — Handlebar remote / D-pad + MAIN + media/call (largest)

**Delete:** `controller/` (3 files: `ControllerStateMachine.kt`,
`RemoteControlAccessibilityService.kt`, `RemoteModeOverlay.kt`),
`ui/components/RemoteDpad.kt`,
`ui/screens/{GridMenuScreen,NotificationScreen,DirectionScreen,DestinationScreen}.kt`,
`audio/MediaControlBridge.kt`, `audio/CallAudioRouter.kt`,
`telephony/CallStateMonitor.kt`, `res/xml/remote_control_accessibility.xml`.
(`DirectionScreen` is safe to delete — `MirrorHomeScreen` already replaces it.
**Do NOT** delete `MirrorHomeScreen`.)

- **`BccuProtocol`:** remove `RCM_REMOTE_CONTROL`, `HandlebarButton`, `RcmState`,
  `parseRcmValue`.
- **`BccuConnectionService`:** remove `buttonEvents` SharedFlow, `lastRcmMask`,
  `modeOverlay`, `upPressTimes`, `TRIPLE_UP_WINDOW_MS`, `DEBUG_MODE_OVERLAY`
  receiver, RCM dispatch (866) + `enableIndication(RCM)` (1016), `handleRcm`/
  `onHandlebarButton`/`showModeOverlay`/`applyRemoteMode` (1077-1198),
  `modeOverlay?.hide()` (601). **Do not touch** the dash-protocol handshake /
  crypto / TBT / telemetry / reconnect paths.
- **`MainActivity`:**
  - **Extract `exitApp()`** (149-155) into a standalone lambda/fun so
    `NAV_HOME onExit` still works; drop the rest of the
    `ControllerStateMachine.Actions` object, `stateMachine`, `mediaControl`,
    `callAudioRouter`, `callStateMonitor`, the `buttonEvents` collector, watchdog
    tick, disconnect-reset, and related imports.
  - `OpenDashApp`: drop `stateMachine`/`actions` params + call-site; drop
    `uiState`/`highContrast` → `OpenDashTheme(highContrast=false)`.
  - `homeRoute()` **already returns `NAV_HOME` (SDK) / `MIRROR_HOME` (mirror)** — keep
    it. Remove only `MAIN`: its enum value, back branch, dispatch block, and the
    `googleNavOffered` gating used by the dead `DirectionScreen`. Retarget any
    remaining `AppRoute.MAIN` default/fallback (e.g. `settingsReturnRoute` init,
    brand/settings fallbacks) to `homeRoute()`. Remove `destinationReturnRoute` +
    `DESTINATION` enum/back-branch/dispatch. **Keep** `MIRROR_HOME` wiring and the
    Bluetooth-enable prompt (both from the pre-D2 work).
- **`AppSettings`:** remove `gamepadEnabled`, `remoteMode` + `KEY_REMOTE_MODE`/
  `MODE_*`. **Keep** `navProvider`/`googleNavEnabled` (engine selector) and
  `navAppOverride` (used by the mirror engine).
- **`SettingsScreen`:** remove "Handlebar gamepad" GroupCard +
  `gamepadEnabled`/`accessibilityGranted` state. **Keep** the Navigation engine
  selector (`onEngineChanged`).
- **`OnboardingScreen`:** remove step 2 "Handlebar remote" + `RemotePreview`/
  `RemoteRow` + intro/permission copy referencing the remote.
- **`AndroidManifest`:** remove the accessibility `<service>`,
  `SYSTEM_ALERT_WINDOW`, `READ_PHONE_STATE`, `ANSWER_PHONE_CALLS`.
- **`strings.xml`:** remove `gamepad_accessibility_description`.
- **Keep:** `AppForegroundState`, `NotificationRepository`,
  `NotificationNavProvider`, `AppNotificationListener`, `TurnIconHeuristic`,
  `IconHashCache` (mirror engine intact).

## Phase 4 — Docs + polish

- Update `architecture.md` (drop legacy remote/GPX/companion sections; document a
  single map-home + two nav engines: SDK + mirror), `NAVIGATION_UX_REVAMP.md`
  (mark D2 done), `CHANGELOG.md`, `IMPLEMENTATION_CHECKLIST.md`.

---

## Risk notes

- Heaviest edits are `MainActivity` and `BccuConnectionService` in P3 — keep the
  frozen dash-protocol/crypto/handshake paths untouched; only excise RCM input.
- After P3, when the SDK is unavailable `NAV_HOME` shows its existing placeholder
  (mirror still runs) — acceptable per the MAIN-removal decision.
- Verified during research: `MediaControlBridge`/`CallAudioRouter`/`CallStateMonitor`
  and `NotificationScreen` are referenced only by `MainActivity` (+ themselves) —
  safe to delete. `READ_PHONE_STATE`/`ANSWER_PHONE_CALLS`/`SYSTEM_ALERT_WINDOW` are
  remote/call-only. No ride/GPX-exclusive dependency or permission exists.
