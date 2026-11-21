package com.navigator.app.settings

import android.content.Context
import android.content.SharedPreferences
import android.telephony.TelephonyManager
import com.navigator.app.logging.AppLogger
import com.navigator.app.nav.model.DrivingSide
import com.navigator.app.nav.model.TravelMode
import com.navigator.app.ride.AvgSpeedMode
import com.navigator.app.ui.theme.FontMode
import com.navigator.app.ui.theme.NavThemeMode
import com.navigator.app.ui.theme.ThemeMode

/**
 * Single place for all persisted app configuration: bonded bike address,
 * notification source apps, nav-app override, and preferred call-audio device.
 * Backed by one plain SharedPreferences file (`opendash_pairing`): the app-private directory is
 * excluded from cloud backup and device transfer, and the bike's session keys deliberately stay
 * in the plain store: an encrypted store that silently resets would force a re-pair.
 */
class AppSettings(context: Context) {

    private val appContext = context.applicationContext

    // The single settings store. The "have we paired this bike before" flag and the session
    // keys MUST persist reliably, because if they don't the app re-triggers the bike's
    // physical "add device" prompt on every reconnect and the handshake stalls at
    // "handshaking" (EncryptedSharedPreferences silently resets on some devices, which is why
    // it is gone).
    private val pairingPrefs: SharedPreferences =
        context.getSharedPreferences("opendash_pairing", Context.MODE_PRIVATE)

    private val prefs: SharedPreferences get() = pairingPrefs

    /** The backing store, exposed so [com.navigator.app.data.settings.SettingsRepository] can observe changes. */
    internal val preferences: SharedPreferences get() = pairingPrefs

    // Bonded bike identity. A BLE MAC isn't a secret; a lost value throws the rider back to
    // the pairing screen ("always asking to re-pair", R21/R22 field reports), which is why this
    // never lived in an encrypted store that can silently reset.
    var bondedDeviceAddress: String?
        get() = pairingPrefs.getString(KEY_BONDED_ADDRESS, null)
        set(value) { pairingPrefs.edit().putString(KEY_BONDED_ADDRESS, value).commit() }

    var bondedDeviceName: String?
        get() = pairingPrefs.getString(KEY_BONDED_NAME, null)
        set(value) { pairingPrefs.edit().putString(KEY_BONDED_NAME, value).commit() }

    /**
     * When the bike was last linked (epoch ms, 0 = never), for "Last seen" in the bike sheet. Device-local: the
     * `bonded_` prefix keeps it out of backups ([com.navigator.app.backup.PrefsBackup.isExcluded]).
     */
    var bondedLastSeenMs: Long
        get() = pairingPrefs.getLong(KEY_BONDED_LAST_SEEN, 0L)
        set(value) { pairingPrefs.edit().putLong(KEY_BONDED_LAST_SEEN, value).apply() }

    /**
     * One-time clean-up of the retired brand choice: a rider who had picked Husqvarna and never set
     * an accent keeps blue, then the orphaned `brand` key is removed.
     */
    fun migrateRetiredBrand() {
        if (!prefs.contains(KEY_RETIRED_BRAND)) return
        val editor = prefs.edit()
        if (prefs.getString(KEY_RETIRED_BRAND, null) == "husqvarna" && accentColorId == null) {
            editor.putString(KEY_ACCENT_COLOR, "blue")
        }
        editor.remove(KEY_RETIRED_BRAND).apply()
    }

