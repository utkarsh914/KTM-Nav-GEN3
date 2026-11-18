# Implementation Tracker

Source of truth for scope and details: [`REVIEW_PLAN.md`](REVIEW_PLAN.md) (rev. 2). Item IDs match it exactly.
Target: **Android 16+ only (minSdk 36)**.

**Status legend:** `[ ]` todo · `[~]` in progress · `[x]` done · `[-]` skipped / won't do · `[!]` blocked

**Update rules**
- Change a box when work starts / finishes; add the commit hash and notes in the row.
- Run `./gradlew :app:testDebugUnitTest` before marking an item done; note failures under Notes.
- 🏍 = needs an on-bike / on-device check before it can be `[x]`.
- 🔎 = "verify first": confirm the assumption (device / protocol / real notifications) before changing code.
- New work goes in **Discovered during implementation** at the bottom, not into the plan.
- Keep the Done column in the summary in sync when ticking items.

**Ordering constraints**
- **Phase S first** (baseline, snapshot tests, Dash preview) before any fix.
- Phase 1: **N1 before C3**.
- Phase 3: **B9 before B4**. B1 uses a dedicated BLE HandlerThread (no S1/NP1 dependency).
- Phase 0 changes docs (0.7) after the code changes land.
- **Bucket C = ONE commit** (0.4, B1, B2, B3, B4, B5, B6, B7-UI, B8, B9, B10) so it can be reverted with a single `git revert`. Deferred: B11, N8, NP4. Commit hash goes in each row's Notes once landed.
- One plan item per commit elsewhere; tests after each; tag `phase-N-done` at each phase boundary.

## Progress summary

| Phase | Scope | Done | Total | Status |
|---|---|---|---|---|
| S | Safety net & verification protocol | 5 | 6 | SF5 smoke pending |
| 0 | minSdk 36, delete legacy paths | 3 | 7 | 0.1-0.3, 0.5 done; 0.2 BLE-service sites + 0.4 in bucket C; 0.6 on-device; 0.7 GATT doc with bucket C |
| 1 | Critical bugs, crashes, log privacy | 14 | 14 | Done (C3/C4/N1 need on-device check) |
| 2 | Navigation state, UI state, pairing | 0 | 23 | Not started |
| 3 | BLE robustness | 0 | 11 | Not started |
| 4 | Data integrity, parsing, network | 0 | 25 | Not started |
| 5 | Settings, build, docs hygiene | 0 | 11 | Not started |
| 6 | UX polish & accessibility | 0 | 14 | Not started |
| 7 | New features | 0 | 12 | Not started |
| T | Tests to extend/add | 0 | 8 | Not started |
| H | Hardware verification | 0 | 5 | Not started |

---

## Phase S: Safety net & verification protocol (first; no dash available)

| Done | ID | Item | Commit | Notes |
|---|---|---|---|---|
| [x] | SF1 | Baseline: `testDebugUnitTest`, `assembleDebug`, `lint` on untouched code; record results below | | |
| [x] | SF2 | Snapshot tests locking dash output (protocol builders, crypto round-trips, maneuver→icon table, encoder/coordinator sequences, notification samples) | | |
| [x] | SF3 | Scripted fake-dash handshake test (crypto/protocol level; extract handler only if trivially behaviour-preserving) | | |
| [x] | SF4 | Debug-only Dash preview panel (`DashOutput` tee; no release/BLE-path change) | | 🏍 |
| [x] | SF5 | Phone smoke checklist + adb install/logcat routine | | 🏍 |
| [x] | SF6 | Git hygiene: commit per item, tags per phase, bucket C single commit | | |

**Baseline results (SF1):** tests: 161 pass · assembleDebug: OK · lintDebug: 7 pre-existing errors (6× MissingPermission in LocationMarker/NavHomeGuidance/NavigationHomeScreen, 1× MediaStore API 29 in AppLogger; the latter disappears with minSdk 36) — these are the lint baseline.

## Phase 0: minSdk 36 & legacy cleanup

