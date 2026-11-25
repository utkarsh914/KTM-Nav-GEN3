# Engineering Notes

Condensed write-ups of non-obvious decisions, bugs, and fixes that aren't self-evident from
the code alone. Companion to [`architecture.md`](architecture.md) (the current-state
reference) and [`BACKLOG.md`](BACKLOG.md) (remaining work) — this doc is a "why does it work
this way" reference, kept so the reasoning isn't lost to git history alone.

---

## The map layer: why page transitions are smooth (do not undo)

**Symptom:** stutter on page opens, worst on opening search and opening a ride (all map screens). Measured with
Perfetto on a 120 Hz phone: the shared map view was resized on almost every frame while a page opened, because the tab
bar slides away and the map's container resized with it. In 4 ride opens that was 110 full re-layouts of the map,
6-30 ms each, each blocking the UI thread on the map's own render thread (`GL-Map`, the Navigation SDK draws into a
TextureView), with stalls of 60-170 ms. A padding change on the map likewise makes it re-lay itself out for several
frames. The cost was in the map, not in Compose.

**What keeps it fixed (each one has been measured; changing any of them brings the stutter back):**
1. `MapLayer` (one `RetainedNavigationView`, hosted once) sits in the root of `KtmApp`, behind `MainShell`, at full
   window size. Never put it inside something that changes size (the shell's content area, a page, an animated
   container) and never re-parent, fade, scale or clip it.
2. What the shell covers (tab bar, rail, activity strip) reaches the map as padding, sent once it has held still
   (`MainShell.onContentInsets`, `RetainedNavigationView.setShellInsets`), never as a resize.
3. All map padding goes through `PaddingFunnel` (`RetainedNavigationView.requestPadding`): requested + shell insets,
   an equal total is not sent again. Do not call `GoogleMap.setPadding` directly, and do not reset the padding "to be
   safe" (search used to, and cost frames for nothing).
4. Map pages (`isMapPage()`: Navigate, replay, trim) do camera, padding and overlay work only once the page has
   arrived (`rememberPageSettled()`), after `MapController.awaitPaddingSettled()`, not under the animation.
5. Pages slide opaque and full width, tabs cross-fade; no page slides and fades at once (a full-screen alpha layer per
   frame). Guard: `PageTransitions.kt` is checked by the UI rules script.
6. **Nothing between the screen and the map may swallow touches over the map's empty areas.** `NavigationSuiteScaffold`
   wraps its content in a Material `Surface`, which consumes every pointer event, so with the map behind the shell the
   map could not be panned or tapped. `MainShell` therefore uses `NavigationSuiteScaffoldLayout` + `NavigationSuite`
   (the scaffold's public parts) without the Surface. Do not put a `Surface`, a clickable background or a catch-all
   `pointerInput` over a map page's empty areas. Whatever is drawn over the map (sheets, panels, covers, an opaque
   error state) takes its own touches with `consumeMapTouches()`, and an opaque page uses `coverGround()` (opaque,
   consumes taps but not every event: a parent that consumes small moves cancels its children's taps).
   `ShellTouchThroughTest` guards the shell only (a stand-in map behind `MainShell`, portrait and landscape); the rest
   is checked on a device (drag the map on Navigate, replay, trim and pick on map).
7. **No offscreen layers for full-screen fades.** Compose's `fadeIn`/`fadeOut` render the content into an offscreen
   buffer of its own size while alpha is below 1. For a screen-sized container that is a new 10 MB GPU buffer every
   frame: opening search allocated 110-180 MB of GPU memory in 12 frames (`gpu_mem_total` in Perfetto), and on a phone
   short of free RAM (kswapd/kcompactd burning CPU) the render thread then stalled for 90-137 ms in `flush layers`. Use
   `layerFade` (`ui/shell/LayerFade.kt`, alpha per draw, `ModulateAlpha`) with `EnterTransition.None` and
   `holdUntilFaded(spec)` for the Navigate stage and the search container (the hold is a tween of the spec's own duration: a fade to the alpha it already has lasts no time at all under a spring); GPU growth per search open fell to 9 MB and
   the stalls disappeared. Page fades follow the same rule: `PageTransitions.kt` only slides
   (and holds the leaving page with `holdUntilFaded`); `pageFade()` decides which page fades and how fast, `PageHost`
   applies it per draw, and `rideContainer`/`searchContainer` fade their shared bounds with `layerFade`. Opening a ride
   went from 674 MB of GPU buffer churn (one 89 ms frame) to 67 MB (worst frame 14 ms). Never use `fadeIn`/`fadeOut`/
   `AnimatedVisibility` fades on a screen-sized container, and measure `gpu_mem_total` (add `gpu_mem/gpu_mem_total` to
   the ftrace events) before adding any large alpha, scale-with-alpha or clip layer. Memory is part of smoothness here.
8. Only the settled top page owns the map (`MapOwnership`, `MapClaim`): a page that is only composing (a back-swipe
   preview, the page under a push) never claims, a page that lost the map takes it back when it is on top again, and
   every controller write is a no-op unless it owns. Padding keeps one funnel for the life of the map; a controller keeps
   its last request and replays it when it takes the map. Pop detection for page fades uses the arriving page
   (`BackStackHistory`), not the leaving one: a tab switch removes the old tab from the stack and is still a push.
9. Pick place is not an `isMapPage()`: it is an ordinary opaque page that turns transparent over the map only while
   it is picking on the map (`PlacePickScreen`); it follows the same rules.

**How to measure again:** `benchmark` build type (release-like, debug-signed, R8 + Baseline Profile) on a real phone;
`adb shell perfetto` with `sched`, `view`, `gfx`, `input` and `wm` categories while a script taps through search /
a ride / the tabs; look at `AndroidOwner:measureAndLayout` slices over 12 ms and how long the main thread slept inside
them, and at `MapHost`-style `onLayout(changed=true)` calls on the map's parent (there should be none while a page
moves). `adb shell dumpsys gfxinfo <package>` gives janky-frame percentiles. Acceptance used: p95 frame time under the
refresh interval, at most 1 % janky frames, no frame over 32 ms, with animation timings unchanged.

## Roundabout exit glyph is chosen by exit angle, not ordinal exit count

**Symptom:** in-app Google navigation showed a "sharp right" roundabout glyph on the dash that
didn't match the actual exit taken.

**Cause:** `KtmManeuverMapping` mapped Google's **ordinal** exit count
(`step.roundaboutTurnNumber`) directly onto the dash's `RAB_SECT_N` icon index (i.e. assumed
"exit 2" → `RAB_SECT_2`). But the dash has no route geometry — it just renders a **fixed glyph
per code**, and `RAB_SECT_1..16` are actually **16 fixed exit-angle glyphs**, not ordinals.

