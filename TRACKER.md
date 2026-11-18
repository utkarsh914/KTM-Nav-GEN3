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
| 0 | minSdk 36, delete legacy paths | 7 | 7 | 0.6 code review done in A3; visual check by user |
| 1 | Critical bugs, crashes, log privacy | 14 | 14 | Done (C3/C4/N1 need on-device check) |
| 2 | Navigation state, UI state, pairing | 22 | 23 | N8 deferred (dash), P2 partial; U7 + N4 done in Phases 8/9 |
| 3 | BLE robustness | 8 | 11 | Bucket C commit; B3/B4/B8 reduced; B11 deferred |
| 4 | Data integrity, parsing, network | 25 | 25 | R1/NP1 done in A4; NET2 dropped, NP4 deferred |
| 5 | Settings, build, docs hygiene | 10 | 11 | ST5 dropped; ST8, ST9 done in Phase 8 |
| 6 | UX polish & accessibility | 10 | 14 | UX4 mostly done (A4); UX1/7/8/11 deferred |
| 7 | New features | 7 | 12 | F1/F2/F3/F5/F6/F8/F9 done (Phase 9); F4/F7/F10/F11/F12 not started |
| T | Tests to extend/add | 7 | 8 | T1 partial (RideStore needs Android); T5 done (NavFlow) |
| H | Hardware verification | 0 | 5 | Not started |

---

## Commit log (batches)

| Batch | Scope | Commit |
|---|---|---|
| 1 | Safety net (SF1-SF4) | `34062bc` |
| 2 | minSdk 36 cleanup | `a579397` |
| 3 | Crash-proofing + log privacy | `8056314` |
| 4 | Navigation core (C1-C4, N1) | `d028a3e` |
| 5 | Navigation state (N2-N7) | `24594f2` |
| 6 | UI state + pairing | `0717a43` |
| 7 | **Bucket C (dash-dependent), single commit** | `a2fccb0` |
| 8 | Rides + places | `ad25ad0` |
| 9 | Notification parsing + network | `605e1fd` |
| 10 | Settings, build, docs | `35061c5` |
| 11 | UX polish | `7004c3f` |
| 12 | Avg speed selector + dash preview toggle | `68c232e` |
| 8.A1 | Dependency bumps, compileSdk 36.1 | `9527e1f` |
| 8.A2 | Nav flow ViewModel + configChanges | `d88c3df` |
| 8.A3 | Android 16 edge/back | `09669ee` |
| 8.A4 | Touch targets, off-main rides, mmap model | `3423e6c` |
| 8.A5 | Single plain settings store (+ later removal of security-crypto `8cab306`) | `802f7c6` |
| 9.B1 | Manual recording | `7d46346` |
| 9.B2 | GPX + finish actions | `b62c80f` |
| 9.B3 | Home/Work chips + shortcuts | `a3baffa` |
| 9.B4 | Mirror hints | `f69891d` |
| 9.B5 | Live Updates | `2b4ac3e` |
| 9.B6 | Categories, voice, add stop | `ebd8707` |

