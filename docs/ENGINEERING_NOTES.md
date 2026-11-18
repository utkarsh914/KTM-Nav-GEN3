# Engineering Notes

Condensed write-ups of non-obvious decisions, bugs, and fixes that aren't self-evident from
the code alone. Companion to [`architecture.md`](architecture.md) (the current-state
reference) and [`BACKLOG.md`](BACKLOG.md) (remaining work) — this doc is a "why does it work
this way" reference, kept so the reasoning isn't lost to git history alone.

---

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
per step. Road/instruction text is logged only in debug builds (lengths otherwise) because the log
file is shareable. Also: model outputs 14/15 are treated as generic "merge" (Maps draws left/right
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
page-alignment of the uncompressed `.so` is handled by AGP 8.13 defaults
(`useLegacyPackaging=false`, no `extractNativeLibs` override, 16 KB zipalign).

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

---

## Toolchain version constraints (Kotlin 2.x / AGP 8.13.2 / compileSdk 36)

- **Compose BOM ceiling:** must stay **≤ 2026.06.01** (Compose 1.11.4) while on AGP 8.13.2 /
  compileSdk 36 — BOM 2026.08.00 pins Compose 1.12.0, which requires compileSdk 37 + AGP
  9.1.0 and fails `checkDebugAarMetadata`. Bump AGP/compileSdk together with the BOM, not
  independently.
- **Kotlin 2.x `jvmTarget` DSL:** the old `android.kotlinOptions { jvmTarget }` block is a hard
  error under Kotlin 2.3 — use the top-level
  `kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }` instead.

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