**Hardware probe result** (KTM 390 Adventure, via Symbol Testing): on the RH block, the exit
arrow sweeps **counter-clockwise** as N increases — N=1 is sharpest right, N=4 is 90° right,
N=8 is straight-through, N=12 is left, N=16 is a U-turn. So:

```
turnAngle = (8 − N) · 22.5°        (RH; LH is mirrored)
```

**Fix:** `roundaboutSection(turnAngleDeg, clockwise)` = `base + (N − 1)`, where
`N = (clockwise ? 8 − steps : 8 + steps).coerceIn(1, 16)` and `steps = round(angle / 22.5)`.
Each `ROUNDABOUT_*` maneuver supplies its 45°-bucket angle (e.g. `SHARP_RIGHT` → +135° → N2,
`LEFT` → −90° → N12, `SHARP_LEFT` → −135° → N14). `ROUNDABOUT_UTURN` maps to plain
`UTURN_LEFT`/`UTURN_RIGHT` (product decision); `ROUNDABOUT_GENERIC` → `UNDEFINED`. The `exit`
param was dropped from `toTurnIcon`/the encoder call (kept-but-unused on
`NormalizedNavigationState.roundaboutExit`, still logged in `GoogleNavSdkProvider`).
RH-clockwise / LH-mirrored confirmed on hardware. Rewritten as angle-based in
`KtmManeuverMappingTest` (RH + LH cases).

### Follow-up: the notification-mirroring path still used ordinals

The fix above only covered the Nav SDK path. Mirroring Google Maps had two more copies of the
old assumption:

- **Text parser** (`TurnIconHeuristic`): "take the 3rd exit" → `RAB_SECT_3`, and a bare
  "roundabout" defaulted to `RAB_SECT_2` — i.e. nearly every mirrored roundabout showed some
  flavour of sharp right. Now the ordinal is converted to an **approximate angle**
  (`KtmManeuverMapping.ordinalExitAngle`: clockwise 1st ≈ left, 2nd ≈ straight, 3rd ≈ right,
  4th+ ≈ U-turn; CCW mirrored) and fed through the shared `roundaboutIcon()`. No ordinal →
  `UNDEFINED`. For roundabouts the **icon** (calibration, then classifier) is consulted before
  the text, because the Maps icon encodes the real exit angle.
