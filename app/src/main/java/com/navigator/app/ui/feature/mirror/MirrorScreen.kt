package com.navigator.app.ui.feature.mirror

import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsetsSides
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.navigator.app.app.AppContainer
import com.navigator.app.app.appContainer
import com.navigator.app.data.mirror.Freshness
import com.navigator.app.data.mirror.MirrorRepository
import com.navigator.app.data.mirror.MirrorStatus
import com.navigator.app.data.mirror.withoutAge
import com.navigator.app.nav.ktm.EtaFormatter
import com.navigator.app.nav.model.NavSessionState
import com.navigator.app.nav.providers.NotificationNavProvider
import com.navigator.app.notifications.AppNotificationListener
import com.navigator.app.ride.RideSession
import com.navigator.app.ui.components.LandscapePanelSpec
import com.navigator.app.ui.feature.navigate.BikePillMapper
import com.navigator.app.ui.feature.navigate.BikeSheetHost
import com.navigator.app.ui.feature.record.rememberStartRecording
import com.navigator.app.ui.shell.GoogleMapsLauncher
import com.navigator.app.ui.theme.KtmTheme
import com.navigator.app.ui.theme.pageInsets
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class MirrorViewModel(private val repo: MirrorRepository) : ViewModel() {
    /**
     * Null until the first reading, so the screen never flashes a wrong state. Without the ticking age: that is in
     * [freshness], so a screen does not recompose every second for a label that is one line of it.
     */
    private val shared = repo.status.shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    val status: StateFlow<MirrorStatus?> = shared
        .map { it.withoutAge() }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** How long ago guidance last arrived; changes every second while there is guidance. */
    val freshness: StateFlow<Freshness> = shared
        .map { (it as? MirrorStatus.Live)?.freshness ?: Freshness.JustNow }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Freshness.JustNow)

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
    // Lifecycle-aware: the repository ticks every second while collected, and not a second of it is wanted in the background.
    val status by vm.status.collectAsStateWithLifecycle()
    val freshness = vm.freshness.collectAsStateWithLifecycle()
    val bikeStatus by container.connection.status.collectAsState()
    val pill = BikePillMapper.map(bikeStatus)
    val bike = MirrorBike(pill, stringResource(pill.label, pill.labelArg.orEmpty()), stringResource(pill.state))
    var showBike by remember { mutableStateOf(false) }
    val recording by RideSession.state.collectAsState()
    val startRecording = rememberStartRecording()
    // The dash's clock: it moves on even while nothing else on the screen changes.
    val time by produceState("") {
        while (true) {
            val now = System.currentTimeMillis()
            value = EtaFormatter.format(now)
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
        onOpenMaps = { GoogleMapsLauncher.openApp(context) },
        onRecord = startRecording,
    )

    BoxWithConstraints(modifier.fillMaxSize().background(KtmTheme.colors.bg)) {
        val landscape = LandscapePanelSpec.isLandscape(maxWidth, maxHeight)
        // The page has no top bar and no map behind it: clear of the status bar and cutout, and in landscape (no tab bar) the gesture area.
        val top = WindowInsetsSides.Top + WindowInsetsSides.Horizontal
        val sides = if (landscape) top + WindowInsetsSides.Bottom else top
        status?.let { MirrorContent(it, bike, time, landscape, actions, Modifier.pageInsets(sides), recording = recording.active, freshness = { freshness.value }) }
    }
    // A mirrored trip is on the dash while Google Maps guides: the sheet's test must not replace it.
    val mirrored by NotificationNavProvider.state.collectAsState()
    if (showBike) BikeSheetHost(tripRunning = mirrored.sessionState == NavSessionState.ENROUTE, onOpenPairing = onOpenConnect, onDismiss = { showBike = false })
}
