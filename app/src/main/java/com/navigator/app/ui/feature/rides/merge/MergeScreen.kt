package com.navigator.app.ui.feature.rides.merge

import androidx.activity.compose.BackHandler
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
import com.navigator.app.ride.MergeRefusal
import com.navigator.app.ride.MergeToken
import com.navigator.app.ride.RecordedRide
import com.navigator.app.ui.shell.LocalFeedback

/** The merge preview page: wires [MergeContent] to the ViewModel. [onMerged] leaves for the merged ride. */
@Composable
fun MergeScreen(
    ids: List<String>,
    onBack: () -> Unit,
    onMerged: (RecordedRide, MergeToken) -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = LocalContext.current.appContainer
    val resources = LocalResources.current
    val feedback = LocalFeedback.current
    val vm: MergeViewModel = viewModel(factory = MergeViewModel.factory(ids, container))
    val ui by vm.ui.collectAsState()
    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is MergeEvent.Merged -> onMerged(event.ride, event.token)
                is MergeEvent.Failed -> feedback.snackbar(
                    resources.getString(
                        when (event.reason) {
                            MergeRefusal.Failed, MergeRefusal.MissingTrack -> R.string.merge_failed_changed
                            MergeRefusal.Active -> R.string.merge_active_body
                            else -> R.string.merge_failed
                        },
                    ),
                )
            }
        }
    }
    // Leaving mid-merge would lose the Undo offer: wait for it to finish.
    val merging = (ui as? MergeUi.Ready)?.merging == true
    BackHandler(enabled = merging) {}
    MergeContent(ui, { if (!merging) onBack() }, vm::merge, modifier)
}