    /** Whether the first-run onboarding (name + permissions) has been completed. */
    var onboardingComplete: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_COMPLETE, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETE, value).apply()

    /**
     * Light / dark / follow-system appearance. Stored in the plain store.
     */
    var themeMode: ThemeMode
        get() = ThemeMode.fromId(pairingPrefs.getString(KEY_THEME_MODE, null))
        set(value) = pairingPrefs.edit().putString(KEY_THEME_MODE, value.id).apply()

    /**
     * App font: the bundled brand fonts (default) or the phone's system font.
     * Stored in the plain store, like [themeMode].
     */
    var fontMode: FontMode
        get() = FontMode.fromId(pairingPrefs.getString(KEY_FONT_MODE, null))
        set(value) = pairingPrefs.edit().putString(KEY_FONT_MODE, value.id).apply()

    /**
     * How the navigation/map screen picks light vs dark: follow the app theme, or
     * auto day/night by local time (default). Independent of [themeMode].
     */
    var navThemeMode: NavThemeMode
        get() = NavThemeMode.fromId(pairingPrefs.getString(KEY_NAV_THEME_MODE, null))
        set(value) = pairingPrefs.edit().putString(KEY_NAV_THEME_MODE, value.id).apply()

    /**
     * Chosen app accent. Null (default) is the KTM orange; a non-null id selects one
     * of [com.navigator.app.ui.theme.AccentPalettes] or is a custom "#RRGGBB".
     */
    var accentColorId: String?
        get() = pairingPrefs.getString(KEY_ACCENT_COLOR, null)
        set(value) {
            pairingPrefs.edit().apply {
                if (value == null) remove(KEY_ACCENT_COLOR) else putString(KEY_ACCENT_COLOR, value)
            }.apply()
        }

    /** Used to personalize the test notification and greeting text ("Hey <name>"). */
    var userName: String?
        get() = prefs.getString(KEY_USER_NAME, null)
        set(value) = prefs.edit().putString(KEY_USER_NAME, value).apply()

    /** Package names of apps whose notifications should be mirrored to the dash. */
    var notificationSourceApps: Set<String>
        get() = prefs.getStringSet(KEY_NOTIFICATION_APPS, DEFAULT_NOTIFICATION_APPS) ?: DEFAULT_NOTIFICATION_APPS
        set(value) = prefs.edit().putStringSet(KEY_NOTIFICATION_APPS, value).apply()

    /** Null = auto-detect via CATEGORY_NAVIGATION notifications. Non-null = manual override package name. */
    var navAppOverride: String?
        get() = prefs.getString(KEY_NAV_APP_OVERRIDE, null)
        set(value) = prefs.edit().putString(KEY_NAV_APP_OVERRIDE, value).apply()

    var preferredCallAudioDeviceAddress: String?
        get() = prefs.getString(KEY_CALL_AUDIO_DEVICE, null)
        set(value) = prefs.edit().putString(KEY_CALL_AUDIO_DEVICE, value).apply()

    /** Quick-mute: when false, non-navigation notifications are not pushed to the dash. */
    var mirrorEnabled: Boolean
        get() = prefs.getBoolean(KEY_MIRROR_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_MIRROR_ENABLED, value).apply()

    /** Whether the dash notification banner scrolls (marquee) when text overflows. */
    var marqueeEnabled: Boolean
        get() = prefs.getBoolean(KEY_MARQUEE_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_MARQUEE_ENABLED, value).apply()


    /**
     * Whether we've ever completed the BCCU handshake with this bike before.
     *
     * The dash shows its physical "add device" prompt purely from the app-status
     * we send in reply to its AppIdKeyArrStatus request (confirmed from the
     * official SDK's BCcuAuth.handleAuthMessage): VALID_KEY_ARRAY(1) => trusted,
     * no prompt; INITIAL_CONNECTION(0) => prompt + fresh key generation. That
     * decision is driven by whether we hold this bike's persisted key array
     * ([loadSessionKeys]), NOT by any OS-level BLE bond. This flag is a
     * secondary "known bike" marker used for handshake-timeout budgeting and
     * logging; the [storeSessionKeys]/[loadSessionKeys] array is the real
     * credential that lets a reconnect resume by key-index alone (cmd 16..31)
     * with no prompt and no re-derivation.
     */
    fun hasPairedBefore(deviceAddress: String): Boolean {
        val key = KEY_PAIRED_PREFIX + deviceAddress.uppercase()
        return pairingPrefs.getBoolean(key, false)
    }

    fun markPairedBefore(deviceAddress: String) {
        // commit() (synchronous) so the flag is on disk before the service can be
        // torn down on the disconnect that often follows authentication.
        pairingPrefs.edit().putBoolean(KEY_PAIRED_PREFIX + deviceAddress.uppercase(), true).commit()
    }

    /**
     * Forget that we've paired this bike, so the next handshake replies HELLO
     * (cmd 0) and the dash shows its physical "add device" prompt to establish
     * fresh keys. Required when the dash has lost its side of the pairing:
     * otherwise we keep replying GENERATE_KEYS against a dash that no longer
     * knows us and the handshake stalls forever at "handshaking".
     */
    fun clearPairedBefore(deviceAddress: String) {
        val key = KEY_PAIRED_PREFIX + deviceAddress.uppercase()
        // Forgetting a pairing also drops its session-key pool: the pool is only
        // meaningful together with the dash-side pairing record, and both callers
        // (user "forget", handshake self-heal after a genuine dash-side key loss)
        // want a from-scratch HELLO + GENERATE_KEYS next time.
        pairingPrefs.edit()
            .remove(key)
            .remove(KEY_SESSION_KEYS_PREFIX + deviceAddress.uppercase())
            .commit()
    }

    /**
     * Persist the session-key pool derived during the pairing handshake, keyed
     * by device MAC. The dash keeps its copy of this pool across ignition cycles
     * and resumes each later session by selecting a key from it (walking the
     * pool downward) instead of re-deriving, so the app-side copy MUST survive
     * disconnects and process death or a silent reconnect is impossible.
     *
     * Stored in the plain pairing store (like [markPairedBefore]) so it is as
     * durable as the "paired before" flag it partners, and so [clearPairedBefore]
     * drops both atomically. commit() for the same reason markPairedBefore uses
     * it: the pool must reach disk before the service can be torn down on the
     * disconnect that often follows authentication.
     */
    fun storeSessionKeys(deviceAddress: String, keys: List<ByteArray>) {
        val joined = SessionKeyCodec.encode(keys)
        pairingPrefs.edit()
            .putString(KEY_SESSION_KEYS_PREFIX + deviceAddress.uppercase(), joined)
            .commit()
    }

    /** The persisted session-key pool for this bike, or null if none stored. */
    fun loadSessionKeys(deviceAddress: String): List<ByteArray>? {
        val stored = pairingPrefs.getString(KEY_SESSION_KEYS_PREFIX + deviceAddress.uppercase(), null)
        val keys = SessionKeyCodec.decode(stored)
        if (keys == null && !stored.isNullOrEmpty()) {
            AppLogger.log("Auth", "!! stored session key pool is malformed - ignoring it")
        }
        return keys
    }

    /** Stereo beep pattern as a turn approaches (left ear = left turn). */
    var turnBeepEnabled: Boolean
        get() = pairingPrefs.getBoolean(KEY_TURN_BEEP, true)
        set(value) = pairingPrefs.edit().putBoolean(KEY_TURN_BEEP, value).apply()

    /** Turn-beep loudness, 5..100 (%). Default deliberately gentle - the R20 field test found full volume grating. */
    var beepVolumePercent: Int
        get() = pairingPrefs.getInt(KEY_BEEP_VOLUME, DEFAULT_BEEP_VOLUME)
        set(value) = pairingPrefs.edit().putInt(KEY_BEEP_VOLUME, value.coerceIn(5, 100)).apply()

    /** Swap the beep left/right channels (default on - matched the rider's headset in the field). */
    var swapBeepChannels: Boolean
        get() = pairingPrefs.getBoolean(KEY_SWAP_BEEP_CHANNELS, true)
        set(value) = pairingPrefs.edit().putBoolean(KEY_SWAP_BEEP_CHANNELS, value).apply()

    /** GPS overspeed alert on/off. */
    var overspeedEnabled: Boolean
        get() = pairingPrefs.getBoolean(KEY_OVERSPEED_ENABLED, true)
        set(value) = pairingPrefs.edit().putBoolean(KEY_OVERSPEED_ENABLED, value).apply()

    /** Speed (km/h) above which the overspeed alert fires. Alert clears at limit − 10. */
    var overspeedLimitKmh: Int
        get() = pairingPrefs.getInt(KEY_OVERSPEED_LIMIT, DEFAULT_OVERSPEED_LIMIT_KMH)
            .coerceIn(OVERSPEED_LIMIT_MIN, OVERSPEED_LIMIT_MAX)
        set(value) = pairingPrefs.edit()
            .putInt(KEY_OVERSPEED_LIMIT, value.coerceIn(OVERSPEED_LIMIT_MIN, OVERSPEED_LIMIT_MAX)).apply()

    /** Which average speed ride screens show (both are always recorded). */
    var avgSpeedMode: AvgSpeedMode
        get() = AvgSpeedMode.fromId(pairingPrefs.getString(KEY_AVG_SPEED_MODE, null))
        set(value) = pairingPrefs.edit().putString(KEY_AVG_SPEED_MODE, value.id).apply()

    /** Debug builds only: show the floating "Dash preview" chip (off by default). */
    var dashPreviewOverlay: Boolean
        get() = pairingPrefs.getBoolean(KEY_DASH_PREVIEW_OVERLAY, false)
        set(value) = pairingPrefs.edit().putBoolean(KEY_DASH_PREVIEW_OVERLAY, value).apply()

    /** Record the GPS track of every navigation by default. */
    var rideRecordingEnabled: Boolean
        get() = pairingPrefs.getBoolean(KEY_RIDE_RECORDING, true)
        set(value) = pairingPrefs.edit().putBoolean(KEY_RIDE_RECORDING, value).apply()

    /** Minimum ride distance (m) to keep; shorter rides are discarded on finish. */
    var rideMinDistanceMeters: Int
        get() = pairingPrefs.getInt(KEY_RIDE_MIN_DISTANCE, DEFAULT_RIDE_MIN_DISTANCE_M)
        set(value) = pairingPrefs.edit()
            .putInt(KEY_RIDE_MIN_DISTANCE, value.coerceIn(RIDE_MIN_DISTANCE_MIN_M, RIDE_MIN_DISTANCE_MAX_M)).apply()

    /** Minimum ride duration (s) to keep; shorter rides are discarded on finish. */
    var rideMinDurationSeconds: Int
        get() = pairingPrefs.getInt(KEY_RIDE_MIN_DURATION, DEFAULT_RIDE_MIN_DURATION_S)
        set(value) = pairingPrefs.edit()
            .putInt(KEY_RIDE_MIN_DURATION, value.coerceIn(RIDE_MIN_DURATION_MIN_S, RIDE_MIN_DURATION_MAX_S)).apply()

    /**
     * How many (unsaved) rides to keep in history. As new rides are recorded the
     * oldest unsaved ones beyond this count are pruned. Rides the rider explicitly
     * saved ([RecordedRide.saved]) are kept regardless and don't count toward it.
     */
    var rideHistoryLimit: Int
        get() = pairingPrefs.getInt(KEY_RIDE_HISTORY_LIMIT, DEFAULT_RIDE_HISTORY_LIMIT)
        set(value) = pairingPrefs.edit()
            .putInt(KEY_RIDE_HISTORY_LIMIT, value.coerceIn(RIDE_HISTORY_LIMIT_MIN, RIDE_HISTORY_LIMIT_MAX)).apply()

    /**
     * Which navigation source feeds the dash. [NAV_PROVIDER_NOTIFICATION] mirrors
     * another nav app's notifications (offline-capable, current default);
     * [NAV_PROVIDER_GOOGLE_NAV_SDK] runs Google's engine in-app (needs a key +
     * Play Services + network). Consumed by provider selection in a later phase.
     */
    var navProvider: String
        get() = pairingPrefs.getString(KEY_NAV_PROVIDER, NAV_PROVIDER_GOOGLE_NAV_SDK) ?: NAV_PROVIDER_GOOGLE_NAV_SDK
        set(value) = pairingPrefs.edit().putString(KEY_NAV_PROVIDER, value).apply()

    /** Whether the in-app Google navigation feature is enabled (vs mirror-only). */
    var googleNavEnabled: Boolean
        get() = navProvider != NAV_PROVIDER_NOTIFICATION
        set(value) { navProvider = if (value) NAV_PROVIDER_GOOGLE_NAV_SDK else NAV_PROVIDER_NOTIFICATION }

    /** Transliterate non-Latin road names / messages to Latin for the dash (its font is believed Latin-only). On by default. */
    var latinDashText: Boolean
        get() = pairingPrefs.getBoolean(KEY_LATIN_DASH_TEXT, true)
        set(value) = pairingPrefs.edit().putBoolean(KEY_LATIN_DASH_TEXT, value).apply()

    /** Show the guidance notification as an Android 16 Live Update (status-bar chip); on by default. */
    var liveUpdatesEnabled: Boolean
        get() = pairingPrefs.getBoolean(KEY_LIVE_UPDATES, true)
        set(value) = pairingPrefs.edit().putBoolean(KEY_LIVE_UPDATES, value).apply()

    /**
     * Ride-track speed colouring (Advanced): the first band (green) covers speeds
     * up to [speedBandBaseKmh]; each subsequent band spans [speedBandStepKmh] km/h
     * and shifts toward red, for [speedBandCount] bands above the base. Defaults
     * give green ≤40, then 40–60, 60–80, 80–100, 100–120 toward red.
     */
    var speedBandBaseKmh: Int
        get() = pairingPrefs.getInt(KEY_SPEED_BAND_BASE, DEFAULT_SPEED_BAND_BASE_KMH)
            .coerceIn(SPEED_BAND_BASE_MIN, SPEED_BAND_BASE_MAX)
        set(value) = pairingPrefs.edit()
            .putInt(KEY_SPEED_BAND_BASE, value.coerceIn(SPEED_BAND_BASE_MIN, SPEED_BAND_BASE_MAX)).apply()

    var speedBandStepKmh: Int
        get() = pairingPrefs.getInt(KEY_SPEED_BAND_STEP, DEFAULT_SPEED_BAND_STEP_KMH)
            .coerceIn(SPEED_BAND_STEP_MIN, SPEED_BAND_STEP_MAX)
        set(value) = pairingPrefs.edit()
            // Step size must stay >= 1 km/h to avoid a zero-width band (division by zero).
            .putInt(KEY_SPEED_BAND_STEP, value.coerceIn(SPEED_BAND_STEP_MIN, SPEED_BAND_STEP_MAX)).apply()

    var speedBandCount: Int
        get() = pairingPrefs.getInt(KEY_SPEED_BAND_COUNT, DEFAULT_SPEED_BAND_COUNT)
            .coerceIn(SPEED_BAND_COUNT_MIN, SPEED_BAND_COUNT_MAX)
        set(value) = pairingPrefs.edit()
            // A single band (count 1) is the floor; the cap keeps a typo (10 million) from
            // building millions of colour bands and freezing the ride screens.
            .putInt(KEY_SPEED_BAND_COUNT, value.coerceIn(SPEED_BAND_COUNT_MIN, SPEED_BAND_COUNT_MAX)).apply()

    /**
     * Direction-indicator style for the custom "you are here" marker on the
     * browse/preview/replay maps: "chevron" (detached arrow, default) or "beam"
     * (detached cone). See LocationMarkerStyle.
     */
    var locationMarkerStyle: String
        get() = pairingPrefs.getString(KEY_LOCATION_MARKER_STYLE, LOCATION_MARKER_CHEVRON)
            ?: LOCATION_MARKER_CHEVRON
        set(value) = pairingPrefs.edit().putString(KEY_LOCATION_MARKER_STYLE, value).apply()

    /**
     * Which side of the road the rider drives on, for the notification-mirroring
     * path (Google Maps etc. carry no driving-side info). [DrivingSidePreference.AUTO]
     * (default) infers from the device region. The in-app Google Nav SDK ignores
     * this - it reports driving side per-step from route geometry.
     */
    var drivingSidePreference: DrivingSidePreference
        get() = DrivingSidePreference.fromId(pairingPrefs.getString(KEY_DRIVING_SIDE, null))
        set(value) = pairingPrefs.edit().putString(KEY_DRIVING_SIDE, value.id).apply()

    /**
     * The resolved driving side (preference + device region for AUTO). Read live
     * so a settings change or a region change takes effect immediately.
     */
    fun resolvedDrivingSide(): DrivingSide =
        DrivingSideResolver.resolve(drivingSidePreference, telephonyCountry())

    /** Country the phone is physically in (network, else SIM), before falling back to the UI locale. */
    private fun telephonyCountry(): String? = runCatching {
        val tm = appContext.getSystemService(TelephonyManager::class.java)
        listOf(tm?.networkCountryIso, tm?.simCountryIso)
            .firstOrNull { !it.isNullOrBlank() }?.takeIf { it.length == 2 }
    }.getOrNull()

    /**
     * Vehicle used for route calculation. Drives which travel mode the route
     * preview requests (and, via the route token, the mode the Nav SDK guides
     * with). Defaults to [com.navigator.app.nav.model.TravelMode.TWO_WHEELER]
     * (bike). Stored by enum name with a safe fallback.
     */
    var vehicleMode: TravelMode
        get() = runCatching {
            TravelMode.valueOf(
                pairingPrefs.getString(KEY_VEHICLE_MODE, null)
                    ?: TravelMode.TWO_WHEELER.name,
            )
        }.getOrDefault(TravelMode.TWO_WHEELER)
        set(value) = pairingPrefs.edit().putString(KEY_VEHICLE_MODE, value.name).apply()

    /** Manual hardware-calibration result for one icon, from the Symbol Testing screen. "works", "wrong", or null (untested). */
    fun getIconTestResult(key: String): String? = prefs.getString(KEY_ICON_TEST_PREFIX + key, null)

    fun setIconTestResult(key: String, result: String) {
        prefs.edit().putString(KEY_ICON_TEST_PREFIX + key, result).apply()
    }

    /**
     * User-supplied turn-icon calibration: maps a captured notification-icon
     * bitmap hash to the correct dash TurnIcon name. Because Google Maps reuses
     * byte-identical bitmaps per maneuver, one label makes that maneuver exact
     * forever (U-turns, roundabouts, etc. that the pixel heuristic can't infer).
     * Stored in plain prefs - not secret, and must persist reliably.
     */
    fun getTurnCalibration(hash: Int): String? = pairingPrefs.getString(KEY_TURN_CAL_PREFIX + hash, null)

    fun setTurnCalibration(hash: Int, iconName: String) {
        pairingPrefs.edit().putString(KEY_TURN_CAL_PREFIX + hash, iconName).apply()
    }

    fun clearTurnCalibration(hash: Int) {
        pairingPrefs.edit().remove(KEY_TURN_CAL_PREFIX + hash).apply()
    }

    companion object {
        private const val KEY_AVG_SPEED_MODE = "avg_speed_mode"
        private const val KEY_DASH_PREVIEW_OVERLAY = "dash_preview_overlay"
        private const val KEY_BONDED_ADDRESS = "bonded_device_address"
        private const val KEY_BONDED_NAME = "bonded_device_name"
        private const val KEY_BONDED_LAST_SEEN = "bonded_last_seen_ms"

        private const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_RETIRED_BRAND = "brand"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_FONT_MODE = "font_mode"
        private const val KEY_NAV_THEME_MODE = "nav_theme_mode"
        private const val KEY_ACCENT_COLOR = "accent_color"

        private const val KEY_NOTIFICATION_APPS = "notification_source_apps"
        private const val KEY_NAV_APP_OVERRIDE = "nav_app_override"
        private const val KEY_CALL_AUDIO_DEVICE = "call_audio_device"
        private const val KEY_MIRROR_ENABLED = "mirror_enabled"
        private const val KEY_MARQUEE_ENABLED = "marquee_enabled"

        private const val KEY_TURN_BEEP = "turn_beep_enabled"
        private const val KEY_BEEP_VOLUME = "beep_volume_percent"
        const val DEFAULT_BEEP_VOLUME = 35
        private const val KEY_SWAP_BEEP_CHANNELS = "swap_beep_channels"
        private const val KEY_OVERSPEED_ENABLED = "overspeed_enabled"
        private const val KEY_OVERSPEED_LIMIT = "overspeed_limit_kmh"
        const val DEFAULT_OVERSPEED_LIMIT_KMH = 80
        private const val KEY_RIDE_RECORDING = "ride_recording_enabled"
        private const val KEY_RIDE_MIN_DISTANCE = "ride_min_distance_m"
        private const val KEY_RIDE_MIN_DURATION = "ride_min_duration_s"
        private const val KEY_RIDE_HISTORY_LIMIT = "ride_history_limit"
        const val DEFAULT_RIDE_MIN_DISTANCE_M = 400
        const val DEFAULT_RIDE_MIN_DURATION_S = 60
        const val RIDE_MIN_DISTANCE_MIN_M = 50
        const val RIDE_MIN_DISTANCE_MAX_M = 50_000
        const val RIDE_MIN_DURATION_MIN_S = 10
        const val RIDE_MIN_DURATION_MAX_S = 3_600
        const val DEFAULT_RIDE_HISTORY_LIMIT = 100
        const val RIDE_HISTORY_LIMIT_MIN = 10
        const val RIDE_HISTORY_LIMIT_MAX = 1_000
        private const val KEY_NAV_PROVIDER = "nav_provider"
        const val NAV_PROVIDER_NOTIFICATION = "notification"
        const val NAV_PROVIDER_GOOGLE_NAV_SDK = "google_nav_sdk"

        private const val KEY_LIVE_UPDATES = "live_updates_enabled"
        private const val KEY_LATIN_DASH_TEXT = "latin_dash_text"

        private const val KEY_SPEED_BAND_BASE = "speed_band_base_kmh"
        private const val KEY_SPEED_BAND_STEP = "speed_band_step_kmh"
        private const val KEY_SPEED_BAND_COUNT = "speed_band_count"
        const val DEFAULT_SPEED_BAND_BASE_KMH = 40
        const val DEFAULT_SPEED_BAND_STEP_KMH = 20
        const val DEFAULT_SPEED_BAND_COUNT = 5
        const val SPEED_BAND_BASE_MIN = 10
        const val SPEED_BAND_BASE_MAX = 120
        // Step size: floor of 1 km/h keeps bands well-defined; the ceiling stops absurd values.
        const val SPEED_BAND_STEP_MIN = 1
        const val SPEED_BAND_STEP_MAX = 100
        const val OVERSPEED_LIMIT_MIN = 30
        const val OVERSPEED_LIMIT_MAX = 200
        // Number of steps: floor of 1; the ceiling keeps a typo from building millions of bands.
        const val SPEED_BAND_COUNT_MIN = 1
        const val SPEED_BAND_COUNT_MAX = 20
        private const val KEY_LOCATION_MARKER_STYLE = "location_marker_style"
        private const val KEY_DRIVING_SIDE = "driving_side"
        private const val KEY_VEHICLE_MODE = "vehicle_mode"
        const val LOCATION_MARKER_CHEVRON = "chevron"
        const val LOCATION_MARKER_BEAM = "beam"
        private const val KEY_PAIRED_PREFIX = "paired_before_"
        private const val KEY_SESSION_KEYS_PREFIX = "session_keys_"
        private const val KEY_ICON_TEST_PREFIX = "icon_test_"
        private const val KEY_TURN_CAL_PREFIX = "turncal_"

        val DEFAULT_NOTIFICATION_APPS = setOf(
            "com.whatsapp",
            "com.google.android.apps.messaging"
        )

        val KNOWN_NAV_APPS = setOf(
            "com.google.android.apps.maps",
            "com.waze",
            "com.here.app.maps",
            "com.sygic.aura",
            "net.osmand",
            "com.kurviger.app"
        )
    }
}
