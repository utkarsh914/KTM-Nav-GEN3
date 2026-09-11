package com.navigator.app.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Single place for all persisted app configuration: bonded bike address,
 * notification source apps, nav-app override, and preferred call-audio device.
 * Backed by EncryptedSharedPreferences (pairing keys are secrets worth
 * protecting at rest).
 */
class AppSettings(context: Context) {

    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "opendash_settings",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // Plain (unencrypted) prefs for non-secret pairing state. EncryptedSharedPreferences
    // is deprecated and prone to silent corruption/reset on some devices; the
    // "have we paired this bike before" flag MUST persist reliably, because if it
    // doesn't the app re-triggers the bike's physical "add device" prompt on every
    // reconnect and the handshake stalls at "handshaking". None of this is secret.
    private val pairingPrefs: SharedPreferences =
        context.getSharedPreferences("opendash_pairing", Context.MODE_PRIVATE)

    // Bonded bike identity lives in the PLAIN store: EncryptedSharedPreferences
    // silently resets on some devices/updates (documented on pairingPrefs), and
    // every reset threw the rider back to the pairing screen ("always asking to
    // re-pair", R21/R22 field reports - observed directly after the R21 update).
    // A BLE MAC isn't a secret. Reads fall back to the legacy encrypted store
    // once, migrating bikes paired by older builds; writes clear the legacy copy
    // so a stale encrypted value can't resurrect after "Re-pair vehicle".
    var bondedDeviceAddress: String?
        get() = pairingPrefs.getString(KEY_BONDED_ADDRESS, null)
            ?: runCatching { prefs.getString(KEY_BONDED_ADDRESS, null) }.getOrNull()?.also {
                pairingPrefs.edit().putString(KEY_BONDED_ADDRESS, it).apply()
            }
        set(value) {
            pairingPrefs.edit().putString(KEY_BONDED_ADDRESS, value).commit()
            runCatching { prefs.edit().remove(KEY_BONDED_ADDRESS).apply() }
        }

    var bondedDeviceName: String?
        get() = pairingPrefs.getString(KEY_BONDED_NAME, null)
            ?: runCatching { prefs.getString(KEY_BONDED_NAME, null) }.getOrNull()?.also {
                pairingPrefs.edit().putString(KEY_BONDED_NAME, it).apply()
            }
        set(value) {
            pairingPrefs.edit().putString(KEY_BONDED_NAME, value).commit()
            runCatching { prefs.edit().remove(KEY_BONDED_NAME).apply() }
        }

