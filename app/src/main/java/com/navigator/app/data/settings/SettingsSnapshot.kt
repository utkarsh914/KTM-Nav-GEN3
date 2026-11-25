package com.navigator.app.data.settings

import com.navigator.app.nav.model.TravelMode
import com.navigator.app.ride.AvgSpeedMode
import com.navigator.app.settings.AppSettings
import com.navigator.app.settings.DrivingSidePreference
import com.navigator.app.ui.theme.FontMode
import com.navigator.app.ui.theme.NavThemeMode
import com.navigator.app.ui.theme.ThemeMode

/** Which navigation source drives the app: Google's engine in-app, or mirroring another app's notifications. */
enum class NavEngine {
    IN_APP, MIRROR;

    companion object {
        /** The stored choice, or the mirror when the in-app engine cannot run. */
        fun inUse(stored: NavEngine, available: Boolean): NavEngine = if (available) stored else MIRROR
    }
}

/**
 * Immutable, typed view of every setting the UI reads. Pairing secrets (session keys, paired-before
 * flags), diagnostics results and calibration data stay in [AppSettings]: they are not UI state.
 *
 * KTM-only: there is no brand, and the retired "full Google nav UI" switch is not exposed.
 */
data class SettingsSnapshot(
    // Bike & identity
    val bondedDeviceAddress: String?,
    val bondedDeviceName: String?,
    val onboardingComplete: Boolean,
    val userName: String?,
    // Appearance. A null accent means the KTM orange.
    val themeMode: ThemeMode,
    val ridingScreens: NavThemeMode,
    val fontMode: FontMode,
    val accentColorId: String?,
    val locationMarkerStyle: String,
    // Navigation
    val engine: NavEngine,
    val vehicleMode: TravelMode,
    val drivingSide: DrivingSidePreference,
    val liveUpdatesEnabled: Boolean,
    val navAppOverride: String?,
    // Dash and mirroring
    val mirrorEnabled: Boolean,
    val notificationSourceApps: Set<String>,
    val marqueeEnabled: Boolean,
    val latinDashText: Boolean,
    val callAudioDeviceAddress: String?,
    // Sounds and alerts
    val turnBeepEnabled: Boolean,
    val beepVolumePercent: Int,
    val swapBeepChannels: Boolean,
    val overspeedEnabled: Boolean,
    val overspeedLimitKmh: Int,
    // Recording
    val rideRecordingEnabled: Boolean,
    val rideMinDistanceMeters: Int,
    val rideMinDurationSeconds: Int,
    val rideHistoryLimit: Int,
    val avgSpeedMode: AvgSpeedMode,
    val speedBandBaseKmh: Int,
    val speedBandStepKmh: Int,
    val speedBandCount: Int,
    // Diagnostics
    val dashPreviewOverlay: Boolean,
    /** When the bike was paired (0 = unknown). */
    val bondedPairedMs: Long = 0L,
) {
    val isPaired: Boolean get() = !bondedDeviceAddress.isNullOrBlank()

    companion object {
        fun read(s: AppSettings) = SettingsSnapshot(
            bondedDeviceAddress = s.bondedDeviceAddress,
            bondedDeviceName = s.bondedDeviceName,
            onboardingComplete = s.onboardingComplete,
            userName = s.userName,
            themeMode = s.themeMode,
            ridingScreens = s.navThemeMode,
            fontMode = s.fontMode,
            accentColorId = s.accentColorId,
            locationMarkerStyle = s.locationMarkerStyle,
            engine = if (s.googleNavEnabled) NavEngine.IN_APP else NavEngine.MIRROR,
            vehicleMode = s.vehicleMode,
            drivingSide = s.drivingSidePreference,
            liveUpdatesEnabled = s.liveUpdatesEnabled,
            navAppOverride = s.navAppOverride,
            mirrorEnabled = s.mirrorEnabled,
            notificationSourceApps = s.notificationSourceApps,
            marqueeEnabled = s.marqueeEnabled,
            latinDashText = s.latinDashText,
            callAudioDeviceAddress = s.preferredCallAudioDeviceAddress,
            turnBeepEnabled = s.turnBeepEnabled,
            beepVolumePercent = s.beepVolumePercent,
            swapBeepChannels = s.swapBeepChannels,
            overspeedEnabled = s.overspeedEnabled,
            overspeedLimitKmh = s.overspeedLimitKmh,
            rideRecordingEnabled = s.rideRecordingEnabled,
            rideMinDistanceMeters = s.rideMinDistanceMeters,
            rideMinDurationSeconds = s.rideMinDurationSeconds,
            rideHistoryLimit = s.rideHistoryLimit,
            avgSpeedMode = s.avgSpeedMode,
            speedBandBaseKmh = s.speedBandBaseKmh,
            speedBandStepKmh = s.speedBandStepKmh,
            speedBandCount = s.speedBandCount,
            dashPreviewOverlay = s.dashPreviewOverlay,
            bondedPairedMs = s.bondedPairedMs,
        )
    }
}
