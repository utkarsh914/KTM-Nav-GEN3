# Changelog

## v0.5.0

### Navigation & maps
- Compass-driven location marker: the "you are here" dot now shows your heading from the phone's
  orientation sensor and rotates as you physically turn (like the Google Maps app), on the browse map,
  route preview and ride replay — the Navigation SDK's own puck only follows GPS course. Direction
  indicator style is selectable (detached arrow / beam) in Settings → Advanced.
- Choice of navigation UI: keep the app's custom guidance overlays (default) or switch to Google's full
  stock navigation UI (maneuver header, ETA card, speedometer) in Settings → Advanced.

### Ride replay
- Tap the ride track to jump playback to the nearest recorded point.
- Shows the wall-clock time of the current playback instant as the ride plays/scrubs.

### Ride history
- Configurable speed-to-colour mapping: set the green ceiling, step size and number of bands (green→red
  ramp) in Settings → Advanced, with a live legend preview. Defaults to green ≤40, then steps every
  20 km/h toward red; no upper cap on the number of bands.

### Settings
- Visual overhaul: branded hero header with live vehicle status, iconified group cards, and a new
  Advanced section for the options above.

### Fixes
- The custom location marker no longer disappears after opening route preview and returning to the map
  (previously required an app restart); it now survives shared-map clears.
- Full Google-nav UI: the SDK chrome and its follow-camera are now laid out inside the system-bar safe
  area (fixes the skew/overlap that appeared under the status/navigation bars), custom controls are moved
  clear of the SDK header/ETA, and the map day/night no longer re-applies on every nav entry (removes a
  start-of-navigation flash).

---

## v0.4.0

### Ride recording & replay
- On-device GPS ride recording, on by default — records every trip's track (position, time, speed). No API calls, no billing impact.
- (Ride recording was removed in v0.3.0's lean-down; it's back, rebuilt from scratch and richer.)
- Rides history screen: speed-coloured track thumbnails, per-ride stats (distance/duration/top speed), search by place, sort by field + direction, sticky day-group headers, long-press multi-select with bulk delete, and save/pin.
- Ride Replay screen: animated playback up to 64x, tap-to-seek and drag-to-scrub, follow toggle, zoom-to-current, and north-reset compass.
- Configurable min distance/duration cutoffs (default 400 m / 60 s) and "keep last N rides" (10–1000, default 100); saved/pinned rides are exempt from pruning.
- Crash-safe streaming track storage with orphan recovery on launch.

### Navigation
- Offline resilience: guidance keeps running offline from the already-computed route; an offline banner shows during active nav and notes rerouting is paused until back online.
- Per-route live traffic delay in route preview — colour-coded "+N min" on each route (no extra API calls, no billing-tier change).
- Leave the active-navigation screen without ending the trip (Back/minimise returns to the map with a "Resume navigation" pill); confirm before ending.
- Richer Trip Finished screen on manual end and auto-arrival: duration, avg/max speed, the ride route drawn on the map, and "Open in Replay".
- Bike-connection button now reachable mid-trip.
- Fixed navigation resuming after arrival while still moving (phone locked).

### Connectivity
- Process-wide online/offline detection (captive/dead Wi-Fi reads as offline).
- Reflects offline state to the dash: flips the nav status bit in NAVIGATION_STATE and posts a one-shot "Offline" warning notification, cleared on reconnect.
- Ride GPS track keeps recording even when the initial route can't be computed offline.

### UX/UI
- Custom app accent colour, independent of the bike brand: presets plus a full HSV colour picker (Settings → Appearance).
- Screen and stage transition animations: direction-aware forward/back route changes, search-panel slide, and stage cross-fades.
- Shared design system (Motion, Haptics, text-style tokens) applied across screens; consistent map controls (recenter + compass) across the map home, active navigation and ride replay.
- Unified rides and saved-places lists (shared multi-select, delete-confirm and card chrome); Settings preserves scroll position when returning from a sub-section; reordered Settings groups.

### Under the hood
- Large refactor: split the two largest files, removed dead code and unused subsystems (~2600 lines net), and de-duplicated shared HTTP/hash/dash-write helpers.
- New JVM unit tests: ride metrics, ride JSON round-trip, route traffic-delay math, and encoder offline-flip cases.

### Known issues
1. First pairing needs the physical "add device" confirmation on the dash. Expected, once per bike; every reconnect after that is silent.
2. Turn-icon accuracy depends on your Google Maps version and your specific dash. Use the Symbol Test and Turn-icon Calibration screens if an icon looks wrong.
3. Husqvarna support has never been verified on a physical Husqvarna. It runs on the same dash protocol and should work, but all testing so far has been on a KTM.
4. Full vehicle telemetry (RPM, gear, coolant, fuel, TPMS) exists in the protocol but the dash rejects the writes — not available.
5. This is a beta. If anything crashes or misbehaves, please open an issue or share your logs from Settings → Diagnostics.

---

## v0.3.0

### Navigation
- In-app Google Maps navigation with turn-by-turn guidance on the dash
- Phone-first map home with live location, search bar, and bike connection pill
- Route preview with alternate routes; on-phone guidance (maneuver, ETA, distance, speed)
- Long-press map to drop a destination pin
- Google Maps link resolver, paste-to-navigate, and share-to-app
- Search with recents/favorites; saved places manager (Home/Work)
- Auto-finish trip on arrival with Trip Finished screen
- Day/night nav theme