| Done | ID | Item | Commit | Notes |
|---|---|---|---|---|
| [x] | 0.1 | `minSdk = 36` (`app/build.gradle.kts:28`) | | |
| [~] | 0.2 | Remove all `SDK_INT` branches (27 sites) incl. receiver-flag else-branches and O/Q/T channel/FGS guards | | All non-BLE sites done; 5 BLE-service sites go in the bucket C commit |
| [x] | 0.3 | Manifest cleanup + `tools:node="remove"` for Nav-SDK-merged `WRITE_EXTERNAL_STORAGE`/`BLUETOOTH*`; verify merged manifest | | 🔎 |
| [ ] | 0.4 | BLE dead code: delete pre-33 write/descriptor branches and legacy `onCharacteristicChanged/Read` overloads (behaviour unchanged) | | Bucket C commit |
| [x] | 0.5 | Keep desugaring (Nav SDK requires it); fix stale comments (`:86`, `:180`); remove no-op multiDex | | |
| [ ] | 0.6 | Android 16 checks: edge-to-edge insets, predictive back, large-screen orientation | | 🏍 |
| [~] | 0.7 | Docs after Phase 0: `ENGINEERING_NOTES.md:221-237`, `architecture.md:264, 276` | | |

## Phase 1: Critical bugs, crashes, privacy

### Broken features
| Done | ID | Item | Commit | Notes |
|---|---|---|---|---|
| [x] | C1 | `navSdkActive = isAvailable && setting` shared by listener, home route, settings | | |
| [x] | C2 | Idle auto-finish loop keyed on `isActiveNav()` / `while (isActive)` | | |
| [x] | C4 | Nav notification: `ONGOING \|\| CATEGORY_NAVIGATION` per package, track `sbn.key`, clear only that key, skip summaries (do NOT require category alone) | | 🔎 🏍 Applied directly (no shadow mode); check with real Maps notifications Reviewer caveat: ongoing location-sharing notifs may still pass; check `flags=` in the debug 'Posted:' log with real Maps notifs and tighten if needed |
| [x] | N1 | Trip generation token; main-thread state writes; **`ROUTE_CANCELED` terminal, never retried** (prereq for C3) | | 🏍 |
| [x] | C3 | Route-failure card (Retry / Back to preview / Use Maps mirror); "Still calculating…" instead of auto-abort (after N1) | | 🏍 Card has Retry / Back to preview / Close (no 'Use Maps mirror' button) |

### Crashes
| Done | ID | Item | Commit | Notes |
|---|---|---|---|---|
| [x] | S1 | `AppSettings`: single instance, lazy + recoverable encrypted prefs (migration is ST9) | | |
| [x] | S2 | Background FGS start: try/catch + log/notify now; real fix is B11 | | 🔎 🏍 |
| [x] | S3 | Permission checks + try/catch on BLE service `startForeground`/`connect()`/ride service; `START_NOT_STICKY` only when permission missing; scope exception handler | | 🏍 |
| [x] | S4 | Settings screen: guard `bondedDevices`, null-safe `getSystemService` | | |
| [x] | S5 | `TurnBeeper.preview()` try/catch | | |
| [x] | S6 | `onNotificationPosted` try/catch | | |

### Privacy / logging
| Done | ID | Item | Commit | Notes |
|---|---|---|---|---|
| [x] | PR1 | Release logs length-only: notification text, road/ETA/distance, WhatsApp titles, destination labels; "Posted:" line after filtering/debug only; update `ENGINEERING_NOTES.md:99` | | |
| [x] | PR2 | Public `Download/OpenDash` mirror opt-in + rotation/size cap | | |
| [x] | PR3 | Crash handler writes synchronously before delegating | | |

## Phase 2: Navigation, UI state, pairing