- **TFLite model**: trained with pre-verification labels (RH slight/sharp right swapped; LH bank
  never mirrored; roundabout U-turn → N16). `ml/train_maneuver_model.py` now derives labels from
  the same law and writes a `#labels-v2` header; until a v2 model ships,
  `LegacyModelRemap` corrects the shipped model's roundabout outputs at runtime.

**Retrained (v2):** the dataset was regenerated from a Play Store emulator (which ships Google
Maps) and the model retrained; the bundled labels now carry `#labels-v2`, so the remap is
dormant. Two findings: `merge`/`merge_left`/`merge_right` are **pixel-identical** in Maps, so
they're one `merge` class resolved by driving side (three labels on one image capped confidence
at 0.42); and direction-less roundabout icons map to `roundabout_generic` → no arrow. Verified
192/192 in Python and on-device.

---

## On-ramps are a turn, not "enter highway"

**Symptom:** "Sharp right onto the ramp to NH 48" showed the enter-highway glyph on the dash,
whose arrow leans *left*.

**Cause:** `ON_RAMP_*` was mapped to `ENTER_HIGHWAY_*`. Google's `ON_RAMP_<dir>` is the turn
from the surface road onto the ramp; joining the highway is the later `MERGE_*` step.

**Fix:** on-ramps → LIGHT/QUITE/HEAVY by severity (matching the text and model paths), sharp
off-ramps → HEAVY, `MERGE_UNSPECIFIED` → driving-side lane. The merge code is picked only in
`enterHighwayIcon()`.

### Follow-up: merge codes 14/15 verified, merge direction was mirrored

A second report ("Turn left onto the ramp to NE 7" → enter-highway glyph on both phone and dash)
turned out to be a pre-fix build (0.6.0+0920-2209), but its dash photo settled the open question:

- **Code 14** (`ENTER_HIGHWAY_RIGHT_LANE`) = ramp on the **right**, arrow bending **left**.
- **Code 15** (`ENTER_HIGHWAY_LEFT_LANE`) = ramp on the **left**, arrow bending **right** (the
  normal left-hand-traffic on-ramp).

What the photos verify is how codes 14/15 *render*. That Google's `MERGE_LEFT` ("current road
joins another on the left") means the rider moves left off a ramp on the right — hence code
**14**, not 15 — is our reading of the SDK doc and is **not yet confirmed on a ride**: the
per-step log (below) should be checked the first time a real `MERGE_LEFT`/`MERGE_RIGHT` appears. `enterHighwayIcon` had one boolean in two opposite
senses: "ramp on the left" for unqualified merges (correct) and "move left" for `MERGE_LEFT` /
"merge left" (mirrored). It now takes `rampOnLeft` only: `MERGE_LEFT` → 14, `MERGE_RIGHT` → 15,
unqualified → driving side (LHT → 15). The phone previews (`dash_highway_left/right`) were redrawn
to look like the dash glyphs.

