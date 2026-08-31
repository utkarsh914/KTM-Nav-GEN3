# Implementation Checklist — Navigation Revamp

Companion to [`NAVIGATION_REVAMP_PLAN.md`](NAVIGATION_REVAMP_PLAN.md).
Legend: `[ ]` todo · `[~]` in progress · `[x]` done · `[!]` blocked.

> **Status (current):** the navigation revamp (P1–P6) **and** the D2 navigation-only
> lean-down are **complete** — see [`NAVIGATION_UX_REVAMP.md`](NAVIGATION_UX_REVAMP.md) and
> [`D2_LEAN_DOWN_PLAN.md`](D2_LEAN_DOWN_PLAN.md). The app is navigation-only with two
> mutually-exclusive engines (`NAV_HOME` map / `MIRROR_HOME`). The "do not modify
> `BccuProtocol.kt`" rule below held throughout the revamp; the D2 lean-down then removed the
> **unused** RCM/handlebar declarations from it as a deliberate, reviewed exception (the
> dash-protocol handshake/crypto/TBT/telemetry paths remain untouched).

Global rule: **do not modify** `BccuProtocol.kt`, `BccuCrypto.kt`, or the
BLE/handshake/reconnect/GATT internals of `BccuConnectionService.kt`. **Additive public
methods** on `BccuConnectionService` are permitted (immediate-clear entry point; re-auth
re-send hook) — see plan §4.

Phase order retires risk first: toolchain (Phase 1) and dependency/size (Phase 2) are pure
risk-retirement and can be abandoned cheaply before any architecture work is invested.

---

## Phase 0 — Planning & docs
- [x] Reverse-engineer existing app (architecture, BCCU protocol, nav flow, weaknesses)
- [x] External research (KTM connectivity, Nav SDK vs consumer Maps, Routes API, India, pricing)
- [x] Feasibility conclusions (full-screen TFT; consumer-session access; chosen approach)
- [x] Lock decisions (provider, key handling, packaging, SDK level, flow, size, model, search, map, URL)
- [x] Write detailed plan (`docs/NAVIGATION_REVAMP_PLAN.md`)
- [x] Write this checklist