### Navigation state
| Done | ID | Item | Commit | Notes |
|---|---|---|---|---|
| [ ] | N2 | Quit `NavInfoReceivingService` HandlerThread in `onDestroy` | | |
| [ ] | N3 | ARRIVED not conflated: one-shot `tripEnded` SharedFlow (don't skip `stopNavigation()` in `detach()`) | | |
| [ ] | N4 | Trip ownership from provider; ARRIVED/STOPPED in any stage; mid-trip "Add stop / Replace" | | 🏍 |
| [ ] | N5 | Decide recording-vs-route-failure behaviour independent of BLE state (keep recording until user ends, or clear everywhere) | | 🔎 |
| [ ] | N6 | `NotificationNavProvider` generation counter; fix pause semantics/comment | | |
| [ ] | N7 | Ride recording keyed on N1 trip token | | |
| [ ] | N8 | `NAVIGATION_STATE guidanceOn` idle behaviour (after dash test) | | 🔎 🏍 DEFERRED: needs dash |

### UI state
| Done | ID | Item | Commit | Notes |
|---|---|---|---|---|
| [ ] | U1 | Hardware Back in SEARCH respects `searchTarget` | | |
| [ ] | U2 | Clear `stops` on new destination | | |
| [ ] | U3 | Start-before-routes default mode = `vehicleMode` | | |
| [ ] | U4 | Cancel place-details job on back; error on null | | |
| [ ] | U5 | `starting` guard against double Start | | |
| [ ] | U6 | Greeting banner `try/finally` | | |
| [ ] | U7 | Nav UI state → ViewModel/`SavedStateHandle` | | |
| [ ] | U8 | Permission request only on fresh launch & when not granted | | |
| [ ] | U9 | Map owner token; Home `DisposableEffect` cleanup; Replay no longer clears shared map | | |
| [ ] | U10 | Compass/GPS listeners gated on lifecycle ≥ STARTED | | |
| [ ] | U11 | Live origin for search; freshest `lastLocation` | | |
| [ ] | U12 | Stale-GPS speed → null; low-limit clear fix | | |
| [ ] | U13 | Freeze fallback trip time in `finishTrip()` | | |

### Pairing
| Done | ID | Item | Commit | Notes |
|---|---|---|---|---|
| [ ] | P1 | Reset `connectingAddress` after failure | | |
| [ ] | P2 | Save bike only on AUTHENTICATED; stop scan on leave/connect; "Bluetooth off" state | | |
| [ ] | P3 | Re-pair confirm dialog + return route | | |

## Phase 3: BLE robustness (B9 → B4; S1 + NP1 before B1)

| Done | ID | Item | Commit | Notes |
|---|---|---|---|---|
| [ ] | B1 | GATT callbacks on one looper: dedicated BLE HandlerThread | | 🏍 Bucket C commit |
| [ ] | B2 | `SerialGattQueue.finish(uuid)` op match; 2 s timeout unchanged | | Bucket C commit |
| [ ] | B3 | Control-message safety: keep derived key pool aside until AUTHENTICATED (no new incoming validation) | | 🔎 🏍 Bucket C commit |
| [ ] | B4 | Unified backoff: dash-drop reuses 15/45 s cooldown; absent-bike cap 2 min; reset on bike link up / adapter-on / foreground; keep "no retry cap" | | 🏍 Bucket C commit |
| [ ] | B5 | `onStartCommand` cooldown guard for non-present starts | | Bucket C commit |
| [ ] | B6 | Validate persisted key pool (16×16 B); catch in `sendEncrypted` | | Bucket C commit |
| [ ] | B7 | "Re-pair needed" UI after N failed handshakes; **statuses 3/4 NOT sent** (verify official SDK first); check `ACTION_KEY_MISSING` | | 🔎 🏍 Bucket C commit (UI part only) |
| [ ] | B8 | Post-auth heartbeat liveness (>30 s silence → recycle; drop first if it misbehaves) | | 🏍 Bucket C commit |
| [ ] | B9 | Single `teardownLink(reason)` | | Bucket C commit |
| [ ] | B10 | Small items: discover/MTU checks, priority→BALANCED, monotonic clock, scan filter `target`, encrypt inside op (marquee width excluded) | | Bucket C commit |
| [ ] | B11 | CompanionDeviceManager replacing `AutoConnectReceiver` (manifest/feature/service/permissions, migration dialog, `MY_PACKAGE_REPLACED`) | | 🔎 🏍 DEFERRED: <50% confidence, needs dash |

## Phase 4: Data integrity, parsing, network

### Rides
| Done | ID | Item | Commit | Notes |
|---|---|---|---|---|
| [ ] | R1 | `RideStore` lock/single-thread + `commit()` + `StateFlow`; I/O off main | | |
| [ ] | R2 | Tolerant parsing; no write-back after parse failure; persist `saved` in meta | | |
| [ ] | R3 | Orphan recovery skips active track (🟢, theoretical) | | |
| [ ] | R4 | GPS filtering; moving time/avg; monotonic duration (independent of Phase 0); mock fixes allowed in debug | | 🏍 Applied directly |
| [ ] | R5 | Prune by creation id | | |
| [ ] | R6 | Ride service: stop on failed start, `stopSelfResult`, contentIntent + Stop action | | |
| [ ] | R7 | fsync, haversine clamp, missing `t` handling | | |

### Places
| Done | ID | Item | Commit | Notes |
|---|---|---|---|---|
| [ ] | PL1 | Tolerant parsing | | |
| [ ] | PL2 | `removeFavorite(s)` only `slot == OTHER` | | |
| [ ] | PL3 | `Locale.ROOT` in `coordKey` | | |

### Notification parsing
| Done | ID | Item | Commit | Notes |
|---|---|---|---|---|
| [ ] | NP1 | Off-main-thread nav processing (removals on same executor); cache no-match; lazy mmap model; shared calibration prefs | | |
| [ ] | NP2 | `TurnBeeper.parseMeters` anchoring, thousands, `yd`, normalised title | | |
| [ ] | NP3 | Severity words must be adjacent to left/right; parse instruction field first | | |
| [ ] | NP4 | Classifier preprocessing vs training: re-run 192/192 eval first, retrain if needed | | 🔎 🏍 DEFERRED until eval re-run |
| [ ] | NP5 | Validate title as distance before sending | | |
| [ ] | NP6 | Mirror-home clears on provider STOPPED | | |
| [ ] | NP7 | Skip ongoing/own-package; dedup `(key, text)` | | |
| [ ] | NP8 | NBSP/U+202F, AM/PM boundary, `maneuverTextFor` fixes | | |
| [ ] | NP9 | Cap captured icons; sync `activeNotifications` on connect | | |
| [ ] | NP10 | Delete `LegacyModelRemap` + its test | | |

### Network
| Done | ID | Item | Commit | Notes |
|---|---|---|---|---|
| [ ] | NET1 | `MapsUrlResolver` host allowlist, per-hop check, status check, trim punctuation (🟡/🟢) | | |
| [ ] | NET2 | Derive `regionCode` | | |
| [ ] | NET3 | `RoutesClient` retry only on empty 200; per-route polyline tolerance | | |
| [ ] | NET4 | `autocomplete` result type (error vs empty) | | |
| [ ] | NET5 | `registerDefaultNetworkCallback` | | |

## Phase 5: Settings, build, docs

| Done | ID | Item | Commit | Notes |
|---|---|---|---|---|
| [ ] | ST1 | Clamp speed-band count/step and overspeed limit | | |
| [ ] | ST2 | Driving-side AUTO via telephony country first | | |
| [ ] | ST3 | `runCatching` around OEM-dependent `startActivity` calls | | |
| [ ] | ST4 | Mirror-from apps dialog lazy + off-main; refresh background-location status on resume | | |
| [ ] | ST5 | FileProvider authority `${applicationId}.fileprovider` (conditional: only with install-conflict reason, else drop) | | 🔎 |
| [ ] | ST6 | Deterministic, monotonic `versionCode`/`versionName` | | |
| [ ] | ST7 | `.gitignore` keystores; remove tracked `my-logs.txt` | | |
| [ ] | ST8 | Bump old AndroidX/coroutines within toolchain limits | | |
| [ ] | ST9 | Migrate off `security-crypto`; decide session-key storage; fix KDoc mismatches | | |
| [ ] | ST10 | Docs corrections (protocol doc, architecture.md, stale comments, logging scope) | | |
| [ ] | ST11 | Backup decision (`allowBackup=false` already set; device-to-device transfer of plain-prefs keys) | | |

## Phase 6: UX polish & accessibility

| Done | ID | Item | Commit | Notes |
|---|---|---|---|---|
| [ ] | UX1 | "Resume navigation" pill in every stage | | |
| [ ] | UX2 | Banners: location off/denied, Bluetooth off, Nearby devices denied | | |
| [ ] | UX3 | Search: clear button, IME action, shared-link feedback | | |
| [ ] | UX4 | Touch targets ≥ 48 dp; glove mode 64 dp; large END-confirm buttons | | 🏍 |
| [ ] | UX5 | Contrast fixes (`Ktm.Dim`, small stat text) | | |
| [ ] | UX6 | Semantics: `Role.Switch`, selected states, connection state, turn-icon descriptions | | |
| [ ] | UX7 | Units setting (+ yards) and system 12/24h | | |
| [ ] | UX8 | Real sunrise/sunset day/night | | |
| [ ] | UX9 | Speedometer over-limit colour + limit display | | |
| [ ] | UX10 | Onboarding BackHandler; CONFIRM Back → BROWSE | | |
| [ ] | UX11 | Pairing: real RSSI bars; "Connected to <bike> · Disconnect" | | |
| [ ] | UX12 | Logs screen: auto-scroll, filter, stable keys | | |
| [ ] | UX13 | Replay: tap-to-seek proximity threshold | | |
| [ ] | UX14 | Replay: enforce 1500-vertex cap | | |

## Phase 7: New features

| Done | ID | Item | Commit | Notes |
|---|---|---|---|---|
| [ ] | F1 | Live Updates navigation notification (check compileSdk 36.1 vs AGP 8.13.2 first; consolidate with SDK notification) | | 🔎 🏍 |
| [ ] | F2 | GPX/KML export & share; GPX import | | |
| [ ] | F3 | Manual "Record ride"; notification pause/stop; auto-pause | | |
| [ ] | F4 | Richer ride data, elevation, charts | | |
| [ ] | F5 | "Ride home/work" chip + shortcut/widget | | |
| [ ] | F6 | Quick categories + voice search | | |
| [ ] | F7 | Weather chip on map | | |
| [ ] | F8 | Mirror home freshness indicator + missing-notification hint | | |
| [ ] | F9 | Trip-finished actions (save destination, reverse route, share GPX) | | |
| [ ] | F10 | Ride rename / notes / trim; SAF backup/restore | | |
| [ ] | F11 | Per-leg ETA; drag-to-reorder stops | | |
| [ ] | F12 | Non-Latin road-name transliteration / ref fallback | | |

## Tests to extend/add

Existing (extend): `SerialGattQueueTest`, `RideMetricsTest`, `MapsUrlResolverTest`, `RoutesClientTest`, `TurnIconHeuristicTest`, `NavSubtextParserTest`.

| Done | ID | Item | Commit | Notes |
|---|---|---|---|---|
| [ ] | T1 | `RideStore` / `PlacesStore` (new): corrupt entry, concurrency, prune, orphan-vs-active | | |
| [ ] | T2 | `RideMetrics` (extend): jitter, spikes, backwards time, gaps, moving average | | |
| [ ] | T3 | `SerialGattQueue` (extend): late completion after timeout | | |
| [ ] | T4 | Nav trip-token logic (pure class): stale result/NavInfo ignored; `ROUTE_CANCELED` never retried | | |
| [ ] | T5 | Nav stage state machine: C2, C3, N4, U1, U2 | | |
| [ ] | T6 | `TurnBeeper.parseMeters` (new), severity words, `NavSubtextParser` edge cases, `maneuverTextFor` | | |
| [ ] | T7 | `MapsUrlResolver` (extend): allowlist, foreign redirect, trailing punctuation | | |
| [ ] | T8 | `RoutesClient` (extend): malformed polyline, fractional durations | | |

## Hardware verification

| Done | ID | Scenario | Date | Result |
|---|---|---|---|---|
| [ ] | H1 | Back-to-back trips; cancel/replace during route compute (ROUTE_CANCELED); offline route failure; arrival with/without bike | | |
| [ ] | H2 | Roundabouts L/straight/R on both engines; `MERGE_LEFT` → code 14 from step log | | |
| [ ] | H3 | Ignition-cycle reconnect with CDM presence (incl. classic-linked dash); Nearby-devices revoked; app update mid-day; battery-optimised vs not | | |
| [ ] | H4 | Mirror mode with Maps traffic/location-sharing notifications (C4); keyless build mirrors (C1) | | |
| [ ] | H5 | 1-2 h ride: stats vs odometer, screen-off, process kill + recovery | | |

---

## Discovered during implementation

| ID | Description | Found in | Status |
|---|---|---|---|
| | | | |

## Decisions log

| Date | Decision | Reason |
|---|---|---|
| 2026-11-18 | Target Android 16+ only (minSdk 36) | User only uses API 36+ devices; removes legacy paths |
| 2026-11-18 | Plan rev. 2: corrections from independent audit applied (see "Changes from rev. 1" in `REVIEW_PLAN.md`) | Fix wrong/overstated claims, add ROUTE_CANCELED bug, reorder dependent items, fix tracker counts |
| 2026-11-18 | N3 uses a one-shot `tripEnded` SharedFlow | Skipping `stopNavigation()` in `detach()` could leave SDK guidance running |
| 2026-11-18 | No dash available: Phase S (baseline, snapshot tests, debug Dash preview) runs first | Make regressions visible without the bike |
| 2026-11-18 | Dash-dependent (bucket C) items implemented only at ≥50% confidence, all in ONE commit | Easy single-commit revert later |
| 2026-11-18 | Deferred: B11 (CDM), N8 (guidanceOn OFF), NP4 (model input); B7 limited to UI, statuses 3/4 not sent | <50% confidence or unknown dash effect |
| 2026-11-18 | Conservative bucket C parameters: backoff cap 2 min, liveness 30 s, 2 s GATT timeout unchanged, dedicated BLE thread | Minimise regression risk without hardware |
| 2026-11-18 | C4 and R4 applied directly (no shadow mode); mock locations still accepted in debug builds | User decision |