    /** Whether the first-run onboarding (name + permissions) has been completed. */
    var onboardingComplete: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_COMPLETE, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETE, value).apply()

    /**
     * Selected motorcycle brand — drives the whole app's theme (KTM dark/orange,
     * Husqvarna light/blue). Plain store so it survives the encrypted-prefs reset
     * that also wiped the bonded bike (see [bondedDeviceAddress]).
     */
    var brand: com.navigator.app.ui.theme.Brand
        get() = com.navigator.app.ui.theme.Brand.fromId(pairingPrefs.getString(KEY_BRAND, null))
        set(value) = pairingPrefs.edit().putString(KEY_BRAND, value.id).apply()

    /** True once the rider has explicitly picked a brand (so first-run shows brand select). */
    var brandChosen: Boolean
        get() = pairingPrefs.contains(KEY_BRAND)
        set(value) { if (!value) pairingPrefs.edit().remove(KEY_BRAND).apply() }

    /**
     * Light / dark / follow-system appearance. Independent of brand (which now
     * only supplies the accent). Plain store so it survives the encrypted-prefs
     * reset, like [brand].
     */
    var themeMode: com.navigator.app.ui.theme.ThemeMode
        get() = com.navigator.app.ui.theme.ThemeMode.fromId(pairingPrefs.getString(KEY_THEME_MODE, null))
        set(value) = pairingPrefs.edit().putString(KEY_THEME_MODE, value.id).apply()

    /**
     * How the navigation/map screen picks light vs dark: follow the app theme, or
     * auto day/night by local time (default). Independent of [themeMode].
     */
    var navThemeMode: com.navigator.app.ui.theme.NavThemeMode
        get() = com.navigator.app.ui.theme.NavThemeMode.fromId(pairingPrefs.getString(KEY_NAV_THEME_MODE, null))
        set(value) = pairingPrefs.edit().putString(KEY_NAV_THEME_MODE, value.id).apply()

    /**
     * Chosen app accent, independent of the bike brand. Null (default) follows the
     * brand's own accent (KTM orange / Husqvarna blue); a non-null id selects one
     * of [com.navigator.app.ui.theme.AccentPalettes]. Plain store so it survives
     * the encrypted-prefs reset, like [brand]/[themeMode].
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
        // Check the reliable plain store first, then fall back to the legacy
        // encrypted store so bikes paired by older builds aren't re-prompted.
        return pairingPrefs.getBoolean(key, false) ||
            runCatching { prefs.getBoolean(key, false) }.getOrDefault(false)
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
     * knows us and the handshake stalls forever at "handshaking". Clears both
     * the plain and legacy-encrypted stores.
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
        runCatching { prefs.edit().remove(key).commit() }
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
        val joined = keys.joinToString(",") { hex(it) }
        pairingPrefs.edit()
            .putString(KEY_SESSION_KEYS_PREFIX + deviceAddress.uppercase(), joined)
            .commit()
    }

    /** The persisted session-key pool for this bike, or null if none stored. */
    fun loadSessionKeys(deviceAddress: String): List<ByteArray>? {
        val stored = pairingPrefs.getString(KEY_SESSION_KEYS_PREFIX + deviceAddress.uppercase(), null)
        if (stored.isNullOrEmpty()) return null
        return stored.split(",").map { unhex(it) }
    }

    private fun hex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }

    private fun unhex(s: String): ByteArray =
        ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

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
        set(value) = pairingPrefs.edit().putInt(KEY_OVERSPEED_LIMIT, value).apply()

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

    /**
     * When true, active navigation shows Google's own stock guidance chrome
     * (maneuver header, ETA card, speedometer) instead of the app's custom
     * overlays. Default false keeps the app-rendered look. (Advanced setting.)
     */
    var fullSdkNavUi: Boolean
        get() = pairingPrefs.getBoolean(KEY_FULL_SDK_NAV_UI, false)
        set(value) = pairingPrefs.edit().putBoolean(KEY_FULL_SDK_NAV_UI, value).apply()

    /**
     * Ride-track speed colouring (Advanced): the first band (green) covers speeds
     * up to [speedBandBaseKmh]; each subsequent band spans [speedBandStepKmh] km/h
     * and shifts toward red, for [speedBandCount] bands above the base. Defaults
     * give green ≤40, then 40–60, 60–80, 80–100, 100–120 toward red.
     */
    var speedBandBaseKmh: Int
        get() = pairingPrefs.getInt(KEY_SPEED_BAND_BASE, DEFAULT_SPEED_BAND_BASE_KMH)
        set(value) = pairingPrefs.edit()
            .putInt(KEY_SPEED_BAND_BASE, value.coerceIn(SPEED_BAND_BASE_MIN, SPEED_BAND_BASE_MAX)).apply()

    var speedBandStepKmh: Int
        get() = pairingPrefs.getInt(KEY_SPEED_BAND_STEP, DEFAULT_SPEED_BAND_STEP_KMH)
        set(value) = pairingPrefs.edit()
            // Step size must stay ≥1 km/h to avoid a zero-width band (division by
            // zero); otherwise unbounded.
            .putInt(KEY_SPEED_BAND_STEP, value.coerceAtLeast(SPEED_BAND_STEP_MIN)).apply()

    var speedBandCount: Int
        get() = pairingPrefs.getInt(KEY_SPEED_BAND_COUNT, DEFAULT_SPEED_BAND_COUNT)
        set(value) = pairingPrefs.edit()
            // No upper cap on the number of steps; a single band (count 1) is the
            // floor so there's always at least one colour transition.
            .putInt(KEY_SPEED_BAND_COUNT, value.coerceAtLeast(SPEED_BAND_COUNT_MIN)).apply()

    /**
     * Direction-indicator style for the custom "you are here" marker on the
     * browse/preview/replay maps: "chevron" (detached arrow, default) or "beam"
     * (detached cone). See LocationMarkerStyle.
     */
    var locationMarkerStyle: String
        get() = pairingPrefs.getString(KEY_LOCATION_MARKER_STYLE, LOCATION_MARKER_CHEVRON)
            ?: LOCATION_MARKER_CHEVRON
        set(value) = pairingPrefs.edit().putString(KEY_LOCATION_MARKER_STYLE, value).apply()

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
        private const val KEY_BONDED_ADDRESS = "bonded_device_address"
        private const val KEY_BONDED_NAME = "bonded_device_name"

        private const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_BRAND = "brand"
        private const val KEY_THEME_MODE = "theme_mode"
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

        private const val KEY_FULL_SDK_NAV_UI = "full_sdk_nav_ui"

        private const val KEY_SPEED_BAND_BASE = "speed_band_base_kmh"
        private const val KEY_SPEED_BAND_STEP = "speed_band_step_kmh"
        private const val KEY_SPEED_BAND_COUNT = "speed_band_count"
        const val DEFAULT_SPEED_BAND_BASE_KMH = 40
        const val DEFAULT_SPEED_BAND_STEP_KMH = 20
        const val DEFAULT_SPEED_BAND_COUNT = 5
        const val SPEED_BAND_BASE_MIN = 10
        const val SPEED_BAND_BASE_MAX = 120
        // Step size only needs a floor (≥1 km/h) to stay well-defined; no ceiling.
        const val SPEED_BAND_STEP_MIN = 1
        // Number of steps: floor of 1, no ceiling.
        const val SPEED_BAND_COUNT_MIN = 1
        private const val KEY_LOCATION_MARKER_STYLE = "location_marker_style"
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
