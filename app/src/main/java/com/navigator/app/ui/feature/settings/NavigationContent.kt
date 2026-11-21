package com.navigator.app.ui.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import com.navigator.app.R
import com.navigator.app.data.settings.NavEngine
import com.navigator.app.ui.components.KtmChoiceCard
import androidx.compose.foundation.layout.Arrangement
import com.navigator.app.nav.model.TravelMode
import com.navigator.app.settings.AppSettings
import com.navigator.app.settings.DrivingSidePreference
import com.navigator.app.ui.components.KtmSegmented
import com.navigator.app.ui.components.KtmSwitchRow
import com.navigator.app.ui.components.ListRow
import com.navigator.app.ui.components.RowChevron
import com.navigator.app.ui.components.SectionHeader
import com.navigator.app.ui.theme.KtmLayout
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme

class NavigationActions(
    val onBack: () -> Unit,
    val onEngine: (NavEngine) -> Unit,
    val onVehicle: (TravelMode) -> Unit,
    val onMarker: (String) -> Unit,
    val onLiveUpdates: (Boolean) -> Unit,
    val onAutoDetect: (Boolean) -> Unit,
    val onChooseNavApp: () -> Unit,
    val onDrivingSide: (DrivingSidePreference) -> Unit,
    val onOpenPlaces: () -> Unit = {},
)

@Composable
private fun Label(text: String) = Text(
    text, style = MaterialTheme.typography.titleSmall, color = KtmTheme.colors.fg2,
    modifier = Modifier.padding(horizontal = KtmSpacing.lg, vertical = KtmSpacing.sm),
)

@Composable
private fun Hint(text: String) = Text(
    text, style = MaterialTheme.typography.bodySmall, color = KtmTheme.colors.fg2,
    modifier = Modifier.padding(horizontal = KtmSpacing.lg, vertical = KtmSpacing.sm),
)

/** ST3: the engine as two radio rows, Route for / marker as segmented controls, and the mirror-only group. Stateless. */
@Composable
fun NavigationContent(ui: NavigationUi, actions: NavigationActions, modifier: Modifier = Modifier) {
    val vehicleLabels = mapOf(TravelMode.TWO_WHEELER to stringResource(R.string.nav_route_bike), TravelMode.DRIVING to stringResource(R.string.nav_route_car))
    val markerLabels = mapOf(
        AppSettings.LOCATION_MARKER_CHEVRON to stringResource(R.string.nav_marker_chevron),
        AppSettings.LOCATION_MARKER_BEAM to stringResource(R.string.nav_marker_beam),
    )
    val sideLabels = mapOf(
        DrivingSidePreference.AUTO to stringResource(R.string.nav_drive_auto),
        DrivingSidePreference.LEFT to stringResource(R.string.nav_drive_left),
        DrivingSidePreference.RIGHT to stringResource(R.string.nav_drive_right),
    )
    SettingsPageScaffold(stringResource(R.string.nav_title), actions.onBack, modifier) {
        if (ui.engineAvailable) {
            SectionHeader(stringResource(R.string.nav_section_engine))
            Column(Modifier.padding(horizontal = KtmSpacing.lg), verticalArrangement = Arrangement.spacedBy(KtmSpacing.sm)) {
                KtmChoiceCard(
                    stringResource(R.string.nav_engine_in_app), stringResource(R.string.nav_engine_in_app_hint),
                    ui.engine == NavEngine.IN_APP, { actions.onEngine(NavEngine.IN_APP) },
                )
                KtmChoiceCard(
                    stringResource(R.string.nav_engine_mirror), stringResource(R.string.nav_engine_mirror_hint),
                    ui.engine == NavEngine.MIRROR, { actions.onEngine(NavEngine.MIRROR) },
                )
            }
        }

        SectionHeader(stringResource(R.string.nav_section_route))
        Label(stringResource(R.string.nav_route_for))
        KtmSegmented(
            listOf(TravelMode.TWO_WHEELER, TravelMode.DRIVING), ui.vehicle, actions.onVehicle, { vehicleLabels.getValue(it) },
            Modifier.padding(horizontal = KtmSpacing.lg),
        )
        Label(stringResource(R.string.nav_marker))
        KtmSegmented(
            NavigationViewModel.MARKERS, ui.marker, actions.onMarker, { markerLabels[it] ?: it },
            Modifier.padding(horizontal = KtmSpacing.lg),
        )
        KtmSwitchRow(
            stringResource(R.string.nav_live_updates), ui.liveUpdates, actions.onLiveUpdates,
            supporting = stringResource(R.string.nav_live_updates_hint), supportingLines = 3,
        )

        SectionHeader(stringResource(R.string.nav_section_mirror))
        Column(Modifier.alpha(if (ui.engine == NavEngine.IN_APP) KtmLayout.inactiveAlpha else 1f)) {
            if (ui.engine == NavEngine.IN_APP) Hint(stringResource(R.string.nav_mirror_note))
            KtmSwitchRow(
                stringResource(R.string.nav_auto_detect), ui.autoDetect, actions.onAutoDetect,
                supporting = stringResource(R.string.nav_auto_detect_hint),
            )
            if (!ui.autoDetect) {
                ListRow(
                    title = stringResource(R.string.nav_app), supporting = ui.navAppLabel ?: stringResource(R.string.nav_app_choose),
                    onClick = actions.onChooseNavApp, trailing = { RowChevron() },
                )
            }
            Label(stringResource(R.string.nav_drive_on))
            KtmSegmented(
                listOf(DrivingSidePreference.AUTO, DrivingSidePreference.LEFT, DrivingSidePreference.RIGHT),
                ui.drivingSide, actions.onDrivingSide, { sideLabels.getValue(it) }, Modifier.padding(horizontal = KtmSpacing.lg),
            )
            Hint(stringResource(R.string.nav_drive_hint))
        }

        SectionHeader(stringResource(R.string.nav_section_places))
        ListRow(
            title = stringResource(R.string.nav_saved_places), supporting = stringResource(R.string.nav_saved_places_hint),
            onClick = actions.onOpenPlaces, trailing = { RowChevron() },
        )
    }
}