Safeguards added: `KtmManeuverMapping.resolveIcon` (shared by the encoder and the phone header)
shows the turn arrow when the SDK reports `MERGE_*` but its own instruction explicitly says
"turn/sharp/slight left/right" (the turn phrase must hold the first left/right word; English
only, since the SDK text follows the device language — other languages just don't override);
`GoogleNavSdkProvider` logs the raw SDK maneuver, the chosen icon and any text-derived turn once
per step. Release logs redact ALL user content - road/instruction text, destinations, notification
text, message titles, the connect-greeting name - via `AppLogger.sensitive` (length only), and the
public `Download/OpenDash` copy of the log is opt-in (Settings), because the log is shareable. Also: model outputs 14/15 are treated as generic "merge" (Maps draws left/right
merges identically), "Merge onto Left Bank Expy" no longer reads the road name as the side, and the
"Then" hint uses the step's driving side.

---

## Provider handoff: stale StateFlow values

The Nav SDK provider's `StateFlow` replays its last value on collection, so switching to it for
a second trip first delivered the *previous* trip's `STOPPED`, and the coordinator's auto-revert
immediately switched back to mirroring (detaching the provider and cancelling the new route).
Now a terminal state reverts only if the session was seen live or was produced after the
provider was selected; `startNavigation` also resets state to IDLE. `NavigationCoordinator.stop()`
no longer detaches a routing provider (BLE service teardown was cancelling in-app trips), and
the service selects the SDK provider on start if a trip is already in progress.

---

## GATT queue must self-heal

Android sometimes never delivers `onCharacteristicWrite` on a link that stays up, and a throw
inside an op skipped `finish()` — either left `SerialGattQueue` busy forever, silently freezing
the dash until reconnect. The queue now has a per-op timeout and exception release, guarded by
an op id so a late timeout can't release the wrong op. Because the encoder's dedup assumes
delivery, a failed guidance write triggers a full resync, and the coordinator re-sends all
guidance every 15 s while navigating.

---

## 16 KB page-size compatibility — the TFLite native lib was the only offender

**Trigger:** Play requires 16 KB page-size compatibility for apps targeting Android 15+ from
2025-11-01; surfaced by the `targetSdk` 36 bump.

**Investigation:** checked the actual ELF LOAD-segment alignment of every bundled
`arm64-v8a` `.so` via `objdump -p`:
- Nav SDK `libgmm-jni.so` → `2**14` (16 KB) — OK.
- `libandroidx.graphics.path.so` → `2**14` even at graphics-path 1.0.0 — OK (an earlier "needs
  fixing" flag was stale; no change needed).
- **`libtensorflowlite_jni.so`** from `org.tensorflow:tensorflow-lite:2.14.0` → `2**12` (4 KB)
  — the only offender.

**Fix:** swapped `org.tensorflow:tensorflow-lite:2.14.0` → **`com.google.ai.edge.litert:litert:1.4.2`**.
This is a drop-in: same underlying `libtensorflowlite_jni.so` (now built `2**14`), same
`org.tensorflow.lite.Interpreter` API (`Interpreter(ByteBuffer)` + `run()` both present,
confirmed via `javap`) — so `ManeuverClassifier` needed no code change. APK-level
page-alignment of the uncompressed `.so` is handled by AGP defaults (since 8.13)
(`useLegacyPackaging=false`, no `extractNativeLibs` override, 16 KB zipalign).

**LiteRT 2.x (now 2.3.2):** the `Interpreter` API is unchanged, but the natives are now
`libLiteRt.so` (holds the `org.tensorflow.lite` JNI symbols) + `liblitert_jni.so`, both `2**14`;
`zipalign -c -P 16` passes on the debug APK.

---

## Nav SDK route-token fix: `CustomRoutesOptions` needs an explicit travel mode

**Symptom:** picking an alternate route in the preview and hitting Start silently guided the
*default* (usually-fastest) route instead of the one the rider picked.

**Cause:** the first attempt called `Navigator.setDestinations(waypoints, String)` — that
overload doesn't exist. The real route-token API is
`Navigator.setDestinations(waypoints, CustomRoutesOptions)`. Confirmed via APK dex
introspection that `CustomRoutesOptions.Builder` requires **both** `setRouteToken(...)` **and**
`setTravelMode(CustomRoutesOptions.TravelMode …)` — the builder validates required properties,
so calling `build()` without a travel mode threw internally and the SDK silently fell back to
default routing.

**Fix:**
```kotlin
CustomRoutesOptions.builder()
    .setRouteToken(token)
    .setTravelMode(CustomRoutesOptions.TravelMode.TWO_WHEELER)
    .build()
```
Tokens come from `RoutesClient` (TWO_WHEELER-first), so the travel mode matches; a graceful
fallback to default `setDestination` routing is kept if the builder ever throws. Edge case: a
rare DRIVE-fallback token would mode-mismatch and silently fall back to default routing too —
acceptable, not yet hit in practice.

**Known SDK limitation (kept as-is):** even with a valid route token, the Nav SDK sometimes
re-picks the fastest route instead of honoring the selected alternate exactly, when tested from
a stationary position (confirmed via one-shot ENROUTE-distance logging: some destinations
honored the token exactly, others didn't). A fresh-fix origin (vs. cached last-known location)
did **not** change this, ruling out an origin-mismatch cause — the SDK appears to road-snap a
stationary test position and re-picks fastest when the alternate diverges early from that
snapped point. Route tokens are the only documented lever and are used correctly; expected to
track the selected route reliably once actually moving/riding along it.

**Superseded in Nav SDK 8.1 (now in use):** `Navigator.setDestinationsWithToken(token, callback)`
takes the destinations (stops included) and travel mode from the token itself, so the
waypoint/mode mismatch above can't happen any more. The callback's
`RouteDescription.getRouteTokenExecutionDetails()` reports whether the token was actually
applied: `GoogleNavSdkProvider` logs it on every start (`Route (token) OK, token <status>`) and
shows the "using the recommended route" toast when it's `TOKEN_IGNORED_*`.
`TOKEN_APPLIED_WITH_LOCATION_MISMATCH` (device > 25 m from the preview origin) is only logged.
That status is the way to confirm or rule out the stationary re-pick behaviour above.

---

## Toolchain version constraints (Gradle 9 / AGP 9 / Kotlin 2.4)

- **Daemon toolchain:** the JVM that runs Gradle is pinned in `gradle/gradle-daemon-jvm.properties`
  (JDK 25, generated by `./gradlew updateDaemonJvm --jvm-version=25`; the foojay resolver plugin in
  `settings.gradle.kts` supplies the per-platform download URLs). Android Studio and `./gradlew`
  therefore share one daemon, and a machine without JDK 25 downloads it. Android Studio's
  "Gradle JDK" setting no longer applies. To move to a newer JDK, re-run `updateDaemonJvm`.
- **Built-in Kotlin (AGP 9):** AGP compiles Kotlin itself; do **not** re-add the
  `org.jetbrains.kotlin.android` plugin (it fails to apply). The Kotlin Gradle plugin version comes
  from the Compose compiler plugin in the root `build.gradle.kts` (AGP's own 2.2.x dependency is
  upgraded to it), so bump `org.jetbrains.kotlin.plugin.compose` to change Kotlin. `jvmTarget`
  follows `compileOptions.targetCompatibility` (17); no `kotlin { compilerOptions }` block needed.
- **AGP ↔ Gradle:** AGP 9.5 needs Gradle ≥ 9.6. Upgrade them together.
- **No raw processes at configuration time:** Gradle 9 deprecates `ProcessBuilder`/`exec` during
  configuration (error in Gradle 11); `gitOutput()` in `app/build.gradle.kts` uses `providers.exec`.
- **Library ceilings:** Compose BOM ≥ 2026.08.00 (Compose 1.12) and core-ktx 1.19 need compileSdk 37 +
  AGP ≥ 9.1. Re-check `checkDebugAarMetadata` before raising compileSdk-sensitive AndroidX libs.
  compileSdk is 37.2 (also covers the 36.1 promoted-ongoing notification APIs); targetSdk stays 36
  (raising it opts in to Android 17 runtime behavior, so do that as its own tested change).
- **Nav SDK 8.x:** needs AGP ≥ 9.0.1 / Gradle ≥ 9.1. `GoogleNavSdkProvider` uses
  `setDestinationsWithToken()` and the `RouteDescriptionLoadCallback` overloads; the 8.1-deprecated
  `CustomRoutesOptions` / `ListenableResultFuture` APIs are no longer used.

---

## App rebrand: `applicationId` and code namespace are intentionally different

The app is branded **"KTM Navigator"** with `applicationId = com.navigator.ktm`, but the
**code namespace stayed `com.navigator.app`** (package declarations, imports, `R`,
`BuildConfig`, FileProvider authority, broadcast action strings) — Google recommends a stable
namespace, and a full rename would touch ~49 files for a purely cosmetic gain.

Practical consequences:
- The rebrand made this install a **new app** from Android's point of view — an existing
  install doesn't upgrade in place; pairing/settings are recreated once on first launch of the
  rebranded build.
- The **API key must be restricted to `com.navigator.ktm`** (the applicationId) + the
  debug/release SHA-1 — not `com.navigator.app`.
- `FileProvider` authority (`com.navigator.app.fileprovider`) and broadcast actions
  (`com.navigator.app.*`) are keyed to the code namespace, not the applicationId — internally
  consistent, no change needed there.
- A full code-namespace rename (`com.navigator.app` → `com.navigator.ktm`) remains a possible
  future cosmetic cleanup (best done via Android Studio's "Rename package" refactor + a clean
  rebuild) — tracked in [`BACKLOG.md`](BACKLOG.md), not required for release.

---

## BLE GATT API (minSdk 36)

The app only runs on Android 16+ (`minSdk` 36), so `BccuConnectionService.kt` uses only the
value-carrying GATT APIs (`writeDescriptor(desc, value)`, `writeCharacteristic(char, value,
writeType)`, checked against `BluetoothStatusCodes.SUCCESS`, and the 3/4-argument callbacks
`onCharacteristicChanged(g,c,value)` / `onCharacteristicRead(g,c,value,status)`). The pre-33
`setValue()` paths and the legacy callback overloads were removed; behaviour on API 33+ is
unchanged.

GATT callbacks run on a dedicated `BccuGatt` HandlerThread (`connectGatt(..., handler)`), so
they are serialised and never run on main or on concurrent binder threads. `connect()` publishes
the new `BluetoothGatt` under `gattLock`, which the first callback also takes, so it can't look
"stale" before it is assigned. All link teardown goes through `teardownLink()`.
