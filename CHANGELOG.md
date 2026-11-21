# Changelog

## Unreleased

### Redesign (UI and UX, all screens)
- A new design system across the app: one set of colours, type, shapes and components, light and dark, with your choice of accent colour (any colour, with contrast checked). The riding screens have their own Day/Night mode.
- New home: a Google map with a Where-to sheet (search, Home and Work, saved and recent places), a bike pill that opens the connection sheet, and a greeting when the bike connects.
- Riding screen rebuilt for gloves and sunlight: a maneuver card with the next turn and lanes, a trip bar, and a trip sheet (add stop, overview, minimise, beeps, phone messages on the dash, dash status, end trip). The trip bar grows into the trip sheet and shrinks into an activity strip that stays above the tab bar. Landscape has its own panel.
- Plan a trip step by step: confirm a place, preview routes with alternates and stops, pick a point on the map, search with categories, voice and offline results.
- Rides: a list with day groups, search, sort, selection, delete with Undo, merge rides (with Undo), a replay with a seek bar and speed, edit and trim, and a new trip-finished screen with the ride's stats.
- Places tab (Home, Work, saved places) and a Settings tab with pages for the bike and dash, navigation, recording, sounds, permissions, appearance, backup and restore, and About.
- First run: welcome, engine choice, Google's terms, your name, permissions, pairing with the bike (with clear errors and help), and a ready screen.
- Notifications rebuilt: a Live Update for guidance (turn glyph, progress with stops, Mute beeps and End trip), a recording notification with Pause and Stop, a quiet bike-connection notification with its likely fix, and a one-time heads-up if it can't connect. Launcher shortcuts: Record a ride, Where to?, Ride home and Ride work.
- Diagnostics screens rebuilt (logs with filters and search, symbol testing, turn-icon calibration) and a debug overlay for builds.
- Motion and accessibility: transitions follow Android's "Remove animations" setting live, TalkBack labels and actions on the controls, the colour picker is adjustable by screen readers, and large-font layouts were checked.
- The brand picker and Husqvarna theming were removed: the app has one KTM identity (Husqvarna dashes still work, they share the electronics). Distances are shown in kilometres everywhere.
- Polish pass: the open trip sheet always fits under the maneuver card, rerouting shows in landscape, start and end markers on ride maps, Back from a Home/Work route goes to the map, merged rides show their parts and tell joins from pauses, failed backups leave no empty file, a restart reminder after restoring settings, and higher text contrast in the light theme.
- More polish: replay keeps real-time pace, tapping the map to seek follows the zoom, onboarding resumes where it was after the app is closed by Android, "Use my current location" asks for permission, the can't-connect alert has Re-pair and Quit, the dash notification line keeps ticking, launcher shortcuts have their own icons and a message when recording can't start, and removed navigation apps show their name.
- Logs: following live no longer switches itself off after the first scroll, and a screen reader's scroll pauses it; the trim bar's two handles are each adjustable by a screen reader; the corners of the colour panel take touches.
- Landscape: the Where-to panel is a floating card like the other landscape panels, the rail shows the bike's state on Navigate, pages without a map get rounded corners while they leave (a back swipe shrinks them), and the trim bar keeps its time order in right-to-left languages.

### Navigation
- Times follow the phone's 12 or 24 hour setting (arrival times, leg lines, the Live Update); the dash stays 24 hour.
- Multi-stop trips: add intermediate stops; in-app guidance continues past each stop to the final
  destination.
- Retrained the on-device maneuver model with the corrected labels (all 64 Google Maps icons
  classified correctly on-device). Merge icons, which Maps draws identically for left/right, are one
  class resolved by driving side; direction-less roundabout icons give no arrow.

### Fixes
- Reconnect robustness: with Bluetooth off at launch, the app could wait forever to "hear" the dash advertise (a dash already linked for media/calls may not) and ignore the dash's own link coming up. The scan is now bounded (45 s, balanced power), then falls back to a direct connect (phone-bonded dash) or a background connect (up to 10 min before recycling). The bike's own link coming up cuts a scan or a parked background connect short and connects directly, without overriding a handshake retry cooldown.
- Merge (enter-highway) arrows now point the right way. Verified on the dash: code 14 is a ramp on
  the right bending left, code 15 a ramp on the left bending right. "Merge left" / the SDK's
  `MERGE_LEFT` was sent as the mirrored glyph; it now uses code 14 (and "merge right" code 15).
  Unqualified merges still follow the driving side. The phone's merge icons now look like the dash's.
