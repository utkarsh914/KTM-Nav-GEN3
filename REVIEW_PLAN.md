# KTM Navigator: Code Review & Improvement Plan (rev. 2)

_Review date: 2026-11-18 · Scope: whole app (`app/src/main`, tests, build, docs) · Status: **plan only, not implemented**_
_Rev. 2 incorporates an independent audit of rev. 1 against the code, the Nav SDK 7.9.0 AAR, and developer.android.com._

**Target platform: Android 16+ only (API 36).** `minSdk` goes 26 → 36. Phase 0 removes the legacy code paths this makes dead.

Severity: 🔴 feature broken · 🟠 crash / state corruption · 🟡 medium · 🟢 low.
"Possible" / "verify first" = inferred from code or unverified protocol/device behaviour; confirm before changing.
🏍 = needs an on-bike / on-device check before it can be called done.

Tracker: [`TRACKER.md`](TRACKER.md) mirrors this document item-for-item (IDs match).

---

## Changes from rev. 1 (audit corrections)

**Removed / downgraded because the code already handles it or the claim was overstated**
- Typed BLE write status is **already checked** (`BccuConnectionService.kt:1400-1412, 1503-1519`; `ENGINEERING_NOTES.md:223-225`). Phase 0 only deletes the pre-33 branches; B2 no longer depends on it.
- `neverForLocation` on `BLUETOOTH_SCAN` is **already set** (`AndroidManifest.xml:13-14`).
- `registerReceiver` flags: only 3 call sites; `MainActivity:157,184` already pass flags, `BccuConnectionService:366` (system broadcast) is exempt. Reduced to deleting the `SDK_INT<33` else-branches.
- Core-library desugaring **must stay** (Nav SDK AAR sets `coreLibraryDesugaringEnabled=true`); only the stale comments change.
- R4 (GPS filtering) does **not** depend on Phase 0: `elapsedRealtimeNanos` / speed accuracy already work at minSdk 26; `isFromMockProvider` works today.
- U7 (config-change state loss) is **not** Android-16-specific: the manifest has no `configChanges`/`screenOrientation`, so rotation already recreates the activity everywhere.
- C3 "stranded" overstated (END/minimise stay visible); R3 only theoretical (recovery runs from `Application.onCreate` before any recorder); NET1 lower severity (cleartext already blocked at targetSdk 36); N6 `volatile` point dropped (all writers on main).
- `AppNotificationListener.kt:48` already redacts the title; only package + category are logged (reworded privacy item).

**Corrected / made conditional**
- C4 fix would reverse a documented decision (`AppNotificationListener.kt:252-255`): now "ongoing OR navigation category, per package, plus key tracking, verified on real notifications".
- S2 / B11: crash is conditional (ACL broadcast + app battery-optimised + service not already running); CDM presence is **not** an Android 16 feature (API 31+; 16 adds a new overload) and helps S2/B11/B4, **not** B1.
- Live Updates promoted-notification request/permission are **36.1** → needs compileSdk 36.1 check against the AGP 8.13.2 pin.
- Manifest cleanup trap: the Nav SDK manifest re-adds unbounded `WRITE_EXTERNAL_STORAGE` / `BLUETOOTH*`; must use `tools:node="remove"`.
- B3, B4, B7, NP4, B2-timeout, N3, N5 rewritten as "verify first" or with safer designs (see items).

**Added**
- **N1 now also covers `RouteStatus.ROUTE_CANCELED`** (treated as failure → retries `setDestinations` with the superseded trip, billing each call and cancelling the new trip).

**Reordered**
- Phase S (safety net) added and runs first; B1 now uses a dedicated BLE thread so the old "S1/NP1 before B1" constraint no longer applies.
- N1 moved into Phase 1 (C3 depends on it). S1's `security-crypto` migration moved to Phase 5. Phase 6's duplicate route-failure/Add-Replace bullet removed (owned by C3/N4). B9 before B4. S1 + NP1 before B1.

---

## Phase S: Safety net & verification protocol (do FIRST; the dash is not available)

**Constraints:** no dash for now; an Android 16 phone is available over `adb` (hands-on checks by the user, installs/logcat by the agent); no emulator installed. Unit tests (161 today) run locally.

**Decisions (user, 2026-11-18)**
- Dash-dependent items ("bucket C") are implemented **only if confidence is ≥ 50% that nothing breaks**, and they all land in **one single commit** so it can be reverted with one `git revert`.
- Behaviour-changing fixes (C4 stricter nav detection, R4 GPS filtering) are applied **directly** (no shadow mode).
- The debug-only **Dash preview** panel is built first.

- [ ] **SF1** Baseline: run `testDebugUnitTest`, `assembleDebug` and `lint` on the untouched code; record results in `TRACKER.md`. Every later step must stay at least this green.
- [ ] **SF2** Snapshot tests that lock what is sent to the dash today (any byte change fails a test): every `BccuProtocol` builder (turn icon, distance, road, ETA, remaining, nav state, notification; truncation, visibility); `BccuCrypto` round-trips with fixed keys/IVs and the control-message format; the full `KtmManeuverMapping` table incl. roundabout angles and merge codes; encoder/coordinator `DashWrite` sequences for the `NavigationReplayTest` scenarios; notification-parsing reference samples.
- [ ] **SF3** Scripted fake-dash handshake test (m1/HELLO → reply → key array → key index → AUTHENTICATED) at the crypto/protocol level. Extract handler logic from `BccuConnectionService` only if the extraction is trivially behaviour-preserving.
- [ ] **SF4** Debug-only **Dash preview**: a `DashOutput` tee (debug builds only) recording the last icon, labels, nav state and banners, shown in an on-screen debug panel; works with no dash connected. Must not change release behaviour or the BLE write path.
- [ ] **SF5** Phone smoke checklist + `adb` install/logcat routine (below), run after each phase.
- [ ] **SF6** Git hygiene: one plan item per commit; tests run after each; a tag at each phase boundary (`phase-N-done`); bucket C is one commit.

