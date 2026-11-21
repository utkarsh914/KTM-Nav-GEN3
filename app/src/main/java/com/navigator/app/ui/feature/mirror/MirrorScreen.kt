package com.navigator.app.ui.feature.mirror

import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.navigator.app.app.AppContainer
import com.navigator.app.app.appContainer
import com.navigator.app.data.mirror.MirrorRepository
import com.navigator.app.data.mirror.MirrorStatus
import com.navigator.app.notifications.AppNotificationListener
import com.navigator.app.ui.components.LandscapePanelSpec
import com.navigator.app.ui.feature.navigate.BikePillMapper
import com.navigator.app.ui.feature.navigate.BikeSheetHost
import com.navigator.app.ui.feature.record.rememberStartRecording
import com.navigator.app.ui.shell.openGoogleMapsApp
import com.navigator.app.ui.theme.KtmTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MirrorViewModel(private val repo: MirrorRepository) : ViewModel() {
    /** Null until the first reading, so the screen never flashes a wrong state. */
    val status: StateFlow<MirrorStatus?> = repo.status.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun refresh() = repo.refresh()
    fun setSending(on: Boolean) = repo.setSending(on)

    companion object {
        fun factory(container: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = MirrorViewModel(container.mirror) as T
        }
    }
}

private const val MINUTE_MS = 60_000L

/** The Navigate tab when the engine is Mirror: wires [MirrorContent] to the repository and to Android's settings. */
@Composable
fun MirrorScreen(onOpenConnect: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val container = context.appContainer
    val vm: MirrorViewModel = viewModel(factory = MirrorViewModel.factory(container))
    val status by vm.status.collectAsState()
    val bikeStatus by container.connection.status.collectAsState()
    val pill = BikePillMapper.map(bikeStatus)
    val bike = MirrorBike(pill, stringResource(pill.label, pill.labelArg.orEmpty()), stringResource(pill.state))
    var showBike by remember { mutableStateOf(false) }
    val recording by com.navigator.app.ride.RideSession.state.collectAsState()
    val startRecording = rememberStartRecording()
    // The dash's clock: it moves on even while nothing else on the screen changes.
    val time by produceState("") {
        while (true) {
            val now = System.currentTimeMillis()
            value = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(now))
            // To the next minute boundary, so the shown time is never up to a tick behind.
            delay(MINUTE_MS - now % MINUTE_MS + 50)
        }
    }

    // Android has no callback for these: read them again whenever the rider comes back from its settings.
    LifecycleResumeEffect(Unit) {
        vm.refresh()
        onPauseOrDispose { }
    }

    val actions = MirrorActions(
        onBike = { if (pill.opensPairing) onOpenConnect() else showBike = true },
        onGrantAccess = { runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) } },
        onReconnect = {
            runCatching {
                NotificationListenerService.requestRebind(ComponentName(context, AppNotificationListener::class.java))
            }
        },
        onAllowBattery = {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:${context.packageName}".toUri()),
                )
            }
        },
        onSending = vm::setSending,
        onOpenMaps = { openGoogleMapsApp(context) },
        onRecord = startRecording,
    )

    BoxWithConstraints(modifier.fillMaxSize().background(KtmTheme.colors.bg)) {
        val landscape = LandscapePanelSpec.isLandscape(maxWidth, maxHeight)
        status?.let { MirrorContent(it, bike, time, landscape, actions, recording = recording.active) }
    }
    if (showBike) BikeSheetHost(tripRunning = false, onOpenPairing = onOpenConnect, onDismiss = { showBike = false })
}
