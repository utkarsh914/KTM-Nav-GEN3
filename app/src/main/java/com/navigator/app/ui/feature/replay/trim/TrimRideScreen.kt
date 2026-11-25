package com.navigator.app.ui.feature.replay.trim

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.navigator.app.app.appContainer
import com.navigator.app.ui.feature.replay.PlaybackEngine
import com.navigator.app.ui.map.LocationMarkerStyle
import com.navigator.app.ui.map.MapClaim
import com.navigator.app.ui.map.MapInsets
import com.navigator.app.ui.map.RetainedNavigationView
import com.navigator.app.ui.map.rememberMapController
import com.navigator.app.ui.map.settled
import com.navigator.app.ui.shell.LocalFeedback
import com.navigator.app.ui.shell.MERGE_OFFER
import com.navigator.app.ui.shell.PopWhenResumed
import com.navigator.app.ui.shell.rememberPageSettled
import com.navigator.app.ui.theme.KtmMotionSpecs
import com.navigator.app.ui.theme.KtmTheme
import kotlinx.coroutines.flow.filter

/**
 * The trim page (RP5): the route on the map, kept part in the accent over the part that goes, faded; the sheet or panel
 * over it. Choosing is cheap and local; Trim writes and leaves. A trimmed merged ride ends its merge's Undo offer.
 */
@Composable
fun TrimRideScreen(rideId: String, retainedNav: RetainedNavigationView, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val container = LocalContext.current.appContainer
    val feedback = LocalFeedback.current
    val vm: TrimViewModel = viewModel(factory = TrimViewModel.factory(rideId, container))
    val ui by vm.ui.collectAsState()
    val vanished by vm.vanished.collectAsState()
    PopWhenResumed(vanished || (ui as? TrimUi.Ready)?.closed == true, onBack)
    val map = rememberMapController(retainedNav, LocationMarkerStyle.fromId(container.settings.legacy.locationMarkerStyle))
    val mapReady by map.ready.collectAsState()
    var insets by remember { mutableStateOf(MapInsets()) }
    val kept = KtmTheme.accent.fill.toArgb()
    val removed = KtmTheme.colors.fg3.copy(alpha = REMOVED_ALPHA).toArgb()
    val ready = ui as? TrimUi.Ready

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is TrimEvent.Trimmed -> if (event.merged) feedback.endOffer(MERGE_OFFER + rideId)
            }
        }
    }

    // Leaving mid-write would lose the end of a merge's Undo offer: wait for the write.
    val applying = ready?.applying == true
    BackHandler(enabled = applying) {}

    Box(modifier.fillMaxSize()) {
        MapClaim(retained = retainedNav, controller = map, navUiEnabled = false, dark = KtmTheme.colors.isDark)

        LaunchedEffect(mapReady) {
            if (!mapReady) return@LaunchedEffect
            map.setMapClickListening(true)
        }
        LaunchedEffect(map) { map.mapClicks.collect { vm.tapRoute(it.lat, it.lng, PlaybackEngine.tapRadiusM(map.cameraZoom(), it.lat)) } }
        DisposableEffect(map) { onDispose { map.setMapClickListening(false) } }

        // Drawn again at every handle move: what the cut keeps is exactly what the map shows.
        val pageSettled by rememberPageSettled()
        LaunchedEffect(mapReady, pageSettled, ready?.points, ready?.range, kept, removed) {
            val r = ready ?: return@LaunchedEffect
            if (!mapReady || !pageSettled) return@LaunchedEffect
            map.showTrimPreview(r.points, r.range.from, r.range.to, kept, removed)
        }
        // The camera frames the whole route once, in what the sheet or panel leaves open.
        var framed by remember { mutableStateOf<Any?>(null) }
        LaunchedEffect(mapReady, pageSettled, ready?.points) {
            val r = ready ?: return@LaunchedEffect
            if (!mapReady || !pageSettled) return@LaunchedEffect
            snapshotFlow { insets }.filter { it != MapInsets() }.settled(KtmMotionSpecs.INSETS_SETTLE_MS).collect { open ->
                map.awaitPaddingSettled()
                map.setPadding(open.left, open.top, open.right, open.bottom)
                if (framed !== r.points) { framed = r.points; map.frameTrack(r.points) }
            }
        }

        if (ui == TrimUi.Loading) CircularProgressIndicator(Modifier.align(Alignment.Center), color = KtmTheme.accent.fill)

        TrimContent(
            ui, onInsets = { insets = it },
            actions = TrimActions(
                onBack = { if (!applying) onBack() }, onMoveStart = vm::moveStart, onMoveEnd = vm::moveEnd, onMoveNearest = vm::moveNearest,
                onNudgeStart = vm::nudgeStart, onNudgeEnd = vm::nudgeEnd, onApply = vm::apply,
            ),
        )
    }
}

/** The part that goes is the neutral grey, faded. */
private const val REMOVED_ALPHA = 0.5f