- If the in-app engine reports a merge but its instruction says to turn ("Turn left onto the ramp
  to NE 7"), the phone and dash show the turn arrow instead of the merge glyph.
- In-app navigation logs each step's raw Google maneuver and the icon chosen, to make mismatches
  traceable from the Logs screen (road/instruction text only in debug builds).
- "Merge onto Left Bank Expy" no longer reads the road name as the merge side; the "Then" hint uses
  your driving side; on-device model merge output follows the driving side.
- If you picked a merge icon (14/15) on the Turn Calibration screen, re-check it: the previews now
  match what the dash draws.
- Highway on-ramps now show the real turn on the dash. "Sharp right onto the ramp to NH 48" was being
  sent as the enter-highway glyph, whose arrow leans *left*; on-ramps now map to the graded turn arrow
  (slight / normal / sharp), matching the notification-text and on-device-model paths. Sharp off-ramps
  likewise show the sharp-turn arrow. An unqualified SDK merge now picks the enter-highway lane from
  the driving side (was: straight arrow). Merge-direction selection is centralised in one helper.
- Roundabouts when mirroring Google Maps now use the hardware-verified exit-angle glyphs (the
  in-app path already did). "Take the Nth exit" no longer maps to section N (which showed a sharp
  right for most roundabouts); the Maps icon is preferred, else the ordinal is converted to an
  approximate angle for the driving side. The bundled model's mis-labelled roundabout classes are
  corrected at runtime (now dormant with the retrained model), and the training script is fixed.
- Starting a second in-app trip no longer bounces straight back to notification mirroring (the
  previous trip's "stopped" state was mistaken for the new trip ending). In-app guidance is picked
  up when the bike connects mid-trip, switching sources clears the old guidance, and the service
  shutting down no longer cancels an in-app trip.
- The dash never shows the previous step's arrow for an unrecognised new turn (it's blanked), and a
  new arrow always arrives with its own distance. Throttled updates are flushed, and a phone clock
  change can no longer freeze distance/ETA.
- Notification parsing matches whole words ("Rampura", "Leftwich Rd" no longer trigger maneuvers),
  "Turn left to stay on…" shows a turn, unqualified U-turns follow the driving side, ETAs with
  localized AM/PM parse correctly, and imperial distances fit the dash ("0.3 mi", not "0.3 m...").
- Bluetooth: a lost or failed write can no longer freeze the dash — the write queue self-heals,
  failed guidance writes trigger a resync, and guidance is refreshed every 15 s while navigating.
  The connect greeting no longer overwrites or blanks live guidance.
- Text is truncated on whole characters (no broken emoji or Kannada conjuncts), 9.95–9.99 km reads
  "10 km", the app shows kilometres everywhere (the dash path is unchanged), and the ETA follows time-zone changes.

### Under the hood
- Build toolchain: Gradle 9.9.1, Android Gradle Plugin 9.5.1 (built-in Kotlin), Kotlin 2.4.21. The
  Gradle daemon JDK (25) is pinned in the project and downloaded automatically, so Android Studio
  and the command line share one daemon.
- Libraries: Navigation SDK 8.1.0, LiteRT 2.3.2, Compose BOM 2026.09.00, AndroidX (core-ktx 1.19.1,
  appcompat 1.8.0, activity-compose 1.13.0, lifecycle 2.11.0), Material 1.14.0, Robolectric 4.18.1.
  compileSdk 37.2 (targetSdk stays 36).
- In-app navigation starts the previewed route with the Navigation SDK's token-only API, so the
  route's stops and travel mode always come from the route you picked. If Google ignores the
  picked route, you now get the "using the recommended route" notice instead of a silent swap.

## v0.6.0

### Ride replay
- Much smoother zoom on long rides: the recorded track is now simplified before it's drawn
  (speed-band hysteresis + micro-run merging + Douglas-Peucker + a vertex cap), collapsing the
  thousands of tiny coloured polylines a 1–2 h ride used to produce (which the map re-tessellated on
  every zoom frame) down to a handful — a ~95% reduction with no visible change to the track.
- Track detail control: an in-replay slider to trade line detail for zoom smoothness on very long
  rides (Off → 30 m). Not persisted; resets to the default each time you open a ride.
- Redesigned bottom info card: shorter, and restyled to match the ride-list cards (equal-width
  DISTANCE / TIME / AVG / MAX columns that never wrap), with the current time-of-day folded inline
  above the seek bar.
- Speed and Track-detail are now slider popovers you can **press-and-hold then slide** (or tap to
  open) right above their buttons, with a haptic tick per value change; the two are mutually
  exclusive. Default playback speed is now 4× (was 1×).

### Navigation
- Driving-side–aware maneuver symbols on the mirrored-notification path: an unqualified "merge"/
  "exit" and roundabout glyphs now follow the rider's driving side instead of assuming right-hand
  traffic — correct for left-hand-traffic countries (e.g. India). New "Drive on" setting
  (Auto / Left / Right; Auto infers from the device region). Enter-highway icons are now directional.

### Fixes
- Ride-replay compass heading no longer recomposes the whole screen on every sensor tick (~16 Hz);
  the map marker updates off the composition path, removing background jank while viewing a ride.

---

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
- Smoother pinch-zoom: the map no longer stutters while zooming/panning. Camera target/zoom
  state now settles when the gesture ends instead of being rewritten (and re-laying-out the
  overlay) on every frame.

### Ride history
- Configurable speed-to-colour mapping: set the green ceiling, step size and number of bands (green→red
  ramp) in Settings → Advanced, with a live legend preview. Defaults to green ≤40, then steps every
  20 km/h toward red; no upper cap on the number of bands.

### Appearance
- App font choice: keep the branded fonts (default) or switch the whole app to the phone's system
  default font, in Settings → Appearance → App font. Applies live across every screen.

### Settings
- Visual overhaul: branded hero header with live vehicle status, iconified group cards, and a new
  Advanced section for the options above.

### Fixes
- The custom location marker no longer disappears after opening route preview and returning to the map
  (previously required an app restart); it now survives shared-map clears.
- Trips now end reliably on arrival: the proximity auto-finish radius was widened (10 m → 40 m) so GPS
  scatter or a destination set off the road no longer leaves a trip running, and a new idle fallback
  finishes a trip that stalls near the destination when the SDK's arrival callback never fires (e.g.
  parked just short of the point, or guidance stuck mid-reroute offline).
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
