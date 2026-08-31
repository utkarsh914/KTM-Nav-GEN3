# Changelog

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
