# Navigation UX Revamp — Phone-First Map Experience

Status: **P1–P5 shipped** (map home, search + recents/favorites, route preview + alternates,
on-phone active guidance, share-link fold-in). Route-token alternate-following is best-effort
pending on-bike validation. P6 (docs) done. Remaining: D2 lean-down + planned improvements (§8).
Target vehicle: 2026 KTM 390 Adventure X (Gen-3 "connected" BCCU dash), also KTM/Husqvarna Gen-3.
Scope: turn the app into a **phone-first, map-centric navigation experience** (inspired by
the Royal Enfield app's flow) on top of the *existing, working* Google Navigation SDK →
KTM BLE pipeline. The bike-dash guidance path is unchanged; this adds the on-phone map,
search, route preview, and full on-phone active-navigation UI.

This document is the authoritative design for the UX revamp. The engine/protocol design
lives in [`NAVIGATION_REVAMP_PLAN.md`](NAVIGATION_REVAMP_PLAN.md); the architecture
overview in [`architecture.md`](architecture.md); the wire protocol in
[`BCCU_BLE_PROTOCOL.md`](BCCU_BLE_PROTOCOL.md). Phase tracking lives in
[`IMPLEMENTATION_CHECKLIST.md`](IMPLEMENTATION_CHECKLIST.md).

> **Provenance labels.** **Confirmed (Google)** = stated in current Google docs;
> **Reverse-engineered** = derived from this repo / binary inspection of the AARs;
> **Assumption** = working hypothesis to verify during implementation. API-level facts
> here were verified against Navigation SDK for Android **7.9.0**.

---

## 1. Motivation

Today the app is **dash-first and remote-driven**, not phone-first:

- Routing is a hand-rolled enum state machine (`MainActivity.kt:48`, `AppRoute`) with a
  second remote-driven sub-router `ControllerScreen { IDLE, GRID, NOTIFICATIONS, DIRECTION }`
  (`ControllerStateMachine.kt:11`) inside `MAIN`.
- Home is `GridMenuScreen` — a 2×2 tile grid driven by the handlebar remote / on-screen
  D-pad (`RemoteDpadScaffold`).
- Destination entry (`DestinationScreen.kt`) has three tabs (SEARCH / MAP / LINK) with
  Places autocomplete, a pin-drop `MapView`, and Google-Maps-link resolving — but **no
  recents, no favorites**.
- **Google navigation runs completely headless.** `GoogleNavSdkProvider` consumes the
  SDK's `NavInfo` at ~1 Hz and streams turn/distance/ETA to the **bike dash over BLE**.
  **Nothing is drawn on the phone during navigation** — there is no `NavigationView` /
  `SupportNavigationFragment` anywhere in the app.

The goal: a clean, map-centric flow — search → confirm → route → start — where the phone
mirrors the same Google-Maps-style navigation the rider sees, so a glance at the phone
resolves any confusion about the dash arrows. The BLE dash path stays exactly as-is.

---

## 2. The enabling fact

**Reverse-engineered / Confirmed (Google):** Navigation SDK 7.9.0 already bundles the
view-based **`com.google.android.libraries.navigation.NavigationView`** (and
`SupportNavigationFragment`). `NavigationView` renders the *entire* Google-Maps-style
navigation UI natively — interactive map, blue route polyline, maneuver header, bottom
ETA sheet, speed limit, recenter, follow-my-location. The app already relies on the Maps
bundled inside the Nav SDK (standalone `play-services-maps` is excluded,
`app/build.gradle.kts:130-132`); the existing pin-picker uses that bundled `MapView`
(`DestinationScreen.kt:246-267`).

**Consequence:** the on-phone map (requirement: "render like Google Maps") and the
on-phone active-navigation screen ("full nav screen on the app too") are largely
*embed-a-view*, not *build-a-map-UI-from-scratch*. This drastically lowers effort/risk.

---

## 3. Decisions

| # | Decision | Rationale |
|---|---|---|
| D1 | **Map screen becomes the primary home.** App opens directly to the new navigation screen; landing screen after pairing. | Phone-first goal. |
| D2 | **North-star (future, not this epic): navigation-only app.** Remove ride recording/GPX, handlebar remote + on-screen D-pad, and non-nav calibration extras. | Lean, focused app. Every decision below is "lean-compatible" so teardown is clean. |
| D3 | **Active on-phone nav = Nav SDK's built-in `NavigationView` full UI.** | Most faithful to Google Maps, least effort. |
| D4 | **Use the view-based `NavigationView` (not `SupportNavigationFragment`).** | Avoids converting the pure-Compose `ComponentActivity` into a `FragmentActivity`. Embeds via `AndroidView`. |
| D5 | **One persistent `NavigationView` across all stages** (browse → confirm → preview → guidance). | "Already running when I glance at the phone" comes for free; single surface. |
| D6 | **Keep KTM-orange / Husqvarna-blue brand tokens** (`DesignTokens.kt`); borrow RE's *layout/interaction* only. | Preserve brand identity. |
| D7 | **Search scope v1: Recent locations + Favorites (Home/Work + saved).** No categories / voice / weather in v1. | Focus. |
| D8 | **Alternate-route selection is out of v1** → Planned improvements. | SDK's public API centers on the single best route; alternates likely need Routes API REST + custom polylines. |
| D9 | **Reuse the entire existing engine unchanged** — `GoogleNavSdkController` → `GoogleNavSdkProvider` → `NavigationCoordinator` → BLE. | The revamp is additive UI; the proven dash path is untouched. |

---

## 4. Screen flow

A single new screen, **`NavigationHomeScreen`**, hosts one persistent `NavigationView`
surface with a bottom sheet that changes per stage. State machine:
`NavFlowState { BROWSE, SEARCH, CONFIRM, PREVIEW, NAVIGATING }`.

| Stage | RE reference | Contents |
|---|---|---|
| **BROWSE** | screenshot #1 | Full map + location dot; top search bar ("Enter Destination"); floating **Connect / Connected** pill (binds `BccuConnectionService.connectionState`; tap → Pairing); recenter button. |
| **SEARCH** | screenshot #2 | Search sheet: **Recent Locations** + **Favorites** + live autocomplete (reuse `PlacesClient`). |
| **CONFIRM** | screenshot #3 | Red pin on map + place card (name / address / straight-line distance) → **GET DIRECTIONS**; **Save / Rename** (favorites). |
| **PREVIEW** | screenshot #4 | Draw the chosen route on the map; card shows ETA + distance → **START NAVIGATION**. |
| **NAVIGATING** | — (new) | `GoogleNavSdkController.startNavigation()` streams to the **bike** (unchanged) **and** `NavigationView` shows full guidance on the phone; **END** stops both. |

**ToS timing.** `NavigationView` requires the Nav SDK Terms accepted before it renders.
Today ToS fires later, inside `NavigationApi.getNavigator` (`GoogleNavSdkController.kt:57`).
We move acceptance to the **first open of the nav home**. *Fallback if undesirable:* use a
plain `MapView` for BROWSE/CONFIRM/PREVIEW and swap to `NavigationView` at START.

---

## 5. Architecture

### 5.1 New components
- **`ui/screens/NavigationHomeScreen.kt`** — the map-first home; hosts `NavigationView`,
  the search/confirm/preview/nav bottom sheets, the Connect pill, and recenter.
- **`NavFlowViewModel`** (or a small state holder) — owns `NavFlowState`, the selected
  `NavDestination`, recents/favorites, and preview route info. Screens read connection
  status directly from `BccuConnectionService` StateFlows as they do now.
- **`NavigationView` Compose wrapper** — `AndroidView` + a lifecycle bridge mirroring the
  existing `rememberMapViewWithLifecycle` (`DestinationScreen.kt:337-365`). Uses
  `getMapAsync` for markers/polyline/camera during browse & preview; `NavigationView`
  takes over the chrome once `startGuidance()` fires; toggle `setNavigationUiEnabled`
  between browse and guidance.
- **`PlacesStore`** — recents + favorites (Home/Work + saved). JSON-encoded in the
  existing `AppSettings` prefs (no Room/DataStore; lean). Recents appended on successful
  navigation start; capped (e.g. last 15), de-duped by place/coords.

### 5.2 Reused unchanged
- `PlacesClient.kt` (autocomplete/details), `MapsUrlResolver.kt` (share links).
- `GoogleNavSdkController` / `GoogleNavSdkProvider` / `NavigationCoordinator` → BLE.
- Connection StateFlows: `BccuConnectionService.connectionState / deviceName / signalRssi`.
- `NavDestination` (`NormalizedNavigationState.kt:141`); brand tokens & components
  (`DesignTokens.kt`, `KtmComponents.kt`).

### 5.3 Retired / folded
- `DestinationScreen.kt` (SEARCH / MAP / LINK tabs) is superseded by the new flow.
- Share-intent / Google-Maps-link handling folds into the search sheet
  (`MainActivity.sharedNavLink`, currently routed to `DestinationScreen`).

### 5.4 Router wiring (`MainActivity.kt`)
- Add `AppRoute.NAV_HOME`; make it the start destination and the post-pairing landing
  (replacing `MAIN`/grid as the default). The existing `AppRoute.MAIN` (grid / notification
  / direction mirror) stays reachable for on-bike use **until the D2 lean-down epic**.
- Preserve sane back-stack behavior (`BackHandler`, `MainActivity.kt:417`).

### 5.5 Data flow (unchanged engine)
```
NavigationHomeScreen (START)
  → GoogleNavSdkController.startNavigation(activity, dest)   [existing]
      → GoogleNavSdkProvider (Navigator, NavInfo @1 Hz)      [existing]
      → NavigationCoordinator → KtmNavigationEncoder         [existing]
      → BccuConnectionService (DashOutput) → BLE → dash      [existing]
  → NavigationView.startGuidance() renders full UI on phone  [NEW, additive]
```

---

## 6. Phasing

| Phase | Deliverable | Status |
|---|---|---|
| **P1** | `NavigationHomeScreen` shell: live `NavigationView` map + Connect pill + search bar; ToS acceptance on entry; set as home. | ✅ shipped |
| **P2** | Search sheet: autocomplete + **Recents/Favorites** (`PlacesStore`); CONFIRM stage (pin + place card). | ✅ shipped |
| **P3** | Route **PREVIEW** (alternates, route chips, route tokens) + START. | ✅ shipped (alternate-follow best-effort) |
| **P4** | On-phone active guidance via the SDK's stock `NavigationView` UI + **END**. | ✅ shipped |
| **P5** | Polish: **share-link fold-in** (done). FGS/notification consolidation → deferred to Phase 7 reliability; a shared `PlaceSearch` de-dup → deferred (cosmetic). | ✅ (partial; deferrals noted) |
| **P6 (docs)** | Reconcile `architecture.md` (§1 shape, §3 flow, banner cleared) + this doc + checklist. | ✅ done |

Each phase: assistant edits; **user builds & tests** in Android Studio (JVM tests where
applicable + on-device); commit per phase/slice on the user's go-ahead.

---

## 7. Risks / to verify

1. **Maps billing/enablement.** Full map + `NavigationView` use more Maps SDK than today's
   tiny pin-picker. Confirm the `NAV_SDK_API_KEY` has Maps SDK enabled and billing set.
   (Key wired via `manifestPlaceholders["MAPS_API_KEY"]`, `AndroidManifest.xml:52-54`.)
2. **`NavigationView` in Compose.** Validate it renders correctly inside `AndroidView`
   with our lifecycle bridge, and that browse↔guidance chrome toggling works.
3. **ToS timing** (§4) feels acceptable when moved to nav-home entry.
4. **Location + FGS.** Continuous location for map + active nav while BLE runs; the FGS
   already declares `location` (`AndroidManifest.xml:94-96`). Pair with the queued Phase-7
   FGS/notification consolidation.
5. **Two-wheeler routing fallback** already handled in `GoogleNavSdkProvider` (TWO_WHEELER
   → DRIVING, `:105-108`); ensure preview reflects the actually-routed mode.

---

## 8. Planned improvements (post-v1)

- **Alternate-route selection** (screenshot #4's multiple ETAs) — likely via Routes API
  (REST) fetching alternates + custom polylines for preview, then hand the chosen
  destination to the Nav SDK. Extra API cost.
- Quick-access **categories** (Fuel / Food / …), **voice search** (mic), **weather chip**.
- **D2 lean-down epic:** remove ride recording/GPX, handlebar remote + on-screen D-pad,
  non-nav calibration → navigation-only app.

---

## 9. Documentation obligations (do NOT skip)

The design docs must be reconciled with reality **as the revamp lands** — not left stale.

- **[`architecture.md`](architecture.md)** — the authoritative update target:
  - §1 "What the app does" — reflect the phone-first, navigation-first shape (and, once
    the D2 lean-down happens, the removal of ride recording and the handlebar-remote
    controller).
  - §3 "Destination entry" — rewrite for the new `NavigationHomeScreen` flow (search sheet
    with recents/favorites, pin confirm, route preview, on-phone active guidance via
    `NavigationView`); the SEARCH/MAP/LINK `DestinationScreen` is retired.
  - Add the on-phone active-navigation surface (`NavigationView`) and the router change
    (`NAV_HOME` as home) to the relevant sections.
  - Remove the temporary "⚠ Being revised" banner once §1/§3 are reconciled.
- **This document** — flip Status to reflect shipped phases; move completed items out of
  §8 Planned improvements.
- **[`IMPLEMENTATION_CHECKLIST.md`](IMPLEMENTATION_CHECKLIST.md)** — track P1–P6 with the
  same per-phase discipline as the engine phases.
- **README / CHANGELOG** — user-facing note that the app is now phone-first with an
  on-phone map + active navigation.

`architecture.md` currently carries a forward-note banner and per-section "superseded"
markers pointing here; those are the checkpoints to clear when P6 runs.

---

## 10. Non-goals

- No change to the BCCU/BLE protocol or the KTM encoder/mapping.
- No full-screen map on the KTM dash (impossible over the link — see
  [`NAVIGATION_REVAMP_PLAN.md`](NAVIGATION_REVAMP_PLAN.md) §2.1).
- No reading of the consumer Google Maps app's active session.
