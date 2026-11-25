package com.navigator.app.ui.feature.settings

import com.navigator.app.R
import com.navigator.app.data.connection.BikeStatus
import com.navigator.app.data.permissions.PermissionsState
import com.navigator.app.data.settings.NavEngine
import com.navigator.app.data.settings.SettingsSnapshot
import com.navigator.app.nav.model.TravelMode
import com.navigator.app.ui.theme.ThemeMode

/** The line under a category on the Settings home: the values that matter, so most riders never open the page. */
data class CategorySummary(val parts: List<UiText>, val attention: Boolean = false)

/** The pure rules behind the live summaries (ST1). */
object SettingsSummary {
    fun of(page: SettingsPage, snap: SettingsSnapshot, bike: BikeStatus, permissions: PermissionsState, version: String, engine: NavEngine): CategorySummary =
        when (page) {
            SettingsPage.BIKE_DASH -> CategorySummary(
                listOf(
                    // The link state is on the card above; here, what the page holds.
                    if (snap.userName.isNullOrBlank()) UiText.Res(R.string.settings_sum_name_unset) else UiText.Raw(snap.userName.trim().take(NAME_IN_SUMMARY)),
                    UiText.Res(if (snap.mirrorEnabled) R.string.settings_sum_messages_on else R.string.settings_sum_messages_off),
                ),
            )
            SettingsPage.NAVIGATION -> CategorySummary(
                listOf(
                    UiText.Res(if (engine == NavEngine.IN_APP) R.string.settings_sum_in_app else R.string.settings_sum_mirror),
                    UiText.Res(if (snap.vehicleMode == TravelMode.DRIVING) R.string.settings_sum_vehicle_car else R.string.settings_sum_vehicle_bike),
                ),
            )
            SettingsPage.SOUNDS -> CategorySummary(
                listOf(
                    if (snap.turnBeepEnabled) UiText.Res(R.string.settings_sum_beeps_at, listOf(snap.beepVolumePercent)) else UiText.Res(R.string.settings_sum_beeps_off),
                    if (snap.overspeedEnabled) UiText.Res(R.string.settings_sum_alert_at, listOf(snap.overspeedLimitKmh))
                    else UiText.Res(R.string.settings_sum_alert_off),
                ),
            )
            SettingsPage.RECORDING -> CategorySummary(
                listOf(
                    UiText.Res(if (snap.rideRecordingEnabled) R.string.settings_sum_rec_auto else R.string.settings_sum_rec_manual),
                    UiText.Res(R.string.settings_sum_keeps, listOf(snap.rideHistoryLimit)),
                ),
            )
            SettingsPage.APPEARANCE -> CategorySummary(
                listOf(
                    UiText.Res(
                        when (snap.themeMode) {
                            ThemeMode.SYSTEM -> R.string.settings_sum_theme_system
                            ThemeMode.LIGHT -> R.string.settings_sum_theme_light
                            ThemeMode.DARK -> R.string.settings_sum_theme_dark
                        },
                    ),
                    UiText.Res(AccentNames.res(snap.accentColorId)),
                ),
            )
            SettingsPage.PERMISSIONS -> {
                val missing = permissions.requiredMissing.size
                CategorySummary(
                    listOf(if (missing == 0) UiText.Res(R.string.settings_sum_permissions_ok) else UiText.Plural(R.plurals.settings_sum_permissions_missing, missing)),
                    attention = permissions.attentionNeeded,
                )
            }
            SettingsPage.BACKUP -> CategorySummary(listOf(UiText.Res(R.string.settings_sum_backup)))
            SettingsPage.ABOUT -> CategorySummary(listOf(UiText.Res(R.string.settings_sum_about, listOf(version))))
        }
}

/** A long name would push the rest of the line off the row. */
private const val NAME_IN_SUMMARY = 16
