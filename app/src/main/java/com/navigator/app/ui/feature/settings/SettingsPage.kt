package com.navigator.app.ui.feature.settings

import androidx.annotation.Keep
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import com.navigator.app.R
import com.navigator.app.ui.theme.KtmIcons

/** The eight categories of Settings, in the order they are listed. A route argument, so kept from obfuscation. */
@Keep
enum class SettingsPage(@StringRes val title: Int, val icon: ImageVector) {
    BIKE_DASH(R.string.settings_cat_bike, KtmIcons.Bike),
    NAVIGATION(R.string.settings_cat_navigation, KtmIcons.Navigation),
    SOUNDS(R.string.settings_cat_sounds, KtmIcons.Sound),
    RECORDING(R.string.settings_cat_recording, KtmIcons.Record),
    APPEARANCE(R.string.settings_cat_appearance, KtmIcons.Palette),
    PERMISSIONS(R.string.settings_cat_permissions, KtmIcons.Permission),
    BACKUP(R.string.settings_cat_backup, KtmIcons.Backup),
    ABOUT(R.string.settings_cat_about, KtmIcons.Diagnostics),
}
