package com.navigator.app.ui.feature.settings

import com.navigator.app.R
import com.navigator.app.data.connection.BikeStatus
import com.navigator.app.data.permissions.PermissionsState
import com.navigator.app.data.settings.NavEngine
import com.navigator.app.data.settings.SettingsSnapshot
import com.navigator.app.nav.model.TravelMode
import com.navigator.app.ui.feature.navigate.BikePillMapper
import com.navigator.app.ui.theme.ThemeMode
import com.navigator.app.ui.theme.accentFor

/** The line under a category on the Settings home: the values that matter, so most riders never open the page. */
data class CategorySummary(val parts: List<UiText>, val attention: Boolean = false)

/** The pure rules behind the live summaries (ST1). */
object SettingsSummary {
    fun of(page: SettingsPage, snap: SettingsSnapshot, bike: BikeStatus, permissions: PermissionsState, version: String): CategorySummary =
        when (page) {
            SettingsPage.BIKE_DASH -> CategorySummary(
                listOf(
                    UiText.Res(BikePillMapper.map(bike).state),
                    UiText.Res(if (snap.mirrorEnabled) R.string.settings_sum_messages_on else R.string.settings_sum_messages_off),
                ),
            )
            SettingsPage.NAVIGATION -> CategorySummary(
                listOf(
                    UiText.Res(if (snap.engine == NavEngine.IN_APP) R.string.settings_sum_in_app else R.string.settings_sum_mirror),
                    UiText.Res(if (snap.vehicleMode == TravelMode.DRIVING) R.string.settings_sum_vehicle_car else R.string.settings_sum_vehicle_bike),
                ),
            )
            SettingsPage.SOUNDS -> CategorySummary(
                listOf(
                    UiText.Res(if (snap.turnBeepEnabled) R.string.settings_sum_beeps_on else R.string.settings_sum_beeps_off),
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
                    UiText.Raw(accentFor(snap.accentColorId).displayName),
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
