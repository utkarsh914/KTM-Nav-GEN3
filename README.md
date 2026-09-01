<p align="center">
  <img src="docs/logo.png" width="128" alt="KTM Navigator logo"/>
</p>

<h1 align="center">KTM Navigator</h1>

<p align="center">
  <b>Turn-by-turn navigation and notifications on your KTM or Husqvarna Gen-3 dashboard — free, private by default, with a phone-first map and an offline notification-mirroring mode.</b>
</p>

<p align="center">
  <i>An independent, open-source companion app. Not affiliated with, or endorsed by, KTM or Husqvarna.</i>
</p>

<p align="center">
  <a href="../../releases/latest"><b>Download the latest APK</b></a>
</p>

---

## What it does

A navigation-only app, all on your phone, with no account and no server of ours. Pick one of
two mutually-exclusive engines in **Settings → Navigation engine**:

| | |
|---|---|
| **In-app maps (Google)** | A phone-first map home: search, long-press to drop a pin, or share a Google Maps link, then preview the route (with alternates) and run turn-by-turn on the phone *and* the bike's dash over Bluetooth. |
| **Notification mirror** | Navigate in Google Maps as usual; the app mirrors its turn-by-turn to the dash (offline-capable), with a pause/resume control. Also the home when no Google API key is present. |

Plus phone-notification mirroring to the dash and a rotating idle screen.

---

## What works today

**Silent auto-reconnect.** Once paired, cycling the ignition off and on reconnects on its own in about a
second — no accepting an "add device" prompt on the dash every ride. The app remembers your pairing keys
and re-presents them, and keeps them across app updates, reinstalls and new phones.

**Google Maps turn-by-turn, on your bike's dash.** Start navigating and the Gen-3 centre display shows
the turn arrow, distance, road name, ETA and remaining distance. The maneuver arrow — including
roundabout exits, U-turns, keep/fork and ramps — is recognised by a small on-device neural network
trained on Google Maps' own icons, because Maps puts no maneuver *text* in its notification.

**Automatic bike detection.** On connect it reads the dash's model, firmware and capabilities and shows
what it found in the connection banner. Nothing to configure.

**KTM and Husqvarna.** They share the same dash electronics, and the whole app re-themes to match your
bike — KTM dark and orange, Husqvarna light and blue. You pick on first run and can switch any time.

| Brand select (KTM) | Brand select (Husqvarna) |
|---|---|
| ![](docs/screenshots/brand-select-ktm.png) | ![](docs/screenshots/brand-select-husqvarna.png) |

**Phone-first map home.** Open the app to a full Google map with your live location, a bike-connection
pill and a search bar. Find a destination by search (with Recents and Home/Work/Saved shortcuts), by
**long-pressing the map to drop a pin**, or by sharing a Google Maps link into the app; confirm it, preview
the route with selectable **alternates**, then Start to run turn-by-turn on the phone and the dash at once.

**Shows over the lock screen.** While navigating, the app stays visible over the lock screen and keeps the
screen awake — and tapping either navigation notification jumps straight back to the live nav screen.

**Turn sounds.** Optional stereo approach beeps — left ear for a left turn, right for a right — that
speed up as you near the corner and go quiet when you stop. Adjustable volume, swappable channels.

**Overspeed alert.** Plays on the alarm channel at full volume, so it's audible over wind and engine and
sounds through Do Not Disturb.

**The dash's idle screen.** When you're not navigating, the dash rotates clock and date, and live weather
(via Open-Meteo, no API key).

**Auto-finish on arrival.** When you reach your destination the app ends navigation automatically and shows
a Trip Finished screen — no manual dismissal needed.

**Day/night navigation theme.** The in-app map and guidance UI switch between day and night styles
automatically based on sunrise/sunset, or you can lock them to follow your app theme.

**Notification mirroring** with group chats and summary notifications filtered out so only real messages
get through, emoji and unsupported glyphs stripped so text reads cleanly, and a one-tap quick-mute.

**Diagnostics that earn their keep:** symbol testing and turn-icon calibration to match glyphs to your
exact dash, a per-slot dash text playground, and shareable logs — which is how bug reports get made.

---

## Privacy — private by default; one clearly-marked online mode

- No accounts of ours. No analytics. No data collection. No ads. No server of ours.
- The **Notification-mirror engine** runs on-device: the turn-icon model and notification handling stay
  on the phone; when you navigate in Google Maps, the app only reads that app's on-screen notification and
  forwards the text over Bluetooth — nothing of ours leaves the phone.
- The **In-app maps engine** *does* go online. It needs your own Google Maps Platform API key + Google
  Play Services; entering a destination runs Google's Navigation SDK, which means your **destination and
  location are sent to Google**, and it needs internet. Pick the engine any time in
  **Settings → Navigation engine** (the two are mutually exclusive). While it's navigating you'll also see
  Google's own notification alongside the app's.