### Rollout buckets

| Bucket | Items | Rule |
|---|---|---|
| **A: unit-testable** | C1, C2, N1 (+ROUTE_CANCELED), N2, N3, N6, N7, U1-U6, U13, P1, PL1-PL3, R1-R3, R5, R7, NP2, NP5, NP8, NP10, NET2-NET5, S4-S6, PR1, PR3, ST1, ST3, ST6, ST7, 0.1, 0.2, 0.5 | Do now, with tests |
| **B: needs only the Android 16 phone** | C3, C4, N4, U7-U12, S1, S3, NP1, NP6, NP7, R4, R6, 0.3, 0.6, UX items | Do now; check on the phone with the Dash preview + smoke checklist |
| **C: dash-dependent** | see below | One single commit; conservative parameters; ≥ 50% confidence only |

### Bucket C: one revertable commit

| Item | In? | Confidence | Conservative scope |
|---|---|---|---|
| 0.4 delete pre-33 BLE paths | yes | ~95% | Code never runs on API 33+ |
| B9 `teardownLink()` | yes | ~85% | Pure refactor |
| B6 validate key pool | yes | ~85% | Only rejects malformed data |
| B2 `finish(uuid)` op match | yes | ~80% | Unit-tested; **2 s timeout unchanged** |
| B10 small BLE checks | yes | ~75% | Marquee-width change excluded (cosmetic) |
| B3 keys kept aside until AUTHENTICATED | yes | ~70% | In-memory until auth succeeds; **no new incoming-message validation** |
| B5 cooldown for every service start | yes | ~65% | Worst case: up to 45 s wait if the app is opened during a cooldown |
| B1 single thread for GATT callbacks | yes | ~60% | **Dedicated BLE `HandlerThread`**, not main |
| B4 backoff | yes | ~60% | Dash-drops-before-auth reuses the existing 15/45 s cooldown; absent-bike backoff capped at **2 min**; reset on bike link up / adapter-on / app foreground |
| B8 liveness | yes | ~55% | **30 s** silence threshold; first thing to drop if it misbehaves |
| B7 | UI part only | n/a | "Re-pair needed" notification; **do not send error statuses 3/4** |
| B11 CompanionDeviceManager | **no** | <50% | Deferred until the dash is available |
| N8 `guidanceOn` OFF when idle | **no** | <50% | Deferred (needs dash) |
| NP4 classifier preprocessing | **no (for now)** | n/a | Deferred until the 192/192 eval is re-run (runs on the computer) |

S2 (try/catch around the background FGS start) is still done; only the real fix (B11) is deferred.