### UX/UI
- Navigation-only app: two engines (In-app maps / Notification mirror) in Settings
- Notification mirror home with pause/resume sending to dash
- Shows over lock screen + keeps screen awake while navigating
- Tapping nav notification returns to the active nav screen
- Consistent design language (circular back buttons, orange labels, cards/pills)
- Light/dark theming, subtle transitions, press feedback

### Bluetooth
- Fixed dash re-prompting on every reconnect (correct AppIdKeyArrStatus reply)
- Faster, stabler reconnect (cut settle grace, gate diagnostics probe)

### Build
- Rebrand to "KTM Navigator"
- Modern toolchain: Kotlin 2.3 / AGP 8.13.2 / SDK 36
- Google Navigation SDK 7.9.0
- 16 KB page-size compliance (Android 15+); BLE API modernisation

### Removed
- GPX ride recording
- Engine detect + power saver
- Handlebar remote / media / call handling
- Debug/Waypoint/Gemini settings

---

## v0.2.0-beta - Navigator Gen3 (formerly OpenDash)

This is a large update over the first public release (v0.1.0), and it is a beta. Please expect a few
rough edges and send logs if something misbehaves.

### About the name

The app is now called Navigator Gen3 (it was OpenDash at v0.1.0). The old name overlapped with
existing projects, which was going to cause confusion. Thanks to u/SubtleAsFucc on Reddit for
flagging it early. Same app, same maintainer, same on-device, no-account approach - just a clearer
name.

### New since v0.1.0

**Husqvarna support (new).** This is the big one. v0.1.0 was KTM only. Navigator Gen3 now also works
with Husqvarna Gen-3 models, which share the same dash electronics, so the same navigation,
notifications, sounds and ride features all work. The app themes itself to match your bike - KTM in
the dark orange look, Husqvarna in a lighter blue look - picked on first run and switchable any time
from Settings.

**Sound.**
- Stereo turn-approach beeps: a beep in your left ear for a left turn, your right ear for a right
  turn, with the tempo tightening as you close in on the corner.
- The beeps drop to a quiet hum when you are stopped (at a light, or with the kill switch off) and
  come back the moment you move off, so they do not nag while you wait.
- Adjustable beep volume, a mellower tone, and a left/right channel swap for headsets that are wired
  the other way.
- A much louder overspeed alarm - it now plays on the alarm audio channel at full volume and sounds
  through Do Not Disturb.
- You can preview the beeps and the overspeed alarm from Settings before you ride.

**Ride recording and the ride viewer (GPX).**
- Optional automatic ride recording to a standard GPX track. It is power-aware: full detail on the
  bike's charger, gentle on the battery otherwise, and it can be turned off entirely.
- A new Rides screen draws each ride as a map coloured by speed (fast in red, mid-range in green,
  easy cruising in blue, crawling in near-black), plus a speed-over-time graph and ride stats.
- A rough traffic readout: when you are stopped with the engine running it is marked as traffic; with
  the engine off it is treated as an intentional stop, so the summary shows time lost to traffic.
- You can open a .gpx file from any other app to view it here, and share your recorded rides out.

**Engine detection.** The app can tell whether the engine is actually running from the phone's motion
sensor, after a short guided calibration. This is what lets the beeps go quiet at a light and lets
the ride viewer tell traffic from a coffee stop.

**Handlebar remote and notifications.**
- The handlebar remote now has selectable modes - media control, phone gamepad, or the app's own
  menu - switched with a triple-press of Up, and the gamepad highlights the actual buttons more
  reliably.
- Group chats and "5 new messages" summaries are filtered out, so only real, individual messages
  reach the dash.
- The persistent notification has an Exit action that genuinely stops the app.

**Reconnection.** The reconnect logic was reworked: it waits for the dash to finish waking after the
ignition comes on, then backs off instead of hammering the link, which cuts down the repeated
re-pair prompts. It is better, but not fully solved - see Known issues.

**Setup and stability.**
- Reworked first-run walkthrough with a "skip all" option.
- Your paired bike and brand choice are now stored somewhere that survives app updates, so you should
  not get thrown back to the pairing screen after every install.

**Turn icons.** The on-device turn-icon recognition works as it did in v0.1.0 (same model). The
training dataset is now included in the repo for anyone who wants to help improve it.

### Known issues

1. First pairing still needs the physical "add device" confirmation on the dash. Expected, once per
   bike.
2. Turn-icon accuracy depends on your Google Maps version and your specific dash. Use the Symbol Test
   and Turn-icon Calibration screens if an icon looks wrong.
3. Husqvarna support has not yet been verified on a physical Husqvarna. It should work on the shared
   dash protocol, but testing so far has only been on a KTM.
4. This is a beta - if anything crashes or misbehaves, please open an issue or share your logs.

---

## v0.1.0 - OpenDash

First public release. Google Maps turn-by-turn mirrored to the KTM Gen-3 dash, on-device maneuver-
icon recognition, notification mirroring, handlebar remote as a gamepad, and symbol/turn-icon
calibration. KTM only.
