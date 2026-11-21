package com.navigator.app.ui.feature.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.navigator.app.R
import com.navigator.app.data.settings.SettingSpecs
import com.navigator.app.ui.components.ButtonSize
import com.navigator.app.ui.components.ButtonStyle
import com.navigator.app.ui.components.KtmButton
import com.navigator.app.ui.components.KtmSegmented
import com.navigator.app.ui.components.KtmStepper
import com.navigator.app.ui.components.KtmSwitchRow
import com.navigator.app.ui.components.ListRow
import com.navigator.app.ui.components.RowChevron
import com.navigator.app.ui.components.SectionHeader
import com.navigator.app.ui.theme.KtmIcons
import com.navigator.app.ui.theme.KtmSpacing
import com.navigator.app.ui.theme.KtmTheme

class SoundsActions(
    val onBack: () -> Unit,
    val onBeeps: (Boolean) -> Unit,
    val onVolume: (BeepVolume) -> Unit,
    val onSwap: (Boolean) -> Unit,
    val onHearBeeps: () -> Unit,
    val onSpeedAlert: (Boolean) -> Unit,
    val onAlertAt: (Int) -> Unit,
    val onEditAlertAt: () -> Unit,
    val onHearAlert: () -> Unit,
    val onOpenCallAudio: () -> Unit,
)

@Composable
private fun BeepVolume.label(): String = stringResource(
    when (this) {
        BeepVolume.LOW -> R.string.sounds_volume_low
        BeepVolume.MEDIUM -> R.string.sounds_volume_medium
        BeepVolume.HIGH -> R.string.sounds_volume_high
        BeepVolume.MAX -> R.string.sounds_volume_max
    },
)

/**
 * ST4: turn beeps (volume as four visible steps, the preview right under them), the speed alert as a stepper with its
 * range shown, and the call-audio device. Stateless.
 */
@Composable
fun SoundsContent(ui: SoundsUi, actions: SoundsActions, modifier: Modifier = Modifier) {
    val c = KtmTheme.colors
    val spec = SettingSpecs.overspeedLimit
    val labels = BeepVolume.entries.associateWith { it.label() }
    SettingsPageScaffold(stringResource(R.string.sounds_title), actions.onBack, modifier) {
        SectionHeader(stringResource(R.string.sounds_section_beeps))
        KtmSwitchRow(
            stringResource(R.string.sounds_beeps), ui.beeps, actions.onBeeps,
            supporting = stringResource(R.string.sounds_beeps_hint),
        )
        Text(
            stringResource(R.string.sounds_volume), style = MaterialTheme.typography.titleSmall, color = c.fg2,
            modifier = Modifier.padding(horizontal = KtmSpacing.lg, vertical = KtmSpacing.sm),
        )
        KtmSegmented(
            BeepVolume.entries, ui.volume, actions.onVolume, { labels.getValue(it) },
            Modifier.padding(horizontal = KtmSpacing.lg), enabled = ui.beeps,
        )
        KtmSwitchRow(
            stringResource(R.string.sounds_swap), ui.swapChannels, actions.onSwap,
            supporting = stringResource(R.string.sounds_swap_hint), enabled = ui.beeps,
        )
        val hearBeeps = stringResource(R.string.sounds_play_beeps_cd)
        KtmButton(
            stringResource(R.string.sounds_play_beeps), actions.onHearBeeps,
            Modifier.padding(horizontal = KtmSpacing.edge).padding(top = KtmSpacing.sm).semantics { contentDescription = hearBeeps },
            style = ButtonStyle.Tonal, size = ButtonSize.Small, icon = KtmIcons.Play,
        )

        SectionHeader(stringResource(R.string.sounds_section_speed))
        KtmSwitchRow(
            stringResource(R.string.sounds_alert), ui.speedAlert, actions.onSpeedAlert,
            supporting = stringResource(R.string.sounds_alert_hint),
        )
        Text(
            stringResource(R.string.sounds_alert_at), style = MaterialTheme.typography.titleSmall, color = c.fg2,
            modifier = Modifier.padding(horizontal = KtmSpacing.lg, vertical = KtmSpacing.sm),
        )
        KtmStepper(
            value = ui.alertAtKmh, spec = spec, onValueChange = actions.onAlertAt, unit = stringResource(R.string.sounds_unit_kmh),
            rangeText = stringResource(R.string.sounds_range, spec.min, spec.max),
            decreaseDescription = stringResource(R.string.sounds_dec_alert), increaseDescription = stringResource(R.string.sounds_inc_alert),
            editDescription = stringResource(R.string.sounds_edit_alert), onEditValue = actions.onEditAlertAt, enabled = ui.speedAlert,
            modifier = Modifier.padding(horizontal = KtmSpacing.edge),
            trailing = {
                val hearAlert = stringResource(R.string.sounds_play_cd)
                KtmButton(
                    stringResource(R.string.sounds_play), actions.onHearAlert, Modifier.semantics { contentDescription = hearAlert },
                    style = ButtonStyle.Tonal, size = ButtonSize.Small, icon = KtmIcons.Play,
                )
            },
        )

        SectionHeader(stringResource(R.string.sounds_section_calls))
        ListRow(
            title = stringResource(R.string.sounds_call_audio),
            supporting = ui.callAudioName
                ?: stringResource(if (ui.callAudioUnavailable) R.string.sounds_call_audio_unavailable else R.string.sounds_call_audio_none),
            onClick = actions.onOpenCallAudio, trailing = { RowChevron() },
        )
    }
}
