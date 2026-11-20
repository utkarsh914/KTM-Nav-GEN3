package com.navigator.app.ui.feature.replay.trim

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.viewmodel.compose.viewModel
import com.navigator.app.R
import com.navigator.app.app.appContainer
import com.navigator.app.ui.screens.TrimRideDialog
import com.navigator.app.ui.shell.LocalFeedback
import com.navigator.app.ui.shell.MERGE_OFFER
import com.navigator.app.ui.theme.KtmTheme

/**
 * The trim page. Its view is the old dialog for now (the map preview of RP5 replaces it in the next batch); the
 * ViewModel, route and the save-on-apply behaviour are final.
 */
@Composable
fun TrimRideScreen(rideId: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val container = LocalContext.current.appContainer
    val resources = LocalResources.current
    val feedback = LocalFeedback.current
    val vm: TrimViewModel = viewModel(factory = TrimViewModel.factory(rideId, container))
    val ui by vm.ui.collectAsState()

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is TrimEvent.Trimmed -> { if (event.merged) feedback.endOffer(MERGE_OFFER + rideId); onBack() }
                TrimEvent.Failed -> feedback.snackbar(resources.getString(R.string.trim_failed))
            }
        }
    }
    LaunchedEffect(ui) { if (ui == TrimUi.Gone) onBack() }

    Box(modifier.fillMaxSize().background(KtmTheme.colors.bg))
    (ui as? TrimUi.Ready)?.let { ready ->
        TrimRideDialog(points = ready.points, onApply = vm::apply, onDismiss = onBack)
    }
}