- Other optional, opt-in online bit: live weather for the dash idle screen (Open-Meteo, no key, coarse
  location only), off by default.

---

## Compatibility and setup

- Tested on a **KTM 390 Adventure (2025)**. It targets the Gen-3 "connected" dash that KTM's and
  Husqvarna's own apps use for turn-by-turn, so it should work on other 2020+ Gen-3 bikes — but 1290 /
  890 / SMC and the Husqvarna models are still lightly tested. Reports and logs are very welcome.
- You likely need the connectivity / Tech Pack active. The turn-by-turn dash feature appears to be gated
  behind that activation; without it the dash may not expose the nav feature to pair with. Not fully
  confirmed — testers with and without it, please report.
- **Pairing tips** if the bike doesn't show up: put the dash into "add device" mode from its own
  connectivity menu first, make sure Bluetooth is on, and note that the app also lists devices already
  bonded to your phone. The first pairing needs the physical "add device" confirmation on the dash.
- **Android 16:** sideloaded apps have notification access blocked until you enable "Allow restricted
  settings" (App info → three-dot menu) before granting it.

## Known issues

1. First pairing needs the physical "add device" confirmation on the dash. Expected, once per bike; every
   reconnect after that is silent.
2. Turn-icon accuracy depends on your Google Maps version and your specific dash. If an icon looks wrong,
   correct it with the Symbol Test and Turn-icon Calibration screens.
3. Husqvarna support has never been verified on a physical Husqvarna. It runs on the same dash protocol
   and should work, but all testing so far has been on a KTM.
4. The in-app maps engine needs a Google Maps Platform API key added at build time (below) plus Google
   Play Services; without a key the app runs in the offline notification-mirror engine.
5. Route-token alternate selection is best-effort: when stationary the Nav SDK may road-snap and recompute
   to the fastest route for some alternates. Expected to be reliable when actually riding.
6. Full vehicle telemetry (RPM, gear, coolant, fuel, TPMS) exists in the protocol but the dash rejects
   the writes. It is **not** available — help welcome.
7. This is a beta. If something crashes or misbehaves, please open an issue or share your logs from
   Settings → Diagnostics, and include your bike model and firmware if you can.

---

## Install (no build needed)

Grab the APK from the [latest release](../../releases/latest), copy it to your phone and open it (you'll
need to allow "install from unknown sources"). First-ever pairing needs the physical "add device"
confirmation on the bike's dash.

## Build it yourself

Prerequisites: Android Studio (latest) or the command-line Android SDK; JDK 17 (bundled with Android
Studio); Android SDK Platform 34 and build-tools.

```bash
git clone <this-repo>
cd <this-repo>

# point Gradle at your SDK (either export this or create local.properties)
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties   # macOS
# echo "sdk.dir=$HOME/Android/Sdk"        > local.properties   # Linux

./gradlew :app:assembleDebug
# -> app/build/outputs/apk/debug/app-debug.apk
```

Install to a connected phone with `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

**Optional — the in-app maps engine.** Get a free key at console.cloud.google.com, enable "Maps SDK for
Android" (and the Navigation SDK / Routes API), restrict it to package `com.navigator.ktm` plus your
keystore's SHA-1, then add `MAPS_API_KEY=your-key` (and `NAV_SDK_API_KEY=your-key`) to `local.properties`
before building.

On the phone: enable **Notification access** (required to mirror navigation and notifications) and disable
battery optimisation so the app stays connected in the background.

## The turn-icon model (`ml/`)

The classifier is trained from scratch on Google Maps' own publicly shipped, self-labeled maneuver icons
— no proprietary weights. `ml/train_maneuver_model.py` reproduces
`app/src/main/res/raw/maneuver_model.tflite` locally or on free Google Colab. The labeled dataset is
generated on-device by a debug exporter (see [`ml/README.md`](ml/README.md)).

## Contributing — help wanted

This is an early, community-driven hobby project and contributions are very welcome, whether you ride one
of these bikes or just enjoy reverse-engineering and Android/BLE work. Good places to start:

- **Full vehicle telemetry** over the dash's PRPC channel — the service is present but the writes are
  rejected.
- **Older "MY RIDE" bikes** need a second connection path (Bluetooth Classic + JSON).
- **Test on a physical Husqvarna** and report how it goes.
- Improve the turn-icon model with real captured icons.
- Testing on other Gen-3 bikes and firmware, and reporting what works.
- UI polish, docs, translations.

Open an [issue](../../issues) with logs and details, or send a pull request. Please keep the project's
core promise intact: **on-device only, no data collection.**

## Disclaimer

Unofficial hobby project for Gen-3 KTM and Husqvarna dashes, provided as-is with no warranty. Not
affiliated with KTM or Husqvarna. Ride responsibly — do not interact with your phone while riding.

## License

[MIT](LICENSE)