### Behaviour changes applied directly
- **C4** stricter nav-notification detection (could drop updates if Maps doesn't set the ongoing flag: check on the phone with real Maps notifications).
- **R4** GPS filtering: ride totals will differ from past rides. Mock fixes are still accepted in **debug builds**.
- **PR2** public Downloads log copy becomes opt-in.
- **NP3** road-name severity parsing changes.
- **ST5** FileProvider authority: dropped unless there is a real install-conflict reason.

### Phone smoke checklist (after each phase)
Install over `adb` and watch logcat for crashes, then check:
- onboarding and permissions
- search → preview → start (check the Dash preview) → minimise and resume → END
- arrival (short real drive or the billed Nav SDK simulator, a few runs only)
- mirror mode with Google Maps navigating, plus a traffic / location-sharing notification
- ride record → history → replay
- settings; share logs
- rotation and dark-mode switch
- Bluetooth off/on
- revoke "Nearby devices": no crash

---

## Phase 0: Raise minSdk to 36 and delete legacy paths

- [ ] **0.1** `app/build.gradle.kts:28` → `minSdk = 36`. (Nav SDK minSdk is 24, AGP requirement 8.13.2: compatible.)
- [ ] **0.2** Remove all `Build.VERSION.SDK_INT` branches (27 sites), including receiver-flag else-branches (`MainActivity.kt:158-160, 185-187`) and O/Q/T guards for channels / FGS types (`RideRecordingService.kt:71-73, 86-89, 108-112`, BLE service).
- [ ] **0.3** Manifest cleanup: drop the app's own capped `BLUETOOTH`, `BLUETOOTH_ADMIN`, `WRITE_EXTERNAL_STORAGE` entries **and** add `tools:node="remove"` so the Nav SDK's unbounded versions don't come back (merged manifest already has `uses-permission-sdk-23` BLUETOOTH entries at lines ~67-68). Verify with the merged manifest.
- [ ] **0.4** BLE dead code: delete the pre-33 `else` branches for `writeCharacteristic`/`writeDescriptor` (`BccuConnectionService.kt:1398-1412, 1501-1519`) and the legacy `onCharacteristicChanged(g,c)` / `onCharacteristicRead(g,c,status)` overloads (`:797, :815`), which never run on API 33+. Dead-code removal only; behaviour unchanged.
- [ ] **0.5** Keep core-library desugaring (Nav SDK requires it). Fix the stale comments (`build.gradle.kts:86, 180`; `java.time` is already native at minSdk 26). Remove no-op `multiDexEnabled` (`:30-32`).
- [ ] **0.6** Android 16 checks 🏍: edge-to-edge is enforced with no opt-out → verify insets (status/nav bar, cutout, IME) on every screen; predictive back is default (no `onBackPressed` overrides exist; confirm all back handling uses `BackHandler`); orientation/resizability restrictions are ignored on sw ≥ 600 dp (`PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY` is a temporary opt-out if needed).
- [ ] **0.7** After Phase 0, update docs: rewrite `ENGINEERING_NOTES.md:221-237` (GATT dual-path section) and `architecture.md:264, 276` (minSdk 26 references).

---

## Phase 1: Critical bugs, crashes, privacy

### Broken features

- [ ] **C1 🔴 Mirror mode never mirrors without Nav SDK.** `navProvider` defaults to `NAV_PROVIDER_GOOGLE_NAV_SDK` (`AppSettings.kt:293`); `AppNotificationListener.kt:62` returns when `googleNavEnabled`; selector hidden when SDK unavailable (`SettingsScreen.kt:328`) while `homeRoute()` (`MainActivity.kt:341-344`) shows the mirror home. **Fix:** one helper `navSdkActive = isAvailable && setting`, used by listener, home route, settings.
- [ ] **C2 🔴 45 s idle auto-finish never runs.** `NavigationHomeScreen.kt:639-649`: loop condition reads `isActiveNav()`, still IDLE when `startTo()` sets `stage = NAVIGATING`; keys never change when ENROUTE arrives. **Fix:** key on `isActiveNav()`, or `while (isActive)` with conditions inside.
- [ ] **C4 🔴 Any Google Maps notification is treated as turn guidance.** `isNavigationNotification` is package-only (`AppNotificationListener.kt:247-257`) → traffic/location-sharing/rating notifications go to the dash, beep, and their removal clears real guidance (`:198-209`); title is also sent as TURN_DISTANCE (see NP5). **Fix:** `FLAG_ONGOING_EVENT || CATEGORY_NAVIGATION`, applied per package (at least `com.google.android.apps.maps`); remember the nav `sbn.key` and clear only when that key is removed; skip group summaries. **Do not** require the category alone (documented regression at `:252-255`). Verify against real Maps/Waze notifications first. **Rollout: applied directly (user decision); check on the phone with real Maps notifications.** 🏍
- [ ] **N1 🟠 Trip generation token + ROUTE_CANCELED handling** (`GoogleNavSdkProvider.kt`). Prerequisite for C3.
  - Every `setOnResultListener` (`:190-207, 241-260`), arrival callback and `onNavInfo` frame checks the token; `_state` writes happen on main.
  - Fixes stale route `OK` restarting a cancelled/replaced trip (`guidanceActive = true; startGuidance()`).
  - Fixes the `onNavInfo` check-then-act race (SDK HandlerThread vs `stopNavigation()` on main, `:282-377`) that can leave state ENROUTE after STOPPED, leaving `isTripInProgress` stuck true.
  - **`RouteStatus.ROUTE_CANCELED` ("canceled in favor of a newer one") is terminal for that request: never retry it.** Today any non-OK retries the token (`:197-205`) or TWO_WHEELER→DRIVING (`:248-251`) with the superseded waypoints, cancelling the new trip, risking a false STOPPED, and billing every call. Double-tap Start (U5) or replace-destination mid-trip triggers it. 🏍 (cancel during route computation)
- [ ] **C3 🟠 Route failure is silent on the NAVIGATING screen.** Only active/ARRIVED handled (`NavigationHomeScreen.kt:565-579`); `STOPPED` from `GoogleNavSdkProvider.kt:254` and `getNavigator` errors (`GoogleNavSdkController.kt:119`, log only) are ignored. END/minimise stay visible, so the rider isn't stuck, but sees "On the way" with a blank header. **Fix (after N1):** route-failure card (Retry / Back to preview / Use Maps mirror). Slow starts: show "Still calculating…" rather than auto-aborting at ~15 s (token → retry → TWO_WHEELER → DRIVING can legitimately take longer). 🏍

### Crashes

- [ ] **S1 🟠 `AppSettings` crash-proofing** (`AppSettings.kt:16-27`: eager `EncryptedSharedPreferences.create`, no try/catch). Failure kills ride recording (`RideRecorder.kt:29`), the app-scope coroutine (`OpenDashApplication.kt:68`, whole process), the notification listener (every notification), and screens; also rebuilt (Keystore) at ~30 call sites incl. the 20 s watchdog. **Fix:** single process-wide instance; `prefs by lazy { runCatching {…}.getOrElse { recreate / fall back } }`. _(The migration off `security-crypto` is Phase 5.)_ Do before B1.
- [ ] **S2 🟠 Background FGS start from `AutoConnectReceiver`** (`AutoConnectReceiver.kt:35-49` → `BccuConnectionService.kt:112`). Throws `ForegroundServiceStartNotAllowedException` only when: ACL broadcast + app battery-optimised (that is an exemption; `SettingsScreen.kt:679-694` offers it) + service not already running. BOOT_COMPLETED is exempt. **Fix now:** try/catch + log/notify. **Real fix:** B11. Verify on device. 🏍
- [ ] **S3 🟠 Unguarded FGS / BLE entry points.** `startForeground(CONNECTED_DEVICE)` throws if Nearby-devices is revoked (`BccuConnectionService.kt:272, 299-316`); `connect()` (`device.name`, `connectGatt`, `getRemoteDevice`) throws from coroutines with no handler (`:594-596`); `RideRecordingService.startForeground(LOCATION)` (`RideRecordingService.kt:39`). **Fix:** permission check + try/catch; **only when the permission is missing** return `START_NOT_STICKY`/`stopSelf()` (don't change the always-on design otherwise); service-scope `CoroutineExceptionHandler`. 🏍 (Nearby-devices revoked)
- [ ] **S4 🟠 Settings screen crash:** `bondedDevices` without BLUETOOTH_CONNECT, and non-null-safe `getSystemService` (`SettingsScreen.kt:167-174`).
- [ ] **S5 🟠** `TurnBeeper.preview()` builds `AudioTrack` in an unhandled coroutine (`TurnBeeper.kt:109-117`).
- [ ] **S6 🟠** `onNotificationPosted` has no try/catch (`AppNotificationListener.kt:43-151`).

### Privacy: release logs are mirrored to public `Download/OpenDash`

- [ ] **PR1** Length-only in release for: notification text (`BccuConnectionService.kt:1123, 1134, 1137`), road/ETA/distance (`:1291-1304`), WhatsApp group/sender titles (`AppNotificationListener.kt:131, 140`), destination labels (`GoogleNavSdkController.kt:113`, `GoogleNavSdkProvider.kt:189, 240`, `RideRecorder.kt:74`). The per-app "Posted:" line (`:48`) already redacts the title but still logs package + category for every app: log after filtering or debug only. (Redaction scope is broader than `ENGINEERING_NOTES.md:99` promises; update the doc.)
- [ ] **PR2** Make the public Downloads mirror opt-in, add rotation and a size cap (`AppLogger.kt:55-57, 113-141`).
- [ ] **PR3** Crash handler writes synchronously before delegating (`AppLogger.kt:84-91`); crashes are almost never captured today.

---

## Phase 2: Navigation state, UI state, pairing

### Navigation state

- [ ] **N2** `NavInfoReceivingService` never quits its HandlerThread (`:45`) → quit in `onDestroy`.
- [ ] **N3 ARRIVED can be conflated away** (likely only when the bike is connected): coordinator revert → `detach()` → `stopNavigation()` → STOPPED overwrites ARRIVED before Compose sees it (`NavigationCoordinator.kt:176-178`). **Decision:** one-shot `tripEnded(reason)` SharedFlow the UI consumes. (Do **not** skip `stopNavigation()` in `detach()` for terminal states: nothing else stops SDK guidance when the UI isn't in NAVIGATING.)
- [ ] **N4 Trip ownership from the provider, not UI stage.** `backToBrowse()` resets `leftNavManually` while a trip runs (`NavigationHomeScreen.kt:502-508, 564`) → arrival never handled, dash never reverts; shared link / saved place hijack the stage mid-trip (`:652-667`). **Fix:** `owningTrip = isTripInProgress`; handle ARRIVED/STOPPED in any stage; mid-trip picks offer "Add as stop / Replace destination". 🏍
- [ ] **N5 Recording vs route failure is inconsistent.** `_currentDestination` is cleared on failure only when the BLE coordinator reverts (`NavigationCoordinator.kt:176-178` → `GoogleNavSdkProvider.kt:98-99, 270`); with no bike it stays set. `OpenDashApplication.kt:55-60` keys recording on destination **on purpose** (offline route failures still record). **Decision needed:** either always keep recording until the user ends/cancels, or clear on failure everywhere; make behaviour independent of BLE state.
- [ ] **N6 `NotificationNavProvider` race:** the debounce coroutine can write STOPPED after a newer ENROUTE because `cancel()` races the non-suspending write after `delay` (`:97-107`). Use a generation counter. Pause comment says it "clears immediately" but IDLE → debounced clear (`:49`; encoder clears immediately only on STOPPED, `KtmNavigationEncoder.kt:80-82`): emit STOPPED on pause or fix the comment.
- [ ] **N7** Ride recording keyed on destination value (`OpenDashApplication.kt:62-86`): A→B merges rides; same-destination restart may not emit; `recording = true` even if start failed. Key on the N1 trip token.
- [ ] **N8** _DEFERRED (needs dash)._ `NAVIGATION_STATE guidanceOn=true` sent on every auth even when idle, never turned OFF (`BccuConnectionService.kt:1004`; backlog). Decide after a dash test. 🏍

### UI state

- [ ] **U1** Hardware Back in SEARCH while adding a stop wipes the trip (`NavigationHomeScreen.kt:693`): mirror the on-screen back's `searchTarget` branch.
- [ ] **U2** Old stops leak into a new destination (`:489-498, 689`) → clear `stops`.
- [ ] **U3** Start before routes load ignores "Car" (`:516`) → default to `appSettings.vehicleMode`.
- [ ] **U4** Place-details lookup survives Back and jumps to CONFIRM (`:792-801`) → keep the `Job`, cancel on back; "Couldn't load place" on null.
- [ ] **U5** Double-tap Start may call `startTo` twice (possible) → `starting` guard (pairs with N1/ROUTE_CANCELED).
- [ ] **U6** Greeting banner stuck if cancelled mid-`delay` (`MainActivity.kt:474-483`) → `try/finally`.
- [ ] **U7** Config change (rotation, night-mode switch) resets all nav UI state (`MainActivity.kt:345`, `NavigationHomeScreen.kt:246-305`) → ViewModel + `SavedStateHandle`. Not Android-16-specific, but large-screen orientation is no longer lockable.
- [ ] **U8** Permission request on every `onCreate` (`MainActivity.kt:126`) → only when `savedInstanceState == null` and not granted.
- [ ] **U9** Replay `onDispose` clears the shared map after Home has set it up (`RideReplayScreen.kt:358-371`); Home never cleans its listeners/marker (`NavigationHomeScreen.kt:671-682`) → owner token + `DisposableEffect`.
- [ ] **U10** Compass/GPS listeners run while backgrounded (`LocationMarker.kt:64-216`, `NavHomeGuidance.kt:210-234`) → gate on lifecycle ≥ STARTED.
- [ ] **U11** Search origin captured once (`NavigationHomeScreen.kt:349`, `SavedPlacesScreen.kt:374`); preview bounds include stale origin (`:434`); `lastLocation` ignores freshness (`:1808-1811`).
- [ ] **U12** Speedometer freezes on GPS loss (`NavHomeGuidance.kt:218-221`); `SpeedMonitor._speedKmh` never nulls on stale fix (`SpeedMonitor.kt:56-59`); limit < 10 never clears the alert (`:117`).
- [ ] **U13** Fallback trip time computed during composition (`:1010-1012`) → freeze in `finishTrip()`.

### Pairing

- [ ] **P1** `connectingAddress` never reset → can't pick a device after a failure (`PairingScreen.kt:170-171, 264-270`).
- [ ] **P2** Bike saved as bonded before AUTHENTICATED (`:266-267`); scan not stopped on leave/connect (`:159-164`); no "Bluetooth off" state (`:133`).
- [ ] **P3** Re-pair is one tap, no confirm, no back (`SettingsScreen.kt:315` → `MainActivity.kt:579-589`) → confirm dialog + `pairingReturnRoute = SETTINGS`.

---

## Phase 3: BLE robustness

Order inside the phase: **B9 → B4**. Bucket C items land together in **one commit** (see Phase S); B11 is deferred.

- [ ] **B1** GATT callbacks on one looper. Passing the main looper puts Keystore-backed `AppSettings` calls in the auth path (`:942, 963, 975, 995, 1047`) on main, so use a **dedicated BLE `HandlerThread`** (decision: no dependency on S1/NP1). Removes the binder-vs-main races on `handshakeTimeoutJob`, `handshakeRecoveries`, `settleJob`, `scanCallback` (`:183-227`) and the "callback before `gatt` is assigned looks stale" case (`:596` vs `:683-687`). 🏍
- [ ] **B2** `SerialGattQueue.finish()` ignores the op id (`:92-95`): a late completion after the 2 s timeout releases the next in-flight op, then busy rejections cascade and drop the queue (incl. auth ACKs). **Fix:** `finish(uuid)` matched to the in-flight op, or swallow N late completions. **Measure before raising the timeout** (a 5-10 s timeout freezes guidance that long per lost callback, against the self-heal rationale in `ENGINEERING_NOTES.md:118-125`). Typed write status is already checked.
- [ ] **B3** Control messages are never validated (`:912-965`). CBC "decrypts" any 16 bytes: 1/256 chance of a fake `GENERATE_KEYS` (overwrites stored keys → forced re-pair) and 1/16 chance of a key-index command (fake AUTHENTICATED). **Safer design:** keep a freshly derived pool aside and only overwrite the stored pool after AUTHENTICATED succeeds. Validating marker/version bytes needs the incoming format confirmed first (those bytes are in *our* outgoing frames, `BccuCrypto.kt:72-79`); "only accept GENERATE_KEYS after INITIAL_CONNECTION" could break recovery when the dash lost its keys. Verify first. **Bucket C scope: key pool kept aside until AUTHENTICATED only; no new incoming-message validation.** 🏍
- [ ] **B4** One backoff policy (after B9). Real problems: the dash drops an unauthenticated link at ~15 s, before our 25 s timeout, and the DISCONNECTED handler cancels `handshakeTimeoutJob` and rescans after 1.5 s with no cooldown (`:703, 718-722`); a bonded bike that's absent loops scan 45 s → direct connect → rescan all day (`:495-501`). **Constraints:** the documented "no retry cap" rationale (`:1036-1038`) stays; exponential backoff must **reset** on ACL/CDM presence, adapter-on, and app foreground (else ignition-on could wait up to 10 min); keep the existing 15/45 s handshake-stall cooldown unless a hardware test says otherwise. **Bucket C parameters:** dash-drops-before-auth reuses the existing 15/45 s cooldown; absent-bike backoff capped at **2 min**; reset on bike link up, adapter-on, app foreground. 🏍
- [ ] **B5** `onStartCommand` honours the cooldown only when `present == true` (`:536-541`); non-present starts (boot, app open, sticky restart) direct-connect during cooldown (`:533-558`) → apply the guard to all starts.
- [ ] **B6** Validate the persisted key pool on load (16 × 16 bytes) (`AppSettings.kt:222-226`); catch in `sendEncrypted` (`:1087-1092`).
- [ ] **B7** Documented error statuses 3/4 are never sent (`:982-985`; `BCCU_BLE_PROTOCOL.md:170-174`). **Verify against the official SDK before sending** (effects on the dash unknown). **Bucket C: do NOT send statuses 3/4; implement only the "Re-pair needed" UI.** After N failed handshakes for a known bike, show "Re-pair needed". Investigate Android 16 `BluetoothDevice.ACTION_KEY_MISSING` (uncertain relevance: BCCU pairing is app-layer). 🏍
- [ ] **B8** Heartbeat liveness after auth (watchdog skips AUTHENTICATED, `:385`): no dash message for > **30 s** (conservative vs the ≈1 Hz heartbeat; first to drop if it causes reconnect loops) → recycle. 🏍
- [ ] **B9** Single `teardownLink(reason)` (logic duplicated ~4×; `linkUp` / `pendingAutoConnect` not reset). Do before B4.
- [ ] **B10** Small: check `discoverServices()` (`:695`) / `requestMtu` results and `onServicesDiscovered` status; drop to `CONNECTION_PRIORITY_BALANCED` after auth (`:761`); `elapsedRealtime()` instead of wall clock (`:399, 582, 690`); scan filter uses upper-cased `target` (`:473`); encrypt inside the queued op, not at enqueue (`:1087`); marquee width counts graphemes vs 16 UTF-16 units (`:1132, 1160`; **marquee excluded from bucket C**, cosmetic).
- [ ] **B11** _DEFERRED until the dash is available (excluded from bucket C: <50% confidence)._ Replace `AutoConnectReceiver` with CompanionDeviceManager. Requirements: `uses-feature companion_device_setup`, `REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE`, a `CompanionDeviceService`, `REQUEST_COMPANION_START_FOREGROUND_SERVICES_FROM_BACKGROUND` (the association itself exempts the FGS start), and a one-time association dialog for existing paired users. Presence observing for BLE is scan-based; **verify it fires while the dash is linked over classic Bluetooth** (`:489-494`). Also: `ACTION_STATE_CHANGED` is never delivered to a manifest receiver (dead branch, `AutoConnectReceiver.kt:38-45`); `ACL_CONNECTED` delivery with `exported=false` is a low-priority sanity check (it is on the implicit-broadcast exception list). Add `MY_PACKAGE_REPLACED` handling. 🏍

---

## Phase 4: Data integrity, parsing, network

### Rides
- [ ] **R1** Lock all `RideStore` read-modify-writes (`RideStore.kt:55-89`; process-wide lock or single-thread dispatcher; `commit()`); expose a `StateFlow`; move main-thread I/O off (`RideHistoryScreen.kt:122, 308-310`, `RideReplayScreen.kt:142`, `SettingsScreen.kt:754`).
- [ ] **R2** Tolerant parsing: one bad entry empties the list and the next write wipes the index (`RideStore.kt:37-44`, `RideJson.kt:75-82`); never write back after a parse failure; drop blank ids (duplicate `LazyColumn` keys crash); persist `saved` in track meta so recovery keeps it.
- [ ] **R3 🟢** Orphan recovery should skip the active track (`RideStore.kt:153-166`). Only theoretical today (runs from `Application.onCreate`), but cheap.
- [ ] **R4** GPS filtering and honest stats (`RideRecorder.kt:70, 83-93`, `RideMetrics.kt:57-93`): drop `accuracy > 25 m`, reject implied speed > 300 km/h, ignore sub-noise segments, skip mock fixes (`isFromMockProvider`), max speed from a short median, moving time + moving average, duration from `elapsedRealtimeNanos`. Independent of Phase 0. **Mock fixes are still accepted in debug builds** (so mock-location testing works). Applied directly. 🏍
- [ ] **R5** Prune by creation id, not GPS `startMs` (`RideMetrics.kt:122`).
- [ ] **R6** `RideRecordingService`: stop when the recorder fails to start (empty FGS today), `stopSelfResult(startId)`, add `contentIntent` + "Stop" action.
- [ ] **R7** fsync periodically / at close (`RideStore.kt:116-120`); `haversine` clamp; missing `t` → skip, not 0.

### Places
- [ ] **PL1** Tolerant parsing (one bad entry wipes Home/Work, `PlacesStore.kt:53-71`).
- [ ] **PL2** `removeFavorite(s)` deletes Home/Work with matching coords (`:94-104`) → filter `slot == OTHER`.
- [ ] **PL3** `coordKey` uses the default locale (`"%.4f"`) → `Locale.ROOT`.

### Notification parsing
- [ ] **NP1** Off-main-thread nav processing (single-thread executor for render → hash → TFLite → disk). **Removals (`pushRemoved`) go through the same executor** (it checks current ENROUTE state, `NotificationNavProvider.kt:98`). Cache "no confident match" (`ManeuverClassifier.kt:137`); lazy memory-mapped model (`:94-97`); read calibration from shared plain prefs, not a new `AppSettings` per lookup (`IconHashCache.kt:51`).
- [ ] **NP2** `TurnBeeper.parseMeters` (`:173`): anchor `^\s*(\d[\d.,]*)\s*(km|mi|m|ft|yd)\b`, thousands separators, add `yd`, pass the ASCII-normalised title (`AppNotificationListener.kt:105`).
- [ ] **NP3** Severity words in road names ("Bear Creek Rd", "Sharp St") override the turn (`TurnIconHeuristic.kt:197-203`) → require adjacency to left/right; parse the instruction field first.
- [ ] **NP4** _DEFERRED until the 192/192 eval is re-run (runs on the computer, no dash needed)._ Classifier preprocessing differs from training (48 px render → upscale, no margin, RGB vs alpha; `TurnIconHeuristic.kt:35, 302`, `ManeuverClassifier.kt:171` vs `ml/train_maneuver_model.py:80-104`). **Verify first:** `ENGINEERING_NOTES.md:60` records 192/192 verified in Python and on-device; re-run that evaluation before changing preprocessing, then retrain if needed. 🏍
- [ ] **NP5** Title sent as dash distance without checking it is one (`AppNotificationListener.kt:91`) → validate with `DISTANCE_RE`.
- [ ] **NP6** Mirror home clears instantly on Maps' remove/repost while the dash debounces (`:206-207`) → clear the repository on provider STOPPED.
- [ ] **NP7** Message mirroring: skip ongoing/FGS notifications and own package; dedup by `(key, text)` (`:120-150`).
- [ ] **NP8** NBSP / U+202F normalisation in `NavSubtextParser`; AM/PM token-boundary match (`:46-47`); `maneuverTextFor` mislabels HIGHWAY_KEEP_* / KEEP_MIDDLE (`AppNotificationListener.kt:178-188`).
- [ ] **NP9** Cap captured unknown-icon PNGs (LRU) (`IconHashCache.kt:60, 80-91`); `onListenerConnected` → sync `activeNotifications`.
- [ ] **NP10** Delete `LegacyModelRemap` and `LegacyModelRemapTest` (dormant; backlog).

### Network
- [ ] **NET1 🟡/🟢** `MapsUrlResolver` host allowlist (`:124-139`): parse with `URI`, allow `maps.app.goo.gl`, `goo.gl`, `(maps.|www.)?google.<tld>`, re-check every redirect hop, reject non-2xx/3xx, trim trailing punctuation (`:44`). (Cleartext is already blocked: no `networkSecurityConfig`, targetSdk ≥ 28; don't add "require https" as a new control. Result also needs user confirmation at CONFIRM.)
- [ ] **NET2** `regionCode` hard-coded `"in"` (`PlacesClient.kt:89, 169`, `RoutesClient.kt:100`) → derive from location/locale.
- [ ] **NET3** `RoutesClient` retries the other travel mode on any failure incl. offline (~40 s) (`:69-70`) → only on empty 200; tolerate a truncated polyline per route (`:203, 213`).
- [ ] **NET4** `autocomplete` returns `emptyList()` on any error (`PlacesClient.kt:117-120`) → result type so the UI distinguishes "Offline / failed" from "No matches".
- [ ] **NET5** `ConnectivityMonitor` → `registerDefaultNetworkCallback` (`:42-71`).

---

## Phase 5: Settings, build, docs hygiene

- [ ] **ST1** Clamp speed-band count/step and overspeed limit (`AppSettings.kt:255-257, 321-333`, `SettingsScreen.kt:776-790`): 10M bands → ANR.
- [ ] **ST2** Driving side AUTO: use `TelephonyManager.networkCountryIso`/`simCountryIso` before locale (`DrivingSidePreference.kt:63`).
- [ ] **ST3** `runCatching` around OEM-dependent `startActivity` (`SettingsScreen.kt:412, 685`, `OnboardingScreen.kt:308`, `LogsScreen.kt:58`).
- [ ] **ST4** Mirror-from apps dialog: lazy list + off-main label loading (`SettingsScreen.kt:867-870, 1290`); refresh background-location status on resume (`:533`).
- [ ] **ST5** FileProvider authority → `${applicationId}.fileprovider` (`AndroidManifest.xml:109`, `LogsScreen.kt:58`). **Conditional:** `ENGINEERING_NOTES.md:212-214` says "no change needed". Only do it if the reason is an install conflict with an old `com.navigator.app` build; otherwise drop.
- [ ] **ST6** `versionCode`/`versionName` deterministic (`build.gradle.kts:33-39`); versionCode must stay monotonic (e.g. git commit count).
- [ ] **ST7** `.gitignore`: add `*.jks`, `*.keystore`; remove tracked empty `my-logs.txt`.
- [ ] **ST8** Bump old AndroidX/coroutines (lifecycle 2.8.6, activity 1.9.2, core-ktx 1.13.1, coroutines 1.8.1) within the toolchain limits in `ENGINEERING_NOTES.md:187-192`.
- [ ] **ST9** Migrate off `security-crypto:1.1.0-alpha06` (read-legacy-then-rewrite; backlog) _(moved from S1)_; decide on session-key storage (plain today, `AppSettings.kt:201-219`, vs class KDoc `:8-13` and `:132`, `:287-293`).
- [ ] **ST10** Docs corrections: `TelemetrySchema.kt`/`parseRcmValue()` referenced but removed (`BCCU_BLE_PROTOCOL.md:13, §5.3, §6`); architecture.md:41 says ride recording removed (it isn't); stale reconnect comments (`autoConnect=true` in `AutoConnectReceiver.kt:19-22`, `BccuConnectionService.kt:370-377, 563-566`); `IconHashCache.kt:33` location; `ManeuverClassifier` class count (28); "replies GENERATE_KEYS/HELLO" comments (`MainActivity.kt:580-582`, `AppSettings.kt:181-186`); `ENGINEERING_NOTES.md:99` logging scope.
- [ ] **ST11** Backup decision: `allowBackup="false"` is already set (`AndroidManifest.xml:42`) and stops cloud backup but **not device-to-device transfer**, so plain-prefs pairing keys can move to a new phone. Decide `dataExtractionRules` for rides/places vs keys.

---

## Phase 6: UX polish & accessibility

(Route-failure card → C3; mid-trip Add/Replace → N4.)

- [ ] **UX1** "Resume navigation" pill in every stage (not only BROWSE).
- [ ] **UX2** In-context banners: Location off/denied (map home never requests it today), Bluetooth off, Nearby-devices denied.
- [ ] **UX3** Search: clear (×), IME "Search" action, feedback when a shared link can't be resolved (`NavigationHomeScreen.kt:658`) or is ignored in mirror mode (`MainActivity.kt:394`).
- [ ] **UX4** Touch targets ≥ 48 dp (64 dp glove mode) 🏍: stop-row icons 22 dp (`NavigationHomeScreen.kt:1416-1437`), preview back 24 dp (`:1326`), add-stop ~26 dp (`:1349`), search back 28 dp (`NavHomeSearch.kt:75`), Home/Work remove 20 dp (`SavedPlacesScreen.kt:353`), `KtmToggle` 42×24 with non-clickable parent row (`KtmComponents.kt:114-121`); large full-width END-confirm buttons (`:1531-1532`).
- [ ] **UX5** Contrast: `Ktm.Dim` ≈ 2.4:1 light / 3.6:1 dark (`DesignTokens.kt:165, 189`); raise 10-13 sp stat text.
- [ ] **UX6** Semantics: `Role.Switch` + state on `KtmToggle`; selected state on `RouteChip`/`SaveToggle`; connection state in the pill description; readable turn-icon descriptions (`TurnIconGlyph.kt:54`).
- [ ] **UX7** Units setting (metric/imperial, incl. yards for UK) used by formatters, UI, rides, overspeed; follow system 12/24h.
- [ ] **UX8** Day/night from real sunrise/sunset (currently fixed 06:00-18:00 in `isNightTime()`).
- [ ] **UX9** Speedometer red over the overspeed limit + show the limit.
- [ ] **UX10** Onboarding `BackHandler`; CONFIRM-from-pin/link Back → BROWSE, not empty SEARCH (`:689`).
- [ ] **UX11** Pairing: real RSSI bars (`PairingScreen.kt:428-441`); "Connected to <bike> · Disconnect" when opened from the pill.
- [ ] **UX12** Logs screen: auto-scroll, level/tag filter, stable keys.
- [ ] **UX13** Replay: tap-to-seek only within ~100 m of the track (`RideReplayScreen.kt:221-233`).
- [ ] **UX14** Replay: enforce the documented 1500-vertex cap (`RideTrackMap.kt:53-92`).

---

## Phase 7: New features (post-fix)

- [ ] **F1** Live Updates navigation notification (`Notification.ProgressStyle`, API 36) and consolidation with the Nav SDK's notification (backlog). The promoted-ongoing request/permission are **36.1**: check compileSdk 36.1 support against the AGP 8.13.2 pin (`ENGINEERING_NOTES.md:187-192`) first. 🏍
- [ ] **F2** GPX/KML export & share (needs `<cache-path>` in `file_paths.xml`); GPX import.
- [ ] **F3** Manual "Record ride"; pause/stop from the notification; auto-pause.
- [ ] **F4** Richer ride data (altitude, accuracy, bearing) → elevation gain/profile, moving vs total time, stop count, charts synced to the replay scrubber.
- [ ] **F5** One-tap "Ride home/work" chip + launcher shortcut/widget.
- [ ] **F6** Quick categories (Fuel/Food) and voice search (backlog).
- [ ] **F7** Weather chip on the map (Open-Meteo already integrated).
- [ ] **F8** Mirror home: "last update Xs ago" freshness + hint when Maps' notification disappears.
- [ ] **F9** Trip-finished actions: save destination, reverse route, share GPX.
- [ ] **F10** Ride rename / notes / trim (atomic temp + rename); SAF backup/restore.
- [ ] **F11** Per-leg ETA for multi-stop previews; drag-to-reorder stops.
- [ ] **F12** Non-Latin road names → transliteration or road-ref fallback (backlog).

---

## Tests to extend/add

Existing and to be **extended** (not created): `SerialGattQueueTest`, `RideMetricsTest`, `MapsUrlResolverTest`, `RoutesClientTest`, `TurnIconHeuristicTest`, `NavSubtextParserTest`. NP10 deletes `LegacyModelRemapTest`.

- [ ] **T1** `RideStore` / `PlacesStore` (new): corrupt entry, concurrent writes, prune with bad timestamps, orphan-vs-active.
- [ ] **T2** `RideMetrics` (extend): jitter, spikes, backwards time, gaps, moving average.
- [ ] **T3** `SerialGattQueue` (extend): late completion after timeout must not release the next op.
- [ ] **T4** Nav trip-token logic (extract a pure class): stale result listener / stale NavInfo ignored; `ROUTE_CANCELED` never retried.
- [ ] **T5** Nav stage state machine (extract to ViewModel/state machine): C2, C3, N4, U1, U2.
- [ ] **T6** `TurnBeeper.parseMeters` (new), road-name severity words, `NavSubtextParser` NBSP/thousands/non-Latin, `maneuverTextFor`.
- [ ] **T7** `MapsUrlResolver` (extend): allowlist, foreign redirect, trailing punctuation.
- [ ] **T8** `RoutesClient` (extend): malformed polyline, fractional durations.

## Hardware verification

- [ ] **H1** Back-to-back in-app trips; cancel/replace during route computation (ROUTE_CANCELED); offline route failure; arrival with and without bike.
- [ ] **H2** Roundabouts L/straight/R on both engines; `MERGE_LEFT` → code 14 from the per-step log (backlog).
- [ ] **H3** Ignition-cycle reconnect with CDM presence (incl. classic-linked dash); Nearby-devices revoked; app update mid-day; battery-optimised vs not.
- [ ] **H4** Mirror mode with Maps traffic / location-sharing notifications present (C4); keyless build mirrors (C1).
- [ ] **H5** 1-2 h ride: stats vs odometer, screen-off, process kill + recovery.

---

## Rev. 3 addendum: Phase 8 (foundation) and Phase 9 (features)

Added after smoke test of batches 1-11. Detailed status in TRACKER.md (Phase 8 / Phase 9).

- **A1** Dependency bumps within the AGP 8.13.2 / Gradle 8.13 / Kotlin 2.3.0 toolchain; compileSdk 36.1.
- **A2** Move the nav stage machine (stage, selected place, stops, confirm origin) into a ViewModel with SavedStateHandle; pure reducer unit-tested (T5). Add `configChanges` (orientation|screenSize|smallestScreenSize|screenLayout|density|uiMode|keyboard|keyboardHidden|navigation) so rotation does not recreate the activity/map; ViewModel covers process death.
- **A3** Android 16 review: edge-to-edge, predictive back, large screens.
- **A4** UX4 remainder, R1 (ride list off main thread), NP1 (mmap model).
- **A5** ST9: Keystore AES-GCM secret store replacing security-crypto; legacy file stays as read fallback; verify-after-write; diagnostics line.
- **B1** F3 manual recording (no auto-pause). **B2** F2/F9 GPX export + import-as-ride + trip-finished actions. **B3** F5 home/work chip + shortcuts. **B4** F8 freshness/hint. **B5** F1 Live Updates (own notification through the Nav SDK's NotificationContentProvider hook; in-app engine only). **B6** F6 categories + voice + along-route + N4 Add as stop.
