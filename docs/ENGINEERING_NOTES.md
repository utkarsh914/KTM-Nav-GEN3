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

## BLE GATT API modernization — why deprecated paths are still present

`BccuConnectionService.kt` targets `minSdk` 26 but the API-33+ GATT callbacks/write methods use
value-carrying overloads (`writeDescriptor(desc, value)`, `writeCharacteristic(char, value,
writeType)`, checked against `BluetoothStatusCodes.SUCCESS`) that don't exist below API 33. The
file intentionally keeps **both** paths:
- API 33+ (`Build.VERSION.SDK_INT >= TIRAMISU`): the new value-carrying overloads.
- API 26–32 fallback: the deprecated `setValue()` + write path, scoped with
  `@Suppress("DEPRECATION")`.
- The two legacy GATT *callback* overrides (`onCharacteristicChanged(g,c)` /
  `onCharacteristicRead(g,c,status)`) are also kept — they're the actual dispatch path on API
  <33, not dead code.

No behavioural change on any API level; this is purely a deprecation-warning cleanup, not a
protocol-layer change. (`BluetoothAdapter.getDefaultAdapter()` was separately removed as a
fallback — the code already preferred `getSystemService(BluetoothManager).adapter`, so the
fallback was dead on minSdk 26 regardless.)
