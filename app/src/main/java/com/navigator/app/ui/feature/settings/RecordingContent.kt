package com.navigator.app.ui.feature.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.navigator.app.R
import com.navigator.app.ride.AvgSpeedMode
import com.navigator.app.ui.components.KtmSegmented
import com.navigator.app.ui.components.KtmSwitchRow
import com.navigator.app.ui.components.SectionHeader
import com.navigator.app.ui.theme.KtmIcons
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme

class RecordingActions(
    val onBack: () -> Unit,
    val onRecording: (Boolean) -> Unit,
    val onAvgMode: (AvgSpeedMode) -> Unit,
    val onChange: (RecordingField, Int) -> Unit,
    val onEdit: (RecordingField) -> Unit,
)

@Composable
private fun Note(text: String) = Text(
    text, style = MaterialTheme.typography.bodySmall, color = KtmTheme.colors.fg2,
    modifier = Modifier.padding(horizontal = KtmSpacing.lg, vertical = KtmSpacing.sm),
)

@Composable
internal fun RecordingField.label(): String = stringResource(
    when (this) {
        RecordingField.MIN_DISTANCE -> R.string.rec_min_distance
        RecordingField.MIN_DURATION -> R.string.rec_min_duration
        RecordingField.HISTORY -> R.string.rec_history
        RecordingField.BAND_BASE -> R.string.rec_band_base
        RecordingField.BAND_STEP -> R.string.rec_band_step
        RecordingField.BAND_COUNT -> R.string.rec_band_count
    },
)

@Composable
internal fun RecordingField.unit(): String = stringResource(
    when (this) {
        RecordingField.MIN_DISTANCE -> R.string.rec_unit_m
        RecordingField.MIN_DURATION -> R.string.rec_unit_s
        RecordingField.HISTORY -> R.string.rec_unit_rides
        RecordingField.BAND_BASE, RecordingField.BAND_STEP -> R.string.sounds_unit_kmh
        RecordingField.BAND_COUNT -> R.string.rec_unit_colors
    },
)

/**
 * ST5: the keep limits stay visible with auto-record off (manual recordings use them too), numbers are steppers,
 * and the track colours sit here with a live legend. Stateless.
 */
@Composable
fun RecordingContent(ui: RecordingUi, actions: RecordingActions, modifier: Modifier = Modifier) {
    val avgLabels = mapOf(AvgSpeedMode.MOVING to stringResource(R.string.rec_avg_moving), AvgSpeedMode.OVERALL to stringResource(R.string.rec_avg_overall))
    @Composable
    fun Stepper(field: RecordingField) = SettingStepper(
        field.label(), ui.valueOf(field), field.spec, field.unit(), { actions.onChange(field, it) }, { actions.onEdit(field) },
        supporting = if (field == RecordingField.HISTORY) stringResource(R.string.rec_history_note) else null,
    )
    SettingsPageScaffold(stringResource(R.string.rec_title), actions.onBack, modifier) {
        KtmSwitchRow(
            stringResource(R.string.rec_record), ui.recording, actions.onRecording,
            supporting = stringResource(R.string.rec_record_hint), supportingLines = 2, leadingIcon = KtmIcons.Record,
        )
        Text(
            stringResource(R.string.rec_avg), style = MaterialTheme.typography.titleSmall, color = KtmTheme.colors.fg2,
            modifier = Modifier.padding(horizontal = KtmSpacing.lg, vertical = KtmSpacing.sm),
        )
        KtmSegmented(AvgSpeedMode.entries, ui.avgMode, actions.onAvgMode, { avgLabels.getValue(it) }, Modifier.padding(horizontal = KtmSpacing.lg))
        Note(stringResource(R.string.rec_avg_hint))

        SectionHeader(stringResource(R.string.rec_section_keep))
        Note(stringResource(R.string.rec_keep_note))
        Stepper(RecordingField.MIN_DISTANCE)
        Stepper(RecordingField.MIN_DURATION)
        Stepper(RecordingField.HISTORY)

        SectionHeader(stringResource(R.string.rec_section_colors))
        Note(stringResource(R.string.rec_colors_note))
        Stepper(RecordingField.BAND_BASE)
        Stepper(RecordingField.BAND_STEP)
        Stepper(RecordingField.BAND_COUNT)
        SpeedBandLegend(ui.bandBase, ui.bandStep, ui.bandCount, Modifier.padding(top = KtmSpacing.sm))
    }
}
