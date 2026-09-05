# Backlog

Consolidated remaining work, replacing the now-deleted phase-by-phase planning/checklist docs
(`NAVIGATION_REVAMP_PLAN.md`, `IMPLEMENTATION_CHECKLIST.md`, `D2_LEAN_DOWN_PLAN.md`,
`NAVIGATION_UX_REVAMP.md` — all superseded by [`architecture.md`](architecture.md), which
documents the shipped, current-state system). See also
[`ENGINEERING_NOTES.md`](ENGINEERING_NOTES.md) for the non-obvious decisions/fixes behind some
of the shipped work.

---

## To implement / deferred

- **FGS/notification consolidation** — the Nav SDK shows its own foreground notification
  during guidance, so there are currently **two** notifications (ours + the SDK's). Consolidate
  via `NavigationApi.initForegroundServiceManager*`.
- **Off-main-thread notification work** — move bitmap render + TFLite inference + disk I/O off
  the notification listener's main thread (currently synchronous).
- **Migrate in-app UI to normalized state** — `DirectionScreen`/`GridMenuScreen`/`MainActivity`
  still read the legacy `NotificationRepository` rather than `NormalizedNavigationState`; no
  functional regression today, but it's a parallel source of truth to eventually retire.
- **Units hardcoded metric** in the Google Nav SDK provider — wire to a user setting
  (imperial/metric), matching the notification-path formatter.
- **Revisit notification-path passthrough overrides** — the notification provider currently
  feeds the encoder via optional override fields on `NormalizedNavigationState`
  (`resolvedIcon`, `preformatted{Distance,Eta,Remaining}`) to stay byte-for-byte identical to
  pre-refactor behavior. Decide whether to keep these or fully normalize the notification path
  to numeric fields now that the Nav SDK path is stable.
- **Remove the legacy `DestinationScreen` LINK path** — only the shared-Maps-link handling is
  still reachable (share-link fold-in now covers this in `NavigationHomeScreen`); safe to
  delete once confirmed unused.
- **Extract a shared `PlaceSearch` composable** to de-duplicate `SavedPlacesScreen`'s inline
  place picker against the main search sheet. Cosmetic/internal; skip if it risks regressing
  either of the two working autocomplete screens.
- **Full code-namespace rename** `com.navigator.app` → `com.navigator.ktm` (package
  declarations + imports across ~49 files, FileProvider authority, broadcast action strings,
  `BuildConfig`/`R`). Cosmetic, not required for release — see
  [`ENGINEERING_NOTES.md`](ENGINEERING_NOTES.md) for why the split exists today.
- **Migrate off `androidx.security.crypto` (EncryptedSharedPreferences)** — the whole Jetpack
  Security library is deprecated (currently pinned at `1.1.0-alpha06`) and is documented in
  `AppSettings.kt` as prone to silent corruption/reset on some devices (the reason bonded
  MAC/name and the "paired before" flag were already moved to a plain `SharedPreferences`
  store). What remains in the encrypted store is non-critical config. Plan: either (a) move the
  few genuinely-sensitive values to the Keystore directly and drop the dependency, or (b) accept
  plain prefs for all of it since the BLE MAC/keys of record already live in the plain store.
  Needs a one-time migration path (read-legacy-then-rewrite) so existing installs don't lose
  settings. Deferred — behavioural change to persisted data, out of scope for the dead-code pass.

## To test / verify

- **Maps SDK + Routes API billing/enablement** — confirm `NAV_SDK_API_KEY`'s Cloud project has
  Maps SDK, Places API (New), and Routes API (New) all enabled with billing attached (the full
  interactive map + route preview use more of the Maps SDK than the old pin-picker did).
- **`NavigationView` in Compose** — validate it continues to render correctly inside
  `AndroidView` with the lifecycle bridge, and that browse↔guidance chrome toggling has no
  edge cases.
- **ToS prompt timing** — confirm showing the Nav SDK ToS at nav-home entry (rather than at
  first navigation start) still reads as acceptable UX.
- **Continuous location + FGS** while BLE runs — pair with the FGS/notification consolidation
  above; confirm no location gaps during an active trip.
- **Release-build reconnect stability** — confirm on a release (non-debug) build that an
  authenticated BLE session stays up indefinitely (continuous 1 Hz `AssignKeyIndex` keepalive,
  no spontaneous `status=8`/`133` drops), now that `probeVehicleInfo()` is debug-only.

## Release tasks

- **In-app attribution/licensing text + Play data-safety disclosure** — the Nav SDK ToS is
  already shown on first use (`GoogleNavSdkController`), but the required in-app
  attribution/licensing text and the Play Store data-safety disclosure (destination + location
  sent to Google when the in-app engine is used) are not yet written.
- **Final review pass**, before a public/release build:
  - Reliability review (state correctness, stale-guard on the encoder/coordinator)
  - Android lifecycle / background / battery review (targetSdk 36 behavior)
  - Bluetooth stability review (confirm no regressions to the protocol layer)
  - Error handling & malformed-input review (`MapsUrlResolver`, Places/Routes JSON parsing)
  - Security / API-key handling review (no key in VCS, not logged, runtime `setApiKey`)
  - Dependency / APK-size note (single-APK Nav SDK, no-minify tradeoff — last measured ~29 MB
    debug, `arm64-v8a` only)
  - Billing sanity check (Navigation Request cap; picker map has no `mapId`; SDK `Simulator` is
    billable — prefer the `NavigationReplayTest` harness for routine testing)
  - Maintainability review (abstraction stays clean for a possible future `RoutesApiProvider`)
  - Full test suite green (`./gradlew :app:testDebugUnitTest`) + a manual on-bike smoke test

## Planned features (post-v1)

- Quick-access **categories** (Fuel / Food / …) in the destination search sheet.
- **Voice search** (mic) for destination entry.
- **Weather chip** on the map home.
