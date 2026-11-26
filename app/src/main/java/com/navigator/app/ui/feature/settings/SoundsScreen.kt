package com.navigator.app.ui.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.navigator.app.R
import com.navigator.app.app.appContainer
import com.navigator.app.data.settings.SettingSpecs

/** The Sounds & alerts page: [SoundsContent] over its view model, with the alert-speed sheet and the previews. */
@Composable
fun SoundsScreen(onBack: () -> Unit, onOpenCallAudio: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val vm: SoundsViewModel = viewModel(factory = SoundsViewModel.factory(context.appContainer, context))
    val ui by vm.ui.collectAsState()
    LifecycleResumeEffect(vm) { vm.refresh(); onPauseOrDispose { } }
    var editAlert by rememberSaveable { mutableStateOf(false) }

    SoundsContent(
        ui, modifier = modifier,
        actions = SoundsActions(
            onBack = onBack, onBeeps = vm::setBeeps, onVolume = vm::setVolume, onSwap = vm::setSwap,
            onHearBeeps = { SoundPreview.beeps(ui.volume.percent, ui.swapChannels) },
            onSpeedAlert = vm::setSpeedAlert, onAlertAt = vm::setAlertAt, onEditAlertAt = { editAlert = true },
            onHearAlert = SoundPreview::speedAlert, onOpenCallAudio = onOpenCallAudio,
        ),
    )
    if (editAlert) {
        EditNumberSheet(
            title = stringResource(R.string.sounds_alert_at), initial = ui.alertAtKmh, spec = SettingSpecs.overspeedLimit,
            unit = stringResource(R.string.sounds_unit_kmh), onSave = { vm.setAlertAt(it); editAlert = false }, onDismiss = { editAlert = false },
        )
    }
}