## Phase 1 — Toolchain migration (highest risk, done first, no nav code)
- [ ] Kotlin 1.9.24 → **2.3.0**; migrate Compose to `org.jetbrains.kotlin.plugin.compose` (drop `composeOptions` compiler-extension route)
- [ ] AGP 8.6.0 → **8.13.2**; Gradle 8.9 → **8.13**
- [ ] Bump Compose BOM to a Kotlin-2.3-compatible version; fix any K2 compile errors
- [ ] `compileSdk`/`targetSdk` 34 → **36** (minSdk 26 already ≥ 24)
- [ ] Enable core-library desugaring (`desugar_jdk_libs_nio:2.1.5`); `multiDexEnabled = true`
- [ ] Confirm build stays **no-minify** (decision #7) and still compiles TFLite + BLE reflection paths
- [ ] Gate: builds + installs on device
- [ ] **On-bike smoke test**: auto-connect from boot, handshake→AUTHENTICATED, notification listener, accessibility gamepad, overlay, FGS starts on a fresh install, guidance still reaches the dash
- [ ] Investigate Android 15/16 behaviour changes vs this app's surface (NotificationListenerService, AccessibilityService, SYSTEM_ALERT_WINDOW, BOOT_COMPLETED→FGS, FGS runtime-permission enforcement)

## Phase 1.5 — Google Cloud setup (prerequisite for Phase 2+)
- [ ] Create or choose a Google Cloud project
- [ ] Enable **Navigation SDK for Android** and **Places API (New)** (add **Geocoding API** only if building the URL-name fallback in §5.9)
- [ ] Attach a **billing account** to the project (required even for free-tier usage)
- [ ] Create an API key; restrict it: Android app (package `com.navigator.app` + release/debug SHA-1), and API restrictions to the enabled APIs above
- [ ] Put the key in `local.properties` as `NAV_SDK_API_KEY=...` (gitignored; never committed, never logged)
- [ ] Note the India free caps for planning (plan §12): Navigation Request 7,000/mo is the only realistically approachable cap

## Phase 2 — Nav SDK dependency + size (SDK present, keyless still builds)
- [ ] Add `com.google.android.libraries.navigation:navigation:7.9.0`
- [ ] `configurations.all { exclude group: "com.google.android.gms", module: "play-services-maps" }`
- [ ] `resConfigs("en")` + `abiFilters("arm64-v8a")` (or ABI splits)
- [ ] `NAV_SDK_API_KEY` from `local.properties` → `BuildConfig`; apply via `NavigationApi.setApiKey()` at runtime
- [ ] Confirm a **keyless** build compiles and runs the notification fallback (nothing to fail on)
- [ ] Measure and record actual APK size (per-ABI); confirm acceptable per decision #7
- [ ] Verify existing foreground service + auto-connect still work with the SDK linked in
- [ ] Document key setup + required API enablement (Navigation SDK, Places API (New), optional Geocoding)

## Phase 3 — Abstraction + encoder + tests (no SDK calls yet)
- [ ] Create package `com.navigator.app.nav`
- [ ] `nav/model/NormalizedNavigationState.kt` (+ `NavSessionState`, `NormalizedManeuver` ~1:1 with SDK, `RoundaboutRotation`, `DrivingSide`, `DistanceUnits`, `NavDestination`, `TravelMode`)
- [ ] `nav/NavigationProvider.kt` + `RoutingNavigationProvider`
- [ ] `nav/ktm/DashWrite.kt` (sealed set, incl. `ClearGuidance(immediate)`)
- [ ] `nav/ktm/DistanceFormatter.kt` (metric/imperial, rounding buckets, <=8 char)
- [ ] `nav/ktm/EtaFormatter.kt` (remainingTimeSeconds → local HH:MM + minute-boundary hysteresis)
- [ ] `nav/ktm/KtmManeuverMapping.kt` (plan §5.3, incl. rotation/exit/driving-side + exit-null fallback + newly-assigned KTM codes)
- [ ] `nav/ktm/KtmNavigationEncoder.kt` (dedup, throttle cooperating with GATT coalescing, state machine, stale/reroute/arrival/cancel)
- [ ] Add JUnit test toolchain to `app/build.gradle.kts` + create `app/src/test/`
- [ ] `KtmManeuverMappingTest`
- [ ] `KtmNavigationEncoderTest` (all scenarios incl. duplicate→no-write, stale→clear, immediate vs debounced)
- [ ] `DistanceFormatterTest`, `EtaFormatterTest` (incl. hysteresis)
- [ ] `NavigationReplayTest` (record → replay → assert DashWrites)
- [ ] All Phase 3 unit tests green (`./gradlew :app:testDebugUnitTest`)

## Phase 4 — Refactor notification path onto the abstraction
- [ ] `nav/providers/NotificationNavProvider.kt` (emits NormalizedNavigationState)
- [ ] Refactor `AppNotificationListener` to feed the provider (keep TFLite/heuristic)
- [ ] Map notification-derived maneuvers → `NormalizedManeuver`
- [ ] Move bitmap render + TFLite inference + disk I/O off the listener's main thread
- [ ] Construct `AppSettings` once (not per notification, `:39`/`:214`)
- [ ] `nav/NavigationCoordinator.kt` (provider → encoder → existing send*/clear*; feeds `TurnBeeper` integer metres)
- [ ] Wire coordinator into `BccuConnectionService`; add re-auth re-send hook + immediate-clear public method
- [ ] Fix clear asymmetry (immediate UI clear vs 4 s dash clear)
- [ ] Migrate `DirectionScreen`/`GridMenuScreen`/`MainActivity` to read normalized state
- [ ] `AppSettings.navProvider` setting added (toggle, decision #10)
- [ ] Verify dash parity with previous behaviour (manual/log check)

## Phase 5 — Destination entry (Search · Map · Link + saved/recents)
- [ ] `nav/destination/DestinationSource.kt` (sealed: Search, MapPin, SharedUrl, SavedWaypoint, Recent)
- [ ] `PlacesAutocompleteClient` (HTTPS, `origin`→`distanceMeters`, `regionCode=in`, session token, 250 ms debounce, cancel-in-flight)
- [ ] `PlaceDetailsClient` (`X-Goog-FieldMask: id,location`, Essentials — session terminator)
- [ ] `MapsUrlResolver` (follow 302; prefer `!3d/!4d` > `?q=`/`?ll=` > `api=1&query=`/`&destination=`; never trust `/@`; optional Geocoding-by-name)
- [ ] `RecentDestinationsStore` in `AppSettings` (+ reuse saved waypoint)
- [ ] `AppRoute.DESTINATION` screen (Search/Map/Link + saved/recents); Nav SDK `MapView` in `AndroidView`, no `mapId`, no Lite Mode, `setOnMapLongClickListener` + `addMarker`
- [ ] `ACTION_SEND` `text/plain` intent filter on `MainActivity` → routes to Destination + MapsUrlResolver
- [ ] Distance labelled straight-line; suppress absent/zero
- [ ] `MapsUrlResolverTest`, `PlacesAutocompleteParserTest` green

## Phase 6 — GoogleNavSdkProvider (primary flow)
- [ ] `NavInfoReceivingService` (bound service + `TurnByTurnManager`, `registerServiceForNavUpdates`)
- [ ] `nav/providers/GoogleNavSdkProvider.kt` (`getNavigator`, start/stop, register feed)
- [ ] Map `NavInfo`/`StepInfo` → `NormalizedNavigationState` (state, maneuver, road, `getDistanceToCurrentStepMeters`, `getDistanceToFinalDestinationMeters`, `getTimeToFinalDestinationSeconds`, roundabout exit/rotation, `getDrivingSide`)
- [ ] Synthesise `ARRIVED` from `Navigator.addArrivalListener()`
- [ ] `GoogleNavManeuverMapTest` (reflection over SDK `Maneuver` @IntDef → completeness)
- [ ] ToS consent flow (`areTermsAccepted` / `showTermsAndConditionsDialog`, Activity) + first-run handling
- [ ] Travel mode `TWO_WHEELER` with `DRIVING` fallback on no-route
- [ ] FGS/notification consolidation via `ForegroundServiceManager` / `NotificationContentProvider` (§5.8)
- [ ] Make Nav SDK the default provider when key + Play Services present (honour Settings toggle)
- [ ] Notification mirroring retained as selectable fallback

## Phase 7 — Reliability pass
- [ ] Reroute handling verified (REROUTING blanks stale turn)
- [ ] Arrival handling (END icon + clear)
- [ ] Cancel/stop handling (immediate vs debounced clear)
- [ ] BLE disconnect/reconnect keeps nav state correct (re-auth re-send, no stale turn)
- [ ] GPS loss / jitter behaviour verified
- [ ] Duplicate/short-distance suppression verified; ETA minute-boundary stable
- [ ] Roundabout RH/LH verified on hardware via SymbolTest/Calibration; mapping locked
- [ ] Two-notification check (consolidation working)
- [ ] Debug `am broadcast` replay hook for on-phone (no-bike) testing
- [ ] Expand `NavigationReplayTest` with real recorded sequences

## Phase 8 — Documentation
- [x] `docs/architecture.md` (end-state architecture + data flow + limitations incl. beta/offline)
- [x] `docs/BCCU_BLE_PROTOCOL.md` (fill the referenced-but-missing protocol doc)
- [x] Build/run instructions incl. `NAV_SDK_API_KEY` + required API enablement (architecture.md §6)
- [~] In-app attribution/licensing text; Play data-safety disclosure — **documented as pending** (architecture.md §7); the actual in-app text + store disclosure are release tasks
- [x] README privacy note (Nav SDK departs from "nothing leaves the phone"; how to stay offline)
- [x] Cost model documented (plan §12; referenced from architecture.md §4)
- [x] Test-without-bike + test-with-dash instructions (architecture.md §6.3/§6.4)
- [x] Update `README.md` / `CHANGELOG.md` as needed

## Phase 9 — Final review
- [ ] Reliability review (state correctness, stale-guard)
- [ ] Android lifecycle / background / battery review (targetSdk 36; optimize-power)
- [ ] Bluetooth stability review (no regressions to protocol layer)
- [ ] Error handling & malformed-input review (incl. MapsUrlResolver, Places JSON)
- [ ] Security / API-key handling review (no key in VCS; not logged; runtime `setApiKey`)
- [ ] Dependency / APK-size note (single-APK Nav SDK, no-minify tradeoff, measured)
- [ ] Billing sanity check (Navigation Request cap; picker map has no `mapId`; simulation billable)
- [ ] Maintainability review (abstraction clean for future RoutesApiProvider)
- [ ] Full test suite green; manual dash smoke test

---

## Notes / open items discovered during implementation
- Nav SDK map load **without** a `mapId` is not a documented billable trigger — treat as likely-free but confirm with a live billing check (plan §10).
- `NavInfo` turn-by-turn feed is a **beta** API — re-verify field sources on every SDK bump; keep the reflection completeness test as the tripwire.
- RH/LH roundabout mapping is reverse-engineered — the hardware check in Phase 7 is authoritative over the theoretical default. **RH/LH DIRECTION verified on hardware by the user: the RH block renders a clockwise roundabout (matches the LHT→clockwise default).**
- **Roundabout EXIT glyph fixed on the SDK path (DONE — Phase 7).** On-bike report: in-app Google nav showed a "sharp right" roundabout glyph that didn't match the actual exit. Cause: `KtmManeuverMapping` mapped Google's **ordinal** exit count (`step.roundaboutTurnNumber`) directly onto the `RAB_SECT_N` index (plan §5.3 assumed `exit n → RAB_SECT_{n}`). But the dash renders a *fixed glyph per code* with no route geometry, so `RAB_SECT_1..16` are **16 fixed exit-angle glyphs**, not ordinals. **Probe result (user, KTM 390 Adv, via Symbol Testing):** RH block, exit arrow sweeps counter-clockwise as N increases — N=1 sharpest right, N=4 = 90° right, N=8 = straight-through, N=12 = left, N=16 = U-turn ⟹ `turnAngle = (8−N)·22.5°`; LH mirrored; RH = clockwise confirmed. **Fix:** `roundaboutSection(turnAngleDeg, clockwise)` = `base + (N−1)`, `N = (clockwise ? 8 − steps : 8 + steps).coerceIn(1,16)`, `steps = round(angle/22.5)`; each `ROUNDABOUT_*` supplies its 45°-bucket angle (SHARP_RIGHT +135 → N2 … LEFT −90 → N12 … SHARP_LEFT −135 → N14). `ROUNDABOUT_UTURN` → plain `UTURN_LEFT/RIGHT` (product decision); `ROUNDABOUT_GENERIC` → `UNDEFINED`. Dropped the `exit` param from `toTurnIcon` and the encoder call; `roundaboutExit` kept-but-unused (logged in `GoogleNavSdkProvider`). The in-app preview (`TurnIconArt`/`TurnIconRef`) already renders sections by angle, so no UI change. Tests rewritten (`KtmManeuverMappingTest` angle-based, RH+LH). Symbol Testing gained a roundabout-probe note + guidance-on. **User to confirm on-bike** through a right/straight/left roundabout.
- **ETA slot is correct (no change).** On-bike the checkered-flag time (e.g. 15:07) looked like the current clock; it is actually the **arrival ETA** — SDK path computes `now + timeToFinalDestination` (`EtaFormatter`), notification path parses Maps' subtext arrival time. It only coincides with "now" on short trips. User confirmed: keep arrival time.
- **16 KB page-size compatibility (DONE — Phase 7).** Surfaced by the targetSdk 36 bump; required for Play targeting Android 15+ since 2025-11-01. Investigated the actual ELF LOAD-segment alignment of every bundled `arm64-v8a` `.so` (`objdump -p`): Nav SDK `libgmm-jni.so` = `2**14` (16 KB, OK); `libandroidx.graphics.path.so` = `2**14` even at graphics-path 1.0.0 (OK — the earlier flag was stale; **no change needed**); **`libtensorflowlite_jni.so` from `org.tensorflow:tensorflow-lite:2.14.0` = `2**12` (4 KB, the only offender).** Fix: swapped `org.tensorflow:tensorflow-lite:2.14.0` → **`com.google.ai.edge.litert:litert:1.4.2`** — a drop-in (same `libtensorflowlite_jni.so`, now `2**14`; same `org.tensorflow.lite.Interpreter` API — `Interpreter(ByteBuffer)` + `run()` both present, confirmed via `javap`), so `ManeuverClassifier` is unchanged. APK-level page-alignment of the uncompressed `.so` is handled by AGP 8.13 defaults (`useLegacyPackaging=false`, no `extractNativeLibs` override, 16 KB zipalign). **User to verify** on a build: all bundled `.so` LOAD align ≥ `0x4000`, and model inference still works on the bike.
- Compose BOM must stay **≤ 2026.06.01 (Compose 1.11.4)** while on AGP 8.13.2 / compileSdk 36 — BOM 2026.08.00 pins Compose 1.12.0 which requires compileSdk 37 + AGP 9.1.0 (fails `checkDebugAarMetadata`).
- Kotlin 2.x toolchain fix applied: `android.kotlinOptions { jvmTarget }` → top-level `kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }` (the old DSL is a hard error under Kotlin 2.3).
- **App rebrand → "KTM Navigator"** (applied). `applicationId` changed `com.navigator.app` → **`com.navigator.ktm`**; app label + user-facing product strings now say "KTM Navigator". **Code namespace kept as `com.navigator.app`** (Google recommends a stable namespace; avoids churn across 49 files). Consequences: installs as a *new* app (existing install won't upgrade in place; pairing/settings re-created once); the **API key must be restricted to `com.navigator.ktm`** + the debug/release SHA-1. FileProvider authority (`com.navigator.app.fileprovider`) and broadcast actions (`com.navigator.app.*`) intentionally left keyed to the namespace, not applicationId — internally consistent, no change needed.
- **Future item: full code-namespace rename** `com.navigator.app` → e.g. `com.navigator.ktm` (package decls + imports across 49 files, FileProvider authority, broadcast action strings, BuildConfig/R). Cosmetic; deferred. Best done via Android Studio's "Rename package" refactor with a clean rebuild. Not required for release.
- **Reconnect fails after ignition-off/on (deferred to final debug pass).** Phase 1 on-bike smoke: initial connect + auth + nav-info on TFT all work. Reconnect shows "reconnecting…" but fails after a while. Suspected pre-existing, but must rule out targetSdk-36 background/BLE-scan/FGS behaviour changes (ties into the "Investigate Android 15/16 behaviour changes" Phase 1 item) — a background-started FGS or BLE scan under Android 15/16 may be throttled/blocked differently. Revisit at the end (Phase 9 Bluetooth stability review) with field logs from `BccuConnectionService` (handshake watchdog, scan callback, reconnect watchdog).
- **Phase 2 APK size (measured):** debug APK **~29 MB** with Nav SDK 7.9.0 linked, `isMinifyEnabled=false`, single ABI (`arm64-v8a`), English-only resources. Bulk is DEX (Nav SDK method count, multidex) since minify is off (decision #7). Native ABIs present: `arm64-v8a` only (ABI filter working). Acceptable per decision #7; revisit only if it becomes a problem.
- **Deprecation warnings in `BccuConnectionService.kt` (DONE — Phase 7).** (1) Removed the deprecated `BluetoothAdapter.getDefaultAdapter()` fallback — the code already used `getSystemService(BluetoothManager).adapter` first, so the fallback was dead on minSdk 26 (now a null-safe `as?`). (2) `writeDescriptor`/`writeCharacteristic`: adopted the API 33+ value-carrying overloads (`writeDescriptor(desc, value)`, `writeCharacteristic(char, value, writeType)`, checked against `BluetoothStatusCodes.SUCCESS` — a compile-time-inlined `0`, so no class-load risk on API <33) behind a `Build.VERSION.SDK_INT >= TIRAMISU` gate, with the deprecated `setValue()`+write path kept as the minSdk-26..32 fallback (scoped `@Suppress("DEPRECATION")`). (3) The two legacy GATT *callback* overrides (`onCharacteristicChanged(g,c)` / `onCharacteristicRead(g,c,status)`) are **kept** — they're the dispatch path on API <33, and already carry `@Suppress("DEPRECATION")`; the value-carrying overloads for API 33+ were already present. No behavioural change on any API level; no protocol-layer change. **User to verify** a clean build (fewer warnings) + on-bike write path still works.
- **Phase 4 passthrough overrides (DECISION TO REVISIT at the very end).** Per the user, the notification path feeds the encoder via optional override fields on `NormalizedNavigationState` (`resolvedIcon`, `preformatted{Distance,Eta,Remaining}`) so it stays byte-for-byte identical to today while gaining dedup/throttle/state-machine. Revisit whether to keep these or fully normalise the notification path to numeric fields once the Nav SDK path exists.
- **Phase 4 deferrals (not done, tracked):**
  - *Off-main-thread notification work* — the checklist called for moving bitmap render + TFLite + disk I/O off the listener's main thread. Deferred: it touches `TurnBeeper` (audio) + threading, is orthogonal to the pipeline cutover, and risks the on-bike parity test. Do it as its own small change after parity is confirmed. (`AppSettings` construction WAS hoisted to a cached lazy field - that part is done.)
  - *UI migration to normalized state* — `DirectionScreen`/`GridMenuScreen`/`MainActivity` still read `NotificationRepository` (the listener keeps updating it), so the in-app UI is unchanged/no-regression. Migrating the UI to read `NormalizedNavigationState` is deferred to a later pass; the coordinator will also need to mirror the Nav SDK path into the repository in Phase 6.
- **Phase 4 done:** `NotificationNavProvider` (session-end debounce replaces the service-side guidance-clear debounce for the notification path), `NavigationCoordinator` + `DashOutput` wired into `BccuConnectionService` (onCreate start, onDestroy stop, `onReAuth()` after auth-time `sendNavigationState`), `AppNotificationListener` NAV branch + removal now feed the provider instead of calling BLE directly, `AppSettings.navProvider` setting added (not yet consumed - Phase 6). The manual test/calibration screens still call `sendGuidanceIfRunning` directly (intentional).
- **Phase 6 done + verified on the bike:** Google Nav SDK drives the KTM dash end-to-end (turn arrow / distance / road / ETA / remaining) via the same encoder pipeline. `GoogleNavSdkProvider` + `NavInfoReceivingService` + `GoogleNavManeuverMap` (all 66 SDK maneuvers, JVM-tested) + `GoogleNavSdkController` (setApiKey → getNavigator → ToS → start; TWO_WHEELER with DRIVING fallback). Coordinator gained `setProvider()` for runtime notification<->SDK switching. Test trigger: Settings → "Navigation (debug)" → GO/STOP (Silk Board Junction, 12.9172/77.6229), debug-only; also an adb broadcast (`com.navigator.ktm.TEST_GOOGLE_NAV`). Needed an explicit `play-services-base` dep (Nav SDK bundles only -basement) for the Play-services availability check.
- **Phase 6 deferrals (tracked):** FGS/notification consolidation NOT done - the Nav SDK shows its own foreground notification during guidance, so there are TWO notifications (ours + SDK's). Confirmed on the bike; consolidate via `NavigationApi.initForegroundServiceManager*` in the reliability pass (§5.8). Also: units hardcoded METRIC in the SDK provider (wire to a setting later); provider selection is manual (the debug button) - the automatic key+PlayServices+`navProvider` selection logic is Phase 5/reliability.
- **Phase 5 (destination entry) done:** 5a search->navigate, 5b map-pin picker, 5c Maps-link resolver (paste + share-sheet; short links resolved via redirect + Places Text Search on the place name, no fragile HTML scraping), 5d navigation-source selection (auto-revert to notification mirroring on ARRIVED/STOPPED + Settings toggle "In-app Google navigation" gating the UI). All verified on device EXCEPT the item below.
- **TO VERIFY LATER (Phase 5d auto-revert, notification side):** after an in-app Google nav ends (ARRIVED/STOPPED), confirm that starting navigation in the **Google Maps app** again mirrors to the dash (i.e. the coordinator reverted to `NotificationNavProvider`). In-app start/stop was verified; the notification-mirroring leg of the revert was not (user doesn't use notification-parsing nav). Fold into the Phase 7 reliability pass.
- (add further findings, deviations, and decisions here as work proceeds)

## UX Revamp — phone-first map experience (see docs/NAVIGATION_UX_REVAMP.md)

- **P1 (map home shell) — code complete, awaiting user build/test.** New `ui/screens/NavigationHomeScreen.kt`: a single persistent Nav SDK `NavigationView` map surface + top search bar (bridges to the existing `DestinationScreen` for now) + settings gear + recenter control + a **Connect/Connected pill** bound to `BccuConnectionService.connectionState`/`deviceName` (tap → Pairing). ToS/navigator warm-up on entry via new `GoogleNavSdkController.prepare(activity, onReady, onError)` (obtains the `Navigator`, which a `NavigationView` needs before it can render); the map only mounts once ready, with graceful placeholders for preparing / error / no-key. Added `OpenDashIcons.Search` + `OpenDashIcons.LocateFixed`. `PairingScreen` gained an optional `onBack` (back chevron) for the Connect-pill entry. Router (`MainActivity`): new `AppRoute.NAV_HOME` is now the start destination + post-pairing landing **when Google nav is available and enabled** (`GoogleNavSdkController.isAvailable && settings.googleNavEnabled`), else legacy `MAIN` grid (non-bricking for keyless/mirror-only builds). Added return-route plumbing (`settingsReturnRoute`/`destinationReturnRoute`/`pairingReturnRoute`) so Settings/Destination/Pairing return to wherever they were opened from; BackHandler + dispatch updated for `NAV_HOME`. The existing grid/notification/direction (dash-mirror) screens remain reachable and untouched. **To verify on device:** map renders inside `NavigationView`, ToS prompt appears once, location dot + recenter work, Connect pill reflects live state and opens Pairing with a working back, search bar opens the destination flow and navigation still starts on the bike.
- **P1 risks flagged to watch during build:** (1) `NavigationView.setNavigationUiEnabled(false)` API name — used to keep the browse map clean (wrapped in `runCatching`, but must compile); (2) Maps SDK billing/enablement on `NAV_SDK_API_KEY` now that a full interactive map renders (vs the old tiny pin-picker).
- **P2 (search sheet + recents/favorites + CONFIRM) — code complete, awaiting user build/test.** `NavigationHomeScreen` is now a `BROWSE/SEARCH/CONFIRM` stage machine over the single persistent `NavigationView` (map stays mounted). **SEARCH:** full panel with a focused text field + live Places autocomplete (reuses `PlacesClient`), plus **Favorites** (Home/Work) and **Recent** sections when the query is empty. **CONFIRM:** drops a red pin + animates the camera, shows a bottom place card (name/address/straight-line distance) with **Get Directions** (adds a recent, then starts navigation via the existing `GoogleNavSdkController` — route preview is P3) and **Save** (Home / Work / Saved). New `nav/destination/PlacesStore.kt` persists recents (dedup by rounded coords, cap 15) + favorites (unique Home/Work slots) as JSON in a private `SharedPreferences` file (no Room/DataStore). Added icons `Bookmark`/`Briefcase`/`Clock`/`Close`. `NavigationHomeScreen` signature changed: dropped `onOpenSearch` (search is now in-screen), added `onStartNavigation`. The old `DestinationScreen` still handles the shared-Maps-link path (fold-in is P5). **To verify on device:** tap search → recents/favorites show; typing gives autocomplete; picking a place shows the pin + card; Get Directions starts nav on the bike + the place appears under Recent next time; Save as Home/Work/Saved persists and shows in the list; back/close return correctly.
- **P2.1 (save-flow rework) — code complete, awaiting user build/test.** Per user feedback the CONFIRM Home/Work/Saved menu was confusing (icons didn't change / no feedback). Reworked: the bookmark beside **Get Directions** is now a **toggle** — outline when unsaved, **brand-accent filled** when saved (orange on KTM, blue on Husqvarna, via `Ktm.Orange` + new `OpenDashIcons.BookmarkFilled`); tap toggles save/unsave against the **Saved (OTHER)** list only (`PlacesStore.isSaved/saveFavorite/removeFavorite`), so Home/Work are never touched here. **Home/Work + saved management moved to Settings:** new **`SavedPlacesScreen`** (route `AppRoute.PLACES`, opened from Settings → Navigation → "Saved places") with Home/Work rows (set/change/remove) and the Saved list (remove + "Add place"), using an inline place picker that reuses `PlacesClient`. `PlacesStore` gained `isSaved()` + `removeFavoriteSlot()`. **To verify:** CONFIRM bookmark fills/empties and persists; Settings → Saved places can set/change/remove Home & Work and add/remove saved; map SEARCH still shows Home/Work/Recent/Saved quick-access.
- **P3 (route preview + START) — code complete, awaiting user build/test.** New `NavStage.PREVIEW`: CONFIRM's **Get Directions** now goes to a preview instead of starting immediately. New `nav/destination/RoutesClient.kt` calls the **Routes API (New)** `directions/v2:computeRoutes` (same REST/auth pattern as `PlacesClient`; TWO_WHEELER with DRIVE fallback; field mask duration/distanceMeters/encodedPolyline) and decodes the polyline. The preview draws the route as a brand-accent polyline on the map, fits the camera to the route+origin bounds, and shows an ETA + distance card with **Start Navigation**. START reuses the existing `GoogleNavSdkController.startNavigation` (records a recent, returns to BROWSE; on-phone active guidance is P4). Back: PREVIEW→CONFIRM→SEARCH→BROWSE. If last-known location is unknown or the route call fails, the card degrades gracefully (Start still works). **Verify on device:** blue/accent route draws with correct ETA+distance; Start begins guidance on the bike. **Also verify the `Routes API` is enabled on the `NAV_SDK_API_KEY` Cloud project** (separate from Places/Maps) — a 403 will just show the graceful fallback.
- **P3 refinements + P4 (on-phone active guidance) — code complete, awaiting user build/test.** From on-device feedback:
  - *Route color:* preview polyline now **Google blue** (`#1A73E8`), not brand orange (`ROUTE_SELECTED`/`ROUTE_ALT` consts).
  - *Alternate routes:* `RoutesClient.computeRoutes` now sends `computeAlternativeRoutes:true` and returns all routes (best-first); preview draws the selected route blue (on top) + alternates **grey and tappable** (`setOnPolylineClickListener` → select), card shows the selected ETA/distance + an "N alternates" hint. START still uses the Nav SDK's own route (documented limitation).
  - *Compass/recenter:* disabled Google's built-in top-left compass (`isCompassEnabled=false`); moved the **recenter** button to the **top-right, stacked below the Settings icon**.
  - *Active navigation (#3/#4):* new `NavStage.NAVIGATING`. On START we enter it and toggle the Nav SDK **`NavigationView.setNavigationUiEnabled(true)`** so Google's built-in guidance UI (maneuver + ETA + remaining distance + speed) renders on the phone. A red **END** button overlays it → `GoogleNavSdkController.stop()` → back to the map. Collects `GoogleNavSdkProvider.state`; auto-returns on ARRIVED. Back is ignored during guidance (use END).
  - Removed the temporary `[Routes]` preview diagnostics (kept the HTTP-error log + the preview-time `lastLocation` re-read).
  - **Verify on device:** blue route + tappable grey alternates; recenter under Settings top-right, no top-left compass; START shows Google guidance UI with live ETA/distance on the phone; END stops guidance and returns to the map; arrival auto-returns.
- **P3/P4 round 2 (feedback) — code complete, awaiting user build/test.**
  - *Compass + recenter:* recenter moved back to **bottom-left**; added a custom **compass** top-right **below Settings**, shown **only when the map is rotated** (`abs(bearing) > 0.5`), needle rotates with bearing, tap → `resetBearing` (bearing/tilt 0). New `OpenDashIcons.Compass`. Bearing tracked via `GoogleMap.setOnCameraMoveListener`.
  - *Start on the selected alternate (route tokens):* `RoutesClient` now sends `routingPreference:TRAFFIC_AWARE` + `routes.routeToken` in the field mask and parses a `routeToken` per route; `NavDestination` gained `routeToken`; `GoogleNavSdkProvider.startNavigation` uses **`Navigator.setDestinations(listOf(waypoint), routeToken)`** when present (starts guidance on the exact picked route), with graceful fallback to default `setDestination` routing. START passes the selected route's token.
  - *Alternate color:* `ROUTE_ALT` → desaturated blue `#7C93B0` (was grey).
  - *Nav screen (Google-like):* keep the SDK's **top maneuver header** + map/camera (`setHeaderEnabled(true)`), **disable the SDK bottom ETA card** (`setEtaCardEnabled(false)`), and render our own **Google-style bottom bar** (`NavBottomBar`): neutral dark **circular X (END)** on the left + centered **"N min · X km · arrival"** from `GoogleNavSdkProvider.state` + `EtaFormatter`. `GoogleMap.setPadding(bottom = 96dp)` while navigating so the SDK's recenter/report float above the bar.
  - **Build-risk calls to confirm compile:** `NavigationView.setHeaderEnabled/​setEtaCardEnabled` and `Navigator.setDestinations(waypoints, routeToken)` — if any signature differs, adjust (token path already falls back at runtime).
  - **Verify on device:** compass appears only when rotated (top-right, below Settings) + resets to north; picking an alternate then Start actually guides that route (bike + phone); alt routes are dull blue; nav screen matches Google (green header, our bottom bar with X END + ETA/dist/arrival, no overlap).
- **P3/P4 round 3 (feedback) — code complete, awaiting user build/test.**
  - *Route token / start-on-selected-alternate (fixed):* first attempt used `setDestinations(waypoints, String)` which doesn't exist — the SDK route-token API is `Navigator.setDestinations(waypoints, CustomRoutesOptions)`. Confirmed via APK dex introspection that `CustomRoutesOptions.Builder` requires `setRouteToken` **and** takes `setTravelMode(CustomRoutesOptions.TravelMode …)`; the builder validates required props, so `build()` without a travel mode threw → silent fallback to the default route (the reported bug). Now builds `CustomRoutesOptions.builder().setRouteToken(token).setTravelMode(CustomRoutesOptions.TravelMode.TWO_WHEELER).build()`. Tokens come from `RoutesClient` (TWO_WHEELER-first), so the mode matches; graceful fallback + `[Nav]` logs retained. (Edge: a rare DRIVE-fallback token would mode-mismatch and fall back to default routing.)
  - *Nav screen reverted to SDK default UI:* removed the custom `NavBottomBar` + `setEtaCardEnabled(false)`/`setHeaderEnabled(true)` overrides + the `GoogleMap.setPadding` hack. `NavigationView` now renders its **full default guidance UI** (top maneuver header + bottom ETA card + re-center + report). The only overlay is a small neutral **circular X (END)** floating **top-left below the header** (`NAV_HEADER_CLEARANCE = 108dp`); hardware **Back** also ends navigation. Arrival auto-return kept.
  - **Confirmed on device (logs):** `[Routes] 3 routes, 3 with token` → `setDestinations(routeToken)` → `[Nav] Route (token) OK`. The `[Dash] !! … not authenticated yet` lines in that test are expected — no bike connected (skip-pairing).
  - **Alternate selection UX (chips):** picking the thin polyline was unreliable, so the preview card now shows a **selectable chip per route** (Fastest / Alt 1 / …) with duration+distance; the chip drives `selectedRoute` (map + token). Polyline tap still works too.
  - **Alternate-route honoring — known SDK limitation (decision: keep as-is, validate on-ride).** Verified via one-shot `first ENROUTE` distance logging: for some destinations the SDK navigates the selected alternate exactly (Phoenix: 9368 m pick → 9368 m), for others it recomputes to the fastest (Koramangala: 15856 m pick → 14863 m). Adding a **fresh-fix origin** (`getCurrentLocation`, replacing the once-remembered stale last-known) did **not** change this, so it isn't an origin mismatch — the Nav SDK road-snaps the *stationary* test position and re-picks the fastest when the alternate diverges early. Route tokens are the only documented lever and are used correctly. Expected to be reliable when actually **riding** along the chosen route. Per user: ship as best-effort, confirm on-bike. Diagnostics removed; fresh-fix origin kept (genuine improvement).
  - Nav-screen SDK-default UI + top-left END + Back-to-end still to eyeball on device.
- **P5 (polish) — done (partial, deferrals noted).** **Share-link fold-in:** a Google-Maps link shared into the app now routes to the map home (`NAV_HOME`) which resolves it via `MapsUrlResolver` and drops into CONFIRM (with an "Opening shared location…" overlay); the old `DestinationScreen` LINK path is no longer used for shares (kept only for the legacy Direction-mirror screen, to be removed with D2). **Deferred:** FGS/notification consolidation → folded into the Phase-7 reliability pass (needs on-bike); extracting a shared `PlaceSearch` composable to de-dup `SavedPlacesScreen`'s picker → cosmetic/internal, skipped to avoid regressions in two working autocomplete screens.
- **P6 (docs) — done.** Reconciled `architecture.md`: rewrote §1 (phone-first; legacy remote/GPX slated for removal), rewrote §3 as the `NavigationHomeScreen` map-home/destination flow (stages, route tokens, shared-link), removed the "⚠ being revised" banner + §3 "superseded" note. Updated `NAVIGATION_UX_REVAMP.md` status + phasing table (P1–P6). This checklist updated.
- **UX revamp remaining:** D2 lean-down (remove ride recording/GPX + handlebar remote/D-pad + non-nav calibration → navigation-only) and §8 planned improvements (categories, voice, weather). On-bike: validate route-token alternate-following + nav-screen look.
