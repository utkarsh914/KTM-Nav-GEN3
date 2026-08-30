# Implementation Checklist — Navigation Revamp

Companion to [`NAVIGATION_REVAMP_PLAN.md`](NAVIGATION_REVAMP_PLAN.md).
Legend: `[ ]` todo · `[~]` in progress · `[x]` done · `[!]` blocked.

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
- [ ] `docs/architecture.md` (end-state architecture + data flow + limitations incl. beta/offline)
- [ ] `docs/BCCU_BLE_PROTOCOL.md` (fill the referenced-but-missing protocol doc)
- [ ] Build/run instructions incl. `NAV_SDK_API_KEY` + required API enablement
- [ ] In-app attribution/licensing text; Play data-safety disclosure
- [ ] README privacy note (Nav SDK departs from "nothing leaves the phone"; how to stay offline)
- [ ] Cost model documented (plan §12)
- [ ] Test-without-bike + test-with-dash instructions
- [ ] Update `README.md` / `CHANGELOG.md` as needed

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
- RH/LH roundabout mapping is reverse-engineered — the hardware check in Phase 7 is authoritative over the theoretical default.
- **16 KB page-size compatibility (deferred, tracked).** Surfaced by the targetSdk 36 bump. Two bundled native libs have LOAD segments not aligned to 16 KB: `libtensorflowlite_jni.so` (from `org.tensorflow:tensorflow-lite:2.14.0`) and `libandroidx.graphics.path.so` (transitive from Compose graphics). Non-fatal on 4 KB devices (app runs); matters for true 16 KB-page devices and for any future Google Play submission (required for Play targeting Android 15+ since 2025-11-01). Not a blocker for sideloaded installs. Fix candidates: bump TFLite to **2.16.1/2.17.0** or migrate to **LiteRT (`com.google.ai.edge.litert`)** — must be validated against `ManeuverClassifier` model loading/inference; and add explicit `androidx.graphics:graphics-path:1.1.0`. **Deferred to Phase 4** (notification/TFLite refactor) so the TFLite change lands with proper testing rather than mid-toolchain-migration. Verify resulting `.so` LOAD alignment ≥ 0x4000 after the bump.
- Compose BOM must stay **≤ 2026.06.01 (Compose 1.11.4)** while on AGP 8.13.2 / compileSdk 36 — BOM 2026.08.00 pins Compose 1.12.0 which requires compileSdk 37 + AGP 9.1.0 (fails `checkDebugAarMetadata`).
- Kotlin 2.x toolchain fix applied: `android.kotlinOptions { jvmTarget }` → top-level `kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }` (the old DSL is a hard error under Kotlin 2.3).
- **App rebrand → "KTM Navigator"** (applied). `applicationId` changed `com.navigator.app` → **`com.navigator.ktm`**; app label + user-facing product strings now say "KTM Navigator". **Code namespace kept as `com.navigator.app`** (Google recommends a stable namespace; avoids churn across 49 files). Consequences: installs as a *new* app (existing install won't upgrade in place; pairing/settings re-created once); the **API key must be restricted to `com.navigator.ktm`** + the debug/release SHA-1. FileProvider authority (`com.navigator.app.fileprovider`) and broadcast actions (`com.navigator.app.*`) intentionally left keyed to the namespace, not applicationId — internally consistent, no change needed.
- **Future item: full code-namespace rename** `com.navigator.app` → e.g. `com.navigator.ktm` (package decls + imports across 49 files, FileProvider authority, broadcast action strings, BuildConfig/R). Cosmetic; deferred. Best done via Android Studio's "Rename package" refactor with a clean rebuild. Not required for release.
- **Reconnect fails after ignition-off/on (deferred to final debug pass).** Phase 1 on-bike smoke: initial connect + auth + nav-info on TFT all work. Reconnect shows "reconnecting…" but fails after a while. Suspected pre-existing, but must rule out targetSdk-36 background/BLE-scan/FGS behaviour changes (ties into the "Investigate Android 15/16 behaviour changes" Phase 1 item) — a background-started FGS or BLE scan under Android 15/16 may be throttled/blocked differently. Revisit at the end (Phase 9 Bluetooth stability review) with field logs from `BccuConnectionService` (handshake watchdog, scan callback, reconnect watchdog).
- **Phase 2 APK size (measured):** debug APK **~29 MB** with Nav SDK 7.9.0 linked, `isMinifyEnabled=false`, single ABI (`arm64-v8a`), English-only resources. Bulk is DEX (Nav SDK method count, multidex) since minify is off (decision #7). Native ABIs present: `arm64-v8a` only (ABI filter working). Acceptable per decision #7; revisit only if it becomes a problem.
- **Deprecation warnings in `BccuConnectionService.kt` (left as-is).** SDK 36 flags `BluetoothAdapter.getDefaultAdapter()` (line ~533, already used only as a fallback after the modern `getSystemService().adapter`) and `OVERRIDE_DEPRECATION` on the `onCharacteristicChanged`/`onCharacteristicRead` GATT callbacks (already `@Suppress("DEPRECATION")`). Warnings only, build green. In the frozen BLE layer — fixing properly means adopting the API 33+ GATT callback signatures (a behavioural change), so deferred to the final Bluetooth-stability pass (Phase 9), potentially with the reconnect debug.
- **Phase 4 passthrough overrides (DECISION TO REVISIT at the very end).** Per the user, the notification path feeds the encoder via optional override fields on `NormalizedNavigationState` (`resolvedIcon`, `preformatted{Distance,Eta,Remaining}`) so it stays byte-for-byte identical to today while gaining dedup/throttle/state-machine. Revisit whether to keep these or fully normalise the notification path to numeric fields once the Nav SDK path exists.
- **Phase 4 deferrals (not done, tracked):**
  - *Off-main-thread notification work* — the checklist called for moving bitmap render + TFLite + disk I/O off the listener's main thread. Deferred: it touches `TurnBeeper` (audio) + threading, is orthogonal to the pipeline cutover, and risks the on-bike parity test. Do it as its own small change after parity is confirmed. (`AppSettings` construction WAS hoisted to a cached lazy field - that part is done.)
  - *UI migration to normalized state* — `DirectionScreen`/`GridMenuScreen`/`MainActivity` still read `NotificationRepository` (the listener keeps updating it), so the in-app UI is unchanged/no-regression. Migrating the UI to read `NormalizedNavigationState` is deferred to a later pass; the coordinator will also need to mirror the Nav SDK path into the repository in Phase 6.
- **Phase 4 done:** `NotificationNavProvider` (session-end debounce replaces the service-side guidance-clear debounce for the notification path), `NavigationCoordinator` + `DashOutput` wired into `BccuConnectionService` (onCreate start, onDestroy stop, `onReAuth()` after auth-time `sendNavigationState`), `AppNotificationListener` NAV branch + removal now feed the provider instead of calling BLE directly, `AppSettings.navProvider` setting added (not yet consumed - Phase 6). The manual test/calibration screens still call `sendGuidanceIfRunning` directly (intentional).
- (add further findings, deviations, and decisions here as work proceeds)