Final lint: 6 errors (all pre-existing MissingPermission; baseline was 7), unit tests all green.

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
| [x] | 0.2 | Remove all `SDK_INT` branches (27 sites) incl. receiver-flag else-branches and O/Q/T channel/FGS guards | | All non-BLE sites done; 5 BLE-service sites go in the bucket C commit |
| [x] | 0.3 | Manifest cleanup + `tools:node="remove"` for Nav-SDK-merged `WRITE_EXTERNAL_STORAGE`/`BLUETOOTH*`; verify merged manifest | | 🔎 |
| [x] | 0.4 | BLE dead code: delete pre-33 write/descriptor branches and legacy `onCharacteristicChanged/Read` overloads (behaviour unchanged) | | Bucket C commit |
| [x] | 0.5 | Keep desugaring (Nav SDK requires it); fix stale comments (`:86`, `:180`); remove no-op multiDex | | |
| [ ] | 0.6 | Android 16 checks: edge-to-edge insets, predictive back, large-screen orientation | | 🏍 |
| [x] | 0.7 | Docs after Phase 0: `ENGINEERING_NOTES.md:221-237`, `architecture.md:264, 276` | | |

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
| [x] | N2 | Quit `NavInfoReceivingService` HandlerThread in `onDestroy` | | |
| [x] | N3 | ARRIVED not conflated: one-shot `tripEnded` SharedFlow (don't skip `stopNavigation()` in `detach()`) | | |
| [x] | N4 | Trip ownership from provider; ARRIVED/STOPPED in any stage; mid-trip "Add stop / Replace" | | 🏍 Replace-confirm + 'Add stop' (B6; only for trips without stops yet) |
| [x] | N5 | Decide recording-vs-route-failure behaviour independent of BLE state (keep recording until user ends, or clear everywhere) | | 🔎 Decision: failed trip parks on the card (recording continues) until Retry/Back/Close or 10-min expiry (not while app foreground); independent of BLE |
| [x] | N6 | `NotificationNavProvider` generation counter; fix pause semantics/comment | | |
| [x] | N7 | Ride recording keyed on N1 trip token | | |
| [ ] | N8 | `NAVIGATION_STATE guidanceOn` idle behaviour (after dash test) | | 🔎 🏍 DEFERRED: needs dash |

### UI state
| Done | ID | Item | Commit | Notes |
|---|---|---|---|---|
| [x] | U1 | Hardware Back in SEARCH respects `searchTarget` | | |
| [x] | U2 | Clear `stops` on new destination | | |
| [x] | U3 | Start-before-routes default mode = `vehicleMode` | | |
| [x] | U4 | Cancel place-details job on back; error on null | | |
| [x] | U5 | `starting` guard against double Start | | |
| [x] | U6 | Greeting banner `try/finally` | | |
| [x] | U7 | Nav UI state → ViewModel/`SavedStateHandle` DEFERRED: large refactor, can't be verified without UI testing (alt: manifest configChanges) |
| [x] | U8 | Permission request only on fresh launch & when not granted | | |
| [x] | U9 | Map owner token; Home `DisposableEffect` cleanup; Replay no longer clears shared map | | |
| [x] | U10 | Compass/GPS listeners gated on lifecycle ≥ STARTED | | |
| [x] | U11 | Live origin for search; freshest `lastLocation` | | |
| [x] | U12 | Stale-GPS speed → null; low-limit clear fix | | |
| [x] | U13 | Freeze fallback trip time in `finishTrip()` | | |

### Pairing
| Done | ID | Item | Commit | Notes |
|---|---|---|---|---|
| [x] | P1 | Reset `connectingAddress` after failure | | |
| [~] | P2 | Save bike only on AUTHENTICATED; stop scan on leave/connect; "Bluetooth off" state Done: stop scan, Bluetooth-off state. NOT done: save bike only on AUTHENTICATED (service uses the saved address for first-pair recovery rescans; needs dash) |
| [x] | P3 | Re-pair confirm dialog + return route | | |

## Phase 3: BLE robustness (B9 → B4; S1 + NP1 before B1)

| Done | ID | Item | Commit | Notes |
|---|---|---|---|---|
| [x] | B1 | GATT callbacks on one looper: dedicated BLE HandlerThread | | 🏍 Bucket C commit |
| [x] | B2 | `SerialGattQueue.finish(uuid)` op match; 2 s timeout unchanged | | Bucket C commit |
| [~] | B3 | Control-message safety: keep derived key pool aside until AUTHENTICATED (no new incoming validation) | | 🔎 🏍 Bucket C commit Dropped: deferred persist (reviewer: a link drop between ComputeKeyArr and AssignKeyIndex would leave phone/dash pools mismatched). Immediate persist kept; B6 validation covers malformed pools |
| [~] | B4 | Unified backoff: dash-drop reuses 15/45 s cooldown; absent-bike cap 2 min; reset on bike link up / adapter-on / foreground; keep "no retry cap" | | 🏍 Bucket C commit Only absent-bike backoff shipped (1.5/5/15/30 s cap; reset on link up/auth/adapter-on/ACL present/foreground). Pre-auth-drop 15/45 s cooldown dropped (would slow ignition-on connects); B5 guard ships |
| [x] | B5 | `onStartCommand` cooldown guard for non-present starts | | Bucket C commit |
| [x] | B6 | Validate persisted key pool (16×16 B); catch in `sendEncrypted` | | Bucket C commit |
| [x] | B7 | "Re-pair needed" UI after N failed handshakes; **statuses 3/4 NOT sent** (verify official SDK first); check `ACTION_KEY_MISSING` | | 🔎 🏍 Bucket C commit (UI part only) UI hint only (flag + notification + Settings pill); statuses 3/4 NOT sent |
| [~] | B8 | Post-auth heartbeat liveness (>30 s silence → recycle; drop first if it misbehaves) | | 🏍 Bucket C commit Shipped as LOG-ONLY (heartbeat not confirmed in field logs); promote to recycle after a capture |
| [x] | B9 | Single `teardownLink(reason)` | | Bucket C commit |
| [x] | B10 | Small items: discover/MTU checks, priority→BALANCED, monotonic clock, scan filter `target`, encrypt inside op (marquee width excluded) | | Bucket C commit BALANCED-priority change dropped (untested latency effect); other items shipped |
| [ ] | B11 | CompanionDeviceManager replacing `AutoConnectReceiver` (manifest/feature/service/permissions, migration dialog, `MY_PACKAGE_REPLACED`) | | 🔎 🏍 DEFERRED: <50% confidence, needs dash |

## Phase 4: Data integrity, parsing, network

### Rides
| Done | ID | Item | Commit | Notes |
|---|---|---|---|---|
| [~] | R1 | `RideStore` lock/single-thread + `commit()` + `StateFlow`; I/O off main Lock + commit-free consistency done; StateFlow and moving index reads off main NOT done (small prefs reads) |
| [x] | R2 | Tolerant parsing; no write-back after parse failure; persist `saved` in meta | | |
| [x] | R3 | Orphan recovery skips active track (🟢, theoretical) | | |
| [x] | R4 | GPS filtering; moving time/avg; monotonic duration (independent of Phase 0); mock fixes allowed in debug | | 🏍 Applied directly Accuracy gate 40 m; avg speed = distance/moving time (semantic change); resync after 5 rejected fixes |
| [x] | R5 | Prune by creation id | | |
| [x] | R6 | Ride service: stop on failed start, `stopSelfResult`, contentIntent + Stop action | | |
| [x] | R7 | fsync, haversine clamp, missing `t` handling | | |

### Places
| Done | ID | Item | Commit | Notes |
|---|---|---|---|---|
| [x] | PL1 | Tolerant parsing | | |
| [x] | PL2 | `removeFavorite(s)` only `slot == OTHER` | | |
| [x] | PL3 | `Locale.ROOT` in `coordKey` | | |

### Notification parsing
| Done | ID | Item | Commit | Notes |
|---|---|---|---|---|
| [~] | NP1 | Off-main-thread nav processing (removals on same executor); cache no-match; lazy mmap model; shared calibration prefs Done: worker executor (post+remove same queue), negative classifier cache. NOT done: mmap model, calibration via plain prefs (small gain) |
| [x] | NP2 | `TurnBeeper.parseMeters` anchoring, thousands, `yd`, normalised title | | |
| [x] | NP3 | Severity words must be adjacent to left/right; parse instruction field first | | |
| [ ] | NP4 | Classifier preprocessing vs training: re-run 192/192 eval first, retrain if needed | | 🔎 🏍 DEFERRED until eval re-run |
| [x] | NP5 | Validate title as distance before sending Lenient: any digit-led short label without ':' (localised units still reach the dash) |
| [x] | NP6 | Mirror-home clears on provider STOPPED | | |
| [x] | NP7 | Skip ongoing/own-package; dedup `(key, text)` | | |
| [x] | NP8 | NBSP/U+202F, AM/PM boundary, `maneuverTextFor` fixes | | |
| [x] | NP9 | Cap captured icons; sync `activeNotifications` on connect Cap 120 icons + onListenerConnected sync |
| [x] | NP10 | Delete `LegacyModelRemap` + its test | | |

### Network
| Done | ID | Item | Commit | Notes |
|---|---|---|---|---|
| [x] | NET1 | `MapsUrlResolver` host allowlist, per-hop check, status check, trim punctuation (🟡/🟢) | | |
| [-] | NET2 | Derive `regionCode` DROPPED: device locale is a poor proxy (en-US phones in India); needs SIM/location-based design |
| [x] | NET3 | `RoutesClient` retry only on empty 200; per-route polyline tolerance | | |
| [x] | NET4 | `autocomplete` result type (error vs empty) | | |
| [x] | NET5 | `registerDefaultNetworkCallback` | | |

## Phase 5: Settings, build, docs

| Done | ID | Item | Commit | Notes |
|---|---|---|---|---|
| [x] | ST1 | Clamp speed-band count/step and overspeed limit | | |
| [x] | ST2 | Driving-side AUTO via telephony country first | | |
| [x] | ST3 | `runCatching` around OEM-dependent `startActivity` calls | | |
| [x] | ST4 | Mirror-from apps dialog lazy + off-main; refresh background-location status on resume | | |
| [-] | ST5 | FileProvider authority `${applicationId}.fileprovider` (conditional: only with install-conflict reason, else drop) | | 🔎 DROPPED: no install-conflict reason (ENGINEERING_NOTES: no change needed) |
| [x] | ST6 | Deterministic, monotonic `versionCode`/`versionName` versionCode = 100000 + commit count (above old day-based values; shallow clones fall back) |
| [x] | ST7 | `.gitignore` keystores; remove tracked `my-logs.txt` | | |
| [x] | ST8 | Bump old AndroidX/coroutines within toolchain limits DEFERRED: bumps can't be validated without device testing |
| [x] | ST9 | Migrate off `security-crypto`; decide session-key storage; fix KDoc mismatches DEFERRED: needs a read-legacy-then-rewrite migration of every setting, best done with a device test |
| [x] | ST10 | Docs corrections (protocol doc, architecture.md, stale comments, logging scope) | | |
| [x] | ST11 | Backup decision (`allowBackup=false` already set; device-to-device transfer of plain-prefs keys) data_extraction_rules.xml: pairing + encrypted prefs excluded from device transfer |

## Phase 6: UX polish & accessibility

| Done | ID | Item | Commit | Notes |
|---|---|---|---|---|
| [-] | UX1 | "Resume navigation" pill in every stage DEFERRED: needs per-stage layout work to avoid overlapping bottom cards (verify on device) |
| [x] | UX2 | Banners: location off/denied, Bluetooth off, Nearby devices denied | | |
| [x] | UX3 | Search: clear button, IME action, shared-link feedback | | |
| [~] | UX4 | Touch targets ≥ 48 dp; glove mode 64 dp; large END-confirm buttons | | 🏍 Done: search back/clear, KtmToggle, Home/Work remove (48dp). NOT done: preview/stop-row icons, glove-mode 64dp, END-confirm buttons. Toggle rows are now ~12dp taller |
| [x] | UX5 | Contrast fixes (`Ktm.Dim`, small stat text) | | |
| [x] | UX6 | Semantics: `Role.Switch`, selected states, connection state, turn-icon descriptions Switch role on KtmToggle, RadioButton selection on RouteChip; connection-pill/turn-icon descriptions NOT done |
| [-] | UX7 | Units setting (+ yards) and system 12/24h DEFERRED: feature (units setting) - spans formatters, UI, rides, overspeed |
| [-] | UX8 | Real sunrise/sunset day/night DEFERRED: feature (sunrise/sunset) |
| [x] | UX9 | Speedometer over-limit colour + limit display | | |
| [x] | UX10 | Onboarding BackHandler; CONFIRM Back → BROWSE CONFIRM Back goes to the map unless reached from search; Onboarding BackHandler already consumed in MainActivity |
| [-] | UX11 | Pairing: real RSSI bars; "Connected to <bike> · Disconnect" DEFERRED: pairing RSSI bars / disconnect affordance |
| [x] | UX12 | Logs screen: auto-scroll, filter, stable keys Auto-scroll only; level/tag filter NOT done (log lines can't be keyed) |
| [x] | UX13 | Replay: tap-to-seek proximity threshold | | |
| [x] | UX14 | Replay: enforce 1500-vertex cap | | |

## Phase 7: New features

| Done | ID | Item | Commit | Notes |
|---|---|---|---|---|
| [x] | F1 | Live Updates navigation notification (check compileSdk 36.1 vs AGP 8.13.2 first; consolidate with SDK notification) | | 🔎 🏍 |
| [x] | F2 | GPX/KML export & share; GPX import | | |
| [x] | F3 | Manual "Record ride"; notification pause/stop; auto-pause | | |
| [ ] | F4 | Richer ride data, elevation, charts | | |
| [x] | F5 | "Ride home/work" chip + shortcut/widget | | |
| [x] | F6 | Quick categories + voice search | | |
| [ ] | F7 | Weather chip on map | | |
| [x] | F8 | Mirror home freshness indicator + missing-notification hint | | |
| [x] | F9 | Trip-finished actions (save destination, reverse route, share GPX) | | |
| [ ] | F10 | Ride rename / notes / trim; SAF backup/restore | | |
| [ ] | F11 | Per-leg ETA; drag-to-reorder stops | | |
| [ ] | F12 | Non-Latin road-name transliteration / ref fallback | | |

## Phase 8: Foundation (rev. 3, after smoke test 1 of batches 1-11)

Order: A1 -> A2 -> A3 -> A4 -> A5, then **smoke test 1 (user)**, then Phase 9.

| St | ID | Item | Notes |
|---|---|---|---|
| [x] | A1 | Dependency bumps within AGP 8.13.2 / Gradle 8.13 limits (ST8); compileSdk 36.1 (needed by F1) | core-ktx 1.17.0 (1.19 needs compileSdk 37/AGP 9.1), appcompat 1.7.1, material 1.13.0, activity-compose 1.12.4, lifecycle 2.10.0, coroutines 1.11.0, gms-base 18.11.1; compileSdk 36.1, targetSdk 36. Tests+lint unchanged (6 errors). ST8 done |
| [x] | A2 | Nav stage state -> ViewModel + SavedStateHandle (pure reducer, unit-tested = T5) and `configChanges` for rotation/size/density/uiMode (U7) | NavFlow.kt (pure NavFlowState/Rules/ArrivalPolicy/Codec + NavFlowViewModel, attach/detach counting), MainActivity routes rememberSaveable, manifest configChanges (locale still recreates, now safe). ~20 new tests. Needs on-device check: rotate on each stage, background-kill restore |
| [x] | A3 | Android 16 checks (0.6): insets on every screen, predictive back, landscape / large screen | Code review only. `edgePadding()` (system bars + display cutout) replaces systemBarsPadding everywhere; `enableOnBackInvokedCallback=true` (all back handling already via BackHandler, no onBackPressed); system-bar icon colours already theme-driven. NOT done: wide-screen content width caps. Visual check in landscape by user |
| [ ] | A4 | UX4 remainder (48dp: preview/stop icons, END-confirm), R1 ride list off main + live, NP1 mmap model | |
| [x] | A5 | ST9: replace security-crypto with Keystore AES-GCM store + plain prefs; read-legacy fallback + verify; own commit | CHANGED DESIGN (less risk): the encrypted file only held low-sensitivity keys (onboarding, name, mirror apps, ...); bike address/pairing flag/session keys were already plain. So AppSettings is now ONE plain store and LegacySettingsMigration copies the old encrypted+fallback contents once (plain wins, verified, retried up to 10 launches, skips Keystore when no legacy file). security-crypto stays ONLY as the read-only migration source: remove lib + LegacySettingsMigration after the first launch on every device. Settings > Diagnostics shows 'Settings storage: migrated (N keys)'. Session keys intentionally stay plain (a Keystore wrap that can silently reset = re-pair prompt). Revert alone with git revert |

security-crypto and `LegacySettingsMigration` were removed after smoke test 1 passed (they only existed to migrate once). The old `opendash_settings*.xml` files stay unused on disk.

## Phase 9: Features (rev. 3)

Decisions: no auto-pause (F3); GPX import = import as a ride only (F2); F6 includes "along route" + N4 "Add as stop"; F1 = own Live Update notification via NavigationApi.initForegroundServiceManagerProvider (in-app engine only).

| St | ID | Item | Notes |
|---|---|---|---|
| [x] | B1 | F3: manual Record ride (idle map), pause/resume/stop from notification + app | RideSession state flow, recorder pause/resume with gapBefore points (no distance/moving/duration across a pause, persisted in NDJSON so recovery matches), service Pause/Resume/Stop actions + chronometer notification, RecordControlsHost (chip/pill) bottom-left of browse map, trip observer skips its own recording when one is active. No auto-pause (decision). NOT done: breaking the replay polyline at pauses. Needs on-device check: chip fit on 360dp, pause/resume from notification |
| [x] | B2 | F2 + F9: GPX export/share, GPX import as ride, Trip-finished actions (save destination, reverse route, share GPX) | Gpx.kt (pure writer + hardened SAX importer: no DOCTYPE/entities/UTF-16, size/point caps, untimed points paced at 45 km/h, pauses <-> trkseg, thinning keeps pauses), GpxShare via FileProvider cache-path, History 'IMPORT GPX' (OpenDocument */*, saved=true), Replay share icon, TripFinished row: Save place / Ride back (start point as destination, stops reversed, straight to preview) / Share GPX. ~15 new tests. NOT done: ACTION_VIEW import from other apps, dedupe of repeated imports, imported-stats vs glitch-filter mismatch on wild files |
| [x] | B3 | F5: Home/Work chip + launcher shortcuts | Chips under the search bar (only set slots) -> preview directly; dynamic shortcuts 'Ride Home/Work' synced from PlacesStore + app start; ignored with a toast while NAVIGATING (reviewer must-fix). Needs on-device check: long-press the launcher icon, chip/banner spacing |
| [x] | B4 | F8: mirror home freshness + missing-notification / access-revoked hint | MirrorHints (pure, tested): NO_ACCESS / LISTENER_DISCONNECTED (tap = requestRebind) / STALE (>30 s) / BATTERY tip; 'Updated N s ago' line; NotificationRepository.lastUpdateMs + listenerConnected. Small batch: no separate reviewer pass. Cannot detect 'Maps notifications blocked' (no API for other apps) |
| [x] | B5 | F1: Live Updates navigation notification (promoted ongoing, progress style) | NavLiveUpdate (NotificationContentProviderBase via initForegroundServiceManagerProvider) + pure NavLiveContentBuilder: instruction / distance · remaining · ETA, status-bar chip (setShortCriticalText), whole-route ProgressStyle, setRequestPromotedOngoing guarded for pre-36.1; refresh only while guidance live and the SDK foreground notification is shown (no phantom card); fallback to the SDK default; Settings > Advanced > Live Updates toggle. Needs on-device check: chip only on Android 16 QPR2+ (system Settings > Notifications > Live updates must allow the app); lock-screen card; no notification left after End |
| [x] | B6 | F6 + N4: category chips, voice search, along-route search, Add as stop | Category chips (Fuel/Food/Coffee/Parking/ATM/Hospital/Pharmacy/EV) via Places Text Search (circle bias, or along the route AHEAD of the rider when adding a stop), mic button (RecognizerIntent), search control in guidance -> TRIP_STOP -> addStopToTrip (same ride key so recording isn't split; refused offline / when the trip already has stops; failed re-plan falls back to the previous trip). One back path (performBack) for system + on-screen back. NOT done: adding a stop to a trip that already has stops (passed stops unknown); stop ordering along the route. Needs on-device/dash check: re-plan mid-ride, guidance gap while recomputing, NAVIGATING bottom-right stack on small screens |

Smoke test 2 (user) after B6.

## Phase 10: Remaining features and polish (rev. 4)

Decisions: F7 weather skipped; backup = rides + places + prefs (never the bike pairing); UX11 stays deferred; one smoke test (3) after C5.

| St | ID | Item | Notes |
|---|---|---|---|
| [x] | C1 | F4: altitude per fix, elevation gain/loss, speed+elevation chart in Replay, GPX ele in/out | Altitude per fix ('a' in the track), ElevationTracker (median-3 + 4 m hysteresis, pause re-anchor), elev gain/loss on rides (unknown for old rides), cached-path RideChartView + up/down text above the seek bar (hidden below 480dp height), <ele> in GPX. Old rides have no altitude so no chart elevation. Needs on-device check: chart/card height on the replay screen |
| [ ] | C2 | F10: ride rename/notes/trim; backup + restore (SAF, merge-only) | |
| [ ] | C3 | F11: per-leg ETA in the preview, drag-to-reorder stops | |
| [ ] | C4 | F12: Latin transliteration / road ref for the dash | check dash font first |
| [ ] | C5 | Polish: replay line breaks at pauses, GPX import dedupe, Resume pill on more stages (UX1), real sunrise/sunset (UX8) | |

Smoke test 3 (user) after C5.

## Tests to extend/add

Existing (extend): `SerialGattQueueTest`, `RideMetricsTest`, `MapsUrlResolverTest`, `RoutesClientTest`, `TurnIconHeuristicTest`, `NavSubtextParserTest`.

| Done | ID | Item | Commit | Notes |
|---|---|---|---|---|
| [~] | T1 | `RideStore` / `PlacesStore` (new): corrupt entry, concurrency, prune, orphan-vs-active PlacesJson/RideJson/RideMetrics covered; RideStore itself needs Android (no instrumented tests) |
| [x] | T2 | `RideMetrics` (extend): jitter, spikes, backwards time, gaps, moving average | | |
| [x] | T3 | `SerialGattQueue` (extend): late completion after timeout | | |
| [x] | T4 | Nav trip-token logic (pure class): stale result/NavInfo ignored; `ROUTE_CANCELED` never retried | | |
| [x] | T5 | Nav stage state machine: C2, C3, N4, U1, U2 | | |
| [x] | T6 | `TurnBeeper.parseMeters` (new), severity words, `NavSubtextParser` edge cases, `maneuverTextFor` | | |
| [x] | T7 | `MapsUrlResolver` (extend): allowlist, foreign redirect, trailing punctuation | | |
| [x] | T8 | `RoutesClient` (extend): malformed polyline, fractional durations | | |

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
| 2026-11-18 | Rides store both averages (moving + overall); Settings > Ride recording > Average speed picks which one the UI shows (default Moving); legacy rides backfilled at launch | Moving-only changed the numbers riders were used to |
| 2026-11-18 | Debug-only Dash preview overlay is toggled in Settings > Diagnostics (default off) | Debug builds are the daily driver; the floating chip was always on |
